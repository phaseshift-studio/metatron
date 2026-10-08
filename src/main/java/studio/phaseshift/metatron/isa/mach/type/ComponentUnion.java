package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.util.CommonUtil;

/**
 * A linked-list union of a machine component across the frame chain.
 * <p>
 * A component (memory, instset, network, compiler, processor) is the atomic unit of isolation. A child frame's
 * component is a union of {@link #current()} (the level the frame introduced) and {@link #previous()} (the
 * enclosing level), and {@code previous()} may itself be a union — so a walk recurses to the root. The union is
 * realized by the concrete component's own read/write (a space knows what {@code hasSpaceFor} means; this type
 * deliberately says nothing about it), while this interface only states the chain and how to release it.
 *
 * @param <T> the component kind being unioned
 */
public interface ComponentUnion<T extends Machine.Component> {

    /** The enclosing level's component, or null at the root. */
    T previous();

    /** This level's own component — null until the frame first introduces something locally. */
    T current();

    /**
     * Release only what this level introduced. The enclosing level is not ours to close: it is still live above
     * us, so closing it here would collect the parent's resources by mistake.
     */
    default void close() {
        CommonUtil.close(this.current());
    }
}
