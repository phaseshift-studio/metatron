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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A q-proc is a contract of {@link Addressable}, not of {@link Space}: this class proves the layer against a
 * source that is not a place at all — a jurisdiction, a q-proc set, and its own read and write, with no pattern
 * matching, no routes and no direct reader.
 * <p>
 * The cases drive {@link QProc.Layer} directly, which is the honest unit for step S1: the dispatch does not
 * hand vids to sources through the layer yet (that is S2's network arm, then S3's memory arm), so the layer is
 * exercised where it is entered rather than through {@code Machine.read}/{@code Machine.write}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AddressableQProcTest extends AbstractMetatronTest {

    /**
     * A source that answers for one jurisdiction and counts what the pipeline let through to it. Extending
     * {@link MRec} is what gives it the {@code ?qproc} entry a declaration lives in — a source with no rec
     * behind it declares nothing (see {@link Addressable#qs()}).
     */
    private static final class Ledger extends MRec implements Addressable {

        private final fURI jurisdiction;
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger writes = new AtomicInteger();

        private Ledger(final fURI jurisdiction) {
            super(mutableMap(), REC_TID, null);
            this.jurisdiction = jurisdiction;
        }

        @Override
        public fURI pattern() {
            return this.jurisdiction;
        }

        @Override
        public Obj read(final fURI vid) {
            this.reads.incrementAndGet();
            return jnt(41);
        }

        @Override
        public Obj write(final fURI vid, final Obj obj) {
            this.writes.incrementAndGet();
            return obj;
        }
    }

    /**
     * A declaration is a property of every source, and it lands in the source's own rec so it is reflected into
     * mtron and travels with the source.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "peek % false % true  % a processor is visible to hasQ only once declared",
            "subq % false % true  % the same declaration works for a processor the source did not start with",
    }, delimiter = '%')
    void testDeclaration(final String q, final boolean before, final boolean after, final String description) {
        final Ledger ledger = new Ledger(f("/t/ledger/@"));
        assertEquals(before, ledger.hasQ(f(q)), description);
        ledger.addQ(QProc.Helper.build(f("/m/qproc/").extend(q), f(q)).create());
        assertEquals(after, ledger.hasQ(f(q)), description);
        assertEquals(1, ledger.qs().lstValue().size(), description + " (the declaration is the rec's ?qproc entry)");
        assertTrue(ledger.at(uri(QPROC)).isLst(), description + " (reflected as a value, not a Java field)");
    }

    /**
     * The read pipeline: a pre_read answer REPLACES the source's own read (which must not run), a post_read
     * DECORATES it, and a processor with neither leaves it alone.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "pre  % 99 % 0 % 0 % the pre_read answer stands in for the source's own read",
            "post % 41 % 1 % 1 % the post_read sees the read after it happened",
            "none % 41 % 1 % 0 % with no handler the source's own read is the answer",
    }, delimiter = '%')
    void testReadPipeline(final String stage, final long expected, final int sourceReads, final int postReads, final String description) {
        final Ledger ledger = new Ledger(f("/t/ledger/@"));
        final AtomicInteger posts = new AtomicInteger();
        final QProc proc = switch (stage) {
            case "pre" -> QProc.Helper.build(f("/m/qproc/peek"), f("peek"))
                    .preRead(vid -> jnt(99)).create();
            case "post" -> QProc.Helper.build(f("/m/qproc/peek"), f("peek"))
                    .postRead((vid, obj) -> {
                        posts.incrementAndGet();
                        return obj;
                    }).create();
            default -> QProc.Helper.build(f("/m/qproc/peek"), f("peek")).create();
        };
        ledger.addQ(proc);
        final fURI vid = f("/t/ledger/x").addQ("peek");
        assertEquals(jnt(expected), QProc.Layer.read(ledger, vid, () -> ledger.read(vid)), description);
        assertEquals(sourceReads, ledger.reads.get(), description);
        assertEquals(postReads, posts.get(), description);
    }

    /**
     * The write pipeline: a pre_write answer stands in for the source's own write; a qless GUARD runs before it and,
     * answering, stands in for it too; a qless BROADCAST runs only once it has landed, and never for a write a guard
     * refused; a post_write lands after it. {@code seen} is how many of the source's writes had happened when a qless
     * handler ran (-1: none ran) -- the column that says before or after.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "pre       % 99 % 0 % 0 % -1 % the pre_write answer stands in for the source's own write",
            "post      % 7  % 1 % 0 % -1 % the post_write sees the write after it happened",
            "guard     % 7  % 1 % 1 % 0  % a guard runs before the write and, answering noobj, lets it through",
            "veto      % 13 % 0 % 1 % 0  % a guard that answers stands in for the write, which never happens",
            "broadcast % 7  % 1 % 1 % 1  % the broadcast runs once the write has landed and does not consume it",
            "refused   % 13 % 0 % 0 % -1 % a write a guard refused is never broadcast, whatever the declaration order",
            "none      % 7  % 1 % 0 % -1 % with no handler the source's own write is the answer",
    }, delimiter = '%')
    void testWritePipeline(final String stage, final long expected, final int sourceWrites, final int qlessRuns,
                           final int seen, final String description) {
        final Ledger ledger = new Ledger(f("/t/ledger/@"));
        final AtomicInteger qless = new AtomicInteger();
        final AtomicInteger seenWrites = new AtomicInteger(-1);
        final BiFunction<fURI, Obj, Obj> witness = (vid, obj) -> {
            qless.incrementAndGet();
            seenWrites.set(ledger.writes.get());
            return noobj();
        };
        switch (stage) {
            case "pre" -> ledger.addQ(QProc.Helper.build(f("/m/qproc/shove"), f("shove"))
                    .preWrite((vid, obj) -> jnt(99)).create());
            case "post" -> ledger.addQ(QProc.Helper.build(f("/m/qproc/shove"), f("shove"))
                    .postWrite((vid, oldObj, newObj) -> newObj).create());
            case "guard" -> ledger.addQ(QProc.Helper.build(f("/m/qproc/shove"), f("shove"))
                    .qlessWrite(witness).create());
            case "veto" -> ledger.addQ(QProc.Helper.build(f("/m/qproc/shove"), f("shove"))
                    .qlessWrite((vid, obj) -> {
                        witness.apply(vid, obj);
                        return jnt(13);
                    }).create());
            case "broadcast" -> ledger.addQ(QProc.Helper.build(f("/m/qproc/shove"), f("shove"))
                    .broadcast(witness).create());
            case "refused" -> {
                // the broadcast is declared FIRST: a layer that ran every qless handler in declaration order would
                // publish the write before the guard refused it
                ledger.addQ(QProc.Helper.build(f("/m/qproc/tell"), f("tell")).broadcast(witness).create());
                ledger.addQ(QProc.Helper.build(f("/m/qproc/shove"), f("shove")).qlessWrite((vid, obj) -> jnt(13)).create());
            }
            default -> ledger.addQ(QProc.Helper.build(f("/m/qproc/shove"), f("shove")).create());
        }
        final fURI vid = f("/t/ledger/x").addQ("shove");
        assertEquals(jnt(expected), QProc.Layer.write(ledger, vid, jnt(7), () -> ledger.write(vid, jnt(7))), description);
        assertEquals(sourceWrites, ledger.writes.get(), description);
        assertEquals(qlessRuns, qless.get(), description);
        assertEquals(seen, seenWrites.get(), description);
    }

    /**
     * A query a SPACE never declared is named rather than silently ignored — and the check is the SOURCE's,
     * because only the source knows which queries it speaks. The layer must not refuse a query on a source's
     * behalf: the network is offered every host-bearing address and declines most of them, so a check there
     * would reject a {@code ?incrq} that belongs to the space the write is on its way to.
     */
    @Test
    void shouldNameAnUndeclaredQueryProcessor() {
        final Space space = memSpace.of(f("/t/ledgerq/#"), f("/sys/space/ledgerq"));
        space.addQ(QProc.Helper.build(f("/m/qproc/shove"), f("shove")).create());
        final fURI vid = f("/t/ledgerq/x").addQ("subq");
        final MTronException thrown = assertThrows(MTronException.class, () -> Machine.write(vid, jnt(1)));
        assertTrue(thrown.getMessage().contains("no subq query processor attached"),
                "the refusal names the processor and the jurisdiction it is missing from: " + thrown.getMessage());
    }
}
