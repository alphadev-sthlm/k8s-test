package se.alphadev.k8stest;

public class K8sClusterException extends RuntimeException {

    public K8sClusterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public K8sClusterException(String message, Throwable cause) {
        super(message, cause);
    }

    public K8sClusterException(String message) {
        super(message);
    }

    public K8sClusterException(Throwable cause) {
        super(cause);
    }

}
