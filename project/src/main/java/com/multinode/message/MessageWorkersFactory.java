package multinode.message;

import java.util.concurrent.ScheduledExecutorService;

public interface MessageWorkersFactory {
	ScheduledExecutorService getScheduledExecutorService();
}
