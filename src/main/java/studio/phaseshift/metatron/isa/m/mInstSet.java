/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.m;

import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.Rewriter;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QSTRING;
import static studio.phaseshift.metatron.furi.Q.Q_TYPE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.CONSTQ_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.space.memSpace.MEM_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.space.metaSpace.META_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.space.stackSpace.STACK_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bytes.BYTES_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Code.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instA;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

@InstSet.JREService(tid = "/m")
public class mInstSet extends AbstractInstSet {

    public static final fURI M_ISA_TID = f("/m");
    public static final fURI MTRON_TID = f("/m");
    // /m/obj
    public static final fURI FAIL_TID = M_ISA_TID.extend("fail");
    public static final fURI BOOL_TID = M_ISA_TID.extend("bool");
    public static final fURI BYTES_TID = M_ISA_TID.extend("bytes");
    public static final fURI INT_TID = M_ISA_TID.extend("int");
    public static final fURI REAL_TID = M_ISA_TID.extend("real");
    public static final fURI STR_TID = M_ISA_TID.extend("str");
    public static final fURI URI_TID = M_ISA_TID.extend("uri");
    public static final fURI REL_TID = M_ISA_TID.extend("rel");
    public static final fURI LST_TID = M_ISA_TID.extend("lst");
    public static final fURI REC_TID = M_ISA_TID.extend("rec");
    public static final fURI INST_TID = M_ISA_TID.extend("inst");
    public static final fURI OBJS_TID = M_ISA_TID.extend("objs");
    public static final fURI TYPE_TID = M_ISA_TID.extend("type");
    public static final fURI CODE_TID = M_ISA_TID.extend("code");
    public static final fURI NOOBJ_TID = f("noobj");
    public static final fURI ALL_STAR = ALL.maybeSome();
    public static final fURI SPACE_TID = M_ISA_TID.extend("space");
    /// ////////////////////////////////////////////////////////
    public static final fURI LIKE_INST_TID = INST_TID.extend("like");
    public static final fURI CAUSE_INST_TID = INST_TID.extend("cause");
    public static final fURI NATIVE_INST_TID = INST_TID.extend("native");
    public static final fURI SERIALIZE_INST_TID = INST_TID.extend("serialize");
    public static final fURI ID_INST_TID = INST_TID.extend("id");
    public static final fURI DEDUP_INST_TID = INST_TID.extend("dedup");
    public static final fURI EXPLAIN_INST_TID = INST_TID.extend("explain");
    public static final fURI HAS_INST_TID = INST_TID.extend("has");
    public static final fURI EVAL_INST_TID = INST_TID.extend("eval");
    public static final fURI FORK_INST_TID = INST_TID.extend("fork");
    public static final fURI CATCH_INST_TID = INST_TID.extend("catch");
    public static final fURI APPLY_INST_TID = INST_TID.extend("apply");
    public static final fURI START_INST_TID = INST_TID.extend("start");
    public static final fURI COUNT_INST_TID = INST_TID.extend("count");
    public static final fURI SUM_INST_TID = INST_TID.extend("sum");
    public static final fURI CC_INST_TID = INST_TID.extend("cc");
    public static final fURI PROD_INST_TID = INST_TID.extend("prod");
    public static final fURI POW_INST_TID = INST_TID.extend("pow");
    public static final fURI MOD_INST_TID = INST_TID.extend("mod");
    public static final fURI REDUCE_INST_TID = INST_TID.extend("reduce");
    public static final fURI NEG_INST_TID = INST_TID.extend("neg");
    public static final fURI MULT_INST_TID = INST_TID.extend("mult");
    public static final fURI DIV_INST_TID = INST_TID.extend("div");
    public static final fURI INV_INST_TID = INST_TID.extend("inv");
    public static final fURI ZERO_INST_TID = INST_TID.extend("zero");
    public static final fURI ONE_INST_TID = INST_TID.extend("one");
    public static final fURI PLUS_INST_TID = INST_TID.extend("plus");
    public static final fURI MPLUS_INST_TID = INST_TID.extend("mplus");
    public static final fURI MINUS_INST_TID = INST_TID.extend("minus");
    public static final fURI MAP_INST_TID = INST_TID.extend("map");
    public static final fURI PARENT_INST_TID = INST_TID.extend("parent");
    public static final fURI FILTER_INST_TID = INST_TID.extend("filter");
    public static final fURI SIDE_INST_TID = INST_TID.extend("side");
    public static final fURI TO_INST_TID = INST_TID.extend("to");
    public static final fURI FROM_INST_TID = INST_TID.extend("from");
    public static final fURI REF_INST_TID = INST_TID.extend("ref");
    public static final fURI SPLIT_INST_TID = INST_TID.extend("split"); // -<
    public static final fURI CHOOSE_INST_TID = INST_TID.extend("choose"); // -<|
    public static final fURI CHAIN_TID = INST_TID.extend("chain"); // -<;
    public static final fURI MERGE_INST_TID = INST_TID.extend("merge");
    public static final fURI FILL_TID = INST_TID.extend("fill");
    public static final fURI FIND_TID = INST_TID.extend("find");
    public static final fURI RMERGE_TID = INST_TID.extend("rmerge");
    public static final fURI RANGE_INST_TID = INST_TID.extend("range");
    public static final fURI WITHIN_INST_TID = INST_TID.extend("within");
    public static final fURI LIFT_INST_TID = INST_TID.extend("lift");
    public static final fURI AUTO_INST_TID = INST_TID.extend("auto");
    public static final fURI AUTO_FROM_INST_TID = INST_TID.extend("auto_from");
    public static final fURI BLOCK_INST_TID = INST_TID.extend("block");
    public static final fURI RNG_INST_TID = INST_TID.extend("rng");
    public static final fURI DOM_INST_TID = INST_TID.extend("dom");
    public static final fURI TID_INST_TID = INST_TID.extend("tid");
    public static final fURI VID_INST_TID = INST_TID.extend("vid");
    public static final fURI TYPE_INST_TID = INST_TID.extend("type");
    public static final fURI GET_INST_TID = INST_TID.extend("get");
    public static final fURI FAILURE_INST_TID = INST_TID.extend("failure");
    public static final fURI AS_INST_TID = INST_TID.extend("as");
    public static final fURI REVERSE_INST_TID = INST_TID.extend("reverse");
    public static final fURI CLOSE_INST_TID = INST_TID.extend("close");
    public static final fURI REPEAT_INST_TID = INST_TID.extend("repeat");
    public static final fURI AT_INST_TID = INST_TID.extend("at");
    public static final fURI IS_INST_TID = INST_TID.extend("is");
    public static final fURI ISA_INST_TID = INST_TID.extend("isa");
    public static final fURI OR_INST_TID = INST_TID.extend("or");
    public static final fURI AND_INST_TID = INST_TID.extend("and");
    public static final fURI MATCHES_INST_TID = INST_TID.extend("matches");
    public static final fURI EQ_INST_TID = INST_TID.extend("eq");
    public static final fURI NEQ_INST_TID = INST_TID.extend("neq");
    public static final fURI GT_INST_TID = INST_TID.extend("gt");
    public static final fURI REGEX_INST_TID = INST_TID.extend("regex");
    public static final fURI ORDER_INST_TID = INST_TID.extend("order");
    public static final fURI LT_INST_TID = INST_TID.extend("lt");
    public static final fURI GTE_INST_TID = INST_TID.extend("gte");
    public static final fURI LTE_INST_TID = INST_TID.extend("lte");
    public static final fURI NOT_INST_TID = INST_TID.extend("not");
    public static final fURI TAKE_INST_TID = INST_TID.extend("take");
    public static final fURI SKIP_INST_TID = INST_TID.extend("skip");
    public static final fURI BARRIER_INST_TID = INST_TID.extend("barrier");
    public static final fURI REIFY_INST_TID = INST_TID.extend("reify");
    public static final fURI SELECT_INST_TID = INST_TID.extend("select");
    public static final fURI UPDATE_INST_TID = INST_TID.extend("update");
    public static final fURI WHERE_INST_TID = INST_TID.extend("where");
    public static final fURI GROUP_INST_TID = INST_TID.extend("group");
    public static final fURI ELSE_INST_TID = INST_TID.extend("else");
    public static final fURI END_INST_TID = INST_TID.extend("end");
    public static final fURI THREAD_INST_TID = INST_TID.extend("thread");
    public static final fURI IMPORT_INST_TID = INST_TID.extend("import");
    public static final fURI SOURCE_INST_TID = INST_TID.extend("source");
    public static final fURI SWAP_TID = INST_TID.extend("swap");
    public static final fURI PRINT_INST_TID = INST_TID.extend("print");
    public static final fURI LSHIFT_INST_TID = INST_TID.extend("lshift");
    public static final fURI RSHIFT_INST_TID = INST_TID.extend("rshift");
    public static final fURI MATH_INST_TID = INST_TID.extend("math");
    public static final fURI PATH_TID = INST_TID.extend("path");
    public static final fURI Q_INST_TID = INST_TID.extend("q");
    public static final fURI URI_C_TID = INST_TID.extend("uri:c");
    public static final fURI LCASE_INST_TID = INST_TID.extend("lcase");
    public static final fURI UCASE_INST_TID = INST_TID.extend("ucase");
    public static final fURI SCHEME_INST_TID = INST_TID.extend("scheme");
    public static final fURI AUTHORITY_INST_TID = INST_TID.extend("authority");
    public static final fURI HOST_INST_TID = INST_TID.extend("host");
    public static final fURI PORT_INST_TID = INST_TID.extend("port");
    /// ////////////
    /// ////////////
    public static final fURI POLY_TID = M_ISA_TID.extend("poly");
    public static final fURI MONO_TID = M_ISA_TID.extend("mono");
    public static final fURI NUM_TID = M_ISA_TID.extend("num");

