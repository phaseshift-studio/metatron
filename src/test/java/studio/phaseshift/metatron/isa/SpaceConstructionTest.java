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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * A space's construction is all-or-nothing on the memory's index ({@link Space.Helper#construct}).
 * <p>
 * A space mounts itself as the first step of its construction, before its constructor body runs, because the body may
 * reach the space it is building through the machine (a memspace loads its persisted data so). A construction that
 * failed after that point used to leave the half-built space claiming its vid: an mqttspace whose broker was down, an
 * httpspace whose route table could not be processed (its bound socket accepting connections and answering none), a
 * memspace whose data would not load. A failure now retracts every mount the construction made, puts back a
 * predecessor it displaced, and the factory releases what it acquired.
 * <p>
 * {@code {{port}}} is a free port: the http row binds it and must give it back, the mqtt row dials it and finds no
 * broker. {@code {{corrupt}}} is a memspace data file whose first line loads and whose second does not parse.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class SpaceConstructionTest extends AbstractMetatronTest {

    /**
     * the vid every scenario constructs at, unmounted after each one
     */
    private static final String VID = "/sys/space/test/construction";
    private static final Path CORRUPT = Path.of("target", "space-construction-test", "corrupt.mtron").toAbsolutePath();

    @BeforeAll
    public static void setup() throws IOException {
        InstSet.importInstSet(WEB_ISA_TID);
        InstSet.importInstSet(IOT_ISA_TID);
        Files.createDirectories(CORRUPT.getParent());
        Files.writeString(CORRUPT, "/t/construction/stale -> 13\n/t/construction/torn -> [unclosed\n");
    }

    public SpaceConstructionTest() {
        this.bind("corrupt", CORRUPT.toString());
    }

    @AfterEach
    public void unmount() {
        final Obj space = mounted(VID);
        Machine.current().memory().removeSpace(f(VID));
        CommonUtil.close(space);
    }

    /**
     * a construction that fails after its space has mounted leaves nothing mounted, and holds no port
     */
    @ParameterizedTest
    @CsvSource(value = {
            "httpspace::[pattern=><http://127.0.0.1:{{port}}/#>,host=><http://127.0.0.1:{{port}}>,route=>[13=>/usr]]@/sys/space/test/construction % <ERROR>",
            "mqttspace::[pattern=></t/construction/#>,host=><mqtt://127.0.0.1:{{port}}>]@/sys/space/test/construction                       % <ERROR>",
            "memspace::[pattern=></t/construction/#>,data=><{{corrupt}}>]@/sys/space/test/construction                                       % <ERROR>",
    }, delimiter = '%')
    public void testFailedConstructionMountsNothing(final String construction, final String expected) throws IOException {
        final int port = generatePort();
        checkCodeEvaluate(LOG, construction
                .replace("{{port}}", String.valueOf(port))
                .replace("{{corrupt}}", CORRUPT.toString()), "noobj", expected);
        checkEquality(LOG, mounted(VID), noobj(), true);
        // a port the failed construction still held would refuse this bind
        new ServerSocket(port, 50, InetAddress.getLoopbackAddress()).close();
    }

    /**
     * a failed construction puts back the space it displaced: the second memspace at the vid displaces the first, its
     * data fails to load, and the first answers again (with none of what the failed load had written)
     */
    @Test
    public void testFailedConstructionPutsThePredecessorBack() {
        this.script(
                "[STATE] memspace::[pattern=></t/construction/#>]@/sys/space/test/construction",
                "[STATE] /t/construction/kept -> 42",
                "[STATE] memspace::[pattern=></t/construction/#>,data=><{{corrupt}}>]@/sys/space/test/construction",
                "*/t/construction/kept  % 42",
                "*/t/construction/stale % noobj");
    }

    /**
     * a nested construction's mounts are kept only as long as the enclosing construction's: memSpace.of is itself a
     * guarded construction, and it returns before the enclosing one fails
     */
    @Test
    public void testEnclosingFailureRetractsNestedMounts() {
        assertThrows(MTronException.class, () -> Space.Helper.construct(() -> {
            memSpace.of(f("/t/construction/#"), f(VID));
            throw MTronException.of("the enclosing construction failed after the nested one returned");
        }));
        checkEquality(LOG, mounted(VID), noobj(), true);
    }

    /**
     * a nested construction that fails retracts only its own mounts: an enclosing construction that recovers from it
     * keeps what it mounted itself
     */
    @Test
    public void testNestedFailureKeepsTheEnclosingMounts() {
        final memSpace enclosing = Space.Helper.construct(() -> {
            final memSpace space = memSpace.of(f("/t/construction/#"), f(VID));
            try {
                Space.Helper.construct(() -> {
                    memSpace.of(f("/t/construction/nested/#"), f(VID + "_nested"));
                    throw MTronException.of("the nested construction failed");
                });
            } catch (final MTronException recovered) {
                LOG.debug("recovered from %s", recovered.getMessage());
            }
            return space;
        });
        checkEquality(LOG, mounted(VID), enclosing, true);
        checkEquality(LOG, mounted(VID + "_nested"), noobj(), true);
    }
}
