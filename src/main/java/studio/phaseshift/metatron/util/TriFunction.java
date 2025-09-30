package studio.phaseshift.metatron.util;

import java.util.Objects;
import java.util.function.Function;

public interface TriFunction<A, B, C, R> {
    R apply(final A a, final B b, final C c);

    default <V> TriFunction<A, B, C, V> andThen(final Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (a, b, c) -> after.apply(this.apply(a, b, c));
    }
}

