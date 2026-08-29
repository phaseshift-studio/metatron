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

package studio.phaseshift.metatron.docs;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.dckr.dckrInstSet;
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
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;
import studio.phaseshift.metatron.isa.rdf.rdfInstSet;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.tble.tbleInstSet;
import studio.phaseshift.metatron.isa.web.webInstSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * CLI tool that pre-processes a skill directory's markdown files, evaluating
 * {@code ```mtron_pre ... ```} blocks via {@link MtronPreprocessor} and
 * replacing them with {@code ```mtron} input/output listings.
 *
 * <p>For a skill root such as {@code docs/skills/mtron}, processes
 * {@code SKILL.md} and every {@code .md} file under {@code references/}; given
 * the parent {@code docs/skills}, every skill root is processed. The processed
 * markdown is written to the output directory ({@code .metatron/skills} by
 * default), mirroring the input layout — the raw source is never modified.</p>
 *
 * <p>A <em>single</em> {@code .md} file may also be given (the iterate-on-one-doc
 * probe mode): it is processed on its own and written to
 * {@code <out>/<file-basename>}, so pair it with an {@code -o} that resolves to
 * the directory the file belongs to.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * java studio.phaseshift.metatron.docs.MarkdownRunner &lt;input-dir&gt; [-b &lt;boot&gt;] [-o &lt;out&gt;]
 * java studio.phaseshift.metatron.docs.MarkdownRunner &lt;file.md&gt;  [-b &lt;boot&gt;] [-o &lt;out&gt;]
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MarkdownRunner {

    private static final String VERSION = "0.1-SNAPSHOT";
    private static final GraphittyLogger LOG = Graphitty.log(MarkdownRunner.class);

    public static void main(final String[] args) throws IOException {
        // ── Parse CLI arguments ──────────────────────────────────────────
        String input = "docs/skills";
        String boot = "boot/docs.mtron";
        Path outDir = Path.of(".metatron/skills");
        // --single-boot: boot the VM once for all files instead of once per file
        //   (or -Dmtron.singleBoot=true). Toggle to measure how much state bleeds
        //   between files when the VM is reused instead of rebuilt per file.
        // --reverse: process files in reverse sorted order — an order-dependence
        //   probe for state bleed (or -Dmtron.reverse=true).
        boolean singleBoot = Boolean.parseBoolean(System.getProperty("mtron.singleBoot", "false"));
        boolean reverse = Boolean.parseBoolean(System.getProperty("mtron.reverse", "false"));

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-b", "--boot" -> boot = args[++i];
                case "-o", "--out" -> outDir = Path.of(args[++i]);
                case "--single-boot" -> singleBoot = true;
                case "--reverse" -> reverse = true;
                default -> input = args[i];
            }
            i++;
        }
        LOG.info("\n[Markdown Runner v" + VERSION + "]\n\targs: " + String.join(" ", args));

        final Path skillDir = Path.of(input).toAbsolutePath().normalize();
        final Path out = outDir.toAbsolutePath().normalize();
        if (skillDir.equals(out)) {
            throw new IllegalArgumentException("ERROR: output directory must differ from input directory.\n"
                    + "  input:  " + skillDir + "\n"
                    + "  output: " + out);
        }
        final boolean singleFile = Files.isRegularFile(skillDir);
        if (!singleFile && !Files.isDirectory(skillDir)) {
            LOG.error("not a file or directory: " + skillDir);
            return;
        }
        if (singleFile && !skillDir.getFileName().toString().endsWith(".md")) {
            LOG.error("expected a .md file: " + skillDir);
            return;
        }
        if (singleFile && out.resolve(skillDir.getFileName()).equals(skillDir)) {
            throw new IllegalArgumentException("output would overwrite the source file: " + skillDir);
        }
        Files.createDirectories(out);

        // ── Collect: a single probe .md, or a skill dir's SKILL.md + references/*.md
        final List<Path> files = new ArrayList<>();
        if (singleFile) {
            files.add(skillDir);
        } else {
            try (var stream = Files.walk(skillDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .filter(MarkdownRunner::isSkillDoc)
                        .sorted()
                        .forEach(files::add);
            }
        }
        if (files.isEmpty()) {
            LOG.info("no skill markdown files found in " + skillDir);
            return;
        }
        if (reverse) Collections.reverse(files);
        LOG.info("processing " + files.size() + " files (singleBoot=" + singleBoot + ", reverse=" + reverse + ")");
        if (singleBoot) bootVM(boot);
        final long buildStart = System.nanoTime();

        ////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////

        for (final Path file : files) {
            // ── Bootstrap metatron VM ────────────────────────────────────
            // Fresh VM per file by default; with --single-boot the VM was booted once above.
            if (!singleBoot) bootVM(boot);

            final Path rel = rel(skillDir, file);
            LOG.info(Graphitty.sillyPrint("\n\nprocessing " + rel + "...\n\n", true, true));
            final long t0 = System.nanoTime();
            final String content = Files.readString(file);
            final String processed = new MtronPreprocessor(MtronPreprocessor.MARKDOWN_HEADER).process(content);

            if (!processed.equals(content)) {
                final Path target = out.resolve(rel);
                Files.createDirectories(target.getParent());
                Files.writeString(target, processed.stripTrailing());
                LOG.info("  processed " + rel + " (" + elapsedMs(t0) + "ms)");
            } else {
                LOG.info("  unchanged " + rel + " (" + elapsedMs(t0) + "ms)");
            }
            ThreadExecutor.instance().shutdownNow();
        }

        ////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////

        LOG.info("done — " + files.size() + " files, " + elapsedMs(buildStart) + "ms total (singleBoot="
                + singleBoot + ", reverse=" + reverse + ")");
        BootLoader.close();
        System.exit(0);
    }

    /**
     * Elapsed milliseconds since {@code startNanos}.
     */
    private static long elapsedMs(final long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Boot the metatron VM and register the instruction sets used by skill docs.
     * This is the per-file cost that {@code --single-boot} amortizes to once.
     */
    private static void bootVM(final String boot) {
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(MRec.rec(uri(LOGG), uri(INFO), uri(BOOT), uri(boot)));
        for (final InstSet is : new InstSet[]{
                new mathInstSet(), new webInstSet(), new iotInstSet(),
                new grphInstSet(), new llmInstSet(), new tbleInstSet(),
                new dcmntInstSet(), new rdfInstSet(), new dckrInstSet(),
                new uiInstSet()
        }) {
            Router.global().addSpace(is);
            Router.writeToSpace(is);
            is.setup();
        }
        // hardcode type checker in support of runtime inst resolution
        TypeCheck.enable(TypeCheck.values());
        TypeCheck.disable(TypeCheck.code_resolve);
    }

    /**
     * Path of {@code file} relative to {@code root}; in single-file probe mode
     * {@code root} <em>is</em> the file, and {@code Path.relativize} would give
     * the empty path — return the file's own name instead.
     */
    private static Path rel(final Path root, final Path file) {
        return root.equals(file) ? file.getFileName() : root.relativize(file);
    }

    /**
     * A markdown file belongs to a skill doc set when it is a {@code SKILL.md}
     * or lives under a {@code references/} directory at any depth.
     */
    private static boolean isSkillDoc(final Path file) {
        if (file.getFileName().toString().equals("SKILL.md")) return true;
        for (Path parent = file.getParent(); parent != null; parent = parent.getParent()) {
            if ("references".equals(parent.getFileName().toString())) return true;
        }
        return false;
    }
}
