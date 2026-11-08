package lt.vsh1ft.simulator;

final class WrappingCounter {

    private final int max;
    private int next;

    WrappingCounter(int max) {
        this.max = max;
    }

    int next() {
        final var current = next;
        next = current == max ? 0 : current + 1;
        return current;
    }
}
