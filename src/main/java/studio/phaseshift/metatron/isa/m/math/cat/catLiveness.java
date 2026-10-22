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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.FILTER_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.GTE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.GT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.IS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.LTE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.LT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.MAP_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


/**
 * The a-priori liveness abstraction — the single-pass {@link Profile} over a code chain (or over an
 * {@code abstraction::T}'s morphisms) that decides, from the category's declarations alone, whether a
 * rule is a live candidate on this chain: {@code live(Profile, Inst)} is the necessary half of each
 * rule's guard, so {@code false} proves the rule is the identity while {@code true} only licenses the
 * match. Also the shared rule-guard vocabulary ({@code sameOp}, {@code mapArgument}, {@code declares})
 * the other slices of the law-driven rewrite family use.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catLiveness {

    /**
     * The carrier flowing INTO each instruction of the chain, index-aligned with {@code code.insts()}: the type a rule
     * reads that instruction's laws at. Reading every law at the SEED's type let real's {@code div⁻¹ = mult} cancel an
     * int's truncating division, so {@code 5.5.as(int::T).div(2).mult(2)} answered 5, not 4.
     * <p>
     * The types are the compiler's own: the chain is probed with the machine's resolver before it is rewritten, so a
     * law is read for the contract dispatch picks — its dom, so a {@code nat} reads the int {@code plus} it admits.
     * The probe is trusted while the type it threads stays concrete. Where it stops, its later answers rest on a
     * guessed token, so the rest of the chain is probed again from the carrier that instruction hands on: the same
     * one when it maps a generic to itself ({@code is}, {@code A ⇒ A}), else {@code #}, against which only the
     * contracts that hold for every carrier resolve.
     */
    public static List<Type> carriers(final Code code) {
        return typing(code).carriers();
    }

    /**
     * The contract the resolver picks for each instruction of the chain, index-aligned with {@code code.insts()}, read
     * in the same probe as {@link #carriers}; null where none resolves. An op the law table does not declare is keyed
     * by it, so its form is its own: the seed's {@code {0} ⇒ A} is initial, where the bare instruction's {@code # ⇒ #}
     * reads as a mapper.
     */
    public static List<Inst> contracts(final Code code) {
        return typing(code).contracts();
    }

    /** The carrier flowing into each instruction and the contract it resolves to, from one probe of the chain. */
    private record Typing(List<Type> carriers, List<Inst> contracts) {
    }

    private static Typing typing(final Code code) {
        final Map.Entry<Code, Typing> last = LAST_TYPING.get();
        if (null != last && last.getKey() == code)
            return last.getValue();
        final Typing typing = resolveTyping(code);
        LAST_TYPING.set(Map.entry(code, typing));
        return typing;
    }

    /**
     * The last chain {@link #typing} typed on this thread. The rules of one rewrite pass are handed the SAME code
     * object until one of them changes it, and each would otherwise probe the resolver again. Identity, not equality:
     * a hit is that very object, typed moments ago.
     */
    private static final ThreadLocal<Map.Entry<Code, Typing>> LAST_TYPING = new ThreadLocal<>();

    private static Typing resolveTyping(final Code code) {
        final List<Inst> insts = code.insts();
        final List<Type> carriers = new ArrayList<>(insts.size());
        final List<Inst> contracts = new ArrayList<>(insts.size());
        Type carrier = T(ALL);
        List<Inst> probe = probe(noobj(), code);
        int offset = 0;     // the chain position the current probe starts at
        for (int i = 0; i < insts.size(); i++) {
            final Inst inst = insts.get(i);
            carriers.add(carrier);
            if (START_INST_TID.equals(inst.tid().basePath())) {
                contracts.add(seedContract(inst, null == probe ? null : probe.get(i - offset)));
                carrier = inst.args().isEmpty() ? T(ALL) : carrierOf(inst.arg(0));
                continue;
            }
            Inst resolved = null == probe ? null : probe.get(i - offset);
            // unresolved where the probe threaded its own token: that token may carry a coefficient the dom refuses
            // (is hands on int{?}, and plus wants int), which the walked carrier — a base type — does not, so the
            // rest of the chain is probed again from here before the type is given up
            if ((null == resolved || !resolved.tid().hasDom()) && offset < i
                    && null != carrier.vid() && !ALL.basePath().equals(carrier.vid().basePath())) {
                offset = i;
                probe = probe(carrier, code(insts.subList(i, insts.size())));
                resolved = null == probe ? null : probe.getFirst();
            }
            contracts.add(resolved);
            final fURI dom = concrete(resolved, true);
            if (null != dom)
                carriers.set(i, T(dom));
            final fURI rng = concrete(resolved, false);
            if (null != rng) {
                carrier = T(rng);
                continue;
            }
            carrier = passesThrough(resolved) ? carrier : T(ALL);
            offset = i + 1;
            probe = offset < insts.size() ? probe(carrier, code(insts.subList(offset, insts.size()))) : null;
        }
        return new Typing(Collections.unmodifiableList(carriers), Collections.unmodifiableList(contracts));
    }

    /**
     * The seed's contract: the one the chain's probe resolved, else the seed resolved alone. Its dom is
     * {@code noobj}, so it answers whatever stream it arrives in, and a probe that fails further along
     * the chain does not leave the seed untyped.
     */
    private static Inst seedContract(final Inst seed, final Inst resolved) {
        if (null != resolved && resolved.tid().hasDom())
            return resolved;
        final List<Inst> alone = probe(noobj(), code(List.of(seed)));
        return null == alone ? null : alone.getFirst();
    }

    /**
     * The chain resolved by the machine's resolver from {@code lhs}, index-aligned with the chain, or null when it
     * cannot be: a probe only, so it never fails the rewrite that asked. The whole chain goes through the resolver's
     * memo (keyed by code identity); a restarted rest is a fresh code each time, so it bypasses the memo.
     */
    private static List<Inst> probe(final Obj lhs, final Code code) {
        try {
            final Resolver resolver = Machine.current().compiler().resolver();
            final Code resolved = lhs.isNoObj()
                    ? resolver.apply(lhs, code)
                    : Resolver.Helper.resolveCode(lhs, code, resolver.selector(), resolver.binder());
            return resolved.insts().size() == code.insts().size() ? resolved.insts() : null;
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * The base type at a resolved instruction's dom (or rng), or null when it has none, or it is generic or
     * {@code #} — a type no law is declared at.
     */
    private static fURI concrete(final Inst resolved, final boolean dom) {
        if (null == resolved || !(dom ? resolved.tid().hasDom() : resolved.tid().hasRng()))
            return null;
        final fURI type = (dom ? resolved.tid().dom() : resolved.tid().rng()).basePath();
        return type.isGeneric() || type.equals(ALL.basePath()) ? null : type;
    }

    /**
     * Whether a resolved instruction maps a generic to itself ({@code A ⇒ A}), handing on the carrier it consumes.
     */
    private static boolean passesThrough(final Inst resolved) {
        if (null == resolved || !resolved.tid().hasDom() || !resolved.tid().hasRng())
            return false;
        final fURI dom = resolved.tid().dom().basePath();
        return dom.isGeneric() && dom.equals(resolved.tid().rng().basePath());
    }

    /**
     * The carrier a seed fixes — the type of ONE obj of its stream. A bulk seed types as its stream: the member type
     * with the bulk's multiplicity as its coefficient ({@code {1,2,3}} is {@code int{3}::T}). A multiplicity is not an
     * object of the category — no theory is declared over {@code int{3}}, and {@code object::T} (dom {@code {?}})
     * refuses it — so it is dropped and the member type ({@code int::T}) carries the chain. The rules and the
     * {@code Profile} both read the carrier here, so the pruner sees exactly the carrier the rule it prunes would.
     */
    public static Type carrierOf(final Obj seed) {
        final Type type = seed.type();
        final fURI id = type.typeId();
        return null == id || id.c().isOne() || id.c().isZero() ? type : T(id.one());
    }

    /**
     * Two chain elements are the same operation, whatever their operands — the guard for a law that
     * relates an operation to itself (poset comparison composition).
     */
    public static boolean sameOp(final Inst a, final Inst b) {
        return a.tid().basePath().equals(b.tid().basePath());
    }

    /**
     * Two chain elements are the same operation AND the same operands — the guard for a law that relates an
     * operation to itself (involution, idempotent, monoidic fold).
     */
    public static boolean same(final Inst a, final Inst b) {
        return sameOp(a, b) && a.args().equals(b.args());
    }

    /**
     * The stream lift, {@code map} — kept keyed on the operation itself: its {@code {?} → {?}} coefficient
     * shape is shared by other instructions ({@code is} is also {@code {?} → {?}}), so a shape-only test
     * would unwrap a predicate. The chain instruction carries no endpoints, so the op <em>address</em> is
     * what identifies it.
     */
    static boolean isMap(final Inst inst) {
        return inst.tid().basePath().equals(MAP_INST_TID);
    }

    /**
     * The instruction a {@code map} wraps, or null when it wraps no instruction. Before resolution the
     * wrapped operand arrives as a uri (the op symbol), so it is read back as an instruction.
     */
    public static Obj mapArgument(final Inst inst, final Type carrier) {
        if (1 != inst.args().count() || !isMap(inst))
            return null;
        final Obj arg = inst.arg(0);
        if (arg.isObjInst())
            return arg;
        if (!arg.isUri())
            return null;
        final fURI op = arg.uriValue().isAbsolute() ? arg.uriValue() : M_ISA_TID.extend(INST).extend(arg.uriValue().name());
        final Inst wrapped = instB(op, lst());
        // keep the chain's pre-resolution form (the op address), but only when the address is a real op
        if (!Machine.read(op).isNoObj())
            return wrapped;
        return null;
    }

    /**
     * The comparison a filter tests — {@code is(gt(a))} or {@code filter(gt(a))}, whose one argument is
     * {@code gt(a)} (an instruction, or a one-instruction code) — or null when the instruction is no filter of one
     * comparison. A bare {@code gt(a)} is no filter: it answers a bool, which is why only filters compose.
     */
    public static Inst comparisonOf(final Inst filter) {
        final fURI op = filter.tid().basePath();
        if ((!IS_INST_TID.equals(op) && !FILTER_INST_TID.equals(op)) || 1 != filter.args().count() || !filter.arg(0).isCall())
            return null;
        final List<Inst> insts = filter.arg(0).<Call>as().asCode().insts();
        if (1 != insts.size())
            return null;
        final String name = insts.getFirst().tid().basePath().name();
        return name.equals(GT_INST_TID.name()) || name.equals(GTE_INST_TID.name())
                || name.equals(LT_INST_TID.name()) || name.equals(LTE_INST_TID.name()) ? insts.getFirst() : null;
    }

    /**
     * Whether an instruction declares a process law for the carrier's type. The chain instruction carries no
     * endpoints (the rewriter runs before resolution), so the declaration is resolved by op address,
     * disambiguated by the operand type — never guessed across types.
     */
    public static boolean declares(final Inst inst, final Type carrier, final Law law) {
        final LawTable.Declared declared = LawTable.declared(inst.tid().basePath(), carrier);
        return null != declared && declares(declared, law);
    }

    static boolean declares(final LawTable.Declared declared, final Law law) {
        return declared.entry().laws().lstValue().stream()
                .anyMatch(l -> l.uriValue().name().equals(law.name()));
    }

    /**
     * The liveness test, decided endogenously: the profile is read off an {@code abstraction::T}'s own
     * morphisms (their {@code law}, {@code form} and {@code name} fields, with each morphism's carried
     * {@code obj} instruction supplying operands), so the vertex prunes its out-edges from data it already
     * contains rather than from a table outside the graph.
     */
    public static boolean maybeNotIdentity(final List<Obj> morphisms, final Inst rewrite) {
        if (morphisms.isEmpty())
            return false; // every rewrite is the identity on the empty word
        try {
            return live(profileOf(morphisms), rewrite);
        } catch (final Exception e) {
            return true; // the profile failed to compute — cannot prove identity, so keep the edge (never prune when unsure)
        }
    }

    /**
     * The profile of an abstraction's morphisms, computed once per lift: a path asks every rule of the same morphism
     * list in turn, so the last list profiled on this thread (by identity) answers again.
     */
    private static Profile profileOf(final List<Obj> morphisms) {
        final Map.Entry<List<Obj>, Profile> last = LAST_PROFILE.get();
        if (null != last && last.getKey() == morphisms)
            return last.getValue();
        final Profile profile = Profile.of(morphisms);
        LAST_PROFILE.set(Map.entry(morphisms, profile));
        return profile;
    }

    private static final ThreadLocal<Map.Entry<List<Obj>, Profile>> LAST_PROFILE = new ThreadLocal<>();

    /**
     * The rule dispatch shared by both profiles — each case is the necessary half of that rule's own guard.
     */
    static boolean live(final Profile profile, final Inst rewrite) {
        return switch (rewrite.tid().basePath().name()) {
            case "theory_unit_removal" -> profile.unit;
            case "theory_involution" -> profile.adjInvolution;
            case "law_idempotent" -> profile.adjIdempotent;
            case "law_absorbing" -> profile.absorbing;
            case "law_monoidic" -> profile.adjMonoidFold;
            case "law_poset" -> profile.adjPoset;
            case "inverse_cancellation" -> profile.adjInverse;
            case "form_map_unwrap" -> profile.mapWrap;
            case "branch_collapse" -> profile.repeatedTerms;
            case "branch_inline" -> profile.oneTerm;
            case "derivation_contraction" -> LawTable.derivations().stream()
                    .anyMatch(d -> profile.ops.contains(d.rhs().insts().getLast().tid().basePath().toString()));
            case "law_shard" ->
                    profile.shard;   // a seed + a reducer whose partials recombine (the one-cut shard)
            default -> true; // an undeclared rule proves nothing -- keep the edge
        };
    }

    /**
     * The declaration profile of one code chain, computed in a single pass over its instructions: which
     * law-bearing adjacency, theory-modeling operands and forms the chain puts on the table. One field
     * per rule family; every field over-approximates its rule's guard (op adjacency stands in for
     * {@code same()}'s operand equality, operand-models-a-theory stands in for the unit test), never under.
     */
    private static final class Profile {

        private final Set<String> ops = new HashSet<>();  // the op addresses present -- derivation heads test membership
        private boolean unit;            // an op over a theory-modeling operand, or a bare argless op (a candidate identity morphism)
        private boolean adjInvolution;   // an adjacent same-op pair declaring involution, or any adjacent same-op pair under a carrier with a declared inverse role
        private boolean adjIdempotent;   // an adjacent same-op pair declaring idempotent
        private boolean adjMonoidFold;   // an adjacent same-op reducer/join pair declaring monoidic
        private boolean adjPoset;        // an adjacent pair of the same filter on the same poset comparison
        private boolean adjInverse;      // an adjacent pair on a declared opposing edge
        private boolean absorbing;       // an absorbing op with live code before it
        private boolean mapWrap;         // a map(f) wrapping an unwrappable instruction
        private boolean repeatedTerms;   // an occurrence of the additive witness with two or more members (like terms need a pair)
        private boolean oneTerm;         // an occurrence of the additive witness with exactly one member
        private boolean shard;           // a seed + a reducer with a combiner (law_shard's anchor)

        /**
         * The profile read off an abstraction's morphisms: {@code law}, {@code form} and {@code name} are
         * endogenous fields of each {@code morphism::T}, and each morphism's carried {@code obj} instruction
         * supplies the operands (args, dom) for the unit, map and inverse tests. An {@code initial} morphism
         * is the seed: it fixes the carrier and takes part in no adjacency.
         */
        private static Profile of(final List<Obj> morphisms) {
            final Profile profile = new Profile();
            final Map<Type, Boolean> modeled = new HashMap<>();
            // the additive normalizers' op, read off the code ring's algebra exactly as their rules read it -- a ring
            // declaring no additive structure has no witness, and neither normalizer is then ever live
            final fURI additive = TheoryHelper.additiveWitness(T(CODE_TID));
            // the carrier at each position, exactly as the rules read it (carriers): the chain is the morphisms' carried
            // instructions, and a morphism carrying none leaves every position unknown
            final List<Rec> blocks = morphisms.stream().filter(Obj::isRec).map(Obj::asRec).toList();
            final List<Inst> chain = blocks.stream().filter(b -> b.has(uri(OBJ))).map(b -> b.at(OBJ).asInst()).toList();
            final List<Type> carriers = chain.size() == blocks.size() ? carriers(code(chain)) : null;
            final Map<Type, Boolean> inverses = new HashMap<>();
            boolean priorCarrierInverse = false;
            Inst priorComparison = null;
            Type priorCarrier = T(ALL);
            String priorName = null;
            fURI priorInverse = null;
            boolean pastSeed = false;
            boolean hasSeed = false;
            for (int i = 0; i < blocks.size(); i++) {
                final Rec morphism = blocks.get(i);
                final Type carrier = null == carriers ? T(ALL) : carriers.get(i);
                final boolean carrierInverse = inverses.computeIfAbsent(carrier, t -> !TheoryHelper.invInst(t).isNoObj());
                final Inst keyed = morphism.has(uri(OBJ)) ? morphism.at(OBJ).asInst() : null;
                final String name = morphism.has(uri(NAME)) ? morphism.at(NAME).uriValue().name()
                        : (null == keyed ? null : keyed.tid().basePath().name());
                final String form = morphism.has(uri(FORM)) ? morphism.at(FORM).uriValue().name() : null;
                final List<String> laws = morphism.has(uri(LAW))
                        ? morphism.at(LAW).asLst().lstValue().stream().map(l -> l.uriValue().name()).toList()
                        : List.of();
                if (Inst.Form.initial.name().equals(form) || (null != keyed && START_INST_TID.equals(keyed.tid().basePath()))) {   // the seed: an initial form (start's contract, {0} => A) or, where neither the chain nor its registrations typed it, the op address
                    hasSeed = true;
                    continue;
                }
                if (hasSeed && Inst.Form.reducer.name().equals(form) && combines(keyed, carrier))
                    profile.shard = true;   // law_shard's anchor: a reducer whose partials recombine behind an order-free barrier
                if (null != keyed) {
                    profile.ops.add(keyed.tid().basePath().toString());
                    if (keyed.args().isEmpty())
                        profile.unit = true;  // a bare op may be a theory's identity morphism
                    else if (models(keyed.dom(), modeled) || models(keyed.arg(0).type(), modeled))
                        profile.unit = true;  // op(id) needs an operand whose type models a theory
                    if (null != mapArgument(keyed, carrier))
                        profile.mapWrap = true;
                    // the additive normalizers read the members' count; whether two members are like terms is the
                    // value residue, so a pair only licenses the collapse while a lone member always inlines
                    if (null != additive && additive.basePath().equals(keyed.tid().basePath())) {
                        if (1 == keyed.args().count())
                            profile.oneTerm = true;
                        else if (1 < keyed.args().count())
                            profile.repeatedTerms = true;
                    }
                }
                if (pastSeed && laws.contains(Law.absorbing.name()))
                    profile.absorbing = true;
                if (pastSeed && null != name && name.equals(priorName)) {
                    // the pair's carrier is its first member's, as the rule reads it
                    if (priorCarrierInverse || laws.contains(Law.involution.name()))
                        profile.adjInvolution = true;
                    if (laws.contains(Law.idempotent.name()))
                        profile.adjIdempotent = true;
                    // two of the same filter on the same comparison, poset-declared at the first one's carrier
                    final Inst comparison = null == keyed ? null : comparisonOf(keyed);
                    if (null != comparison && null != priorComparison && sameOp(comparison, priorComparison)
                            && declares(priorComparison, priorCarrier, Law.poset))
                        profile.adjPoset = true;
                    if ((Inst.Form.reducer.name().equals(form) || Inst.Form.join.name().equals(form)) && laws.contains(Law.monoidic.name()))
                        profile.adjMonoidFold = true;
                }
                final fURI inverse = null == keyed ? null : inverseOf(keyed);
                // directional, as the rule is: the current op must be the PRIOR op's declared inverse (x.f().f⁻¹)
                if (pastSeed && null != priorInverse && priorInverse.name().equals(name))
                    profile.adjInverse = true;
                priorName = name;
                priorInverse = inverse;
                priorCarrierInverse = carrierInverse;
                priorComparison = null == keyed ? null : comparisonOf(keyed);
                priorCarrier = carrier;
                pastSeed = true;
            }
            return profile;
        }

        /**
         * Whether the chain instruction's partials recombine — it has a computed {@link LawTable#combiner} once keyed
         * with its declared n-tid, exactly as the shard plan keys it ({@link LawTable#keyed}, a keyed fold's spec folds
         * included). The morphism carries the ORIGINAL instruction, so the key is re-derived from the carrier here.
         */
        private static boolean combines(final Inst chain, final Type carrier) {
            return null != chain && null != LawTable.combiner(LawTable.keyed(chain, carrier));
        }

        /**
         * The declared opposing edge of the keyed instruction, read from its own entry; null when none. The
         * one residue the morphism does not yet surface eagerly (it sits in the lazy {@code analysis} block),
         * so it is read off the carried n-tid rather than forcing the analysis.
         */
        private static fURI inverseOf(final Inst keyed) {
            final LawTable.Entry entry = LawTable.lawsOf(keyed.tid());
            return null == entry ? null : entry.inverse();
        }

        /**
         * Whether the operand's type models any algebraic theory -- memoized per type, since a chain's
         * instructions overwhelmingly share their operand types.
         */
        private static boolean models(final Type operand, final Map<Type, Boolean> cache) {
            return cache.computeIfAbsent(operand, t -> !LawTable.typeLawsOf(t).jvm().isEmpty());
        }

        /**
         * Whether the declaration's opposing edge is the given op -- one side of an adjacent inverse pair.
         */
        private static boolean opposes(final LawTable.Declared declared, final fURI op) {
            return null != declared && null != declared.entry().inverse() && declared.entry().inverse().basePath().equals(op);
        }
    }
}
