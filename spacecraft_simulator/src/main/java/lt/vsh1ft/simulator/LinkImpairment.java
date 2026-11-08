package lt.vsh1ft.simulator;

import java.util.Optional;
import java.util.random.RandomGenerator;

import lt.vsh1ft.ccsds.FrameSynchronizer;

final class LinkImpairment {

    private final double dropRate;
    private final double corruptRate;
    private final RandomGenerator random;

    LinkImpairment(double dropRate, double corruptRate, RandomGenerator random) {
        this.dropRate = dropRate;
        this.corruptRate = corruptRate;
        this.random = random;
    }

    Optional<byte[]> apply(byte[] cadu) {
        if (random.nextDouble() < dropRate) {
            return Optional.empty();
        }
        if (random.nextDouble() < corruptRate) {
            final var damaged = cadu.clone();
            final var bit = random.nextInt(FrameSynchronizer.MARKER_LENGTH * Byte.SIZE, cadu.length * Byte.SIZE);
            damaged[bit / Byte.SIZE] ^= (byte) (1 << bit % Byte.SIZE);
            return Optional.of(damaged);
        }
        return Optional.of(cadu);
    }
}
