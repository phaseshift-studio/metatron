package studio.phaseshift.metatron.lang.obj;

import java.util.Objects;

public interface C<T extends Comparable<T>, D extends C<T, D>> extends Comparable<D> {

    T min();

    T max();

    D neg();

    D plus(final D rhs);

    D mult(final D rhs);

    default D minus(final D rhs) {
        return this.plus(rhs.neg());
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean within(final D rhs);

    default boolean lt(final D rhs) {
        return this.compareTo(rhs) < 0;
    }

    default boolean gt(final D rhs) {
        return this.compareTo(rhs) > 0;
    }

    default boolean lte(final D rhs) {
        return this.compareTo(rhs) <= 0;
    }

    default boolean gte(final D rhs) {
        return this.compareTo(rhs) >= 0;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////

    D any();

    D some();

    D zero();

    D one();

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
