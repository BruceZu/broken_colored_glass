package multinode.message.siblings;

public class Message {
  public String signature;
  public String message;
  public MessageType type;

  @Override
  public String toString() {
    return String.format("From %s, message %s, type %s", signature, message, type);
  }
}
