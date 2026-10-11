/*
 * TEMPORARY DIAGNOSTIC — delete after use.
 */

package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

public class MoveProbeTest extends AbstractMetatronTest {

    @Test
    void probe() throws Exception {
        final List<String> out = new ArrayList<>();
        final fURI authority = f("ws://localhost:" + freePort() + "/mtron");
        final Machine handle = Machine.current().push(authority);
        try {
            out.add("authority = " + authority);
            out.add("handle.vid() = " + handle.vid());
            out.add("handle.vid().basePath() = " + handle.vid().basePath());
            out.add("handle.vid().localize() = " + handle.vid().localize());
            out.add("LOCAL read(authority) = " + Machine.root().memory().read(authority));
            out.add("LOCAL findSpace(authority) = " + Machine.root().memory().findSpace(authority));
            out.add("LOCAL getSpaceFor(authority) = " + Machine.root().memory().getSpaceFor(authority));
            out.add("LOCAL read(/mtron) = " + Machine.root().memory().read(f("/mtron")));
            // re-register explicitly and read back
            Machine.root().memory().write(authority, handle);
            out.add("after re-write, LOCAL read(authority) = " + Machine.root().memory().read(authority));
            try {
                final Obj moved = ObjmtronSerializer.parse("move(<" + authority + ">)").apply();
                out.add("move -> " + moved);
            } catch (final Throwable e) {
                final StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                out.add("move threw: " + e);
                out.add(sw.toString());
            }
        } finally {
            Machine.current().pop();
        }
        Files.write(Path.of("target/move-probe.log"), out);
    }

    private static int freePort() {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
