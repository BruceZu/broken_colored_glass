package multinode.message.local;

import multinode.message.LocalMessageEvent;
import multinode.message.siblings.MessageType;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

@Component
public class LocalMessageEventFirerImp
    implements LocalMessageEventFirer, ApplicationEventPublisherAware {
  private ApplicationEventPublisher publisher;

  @Override
  public void fireEvent(Supplier<String> messageSupplier, MessageType type) {
    publisher.publishEvent(new LocalMessageEvent(this, messageSupplier.get(), type));
  }

  @Override
  public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    publisher = applicationEventPublisher;
  }
}
