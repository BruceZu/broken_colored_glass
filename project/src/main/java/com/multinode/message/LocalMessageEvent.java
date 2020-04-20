package multinode.message;

import multinode.message.siblings.MessageType;

public class LocalMessageEvent extends MessageEvent {
  public LocalMessageEvent(Object source, String message, MessageType type) {
    super(source, message, type);
  }
}
