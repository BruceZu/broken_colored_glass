package multinode.message.siblings.receive;

import multinode.message.siblings.Message;

public interface Propagater {

  /**
   * Asynchronously distribute message to message type related subscribers
   *
   * @param type
   * @param message
   */
  void propagate(Message messages);
}
