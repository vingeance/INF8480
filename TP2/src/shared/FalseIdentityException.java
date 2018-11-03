package shared;

public class FalseIdentityException extends Exception {
    public FalseIdentityException() {
        super("LoadBalancer identity verification failed.");
    }
}