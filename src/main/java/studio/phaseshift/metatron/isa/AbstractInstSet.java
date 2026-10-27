/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.type.impl.ObjectMap;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.compiler.Typer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public abstract class AbstractInstSet extends AbstractSpace<Map<fURI, Set<? extends Obj>>> implements InstSet {

    protected static final String[] EMPTY_STRING_ARRAY = new String[0];

    /// /////////////////////////////////////////////////////////////////////////////////////////

    protected final Map<fURI, Set<Inst>> INST_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());
    protected final Map<fURI, Type> TYPE_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());
    protected final Map<fURI, Obj> CONST_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());
    protected final Map<fURI, Inst> REWRITE_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());

    // ======================== the names ========================

    /**
     * THE NAMES this vocabulary answers to: {@code <short scheme> => <vid>} — what {@code import(/m/math, math)}
     * binds, and the reason {@code math:cos} denotes {@code /m/math/inst/cos}.
     * <p>
     * It lives with the ISA because a MOUNT is what gave the name: an instruction set owns the shorthand it was
     * imported under the way it owns its consts and insts, and memory mounts nothing and names nothing.
     * <p>
     * A Java field, with the rec's {@code prefix} entry as its MIRROR, written on every change so
     * {@code *</instset/prefix>} answers the relation the resolver actually uses. The mirror is a write, never the
     * source: the read path consults this map, exactly as memory's redirect tables are consulted rather than their
     * (absent) recs.
     */
    protected final ObjectMap<fURI, fURI> PREFIX_TABLE = new ObjectMap<>();

    /**
     * BIND a short name to a vid. Bound once, on purpose — a rebind to a DIFFERENT vid is refused, because every
     * existing spelling of that name would silently come to mean something else.
     */
    @Override
    public void registerPrefix(final fURI prefix, final fURI vid) {
        final fURI existing = this.PREFIX_TABLE.getRaw(prefix);
        if (null != existing && !Objects.equals(vid, existing))
            throw MTronException.of("%s prefix already bound: %s + %s", prefix, vid, existing);
        this.PREFIX_TABLE.putRaw(prefix, vid);
        this.at(uri(PREFIX), this.PREFIX_TABLE.toRec(), MUTABLE);
        LOG.info("prefix {{b}}%s {{g}}=> {{b}}%s{{X}} registered", prefix, vid);
    }

    /**
     * UNBIND a short name — the deletion side of {@link #registerPrefix}, and the universal close applied to a name:
     * after it the spelling denotes nothing rather than denoting somewhere else.
     */
    @Override
    public void unregisterPrefix(final fURI prefix) {
        this.PREFIX_TABLE.computeRaw(prefix, (k, v) -> null);
        // THE FIELD GOES WHEN THE LAST NAME DOES, rather than standing as an empty rec that says "names were here"
        this.at(uri(PREFIX), this.PREFIX_TABLE.isEmpty() ? noobj() : this.PREFIX_TABLE.toRec(), MUTABLE);
        LOG.info("prefix {{b}}%s{{X}} unregistered", prefix);
    }

    /**
     * RESOLVE a prefixed spelling: {@code math:cos} becomes {@code /m/math/inst/cos}.
     * <p>
     * The prefix supplies the ROOT and the short name supplies the rest, but the rest is not simply appended: the
     * name {@code cos} has its own home inside the vocabulary ({@code /m/math/inst/cos}, not {@code /m/math/cos}), and
     * only memory's redirect table knows where. So the candidates are asked for and filtered to those lying UNDER the
     * prefix's root — a candidate from another vocabulary is not this prefix's to place — and only when none lies
     * there does the name extend naively under the root.
     */
    @Override
    public fURI alignPrefix(final fURI vid) {
        final fURI readableVID = vid.one();
        if (!readableVID.hasScheme())
            return readableVID;
        final fURI prefixed = this.PREFIX_TABLE.getRaw(f(readableVID.scheme()));
        if (null == prefixed)
            return readableVID;
        final fURI suffix = readableVID.scheme(null);
        // THE REDIRECT TABLE IS MEMORY'S, and this is a question for it rather than a copy of it: the winner
        // redirect() would return answers "where does this name live", which is the wrong question when the asker
        // already knows the root it must live under.
        final Machine current = Machine.current();
        final Set<fURI> routes = null == current ? Set.of() : current.memory().redirects(suffix);
        final java.util.Optional<fURI> target = routes.stream()
                .filter(r -> r.hasPrefix(prefixed.toString()))
                .findFirst();
        if (target.isPresent())
            return target.get().c(suffix.c()).q(suffix.qMap());
        return prefixed.extend(suffix);
    }

    /**
     * Whether some bound prefix denotes an ancestor of {@code target} — see {@code InstSet#hasPrefixFor}.
     */
    @Override
    public boolean hasPrefixFor(final fURI target) {
        for (final Obj value : this.PREFIX_TABLE.values()) {
            if (value.isUri() && target.hasPrefix(value.uriValue()))
                return true;
        }
        return false;
    }

    @Override
    public boolean isResolved(boolean nested) {
        return super.isResolved(nested);
    }

    protected boolean checkPattern(final Obj obj) {
        if (obj.isInst()) {
            if (obj.tid().isAbsolute() && !obj.tid().test(this.pattern())) {
                LOG.debug("migrating inst at {{b}}%s{{X}} to respective instset: %s", obj.tid(), obj);
                return false;
            }
        } else if (null != obj.vid() && obj.vid().isAbsolute() && !obj.vid().test(this.pattern())) {
            LOG.debug("migrating obj at {{b}}%s{{X}} to respective instset: %s", obj.vid(), obj);
            return false;
        }
        return true;
    }

    protected boolean checkDepth(final Obj obj, final fURI requiredPrefix) {
        // TODO: may not want this -- depends on inst set bleed through
        if (false && null != obj.tid() && !obj.tid().hasPrefix(requiredPrefix.toString())) {
            LOG.warn("obj at %s must have prefix at %s: (ignoring) %s", obj.tid(), requiredPrefix, obj);
            return false;
        }
        return true;
    }

    private boolean old = true;

    public AbstractInstSet(final boolean noDocq) {
        super(new LinkedHashMap<>(), mutableMap(
                uri(Tokens.PATTERN), uri(ALL)), INSTSET_TID, null);
        old = false;
    }

    private static Map<Obj, Obj> addDocQ(final Map<Obj, Obj> jvm) {
        if (jvm.containsKey(uri(QPROC))) {
            Obj qprocs = jvm.get(uri(QPROC));
            if (qprocs.isFail() || qprocs.isNoObj())
                jvm.put(uri(QPROC), lst(QCollection.docQ(), QCollection.lawQ()));
            else {
                if (qprocs.lstValue().stream().noneMatch(q -> q.tid().equals(QCollection.DOCQ_TID)))
                    qprocs.lstValue().add(QCollection.docQ());
                if (qprocs.lstValue().stream().noneMatch(q -> q.tid().equals(QCollection.LAWQ_TID)))
                    qprocs.lstValue().add(QCollection.lawQ());
            }
        } else {
            jvm.put(uri(QPROC), lst(QCollection.docQ(), QCollection.lawQ()));
        }
        return jvm;
    }

    public AbstractInstSet(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(), addDocQ(jvm), tid, vid);
        this.sugars().forEach(mParser::addSugar);
        old = false;
    }

    @Override
    public void setup() {
        this.jvm().forEach((k, v) -> {
            if (k.equals(uri(CONST))) {
                v.lstValue().stream()
                        .filter(c -> checkDepth(c, this.tid.extend(CONST)))
                        .forEach(c -> {
                            if (!checkPattern(c))
                                Machine.write(c);
                            else {
                                CONST_TABLE.put(c.vid(), c);
                                Machine.current().memory().registerRedirect(f(c.vid().name()), c.vid());
                            }
                        });
            } else if (k.equals(uri(TYPE))) {
                v.lstValue().stream()
                        .filter(t -> checkDepth(t, this.tid))
                        .filter(t -> !Objects.isNull(t))
                        .forEach(t -> {
                            if (!checkPattern(t))
                                Machine.write(t);
                            else {
                                TYPE_TABLE.put(t.vid(), t.as());
                                Machine.current().memory().registerRedirect(f(t.vid().name()), t.vid());
                            }
                        });
            } else if (k.equals(uri(INST))) {
                v.lstValue().stream()
                        .filter(i -> checkDepth(i, this.tid.extend(INST)))
                        .forEach(i -> {
                            if (!checkPattern(i))
                                Machine.write(i.tid(), i);
                            else {
                                INST_TABLE.computeIfAbsent(i.tid().basePath(), kk -> new LinkedHashSet<>()).add(i.as());
                                Machine.current().memory().registerRedirect(f(i.tid().name()), i.tid().basePath());
                            }
                        });
            } else if (k.equals(uri(REWRITE))) {
                v.lstValue().stream()
                        .filter(r -> checkDepth(r, this.tid.extend(INST).extend(REWRITE)))
                        .forEach(r -> {
                            if (!checkPattern(r))
                                Machine.write(r.tid(), r);
                            else
                                REWRITE_TABLE.put(r.tid(), r.as());
                        });
            } else if (k.equals(uri(SUGAR))) {
                LOG.warn("unable to load sugar: %s", v);
            }
        });
        Machine.current().memory().write(this.vid(), this);
        // the space is mounted: persist any algebra its types declared in their builder (?lawq)
        this.types().forEach(Type.Builder::drain);
        this.insts().forEach(InstBuilder::drain);
    }

    @Override
    public Set<Obj> consts() {
        return old ? new LinkedHashSet<>() : new LinkedHashSet<>(CONST_TABLE.values());
    }

    @Override
    public Set<Type> types() {
        return old ? new LinkedHashSet<>() : new LinkedHashSet<>(TYPE_TABLE.values());
    }

    @Override
    public Set<Inst> insts() {
        return old ? new LinkedHashSet<>() : new LinkedHashSet<>(INST_TABLE.values().stream().flatMap(Set::stream).toList());
    }

    @Override
    public Set<Inst> rewrites() {
        return old ? new LinkedHashSet<>() : new LinkedHashSet<>(REWRITE_TABLE.values());
    }

    @Override
    public fURI vidToTid(final fURI vid) {
        if (null == vid)
            return null;
        // type registry — the refinement edge (vid → parent tid); base types (vid == tid) have no parent
        final Type type = TYPE_TABLE.get(vid);
        if (null != type) {
            final fURI tid = type.tid();
            return (null == tid || tid.test(type.vid())) ? null : tid;
        }
        // constants — keyed by vid
        final Obj constant = CONST_TABLE.get(vid);
        if (null != constant)
            return constant.tid();
        // rewrites — keyed by tid
        final Inst rewrite = REWRITE_TABLE.get(vid);
        if (null != rewrite)
            return rewrite.tid();
        // instructions — keyed by basePath
        final Set<Inst> insts = INST_TABLE.get(vid.basePath());
        if (null != insts)
            for (final Inst inst : insts)
                if (inst.tid().test(vid))
                    return inst.tid();
        return null;
    }

    @Override
    public void close() {
        this.types().stream().filter(t -> t.vid() != null).filter(t -> t.vid().test(this.pattern())).forEach(t -> Machine.current().memory().unregisterRedirect(f(t.vid().name()), t.vid()));
        this.consts().stream().filter(c -> c.vid() != null).filter(c -> c.vid().test(this.pattern())).forEach(c -> Machine.current().memory().unregisterRedirect(f(c.vid().name()), c.vid()));
        this.insts().stream().filter(i -> i.tid().test(this.pattern())).forEach(i -> Machine.current().memory().unregisterRedirect(f(i.tid().name()), i.tid()));
        this.rewrites().stream().filter(r -> r.tid().test(this.pattern())).forEach(r -> Machine.current().memory().unregisterRedirect(f(r.tid().name()), r.tid()));
        super.close();
    }

    @Override
    public Obj read(final fURI pattern) {
        return this.readTables(pattern);
    }

    /**
     * THE DECLARED READ — this set's tables, and nothing else. A subclass may make {@link #read(fURI)} do
     * more than answer: {@code SQLSchemaInstSet.read} populates a table's schema on demand, which is right
     * for a DELIBERATE read of that schema. This form never does, and it is what a FALLBACK read uses,
     * because a machine reaching for instruction memory after a data-memory miss must not cause work on
     * the reader's behalf. Measured: a logger's type lookup during tbleSpace's own construction walked
     * into schemaInstset -> ensureTables and hit a database connection that was not open yet.
     */
    public Obj readDeclared(final fURI pattern) {
        return this.readTables(pattern);
    }

    /**
     * THE TABLES, and nothing else — the one implementation both read forms share. It is PRIVATE on purpose:
     * a virtual delegation from {@code read} to {@code readDeclared} let a {@code BasicInstSet} (which
     * overrides the declared form with its union walk) re-enter the walk from its own own-level read, and the
     * stack died. Both forms reach the tables through this, so an override of either cannot do that.
     */
    private Obj readTables(final fURI pattern) {
        if (Objects.equals(this.vid, pattern))
            return this;
        // dom admission: a contract's dom is admitted for a queried dom iff it lies
        // on the queried type's single path to the root (eg nat -> int -> # admits
        // the int-dom plus, not the real-dom one), or the dom is generic (binds to
        // anything). siblings (metric/imperial under real) admit each other for
        // no query. results are ranked closest-to-the-token first.
        final Type queriedDom = !pattern.hasDom() ? null : T(pattern.dom());
        final Type queriedRng = !pattern.hasRng() ? null : T(pattern.rng());
        return QProc.Helper.processPreRead(this.qs(), pattern).orElseGet(() -> {
            final Obj result = objs(admitted(pattern, queriedDom, queriedRng)
                    // copy any user qs to api inst
                    .map(i -> pattern.hasNonDomRngQ() ? i.clone().selfTID(i.tid().copyQ(pattern)) : i)
                    .map(i -> pattern.isNode() ? i : rel(i.tid().toUri(), i)))
                    .append(objs(TYPE_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))))
                    .append(objs(REWRITE_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))))
                    .append(objs(CONST_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))));
            return QProc.Helper.processPostRead(this.qs(), pattern, result);
        });
    }

    /**
     * the insts of this set matching the pattern's family, admitted on the
     * dom axis per {@link Type#pathIncludes(Type)}, ranked closest-to-the-token
     * first (a no-dom pattern returns them all, unranked). The rule itself lives on
     * {@link Typer.Helper#admitted} — one implementation, shared with the deduction the law table reads — and this
     * method supplies only the family, looked up in this set's own index.
     */
    private Stream<Obj> admitted(final fURI pattern, final Type queriedDom, final Type queriedRng) {
        // dom admission is a PATH relation, not a name match: a contract on nat's path
        // (nat -> int -> #) is admitted for a nat query, which is what makes ?dom=nat resolve
        // to the int contract. A dom .test(pattern.dom()) here defeats that -- only exact-name
        // doms survive, so nat admits nothing and imperial admits only itself.
        return Typer.Helper.admitted(this.family(pattern), pattern, queriedDom, queriedRng).map(i -> (Obj) i);
    }

    /**
     * this set's registrations of one family — the table is keyed by the inst's base path, so the index answers
     * {@link Typer.Helper#family} without a scan.
     */
    private Stream<Inst> family(final fURI pattern) {
        return INST_TABLE.entrySet()
                .stream()
                .filter(kv -> kv.getKey().bimatches(pattern.basePath().asNode()))
                .flatMap(kv -> kv.getValue().stream());
    }

    /**
     * private Stream<Obj> admitted(final fURI pattern) {
     * final Stream<Obj> insts = INST_TABLE.entrySet()
     * .stream()
     * .filter(kv -> kv.getKey().test(pattern.basePath().asNode()))
     * .flatMap(kv -> kv.getValue().stream())
     * .<Obj>map(i -> i)
     * .filter(i -> !pattern.hasDom() || Obj.Helper.isRefinementOfTid(pattern.dom(), i.tid().dom(), i.tid().dom()))
     * .filter(i -> !pattern.hasRng() || Obj.Helper.isRefinementOfTid(pattern.rng(), i.tid().rng(), i.tid().rng()));
     * //.filter(i -> !pattern.hasRng() || i.rng().vidOrTid().test(pattern.rng()));
     * return pattern.hasDom() ?
     * insts.sorted(Comparator.comparingInt(i -> T(pattern).pathTo(i.dom()))) : insts;
     * }
     *
     * @param vid
     * @param obj
     * @return
     */

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return QProc.Helper.processPreWrite(this.qs(), vid, obj).orElseGet(
                () -> QProc.Helper.processQlessWrite(this.qs(), vid, obj).orElseGet(
                        () -> {
                            if (obj.isInst()) {
                                final Inst inst = obj.as();
                                if (inst.dom().isCode()) {
                                    REWRITE_TABLE.put(inst.tid(), inst);
                                } else {
                                    Machine.current().memory().registerRedirect(f(vid.name()), vid);
                                    INST_TABLE.computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashSet<>()).add(inst);
                                }
                            } else if (obj.isType()) {
                                TYPE_TABLE.put(vid, obj.as());
                            } else if (obj.isNoObj()) {
                                final Set<Inst> insts = INST_TABLE.get(vid.basePath());
                                insts.removeIf(i -> i.tid().test(vid));
                            } else {
                                CONST_TABLE.put(vid, obj);
                                // throw MTronException.of("inst set %s can only store insts, types, and rewrites: {{r}}!{{/r}} %s", this.simpeToString(), obj);
                            }
                            return QProc.Helper.processPostWrite(this.qs(), vid, obj).orElse(obj);
                        }));
    }

    public Set<Sugar> sugars() {
        return Set.of();
    }

}
