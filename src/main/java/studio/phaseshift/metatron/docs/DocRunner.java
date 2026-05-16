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

package studio.phaseshift.metatron.docs;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.iot.iotInstSet;
import studio.phaseshift.metatron.isa.llm.llmInstSet;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.rdf.rdfInstSet;
import studio.phaseshift.metatron.isa.tble.tbleInstSet;
import studio.phaseshift.metatron.isa.web.webInstSet;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static studio.phaseshift.metatron.Tokens.BOOT;
import static studio.phaseshift.metatron.Tokens.LOGG;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * CLI tool that processes AsciiDoc files containing mtron code blocks.
 * Replaces the Python {@code docs-code-parser.py} with in-process mtron evaluation
 * via {@code mParser.parse(code).apply()}, and optionally generates HTML via AsciiDoctorJ.
 *
 * <h3>Usage</h3>
 * <pre>
 * java studio.phaseshift.metatron.docs.DocRunner &lt;input&gt; [-o &lt;output&gt;] [-p &lt;prefix&gt;]
 *     [-b &lt;boot&gt;] [--html &lt;dir&gt;] [--copy_only &lt;true|false&gt;] [--verbose] [-t &lt;timeout&gt;]
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DocRunner {

    private static final String VERSION = "0.1-SNAPSHOT";
    private static final GraphittyLogger LOG = Graphitty.log(DocRunner.class);

    public static void main(final String[] args) throws IOException {
        // ── Parse CLI arguments ──────────────────────────────────────────
        // Defaults so you can right-click → run in IntelliJ with no args.
        // Reads from docs/website/adoc/, writes to target/temp/ (matches Maven pipeline).
        String input = "docs/website/adoc";
        String output = "target/temp";
        String prefix = "docs/python/prefix.adoc";
        String htmlDir = "docs/website";   // --html <dir> → generate tractatus.html there
        String boot = "boot/docs.mtron";   // --boot <file>  → boot file for ISA imports
        boolean verbose = true;
        boolean copyOnly = false;
        int timeout = 8;

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-o", "--output" -> output = args[++i];
                case "-p", "--prefix" -> prefix = args[++i];
                case "--html" -> htmlDir = args[++i];
                case "-b", "--boot" -> boot = args[++i];
                case "-d", "--verbose" -> verbose = true;
                case "-c", "--copy_only" -> copyOnly = "true".equals(args[++i]);
                case "-t", "--timeout" -> timeout = Integer.parseInt(args[++i]);
                case "-v", "--version" -> {
                    LOG.info("Docs Runner v" + VERSION);
                    return;
                }
                default -> input = args[i];
            }
            i++;
        }

        LOG.info("\n[Docs Runner v" + VERSION + "]\n\targs: " + String.join(" ", args));
        // ── Load .env files ──────────────────────────────────────────────
        loadDotEnv(Path.of(System.getProperty("user.home"), ".metatron", "env"));
        loadDotEnv(Path.of(".env").toAbsolutePath());
        // Diagnostic: is OS env injection working?
        System.err.println("[env] OS_ENV size = " + OS_ENV.size());
        System.err.println("[env] ENV_VAR_CTOR = " + (ENV_VAR_CTOR != null ? "available" : "NULL"));
        // Dump loaded env (to stderr so it doesn't mix with doc output)
        System.err.println("[env] keys loaded:");
        System.getProperties().forEach((k, v) -> {
            final String key = k.toString().toUpperCase();
            if (key.contains("API_KEY") || key.contains("TOKEN") || key.contains("SECRET")
                    || key.contains("ENDPOINT") || key.contains("MODEL") || key.contains("ORG_ID"))
                System.err.printf("  %s = %s  (getenv: %s)%n", k, v,
                        System.getenv(k.toString()) != null ? "YES" : "NO");
        });
        System.err.println("[env] done");

        final Path inputPath = Path.of(input).toAbsolutePath().normalize();
        final Path outputPath = Path.of(output).toAbsolutePath().normalize();
        final Path htmlPath = htmlDir != null ? Path.of(htmlDir).toAbsolutePath().normalize() : null;

        // Guard: never overwrite source files in-place.
        if (inputPath.equals(outputPath)) {
            throw new IllegalArgumentException("ERROR: output directory must differ from input directory.\n"
                    + "  input:  " + inputPath + "\n"
                    + "  output: " + outputPath + "\n"
                    + "  Source .adoc files must never be overwritten.");
        }

        LOG.info(inputPath + " => " + outputPath);

        // ── Read prefix file ─────────────────────────────────────────────
        List<String> prefixLines = null;
        if (prefix != null) {
            final Path prefixPath = Path.of(prefix);
            if (Files.exists(prefixPath) && Files.isRegularFile(prefixPath)) {
                prefixLines = Files.readAllLines(prefixPath);
                if (verbose)
                    LOG.info("prefix file: " + prefix + " (" + prefixLines.size() + " lines)");
            }
        }

        // ── Collect .adoc files ──────────────────────────────────────────
        final List<Path> adocFiles = new ArrayList<>();
        try (var stream = Files.list(inputPath)) {
            stream.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".adoc"))
                    .sorted()
                    .forEach(adocFiles::add);
        }

        if (adocFiles.isEmpty()) {
            LOG.info("no .adoc files found in " + inputPath);
            return;
        }

        // ── Ensure output directory exists ───────────────────────────────
        Files.createDirectories(outputPath);
        if (htmlPath != null)
            Files.createDirectories(htmlPath);

        // ── Copy-only mode ───────────────────────────────────────────────
        if (copyOnly) {
            for (final Path file : adocFiles) {
                final Path outFile = outputPath.resolve(file.getFileName());
                if (verbose)
                    LOG.info("copying " + file.getFileName() + " -> " + outFile);
                Files.writeString(outFile, Files.readString(file));
            }
            if (verbose)
                LOG.info("done (copy only)");
            return;
        }

        // ── Bootstrap metatron VM ────────────────────────────────────────
        TypeCheck.disable(TypeCheck.code_resolve);
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(MRec.rec(uri(LOGG), uri("info"), uri(BOOT), uri(boot)));
        for (final InstSet is : new InstSet[] {
                new mathInstSet(), new webInstSet(), new iotInstSet(),
                new grphInstSet(), new llmInstSet(), new tbleInstSet(),
                new dcmntInstSet(), new rdfInstSet()
        }) {
            Router.global().addSpace(is);
            Router.writeToSpace(is);
            is.setup();
        }

        // ── Evaluate prefix expressions for side-effects ───────────
        MtronDocPreprocessor.evalPrefixBlocks(prefixLines);

        // ── Copy adoc files and preprocess ──────────────────────
        final MtronDocPreprocessor preprocessor = new MtronDocPreprocessor();
        for (final Path file : adocFiles) {
            final Path outFile = outputPath.resolve(file.getFileName());
            final String content = Files.readString(file);
            final String processed = preprocessor.process(content);
            Files.writeString(outFile, processed.stripTrailing());
            if (verbose) LOG.info("  processed " + file.getFileName());
        }

        // ── Copy supporting files (header.html, footer.html, images) ────
        final Path includesDir = inputPath.getParent().resolve("includes");
        final List<Path> supportDirs = new ArrayList<>();
        supportDirs.add(inputPath);
        if (Files.isDirectory(includesDir)) supportDirs.add(includesDir);
        for (final Path dir : supportDirs) {
            try (var stream = Files.list(dir)) {
                stream.filter(f -> Files.isRegularFile(f) && !f.getFileName().toString().endsWith(".adoc"))
                        .forEach(f -> {
                            try {
                                Files.copy(f, outputPath.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        // ── Generate tractatus.html (single mega-page) ──────────────────
        if (htmlPath != null) {
            final Path tractatusAdoc = outputPath.resolve("tractatus.adoc");
            if (!Files.exists(tractatusAdoc)) {
                LOG.warn("[docs-runner] tractatus.adoc not found — skipping HTML");
            } else {
                LOG.info("[docs-runner] tractatus.adoc -> " + htmlPath.resolve("tractatus.html"));
                try (final Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
                    final String html = asciidoctor.convert(
                            Files.readString(tractatusAdoc),
                            Options.builder()
                                    .safe(SafeMode.UNSAFE)
                                    .toFile(false)
                                    .baseDir(outputPath.toFile())
                                    .build());
                    Files.writeString(htmlPath.resolve("tractatus.html"), html);
                    LOG.info("[docs-runner] wrote " + htmlPath.resolve("tractatus.html"));
                }
            }
        }

        if (verbose)
            LOG.info("done");
        BootLoader.close();
        System.exit(0);
    }

    /**
     * Constructor for {@code ProcessEnvironment$Variable}, used to safely inject
     * env entries so {@code System.getenv()} iteration doesn't crash.
     */
    private static final Constructor<?> ENV_VAR_CTOR = findEnvVarCtor();
    /**
     * Mutable OS environment map (populated via reflection once).
     */
    @SuppressWarnings("unchecked")
    private static final Map<String, Object> OS_ENV = initEnvMap();

    private static Constructor<?> findEnvVarCtor() {
        try {
            final Class<?> c = Class.forName("java.lang.ProcessEnvironment$Variable");
            // Dump available constructors for diagnosis
            for (final Constructor<?> ctor : c.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                final StringBuilder sig = new StringBuilder("  Variable(");
                for (final Class<?> p : ctor.getParameterTypes())
                    sig.append(p.getSimpleName()).append(", ");
                sig.append(")");
                System.err.println("[env] " + sig);
                // Accept any constructor that takes 2 or more params
                if (ctor.getParameterCount() >= 2) return ctor;
            }
        } catch (final Exception e) {
            System.err.println("[env] warning: cannot access ProcessEnvironment$Variable: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "java:S3011"})
    private static Map<String, Object> initEnvMap() {
        // Try ProcessEnvironment.theEnvironment (JDK 9+)
        try {
            final Class<?> pe = Class.forName("java.lang.ProcessEnvironment");
            final Field f = pe.getDeclaredField("theEnvironment");
            f.setAccessible(true);
            return (Map<String, Object>) f.get(null);
        } catch (final Exception ignored) {
        }
        // Fallback: unwrap UnmodifiableMap from System.getenv()
        try {
            final Map<String, String> env = System.getenv();
            for (final Field field : env.getClass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (Map<String, Object>) field.get(env);
                }
            }
        } catch (final Exception ignored) {
        }
        return Collections.emptyMap();
    }

    /**
     * Inject a KEY=value into the OS environment, wrapping the value properly.
     */
    private static void injectEnv(final String key, final String val) {
        System.setProperty(key, val);
        if (!OS_ENV.isEmpty() && ENV_VAR_CTOR != null) {
            try {
                final Object varObj;
                final int count = ENV_VAR_CTOR.getParameterCount();
                if (count == 2)
                    varObj = ENV_VAR_CTOR.newInstance(key, val);
                else if (count == 3)
                    varObj = ENV_VAR_CTOR.newInstance(key, val, key.hashCode());
                else
                    throw new IllegalArgumentException("unexpected Variable ctor param count: " + count);
                OS_ENV.put(key, varObj);
                // Also update case-insensitive env if available
                try {
                    final Class<?> pe = Class.forName("java.lang.ProcessEnvironment");
                    final Field ci = pe.getDeclaredField("theCaseInsensitiveEnvironment");
                    ci.setAccessible(true);
                    @SuppressWarnings("unchecked") final Map<String, Object> ciEnv = (Map<String, Object>) ci.get(null);
                    ciEnv.put(key, varObj);
                } catch (final Exception ignored) {
                }
            } catch (final Exception e) {
                System.err.println("[env] WARNING: cannot inject " + key + ": " + e.getMessage());
            }
        }
    }

    /**
     * Load KEY=value pairs from a dot-env file into both {@link System#getProperties}
     * and the OS environment (via reflection, so {@code /sys/env/KEY} resolves).
     * Lines starting with {@code #} are comments; blank lines are skipped.
     * Missing files are silently ignored.
     */
    private static void loadDotEnv(final Path path) {
        if (!Files.exists(path)) {
            System.err.println("[env] not found: " + path);
            return;
        }
        System.err.println("[env] loading: " + path);
        try {
            int count = 0;
            for (final String line : Files.readAllLines(path)) {
                final String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                final int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                final String key = trimmed.substring(0, eq).strip();
                final String val = trimmed.substring(eq + 1).strip();
                injectEnv(key, val);
                System.err.printf("[env]   %s = %s%n", key, mask(val));
                count++;
            }
            System.err.println("[env] " + count + " key(s) loaded from " + path.getFileName());
        } catch (final IOException e) {
            System.err.println("[env] error reading " + path + ": " + e.getMessage());
        }
    }

    /**
     * Mask an API key for display: show first 4 and last 4 chars.
     */
    private static String mask(final String s) {
        if (s == null) return "null";
        if (s.length() <= 8) return "***";
        return s.substring(0, 4) + "..." + s.substring(s.length() - 4);
    }
}
