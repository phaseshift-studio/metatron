package studio.phaseshift.metatron.lang.obj;

import org.javatuples.Pair;

public interface Cardinality<A> {

    A min();

    A max();

    default Pair<A,A> range() {
        return Pair.with(this.min(),this.max());
    }

    Cardinality<A> plus(final Cardinality<A> rhs);
    Cardinality<A> mult(final Cardinality<A> rhs);

    boolean isOne();

    boolean isStar();

    boolean isPlus();

    boolean isQuestion();
}
