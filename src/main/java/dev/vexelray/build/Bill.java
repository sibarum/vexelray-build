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
 * stack.count=2
 * stack.1=dev.vexelray.gui:vexelray-gui-core:0.1.1-SNAPSHOT@1e057d6...
 * stack.2=sibarum.atchung:atchung-probe:1.1-SNAPSHOT@943b128...+dirty.0a1b2c3d4e5f
 * </pre>
 *
 * @param self  this build's own stamp
 * @param stack the stamps found on the class path, ordered by group and artifact
 */
public record Bill(Stamp self, List<Stamp> stack) {

    /** Where the bill lives in the build's output, and so in its jar. */
    public static final String RESOURCE = "META-INF/vexelray/build.properties";

    public Bill {
        stack = stack.stream().sorted(Comparator.comparing(Stamp::group).thenComparing(Stamp::artifact)).toList();
    }

    /** As the lines of a properties file. */
    public String text() {
        StringBuilder sb = new StringBuilder(self.text()).append("stack.count=").append(stack.size()).append('\n');
        for (int i = 0; i < stack.size(); i++) {
            sb.append("stack.").append(i + 1).append('=').append(stack.get(i).line()).append('\n');
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
