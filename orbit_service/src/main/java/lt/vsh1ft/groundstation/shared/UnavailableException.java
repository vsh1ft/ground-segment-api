package lt.vsh1ft.groundstation.shared;

public abstract class UnavailableException extends RuntimeException {

    protected UnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
