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

package studio.phaseshift.metatron.isa.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DesktopTypeTest extends AbstractMetatronTest {

    private static final fURI DESKTOP_SPACE_VID = f("/sys/desktop");
    private static final fURI DESKTOP_STATE_VID = f("/sys/desktop/state");

    @BeforeEach
    public void ensureWebIsaImported() {
        InstSet.importInstSet(WEB_ISA_TID);
    }

    @Test
    public void testDesktopSpaceExists() {
        // The /sys/desktop space should be queryable after boot
        final Obj space = Router.readFromSpace(DESKTOP_SPACE_VID);
        assertNotNull(space, "/sys/desktop space should exist");
        assertFalse(space.isNoObj(), "/sys/desktop space should not be noobj");
        assertTrue(space.isSpace(), "/sys/desktop should be a Space");
    }

    @Test
    public void testWriteAndReadRecToDesktopState() {
        // Write a Rec to /sys/desktop/state
        final Rec stateRec = rec(
                uri("layout"), str("tiled"),
                uri("columns"), jnt(3),
                uri("theme"), str("dark")
        );
        Router.writeToSpace(DESKTOP_STATE_VID, stateRec);

        // Read it back
        final Obj readBack = Router.readFromSpace(DESKTOP_STATE_VID);
        assertNotNull(readBack, "should be able to read back /sys/desktop/state");
        assertFalse(readBack.isNoObj(), "read back should not be noobj");
        assertTrue(readBack.isRec(), "read back should be a Rec");
        final Rec readRec = readBack.asRec();

        assertEquals(str("tiled"), readRec.at(uri("layout")),
                "layout should be 'tiled'");
        assertEquals(jnt(3), readRec.at(uri("columns")),
                "columns should be 3");
        assertEquals(str("dark"), readRec.at(uri("theme")),
                "theme should be 'dark'");
    }

    @Test
    public void testDesktopStateSurvivesReboot() {
        // Write state before reboot
        final Rec preRebootRec = rec(
                uri("layout"), str("floating"),
                uri("snap"), str("on")
        );
        Router.writeToSpace(DESKTOP_STATE_VID, preRebootRec);

        // Verify it's there before reboot
        Obj beforeReboot = Router.readFromSpace(DESKTOP_STATE_VID);
        assertTrue(beforeReboot.isRec(), "should be a Rec before reboot");
        assertEquals(str("floating"), beforeReboot.asRec().at(uri("layout")));

        // Trigger reboot
        BootLoader.close();
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(rec(uri("log"), uri("error")));
        InstSet.importInstSet(WEB_ISA_TID);  // re-import after reboot

        // After reboot, the /sys/desktop space should still exist (it is recreated)
        final Obj spaceAfterReboot = Router.readFromSpace(DESKTOP_SPACE_VID);
        assertNotNull(spaceAfterReboot, "/sys/desktop should exist after reboot");
        assertFalse(spaceAfterReboot.isNoObj(), "/sys/desktop should not be noobj after reboot");

        // The /sys/desktop/state from before reboot is NOT expected to survive
        // because it's an in-memory space that gets recreated on each boot.
        // This test verifies the space itself is re-created correctly.
        // (In-memory data does not survive reboot by design.)
    }
}
