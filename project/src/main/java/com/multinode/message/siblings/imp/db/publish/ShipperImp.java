package multinode.message.siblings.imp.db.publish;

import multinode.message.siblings.Message;
import multinode.message.siblings.MessageType;
import multinode.message.siblings.imp.Abstract;
import multinode.message.siblings.publish.Shipper;

public class ShipperImp extends Abstract implements Shipper {

  @Override
  public void deliver(Message message, MessageType type) {
    // create table if need

  }
}
