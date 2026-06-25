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

package studio.phaseshift.metatron.isa.llm.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.tble.MariaDBDatabaseConfig;
import studio.phaseshift.metatron.isa.tble.tbleSpace;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * MariaDB-backed implementation of {@link AbstractLLMSessionIntegrationTest}.
 * <p>
 * Uses TestContainers MariaDB 11.x.  The {@code llm_memory} table is
 * auto-created by tbleSpace's {@code createTableFromRecord} on the first
 * write — no manual DDL needed.
 */
public class MariaDBLLMSessionIntegrationTest extends AbstractLLMSessionIntegrationTest {

    private static final String MEM_TABLE = "llm_session";
    private static final fURI SPACE_VID = f("/sys/space/test_llm_mem_int_maria");
    private static final fURI MEM_VID = f("maria:" + MEM_TABLE + "/1");

    private MariaDBDatabaseConfig dbConfig;
    private tbleSpace space;

    @Override
    protected Space createSessionSpace() throws Exception {
        this.dbConfig = new MariaDBDatabaseConfig();
        dbConfig.setup();

        InstSet.importInstSet(TBLE_ISA_TID);

        // createTableFromRecord builds llm_memory from the first write in
        // preCreateMemoryRow() — no manual SQL needed.
        this.space = tbleSpace.of(
                Map.of(
                        uri(PATTERN), uri("maria:#"),
                        uri(HOST), uri(dbConfig.getJdbcHost()),
                        uri(DRIVER), uri(dbConfig.getDriverClass()),
                        uri(ROUTE), rec(uri("maria:"), uri("")),
                        uri(QPROC), lst(incrQ())
                ),
                SPACE_VID
        );
        return this.space;
    }

    @Override
    protected fURI sessionVID() {
        return MEM_VID;
    }

    @Override
    protected void cleanupSession() throws Exception {
        if (this.space != null) {
            try { Router.global().removeSpace(this.space.vid()); } catch (final Exception ignored) {}
            this.space.close();
            this.space = null;
        }
        if (this.dbConfig != null) {
            try { dbConfig.teardown(); } catch (final Exception ignored) {}
            this.dbConfig = null;
        }
    }
}
