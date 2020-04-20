package multinode.message.siblings.receive;

import java.util.concurrent.TimeUnit;

interface Receiver {
  void setInterval(long interval, TimeUnit unit);

  long getInterval(TimeUnit unit);

  void scheduleSync();

  /** Garbage collection of old message to keep performance */
  void scheduleGC();

  void sync();
}
