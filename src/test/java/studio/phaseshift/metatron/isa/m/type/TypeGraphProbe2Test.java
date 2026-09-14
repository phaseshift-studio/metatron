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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;

/**
 * TEMPORARY diagnostic -- dump the testNominally(URI_TYPE) walk node by node.
 */
public class TypeGraphProbe2Test extends AbstractMetatronTest {

    @Test
    public void probe() {
        final Obj parse = ObjmtronSerializer.parse("datetime::<//2024.12:25/09/00/00/000?tz=-0500>").apply();
        System.out.println("[P2] parse: tid=" + parse.tid() + " vid=" + parse.vid() + " type.tid=" + parse.tid());
        final Type lhs = Obj.Helper.specificType(parse);
        final Type rhs = Obj.Helper.specificType(URI_TYPE);
        System.out.println("[P2] lhs : tid=" + lhs.tid() + " vid=" + lhs.vid());
        System.out.println("[P2] rhs : tid=" + rhs.tid() + " vid=" + rhs.vid());
        Type node = lhs;
        for (int i = 0; i < 6 && null != node; i++) {
            final boolean match = null != node.vid() && node.vid().test(rhs.vid());
            System.out.println("[P2] hop" + i + ": tid=" + node.tid() + " vid=" + node.vid() + " baseType=" + node.isBaseType() + " match=" + match);
            if (match || node.isBaseType())
                break;
            node = node.parentType();
        }
        // the uri type itself -- memo vs fresh
        final Obj uriInSpace = Router.loaded() ? Router.readFromSpace(URI_TYPE.vid()) : null;
        System.out.println("[P2] URI in space: " + uriInSpace);
        assertTrue(true);
    }
}
