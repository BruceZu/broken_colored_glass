package multinode.message.siblings.publish;

import multinode.message.siblings.Message;
import multinode.message.siblings.MessageType;

public interface Shipper {

	void deliver(Message message, MessageType type);
}
