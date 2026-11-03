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

package studio.phaseshift.metatron.algebra;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.algebra.rewrite.Derivation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static studio.phaseshift.metatron.Tokens.ARGS;

/**
 * PENDING DECLARATIONS — the staging area an instruction set's algebra claims land in while its own class
 * initializes, before anything exists to publish them to.
 *
 * <p>WHY A STAGING AREA AT ALL: a declaration is made during an instset's static initialization, and at that moment
 * {@code BootLoader.ROOT_MACHINE} does not exist and the owning instset is not mounted. Both of the obvious
 * destinations are closed for exactly that reason. {@code ?lawq} is a SPACE write, so it needs a mounted space; and
 * there is no law table to write into, because {@code LawTable.TABLES} is private and a table is constructed per
 * family by its own subclass — a builder that runs before the family exists has no table instance to hand. So the
 * staging is forced by boot ordering rather than chosen for convenience, and {@code ?lawq} remains the store of
 * record: publishing happens at {@code drain}, when the space exists, and a later boot reads the declaration back
 * from the store.
 *
 * <p>THE STAGING IS ALSO THE QUERY INDEX: a rewrite pass asks a law question per instruction it visits, and answering
 * that from the store would be a space read beside every registered instruction — which is what made a law lookup a
 * per-evaluation cost. The staged maps are the index instead: a hash read, answerable the moment the declaration is
 * made and never a walk of the registry.
 *
 * <p>STATIC STATE, NOT AN INSTANCE — this registry is process-wide state, so it takes the shape
 * {@code InstBuilder.DECLARATIONS} already uses for its own index ({@code private static final} maps plus
 * {@code public static} methods): there is one boot in progress in a process, hence one staging area, and an instance
 * would have to be threaded through builders that run before anyone could receive it.
 *
 * <p>NOTHING IS EVER WRITTEN THROUGH HERE: no I/O, no logging, no space, and no mutation of an {@link Algebra} — the
 * algebra is read-only and the reference is stored as it came in. The maps answer reads; the only writes are
 * declarations landing, which is what the class is for.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PendingDeclarations {

    /**
     * THE INSTRUCTION ROWS, IN MEMORY, keyed by the OPERATION's address. This is the query index over what
     * {@code InstBuilder.drain} will publish to {@code ?lawq}: a rewrite pass reads a row from here instead of
     * walking the registered instructions and reading the store beside each.
     */
    private static final Map<fURI, InstRow> INST_ROWS = new ConcurrentHashMap<>();

    /**
     * The order instruction declarations were made in — the replay order {@link #instRows()} promises. A reader that
     * unions the rows must replay them deterministically, and a hash map's iteration order is not an order; the
     * declarations' own arrival order is.
     */
    private static final Queue<fURI> INST_ORDER = new ConcurrentLinkedQueue<>();

    /**
     * The ordered READ-ONLY view of {@link #INST_ROWS}, rebuilt only when a declaration lands — so the union a rewrite
     * pass reads is one plain map, not a copy and not a hash-ordered walk.
     */
    private static volatile Map<fURI, InstRow> INST_VIEW = Map.of();
    private static volatile List<fURI> INST_ORDER_VIEW = List.of();
    private static volatile int INST_VIEW_SIZE = -1;

    /**
     * THE TYPE ALGEBRAS, IN MEMORY, keyed by the declaring type's vid (its base path) — the same key
     * {@code Type.Builder.LAWS} writes, for the same reason: a type is built before its instset is mounted, so its
     * algebra is staged here and published to {@code ?lawq} by the drain that mounts the set.
     */
    private static final Map<fURI, Algebra> ALGEBRAS = new ConcurrentHashMap<>();

    /**
     * The order algebra declarations were made in, mirroring {@link #INST_ORDER} for the same reason: the view a
     * caller reads must not depend on a hash map's iteration order.
     */
    private static final Queue<fURI> ALGEBRA_ORDER = new ConcurrentLinkedQueue<>();

    private static volatile Map<fURI, Algebra> ALGEBRA_VIEW = Map.of();
    private static volatile int ALGEBRA_VIEW_SIZE = -1;

    /**
     * A registry, never a value — the constructor exists only to say so.
     */
    private PendingDeclarations() {
    }

    /**
     * ONE INSTRUCTION'S CLAIM: the process laws it obeys, the opposing edge it declares, and its defining equation —
     * staged as one unit because a law row is one claim, and a declaration carrying only an inverse or only a
     * derivation is as complete as one carrying only laws. The laws are copied and never null, so a row that claims
     * only a relation carries the empty list rather than a null a reader must test for.
     */
    public record InstRow(List<Law> laws, fURI inverse, Derivation derivation) {
        public InstRow {
            laws = null == laws ? List.of() : List.copyOf(laws);
        }
    }

    /**
     * A DECLARATION'S IDENTITY IS THE OPERATION'S, not the argument types the instruction happened to be minted with:
     * a law/inverse/derivation is a fact about the algebra, so the {@code args} component is stripped before the
     * index is read or written — the same key {@code InstBuilder.declarationKey} computes, so a lookup made here and
     * a lookup made there cannot disagree.
     */
    private static fURI instKey(final fURI instTid) {
        return null == instTid || !instTid.hasQ(ARGS) ? instTid : instTid.args(List.of());
    }

    /**
     * A TYPE'S IDENTITY IS ITS BASE PATH, not the spelling a caller happens to hold: the coefficient, the query and
     * the poly are erased ({@code obj?} and {@code obj} are the same type address), which is the key
     * {@code Type.Builder.LAWS} uses. A vid whose base path is absent is its own key.
     */
    private static fURI algebraKey(final fURI vid) {
        return null == vid ? null : (null == vid.basePath() ? vid : vid.basePath());
    }

    /**
     * STAGE one instruction's declaration and answer the row that was staged. The address is the OPERATION's, so a
     * row declared at {@code inst?rng=int&dom=int&args=[int]} answers a lookup at {@code inst?rng=int&dom=int}: the
     * arguments are part of how the instruction was minted, not part of what it declares.
     * <p>
     * NULL IS NOT A DECLARATION: a null address stages nothing and answers null rather than throwing, because this is
     * the write path every instset's static initialization runs through and a missing address is not worth failing a
     * boot over. A re-declaration at the same operation replaces the row and keeps its original position in the
     * replay order.
     */
    public static InstRow declareInst(final fURI instTid, final List<Law> laws, final fURI inverse,
                                      final Derivation derivation) {
        final fURI key = instKey(instTid);
        if (null == key)
            return null;
        final InstRow row = new InstRow(laws, inverse, derivation);
        INST_ROWS.put(key, row);
        INST_ORDER.add(key);
        return row;
    }

    /**
     * THE ROW AN OPERATION STAGED, or null when its address made no claim — a hash read keyed by the operation, so
     * the arguments the queried spelling carries are stripped exactly as they were on the way in.
     */
    public static InstRow findInst(final fURI instTid) {
        final fURI key = instKey(instTid);
        return null == key ? null : INST_ROWS.get(key);
    }

    /**
     * EVERY STAGED INSTRUCTION ROW, in DECLARATION order, as an UNMODIFIABLE view — the union a rewrite pass reads.
     * Nothing is written through it: {@code ?lawq} stays the store of record, and this is the in-memory index over
     * what the drain will publish. The view is rebuilt only when a declaration lands, so a pass pays one map read
     * rather than a copy.
     */
    public static Map<fURI, InstRow> instRows() {
        if (INST_VIEW_SIZE != INST_ORDER.size()) {
            final Map<fURI, InstRow> view = new LinkedHashMap<>();
            for (final fURI key : INST_ORDER) {
                final InstRow row = INST_ROWS.get(key);
                if (null != row)
                    view.put(key, row);
            }
            INST_VIEW = Collections.unmodifiableMap(view);
            INST_ORDER_VIEW = List.copyOf(view.keySet());
            INST_VIEW_SIZE = INST_ORDER.size();
        }
        return INST_VIEW;
    }

    /**
     * The OPERATION addresses the staged rows replay in — the order the declarations arrived in, which is what a
     * reader that unions them must reproduce (a hash map's iteration order is not an order). It is the key sequence of
     * {@link #instRows()}, so the two reads can never disagree about the order.
     */
    public static List<fURI> instOrder() {
        if (INST_VIEW_SIZE != INST_ORDER.size())
            instRows();
        return INST_ORDER_VIEW;
    }

    /**
     * STAGE a type's algebra and answer the algebra that was staged. The key is the TYPE's vid base path, not the
     * spelling the caller holds: the declaration is a fact about the type, and every spelling of that type address
     * asks the same question.
     * <p>
     * The algebra is a read-only value, so the reference is stored as it came in — it is never mutated, wrapped or
     * translated. A null vid or a null algebra stages nothing and answers null, for the same reason a null inst
     * address does: this is a hot write path taken during static initialization.
     */
    public static Algebra declareAlgebra(final fURI vid, final Algebra algebra) {
        final fURI key = algebraKey(vid);
        if (null == key || null == algebra)
            return null;
        ALGEBRAS.put(key, algebra);
        ALGEBRA_ORDER.add(key);
        return algebra;
    }

    /**
     * THE ALGEBRA A TYPE STAGED, or null when it staged none — a hash read by the vid's base path, so a caller
     * holding any spelling of the type's address is answered.
     */
    public static Algebra findAlgebra(final fURI vid) {
        final fURI key = algebraKey(vid);
        return null == key ? null : ALGEBRAS.get(key);
    }

    /**
     * EVERY STAGED TYPE ALGEBRA, in DECLARATION order, as an UNMODIFIABLE view — the union the type side of a drain
     * reads, rebuilt only when an algebra lands.
     */
    public static Map<fURI, Algebra> algebras() {
        if (ALGEBRA_VIEW_SIZE != ALGEBRA_ORDER.size()) {
            final Map<fURI, Algebra> view = new LinkedHashMap<>();
            for (final fURI key : ALGEBRA_ORDER) {
                final Algebra algebra = ALGEBRAS.get(key);
                if (null != algebra)
                    view.put(key, algebra);
            }
            ALGEBRA_VIEW = Collections.unmodifiableMap(view);
            ALGEBRA_VIEW_SIZE = ALGEBRA_ORDER.size();
        }
        return ALGEBRA_VIEW;
    }

    /**
     * HOW MANY DECLARATIONS ARE PENDING — instruction rows plus type algebras, the ledger a store-residue snapshot
     * keys on. It counts distinct addresses, so re-declaring the same operation or type does not inflate it: the
     * question is what the next drain has to publish, not how many times a builder ran.
     */
    public static int size() {
        return INST_ROWS.size() + ALGEBRAS.size();
    }

    /**
     * DROP EVERY STAGED DECLARATION — TESTS ONLY. The registry is process-wide, so a test declares into the same
     * staging area the VM's own boot fills; this is how a test starts from an empty ledger. It is package-private on
     * purpose: there is no public undo here because the staging is not the record — {@code ?lawq} is, and it is not
     * touched.
     */
    static void clear() {
        INST_ROWS.clear();
        INST_ORDER.clear();
        ALGEBRAS.clear();
        ALGEBRA_ORDER.clear();
        INST_VIEW = Map.of();
        INST_ORDER_VIEW = List.of();
        INST_VIEW_SIZE = -1;
        ALGEBRA_VIEW = Map.of();
        ALGEBRA_VIEW_SIZE = -1;
    }
}
