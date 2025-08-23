package studio.phaseshift.metatron.traversal;

import java.util.function.Function;

public interface Source {

    public interface Modulator extends Function<Source, Source> {

    }

    <S, E> Traversal<S, E> traversal();

    Source using(final Modulator modulator);
}
