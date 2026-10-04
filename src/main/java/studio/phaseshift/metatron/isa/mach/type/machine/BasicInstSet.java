package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * The machine's OWN instset: an N-ARY union, unlike every other Machine component.
 * <p>
 * Memory and the network are BINARY — a frame's level over the parent machine's, a fixed two-level tree walked
 * upward. An ISA is a LIST: an arbitrary number of imported instsets held BY REFERENCE, with this machine's own
 * structure layered over them. Two consequences follow, and they are the whole point:
 * <ul>
 *     <li><b>an import is a reference</b>, never a copy, so the library instset out in space is shared by every
 *     machine that imported it and is never mutated by any of them;</li>
 *     <li><b>resolution is own-first</b>, so a type, inst, const or rewrite written here SHADOWS a name from any
 *     instset this one refers to — and among the references, IMPORT ORDER IS PRECEDENCE.</li>
 * </ul>
 * The order rule is the same one import systems have always had, and it is why the references are an ordered list
 * rather than a set. Writing only ever reaches the own structure here, which is what makes the library safe to
 * share.
 */
public class BasicInstSet extends AbstractInstSet {

    /** Import order is precedence: earlier entries are consulted first, after this machine's own structure. */
    private final List<InstSet> references = new CopyOnWriteArrayList<>();

    public BasicInstSet() {
        super(false);
    }

    /** Refer to an instset out in space — an import, held by reference and never copied. */
    public BasicInstSet refer(final InstSet isa) {
        if (null != isa && isa != this)
            this.references.add(isa);
        return this;
    }

    public List<InstSet> references() {
        return List.copyOf(this.references);
    }

    /** Own structure first — this is what shadows a referenced name — then the references in import order. */
    @Override
    public Obj read(final fURI pattern) {
        final Obj own = super.read(pattern);
        if (!own.isNoObj())
            return own;
        for (final InstSet isa : this.references) {
            final Obj found = isa.read(pattern);
            if (!found.isNoObj())
                return found;
        }
        return noobj();
    }

    /** Writes reach only this machine's own structure: the referenced libraries are read-only by construction. */
    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return super.write(vid, obj);
    }

    // ---- the kind accessors merge, own-first, so enumeration sees what resolution sees ----

    @Override
    public Set<Obj> consts() {
        return this.merge(super.consts(), isa -> isa.consts(), Obj::vid);
    }

    @Override
    public Set<Type> types() {
        return this.merge(super.types(), isa -> isa.types(), Type::vid);
    }

    @Override
    public Set<Inst> insts() {
        return this.merge(super.insts(), isa -> isa.insts(), Inst::vid);
    }

    @Override
    public Set<Inst> rewrites() {
        return this.merge(super.rewrites(), isa -> isa.rewrites(), Inst::vid);
    }

    /**
     * Own entries first, then each reference's entries, dropping any whose vid is already shadowed.
     */
    private <T> Set<T> merge(final Set<T> own, final java.util.function.Function<InstSet, Set<T>> of,
                             final java.util.function.Function<T, fURI> vidOf) {
        final Set<T> merged = new LinkedHashSet<>(own);
        final Set<fURI> seen = new LinkedHashSet<>();
        own.forEach(t -> { final fURI v = vidOf.apply(t); if (null != v) seen.add(v); });
        for (final InstSet isa : this.references)
            for (final T t : of.apply(isa)) {
                final fURI v = vidOf.apply(t);
                if (null == v || seen.add(v))
                    merged.add(t);
            }
        return merged;
    }
}
