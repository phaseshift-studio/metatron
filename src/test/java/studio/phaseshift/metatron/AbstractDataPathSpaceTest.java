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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Abstract contract test for {@link studio.phaseshift.metatron.isa.AbstractDataPathSpace}:
 * the shared deduced flat-key-value routing and the collection-creation migration.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractDataPathSpaceTest extends AbstractDataPathTest {

    public AbstractDataPathSpaceTest(final fURI baseURI, final Supplier<Space> spaceSupplier) {
        super(baseURI, spaceSupplier);
    }

    /**
     * A non-reserved base URI used to exercise the deduced flat namespace
     * (e.g. {@code mongo:scratch} / {@code db:scratch}).
     */
    protected abstract fURI deducedBaseUri();

    /**
     * Whether a flat entry can be migrated into a structured collection
     * losslessly.  Document stores can (schemaless); schema-fixed tables cannot
     * promote a mono value, so they skip {@link #testFlatMigration}.
     */
    protected boolean supportsLosslessFlatMigration() {
        return true;
    }

    /**
     * Drop the deduced test collection so tests stay order-independent.
     */
    protected void dropDeducedCollection(final String collectionName) {
    }

    @Test
    public void testFlatDeduction() {
        final Space space = this.spaceSupplier.get();
        try {
            // mono → deduced flat, round-trips exactly
            space.write(this.deducedBaseUri().extend("a/b/c"), jnt(23));
            assertEquals(jnt(23), space.read(this.deducedBaseUri().extend("a/b/c")).selfVID(null));
            // list → deduced flat, round-trips exactly
            space.write(this.deducedBaseUri().extend("lst"), lst(jnt(1), jnt(2), jnt(3)));
            assertEquals(lst(jnt(1), jnt(2), jnt(3)),
                    space.read(this.deducedBaseUri().extend("lst")).selfVID(null));
        } finally {
            space.close();
        }
    }

    @Test
    public void testFlatMigration() {
        Assumptions.assumeTrue(this.supportsLosslessFlatMigration(),
                "space does not support lossless flat migration");
        final String collection = this.deducedBaseUri().segments().getFirst();
        final Space space = this.spaceSupplier.get();
        try {
            // Park a mono under an unknown collection (deduced flat).
            space.write(this.deducedBaseUri().extend("a/b/c"), jnt(23));
            // A Rec write creates the collection and migrates the parked entry.
            space.write(this.deducedBaseUri().extend("xyz"),
                    rec(uri("id"), str("xyz"), uri("k"), jnt(1)));
            // The mono is now a structured entry at the same address.
            assertEquals(jnt(23), space.read(this.deducedBaseUri().extend("a/b/c")).selfVID(null));
            // The triggering Rec is intact.
            assertEquals(str("xyz"),
                    space.read(this.deducedBaseUri().extend("xyz")).selfVID(null)
                            .asRec().at(uri("id")));
        } finally {
            space.close();
            this.dropDeducedCollection(collection);
        }
    }
}
