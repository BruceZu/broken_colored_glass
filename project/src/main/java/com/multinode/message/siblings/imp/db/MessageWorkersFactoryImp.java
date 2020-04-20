package multinode.message.siblings.imp.db;

import multinode.message.MessageWorkersFactory;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * <pre>
 * Note:
 * This work queues is designed for DB based message processing
 * situation where each task is IO-bound. For different classes
 * of tasks with radically different characteristics, please do
 * not share this work queues, instead use another work queues for
 * different types of tasks, so each pool can be tuned accordingly.
 */
@Component("MessageExecutorServiceFactoryForDBImp")
@Lazy
public class MessageWorkersFactoryImp implements MessageWorkersFactory, DisposableBean {
  private static final Logger logger = LoggerFactory.getLogger(MessageWorkersFactoryImp.class);
  private ScheduledExecutorService workers;

  private static final UncaughtExceptionHandler LOG_UNCAUGHT_EXCEPTION =
      (t, e) ->
          logger.error(String.format("MessageWorkers thread %s threw exception", t.getName()), e);

  public MessageWorkersFactoryImp() {
    /**
     * <pre>
     * 'corePoolSize'
     * * Assume:
     * - CPU utilization:  at most M/E = 0.5. reserve processing for other unrelated
     *   purposes.
     *   M = compute-bound tasks of message processing sub-system
     *   E = entirely compute-bound tasks
     *
     * - No limitations of available memory, or other system resources,
     *   such the number of database connections.
     *
     * - The ratio WT/ST = 2. Refer N*(1 + WT/ST) provided by Brian Goetz
     *
     * * TODO: make it configurable.
     *   Enable end user tuning it in production environment according
     *   Little's law.
     */
    float cpuUtilization = 0.5f;
    int blockingCoefficient = 2;
    int processNumber = Runtime.getRuntime().availableProcessors();
    float corePoolSizefloat = processNumber * cpuUtilization * (1 + blockingCoefficient);
    int corePoolSizeUpperBound = (int) (corePoolSizefloat < 1 ? 1 : corePoolSizefloat);
    ScheduledThreadPoolExecutor imp =
        new ScheduledThreadPoolExecutor(
            corePoolSizeUpperBound,
            new ThreadFactory() {
              private final ThreadFactory parent = Executors.defaultThreadFactory();
              private final AtomicInteger tid = new AtomicInteger(1);

              @Override
              public Thread newThread(Runnable task) {
                final Thread t = parent.newThread(task);
                t.setName("MessageWorkers-" + tid.getAndIncrement());
                t.setUncaughtExceptionHandler(LOG_UNCAUGHT_EXCEPTION);
                if (t.isDaemon()) t.setDaemon(false);
                if (t.getPriority() != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY);
                return t;
              }
            });
    imp.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    imp.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
    /** Should be great than the configured receive delay time which is 5 seconds by default now */
    imp.setKeepAliveTime(60L, TimeUnit.SECONDS);
    /**
     * May never be considered. The default DelayedWorkQueue can grow its capacity to
     * Integer.MAX_VALUE
     */
    imp.setMaximumPoolSize(corePoolSizeUpperBound * 2);
    imp.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    /** Performance concern */
    imp.setRemoveOnCancelPolicy(false);
    imp.allowCoreThreadTimeOut(true);
    workers = Executors.unconfigurableScheduledExecutorService(imp);
  }

  /** Tasks are not guaranteed to execute sequentially */
  @Override
  public ScheduledExecutorService getScheduledExecutorService() {
    return workers;
  }

  /** Tasks are not guaranteed to execute sequentially */
  @Bean("MessageExecutorServiceForDBImp")
  @Lazy
  @DependsOn("MessageExecutorServiceFactory")
  public ExecutorService getExecutorService() {
    return Executors.unconfigurableExecutorService(workers);
  }

  @Override
  public void destroy() throws Exception {
    workers.shutdown();
    boolean isTerminated;
    do {
      try {
        isTerminated = workers.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
        isTerminated = false;
      }
    } while (!isTerminated);
  }
}
