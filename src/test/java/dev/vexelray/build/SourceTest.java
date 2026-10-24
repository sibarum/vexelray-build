package dev.vexelray.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exact source of a real git checkout in a temporary folder: clean, changed, changed differently, untracked files,
 * ignored files; and stamps written, found in a jar and in a folder, and listed in a bill.
 */
class SourceTest {

    private static void git(Path dir, String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>(List.of("git", "-C", dir.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new AssertionError("git " + String.join(" ", args) + ": " + out);
        }
    }

    private static Path repo(Path dir) throws Exception {
        git(dir, "init", "-q");
        git(dir, "config", "user.email", "t@example.invalid");
        git(dir, "config", "user.name", "t");
        git(dir, "config", "core.autocrlf", "false");
        Files.writeString(dir.resolve("A.java"), "class A {}\n");
        Files.writeString(dir.resolve(".gitignore"), "target/\n");
        Files.createDirectories(dir.resolve("module"));
        Files.writeString(dir.resolve("module/B.java"), "class B {}\n");
        git(dir, "add", ".");
        git(dir, "commit", "-q", "-m", "one");
        return dir;
    }

    @Test
    void aCleanTreeIsItsCommitAlone(@TempDir Path dir) throws Exception {
        repo(dir);
        String[] s = Source.of(dir.resolve("module"));
        assertEquals(40, s[0].length(), s[0]);
        assertEquals("", s[1]);
    }

    @Test
    void anUncommittedChangeAnywhereGivesADigestAndADifferentChangeAnother(@TempDir Path dir) throws Exception {
        repo(dir);
        String[] clean = Source.of(dir.resolve("module"));
        Files.writeString(dir.resolve("A.java"), "class A { int x; }\n");
        String[] one = Source.of(dir.resolve("module"));
        assertEquals(clean[0], one[0], "the same commit");
        assertEquals(12, one[1].length());
        Files.writeString(dir.resolve("A.java"), "class A { int y; }\n");
        String[] two = Source.of(dir.resolve("module"));
        assertNotEquals(one[1], two[1], "a different change is a different source");
        Files.writeString(dir.resolve("A.java"), "class A { int x; }\n");
        assertEquals(one[1], Source.of(dir.resolve("module"))[1], "and the same change the same one");
    }

    @Test
    void anUntrackedFileCountsAndAnIgnoredOneDoesNot(@TempDir Path dir) throws Exception {
        repo(dir);
        Files.createDirectories(dir.resolve("target"));
        Files.writeString(dir.resolve("target/Built.class"), "bytes");
        assertEquals("", Source.of(dir)[1], "target/ is ignored: not source");
        Files.writeString(dir.resolve("module/C.java"), "class C {}\n");
        String[] withC = Source.of(dir);
        assertEquals(12, withC[1].length());
        Files.writeString(dir.resolve("module/C.java"), "class C { }\n");
        assertNotEquals(withC[1], Source.of(dir)[1], "the untracked file's bytes count, not only its name");
    }

    @Test
    void outsideACheckoutTheSourceIsUnknown(@TempDir Path dir) throws Exception {
        String[] s = Source.of(dir);
        assertEquals(Source.UNKNOWN, s[0]);
    }

    @Test
    void aStampRoundTripsAndNamesItsSourceExactly() throws Exception {
        Stamp clean = new Stamp("dev.x", "app", "1.0-SNAPSHOT", "abc123", "");
        Stamp dirty = new Stamp("dev.x", "app", "1.0-SNAPSHOT", "abc123", "0a1b2c3d4e5f");
        assertEquals("dev.x:app:1.0-SNAPSHOT@abc123", clean.line());
        assertEquals("abc123+dirty.0a1b2c3d4e5f", dirty.source());
        assertEquals(dirty, Stamp.read(new ByteArrayInputStream(dirty.text().getBytes(StandardCharsets.UTF_8))));
        assertEquals("META-INF/vexelray/build/dev.x/app.properties", Stamp.resource("dev.x", "app"));
    }

    @Test
    void stampsAreFoundInAJarAndInAFolderAndABillListsThemInOrder(@TempDir Path dir) throws Exception {
        Stamp a = new Stamp("dev.b", "zeta", "1", "c1", "");
        Stamp b = new Stamp("dev.a", "alpha", "2", "c2", "d2d2d2d2d2d2");
        Path jar = dir.resolve("zeta.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry(Stamp.resource(a.group(), a.artifact())));
            out.write(a.text().getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry(Bill.RESOURCE));      // another build's bill: not a stamp
            out.write("group=x\nartifact=y\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        Path classes = dir.resolve("classes");
        Path f = classes.resolve(Stamp.resource(b.group(), b.artifact()));
        Files.createDirectories(f.getParent());
        Files.writeString(f, b.text());
        assertEquals(List.of(a), Bill.stampsIn(jar));
        assertEquals(List.of(b), Bill.stampsIn(classes));
        Bill bill = new Bill(new Stamp("dev.app", "app", "0.1", "c0", ""), "dev.app.app", null, List.of(a, b));
        String text = bill.text();
        assertTrue(text.contains("app.id=dev.app.app\nstack.count=2\nstack.1=dev.a:alpha:2@c2+dirty.d2d2d2d2d2d2\nstack.2=dev.b:zeta:1@c1\n"),
                text);
    }
}
