package lt.vsh1ft.groundstation.elements;

import lt.vsh1ft.groundstation.shared.UnavailableException;

public class OrbitDataUnavailableException extends UnavailableException {

    public OrbitDataUnavailableException(String message) {
        super(message);
    }

    public OrbitDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
