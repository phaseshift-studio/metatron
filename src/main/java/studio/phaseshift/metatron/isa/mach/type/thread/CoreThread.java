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

package studio.phaseshift.metatron.isa.mach.type.thread;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.type.net.FutureObj;

import java.util.Map;
import java.util.UUID;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CoreThread extends AbstractThread {

    final Machine machine;
    final FutureObj<Obj> future = new FutureObj<>(UUID.randomUUID());

    public CoreThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.machine = SwarmMachine.of(this.at(CODE).as());
    }

    @Override
    public Fail stop() {
        return this.machine.interrupt();
    }

    @Override
    public NoObj pause() {
        return this.machine.pause();
    }

    @Override
    public Obj result() {
        return this.at(RESULT);
    }

    @Override
    public FutureObj<Obj> run() {
        BootLoader.getExecutor().submit(() -> {
            this.at(STATE, uri("running"), MUTABLE);
           final Obj result = this.machine.apply();
            this.future.setObj(result);
            this.at(STATE, uri("stopped"), MUTABLE);
            this.at(RESULT).apply(result);
        });
        return this.future;
    }
}
