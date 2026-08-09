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

import studio.phaseshift.metatron.SkipWhenPortUnavailable;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.tble.tbleSpace;

import java.io.File;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * SQLite-backed implementation of {@link AbstractLLMSessionIntegrationTest}.
 * <p>
 * The {@code llm_session} table is auto-created by tbleSpace's
 * {@code createTableFromRecord} on the first write — no manual DDL needed.
 * Messages are stored via KV at {@code sqlite:msg/{memId}/{pos}}.
 */
@SkipWhenPortUnavailable(value = 11434)
public class SqliteLLMSessionIntegrationTest extends AbstractLLMSessionIntegrationTest {

    private static final String DB_PATH = "target/test-llm-session-int.db";
    private static final String MEM_TABLE = "llm_session";
    private static final fURI SPACE_VID = f("/sys/space/test_llm_mem_int");
    private static final fURI MEM_VID = f("sqlite:" + MEM_TABLE + "/1");

    private tbleSpace space;

    @Override
    protected Space createSessionSpace() throws Exception {
        final File dbFile = new File(DB_PATH);
        if (dbFile.exists()) dbFile.delete();
        dbFile.getParentFile().mkdirs();

        InstSet.importInstSet(TBLE_ISA_TID);

        // tbleSpace opens the DB.  The llm_session table does not exist yet —
        // createTableFromRecord will build it from the first write in
        // preCreateMemoryRow().
        this.space = tbleSpace.of(
                Map.of(
                        uri(PATTERN), uri("sqlite:#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(ROUTE), rec(uri("sqlite:"), uri("")),
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
            try {
                Router.global().removeSpace(this.space.vid());
            } catch (final Exception ignored) {
            }
            this.space.close();
            this.space = null;
        }
        final File dbFile = new File(DB_PATH);
        if (dbFile.exists()) dbFile.delete();
    }
}
