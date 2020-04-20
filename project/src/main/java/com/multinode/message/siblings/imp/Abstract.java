package multinode.message.siblings.imp;

import multinode.UUID;
import multinode.message.MessageWorkersFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;

public abstract class Abstract {
  protected UUID uuid;
  protected ScheduledExecutorService scheduledExecutorService;
  protected ExecutorService executorService;

  @Autowired
  public void setUUID(UUID uuid) {
    this.uuid = uuid;
  }

  @Autowired
  @Qualifier("MessageExecutorServiceFactoryForDBImp")
  @Lazy
  public void setMessageWorkersFactory(MessageWorkersFactory messageWorkersFactory) {
    this.scheduledExecutorService = messageWorkersFactory.getScheduledExecutorService();
  }

  @Autowired
  @Qualifier("MessageExecutorServiceForDBImp")
  @Lazy
  public void setMessageExecutorService(ExecutorService executorService) {
    this.executorService = executorService;
  }
}
