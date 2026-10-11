package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.REFERENCE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

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
 * <p>
 * <b>The references live IN THE REC, under {@code reference}, and that is what makes this union reflective.</b>
 * Held in a Java field the membership was invisible to mtron, so "what are my insts, rewrites, sugars and types"
 * had no answer that did not mean walking a flurry of mounts. The rec holds the referred <em>vids</em> in import
 * order — a vid rather than the object, so that referring never re-parents the library into this machine and
 * reading the rec never inlines a whole ISA. Resolution looks each vid up in the index that holds it.
 */
public class BasicInstSet extends AbstractInstSet {

    public BasicInstSet() {
        super(false);
    }

    /**
     * Refer to an instset out in space — an import, held by reference and never copied. Appends to the rec's
     * ordered {@code reference}, so import order IS the rec's order.
     */
    public BasicInstSet refer(final InstSet isa) {
        if (null == isa || isa == this || null == isa.vid())
            return this;
        final fURI vid = isa.vid();
        final List<Obj> referred = new ArrayList<>(this.referVids());
        if (referred.stream().noneMatch(reference -> reference.isUri() && reference.uriValue().equals(vid))) {
            referred.add(uri(vid.toString()));
            // a MULTIPLICITY, not a list: an Objs streams its members, collapses one member to that member and an
            // empty one to noobj — so the read below covers one reference and many with the same code
            this.at(uri(REFERENCE), objs(referred.toArray(new Obj[0])), Poly.MUTABLE);
        }
        return this;
    }

    /**
     * The referred vids, in import order — read straight out of the rec, so it is exactly what mtron sees. No
     * shape inspection: a multiplicity streams as one or as many, which is the whole reason it is stored as one.
     */
    public List<Obj> referVids() {
        return this.at(uri(REFERENCE)).stream().filter(Obj::isUri).toList();
    }

    /**
     * The referred instsets, resolved in import order. A vid that no longer resolves is skipped rather than
     * thrown: a reference outlives the thing it names only in a torn-down world, and a broken reference must not
     * take resolution with it.
     */
    public List<InstSet> references() {
        final List<InstSet> references = new ArrayList<>();
        for (final Obj reference : this.referVids())
            if (reference.isUri()) {
                final InstSet isa = resolve(reference.uriValue());
                if (null != isa)
                    references.add(isa);
            }
        return List.copyOf(references);
    }

    /**
     * Resolve a referred vid against the machines in scope, WALKING THE FRAME CHAIN: an ISA is a space, and the
     * index that holds it is keyed by vid. The walk is what lets a child resolve a reference it inherited rather
     * than only one it made itself.
     */
    private static InstSet resolve(final fURI vid) {
        for (Memory memory = Machine.current().memory(); null != memory; memory = memory.previous()) {
            final Obj space = memory.spaces().at(uri(vid.toString()));
            if (space instanceof InstSet isa)
                return isa;
        }
        return null;
    }

    /** Own structure first — this is what shadows a referenced name — then the references in import order. */
    @Override
    public Obj read(final fURI pattern) {
        final Obj own = super.read(pattern);
        if (!own.isNoObj())
            return own;
        for (final InstSet isa : this.references()) {
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
        for (final InstSet isa : this.references())
            for (final T t : of.apply(isa)) {
                final fURI v = vidOf.apply(t);
                if (null == v || seen.add(v))
                    merged.add(t);
            }
        return merged;
    }
}
