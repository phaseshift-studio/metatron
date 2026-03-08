/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.tble;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;

import java.io.File;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for tbleSpace with MQTT-indexed schema.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleSpaceTest extends AbstractSpaceTest {

    private static final String DB_PATH = "target/test-tble-space.db";
    private static final fURI SPACE_VID = f("/sys/space/tble/test");

    public tbleSpaceTest() {
        super(f("/tble"), () -> tbleSpace.of(
                rec(
                        uri(PATTERN), uri("/t/#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(ROUTE), rec(uri(""), uri(""))
                ).jvm(),
                SPACE_VID
        ));
    }

    @BeforeAll
    public static void setupDatabase() throws Exception {
        // Load SQLite JDBC driver
        Class.forName("org.sqlite.JDBC");

        // Delete existing test database
        final File dbFile = new File(DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @AfterAll
    public static void cleanupDatabase() {
        final File dbFile = new File(DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }
}
