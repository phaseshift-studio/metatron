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

package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Typer — the compiler's fourth stage and the ONE GATEWAY for type-checking/inference: the utilities the rewriter,
 * the resolver and the processor call, at compile time or at run time, with the typer's own STATE deciding how far
 * the assertions go.
 * <p>
 * THE STATE IS THE TYPER'S REC ({@code /compiler/typer}), not a registry: a {@code check} field holds one flag per
 * type-check point ({@link #CODE_RESOLVE}, {@link #INST_RNG}, {@link #INST_DOM}, {@link #TYPE_PRED},
 * {@link #OBJ_WRITE}). Each flag is a {@code bool::T} today — a more fine-grained realization later (a level, a
 * predicate) is a stronger value at the same key, not a new grammar — and grouping them under {@code check} leaves
 * the typer free to acquire more orthogonal features beside it.
 * <pre>
 * typer::[check=>[code_resolve=>bool::T, inst_rng=>bool::T, inst_dom=>bool::T,
 *                 type_pred=>bool::T, obj_write=>bool::T]]
 * </pre>
 * <p>
 * {@link Helper} is the funnel. It also holds the stage's <b>deduction rule</b> — which instruction <em>implementation</em>
 * a type lands on (the contract between a family read and the resolver).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Typer extends Component {

    // ======================== the typer's state ========================

    /**
     * the state field: one flag per type-check point.
     */
    fURI CHECK = f("check");
    /**
     * require code to be fully resolved before evaluation.
     */
    fURI CODE_RESOLVE = f("code_resolve");
    /**
     * require the result to match the instruction's range.
     */
    fURI INST_RNG = f("inst_rng");
    /**
     * require the object to match the instruction's domain.
     */
    fURI INST_DOM = f("inst_dom");
    /**
     * require a value to match its type's predicate.
     */
    fURI TYPE_PRED = f("type_pred");
    /**
     * require a value to match its type on a space write.
     */
    fURI OBJ_WRITE = f("obj_write");

    /**
     * every type-check point, in the order the boot profile and the console present them.
     */
    List<fURI> CHECKS = List.of(CODE_RESOLVE, INST_RNG, INST_DOM, TYPE_PRED, OBJ_WRITE);

    /**
     * the boot default: every assertion on except {@link #CODE_RESOLVE}.
     */
    static boolean defaultsOn(final fURI flag) {
        return !CODE_RESOLVE.equals(flag);
    }

    /**
     * the boot default as a rec — LAZY, and never on an assertion read path: building the flags mints bools, whose
     * construction runs a type check, so a static field here would re-enter the typer's own initialization.
     */
    static Rec defaults() {
        return Defaults.CHECK;
    }

    final class Defaults {
        private static final Rec CHECK = rec(
                uri(CODE_RESOLVE), BOOL_FALSE,
                uri(INST_RNG), BOOL_TRUE,
                uri(INST_DOM), BOOL_TRUE,
                uri(TYPE_PRED), BOOL_TRUE,
                uri(OBJ_WRITE), BOOL_TRUE);

        private Defaults() {
        }
    }

    /**
     * this typer's state — its {@code check} rec, the boot defaults when it declares none.
     */
    default Rec check() {
        final Obj check = this.at(uri(CHECK));
        return check.isNoObj() ? rec() : check.asRec();
    }

    /**
     * is a type-check point on IN THIS TYPER? A point the state does not name falls back to the boot default.
     */
    default boolean asserts(final fURI flag) {
        final Obj check = this.at(uri(CHECK));
        final Obj on = check.isNoObj() ? noobj() : Helper.raw(check.asRec(), flag.name());
        return on.isNoObj() ? defaultsOn(flag) : on.boolValue();
    }

    /**
     * turn a type-check point on or off IN THIS TYPER — the state is the typer's own rec, so a machine, a pushed
     * frame, or a test boot can each set its own degree of checking.
     */
    default Typer asserts(final fURI flag, final boolean value) {
        if (this.at(uri(CHECK)).isNoObj())
            this.at(uri(CHECK), rec(new LinkedHashMap<>(defaults().jvm())), MUTABLE);
        this.check().at(uri(flag), bool(value), MUTABLE);
        return this;
    }

    /**
     * Type-check {@code code} according to this typer's enabled assertions.
     *
     * @param code the code to type-check
     * @return the type-checked code
     */
    Code apply(Obj code);

    /**
     * the deduction rule: for a type, which instruction implementation it reaches, given the instruction sets
     * mounted on the machine.
     */
    final class Helper {

        private Helper() {
        }

        /**
         * THE INSTRUCTION A TYPE LANDS ON — the most specific registered contract of {@code genericInst}'s family
         * whose {@code dom} lies on {@code type}'s single path to the root ({@code nat -> int -> #}), ranked
         * closest-to-the-token first. {@code nat::T}'s {@code plus} is the {@code int}-dom contract; an
         * {@code imperial} {@code plus} is its own, {@code real}'s is next, and its sibling {@code metric}'s is
         * never admitted. So a type with no implementation of its own continues up its path until it lands on a
         * base type's — which is what makes {@code ?dom=nat} resolve at all.
         * <p>
         * The returned instruction is the registered contract itself: its {@code tid} names the endpoints it was
         * registered over (the implementation's, not the query's) and its function is the implementation that
         * runs. A caller keying on the address reads {@code returned.tid()}; one re-stamping a chain instruction
         * writes {@code genericInst.tid(returned.tid())}.
         * <p>
         * {@code null} when nothing is admitted — never a guess: a family read and a resolver must both decline,
         * so that no law-driven rewrite fires on the wrong type. {@code type} decides the dom axis; a dom/rng
         * already on {@code genericInst} decides the rng axis (an exact rng match ranks first).
         *
         * @param instSet     the instruction set to search — the machine's union ({@link Machine#instset()}) in
         *                    production, where one family may be registered across several sets
         * @param type        the type being typed (the operand, e.g. {@code nat::T})
         * @param genericInst the underspecified instruction ({@code |plus}); its family name and any rng it
         *                    already carries are the query
         * @return the most specific admitted contract, or null when none is admitted
         */
        public static Inst deduceTypeInst(final InstSet instSet, final Type type, final Inst genericInst) {
            return null == genericInst ? null : deduceTypeInst(instSet, type, genericInst.tid());
        }

        /**
         * {@link #deduceTypeInst(InstSet, Type, Inst)} from an op address alone — what {@code Law.Helper.declared} has
         * before resolution, when it knows the family ({@code /m/inst/plus}) and the operand but holds no instruction.
         */
        public static Inst deduceTypeInst(final InstSet instSet, final Type type, final fURI op) {
            if (null == instSet || null == op || null == op.basePath())
                return null;
            final Type dom = null != type ? type : (op.hasDom() ? T(op.dom()) : null);
            if (null == dom)                              // nothing to deduce from: fail closed
                return null;
            final Type rng = op.hasRng() ? T(op.rng()) : null;
            return admitted(instSet.insts().stream(), op, dom, rng).findFirst().orElse(null);
        }

        /**
         * {@link #deduceTypeInst(InstSet, Type, Inst)} over the current machine's instset — the union of every
         * mounted set, which is where a family's contracts actually live together.
         */
        public static Inst deduceTypeInst(final Type type, final Inst genericInst) {
            return null == genericInst ? null :
                    deduceTypeInst(Machine.current().instset(), type, genericInst.tid());
        }

        /**
         * {@link #deduceTypeInst(InstSet, Type, Inst)} over the current machine's instset, from an op address.
         */
        public static Inst deduceTypeInst(final Type type, final fURI op) {
            return deduceTypeInst(Machine.current().instset(), type, op);
        }

        /**
         * THE ADMISSION RULE — the contracts of one family that may legally run for {@code queriedDom}, ranked.
         * <ul>
         *   <li><b>dom</b> is a PATH relation, not a name match: a contract's dom is admitted iff it lies on
         *       {@code queriedDom}'s single path to the root ({@link Type#pathIncludes}), so {@code nat} admits
         *       the {@code int} contract and {@code imperial} admits {@code real}'s — while siblings never admit
         *       each other ({@code metric} is off {@code imperial}'s path);</li>
         *   <li><b>rng</b> is admitted by the same rule, and a root or generic rng ({@code #}, {@code A}) admits
         *       nothing specific — it is the weakest claim, not a match for everything;</li>
         *   <li><b>ranking</b>: an exact rng match first (that is what {@code ?rng=} asks for), then
         *       closest-to-the-token on the dom axis ({@link Type#pathTo}), then on the rng axis. Deterministic
         *       on purpose — a family read, a resolver and {@code Law.Helper.declared} must agree on the winner.</li>
         * </ul>
         * A null {@code queriedDom} admits the whole family, unranked (a bare family read).
         *
         * @param insts      the candidate instructions — a set's whole table, as the stream a family is cut from
         * @param pattern    the query instruction, read for its base path and any rng it carries
         * @param queriedDom the type being typed, or null for an unranked family read
         * @param queriedRng the type the result must land in, or null when the rng is unconstrained
         * @return the admitted contracts, ranked when a dom is queried
         */
        public static Stream<Inst> admitted(final Stream<Inst> insts, final fURI pattern, final Type queriedDom, final Type queriedRng) {
            Objects.requireNonNull(insts, "insts");
            Objects.requireNonNull(pattern, "pattern");
            final Stream<Inst> candidates = family(insts, pattern)
                    .filter(i -> null == queriedDom || queriedDom.pathIncludes(i.dom()))
                    .filter(i -> null == queriedRng ?
                            (!pattern.hasRng() || i.rng().vidOrTid().test(pattern.rng())) :
                            (queriedRng.pathIncludes(i.rng()) && !i.rng().isRootType() && !i.rng().isGeneric()));
            return null == queriedDom ?
                    candidates :
                    candidates.sorted(Comparator
                            .comparingInt((Inst i) -> pattern.hasRng() && i.rng().vidOrTid().test(pattern.rng()) ? 0 : 1)
                            .thenComparingInt(i -> queriedDom.pathTo(i.dom()))
                            .thenComparingInt(i -> null == queriedRng ? 0 : queriedRng.pathTo(i.rng())));
        }

        /**
         * The instructions of one family — every registration sharing the family name of {@code pattern}'s base
         * path (bidirectional names included, {@link fURI#bimatches}). An instset's table is keyed by exactly this
         * name, so a set may answer from its own index instead of scanning; both forms are the same family.
         */
        public static Stream<Inst> family(final Stream<Inst> insts, final fURI pattern) {
            return insts
                    .filter(i -> null != i && null != i.tid() && null != i.tid().basePath())
                    .filter(i -> i.tid().basePath().bimatches(pattern.basePath().asNode()));
        }

        /**
         * DOM/RNG ADMISSION — does {@code obj} satisfy {@code type} as a runtime value? The fast-out trusts
         * construction-time validation when the labels and the coefficient already agree, and falls back to the full
         * structural test otherwise. It lives here, not on the executor: the question is a TYPE question, and the
         * typer is the one funnel every type check goes through ({@link Type}, {@link Obj#test(Obj)}).
         * <p>
         * The label comparison is the INSTANTIATION MATCH: a value of {@code object[/m/code]} is an {@code object},
         * so a bracketed id is compared against its erasure (identity, not a claim — the bracket rule in
         * {@link studio.phaseshift.metatron.furi.fURI#test} still refuses {@code object[/m/code].test(object)} at the
         * claim position). A bracketed CONTRACT still fails this fast-out and falls through to the structural test,
         * which is the claim.
         *
         * @param obj  the value being admitted
         * @param type the expected type
         * @return true when the value satisfies the type
         */
        // ======================== the state gateway ========================

        /**
         * THE CURRENT MACHINE'S TYPE-CHECK STATE — read off {@code /compiler/typer/check}. The state is the typer's
         * rec, not a registry, so a pushed frame (its own machine) or a test boot carries its own degree of checking.
         */
        /**
         * A RAW FIELD LOOKUP — name compared, no obj minted. The assertion path must not CONSTRUCT anything: minting
         * a uri or a bool runs a type check, which asks the typer again, which would mint again (a stack overflow at
         * class-init time, and a per-check allocation ever after). The typer's recs are a handful of entries, so the
         * walk is the cheaper read as well as the safe one.
         */
        /**
         * drop the cached state rec — a write that REPLACES the {@code check} rec (the address write the mtron form
         * makes) must be seen by the next assertion. An in-place flag write ({@code at("check/inst_dom", …)}) needs no
         * invalidation: the cached rec is the object that changed.
         */
        private static Obj raw(final Rec rec, final String name) {
            for (final java.util.Map.Entry<Obj, Obj> entry : rec.recValue().entrySet())
                if (entry.getKey().isUri() && name.equals(entry.getKey().uriValue().name()))
                    return entry.getValue();
            return noobj();
        }

        /**
         * the state is read LIVE, every time: a write is an ordinary rec write, and a rec write REPLACES the node it
         * writes ({@code at("check/inst_dom", …)} swaps the check rec out), so a cached rec would go stale behind the
         * caller's back. The walks below are over recs with a handful of entries each.
         */
        private static Obj check() {
            // BEFORE OR DURING BOOT there is no machine to ask, and asking would recurse into the very machine being
            // constructed (a bool's construction runs a type check). The boot default is the only safe answer then.
            if (!Machine.loaded())
                return noobj();
            final Machine machine = Machine.current();
            if (null == machine)
                return noobj();
            final Obj compiler = raw(machine, COMPILER);
            final Obj stage = compiler.isRec() ? raw(compiler.asRec(), TYPER) : noobj();
            return stage.isRec() ? raw(stage.asRec(), CHECK.name()) : noobj();
        }

        /**
         * is a type-check point on? The one question every stage (rewriter, resolver, processor, space write) asks.
         */
        public static boolean asserts(final fURI flag) {
            final Obj check = check();
            final Obj on = check.isNoObj() ? noobj() : raw(check.asRec(), flag.name());
            return on.isNoObj() ? defaultsOn(flag) : on.boolValue();
        }

        public static boolean codeResolve() {
            return asserts(CODE_RESOLVE);
        }

        public static boolean instDom() {
            return asserts(INST_DOM);
        }

        public static boolean instRng() {
            return asserts(INST_RNG);
        }

        public static boolean typePred() {
            return asserts(TYPE_PRED);
        }

        public static boolean objWrite() {
            return asserts(OBJ_WRITE);
        }

        /**
         * turn one type-check point on or off in the current machine's typer. The state is the typer's rec, so this
         * is the ordinary nested write — {@code Machine.current().compiler().typer().at("check/inst_dom", BOOL_TRUE,
         * MUTABLE)} — and the mtron spelling is the same act:
         * <pre>
         * &#64;/compiler/typer/check >>= +[inst_dom => true]
         * </pre>
         * The console, the docs runner and the tests therefore all write the place the stages read.
         */
        public static void set(final fURI flag, final boolean value) {
            if (!Machine.loaded())
                return;
            final Machine machine = Machine.current();
            if (null == machine)
                return;
            final Obj compiler = raw(machine, COMPILER);
            if (compiler.isNoObj() || !compiler.isRec())
                return;
            final Obj stage = raw(compiler.asRec(), TYPER);
            if (stage.isNoObj() || !stage.isRec())
                return;
            stage.asRec().at(uri(CHECK.name() + "/" + flag.name()), bool(value), MUTABLE);
        }

        /**
         * turn every type-check point on (the start of a debugging session).
         */
        public static void enableAll() {
            CHECKS.forEach(flag -> set(flag, true));
        }

        /**
         * the type-check points currently on, in declaration order.
         */
        public static Set<fURI> enabled() {
            final Set<fURI> enabled = new LinkedHashSet<>();
            CHECKS.stream().filter(Typer.Helper::asserts).forEach(enabled::add);
            return enabled;
        }

        /**
         * the type-check point named {@code name}, when it is one.
         */
        public static Optional<fURI> flag(final String name) {
            return CHECKS.stream().filter(flag -> flag.name().equalsIgnoreCase(name)).findFirst();
        }

        /**
         * how many type-check points are on (the console's strictness level).
         */
        public static int level() {
            return enabled().size();
        }

        /**
         * the console's color for the current strictness.
         */
        public static String colorLevel() {
            return switch (level()) {
                case 0 -> "w";
                case 1 -> "k";
                case 2 -> "r";
                case 3 -> "m";
                case 4 -> "y";
                default -> "g";
            };
        }

        public static boolean domRngMatch(final Obj obj, final Type type) {
            if (type.tid().poly().isEmpty()
                    // identity, not a claim: erasure is explicit here
                    && Type.Helper.testByIdentity(Obj.Helper.specificTypeId(obj), Obj.Helper.specificTypeId(type))
                    && obj.c().within(type.c()))
                return true;
            return obj.test(type);
        }
    }
}
