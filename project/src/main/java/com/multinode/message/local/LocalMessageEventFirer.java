package multinode.message.local;

import java.util.function.Supplier;

import multinode.message.siblings.MessageType;

interface LocalMessageEventFirer {

  void fireEvent(Supplier<String> messageSupplier, MessageType type);
}
