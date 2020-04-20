package multinode.message.siblings.imp.db.publish;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import multinode.message.LocalMessageEvent;
import multinode.message.siblings.MessageSigner;
import multinode.message.siblings.MessageType;
import multinode.message.siblings.publish.Publisher;

@Component
public class PublisherImp implements Publisher {
	/**
	 * <pre>
	 * Before the complement mechanism is available use
	 * the default synchronous way:
	 * - Participate in the caller's transaction context.
	 * - Exception will be propagated to the caller
	 * Thus avoid data consistent issue between DB and cache in local node.
	 */
	@EventListener
	public void onApplicationEvent(LocalMessageEvent event) {
		//  TODO: Check if and how
		//   @TransactionalEventListener(fallbackExecution = true)
		//   take part in caller's transaction

	}

	@Override
	public void publish(MessageSigner c, MessageType type) {
		// TODO Auto-generated method stub

	}
}
