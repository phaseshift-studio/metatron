package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.ComponentUnion;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.REFERENCE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The machine's OWN instset: instruction memory, and the one machine component that is a FACADE as well as a union.
 * <p>
 * It is a UNION because a child's vocabulary is its own level over the enclosing machine's — the same shape as
 * {@link studio.phaseshift.metatron.isa.mach.type.memory.MemoryUnion}, and for the same reason: a component belongs
 * to the machine that holds it, so a write made in a child lands in the child's level and the enclosing machine's
 * vocabulary is untouched. What is unioned here is {@link #previous()}; see {@link ComponentUnion}.
 * <p>
 * It is a FACADE because the instruction sets it uses are stored in Memory (imported ISAs are spaces), and a user
 * should not have to wade through a sea of spaces looking for instsets and then search each of those for the items of
 * interest. The own structure plus the ordered {@code reference} list plus {@link #previous()} are the single locus:
 * every read and every kind accessor aggregates them, so "what are my insts, rewrites, sugars and types" is one
 * question asked in one place.
 * <p>
 * Resolution is OWN-FIRST — this level's structure, then the imported instsets in import order, then the enclosing
 * machine's — so a name written here SHADOWS the same name from any instset this one refers to, and among the
 * references IMPORT ORDER IS PRECEDENCE. Writing only ever reaches the own structure, which is what makes an imported
 * library safe to share. (Precedence: own → imports → previous.)
 * <p>
 * <b>The references live IN THE REC, under {@code reference}, and that is what makes this union reflective.</b> The
 * rec holds the referred <em>vids</em> in import order — a vid rather than the object, so that printing the rec never
 * inlines a whole ISA and an import order is legible to mtron. The OBJECT is held beside it (a map on this level), so
 * <em>resolution</em> does not depend on the data index: instruction memory can be a member of a machine without
 * being one of its spaces. Both are written by {@link #refer(InstSet)}, the only way in, so they cannot drift.
 */
public class BasicInstSet extends AbstractInstSet implements ComponentUnion<InstSet> {

    private InstSet previous = null;

    public BasicInstSet() {
        super(false);
        // the jurisdiction is the references' (see pattern()), never the "#" a bare instset is built with: a level
        // that refers nothing reports its enclosing machine's, through at()'s walk up the chain
        this.jvm().remove(uri(PATTERN));
    }

    /**
     * A level over the enclosing machine's instset.
     */
    public BasicInstSet(final InstSet previous) {
        this();
        this.previous = previous;
    }

    /**
     * THE VOCABULARY'S JURISDICTION: the "lowest common denominator" of the patterns of the instruction sets it refers
     * to (Marko, 2026-10-25: "I would set the pattern to the 'lowerst common denominator' uri. thus /m/#") — the
     * deepest path every member's pattern lies beneath, written into the rec by {@link #refer(InstSet)}. A level that
     * refers nothing answers its enclosing machine's ({@link #at(Obj)} walks the chain), and a machine with no
     * references at all answers the bare {@code #} it was built with.
     */
    @Override
    public fURI pattern() {
        final Obj pattern = this.at(uri(PATTERN));
        return pattern.isUri() ? pattern.uriValue() : super.pattern();
    }

    /**
     * the deepest path all of {@code members}' patterns lie beneath, as a pattern ({@code /m/#} for /m, /m/math, …;
     * {@code /#} once a member lies elsewhere) — a scheme or a host shares no path with the rest, so it widens the
     * jurisdiction to everything
     */
    private static fURI commonPattern(final List<InstSet> members) {
        List<String> common = null;
        for (final InstSet member : members) {
            final fURI pattern = member.pattern();
            if (null == pattern || pattern.hasScheme() || pattern.hasHost())
                return ALL;
            final List<String> segments = pattern.retractPattern().segments();
            if (null == common)
                common = new ArrayList<>(segments);
            else {
                int shared = 0;
                while (shared < common.size() && shared < segments.size() && common.get(shared).equals(segments.get(shared)))
                    shared++;
                common = new ArrayList<>(common.subList(0, shared));
            }
        }
        return null == common ? ALL : f("/" + String.join("/", common)).extend(ALL);
    }

    // ======================== the frame chain ========================

    /** This level's own component — the ISA itself, whose structure is the level. */
    @Override
    public InstSet current() {
        return this;
    }

    /** The enclosing machine's instset, or null at the root. */
    @Override
    public InstSet previous() {
        return this.previous;
    }

    public BasicInstSet previous(final InstSet previous) {
        this.previous = previous;
        return this;
    }

    /**
     * RESOLVE a name: this level's table first, then the enclosing machine's — the frame law applied to names, so a
     * child that imports its own shorthand shadows the parent's and every miss still walks outward. A name is
     * knowledge, not reach: nothing is retracted from the level above, and a popped frame takes only its own names.
     */
    @Override
    public fURI alignPrefix(final fURI vid) {
        final fURI aligned = super.alignPrefix(vid);
        if (!aligned.equals(vid.one()))
            return aligned;
        return null == this.previous ? aligned : this.previous.alignPrefix(vid);
    }

    /**
     * Refer to an instset out in space — an import, held by reference and never copied. Records the vid in the rec's
     * ordered {@code reference}, so import order IS the rec's order.
     * <p>
     * <b>The entry is WRITTEN, not {@code at(..., MUTABLE)}-saved.</b> {@code Rec.at} APPENDS when the entry already
     * holds an {@code Objs}, and the payload here is the whole membership — so saving it added every member's
     * coefficient to itself and DOUBLED them on every import (measured: 1, then {2}, then {4,2}, then {4,2,1}; i.e.
     * the first import reaches 2^(n-2) after n imports, which is the 1024 in a twelve-import boot). A direct put
     * REPLACES, which is what a set of vids wants.
     * <p>
     * Members are carried at coefficient ONE for the same reason: a multiplicity's coefficients are its cardinality
     * (two {@code /m/math} is {@code {2}/m/math}), and cardinality is meaningful for data and meaningless for
     * membership.
     */
    public BasicInstSet refer(final InstSet isa) {
        if (null == isa || isa == this || null == isa.vid())
            return this;
        final fURI vid = isa.vid();
        if (this.referVids().stream().anyMatch(reference -> reference.isUri() && reference.uriValue().equals(vid))) {
            // ALREADY A MEMBER — REFRESH THE OBJECT. A re-mount (a new instance of the same ISA, which is what a test
            // fixture, a reload, or a second `import` does) must resolve to the ISA now in hand rather than to
            // whatever the first refer recorded: the map is what resolution reads, so leaving a retired instance in
            // it would keep answering from an ISA that has been replaced.
            this.imports.put(vid.toString(), isa);
            this.unrouted.remove(vid.toString());
            this.ownLibraries = null;
            return this;
        }
        final List<Obj> referred = new ArrayList<>();
        this.ownReferVids().forEach(reference -> referred.add(reference.c(cInt::one)));
        referred.add(uri(vid.toString()));
        // a MULTIPLICITY, not a list: an Objs streams its members, collapses one member to that member and an
        // empty one to noobj — so the reads below cover one reference and many with the same code
        this.jvm().put(uri(REFERENCE), objs(referred.toArray(new Obj[0])));
        // ... AND THE OBJECT BESIDE THE VID. The rec is what mtron reads (import order, precedence, and no inlining
        // when the ISA is printed); this map is what resolution USES, so an import resolves without the data index —
        // which is what lets an instruction set be a member of a machine without being one of its spaces. Both are
        // written here, the only way in, so they cannot drift.
        this.imports.put(vid.toString(), isa);
        this.unrouted.remove(vid.toString());
        this.ownLibraries = null;
        // the jurisdiction follows the membership: the deepest path every referred ISA lies beneath
        this.jvm().put(uri(PATTERN), uri(commonPattern(this.references())));
        return this;
    }

    /**
     * the vids this level referred and memory has since UNMOUNTED ({@code removeSpace}): still members — the union
     * reads them (an unmounted instruction set is still a member and answers, {@code InstSetUnionReadTest}) — but no
     * longer routed by address, exactly as removing an ISA from memory's index used to leave it. A re-mount clears it.
     */
    private final Set<String> unrouted = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile List<InstSet> ownLibraries = null;

    /**
     * THE LIBRARIES THIS LEVEL ROUTES — the instruction sets this level referred that hold a jurisdiction of their own
     * ({@link #isLibrary}) and are mounted. Memory's lookup weighs them against the data spaces of the memory level of
     * the same machine (Marko, 2026-10-25: instruction sets live in the instset, not in memory's index): an ISA is
     * routed where it would have been indexed, by the frame that mounted it, so a child's memory level does not
     * claim its parent's libraries ahead of the parent's own, more specific, data spaces. Cached until this level
     * refers or un-routes.
     */
    public List<InstSet> ownLibraries() {
        final List<InstSet> cached = this.ownLibraries;
        if (null != cached)
            return cached;
        final List<InstSet> fresh = this.ownReferences().stream()
                .filter(isa -> isLibrary(isa) && !this.unrouted.contains(isa.vid().toString()))
                .toList();
        this.ownLibraries = fresh;
        return fresh;
    }

    /**
     * UNMOUNT: {@code vid}, if this level referred it, stops being routed by address and stays a member — what
     * {@code memory().removeSpace(vid)} did to a mounted instruction set while ISAs were indexed in memory.
     */
    public void unroute(final fURI vid) {
        if (this.ownReferVids().stream().anyMatch(reference -> reference.isUri() && reference.uriValue().equals(vid))) {
            this.unrouted.add(vid.toString());
            this.ownLibraries = null;
        }
    }

    /**
     * Does {@code isa} hold a jurisdiction of its own — a vid and a path its pattern names? A frame's own level (a
     * {@link ComponentUnion}, the vocabulary's facade) does not, nor does an ISA whose pattern is everything
     * ({@code #}, {@code /#}): that is pure vocabulary, reached through the union read, and must never outrank memory
     * for an address it does not name.
     */
    public static boolean isLibrary(final InstSet isa) {
        return !(isa instanceof ComponentUnion<?>) && null != isa.vid() && null != isa.pattern()
                && !isa.pattern().retractPattern().segments().isEmpty();
    }

    /** The imported ISAs, by vid — see {@link #refer(InstSet)}. Resolution reads THIS; the rec is the reflection. */
    private final Map<String, InstSet> imports = new LinkedHashMap<>();

    /**
     * The referred vids THIS level recorded, in import order — read straight out of the rec, NEVER through
     * {@link #at(Obj)}: this is the one accessor that must stay own-only, or a level would report its parent's
     * imports as its own. No shape inspection: a multiplicity streams as one or as many, which is the whole reason
     * it is stored as one.
     */
    public List<Obj> ownReferVids() {
        return super.atDirect(uri(REFERENCE)).stream().filter(Obj::isUri).toList();
    }

    /**
     * A REC-KEY READ WALKS THE FRAME CHAIN, own-first — the contract every other component union has
     * ({@code MemoryUnion} answers own then {@code previous}). Without it the membership was not reflectively
     * readable inside a frame: {@code *~/instset/reference} was {@code noobj} in a child even though that child
     * resolved every ISA its parent had imported, so the single locus went unaskable in the one place a frame makes
     * it matter. The rec stays LAZY — nothing is copied up front — so an import the parent makes later is still seen.
     */
    @Override
    public <O extends Obj> O at(final Obj key) {
        final O own = super.at(key);
        return own.isNoObj() && this.previous instanceof BasicInstSet enclosing ? enclosing.at(key) : own;
    }

    @Override
    public <O extends Obj> O atDirect(final Obj key) {
        final O own = super.atDirect(key);
        return own.isNoObj() && this.previous instanceof BasicInstSet enclosing ? enclosing.atDirect(key) : own;
    }

    /**
     * The referred vids the union knows: this level's, then the enclosing machine's (which excludes any this level
     * already recorded). The facade's membership, and what makes {@code reference} answer for a child that imported
     * nothing itself.
     */
    public List<Obj> referVids() {
        final List<Obj> vids = new ArrayList<>(this.ownReferVids());
        if (this.previous instanceof BasicInstSet enclosing)
            for (final Obj vid : enclosing.referVids())
                if (vids.stream().noneMatch(known -> known.isUri() && vid.isUri() && known.uriValue().equals(vid.uriValue())))
                    vids.add(vid);
        return List.copyOf(vids);
    }

    /**
     * The referred instsets, resolved in import order — THE UNION'S, not this level's: a child that imported nothing
     * itself still uses every ISA its enclosing machine referred, so resolving only {@code ownReferVids()} left
     * {@code references()} disagreeing with {@link #referVids()} (measured: 0 vs 4 in a pushed child) while
     * {@link #read(fURI)} happily resolved through the parent. A vid that no longer resolves is skipped rather than
     * thrown: a reference outlives the thing it names only in a torn-down world, and a broken reference must not
     * take resolution with it.
     */
    public List<InstSet> references() {
        return this.resolveAll(this.referVids());
    }

    /**
     * THE ISAS THIS LEVEL IMPORTED — the libraries, not the union's whole list. The tree order comes from walking
     * {@link #previous}, so flattening the enclosing machine's imports here would place them AHEAD of that
     * machine's own structure; {@link #references()} (the union's, for reflection) is the list to publish, this one
     * is the list to walk.
     */
    private List<InstSet> ownReferences() {
        return this.resolveAll(this.ownReferVids());
    }

    private List<InstSet> resolveAll(final List<Obj> vids) {
        final List<InstSet> references = new ArrayList<>();
        for (final Obj reference : vids)
            if (reference.isUri()) {
                final InstSet isa = resolve(reference.uriValue());
                if (null != isa)
                    references.add(isa);
            }
        return List.copyOf(references);
    }

    /**
     * The ISA a referred vid names. RESOLUTION IS BY OBJECT FIRST — every reference this level recorded is held as
     * the ISA itself (see {@link #refer(InstSet)}), so membership never requires the data index: an instruction set
     * can be a member of a machine without being one of its spaces. The index walk is the FALLBACK, for a reference
     * that only ever had a vid — a persisted ISA, or a level built before the object was held.
     */
    private InstSet resolve(final fURI vid) {
        final InstSet imported = this.imports.get(vid.toString());
        if (null != imported)
            return imported;
        // AN INHERITED REFERENCE IS HELD BY THE LEVEL THAT MADE IT: a child answers its enclosing machine's imports
        // with the objects that level holds. (The data index below used to answer them too, while ISAs were mounted
        // there; instruction sets are not in memory any more, so the chain is where the objects are.)
        if (this.previous instanceof BasicInstSet enclosing) {
            final InstSet inherited = enclosing.resolve(vid);
            if (null != inherited)
                return inherited;
        }
        for (Memory memory = Machine.current().memory(); null != memory; memory = memory.previous()) {
            final Obj space = memory.spaces().at(uri(vid.toString()));
            if (space instanceof InstSet isa)
                return isa;
        }
        return null;
    }

    /**
     * THE UNION READ: the tree is WALKED and candidates ACCUMULATE — this level's structure, then the ISAs this
     * level imported (in import order), then the enclosing machine, recursively. A nearer candidate wins <b>only
     * when it names the same thing</b>: an inst's identity is its {@code tid}, a type's or a const's is its
     * {@code vid}. So a frame that declares its own {@code plus} for a new domain ADDS a candidate — the library's
     * other {@code plus} contracts survive, which is what an overloaded name needs — while a frame that re-declares
     * the very contract the library already has SHADOWS it.
     * <p>
     * This is deliberately NOT "the first level that answers, wins": that would make a local overload erase every
     * imported overload of the same name, and resolution is a question about candidates, not about levels.
     */
    @Override
    public Obj read(final fURI pattern) {
        return this.read(pattern, new LinkedHashSet<>(), false);
    }

    /**
     * The union walked with {@link InstSet#readDeclared(fURI)} — the form a FALLBACK read uses (the machine's
     * instset hop), so asking the vocabulary after a data-memory miss cannot trigger a member's lazy
     * materialization.
     */
    @Override
    public Obj readDeclared(final fURI pattern) {
        return this.read(pattern, new LinkedHashSet<>(), true);
    }

    /**
     * The walk, carrying the ISAs already on the stack — an import graph with a cycle in it answers nothing rather
     * than recursing forever. (A frame's {@code previous} chain is a tree; the imports are libraries, and a cycle
     * among them is a wiring mistake, not something to hang on.)
     */
    private Obj read(final fURI pattern, final Set<InstSet> visiting, final boolean declared) {
        if (!visiting.add(this))
            return noobj();
        try {
            final List<Obj> candidates = new ArrayList<>();
            final Set<String> seen = new LinkedHashSet<>();
            this.gather(pattern, candidates, seen, visiting, declared);
            // A SHORT NAME IS AN ADDRESS IN DISGUISE. Memory's small→big tables are what turn `plus` into
            // `/m/inst/plus`, and an ISA's tables are keyed by the FULL address — so asking only the pattern as
            // written answered full names and nothing else. Ask the routed spelling too (identity dedupe collapses
            // the overlap). Routing names stays Memory's job: a name is not something an ISA resolves about itself.
            final fURI routed = Machine.current().memory().redirect(pattern, true);
            if (!routed.equals(pattern))
                this.gather(routed, candidates, seen, visiting, declared);
            return objs(candidates);
        } finally {
            visiting.remove(this);
        }
    }

    private void gather(final fURI pattern, final List<Obj> candidates, final Set<String> seen,
                        final Set<InstSet> visiting, final boolean declared) {
        this.gather(super.read(pattern), candidates, seen);
        for (final InstSet isa : this.ownReferences())
            this.gather(declared
                            ? (isa instanceof BasicInstSet basic ? basic.read(pattern, visiting, true) : isa.readDeclared(pattern))
                            : (isa instanceof BasicInstSet basic ? basic.read(pattern, visiting, false) : isa.read(pattern)),
                    candidates, seen);
        if (null != this.previous)
            this.gather(this.previous instanceof BasicInstSet basic
                            ? basic.read(pattern, visiting, declared)
                            : (declared ? this.previous.readDeclared(pattern) : this.previous.read(pattern)),
                    candidates, seen);
    }

    /** Take a level's candidates, dropping any whose identity a nearer level has already answered. */
    private void gather(final Obj from, final List<Obj> candidates, final Set<String> seen) {
        if (null == from || from.isNoObj())
            return;
        from.stream().forEach(candidate -> {
            final fURI identity = identity(candidate);
            if (null == identity || seen.add(identity.toString()))
                candidates.add(candidate);
        });
    }

    /**
     * WHAT A CANDIDATE <em>IS</em>: an inst is its tid (its dom/rng contract — two overloads of one name are two
     * candidates), a type or a const is its vid. A candidate carried as a rel is judged by the obj inside it,
     * because a non-node read wraps each hit as {@code uri => obj}.
     */
    private static fURI identity(final Obj candidate) {
        final Obj obj = candidate.isRel() ? candidate.asRel().second() : candidate;
        return obj.isInst() ? obj.tid() : obj.vid();
    }

    /** Writes reach only this machine's own structure: the enclosing level and the libraries are read-only here. */
    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return super.write(vid, obj);
    }

    /**
     * Every instruction set this machine's vocabulary is composed of: this level, the ISAs it imported (in import
     * order), then the enclosing machine's levels, recursively — each ISA once, nearest first. The walk carries
     * the visiting set the union read carries, and for the same reason: an import cycle answers a shorter list
     * rather than recursing forever.
     */
    @Override
    public List<InstSet> members() {
        final List<InstSet> members = new ArrayList<>();
        this.members(members, new LinkedHashSet<>());
        return List.copyOf(members);
    }

    private void members(final List<InstSet> members, final Set<InstSet> visiting) {
        if (!visiting.add(this))
            return;
        members.add(this);
        for (final InstSet isa : this.ownReferences())
            if (isa instanceof BasicInstSet basic)
                basic.members(members, visiting);
            else if (visiting.add(isa))
                members.add(isa);
        if (null != this.previous) {
            if (this.previous instanceof BasicInstSet basic)
                basic.members(members, visiting);
            else if (visiting.add(this.previous))
                members.add(this.previous);
        }
    }

    // ---- the kind accessors merge by the SAME identity rule, so enumeration sees what resolution sees ----

    @Override
    public Set<Obj> consts() {
        return this.merge(super.consts(), InstSet::consts, Obj::vid);
    }

    @Override
    public Set<Type> types() {
        return this.merge(super.types(), InstSet::types, Type::vid);
    }

    @Override
    public Set<Inst> insts() {
        return this.merge(super.insts(), InstSet::insts, Inst::tid);
    }

    @Override
    public Set<Inst> rewrites() {
        return this.merge(super.rewrites(), InstSet::rewrites, Inst::tid);
    }

    /**
     * Own entries first, then each import's, then the enclosing machine's — dropping an entry whose IDENTITY is
     * already answered by a nearer level. Insts are identified by tid and types/consts by vid, which is the same
     * rule {@link #read(fURI)} applies per candidate: an overloaded name keeps every distinct contract, and a
     * re-declared one keeps only the nearest.
     */
    private <T> Set<T> merge(final Set<T> own, final java.util.function.Function<InstSet, Set<T>> of,
                             final java.util.function.Function<T, fURI> identityOf) {
        final Set<T> merged = new LinkedHashSet<>(own);
        final Set<fURI> seen = new LinkedHashSet<>();
        own.forEach(t -> {
            final fURI id = identityOf.apply(t);
            if (null != id) seen.add(id);
        });
        for (final InstSet isa : this.ownReferences())
            this.mergeFrom(merged, seen, of.apply(isa), identityOf);
        if (null != this.previous)
            this.mergeFrom(merged, seen, of.apply(this.previous), identityOf);
        return merged;
    }

    private <T> void mergeFrom(final Set<T> merged, final Set<fURI> seen, final Set<T> entries,
                               final java.util.function.Function<T, fURI> identityOf) {
        for (final T t : entries) {
            final fURI id = identityOf.apply(t);
            if (null == id || seen.add(id))
                merged.add(t);
        }
    }
}
