package lt.vsh1ft.groundstation.shared;

import java.util.List;
import java.util.function.Function;

public record CursorPage<T>(List<T> items, Long nextBefore) {

    public CursorPage {
        items = List.copyOf(items);
    }

    public static <T> CursorPage<T> of(List<T> foundWithOneMore, int limit, Function<T, Long> id) {
        final var items = foundWithOneMore.stream().limit(limit).toList();
        return new CursorPage<>(items, foundWithOneMore.size() > limit ? id.apply(items.getLast()) : null);
    }

    public <R> List<R> map(Function<T, R> mapper) {
        return items.stream().map(mapper).toList();
    }
}
