package multinode.message;

import multinode.message.siblings.MessageType;
import org.springframework.context.ApplicationEvent;

public class MessageEvent extends ApplicationEvent {
  public String message;
  public MessageType type;

  public MessageEvent(Object source, String message, MessageType type) {
    super(source);
    this.message = message;
    this.type = type;
  }
}
