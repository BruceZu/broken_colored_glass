package multinode.message.siblings.imp.db;

import multinode.message.siblings.MessageType;

public class MessageTypeManager {

  private static final String prefix = "TMP_MESSAGE_";

  public static String getEntityName(MessageType type) {
    return prefix + type.name().toUpperCase();
  }

  public static MessageType getMessageType(String name) {
    String typName = name.toUpperCase().substring(prefix.length());
    return MessageType.valueOf(typName);
  }
}
