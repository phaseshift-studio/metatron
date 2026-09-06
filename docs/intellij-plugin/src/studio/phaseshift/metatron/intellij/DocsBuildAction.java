// This file is part of metatron — a distributed, data-oriented computing language and VM.
// Copyright (C) 2026 Phaseshift Studio.
//
// This file is part of the docs IntelliJ plugin. It is NOT compiled by the metatron Maven
// build (it lives outside src/); it is compiled against the IntelliJ Platform SDK by
// docs/intellij-plugin/build-plugin.sh.
package studio.phaseshift.metatron.intellij;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Right-click "build and view" for the two bulk website source types.
 *
 * <p>Dispatches on the file extension and shells out to the same runners the {@code docs}
 * build already uses, but scoped to fast single-file turnaround:
 *
 * <ul>
 *   <li>{@code .md} under {@code docs/skills/} → {@code MarkdownRunner <file> -o <outDir>}
 *       (single-file native) → opens the processed markdown in the editor.</li>
 *   <li>{@code .adoc} under {@code docs/website/adoc/} → {@code AsciiDocRunner docs/website/adoc
 *       ... --single-boot} (the adoc tree is one book; the viewable artifact is
 *       {@code docs/website/tractatus.html}) → opens that in the browser.</li>
 * </ul>
 *
 * <p>All relative paths are resolved against the project base dir, exactly like
 * {@code bin/metatron-build-docker docs}. Build output is teed to {@code target/docs-build.log};
 * on failure that log is opened in the editor.
 *
 * <p>Deliberately uses only long-stable, core platform APIs (AnAction, VirtualFile,
 * ApplicationManager, FileEditorManager, BrowserUtil, Notifications) so it compiles against a
 * wide range of IntelliJ versions.
 */
