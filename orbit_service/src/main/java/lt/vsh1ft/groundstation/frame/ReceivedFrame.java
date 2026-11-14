package lt.vsh1ft.groundstation.frame;

import java.time.Instant;
import java.util.List;

record ReceivedFrame(long id, Instant firstReceivedAt, String container, Instant generationTime, Boolean crcChecked,
                     byte[] raw, List<Reception> receptions) {

    ReceivedFrame {
        receptions = List.copyOf(receptions);
    }

    ReceivedFrame withReceptions(List<Reception> heard) {
        return new ReceivedFrame(id, firstReceivedAt, container, generationTime, crcChecked, raw, heard);
    }
}
