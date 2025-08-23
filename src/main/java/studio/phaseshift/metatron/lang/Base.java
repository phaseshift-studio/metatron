package studio.phaseshift.metatron.lang;

import org.javatuples.Quartet;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface Base {

    interface Obj extends Function<Obj, Obj> {
        Object value();

        @Override
        default Obj apply(final Obj other) {
            return this;
        }
    }

    interface Mono extends Obj {
    }

    interface Poly extends Obj {
    }

    interface Bool extends Mono {
        @Override
        Boolean value();

        @Override
        default Bool apply(final Obj other) {
            return this;
        }
    }

    interface Int extends Mono {
        @Override
        Integer value();

        @Override
        default Int apply(final Obj other) {
            return this;
        }
    }

    interface Real extends Mono {
        @Override
        Double value();

        @Override
        default Real apply(final Obj other) {
            return this;
        }
    }

    interface Str extends Mono {
        @Override
        String value();

        @Override
        default Str apply(final Obj other) {
            return this;
        }
    }

    interface Uri extends Mono {
        @Override
        URI value();

        @Override
        default Uri apply(final Obj other) {
            return this;
        }
    }

    interface Lst extends Poly {
        @Override
        List<Obj> value();

        @Override
        Lst apply(final Obj other);
    }

    interface Rec extends Poly {
        @Override
        Map<Obj, Obj> value();

        @Override
        Rec apply(final Obj other);
    }

    interface Inst extends Poly {
        @Override
        Quartet<Uri, Lst, BiFunction<Obj, Lst, Obj>, Obj> value();

        @Override
        default Obj apply(final Obj other) {
            final int size = this.value().getValue1().value().size();
            final List<Obj> args = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                args.set(i, this.value().getValue1().value().get(i).apply(other));
            }
            return this.value().getValue2().apply(other, this.value().getValue1());
        }

    }
}