    //public static final Set<fURI> MARKER_TYPES = Set.of(MONO_TID, POLY_TID, NUM_TID);
    public static final Set<fURI> BASE_TYPES = Set.of(
            FAIL_TID, BOOL_TID, BYTES_TID, INT_TID, REAL_TID,
            STR_TID, URI_TID, REL_TID,
            LST_TID, REC_TID, INST_TID,
            CODE_TID, OBJS_TID, NOOBJ_TID);

    public static final Type SPACE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(SPACE_TID)
            .isaPredicate(rec(
                    uri(PATTERN), URI_TYPE,
                    uri(QSTRING).maybe(), rec(URI_TYPE, Q_TYPE))).create();

    public static final Type MONO_TYPE = Type.Builder.build()
            .tid(MONO_TID)
            .vid(MONO_TID)
            .predicate((lhs, inst) -> bool(lhs.isBytes() || lhs.isBool() || lhs.isInt() || lhs.isReal() || lhs.isStr() || lhs.isUri() || lhs.isInst()))
            .create();

    public static final Type NUM_TYPE = Type.Builder.build()
            .tid(NUM_TID)
            .vid(NUM_TID)
            .predicate((lhs, inst) -> bool(lhs.isInt() || lhs.isReal()))
            .create();

    public static final Type POLY_TYPE = Type.Builder.build()
            .tid(POLY_TID)
            .vid(POLY_TID)
            .predicate((lhs, inst) -> bool(lhs.isLst() || lhs.isRec() || lhs.isRel() || lhs.isCode()))
            .create();


