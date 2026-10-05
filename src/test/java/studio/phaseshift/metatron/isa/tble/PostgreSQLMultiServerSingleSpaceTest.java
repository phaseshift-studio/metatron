package studio.phaseshift.metatron.isa.tble;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.mach.AbstractMultiServerSingleSpaceTest;

import java.util.Map;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.subq;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;

/**
 * POSTGRESQL AS THE SHARED SUBSPACE. The distributed scenarios run here over a table-backed space, which is the point of
 * opting in from the tble suite rather than from the machine contract: nothing about the rewrite changes, only what
 * answers {@link AbstractMultiServerSingleSpaceTest#COMPUTE_ROOT}.
 *
 * <p>THE PATTERN IS THE NAMESPACE, NOT A SCHEME. Table spaces are addressed scheme-first by default (db:kv/test), but
 * the namespace the rewrite mints into is a plain one, so the space is asked to expose /usr/compute/# and the ROUTE
 * declares how addresses under it map to tables. If a table space cannot answer a path pattern, this suite is where
 * that shows up -- as a failure about the space, in the class that claims the space, which is what opting in buys.
 *
 * <p>NO FALLBACK: this suite requires MariaDB. If it cannot start, it fails here saying so, rather than silently
 * running somewhere else and leaving the table-backed path untested.
 */
@Disabled
public class PostgreSQLMultiServerSingleSpaceTest extends AbstractMultiServerSingleSpaceTest {

    private static PostgreSQLDatabaseConfig DB;

    public PostgreSQLMultiServerSingleSpaceTest() {
        super(f("/sys/space/compute"));
    }

    @BeforeAll
    public static void registerPostgreSQL() throws Exception {
        DB = new PostgreSQLDatabaseConfig();
        DB.setup();
        studio.phaseshift.metatron.isa.m.type.InstSet.importInstSet(TBLE_ISA_TID);
    }

    @Override
    protected Supplier<Space> computeSpace() {
        return () -> tbleSpace.of(
                Map.of(
                        uri(PATTERN), uri("/usr/compute/#"),
                        uri(HOST), uri(DB.getJdbcHost()),
                        uri(DRIVER), uri(DB.getDriverClass()),
                        uri(TABLE), lst(),
                        uri(QPROC), lst(subq())),
                nextComputeVID());
    }

    @AfterAll
    public static void stopPostgreSQL() throws Exception {
        if (null != DB) {
            DB.teardown();
            DB = null;
        }
    }
}
