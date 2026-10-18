package dev.vexelray.build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The exact source of a git checkout: the commit {@code HEAD} names, and a digest of everything that differs from it.
 *
 * <p>The digest covers the whole repository, not only the module being built: a module's binaries also depend on its
 * parent pom and its siblings, so a change anywhere in the repository gives a different source. That can only make
 * two builds look different that were in fact the same, never the reverse, which is the safe way to be wrong.
 *
 * <p>It is SHA-256 over {@code git diff HEAD --binary} (every change to a tracked file, staged or not), then each
 * untracked file that is not ignored, in path order, as its path and its bytes. Ignored files, such as
 * {@code target/}, are not source. The first {@link #DIGEST_HEX} hex digits are kept: enough to tell apart the changes
 * made to one commit in practice, and short enough to read.
 */
public final class Source {

    /** How many hex digits of the digest are kept. */
    static final int DIGEST_HEX = 12;

    /** The commit when there is no git, no checkout, or no commit yet. */
    public static final String UNKNOWN = "unknown";

    private static final long TIMEOUT_SECONDS = 60;

    private Source() {
    }

    /** {@code dir}'s checkout as {@code [commit, digest]}; the digest is empty when the tree is clean. */
    public static String[] of(Path dir) throws IOException {
        byte[] head = git(dir, "rev-parse", "HEAD");
        if (head == null) {
            return new String[] {UNKNOWN, ""};
        }
        String commit = new String(head, StandardCharsets.UTF_8).trim();
        byte[] diff = git(dir, "diff", "HEAD", "--binary");
        byte[] others = git(dir, "ls-files", "--others", "--exclude-standard", "-z", "--full-name", ":/");
        byte[] top = git(dir, "rev-parse", "--show-toplevel");
        if (diff == null || others == null || top == null) {
            throw new IOException("git could read HEAD in " + dir + " but not its changes");
        }
        Path root = Path.of(new String(top, StandardCharsets.UTF_8).trim());
        List<String> untracked = new ArrayList<>();
        for (String name : new String(others, StandardCharsets.UTF_8).split("\0")) {
            if (!name.isEmpty()) {
                untracked.add(name);
            }
        }
        untracked.sort(null);
        if (diff.length == 0 && untracked.isEmpty()) {
            return new String[] {commit, ""};
        }
        MessageDigest sha = sha256();
        sha.update(diff);
        for (String name : untracked) {
            sha.update((byte) 0);
            sha.update(name.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0);
            Path f = root.resolve(name);
            if (Files.isRegularFile(f)) {
                sha.update(Files.readAllBytes(f));
            }
        }
        return new String[] {commit, HexFormat.of().formatHex(sha.digest()).substring(0, DIGEST_HEX)};
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK has SHA-256", e);
        }
    }

    /** Run git in {@code dir}: its standard output, or {@code null} when git failed or is not on the PATH. */
    static byte[] git(Path dir, String... args) throws IOException {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", dir.toString()));
        cmd.addAll(List.of(args));
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            return null;
        }
        byte[] out;
        try (InputStream in = p.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            out = buf.toByteArray();
        }
        try {
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("git " + String.join(" ", args) + " took more than " + TIMEOUT_SECONDS + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted waiting for git", e);
        }
        return p.exitValue() == 0 ? out : null;
    }
}