    public mInstSet() {
        super(M_ISA_TID, M_ISA_TID);
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public Set<Type> types() {
        return new LinkedHashSet<>(List.of(
                MONO_TYPE,
                POLY_TYPE,
                NUM_TYPE,
                NOOBJ_TYPE,
                FAIL_TYPE,
                BOOL_TYPE,
                INT_TYPE,
                REAL_TYPE,
                BYTES_TYPE,
                STR_TYPE,
                URI_TYPE,
                REL_TYPE,
                LST_TYPE,
                REC_TYPE,
                INST_TYPE,
                CODE_TYPE,
                Type.Builder.build().tid(OBJS_TID).vid(OBJS_TID).create(),
                /// ///////////////////////////////////
                SPACE_TYPE,
                MEM_SPACE_TYPE,
                STACK_SPACE_TYPE,
                META_SPACE_TYPE,
                /// ///////////////////////////////////
                CONSTQ_TYPE));
    }

    @Override
    public Set<Obj> consts() {
        return Stream.of(noobj()).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    @Override
    public Set<Inst> rewrites() {
        return new LinkedHashSet<>(List.of(
                // Remove identity instructions (no-op)
                InstSet.Helper.rewriter(f("id_removal_rewrite"),
                        code -> code.selfJVM(
                                Rewriter.search(code.insts())
                                        .match(instA(ID_INST_TID).insts())
                                        .rewrite(_ -> List.of())).asCode()),

                // Flatten nested map instructions
                InstSet.Helper.rewriter(f("map_nest_rewrite"),
                        code -> code.selfJVM(
                                Rewriter.search(code.insts())
                                        .match(instB(MAP_INST_TID, lst(instA(MAP_INST_TID))).insts())
                                        .repeat()
                                        .rewrite(map -> map.values().stream().map(objs -> objs.arg(0).asInst()).toList())).asCode()),

                // Eliminate else() after non-maybe instruction (dead code)
                // Pattern: .count().else(x) → .count() (count always returns a value)
                InstSet.Helper.rewriter(f("else_after_count_rewrite"),
                        code -> code.selfJVM(
                                Rewriter.search(code.insts())
                                        .match(List.of(instA(COUNT_INST_TID), instA(ELSE_INST_TID)))
                                        .rewrite(map -> {
                                            final List<Inst> matched = map.values().stream().toList();
                                            // COUNT always returns int, so ELSE is dead code
                                            return List.of(matched.getFirst());
                                        })).asCode()),

                // Optimize plus(0) for any PlusMonoid (identity)
                // Pattern: .plus(0) → identity (no-op)
                // DISABLED: This rewrite is interfering with Rec operations (RecTest.testAt() failures)
                // The rewrite removes .plus(0) operations that are needed for record access patterns

                InstSet.Helper.rewriter(f("plus_zero_rewrite"),
                        code -> code.selfJVM(
                                Rewriter.search(code.insts())
                                        .match(List.of(instB(PLUS_INST_TID, lst(is_(eq_(zero_())).tryToInst()))))
                                        .rewrite(map -> {
                                            final Inst plusInst = map.values().iterator().next();
                                            if (plusInst.args().count() > 0) {
                                                if (plusInst.arg(0) instanceof PlusMonoid<?> && ((PlusMonoid<?>) plusInst.arg(0)).isZero()) {
                                                    // plus(0) is identity, remove it
                                                    return List.of();
                                                }
                                            }
                                            return List.of(plusInst);
                                        })).asCode()),


                // Optimize mult(1) for integers (identity)
                // Pattern: .mult(1) → identity (no-op)
                // DISABLED: This rewrite is interfering with list operations

                InstSet.Helper.rewriter(f("mult_one_rewrite"),
                        code -> code.selfJVM(
                                Rewriter.search(code.insts())
                                        .match(List.of(mult_(is_(eq_(one_()))).tryToInst().as()))
                                        .rewrite(map -> {
                                            final Inst multInst = map.values().iterator().next();
                                            if (multInst.args().count() > 0) {
                                                if (multInst.arg(0) instanceof MultMonoid<?> && ((MultMonoid<?>) multInst.arg(0)).isOne()) {
                                                    // mult(1) is identity, remove it
                                                    return List.of();
                                                }
                                            }
                                            return List.of(multInst);
                                        })).asCode()),


                // Collapse identical branches in split-merge by summing coefficients
                // Pattern: -<[inst,inst,...]>- → inst{n}
                // This leverages the ring structure where identical branches collapse on merge
                // Note: Only applies to split-merge pairs, as split alone creates superposition
                InstSet.Helper.rewriter(f("split_merge_collapse_rewrite"),
                        code -> code.selfJVM(
                                Rewriter.search(code.insts())
                                        .match(List.of(instA(SPLIT_INST_TID), instA(MERGE_INST_TID)))
                                        .rewrite(map -> {
                                            final List<Inst> matched = map.values().stream().toList();
                                            final Inst splitInst = matched.get(0);
                                            final Inst mergeInst = matched.get(1);

                                            if (splitInst.args().count() > 0 && splitInst.arg(0).isLst()) {
                                                final Lst branches = splitInst.arg(0).asLst();
                                                // Check if all branches are identical instructions
                                                if (branches.count() > 1) {
                                                    final List<Obj> branchList = branches.elements().toList();
                                                    final Obj firstBranch = branchList.get(0);

                                                    // First check if firstBranch is an instruction
                                                    if (!firstBranch.isInst()) {
                                                        return matched;
                                                    }

                                                    // Check if all branches are the same instruction
                                                    boolean allIdentical = branchList.stream()
                                                            .allMatch(b -> b.isInst() &&
                                                                    b.asInst().tid().basePath().equals(firstBranch.asInst().tid().basePath()) &&
                                                                    b.asInst().args().count() == firstBranch.asInst().args().count() &&
                                                                    (b.asInst().args().count() == 0 ||
                                                                            b.asInst().arg(0).equals(firstBranch.asInst().arg(0))));

                                                    if (allIdentical) {
                                                        // Sum the coefficients (using max() since coefficients are exact values)
                                                        final long totalCoeff = branchList.stream()
                                                                .mapToLong(b -> b.asInst().c().max())
                                                                .sum();

                                                        // Return single instruction with summed coefficient
                                                        // The merge is implicit in the collapsed instruction
                                                        return List.of(firstBranch.asInst().c(c -> cInt.of(totalCoeff)).asInst());
                                                    }
                                                }
                                            }
                                            return matched;
                                        })).asCode()),

                // Left factoring: pull out common prefix from split branches
                // Pattern: a-<[b.c.d, b.c.e]>- → a.b.c-<[d, e]>-
                // This reduces clock cycles by executing common prefix once
                InstSet.Helper.rewriter(f("split_merge_left_factor_rewrite"),
                        code -> code.selfJVM(
                                Rewriter.search(code.asCode().insts())
                                        .match(List.of(instA(SPLIT_INST_TID), instA(MERGE_INST_TID)))
                                        .repeat()
                                        .rewrite(map -> {
                                            final List<Inst> matched = map.values().stream().toList();
                                            final Inst splitInst = matched.get(0);
                                            final Inst mergeInst = matched.get(1);

                                            if (splitInst.args().count() > 0 && splitInst.arg(0).isLst()) {
                                                final Lst branches = splitInst.arg(0).asLst();
                                                final List<Obj> branchList = branches.jvm();

                                                if (branchList.size() > 1) {
                                                    // Get instruction lists for each branch
                                                    final List<List<Inst>> branchInsts = branchList.stream()
                                                            .map(b -> b.<Call>as().insts())
                                                            .toList();

                                                    // Find common prefix length
                                                    int commonPrefixLen = 0;
                                                    final int minLen = branchInsts.stream().mapToInt(List::size).min().orElse(0);

                                                    for (int i = 0; i < minLen; i++) {
                                                        final Inst firstInst = branchInsts.get(0).get(i);
                                                        final int idx = i;
                                                        final boolean allMatch = branchInsts.stream()
                                                                .allMatch(insts -> insts.get(idx).tid().equals(firstInst.tid()) &&
                                                                        insts.get(idx).args().equals(firstInst.args()));
                                                        if (allMatch) {
                                                            commonPrefixLen++;
                                                        } else {
                                                            break;
                                                        }
                                                    }

                                                    if (commonPrefixLen > 0 && commonPrefixLen < minLen) {
                                                        // Only optimize if there's a common prefix AND remaining instructions
                                                        // (don't optimize if all branches are identical - that's handled by collapse rewrite)

                                                        // Extract common prefix
                                                        final List<Inst> commonPrefix = branchInsts.get(0).subList(0, commonPrefixLen);

                                                        // Create new branches without the common prefix
                                                        final int commonPrefixLenFinal = commonPrefixLen;
                                                        final List<Obj> newBranches = branchInsts.stream()
                                                                .map(insts -> (Obj) MCode.of(insts.subList(commonPrefixLenFinal, insts.size())).tryToInst())
                                                                .toList();

                                                        // Return: common_prefix + split(new_branches) + merge
                                                        return Stream.concat(
                                                                commonPrefix.stream(),
                                                                Stream.of(
                                                                        instB(SPLIT_INST_TID, lst(lst(newBranches))),
                                                                        instB(MERGE_INST_TID, lst())
                                                                )
                                                        ).toList();
                                                    }
                                                }
                                            }
                                            // No optimization possible, return original
                                            return matched;
                                        })).asCode()),

                // Right factoring: pull out common suffix from split branches
                // Pattern: a-<[b.d, c.d]>- → a-<[b, c]>-.d
                // This reduces clock cycles by executing common suffix once
                InstSet.Helper.rewriter(f("split_merge_right_factor_rewrite"),
                        code -> code.selfJVM(
                                Rewriter.search(code.asCode().insts())
                                        .match(List.of(instA(SPLIT_INST_TID), instA(MERGE_INST_TID)))
                                        .repeat()
                                        .rewrite(map -> {
                                            final List<Inst> matched = map.values().stream().toList();
                                            final Inst splitInst = matched.get(0);
                                            final Inst mergeInst = matched.get(1);

                                            if (splitInst.args().count() > 0 && splitInst.arg(0).isLst()) {
                                                final Lst branches = splitInst.arg(0).asLst();
                                                final List<Obj> branchList = branches.jvm();

                                                if (branchList.size() > 1) {
                                                    // Get instruction lists for each branch
                                                    final List<List<Inst>> branchInsts = branchList.stream()
                                                            .map(b -> b.<Call>as().insts())
                                                            .toList();

                                                    // Find common suffix length
                                                    int commonSuffixLen = 0;
                                                    final int minLen = branchInsts.stream().mapToInt(List::size).min().orElse(0);

                                                    for (int i = 1; i <= minLen; i++) {
                                                        final int offset = i;
                                                        final Inst firstInst = branchInsts.getFirst().get(branchInsts.getFirst().size() - offset);
                                                        final boolean allMatch = branchInsts.stream()
                                                                .allMatch(insts -> {
                                                                    final Inst inst1 = insts.get(insts.size() - offset);
                                                                    return inst1.tid().equals(firstInst.tid()) &&
                                                                            inst1.args().equals(firstInst.args());
                                                                });
                                                        if (allMatch) {
                                                            commonSuffixLen++;
                                                        } else {
                                                            break;
                                                        }
                                                    }

                                                    if (commonSuffixLen > 0 && commonSuffixLen < minLen) {
                                                        // Only optimize if there's a common suffix AND remaining instructions
                                                        // (don't optimize if all branches are identical - that's handled by collapse rewrite)

                                                        // Extract common suffix
                                                        final List<Inst> firstBranchInsts = branchInsts.getFirst();
                                                        final List<Inst> commonSuffix = firstBranchInsts.subList(
                                                                firstBranchInsts.size() - commonSuffixLen,
                                                                firstBranchInsts.size()
                                                        );

                                                        // Create new branches without the common suffix
                                                        final int commonSuffixLenFinal = commonSuffixLen;
                                                        final List<Obj> newBranches = branchInsts.stream()
                                                                .map(insts -> (Obj) MCode.of(insts.subList(0, insts.size() - commonSuffixLenFinal)).tryToInst())
                                                                .toList();

                                                        // Return: split(new_branches) + merge + common_suffix
                                                        return Stream.concat(
                                                                Stream.of(
                                                                        instB(SPLIT_INST_TID, lst(lst(newBranches))),
                                                                        instB(MERGE_INST_TID, lst())
                                                                ),
                                                                commonSuffix.stream()
                                                        ).toList();
                                                    }
                                                }
                                            }
                                            // No optimization possible, return original
                                            return matched;
                                        })).asCode())));
    }

    @Override
    public Set<Tuple.Triplet<Tuple.Pair<String, String>, List<fURI>, Integer>> sugars() {
        return new LinkedHashSet<>(List.of(
                Tuple.Triplet.with(Tuple.Pair.with("select", null), List.of(SELECT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("where", null), List.of(WHERE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("update", null), List.of(UPDATE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?==", null), List.of(WHERE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("%==", null), List.of(GROUP_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("==", null), List.of(SELECT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("=~", null), List.of(MATCHES_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?=~", null), List.of(IS_INST_TID, MATCHES_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?=", null), List.of(IS_INST_TID, EQ_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?>", null), List.of(IS_INST_TID, GT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?>=", null), List.of(IS_INST_TID, GTE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?<=", null), List.of(IS_INST_TID, LTE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?<", null), List.of(IS_INST_TID, LT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?!=", null), List.of(IS_INST_TID, NEQ_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?", null), List.of(ISA_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("@", null), List.of(AT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("|", null), List.of(BLOCK_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("_/", "\\_"), List.of(WITHIN_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("_", null), List.of(ID_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("⋅", null), List.of(MULT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("*", null), List.of(FROM_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">|", null), List.of(BARRIER_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">|", null), List.of(BARRIER_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with(">>-", null), List.of(RNG_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("-<|", null), List.of(CHOOSE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-<", null), List.of(SPLIT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("->", null), List.of(REF_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>=", null), List.of(UPDATE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_INST_TID), 0),
                //Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("++", null), List.of(MPLUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("+", null), List.of(PLUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-", null), List.of(MINUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(";", null), List.of(END_INST_TID), 0),
                //  Tuple.Triplet.with(Tuple.Pair.with("(", ")"), List.of(GET_INST_TID), 1),
                //Tuple.Triplet.with(Tuple.Pair.with("./", null), List.of(GET_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("!*", null), List.of(AUTO_FROM_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("!", null), List.of(AUTO_INST_TID), 1)));
        //   Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(PARENT_INST_TID), 0)));
        //  Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(LIFT_INST_TID), 1)));
    }

    @Override
    public Set<Inst> insts() {
        final Set<Inst> set = new LinkedHashSet<>();
        set.addAll(Fail.FailType.insts());
        set.addAll(NoObj.NoObjType.insts());
        set.addAll(Bytes.BytesType.insts());
        set.addAll(Bool.BoolType.insts());
        set.addAll(Int.IntType.insts());
        set.addAll(Real.TypeObj.insts());
        set.addAll(Str.StrType.insts());
        set.addAll(Uri.UriType.insts());
        set.addAll(Rel.RelType.insts());
        set.addAll(Rec.RecType.insts());
        set.addAll(Lst.LstType.insts());
        set.addAll(Inst.InstType.insts());
        set.addAll(Obj.ObjType.insts());
        set.addAll(Objs.ObjsType.insts());
        set.addAll(Type.TypeType.insts());
        set.addAll(Space.SpaceType.insts());
        //set.add(instC(f("/m/inst/card").dom(A).rng(B), lst(T(f("/m/sys/ui/card"))), (lhs, inst) -> str(lhs.toCleanString())));
        return set;
    }

}