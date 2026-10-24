package dev.vexelray.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * What one module's binaries were built from: its coordinates, and the exact source.
 *
 * <p>The source is the git commit, and when the working tree had uncommitted changes, a digest of exactly those
 * changes: {@code a1b2c3d4e5f6...+dirty.7f3e9a01b2c4}. Two builds with the same {@link #source()} were built from the
 * same files; two with different ones were not. A version alone cannot say that, since a {@code -SNAPSHOT} covers
 * every commit between two releases.
 *
 * @param group    the Maven groupId
 * @param artifact the Maven artifactId
 * @param version  the Maven version
 * @param commit   the full commit hash of {@code HEAD}; {@code unknown} outside a git checkout or without git
 * @param digest   the digest of the uncommitted changes, or empty when the tree was clean
 * @param issues   where the module's problems are reported: its pom's {@code <issueManagement><url>}, inherited
 *                 like the rest of the model, or empty when it names none. Not part of the identity: it says who
 *                 looks after the module, not what it was built from
 */
public record Stamp(String group, String artifact, String version, String commit, String digest, String issues) {

    public Stamp {
        issues = issues == null ? "" : issues.trim();
    }

    /** A stamp that names no issue tracker. */
    public Stamp(String group, String artifact, String version, String commit, String digest) {
        this(group, artifact, version, commit, digest, "");
    }

    /** The prefix every module stamp shares inside a jar. */
    public static final String PREFIX = "META-INF/vexelray/build/";

    /** Where a module's own stamp lives inside its jar: one file per module, so jars never collide. */
    public static String resource(String group, String artifact) {
        return PREFIX + group + "/" + artifact + ".properties";
    }

    /** Whether the tree had uncommitted changes. */
    public boolean dirty() {
        return !digest.isEmpty();
    }

    /** The exact source: the commit, plus {@code +dirty.<digest>} when there were uncommitted changes. */
    public String source() {
        return dirty() ? commit + "+dirty." + digest : commit;
    }

    /** {@code group:artifact:version@source}: one line naming one part of a build exactly. */
    public String line() {
        return group + ":" + artifact + ":" + version + "@" + source();
    }

    /** As the lines of a properties file. */
    public String text() {
        return "group=" + group + "\nartifact=" + artifact + "\nversion=" + version + "\ncommit=" + commit
                + "\ndirty=" + dirty() + "\ndigest=" + digest + "\nsource=" + source() + "\n"
                + (issues.isEmpty() ? "" : "issues=" + issues + "\n");
    }

    /** Read a stamp written by {@link #text()}; {@code null} when it is not one. */
    public static Stamp read(InputStream in) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        String group = p.getProperty("group");
        String artifact = p.getProperty("artifact");
        if (group == null || artifact == null) {
            return null;
        }
        return new Stamp(group, artifact, p.getProperty("version", "-"), p.getProperty("commit", Source.UNKNOWN),
                p.getProperty("digest", ""), p.getProperty("issues", ""));
    }
}
