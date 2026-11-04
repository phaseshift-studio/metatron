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

package studio.phaseshift.metatron.isa.m.mach.type.processor;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Computer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.COMPUTER;
import static studio.phaseshift.metatron.Tokens.PROCESSOR;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_SWARM_PROCESSOR_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * BasicSwarmProcessor — the swarm architecture: a machine component (never a thread) that mints a
 * {@link BasicSwarmComputer} per {@link #apply(Code)} and indexes it. Zero Java fields; the computers index lives in
 * the Rec jvm map under {@code computers}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicSwarmProcessor extends MRec implements Processor {

    public BasicSwarmProcessor(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.jvm().putIfAbsent(uri(COMPUTER), lst());
    }

    public static BasicSwarmProcessor processor(final Map<Obj, Obj> state, final fURI tid, final fURI vid) {
        return new BasicSwarmProcessor(new LinkedHashMap<>(state), tid, vid);
    }

    public static BasicSwarmProcessor of() {
        return new BasicSwarmProcessor(new LinkedHashMap<>(), MACH_SWARM_PROCESSOR_TID, Machine.current().vid().extend(PROCESSOR));
    }

    @Override
    public BasicSwarmComputer apply(final Code code) {
        final BasicSwarmComputer computer = BasicSwarmComputer.of(code);
        //final Lst index = this.atDirect(uri(COMPUTER)).orElse(lst()).asLst();
        //if (!index.isNoObj())
        //   index.add(computer, MUTABLE);
        Machine.write(CommonUtil.mintShortUUID(Machine.current().vid().extend(PROCESSOR).extend(COMPUTER), true), computer);
        return computer;
    }

    @Override
    public Lst computers() {
        return this.at(uri(COMPUTER)).orElse(lst0()).asLst();
    }

    @Override
    public void close() {
        this.computers().stream().forEach(obj -> {
            if (obj instanceof Computer computer)
                computer.stop();
        });
        this.at(uri(COMPUTER), lst(), MUTABLE);
    }
}
