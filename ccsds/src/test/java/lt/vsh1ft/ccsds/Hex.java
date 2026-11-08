package lt.vsh1ft.ccsds;

import java.util.HexFormat;

final class Hex {

    private Hex() {
    }

    static byte[] bytes(String hex) {
        return HexFormat.ofDelimiter(" ").parseHex(hex);
    }
}
