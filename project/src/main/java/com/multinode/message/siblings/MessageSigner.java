package multinode.message.siblings;

public interface MessageSigner {

	Message sign(String localMessage);
}
