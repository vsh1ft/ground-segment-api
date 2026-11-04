package lt.vsh1ft.groundstation.elements;

public class CelestrakException extends RuntimeException {

    public CelestrakException(String message) {
        super(message);
    }

    public CelestrakException(String message, Throwable cause) {
        super(message, cause);
    }
}
