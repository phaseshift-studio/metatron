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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
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

    /**
     * A dedicated collection (derived from {@link #deducedBaseUri()}) so these
     * tests never collide with the shared deduced-flat namespace.
     */
    private fURI deducedCollection(final String name) {
        return this.deducedBaseUri().retract(1).extend(name);
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

    // =========================================================================
    //  DataPath extension navigation — shared contract across spaces
    // =========================================================================

    /**
     * DataPath extension navigation must work when the field value is a bare
     * Lst, not just a Rec: /collection/entry/field/0 must resolve the element
     * (the trailing index must not be ignored), and the deeper
     * rec -&gt; rec -&gt; lst shape must keep working.  The extension-index write
     * path is covered too.
     */
    @Test
    public void testDataPathExtensionOnLstColumn() {
        final fURI collection = this.deducedCollection("dp_lst_ext");
        final fURI entry = collection.extend("0");
        final fURI nested = collection.extend("4");
        try {
            // bare Lst field — index must resolve
            this.space.write(entry, rec(uri("a"), lst(jnt(10), jnt(20), jnt(30))));
            final Obj elem0 = this.space.read(entry.extend("a/0"));
            assertTrue(elem0.isInt(),
                    "extension index on a list-valued field must resolve the element, got: " + elem0);
            assertEquals(jnt(10), elem0);

            // wildcard index / field navigation on the bare list field
            assertEquals(objs(jnt(10), jnt(20), jnt(30)),
                    this.space.read(entry.extend("a/+")),
                    "wildcard index should enumerate every list element");
            assertEquals(jnt(10), this.space.read(entry.extend("+/0")),
                    "wildcard field + index 0 should resolve the first element");
            assertEquals(objs(jnt(10), jnt(20), jnt(30)),
                    this.space.read(entry.extend("+/+")),
                    "wildcard field + wildcard index should enumerate all elements");

            // nested Rec holding a list — must keep resolving
            this.space.write(nested, rec(uri("a"), rec(uri("b"), lst(jnt(11), jnt(22), jnt(33)))));
            final Obj deep0 = this.space.read(nested.extend("a/b/0"));
            assertTrue(deep0.isInt(),
                    "extension index on a nested list must resolve the element, got: " + deep0);
            assertEquals(jnt(11), deep0);

            // extension-index write: update just element 0 of the bare list field
            this.space.write(entry.extend("a/0"), jnt(99));
            assertEquals(jnt(99), this.space.read(entry.extend("a/0")),
                    "extension-index write should update just element 0");
            assertEquals(jnt(20), this.space.read(entry.extend("a/1")),
                    "element 1 should be untouched by the element-0 update");
        } finally {
            this.dropDeducedCollection(collection.name());
        }
    }

    /**
     * The uri {@code >>} walk must descend through every level of a stored
     * entry just like a memSpace rec: collection -&gt; entry -&gt; rec fields -&gt;
     * list indices -&gt; nested rec keys.  Regression: on a tbleSpace row the walk
     * stopped at the entry uri (/row/+) because a field-position wildcard was
     * treated as a literal column name, and wildcard extension reads were
     * navigated instead of being left for unrollPoly to expand into child uris.
     */
    @Test
    public void testUriGraphTreeWalk() {
        final fURI collection = this.deducedCollection("dp_walk");
        final fURI entry = collection.extend("0");
        try {
            this.space.write(entry, rec(
                    uri("title"), str("a title"),
                    uri("message"), lst(jnt(1), rec(uri("a"), str("b")), jnt(3))));

            // collection >> entry
            assertEquals(uri(entry),
                    Uri.Helper.rshiftUri(uri(collection), noobj()));
            // entry >> rec fields
            assertEquals(objs(uri(entry.extend("title")), uri(entry.extend("message"))),
                    Uri.Helper.rshiftUri(uri(entry), noobj()));
            // rec field >> list indices
            assertEquals(objs(uri(entry.extend("message/0")),
                            uri(entry.extend("message/1")),
                            uri(entry.extend("message/2"))),
                    Uri.Helper.rshiftUri(uri(entry.extend("message")), noobj()));
            // list element (a rec) >> nested rec keys
            assertEquals(uri(entry.extend("message/1/a")),
                    Uri.Helper.rshiftUri(uri(entry.extend("message/1")), noobj()));
        } finally {
            this.dropDeducedCollection(collection.name());
        }
    }

    /**
     * The mirror of {@link #testDataPathExtensionOnLstColumn}: a bare Lst whose
     * elements are Recs, accessed through a single uri dereference.
     * /collection/entry/0/a must resolve the rec field (the rec must not be
     * skipped), with wildcard variants for the index and/or the field position.
     */
    @Test
    public void testDataPathExtensionOnRecInLst() {
        final fURI collection = this.deducedCollection("dp_lst_rec");
        final fURI entry = collection.extend("0");
        try {
            // bare Rec elements in a Lst — field must resolve via index
            this.space.write(entry, lst(rec(uri("a"), jnt(10)), rec(uri("a"), jnt(20))));
            assertEquals(jnt(10), this.space.read(entry.extend("0/a")),
                    "lst index 0 -> rec field a should resolve");
            assertEquals(jnt(20), this.space.read(entry.extend("1/a")),
                    "lst index 1 -> rec field a should resolve");

            // wildcard index / field navigation on the list of recs
            assertEquals(objs(jnt(10), jnt(20)), this.space.read(entry.extend("+/a")),
                    "wildcard index should enumerate every rec element's field a");
            assertEquals(jnt(10), this.space.read(entry.extend("0/+")),
                    "lst index 0 + wildcard field should resolve the rec's value");
            assertEquals(objs(jnt(10), jnt(20)), this.space.read(entry.extend("+/+")),
                    "wildcard index + wildcard field should enumerate all rec values");
        } finally {
            this.dropDeducedCollection(collection.name());
        }
    }
}
