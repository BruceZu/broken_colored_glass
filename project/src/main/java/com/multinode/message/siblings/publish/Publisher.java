package multinode.message.siblings.publish;

import multinode.message.siblings.MessageSigner;
import multinode.message.siblings.MessageType;

public interface Publisher {
	void publish(MessageSigner c, MessageType type);
}
