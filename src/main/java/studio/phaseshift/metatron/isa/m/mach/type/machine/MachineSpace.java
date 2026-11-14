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

package studio.phaseshift.metatron.isa.m.mach.type.machine;

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Compiler;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.MEM_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * MACHINE SPACE — a machine's own address space (Marko, 2026-10-25: "this space will be the primary space when
 * accessing a Machine and will be used solely for the Machine sphere of influence"), and its ONLY one: mounted in the
 * machine's memory at {@code <vid>/#} (the root's is {@code /#}), so memory's ordinary routing reaches it (Marko,
 * 2026-11-05: the sphere and the machine's data level are one space, "necessary and sufficient"). The innermost level
 * answers first ({@link studio.phaseshift.metatron.isa.m.mach.type.memory.MemoryUnion}), {@code ~} resolves there, and
 * a more specific space — a schema instset at {@code /sys/space/<name>/instset/#}, a space mounted beneath the vid —
 * wins its own addresses, as the most specific space always does. What it answers itself:
 * <ul>
 *     <li>{@code <vid>} — the machine itself, live;</li>
 *     <li>{@code <vid>/network/…} — handed to the network, a space of its own (a memSpace cache behind it): its fields
 *     and its members at their keys' paths are read as any memSpace's data, a member (a peer, a listener) is
 *     constructed, never written, and {@code -> noobj} retracts it
 *     ({@link studio.phaseshift.metatron.isa.m.mach.type.network.BasicNetwork}). The component itself, at
 *     {@code <vid>/network}, is read live as every component is;</li>
 *     <li>{@code <vid>/processor/…} — the processor, read live and written in place: the computers it indexes
 *     ({@code computer/<n>}) and the fail stacks, the machine's ({@code fail}) and each evaluation's under the computer
 *     that ran it ({@code computer/<n>/fail}), whose {@code ?incrq} keys this space's q-processors mint;</li>
 *     <li>{@code <vid>/compiler/…} — written in place: the typer's state lives at
 *     {@code <vid>/compiler/typer/check};</li>
 *     <li>{@code <vid>/<component>/analyzer/…} — any component's analyzer, the network's included: read live through
 *     the component that carries it, written in place (a write declares its settings, never replaces it), and
 *     subscribed to here, so one {@code ?subq} registry hears every analyzer's publication;</li>
 *     <li>{@code <vid>/<component>/…} — any component, read live; a branch pattern answers each match at its address
 *     ({@code vid => obj}), as a space's does, which is what {@code @} needs to anchor a value. What the component
 *     does not hold, the store answers: a write under {@code ~/memory} or {@code ~/instset} that is no part of it
 *     lands there;</li>
 *     <li>everything else — {@code <vid>/sys/#} (threads, processors and the log sink: Marko, 2026-10-25, "I would
 *     have sys/# simply go into MachineSpace"), the machine's data and every query — held in this space's OWN store
 *     (it is a {@link memSpace} for that), through its q-processors. The machine itself is never stored here.</li>
 * </ul>
 */
public final class MachineSpace extends memSpace {

    private static final String SYS = Machine.Helper.SYS_DIR.toString();

    /**
     * the segments a machine is made of, below its vid: its components and its system information
     */
    private static final Set<String> SPHERE = Set.of(INSTSET, COMPILER, MEMORY, NETWORK, PROCESSOR, SYS);

    private final Machine owner;
    private final fURI root;
    /**
     * how many segments the machine's vid has: an address's next segment, at this index, names the machine's part.
     * The root's resolved vid {@code /} has none, so its depth is 0.
     */
    private final int depth;

    /**
     * The space of {@code machine}, which must have a vid: its pattern is the machine's resolved vid ({@code /.} is
     * {@code /}) and everything below it, and it is indexed in the machine's memory as {@code <vid>/space}.
     */
    public MachineSpace(final Machine machine) {
        super(mutableMap(uri(PATTERN), uri(machine.vid().resolve().extend(ALL))), MEM_SPACE_TID,
                machine.vid().resolve().extend(SPACE), false);
        this.owner = machine;
        this.root = machine.vid().resolve();
        this.depth = this.root.segments().size();
        // the q-processors the machine's addresses speak: a ?docq, ?subq, ?lockq or ?mimeq lands here, and so does an
        // ?incrq — a fail's on ~/processor/…/fail among them
        this.addQ(QCollection.docQ());
        this.addQ(QCollection.decQ());
        this.addQ(QCollection.subQ());
        this.addQ(QCollection.incrQ());
        this.addQ(QCollection.mimeQ());
        this.addQ(QCollection.lockQ());
    }

    /**
     * Is {@code vid} one of the machine's own parts — the machine itself, one of its components, or its system
     * information — rather than its data? The address must already be absolute: memory resolves {@code ~}.
     */
    public boolean covers(final fURI vid) {
        if (null == this.root)
            return false;
        final fURI at = vid.qLess().resolve();
        // A SCHEME OR A HOST NAMES ANOTHER RESOURCE OR ANOTHER PROCESS, whatever its path: ws://peer:8555/network is
        // the peer's network, not this machine's. The check is here because hasPrefix leaves both unchecked when the
        // prefix has neither — and no machine vid does, so every ws://…/sys/… would otherwise be the root's.
        if (at.hasScheme() || at.hasHost() || !at.hasPrefix(this.root))
            return false;
        final List<String> segments = at.segments();
        return segments.size() == this.depth || (segments.size() > this.depth && SPHERE.contains(segments.get(this.depth)));
    }

    /**
     * the part an address it covers names — the segment below the machine's vid — or {@code null} for the machine itself
     */
    private String part(final fURI vid) {
        final List<String> segments = vid.qLess().resolve().segments();
        return segments.size() > this.depth ? segments.get(this.depth) : null;
    }

    /**
     * whether an address it covers names a component's analyzer, {@code <vid>/<component>/analyzer[/…]}
     */
    private boolean analyzes(final fURI vid) {
        final List<String> segments = vid.qLess().resolve().segments();
        return segments.size() > this.depth + 1 && ANALYZER.equals(segments.get(this.depth + 1));
    }

    @Override
    public Obj read(final fURI vid) {
        if (null == this.owner || !this.covers(vid))
            return super.read(vid);
        final String part = this.part(vid);
        // the network answers below itself, a space of its own: fields, and members at their keys' paths (and the
        // q-procs it speaks); the component's own address is read live, as every component's is. ITS ANALYZER is the
        // machine's to answer, as every component's is: one ?subq registry then hears every analyzer
        // (~/+/analyzer/analysis?subq), and the analyzer is read live through the component that carries it
        if (NETWORK.equals(part) && this.below(vid) && !this.analyzes(vid))
            return this.owner.network().read(vid);
        // ~/sys, and a query on the machine or a component, are the store's: the component itself is no doc
        if (SYS.equals(part) || vid.hasQ())
            return super.read(vid);
        if (null == part)
            return this.owner;
        // any other component, read live: the path below the machine walked into the component as it stands (the
        // whole remaining path, so <vid>/memory/previous walks into the memory rather than collapsing to its name)
        final List<String> segments = vid.qLess().resolve().segments();
        // A BRANCH PATTERN (what @ reads) answers each match at its address, vid => obj, as a space's pattern read
        // does: @ stamps the value with it, so a fail read that way from a stack can be caught out of it
        final Obj component = this.owner.at(uri(segments.get(this.depth)));
        final Obj live = vid.isBranch() && vid.hasPattern() && component.isPoly()
                ? objs(Space.Helper.unrollPoly(this.root.extend(segments.get(this.depth)), component.as(), vid.qLess().resolve().asNode())
                        .stream()
                        .filter(id -> !id.obj().isNoObj())
                        .map(id -> rel(id.furi().toUri(), id.obj())))
                : this.owner.at(uri(String.join("/", segments.subList(this.depth, segments.size()))));
        // what the component does not hold is the store's: a write under ~/memory or ~/instset that is no part of the
        // component lands there (the compiler's and the processor's fold into theirs, and the network writes its own)
        return live.isNoObj() ? super.read(vid) : live;
    }

    /**
     * whether an address it covers lies below the part it names, rather than naming the part itself
     */
    private boolean below(final fURI vid) {
        return vid.qLess().resolve().segments().size() > this.depth + 1;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final String part = null == this.owner || !this.covers(vid) ? null : this.part(vid);
        final boolean analyzes = null != part && this.analyzes(vid);
        // the network writes below itself: a field into its cache, a member's deletion its retraction, a value at a
        // member refused (BasicNetwork); the component's own address is the store's, as every component's is
        if (NETWORK.equals(part) && this.below(vid) && !analyzes)
            return this.owner.network().write(vid, obj);
        // A COMPONENT'S ANALYZER IS WRITTEN IN PLACE, NEVER REPLACED: the analyzer IS the component's meters, so a write
        // at it declares its settings (step, profile) field by field, and a write below it sets the one field. Its
        // analysis is derived from the meters and takes no write. A ?subq registers in this space, the one registry.
        if (analyzes && !vid.hasQ() && this.owner.at(uri(part)) instanceof Component component) {
            final List<String> segments = vid.qLess().resolve().segments();
            final List<String> field = segments.subList(this.depth + 2, segments.size());
            if (!field.isEmpty() && ANALYSIS.equals(field.getFirst())) {
                LOG.warn("an analysis is derived from its meters, not written: %s refused", vid);
                return noobj();
            }
            return QProc.Layer.write(this, vid, obj, () -> {
                final Analyzer analyzer = component.analyzer();
                if (!field.isEmpty())
                    analyzer.at(uri(String.join("/", field)), obj, MUTABLE);
                else if (obj.isRec())
                    obj.asRec().jvm().forEach((key, value) -> {
                        if (!uri(ANALYSIS).equals(key))
                            analyzer.at(key, value, MUTABLE);
                    });
                return obj;
            });
        }
        // the instset writes its table doors, ~/instset/<table>/<name>, into this machine's own level (BasicInstSet)
        if (INSTSET.equals(part) && !vid.hasQ()) {
            final List<String> segments = vid.qLess().resolve().segments();
            if (segments.size() == this.depth + 3 && Set.of(REWRITE, INST, TYPE, CONST).contains(segments.get(this.depth + 1)))
                return this.owner.instset().write(f(String.join("/", segments.subList(this.depth + 1, segments.size()))), obj);
        }
        // a component is the machine's own rec, so its write folds there — through the COMPONENT's own rec, which is
        // the object the machine holds (writing the machine rec itself would re-type the whole machine). The compiler
        // holds the typer's state; the processor indexes its computers and holds the fail stacks, written through the
        // q-processors this space speaks so a fail's ?incrq mints its key in the stack it is filed in
        final boolean folds = (COMPILER.equals(part) && !vid.hasQ())
                || (PROCESSOR.equals(part) && (!vid.hasQ() || vid.hasQ(QCollection.INCRQ)));
        if (!folds)
            return super.write(vid, obj);
        final List<String> segments = vid.qLess().resolve().segments();
        final Obj below = uri(String.join("/", segments.subList(this.depth + 1, segments.size())));
        if (COMPILER.equals(part)) {
            // A COMPILER WRITE IS CHECKED BY THE COMPILER IN FORCE, NEVER BY ITSELF. compiler::T's predicate runs
            // through the processor, and runtime resolution asks the machine's own resolver, so a write typed IN PLACE
            // was checked by the stage it was installing: a first-find selector resolved the predicate first-match
            // and, in a vocabulary wide enough for the first match to be wrong, refused itself. So the write lands in a
            // CANDIDATE: a copy of the compiler and of every stage along the written path (the other stages shared),
            // written in place (MUTABLE installs the very obj given, where an IMMUTABLE copy of an equal map answers the
            // original) and typed at each level while the machine still compiles with the live compiler. Only a
            // candidate that passed becomes the live compiler's stages.
            final Compiler live = this.owner.compiler();
            final Rec candidate = live.clone().asRec();
            Rec level = candidate;
            for (final String stage : segments.subList(this.depth + 1, segments.size() - 1)) {
                final Obj held = level.atDirect(uri(stage));
                if (!held.isRec())
                    break;
                final Rec copy = held.clone().asRec();
                level.jvm().put(uri(stage), copy);
                level = copy;
            }
            candidate.at(below, obj, MUTABLE);
            live.self(candidate.jvm(), live.tid(), live.vid());
            return obj;
        }
        return QProc.Layer.write(this, vid, obj, () -> {
            this.owner.processor().at(below, obj, MUTABLE);
            return obj;
        });
    }
}
