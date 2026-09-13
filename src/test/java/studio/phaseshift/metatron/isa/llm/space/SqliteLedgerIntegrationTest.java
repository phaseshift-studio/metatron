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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
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
 * The ledger fsck against a real sqlite-backed store — the backend the application
 * uses — with no model dependency.
 *
 * <p>{@link AbstractLLMSessionIntegrationTest} covers the same thing across every
 * space backend, but it is gated on a reachable model, so without one it skips and
 * the store's read path goes untested.  This one always runs, which is the point:
 * the difference between a {@code memSpace} and a tble store is what hid two
 * defects in {@link LedgerUtil} until it was run against real data.
 */
public class SqliteLedgerIntegrationTest extends AbstractMetatronTest {

    private static final String DB_PATH = "target/test-llm-ledger-int.db";

    /** A session vid in the store's own layout — its retraction is the ledger root. */
    private static final fURI SESSION_VID = f("sqlite:llm_ledger/1");

    private static final fURI SPACE_VID = f("/sys/space/test_llm_ledger_int");

    private tbleSpace space;

    @BeforeAll
    public static void setup() {
        InstSet.importInstSet(f("/m/llm"));
    }

    @BeforeEach
    void openStore() {
        new File(DB_PATH).delete();
        InstSet.importInstSet(TBLE_ISA_TID);
        // the message table is auto-created from the first write
        this.space = tbleSpace.of(
                Map.of(
                        uri(PATTERN), uri("sqlite:#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(ROUTE), rec(uri("sqlite:"), uri("")),
                        uri(QPROC), lst(incrQ())),
                SPACE_VID);
    }

    @AfterEach
    void closeStore() {
        if (null != this.space) {
            try {
                Router.global().removeSpace(this.space.vid());
            } catch (final Exception ignored) {
                // the space may already be gone
            }
            this.space.close();
            this.space = null;
        }
        new File(DB_PATH).delete();
    }

    @Test
    public void testSweepAgainstSqliteLedger() {
        LedgerSweepAssertions.verify(SESSION_VID);
    }
}
