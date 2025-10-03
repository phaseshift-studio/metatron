package studio.phaseshift.metatron.lang.obj;

import java.util.Objects;

public interface Coeff<T extends Comparable<T>, C extends Coeff<T, C>> extends Comparable<C> {

    T min();

    T max();

    C neg();

    C plus(final C rhs);

    C mult(final C rhs);

    default C minus(final C rhs) {
        return this.plus(rhs.neg());
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean within(final C rhs);

    default boolean lt(final C rhs) {
        return this.compareTo(rhs) < 0;
    }

    default boolean gt(final C rhs) {
        return this.compareTo(rhs) > 0;
    }

    default boolean lte(final C rhs) {
        return this.compareTo(rhs) <= 0;
    }

    default boolean gte(final C rhs) {
        return this.compareTo(rhs) >= 0;
    }


    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean isNeg();

    default boolean isPos() {
        return !this.isNeg();
    }

    boolean isZeroOrNeg();

    boolean isOne();

    boolean isAny();

    boolean isSome();

    boolean isMaybe();

    boolean isZero();

    boolean isNoObjable();

    default boolean isExact() {
        return Objects.equals(this.min(), this.max());
    }
}
