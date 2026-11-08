package lt.vsh1ft.groundstation.elements;

import lt.vsh1ft.groundstation.shared.BadRequestException;

public class InvalidTleException extends BadRequestException {

    public InvalidTleException(String message) {
        super(message);
    }

    public InvalidTleException(String message, Throwable cause) {
        super(message, cause);
    }
}
