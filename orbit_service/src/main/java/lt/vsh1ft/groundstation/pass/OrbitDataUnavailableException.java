package lt.vsh1ft.groundstation.pass;

import lt.vsh1ft.groundstation.error.UnavailableException;

public class OrbitDataUnavailableException extends UnavailableException {

    public OrbitDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
