package multinode.message.siblings.imp.db.receive;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import multinode.message.siblings.Message;
import multinode.message.siblings.MessageType;
import multinode.message.siblings.imp.Abstract;
import multinode.message.siblings.imp.db.MessageTypeManager;
import multinode.message.siblings.receive.Propagater;
import multinode.message.siblings.receive.Receiver;

@Component
public class ReceiverImp extends Abstract implements Receiver {
	private static final Logger logger = LoggerFactory.getLogger(ReceiverImp.class);

	private static class FetchResult {
		ArrayList<Message> messages;
		long batchTimeMillis;

		public FetchResult(ArrayList<Message> messages, long batchTimeMillis) {
			this.messages = messages;
			this.batchTimeMillis = batchTimeMillis;
		}
	}

	private static class Success {
		public MessageType type;
		public long batchTimeMillis;

		public Success(MessageType type, long batchTimeMillis) {
			this.type = type;
			this.batchTimeMillis = batchTimeMillis;
		}
	}

	/** Default 5 seconds. Saved in TimeUnit.NANOSECONDS */
	private long timeInterval = TimeUnit.SECONDS.toNanos(5);

	private Propagater propagater;
	private DataSource ds;

	private Map<MessageType, Long> MessageTypelastReceiveTime =
			new HashMap<>(MessageType.values().length);

	/** clean each type old message ; what is old: 7*24 hours. */
	// TODO
	private Runnable clean;

	private Function<? super FetchResult, ? extends CompletionStage<Success>>
	consumeSiblingsMessages =
	(fetchResult) -> {
		Message[] messages = (Message[]) fetchResult.messages.toArray();
		for (int i = 0; i < messages.length; i++) {
			propagater.propagate(messages[i]);
		}
		return CompletableFuture.completedFuture(
				new Success(messages[0].type, fetchResult.batchTimeMillis));
	};
	private BiFunction<? super Success, Throwable, ? extends Boolean> resultHandler =
			(okay, ex) -> {
				if (ex != null) {
					// Throwable happened on the first or second phrase
					logger.error("Consume sibling message error:", ex);
					// TODO email admin about the issue
					return false;
				}

				setLastReceiveTime(okay.type, okay.batchTimeMillis, TimeUnit.MILLISECONDS);
				logger.debug(
						String.format(
								"sync started at time(UTC) %d, consume job is done with message type: %s. Now(UTC): %d ",
								okay.batchTimeMillis, okay.type, Instant.now().toEpochMilli()));
				return true;
			};

			private void setLastReceiveTime(MessageType type, long lastReceiveTime, TimeUnit unit) {
				MessageTypelastReceiveTime.put(type, unit.toNanos(lastReceiveTime));
			}

			private long getLastReceiveTime(MessageType type, TimeUnit unit) {
				return unit.convert(MessageTypelastReceiveTime.get(type), TimeUnit.NANOSECONDS);
			}
			/** initial last receive time make sure table is created if not exists */
			private void initial() {
				long startTime = Instant.now().toEpochMilli();
				for (MessageType type : MessageType.values()) {
					setLastReceiveTime(type, startTime, TimeUnit.MILLISECONDS);
				}
				setInterval(5, TimeUnit.SECONDS);
				// TODO
			}

			/**
			 * <pre>
			 *  For each message type
			 *   -  get related table
			 *   -  search distinct result of related other nodes message created from last fetch time.
			 */
			private FetchResult fetchFromDB(
					MessageType type,
					Function<MessageType, String> tableNameParser,
					long batchTimeMillis,
					String notInlucdeNode) {

				long fromTime = getLastReceiveTime(type, TimeUnit.MILLISECONDS);
				String tableName = tableNameParser.apply(type);
				// TODO
				logger.debug(
						String.format(
								"sync started at time(UTC) %d. fetch job is done with message type: %s. Now(UTC): %d ",
								batchTimeMillis, type, Instant.now().toEpochMilli()));
				return null;
			}

			@Autowired
			void setDataSource(DataSource ds) {
				this.ds = ds;
			}

			@Autowired
			void setPropagater(Propagater propagater) {
				this.propagater = propagater;
			}

			@Override
			public void setInterval(long interval, TimeUnit unit) {
				timeInterval = unit.toNanos(interval);
			}

			@Override
			public long getInterval(TimeUnit unit) {
				return unit.convert(timeInterval, TimeUnit.NANOSECONDS);
			}

			@Override
			public void sync() {
				final long batchTimeMillis = Instant.now().toEpochMilli();
				logger.debug(String.format("sync started at time(UTC) %d", batchTimeMillis));

				Set<MessageType> types = EnumSet.allOf(MessageType.class);
				CompletableFuture<?>[] futures =
						types
						.stream()
						.map(
								type ->
								CompletableFuture.supplyAsync(
										() ->
										fetchFromDB(
												type,
												MessageTypeManager::getEntityName,
												batchTimeMillis,
												uuid.getUUID()),
										executorService)
								.thenComposeAsync(consumeSiblingsMessages, executorService)
								.handle(resultHandler))
						.toArray(CompletableFuture[]::new);
				try {
					CompletableFuture.allOf(futures).join();
				} catch (CancellationException | CompletionException e) {
					logger.error(
							String.format(
									"Error: task cancelled or encountered error in completing a result or task started at time (UTC) %d ",
									batchTimeMillis),
							e);
					// TODO email admin about the issue
				}
				logger.debug(
						String.format(
								"sync started at time(UTC) %d is done. Now(UTC): %d ",
								batchTimeMillis, Instant.now().toEpochMilli()));
			}

			@Override
			public void scheduleSync() {
				scheduledExecutorService.scheduleWithFixedDelay(
						this::sync, 3, getInterval(TimeUnit.SECONDS), TimeUnit.SECONDS);
				logger.debug("Scheduled message fetch task started");
			}

			@Override
			public void scheduleGC() {
				scheduledExecutorService.scheduleWithFixedDelay(clean, 0, 7 * 24, TimeUnit.HOURS);
				logger.debug("Scheduled old message clean task started");
			}

			@EventListener(ContextStartedEvent.class)
			private void trigger(ContextStartedEvent startEvent) {
				initial(); // TODO Which will be the early one?
				scheduleSync();
				scheduleGC();
			}

			@PostConstruct
			public void init() throws Exception {
				initial(); // TODO Which will be the early one?
			}


}
