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

package studio.phaseshift.metatron.util;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Parser;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The ONE door to the tree-sitter native library, and the ONE place its absence is a fact rather than a crash.
 * <p>
 * The library is musl-linked and only loads on a glibc host once its ELF {@code NEEDED} entry has been re-pointed
 * at glibc, which takes {@code patchelf} — and {@code patchelf} is not on every host. When it is missing,
 * {@code System.load} throws {@link UnsatisfiedLinkError}, which is an {@link Error}. Doing that inside a static
 * initializer is how this project once died on a LAN machine: the {@code Error} escaped {@code <clinit>}, the class
 * was marked ERRONEOUS for the life of the JVM, and every later use of it threw
 * {@code NoClassDefFoundError: Could not initialize class ...} — from {@code MIME}, from {@code fsSpace}, from
 * anywhere. Nothing at the throw site can recover from that, so the fix is to never let it happen:
 * <ul>
 *   <li>this class's initializer does no work at all — every field starts as a literal;</li>
 *   <li>the probe is lazy, runs once, and catches {@link Throwable} (see {@link #probeOnce()});</li>
 *   <li>the FAILURE is cached, not just the success: a failed tree-sitter {@code <clinit>} is erroneous for the
 *       life of the JVM too, so a retry could only manufacture a second, different-looking failure;</li>
 *   <li>every use throws {@link MTronException} — which the engine's error funnel
 *       ({@code catch (Exception) → fail}) turns into {@code fail::T} — instead of poisoning the class.</li>
 * </ul>
 * <p>
 * {@code -Dmetatron.treesitter=off} disables the parser outright: a host that cannot load the library can say so
 * instead of probing for it, and a test can drive the unavailable path deterministically.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class TreeSitterUtil {

    /** the switch that disables tree-sitter entirely: {@code -Dmetatron.treesitter=off}. */
    public static final String ENABLED_PROPERTY = "metatron.treesitter";

    private static final String LIB_NAME = "libjava-tree-sitter.so";

    /** the shipped library's {@code NEEDED} entry — musl-built, unloadable on glibc until patched. */
    private static final String MUSL_NEEDED = "libc.musl-x86_64.so.1";
    private static final String GLIBC_NEEDED = "libc.so.6";

    private static volatile Parser javaParser;
    private static volatile String unavailability;
    private static volatile boolean probed;

    private TreeSitterUtil() {
    }

    /**
     * The shared java parser.
     *
     * @return the parser, once probed successfully
     * @throws MTronException if the native library cannot be loaded on this host — the reason is in the message
     */
    public static Parser parser() throws MTronException {
        if (null == TreeSitterUtil.javaParser)
            TreeSitterUtil.probeOnce();
        if (null == TreeSitterUtil.javaParser)
            throw MTronException.of("the tree-sitter java parser is unavailable: %s", TreeSitterUtil.unavailability);
        return TreeSitterUtil.javaParser;
    }

    public static boolean available() {
        try {
            TreeSitterUtil.parser();
            return true;
        } catch (final MTronException e) {
            return false;
        }
    }

    /**
     * Why {@link #parser()} refuses — the first failure's reason, kept for the life of the JVM. {@code null}
     * until a probe has failed.
     */
    public static String unavailability() {
        return TreeSitterUtil.unavailability;
    }

    /**
     * Test seam: forget the probe so the unavailable path can be exercised on a host where the library does load.
     * Re-probing after a REAL failure is safe ({@link #probeOnce()} catches it all), just pointless.
     */
    static synchronized void resetForTest() {
        TreeSitterUtil.javaParser = null;
        TreeSitterUtil.unavailability = null;
        TreeSitterUtil.probed = false;
    }

    /**
     * Load once, and record what happened — never throw. A {@link Throwable} is caught deliberately: the failure
     * arrives as {@link UnsatisfiedLinkError} (an {@link Error}), and if tree-sitter's own classes already failed
     * to initialize it arrives as {@link NoClassDefFoundError}. Either one escaping this method would take the
     * CALLER's initialization down with it, which is exactly the bug this probe exists to prevent.
     */
    private static synchronized void probeOnce() {
        if (TreeSitterUtil.probed)
            return;
        // CLAIM THE PROBE BEFORE RUNNING IT. once a tree-sitter <clinit> has failed it is erroneous for the life
        // of the JVM; a second probe can only fail differently and report a misleading reason.
        TreeSitterUtil.probed = true;
        if (!TreeSitterUtil.enabled()) {
            TreeSitterUtil.unavailability = "disabled by -D%s=off".formatted(ENABLED_PROPERTY);
            return;
        }
        try {
            TreeSitterUtil.loadNativeLibrary();
            TreeSitterUtil.javaParser = Parser.getFor(Language.JAVA);
        } catch (final Throwable e) {
            TreeSitterUtil.unavailability = TreeSitterUtil.describe(e);
            Graphitty.log(TreeSitterUtil.class).error("the tree-sitter java parser is unavailable: %s", TreeSitterUtil.unavailability);
        }
    }

    private static boolean enabled() {
        return !"off".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY, "on"));
    }

    /**
     * Publish the library into a directory every JVM of this deployment shares, and load it.
     * <p>
     * The directory name comes from the library's own bytes (plus how it was prepared), so a second JVM finds the
     * file already published and copies nothing. That matters more than it looks: the library is ~79 MB, surefire
     * forks one JVM per test class (six by default here), and this used to call {@link Files#createTempDirectory} —
     * so every fork unpacked its OWN copy under {@code java.io.tmpdir} and nothing ever removed the directory.
     * Against a container's 512 MB {@code /tmp} tmpfs, one suite run filled the filesystem, after which no JVM
     * could create its shared-memory file and the entire test suite refused to start.
     */
    private static void loadNativeLibrary() throws java.io.IOException {
        try (final InputStream is = TreeSitterUtil.class.getClassLoader().getResourceAsStream(LIB_NAME)) {
            if (null == is)
                throw MTronException.of("tree-sitter native library not found on classpath: %s", LIB_NAME);
            System.load(TreeSitterUtil.extractShared(is.readAllBytes()).toAbsolutePath().toString());
        }
    }

    /**
     * Unpack and patch a PRIVATE copy, then publish it with one atomic move: a fork that found a half-written or
     * half-patched library would fail in a way that looks like a broken JAR.
     * <p>
     * The published directory records HOW the copy was prepared ({@code -glibc} patched, {@code -raw} untouched).
     * That is not cosmetic: an unpatched copy published by a host without {@code patchelf} must not be reused by a
     * host that has it, or installing {@code patchelf} later silently keeps the old, unloadable library in place.
     */
    private static Path extractShared(final byte[] bytes) throws java.io.IOException {
        final Path staging = Files.createTempFile("metatron-treesitter-", ".part");
        try {
            Files.write(staging, bytes);
            final boolean patched = TreeSitterUtil.patchMuslNeededEntry(staging);
            final Path dir = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"),
                    "metatron-treesitter-%s-%s".formatted(TreeSitterUtil.fingerprint(bytes), patched ? "glibc" : "raw")));
            final Path lib = dir.resolve(LIB_NAME);
            if (Files.exists(lib))
                return lib;   // another JVM of this run published an equivalent copy already
            try {
                Files.move(staging, lib, StandardCopyOption.ATOMIC_MOVE);
            } catch (final java.nio.file.FileAlreadyExistsException race) {
                // another JVM published first — keep theirs
            } catch (final java.nio.file.AtomicMoveNotSupportedException noAtomicMove) {
                Files.move(staging, lib, StandardCopyOption.REPLACE_EXISTING);
            }
            return lib;
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    /**
     * Re-point the library's musl {@code NEEDED} entry at glibc. Best-effort by necessity: {@code patchelf} is not
     * installed everywhere, and on a musl host the library loads unpatched anyway — the load is what decides.
     *
     * @return whether the library was actually patched (patchelf present AND exited cleanly)
     */
    private static boolean patchMuslNeededEntry(final Path lib) {
        try {
            return 0 == new ProcessBuilder("patchelf", "--replace-needed", MUSL_NEEDED, GLIBC_NEEDED, lib.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor();
        } catch (final Exception e) {
            return false;
        }
    }

    /** A stable short digest of the library bytes, so every JVM agrees on one extraction directory. */
    private static String fingerprint(final byte[] bytes) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            final StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++)
                hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            return Integer.toHexString(bytes.length);   // SHA-256 always exists; length is a safe fallback
        }
    }

    /**
     * The failure, in one line, naming the cause a message-only reader would miss. The raw linker text says
     * {@code libc.musl-x86_64.so.1: cannot open shared object file}, which is true but does not explain that the
     * JAR's own library is musl-built.
     */
    private static String describe(final Throwable e) {
        final String detail = null == e.getMessage() ? e.getClass().getName() : e.getMessage();
        final String why = "%s: %s".formatted(e.getClass().getSimpleName(), detail);
        return detail.contains(MUSL_NEEDED)
                ? "%s -- the shipped %s is musl-linked and needs patchelf to load on a glibc host".formatted(why, LIB_NAME)
                : why;
    }
}