public class DocsBuildAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(DocsBuildAction.class);
    private static final String GROUP = "metatron.docs";

    private static final String ADOC_DIR = "docs/website/adoc";
    private static final String SKILLS_DIR = "docs/skills";
    private static final String MD_RUNNER = "studio.phaseshift.metatron.docs.MarkdownRunner";
    private static final String ADOC_RUNNER = "studio.phaseshift.metatron.docs.AsciiDocRunner";
    private static final String INSTSET_RUNNER = "studio.phaseshift.metatron.docs.InstSetDocGenerator";
    private static final String INSTSET_OUTDIR = "docs/website/instset";

    /// The kind of doc build this action is performing.
    private enum BuildKind { MD, ADOC, INSTSET }

    // Mirrors bin/metatron-build-docker JVM_FLAGS.
    private static final String[] JVM_FLAGS = {
            "--enable-native-access=ALL-UNNAMED",
            "--add-modules", "jdk.incubator.vector",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens", "java.base/java.io=ALL-UNNAMED",
            "--add-opens", "java.base/java.nio=ALL-UNNAMED",
            "--add-opens", "java.base/java.net=ALL-UNNAMED",
            "--add-opens", "java.base/sun.nio.cs=ALL-UNNAMED",
    };

    // ── Menu visibility ────────────────────────────────────────────────
    @Override
    public void update(final AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(isBuildable(event.getData(CommonDataKeys.VIRTUAL_FILE)));
    }

    private static boolean isBuildable(final VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        final String name = file.getName().toLowerCase(Locale.ROOT);
        // Content-root relative file.getPath() is unreliable (IntelliJ returns it relative to
        // the nearest content root, which may not include docs/). Use the on-disk absolute path.
        final String path = diskPath(file).replace('\\', '/');
        if (name.endsWith(".adoc")) {
            return inDir(path, ADOC_DIR);
        }
        if (name.endsWith(".md")) {
            return inDir(path, SKILLS_DIR) || inDir(path, ADOC_DIR);
        }
        // An InstSet class: generate its docs via InstSetDocGenerator (resolved from @JREService(vid=...)).
        if (name.endsWith("instset.java")) {
            return path.contains("/src/main/java/");
        }
        return false;
    }

    /// Absolute on-disk path, independent of content roots.
    private static String diskPath(final VirtualFile file) {
        try {
            final java.nio.file.Path nio = file.toNioPath();
            if (nio != null) {
                return nio.toAbsolutePath().normalize().toString();
            }
        } catch (final Exception ignore) {
            // not a file-system file; fall back to the VFS path
        }
        return file.getPath();
    }

    /// Project-relative path (e.g. docs/skills/foo.md) from the on-disk path — content-root independent.
    private static String projectRelative(final String projectBase, final VirtualFile file) {
        if (projectBase != null) {
            final String base = new java.io.File(projectBase).getAbsolutePath().replace('\\', '/') + "/";
            final String abs = diskPath(file).replace('\\', '/');
            if (abs.startsWith(base)) {
                return abs.substring(base.length());
            }
        }
        return file.getPath();
    }

    /// Boundary-safe "is path under the project-relative dir rel" (docs/skills, docs/website/adoc).
    private static boolean inDir(final String path, final String rel) {
        return path.contains("/" + rel + "/");
    }

    // ── The action ─────────────────────────────────────────────────────
    @Override
    public void actionPerformed(final AnActionEvent event) {
        final Project project = event.getProject();
        final VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || file == null) {
            notify("docs build", "No file or project in context.", NotificationType.WARNING);
            return;
        }
        final File root = new File(project.getBasePath() == null ? "." : project.getBasePath());
        // file.getPath() is content-root relative (unreliable); derive the project-relative path
        // from the on-disk path so the runner (cwd = project root) gets docs/skills/foo.md.
        final String srcRel = projectRelative(project.getBasePath(), file);
        // What to build: a .md skill doc, a single .adoc page, or an InstSet's generated docs.
        final String name = file.getName().toLowerCase(Locale.ROOT);
        final BuildKind kind;
        final String target;
        if (name.endsWith(".adoc")) {
            kind = BuildKind.ADOC;
            target = srcRel;
        } else if (name.endsWith("instset.java")) {
            final String vid = instsetVid(file);
            if (vid == null) {
                notify("InstSet docs",
                        "could not read @JREService(vid = \"...\") from " + file.getName(),
                        NotificationType.WARNING);
                return;
            }
            kind = BuildKind.INSTSET;
            target = vid;
        } else {
            kind = BuildKind.MD;
            target = srcRel;
        }

        final File jar = findJar(root);
        if (jar == null) {
            notify("docs build skipped",
                    "metatron uber-jar not found (looked under " + root + "/target).\n\n"
                            + "build it first:  ./mvnw install -DskipTests",
                    NotificationType.WARNING);
            return;
        }

        // Open a live progress tab: the build tees its output to target/docs-build.log as it streams.
        openProgressTab(project, root);

        notify("docs build: " + file.getName(), "booting metatron vm (takes a few seconds)…",
                NotificationType.INFORMATION);

        final List<String> command = buildCommand(root, kind, target, jar);
        LOG.info("docsBuild: " + String.join(" ", command));
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                run(project, kind, root, target, file, command));
    }

    // ── Command construction (all relative paths, cwd = project root) ──
    private static List<String> buildCommand(final File root, final BuildKind kind, final String target, final File jar) {
        final List<String> command = new ArrayList<>();
        command.add(findJava(root));
        command.addAll(Arrays.asList(JVM_FLAGS));
        command.add("-cp");
        command.add(jar.getAbsolutePath());
        switch (kind) {
            case MD -> {
                // Single-file markdown (MarkdownRunner).
                command.add(MD_RUNNER);
                command.add(target);
                command.add("-o");
                command.add(markdownOutDir(target));
                command.add("-b");
                command.add("boot/docs.mtron");
            }
            case ADOC -> {
                // Single-file adoc: build just this adoc (+ any .adoc it includes) -> <docs/website>/<name>.html.
                command.add(ADOC_RUNNER);
                command.add(target);
                command.add("-o");
                command.add("target/temp");
                command.add("--html");
                command.add("docs/website");
                command.add("-b");
                command.add("boot/docs.mtron");
                command.add("-p");
                command.add("docs/python/prefix.adoc");
                command.add("--single-boot");
            }
            case INSTSET -> {
                // InstSetDocGenerator <vid>: boots the VM, reads that InstSet's tables, renders a web page.
                command.add(INSTSET_RUNNER);
                command.add(target);
                command.add("-o");
                command.add(INSTSET_OUTDIR);
                command.add("--website-template");
                command.add("-b");
                command.add("boot/docs.mtron");
            }
        }
        return command;
    }

    /// outDir for a single skills .md — mirrors bin/metatron-build-docker .metatron/skills/<sub>.
    private static String markdownOutDir(final String srcRel) {
        final int slash = srcRel.lastIndexOf('/');
        final String prefix = SKILLS_DIR + "/";
        if (srcRel.startsWith(prefix) && srcRel.length() > prefix.length()) {
            final String sub = srcRel.substring(prefix.length(), slash < 0 ? srcRel.length() : slash);
            return sub.isEmpty() ? ".metatron/skills" : ".metatron/skills/" + sub;
        }
        return ".metatron/skills/_probe"; // probe mode for non-skills .md
    }

    // ── Run + report ───────────────────────────────────────────────────
    private static void run(final Project project, final BuildKind kind, final File root,
                            final String srcRel, final VirtualFile source, final List<String> command) {
        final File logFile = new File(root, "target/docs-build.log");
        try {
            if (logFile.getParentFile() != null) {
                logFile.getParentFile().mkdirs();
            }
            final ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(new File(root.getAbsolutePath()))
                    .redirectErrorStream(true);
            final Process process = builder.start();
            final List<String> tail = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 final Writer writer = new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.write(System.lineSeparator());
                    tail.add(line);
                    if (tail.size() > 40) {
                        tail.remove(0);
                    }
                }
                writer.flush();
            }
            final int exit = process.waitFor();
            if (exit == 0) {
                onEdt(() -> {
                    openView(project, kind, root, srcRel);
                    notify("docs build: " + source.getName(), "built → " + viewPath(kind, root, srcRel),
                            NotificationType.INFORMATION);
                });
            } else {
                onEdt(() -> {
                    openLog(project, logFile); // open the full log in the editor
                    notify("docs build FAILED: " + source.getName(),
                            "exit " + exit + "\n\n" + String.join("\n", tail),
                            NotificationType.ERROR);
                });
            }
        } catch (final InterruptedException interrupt) {
            Thread.currentThread().interrupt();
            onEdt(() -> notify("docs build interrupted", "cancelled", NotificationType.ERROR));
        } catch (final IOException io) {
            final String msg = io.getMessage() == null ? "io error" : io.getMessage();
            onEdt(() -> notify("docs build error", msg, NotificationType.ERROR));
        }
        LOG.info("docsBuild done → " + logFile);
    }

    private static String viewPath(final BuildKind kind, final File root, final String target) {
        switch (kind) {
            case MD:
                return new File(root, markdownOutDir(target) + "/" + baseName(target)).getPath();
            case ADOC:
                return new File(root, "docs/website/" + htmlBaseName(target)).getPath();
            case INSTSET:
                return new File(root, INSTSET_OUTDIR + "/" + vidToFile(target)).getPath();
        }
        return "";
    }

    private static void openView(final Project project, final BuildKind kind, final File root, final String srcRel) {
        final File output = new File(viewPath(kind, root, srcRel));
        if (!output.exists()) {
            notify("Docs build", "output not found: " + output, NotificationType.WARNING);
            return;
        }
        try {
            // Pull the built output into a focused editor tab (all three modes).
            refreshAndOpen(project, output);
            // For rendered HTML (adoc / InstSet), also open it in the browser.
            if (kind == BuildKind.ADOC || kind == BuildKind.INSTSET) {
                final URL u = url(output);
                if (u != null) {
                    BrowserUtil.browse(u);
                }
            }
        } catch (final Exception ex) {
            notify("Docs build", "built, but could not open " + output + " (" + ex.getMessage() + ")",
                    NotificationType.WARNING);
        }
    }

    /// Open the build log in the editor (best-effort).
    private static void openLog(final Project project, final File logFile) {
        if (project == null || !logFile.exists()) {
            return;
        }
        try {
            refreshAndOpen(project, logFile);
        } catch (final Exception ex) {
            LOG.warn("could not open log: " + ex.getMessage());
        }
    }

    /// Make the freshly written file visible to the VFS, then open it in the editor.
    private static void refreshAndOpen(final Project project, final File target) {
        final VirtualFile virtual =
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.getAbsoluteFile());
        if (virtual == null) {
            throw new IllegalStateException("VFS did not pick up " + target);
        }
        FileEditorManager.getInstance(project).openFile(virtual, true);
    }

    private static URL url(final File file) {
        try {
            return file.toURI().toURL();
        } catch (final Exception ex) {
            return null;
        }
    }

    private static String baseName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /// For an adoc file, the name of the rendered html output (index.adoc -> index.html).
    private static String htmlBaseName(final String srcRel) {
        final String base = baseName(srcRel);
        return base.endsWith(".adoc") ? base.substring(0, base.length() - 5) + ".html" : base + ".html";
    }

    /// Read an InstSet's furi from the source's @JREService(vid = "...") annotation
    /// (the authoritative class->furi mapping; not guessed from the file name).
    private static String instsetVid(final VirtualFile file) {
        final String text;
        try {
            text = new String(java.nio.file.Files.readAllBytes(file.toNioPath()), StandardCharsets.UTF_8);
        } catch (final Exception ex) {
            return null;
        }
        final java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("@JREService\\s*\\((?:[^()])*?vid\\s*=\\s*\"([^\"]+)\"")
                .matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        final java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("vid\\s*=\\s*\"([^\"]+)\"").matcher(text);
        return m2.find() ? m2.group(1) : null;
    }

    /// InstSetDocGenerator output filename for a VID:  /m/math -> m_math.html ;  /m -> m.html.
    private static String vidToFile(final String vid) {
        final String s = vid.replace('/', '_');
        return (s.startsWith("_") ? s.substring(1) : s) + ".html";
    }

    private static File findJar(final File root) {
        final File target = new File(root, "target");
        if (target.isDirectory()) {
            final File[] matches = target.listFiles((dir, name) ->
                    name.startsWith("metatron-") && name.endsWith("-jar-with-dependencies.jar"));
            if (matches != null && matches.length > 0) {
                return matches[0];
            }
        }
        return null;
    }

    /// A Java 21+ runtime to run the jar: prefer the project's own JDK (.build/jdk, Temurin 24)
    /// because `java` on PATH can resolve to an older runtime inside the IDE's environment.
    private static String findJava(final File root) {
        final File projectJdk = new File(root, ".build/jdk/bin/java");
        if (projectJdk.isFile() && projectJdk.canExecute()) {
            return projectJdk.getAbsolutePath();
        }
        return "java";
    }

    /// Run UI work (open an editor, post a notification) on the EDT.
    private static void onEdt(final Runnable r) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(r);
    }

    /// Open (and truncate) the build log as a live editor tab so progress is visible as it streams.
    /// IntelliJ auto-reloads a local file's external changes, so the tab fills in in near-real-time.
    private static void openProgressTab(final Project project, final File root) {
        try {
            final File logFile = new File(root, "target/docs-build.log");
            if (logFile.getParentFile() != null) {
                logFile.getParentFile().mkdirs();
            }
            try (final FileOutputStream fos = new FileOutputStream(logFile)) {
                // truncate the previous run's log; the process then appends the new build's output
            }
            refreshAndOpen(project, logFile);
        } catch (final Exception ex) {
            LOG.warn("could not open progress tab: " + ex.getMessage());
        }
    }

    /// Minimal, version-stable notification (no attached actions).
    private static void notify(final String title, final String text, final NotificationType type) {
        Notifications.Bus.notify(new Notification(GROUP, title, text, type));
    }
}
