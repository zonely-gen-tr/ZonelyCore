package dev.zonely.whiteeffect.api.exception;


public class CreditOperationException extends Exception {

    private static final long serialVersionUID = 1L;

    public CreditOperationException(String message) {
        super(message);
    }

    public CreditOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public CreditOperationException(Throwable cause) {
        super(cause);
    }
}
