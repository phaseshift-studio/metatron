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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * {@link BootLoader#resolveBootFile(fURI)}: the boot address maps to a local file.  A bare path (no {@code ://}) is
 * taken as a file, a {@code file://} uri is stripped to its path, and an {@code http(s)://} uri is fetched once and
 * cached to a temp file.
 */
public class BootLoaderResolveTest extends AbstractMetatronTest {

    private static HttpServer far;

    @BeforeAll
    public static void startServer() throws IOException {
        far = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        far.createContext("/deploy.mtron", exchange -> {
            final byte[] bytes = "log=>info\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        far.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        far.start();
    }

    @AfterAll
    public static void stopServer() {
        if (null != far)
            far.stop(0);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // bare relative paths — no scheme, assumed file, unchanged
            "boot/docker.boot.mtron     % boot/docker.boot.mtron",
            "x.mtron                    % x.mtron",
            "boot/nested/deep/x.mtron   % boot/nested/deep/x.mtron",
            // bare absolute paths — no scheme, assumed file, unchanged
            "/app/boot/x.mtron          % /app/boot/x.mtron",
            "/x.mtron                   % /x.mtron",
            // file:// — scheme stripped, path normalized to a clean absolute path
            "file:///app/boot/x.mtron   % /app/boot/x.mtron",
            "file:/app/boot/x.mtron     % /app/boot/x.mtron",
            "file:///tmp/x.mtron        % /tmp/x.mtron",
            "file://localhost/app/x.mtron % /app/x.mtron",          // authority is stripped too
            "FILE:///app/boot/x.mtron  % /app/boot/x.mtron",        // scheme is case-insensitive
            // relative dot-segments — no scheme, assumed file, unchanged
            "./boot/x.mtron             % ./boot/x.mtron",
            "../boot/x.mtron            % ../boot/x.mtron",
            // dots, hyphens and underscores in a filename — unchanged
            "boot/my-app_v2.0.mtron     % boot/my-app_v2.0.mtron",
    }, delimiter = '%')
    void testResolveLocal(final String input, final String expected) throws IOException {
        assertEquals(f(expected), BootLoader.resolveBootFile(f(input)));
    }

    @Test
    void testResolveHttpFetchesAndCaches() throws IOException {
        final String url = "http://127.0.0.1:" + far.getAddress().getPort() + "/deploy.mtron";
        final fURI resolved = BootLoader.resolveBootFile(f(url));
        assertEquals("log=>info\n", Files.readString(Path.of(resolved.toString())));
    }

    @Test
    void testResolveHttpNon2xxThrows() {
        final String url = "http://127.0.0.1:" + far.getAddress().getPort() + "/missing";
        assertThrows(IOException.class, () -> BootLoader.resolveBootFile(f(url)));
    }
}
