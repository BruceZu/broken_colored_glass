package multinode.message;

import multinode.message.siblings.MessageType;

public class SiblingsMessageEvent extends MessageEvent {
  public SiblingsMessageEvent(Object source, String message, MessageType type) {
    super(source, message, type);
  }
}
