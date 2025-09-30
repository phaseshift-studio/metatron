package studio.phaseshift.metatron.lang.obj;

import org.javatuples.Pair;

import java.util.Objects;

public interface Coeff<T extends Comparable<T>, C extends Coeff<T, C>> {

    T min();

    T max();

    default Pair<T, T> range() {
        return Pair.with(this.min(), this.max());
    }

    C plus(final C rhs);

    C mult(final C rhs);

    boolean isOne();

    boolean isAny();

    boolean isSome();

    boolean isMaybe();

    boolean isZero();

    boolean isNoObjable();

    default boolean isExact() {
        return Objects.equals(this.min(), this.max());
    }

    boolean within(final C rhs);
}
