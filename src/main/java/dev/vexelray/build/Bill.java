package dev.vexelray.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Everything a build was made from: its own {@link Stamp} and the stamp of every part on its class path that has
 * one. A part with no stamp (a third-party library, or a sibling module not yet built with this plugin) is not
 * listed; its version is still in the build's dependency tree, and its source is not this plugin's to know.
 *
 * <p>Written as {@code META-INF/vexelray/build.properties}, which the framework's code generator reads while the
 * application compiles and bakes into the generated wiring:
 *
 * <pre>
 * group=dev.sibarum
 * artifact=vexelray-inspector
 * version=0.1.0-SNAPSHOT
 * commit=ef816d9...
 * dirty=false
 * digest=
 * source=ef816d9...
 * app.id=dev.sibarum.vexelray-inspector
 * name=VexelRay Inspector
 * built=2026-10-24T14:03:12Z
 * notices=https://github.com/sibarum/vexelray-dist/releases/download/inspector-v0.1.0-SNAPSHOT/THIRD-PARTY-NOTICES.txt
 * installer.id=inspector
 * installer.replaces=
 * stack.count=2
 * stack.1=dev.vexelray.gui:vexelray-gui-core:0.1.1-SNAPSHOT@1e057d6...
 * stack.1.issues=https://github.com/sibarum/vexelray-gui/issues
 * stack.2=sibarum.atchung:atchung-probe:1.1-SNAPSHOT@943b128...+dirty.0a1b2c3d4e5f
 * </pre>
 *
 * @param self      this build's own stamp
 * @param appId     the name the application's windows go by on the desktop ({@link AppId})
 * @param name      the application's proper name, as a person reads it: the {@code name} in its
 *                  {@code installer.json}, else its pom's {@code <name>}, else its artifactId
 * @param built     when this identity was first built, as an ISO-8601 instant. Not part of the identity, which is
 *                  why a rebuild of the same source keeps the time it had ({@link #sameBuildAs})
 * @param notices   where the third-party notices of this build's release are published, or empty when it has none
 * @param installer its {@code installer.json}, or {@code null} for none: the framework's code generator checks that
 *                  its {@code id} is the application's name, and its {@code replaces} the names it had before
 * @param stack     the stamps found on the class path, ordered by group and artifact
 */
public record Bill(Stamp self, String appId, String name, String built, String notices, InstallerJson installer,
                   List<Stamp> stack) {

    /** Where the bill lives in the build's output, and so in its jar. */
    public static final String RESOURCE = "META-INF/vexelray/build.properties";

    public Bill {
        notices = notices == null ? "" : notices;
        stack = stack.stream().sorted(Comparator.comparing(Stamp::group).thenComparing(Stamp::artifact)).toList();
    }

    /**
     * This bill, built when {@code earlier} was if it is the same build: the same text but for the time. A build of
     * unchanged source is the same build (the README's last note), and saying so keeps the bill on disk unchanged,
     * which is what lets the compiler skip a module whose identity did not move. A time that changed every build
     * would recompile every application every time, and the wiring would still only carry the last one it compiled.
     */
    public Bill sameBuildAs(String earlier) {
        if (earlier == null) {
            return this;
        }
        String was = null;
        for (String line : earlier.split("\n")) {
            if (line.startsWith("built=")) {
                was = line.substring("built=".length()).trim();
            }
        }
        if (was == null || was.isEmpty()) {
            return this;
        }
        Bill then = new Bill(self, appId, name, was, notices, installer, stack);
        return then.text().equals(earlier) ? then : this;
    }

    /** As the lines of a properties file. */
    public String text() {
        StringBuilder sb = new StringBuilder(self.text()).append("app.id=").append(appId).append('\n')
                .append("name=").append(name).append('\n')
                .append("built=").append(built).append('\n');
        if (!notices.isEmpty()) {
            sb.append("notices=").append(notices).append('\n');
        }
        if (installer != null) {
            sb.append("installer.id=").append(installer.id() == null ? "" : installer.id()).append('\n')
                    .append("installer.replaces=").append(String.join(",", installer.replaces())).append('\n');
        }
        sb.append("stack.count=").append(stack.size()).append('\n');
        for (int i = 0; i < stack.size(); i++) {
            sb.append("stack.").append(i + 1).append('=').append(stack.get(i).line()).append('\n');
            if (!stack.get(i).issues().isEmpty()) {
                sb.append("stack.").append(i + 1).append(".issues=").append(stack.get(i).issues()).append('\n');
            }
        }
        return sb.toString();
    }

    /** The stamps found in {@code part}: a jar, or a directory of classes such as a reactor sibling's output. */
    public static List<Stamp> stampsIn(Path part) throws IOException {
        List<Stamp> found = new ArrayList<>();
        if (part == null || !Files.exists(part)) {
            return found;
        }
        if (Files.isDirectory(part)) {
            Path dir = part.resolve(Stamp.PREFIX);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.walk(dir)) {
                    for (Path f : files.filter(p -> p.toString().endsWith(".properties")).toList()) {
                        try (InputStream in = Files.newInputStream(f)) {
                            add(found, Stamp.read(in));
                        }
                    }
                }
            }
            return found;
        }
        if (!part.toString().endsWith(".jar")) {
            return found;
        }
        try (ZipFile zip = new ZipFile(part.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && e.getName().startsWith(Stamp.PREFIX) && e.getName().endsWith(".properties")) {
                    try (InputStream in = zip.getInputStream(e)) {
                        add(found, Stamp.read(in));
                    }
                }
            }
        }
        return found;
    }

    private static void add(List<Stamp> into, Stamp s) {
        if (s != null) {
            into.add(s);
        }
    }
}
