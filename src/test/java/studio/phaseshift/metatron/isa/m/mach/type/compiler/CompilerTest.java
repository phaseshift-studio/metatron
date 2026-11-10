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

package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Compiler;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.resolver.FirstFindSelector;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.resolver.SpecificitySelector;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;

/**
 * EVERY MACHINE OWNS ITS COMPILER (Marko, 2026-11-10). push gives the child a deep clone of its parent's compiler,
 * stages and all: the child compiles as its parent did at the push, a write under the child's {@code ~/compiler}
 * stays the child's, and a write the parent makes after the push stays the parent's.
 */
public class CompilerTest extends AbstractMetatronTest {

    @Test
    public void testAPushedMachineOwnsItsCompiler() {
        final Machine parent = Machine.current();
        final Selector selector = parent.compiler().resolver().selector();
        // a check point the parent turns off before the push, which the child's clone carries
        parent.compiler().typer().at("check/inst_dom", BOOL_FALSE, Poly.MUTABLE);
        final Machine child = parent.push(f("own_compiler"));
        try {
            final Compiler own = child.compiler();
            assertNotSame(parent.compiler().resolver(), own.resolver(), "the child's resolver is its own");
            assertNotSame(parent.compiler().typer(), own.typer(), "and so is its typer");
            assertFalse(Typer.Helper.instDom(), "both start as the parent's were at the push");

            Machine.write(f("~/compiler/resolver/selector"), new FirstFindSelector());
            Machine.write(f("~/compiler/typer/check/inst_dom"), BOOL_TRUE);
            assertInstanceOf(FirstFindSelector.class, child.compiler().resolver().selector(), "the child's write is the child's");
            assertTrue(Typer.Helper.instDom(), "its typer's included");
            assertSame(selector, parent.compiler().resolver().selector(), "the parent keeps its selector");
            assertEquals(BOOL_FALSE, parent.compiler().typer().at("check/inst_dom"), "and its check point");

            parent.compiler().typer().at("check/type_pred", BOOL_FALSE, Poly.MUTABLE);
            assertTrue(Typer.Helper.typePred(), "a write the parent makes after the push never reaches the child");
        } finally {
            child.pop();
            parent.compiler().typer().at("check/inst_dom", BOOL_TRUE, Poly.MUTABLE);
            parent.compiler().typer().at("check/type_pred", BOOL_TRUE, Poly.MUTABLE);
            Machine.write(f("~/compiler/resolver/selector"), selector);
        }
    }

    /**
     * ONE CODE, TWO MACHINES, TWO RESOLUTIONS: the resolve cache keys a code by the selector and binder that resolve it,
     * so a child whose selector differs resolves a code its parent already resolved, never reading the parent's back.
     */
    @Test
    public void testACodeIsResolvedByTheMachineThatResolvesIt() {
        final AtomicInteger asked = new AtomicInteger();
        final Code code = ObjmtronSerializer.parse("start(1).plus(2)");
        final Machine parent = Machine.current();
        final Code byParent = parent.compiler().resolver().apply(code);
        assertTrue(Resolver.Helper.RESOLVE_CACHE.containsValue(byParent), "the parent's resolution is cached");
        final Machine child = parent.push(f("own_resolution"));
        try {
            Machine.write(f("~/compiler/resolver/selector"), new SpecificitySelector() {
                @Override
                public Inst apply(final Obj lhs, final Inst userInst, final Binder binder) {
                    asked.incrementAndGet();
                    return super.apply(lhs, userInst, binder);
                }
            });
            final Code byChild = child.compiler().resolver().apply(code);
            assertNotSame(byParent, byChild, "the child resolves the code itself");
            assertTrue(0 < asked.get(), "through its own selector");
            assertEquals(byParent, byChild, "to the same contracts, its selector being specificity's");
        } finally {
            child.pop();
        }
    }
}
