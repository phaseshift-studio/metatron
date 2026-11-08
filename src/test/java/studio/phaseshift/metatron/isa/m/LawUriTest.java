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

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.algebra.rewrite.plan.Cut;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE LAW VOCABULARY IS DATA — the law axis is no longer a per-law Java type, it is where the uri lives:
 * {@code /m/law/<label>} holds a plain {@code uri} for every {@link Law} constant, and the type {@code law::T}
 * (registered at {@link Law#LAW_TID}) is the union of those labels (Marko, 2026-11-08). These tests read the store and
 * the type:
 * <ol>
 *   <li>every constant is stored at its address and the wildcard {@code /m/law/+} read lists them;</li>
 *   <li>{@code law::T} is a union of the labels: it admits every one of them and refuses anything else;</li>
 *   <li>each law publishes its documentation at {@code ?docq} and its shape declaration at {@code ?decq};</li>
 *   <li>registration is IDEMPOTENT — a second call adds no address to the retained log;</li>
 *   <li>the vocabulary is CLOSED — a uri written at {@code /m/law/<label>} after boot is stored, and is no law: users
 *       assert the existing labels and declare no new logics (ruling 2026-10-21).</li>
 * </ol>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LawUriTest extends AbstractMetatronTest {

    /**
     * (a) EVERY LAW IS A URI IN THE STORE. The boot registration published one value per constant, and the wildcard
     * read the owner proved at the console (the wildcard {@code /m/law/+}) lists exactly that vocabulary.
     */
    @Test
    void testEveryLawIsStoredAtItsAddress() {
        final Set<String> labels = Machine.read(Law.LAW_TID.extend("+")).stream()
                .filter(Obj::isUri)
                .map(law -> law.uriValue().name())
                .collect(Collectors.toSet());
        for (final Law law : Law.values()) {
            assertTrue(labels.contains(law.name()),
                    law + " is not stored at " + law.address() + "; stored labels: " + labels);
            assertEquals(law.name(), Machine.read(law.address()).uriValue().name(),
                    "the value at " + law.address() + " is the law's own label");
        }
    }

    /**
     * (b) THE TYPE IS A UNION OF THE LABELS (Marko, 2026-11-08): {@code law::T} is
     * {@code uri::T[?union(absorbing, …, cyclic)]}, so reading the type says what it admits. Every constant's label is
     * admitted, the last of the 22 too (a union is cut at no fixed arity), and anything it does not name is refused; the
     * type answers the match or {@code noobj()}, never a boolean.
     */
    @Test
    void testTheLawTypeIsAUnionOfTheLabels() {
        final Type lawType = T(Law.LAW_TID);
        assertTrue(lawType.isType(), "law::T is registered at " + Law.LAW_TID + ", got " + lawType);
        assertTrue(lawType.toString().contains("union("), "law::T states its labels as a union, got " + lawType);
        for (final Law law : Law.values())
            assertFalse(lawType.apply(uri(law.name())).isNoObj(), "law::T admits uri(" + law + ")");
        assertTrue(lawType.apply(uri("not_a_law")).isNoObj(), "the union names no not_a_law, so law::T refuses it");
        assertTrue(lawType.apply(str("pure")).isNoObj(), "a str is no uri, so law::T refuses 'pure'");
    }

    /**
     * (c) EVERY LAW PUBLISHES ITS PROSE AND ITS SHAPES. The documentation is the one source
     * {@link Law#documentation()} states (served through {@code ?docq}), and the declaration is the shape rec the
     * analyzer reads: {@code [inst => lst(<shape uris>)]}.
     */
    @Test
    void testEachLawPublishesItsDocumentationAndShapes() {
        for (final Law law : Law.values()) {
            final Obj doc = Machine.read(PendingWrites.docq(law.address()));
            assertTrue(doc.isRec(), law + "'s documentation is readable through ?docq, got " + doc);
            assertEquals(law.documentation(), doc.asRec().at(uri(DESC)).strValue(),
                    law + "'s ?docq desc is the prose documentation() publishes");
            final Obj declaration = Machine.read(PendingWrites.decq(law.address()));
            assertTrue(declaration.isRec(), law + "'s declaration is readable through ?decq, got " + declaration);
            final Obj shapes = declaration.asRec().atDirect(uri(INST));
            assertTrue(shapes.isLst(), law + "'s ?decq states [inst => lst(shape uris)], got " + declaration);
            assertEquals(law.shapes().stream().map(fURI::toString).toList(),
                    shapes.lstValue().stream().map(shape -> shape.uriValue().toString()).toList(),
                    law + "'s ?decq shapes are exactly Law.shapes()");
        }
    }

    /**
     * (d) REGISTRATION IS IDEMPOTENT. The log is keyed by address, so a second call re-stages the same addresses
     * rather than appending a duplicate set — the property that makes the boot hook safe on a re-mount.
     */
    @Test
    void testRegisteringTwiceDoesNotDuplicateAnything() {
        Law.register();
        final int once = PendingWrites.size();
        Law.register();
        assertEquals(once, PendingWrites.size(), "a second Law.register() staged no new address");
        for (final Law law : Law.values())
            assertEquals(1, PendingWrites.writes().stream()
                            .filter(write -> write.address().equals(law.address())).count(),
                    law + " has exactly one staged write at " + law.address());
    }

    /**
     * (e) THE VOCABULARY IS CLOSED (ruling 2026-10-21; Marko, 2026-11-08): users assert the existing law labels and
     * declare no new logics, so {@code law::T} is the union of the constants' labels. A uri written at
     * {@code /m/law/probe_law} after boot is stored like any value, and {@code law::T} still refuses it.
     */
    @Test
    void testAWrittenUriIsNoNewLaw() {
        final fURI probe = Law.LAW_TID.extend("probe_law");
        Machine.write(probe, uri("probe_law"));
        assertEquals("probe_law", Machine.read(probe).uriValue().name(), "the store holds the written uri");
        assertTrue(T(Law.LAW_TID).apply(uri("probe_law")).isNoObj(),
                "law::T is the union of the declared labels, so probe_law is no law");
    }

    /**
     * A TYPE-VALUED LAW SLOT MEANS "ANY LAW". A declaration may state {@code law => /m/law} (the type's address,
     * {@code law::T}) instead of listing labels; every reader that turns the slot into a law set must expand it to the
     * whole vocabulary rather than let the non-lst form silently disable the check. {@link Cut#laws()} is the reader
     * the rewrite plans use.
     */
    @Test
    void testATypeValuedLawSlotMeansAnyLaw() {
        final Rec declaration = rec(uri(LAW), uri(Law.LAW_TID));
        final Cut cut = Cut.of(declaration);
        assertEquals(Law.values().length, cut.laws().size(),
                "law => law::T expands to the whole vocabulary, got " + cut.laws());
        assertTrue(cut.laws().containsAll(List.of(Law.values())),
                "every law is demanded by a law::T slot, got " + cut.laws());
    }
}
