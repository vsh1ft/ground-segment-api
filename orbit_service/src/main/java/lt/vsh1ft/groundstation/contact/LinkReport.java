package lt.vsh1ft.groundstation.contact;

import java.time.Instant;

record LinkReport(int noradId, Instant passStart, Instant passEnd, long framesReceived, long framesLost,
                  long framesFailedCheck, long idleFrames, Instant updatedAt) {

    double lossRatio() {
        final var expected = framesReceived + framesLost;
        return expected == 0 ? 0 : (double) framesLost / expected;
    }
}
