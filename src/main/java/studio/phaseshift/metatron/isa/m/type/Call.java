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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Memory;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.isa.m.mInstSet.AUTO_FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.ID_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.branch_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Call extends Obj {

    default boolean hasDomOrRng() {
        return this.hasDom() || this.hasRng();
    }

    default boolean hasDomAndRng() {
        return this.hasDom() && this.hasRng();
    }

    default boolean hasDom() {
        return this.tid().hasDom();
    }

    default boolean hasRng() {
        return this.tid().hasRng();
    }

    static Call from(final List<Inst> insts) {
        if (insts.isEmpty())
            return noobj();
        else if (insts.size() == 1)
            return insts.get(0);
        else
            return MCode.of(insts);
    }

    default Call tryToInst() {
        if (this.isCode()) {
            if (this.codeValue().isEmpty())
                return noobj();
            else if (this.codeValue().size() == 1)
                // a one-instruction code IS its instruction, bulk included: the code's coefficient multiplies the
                // instruction's own (a code of coefficient one, nearly every code, answers the instruction itself)
                return this.codeValue().getFirst().c(c -> c.mult(this.c())).as();
        }
        return this;
    }

    @Override
    default Code asCode() {
        if (this.isCode())
            return (Code) this;
        else
            return new MCode(List.of(this.as()), Tokens.CODE_TID, null);
    }

    default List<Inst> insts() {
        if (this.isCode()) {
            return new ArrayList<>(this.codeValue());
        } else if (this.isInst()) {
            return List.of((Inst) this.clone());
        } else {
            return List.of();
        }
    }

    /**
     * Whether a Java lambda, rather than mtron code, runs anywhere in this call: for a code, in any of its
     * instructions. An instruction answers for itself and its arguments ({@link Inst#containsJavaFunction()}).
     */
    default boolean containsJavaFunction() {
        return this.isCode() && this.codeValue().stream().anyMatch(Inst::containsJavaFunction);
    }

    default boolean isPredicate(final Obj lhs) {
        return !this.isNoObj() && this.resolve(lhs).insts().getLast().rng().c().equals(cInt.MAYBE());
    }

    @Override
    Call resolve(final Obj start);

    default <C extends Call> C dom(final Type domain) {
        return (C) this.tid(this.tid().dom(domain.vid().c(domain.tid().c()))); // TODO: we need to make the distrinction between vid coeffs and tid coeffs for type (remove c(domain...) when refactor complete)
    }

    default <C extends Call> C rng(final Type range) {
        return (C) this.tid(this.tid().rng(range.vid().c(range.tid().c())));  // TODO: we need to make the distrinction between vid coeffs and tid coeffs for type (remove c(range...) when refactor complete)
    }

    default Call neg() {
        return this.c(cInt::neg).as();
    }

    @Override
    default Obj append(final Obj obj) {
        return obj.isCall() && !obj.tid().basePath().equals(AUTO_FROM_INST_TID) ? this.plus((Call) obj) : objs(List.of(this, obj));
    }

    default Call one() {
        return MInst.instB(ID_INST_TID, lst());
    }

    default boolean isOne() {
        return this.tryToInst().equals(this.one());
    }

    default boolean isZero() {
        return this.isNoObj();
    }

    @Override
    default Call c(final Function<cInt, cInt> func) {
        return (Call) Obj.super.c(func);
    }

    default Call plus(final Call rhs) {
        if (rhs.isZero()) return this;
        if (this.isZero()) return rhs;
        if (this.clessEquals(rhs))
            return this.c(c -> c.plus(rhs.c()));
        return branch_(this, rhs).tryToInst();
    }

    default Call mult(final Call rhs) {
        if (rhs.isZero() || this.isZero())
            return noobj();
        if (rhs.isOne()) return this;
        if (this.isOne()) return rhs;
        final List<Inst> insts = new ArrayList<>(this.insts());
        insts.addAll(rhs.tryToInst().insts());
        return MCode.of(insts).tryToInst().c(c -> this.c().mult(rhs.c()));
    }

    default Call zero() {
        return noobj();
    }

    public static class Helper {
        private Helper() {
            // do nothing
        }

        public static List<Inst> getUnresolvedInsts(final Call call) {
            return call.insts().stream().filter(i -> !i.isResolved(true)).toList();
        }

        public static Call resolveInspection(final Obj lhs, final Call call, final Consumer<List<Inst>> consumer) {
            final Call resolvedCall = call.resolve(lhs);
            final List<Inst> unresolved = Helper.getUnresolvedInsts(resolvedCall);
            if (!unresolved.isEmpty())
                consumer.accept(unresolved);
            return resolvedCall;
        }

        /**
         * THE CODE UPDATE, {@code str::T}'s update over instructions instead of characters ({@link Str.Helper#project}):
         * each rule {@code pattern => change} matches its pattern against every run of consecutive instructions, left
         * to right and never overlapping. An instruction matches when it tests the pattern's instruction, as a value
         * tests a type ({@link Inst#test}): its name against a uri pattern ({@code </m/inst/{plus,mult}>}) and each
         * argument against the type it must be ({@code int::T} one int, {@code int{*}::T} a bag of them). A matched run becomes the rule's change:
         * a code (or an inst) replaces it, and a rec applies its own rules inside the run. The rules run in order, each
         * over the code the last one left, and a replacement is not matched again.
         * <p>
         * NAMES AND TEMPLATES (Marko, 2026-11-06). A pattern element with a value name names what it matched
         * ({@code plus(int::T@a)}, {@code 2@a}, {@code plus(int::T)@f}), bound by the binder's walk as any instruction's
         * names are ({@link Code.Helper#bind}): at any depth, a name met twice meeting the same obj, every name the
         * pattern declares bound or the run does not match, and an all-caps name a generic, a type and not a value
         * (2026-11-08). The names are the change's own argument
         * uris: a frame pushed on this thread's arg stack while the change is made, so threads never share them. A
         * change's {@code ${...}} argument is a template parameter, evaluated against what the run holds in that place
         * and reading the names: {@code plus(int::T@a).plus(int::T@b) => plus(${*a.plus(*b)})}. A named hole in the
         * change is what its name matched ({@code minus(#::T@b)} reads {@code ${*b}}), so a rule reversed is the same
         * equation (2026-11-08). All else is literal.
         * <p>
         * COMPONENTS (Marko, 2026-11-06). A rule keyed by the uri {@code args}, not a pattern, updates the arguments of
         * every instruction in the run in place, as {@code >>=} updates a lst. So {@code [plus(int::T)=>[args=>[+10]]]}
         * turns {@code plus(5)} into {@code plus(15)}, and an inst updates itself the same way
         * ({@code update?inst<=inst(rec::T)} is this over a run of one).
         */
        public static List<Inst> update(final List<Inst> insts, final Obj rules) {
            return update(insts, rules, start -> true);
        }

        /**
         * {@link #update(List, Obj)} with a matched run changed only where it begins at a position {@code starts} admits,
         * indexed into the code its rule scans: what a pattern cannot state, such as the carrier flowing into the run
         * ({@code derivation_contraction}), which is why it is asked only of a run that matched.
         */
        public static List<Inst> update(final List<Inst> insts, final Obj rules, final IntPredicate starts) {
            if (rules.isCall()) {
                // the change laid over the run, each of its insts against the run's inst in its place
                final List<Inst> template = rules.asCall().insts();
                return IntStream.range(0, template.size())
                        .mapToObj(k -> expand(template.get(k), k < insts.size() ? insts.get(k) : noobj()).asInst())
                        .toList();
            }
            if (!rules.isRec())
                throw MTronException.of("a matched run of code changes into a code or a rec of rules, not %s", rules);
            List<Inst> current = insts;
            // the rules walked as the rec's own rels (elements()), never through at(): a rule's key and value are code
            // held in a poly, and at() would fire an initial or an auto among them
            for (final Rel rule : rules.asRec().elements().toList()) {
                // A URI KEY NAMES A COMPONENT of every instruction in the run, in the word Inst already reads: its args.
                // They are updated as >>= updates a lst (Poly.Helper.updateRecursion): a call transforms, a literal or a
                // blocked |x replaces, _ keeps a value (#::T keeps an instruction, which _ would apply id() to), and
                // the rhs gives the new arity. A dom or rng is a type, which that rule does not update: refused.
                if (rule.first().isUri()) {
                    if (!Tokens.ARGS.equals(rule.first().uriValue().toString()))
                        throw MTronException.of("an instruction updates its %s, not %s", Tokens.ARGS, rule.first());
                    current = current.stream()
                            .map(inst -> inst.args((Poly<?, ?>) Poly.Helper.updateRecursion(inst.args(), rule.second(), Poly.IMMUTABLE)))
                            .toList();
                    continue;
                }
                if (!rule.first().isCall())
                    throw MTronException.of("a code update matches a code pattern, not %s", rule.first());
                final List<Inst> pattern = rule.first().asCall().insts();
                final List<Inst> scanned = current;
                final List<Inst> next = new ArrayList<>(scanned.size());
                int i = 0;
                while (i < scanned.size()) {
                    final List<Inst> run = !pattern.isEmpty() && i + pattern.size() <= scanned.size() ? scanned.subList(i, i + pattern.size()) : List.of();
                    // the run binds the pattern as a code binds a code pattern (Code.Helper#bind): it tests the pattern as a
                    // value tests a type (1.test(int::T)), never the reverse, and the binding is the change's names
                    final Map<fURI, Obj> names = run.isEmpty() ? null : Code.Helper.bind(run, pattern);
                    if (null != names && starts.test(i)) {
                        Memory.argStack().push(names.entrySet().stream().map(e -> rel(uri(e.getKey()), e.getValue())).collect(new CommonUtil.RecCollector()));
                        try {
                            next.addAll(update(run, rule.second()));
                        } finally {
                            Memory.argStack().pop();
                        }
                        i += pattern.size();
                    } else
                        next.add(scanned.get(i++));
                }
                current = next;
            }
            return current;
        }

        /**
         * A change's element made concrete against the run's element in its place. A templated uri argument
         * ({@code ${...}} or {@code {{{...}}}}, parsed as a uri) is a template parameter: its inner templates are
         * spliced first ({@link Str#expandTemplates}), then its expression is applied to the matched element, or to
         * noobj when it takes none, and the VALUE is the argument, not its text. A uri whose template is only part of
         * it splices text as a uri does. A parameter that cannot evaluate fails the update rather than staying put.
         * <p>
         * A NAMED HOLE READS ITS BINDING (Marko, 2026-11-08): a type in the change carrying a value name is what the
         * pattern matched under that name, read as {@code ${*name}} reads it, so a rule and its reverse are one equation:
         * {@code [plus(map(#::T@b).neg())=>minus(#::T@b)]} turns {@code plus(map(3).neg())} into {@code minus(3)}, and
         * {@code [minus(#::T@b)=>plus(map(#::T@b).neg())]} turns it back. A code argument is walked instruction by
         * instruction, as the binder walks one ({@link Inst.Helper#bindGenerics}).
         */
        private static Obj expand(final Obj change, final Obj matched) {
            if (change.isType() && null != Inst.Helper.name(change))
                return expand(uri("${*" + change.vid() + "}"), matched);
            if (change.isCode())
                return change.asCode().jvm(IntStream.range(0, change.asCode().insts().size())
                        .mapToObj(k -> expand(change.asCode().inst(k),
                                matched.isCall() && k < matched.asCall().insts().size() ? matched.asCall().insts().get(k) : noobj()).asInst())
                        .toList());
            if (change.isUri() && change.asUri().hasTemplates()) {
                final String text = change.uriValue().toString();
                final int open = text.startsWith("${") ? 2 : text.startsWith("{{{") ? 3 : 0;
                if (0 != open && text.endsWith(2 == open ? "}" : "}}}")) {
                    final Obj parsed = ObjmtronSerializer.parse(Str.expandTemplates(text.substring(open, text.length() - (2 == open ? 1 : 3)), matched));
                    // an expression that does not parse may be several templates in one uri (${a}${b}): spliced below
                    if (parsed.stream().noneMatch(Obj::isFail)) {
                        Object failure = null;
                        for (final Obj lhs : List.of(matched, noobj())) {
                            try {
                                final Obj value = parsed.apply(lhs);
                                if (!value.isFail())
                                    return value;
                                failure = value;
                            } catch (final Exception e) {
                                // a complete expression takes no lhs: it is tried against noobj next
                                failure = e.getMessage();
                            }
                        }
                        throw MTronException.of("the template parameter %s did not evaluate against %s: %s", change, matched, failure);
                    }
                }
                final Obj spliced = change.apply(matched);
                if (spliced.isUri() && spliced.asUri().hasTemplates())
                    throw MTronException.of("the template parameter %s did not evaluate against %s", change, matched);
                return spliced;
            }
            if (!change.isInst() || !change.asInst().args().isLst())
                return change;
            return change.asInst().args(lst(IntStream.range(0, (int) change.asInst().args().count())
                    .mapToObj(i -> expand(change.asInst().arg(i), matched.isInst() ? matched.asInst().arg(i) : noobj()))
                    .toList()));
        }
    }
}
