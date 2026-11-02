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

package studio.phaseshift.metatron.isa.m.web.space.http;

import org.zeroturnaround.exec.ProcessExecutor;
import studio.phaseshift.metatron.util.MTronException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

/**
 * The certificates the https tests serve and trust: a self-signed key pair for {@code localhost} and
 * {@code 127.0.0.1}, generated once per JVM by the JDK's own {@code keytool}, so no key material is checked in. Each
 * JVM writes its own directory under {@code target/test-tls/}, since surefire runs several forks at once.
 */
final class TlsTestKeys {

    static final String PASS = "metatron-over-tls";
    private static final String ALIAS = "metatron";

    private TlsTestKeys() {
    }

    /**
     * generated on first use, once per JVM
     */
    private static final class Generated {
        private static final Path KEYSTORE = TlsTestKeys.generate();
    }

    /**
     * a PKCS12 key store holding the private key and its self-signed certificate, opened with {@link #PASS}
     */
    static Path keystore() {
        return Generated.KEYSTORE;
    }

    private static Path generate() {
        try {
            final Path dir = Files.createTempDirectory(Files.createDirectories(Path.of("target", "test-tls")), "keys");
            final Path keystore = dir.resolve("server.p12");
            new ProcessExecutor()
                    .command(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                            "-genkeypair", "-alias", ALIAS, "-keyalg", "EC",
                            "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2",
                            "-storetype", "PKCS12", "-keystore", keystore.toString(),
                            "-storepass", PASS, "-keypass", PASS, "-noprompt")
                    .readOutput(true)
                    .exitValueNormal()
                    .timeout(60, TimeUnit.SECONDS)
                    .execute();
            return keystore;
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to generate the test keystore: %s", e.getMessage());
        }
    }

    /**
     * a PKCS12 store with the certificate and no private key: a trust store, which has nothing to serve with
     */
    static Path certificateOnly() {
        try {
            final Path path = keystore().resolveSibling("certificate-only.p12");
            try (final OutputStream out = Files.newOutputStream(path)) {
                TlsTestKeys.certificateStore().store(out, PASS.toCharArray());
            }
            return path;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * a client context that trusts the test certificate, and nothing else
     */
    static SSLContext trusting() {
        try {
            final TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(TlsTestKeys.certificateStore());
            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    private static KeyStore certificateStore() throws Exception {
        final KeyStore server = KeyStore.getInstance("PKCS12");
        try (final InputStream in = Files.newInputStream(keystore())) {
            server.load(in, PASS.toCharArray());
        }
        final KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry(ALIAS, server.getCertificate(ALIAS));
        return store;
    }
}
