package dev.vexelray.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformMarksTest {

    private static List<Integer> lines(String source) {
        return PlatformMarks.scan(source).stream().map(PlatformMarks.Found::line).toList();
    }

    @Test
    void codeThatAsksOrNamesAPlatformIsFound() {
        String source = """
                class A {
                    boolean w = System.getProperty("os.name").startsWith("Windows");
                    Object k = SymbolLookup.libraryLookup("kernel32", arena);
                    Object v = SymbolLookup.libraryLookup("libX11.so.6", arena);
                    Object d = new File("/dev/null");
                    String c = "cmd.exe /c dir";
                    boolean p = platform == Platform.WINDOWS;
                    Object n = lib("nfd.dll");
                }
                """;
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8), lines(source));
    }

    @Test
    void reportingAndLookalikesAreNotDeciding() {
        String source = """
                class A {
                    String header = String.format("%s on %s", System.getProperty("java.version"), System.getProperty("os.name"));
                    Object lang = Map.entry("cmd", "source.batchfile");
                    Object mod = MODS.put("cmd", Modifier.SUPER);
                    List<String> blocked = List.of(".exe", ".dll", ".sys");
                }
                """;
        assertTrue(lines(source).isEmpty(), lines(source).toString());
    }

    @Test
    void commentsMayNameAnything() {
        String source = """
                /** Binds kernel32.dll, unlike "os.name" checks. */
                class A {
                    // System.getProperty("os.name")
                    /* "/dev/null" and
                       Platform.WINDOWS */
                    int x = 1; // "libc.so.6"
                }
                """;
        assertTrue(lines(source).isEmpty(), lines(source).toString());
    }

    @Test
    void aCommentInsideAStringIsTheString() {
        String source = "class A { String s = \"// not a comment\"; Object k = lib(\"user32\"); }\n";
        assertEquals(List.of(1), lines(source));
    }

    @Test
    void lineNumbersSurviveABlockComment() {
        String source = "/*\n\n*/\nclass A { boolean w = System.getProperty(\"os.name\").startsWith(\"W\"); }\n";
        assertEquals(List.of(4), lines(source));
    }

    @Test
    void osModulesAreRecognisedByTheirName() {
        assertTrue(IsolateMojo.OS_MODULE.matcher("vexelray-os-linux").matches());
        assertTrue(IsolateMojo.OS_MODULE.matcher("ffmpeg-wrapper-windows").matches());
        assertTrue(!IsolateMojo.OS_MODULE.matcher("vexelray-os-api").matches());
        assertTrue(!IsolateMojo.OS_MODULE.matcher("linux-tools").matches());
    }
}
