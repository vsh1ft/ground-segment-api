package lt.vsh1ft.groundstation.error;

public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }

    protected NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
