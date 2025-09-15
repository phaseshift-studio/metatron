package studio.phaseshift.metatron.lang.obj;

import org.javatuples.Pair;

public interface Coeff<T extends Comparable<T>, C extends Coeff<T,C>> {

    T min();

    T max();

    default Pair<T, T> range() {
        return Pair.with(this.min(), this.max());
    }

   C plus(final C rhs);

   C mult(final C rhs);

    boolean isOne();

    boolean isStar();

    boolean isPlus();

    boolean isQuestion();

    boolean within(final C rhs);
}
