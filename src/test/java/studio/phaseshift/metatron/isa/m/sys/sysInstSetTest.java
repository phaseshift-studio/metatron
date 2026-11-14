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

package studio.phaseshift.metatron.isa.m.sys;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.space.fsSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.nio.file.FileSystems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class sysInstSetTest extends AbstractInstSetTest {

    public sysInstSetTest() {
        super(sysInstSet::new);
    }

    @BeforeAll
    public static void loadFileSystem() {
        InstSet.importInstSet(MACH_ISA_TID);
        InstSet.importInstSet(WEB_ISA_TID);
        fsSpace.of(FileSystems.getDefault(), rec(
                uri(PATTERN), uri("test:#"),
                uri(ROUTE), rec(uri("test:"), uri("src/test/resources/isa/sys/space/sys"))
        ), f("/sys/space/test/fs"));
    }

    @Test
    public void testFindFile() {
        assertEquals(1, Machine.evaluate("find_file(name=><find_read_write_file.txt>,root=><test:.>).count()").intValue().intValue());
    }

    @Test
    public void testFindFileRegexIsGuarded() {
        // a regex name still searches the tree ...
        final Obj found = Machine.evaluate("find_file(name=>'find_read_write_file\\.txt$',root=><test:.>,pattern=>true)");
        assertFalse(found.isFail(), "a plain regex must still search: " + found);
        assertEquals(1, found.asLst().lstValue().size());
        // ... but a nested quantifier is refused before it can backtrack against every path in it
        checkCodeParseApply(LOG, "find_file(name=>'(f+)+$',root=><test:.>,pattern=>true)", "<ERROR>");
    }

    @Test
    public void testSysStatReportsPhysicalMachine() {
        final Obj stat = Machine.evaluate("sys_stat()");
        assertFalse(stat.isFail(), "sys_stat must answer: " + stat);
        // the box, not the vm's heap: memory + root filesystem are normalized byte reals,
        // cpu load is a percent real, the model is a str, cores is an int
        assertTrue(stat.asRec().at(uri("total_mem_phys")).isReal(), "total_mem_phys is a real");
        assertTrue(stat.asRec().at(uri("free_mem_phys")).isReal(), "free_mem_phys is a real");
        assertTrue(stat.asRec().at(uri("disk_total")).isReal(), "disk_total is a real");
        assertTrue(stat.asRec().at(uri("disk_free")).isReal(), "disk_free is a real");
        assertTrue(stat.asRec().at(uri("cpu_load")).isReal(), "cpu_load is a real (0..100)");
        assertTrue(stat.asRec().at(uri("cpu_model")).isStr(), "cpu_model is a str");
        assertTrue(stat.asRec().at(uri("processor")).isInt(), "processor is an int");
    }
}
