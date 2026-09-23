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

package studio.phaseshift.metatron.isa.tble;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * DIAGNOSTIC (temporary): datetime::T write/read round-trip through
 * tbleSpace tables — pin down where the datetime::T refinement is lost
 * on read-back and why the value comes back as a bare uri::T.
 */
public class DatetimeReadBackDiagTest extends AbstractMetatronTest {

    private static final String DB_PATH = "target/dt-diag.db";

    private static tbleSpace makeSpace(final String vid) {
        new File(DB_PATH).delete();
        return tbleSpace.of(
                rec(
                        uri(PATTERN), uri("db:#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(ROUTE), rec(uri("db:"), uri("")),
                        uri(TABLE), lst()
                ).jvm(),
                f("/sys/space/dt-diag/" + vid)
        );
    }

    private static void dump(final String label) {
        try (final Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             final Statement stmt = conn.createStatement()) {
            STATIC_LOG.info("== {} raw db ==", label);
            try (final ResultSet rs = stmt.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='table'")) {
                while (rs.next())
                    STATIC_LOG.info("  schema: {} {}", rs.getString(1), rs.getString(2));
            }
            try (final ResultSet rs = stmt.executeQuery("SELECT * FROM events")) {
                while (rs.next())
                    STATIC_LOG.info("  events row: {}", rs.toString());
            } catch (final Exception ignored) { }
            try (final ResultSet rs = stmt.executeQuery("SELECT * FROM stamps")) {
                while (rs.next())
                    STATIC_LOG.info("  stamps row: {}", rs.toString());
            } catch (final Exception ignored) { }
            try (final ResultSet rs = stmt.executeQuery("SELECT * FROM _mtron_meta")) {
                while (rs.next())
                    STATIC_LOG.info("  meta: {} {} base={} obj={} ref={}", rs.getString(1), rs.getString(2),
                            rs.getString(3), rs.getString(4), rs.getString(5));
            } catch (final Exception ignored) { }
        } catch (final Exception e) {
            STATIC_LOG.warn("dump failed: {}", e.getMessage());
        }
    }

    @Test
    public void diagDatetimeRoundTrip() throws Exception {
        // ── case A: metatron auto-creates the table (TEXT column) ──
        final tbleSpace spaceA = makeSpace("a");
        final Obj dt = mathInstSet.parseDatetime("2026-08-25T22:34:11.533-06:00");
        LOG.info("writing dt = {} tid={}", dt, dt.tid());
        Router.writeToSpace(f("db:events/1"), rec(uri("label"), str("hello world"), uri("created"), dt));
        dump("caseA after write");

        final Obj fieldBack = Router.readFromSpace(f("db:events/1/created")).selfVID(null);
        LOG.info("caseA field read-back: {} tid={} class={}", fieldBack, fieldBack.tid(), fieldBack.getClass().getSimpleName());
        final Rec rowBack = (Rec) Router.readFromSpace(f("db:events/1")).selfVID(null);
        final Obj rowDt = rowBack.at(uri("created"));
        LOG.info("caseA row  read-back: {} tid={} class={}", rowDt, rowDt.tid(), rowDt.getClass().getSimpleName());
        Router.global().removeSpace(spaceA.vid());
        spaceA.close();

        // ── case B: pre-existing table with a native SQL TIMESTAMPe column ──
        final tbleSpace spaceB = makeSpace("b");
        try (final Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE stamps (id INTEGER PRIMARY KEY, ts TIMESTAMP)");
            stmt.executeUpdate("INSERT INTO stamps (id, ts) VALUES (7, '2026-08-25 22:34:11.533')");
        }
        // force table discovery with a fresh space over the current db
        Router.global().removeSpace(spaceB.vid());
        spaceB.close();
        final tbleSpace spaceB2 = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("db:#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(ROUTE), rec(uri("db:"), uri("")),
                        uri(TABLE), lst()
                ).jvm(),
                f("/sys/space/dt-diag/b2")
        );
        final Obj bBack = Router.readFromSpace(f("db:stamps/7/ts")).selfVID(null);
        LOG.info("caseB pre-seeded read-back: {} tid={} class={}", bBack, bBack.tid(), bBack.getClass().getSimpleName());
        dump("caseB before write");

        Router.writeToSpace(f("db:stamps/8"), rec(uri("ts"), dt));
        dump("caseB after write");
        final Obj bBack2 = Router.readFromSpace(f("db:stamps/8/ts")).selfVID(null);
        LOG.info("caseB write-then-read: {} tid={} class={}", bBack2, bBack2.tid(), bBack2.getClass().getSimpleName());
        Router.global().removeSpace(spaceB2.vid());
        spaceB2.close();
    }
}
