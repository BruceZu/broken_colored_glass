package multinode.message.siblings.imp.db.receive;

import multinode.message.siblings.Message;
import multinode.message.siblings.receive.Propagater;
import org.springframework.stereotype.Component;

@Component
public class PropagaterImp implements Propagater {

  @Override
  public void propagate(Message messages) {}
}
