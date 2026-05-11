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

import dev.langchain4j.agent.tool.P;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.UUID;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class VirtualThread extends AbstractThread {

    private Thread thread;
    volatile FutureObj<Obj> future = new FutureObj<>(UUID.randomUUID());

    public VirtualThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.thread = Thread.ofVirtual()
                .name(this.vid() == null ? "metatron-virtual-thread" : this.vid().toString())
                .unstarted(() -> {
                    this.at(STATE, uri("running"), MUTABLE);
                    final Obj result = this.at(CODE).apply(this.at(START));
                    this.at(RESULT, result, MUTABLE);
                    this.future.setObj(result);
                    this.at(STATE, uri("stopped"), MUTABLE);
                });
    }


    @Override
    public Fail stop() {
        try {
            this.thread.interrupt();
            return fail("interrupted").c(cInt.ZERO()).asFail();
        } catch (final Exception e) {
            return fail(e);
        }
    }
    
    @Override
    public Obj at(final Obj key) {
        if(!key.equals(uri(RESULT)))
            return super.at(key);
        else {
            try {
                if (this.future.isDone())
                    return this.future.get();
                else {
                    return Obj.none();
                }
            } catch(final Exception e) {
                throw MTronException.of(e);
            }
        }
    }

    @Override
    public NoObj pause() {
        return noobj(); // use semaphone that wraps the run task
    }

    @Override
    public Obj result() {
        return this.at(RESULT);
    }

    @Override
    public FutureObj<Obj> apply(final Obj other) {
        this.jvm().put(uri(START), other);
        this.thread.start();
        return this.future;
    }
}
