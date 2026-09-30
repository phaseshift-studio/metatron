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

package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Compiler;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Processor;
import studio.phaseshift.metatron.isa.mach.type.compiler.DefaultCompiler;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.util.MTronException;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.mach.machInstSet.*;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Machine-universal contract: every {@link Machine} implementation inherits these tests for free.
 * They pin the state of the machine as components are bound and fetched — eager instances round-trip
 * as themselves, templates mint on fetch, and the machine back-ref resolves through the parent nest.
 */
public abstract class AbstractMachineTest extends AbstractMetatronTest {

    protected abstract Machine newMachine();

    // ======================== eager round-trip ========================

    public AbstractMachineTest(final Supplier<InstSet> instSetSupplier) {
        // super(instSetSupplier);
    }

    //@BeforeAll
    public static void start() {
        // AbstractMetatronTest.begin();
        // InstSet.importInstSet(f("#"));
    }

    @Test
    public void testAddEagerProcessorThenFetch() {
        final Machine machine = this.newMachine();
        final Processor processor = SwarmProcessor.processor(mutableMap(), MACH_SWARM_PROCESSOR_TID, null);
        machine.processor(processor);
        assertEquals(processor, machine.processor(), "fetch returns the bound processor instance");
    }

    @Test
    public void testAddEagerCompilerThenFetch() {
        final Machine machine = this.newMachine();
        final Compiler compiler = new DefaultCompiler();
        machine.compiler(compiler);
        assertEquals(compiler, machine.compiler(), "fetch returns the bound compiler instance");    }

    // ======================== template minting ========================

    @Test
    public void testTemplateProcessorMintsOnFetch() {
        final Machine machine = this.newMachine();
        machine.processor(start_(rec()).as_(MACH_SWARM_PROCESSOR_TYPE).tryToInst());
        final Processor processor = machine.processor();
        assertInstanceOf(SwarmProcessor.class, processor, "a processor template mints a swarm processor on fetch");
    }

    @Test
    public void testTemplateCompilerMintsOnFetch() {
        final Machine machine = this.newMachine();
        machine.compiler(start_(rec()).as_(MACH_DEFAULT_COMPILER_TYPE).tryToInst());
        final Obj compiler = machine.compiler();
        LOG.warn("compiler: %s", compiler);
        assertEquals(MACH_DEFAULT_COMPILER_TID, compiler.tid());
        assertInstanceOf(DefaultCompiler.class, compiler, "a compiler template mints a default compiler on fetch");
    }

    // ======================== absence ========================

    @Test
    public void testFetchProcessorWhenAbsentThrows() {
        final Machine machine = this.newMachine().processor(noobj());
        assertThrows(MTronException.class, machine::processor, "fetching an unbound processor fails");
    }

    @Test
    public void testFetchCompilerWhenAbsentThrows() {
        final Machine machine = this.newMachine().compiler(noobj());
        assertThrows(MTronException.class, machine::compiler, "fetching an unbound compiler fails");
    }

    // ======================== back-ref ========================

    @Test
    public void testProcessorMachineBackRef() {
        final Machine machine = this.newMachine();
        final Processor processor = SwarmProcessor.processor(mutableMap(), MACH_SWARM_PROCESSOR_TID, null);
        machine.processor(processor);
        assertSame(machine, processor.machine(), "a bound processor resolves its machine through the parent nest");
    }

    @Test
    public void testCompilerMachineBackRef() {
        final Machine machine = this.newMachine();
        final Compiler compiler = new DefaultCompiler();
        machine.compiler(compiler);
        assertSame(machine, compiler.machine(), "a bound compiler resolves its machine through the parent nest");
    }
}
