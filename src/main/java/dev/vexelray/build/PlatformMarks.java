package dev.vexelray.build;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What platform-specific code looks like, and finding it in a Java source with its comments taken out (a comment may
 * name {@code kernel32}; only code may call it).
 *
 * <p>Each mark is something only one operating system has or answers: asking which OS this is, a native library
 * named by its file, an OS's own shell or device, or a branch on a platform constant. Shared code asks an interface
 * instead, and the answer lives in a {@code -windows}, {@code -linux} or {@code -macos} module.
 */
final class PlatformMarks {

    /** One kind of platform-specific code: what it looks like, and what to say about it. */
    record Mark(Pattern pattern, String what) {
    }

    /** One place a mark was found. */
    record Found(int line, String what, String text) {
    }

    static final List<Mark> MARKS = List.of(
            // Deciding by the OS's name. Reading it to report (a log header, a benchmark's machine) is not deciding.
            new Mark(Pattern.compile("\"os\\.name\"[^;]*?\\)\\s*\\.\\s*(toLowerCase|startsWith|contains|equals|matches|endsWith)"),
                    "decides by which OS this is (os.name)"),
            new Mark(Pattern.compile("\"[^\"\\s]*[\\w-]\\.(dll|dylib)\""), "names a native library file"),
            new Mark(Pattern.compile("\"[^\"\\s]*lib[\\w+-]+\\.so(\\.\\d+)*\""), "names a native library file"),
            new Mark(Pattern.compile("\"(kernel32|user32|gdi32|shell32|dwmapi|winmm|ole32|advapi32|ntdll)\""),
                    "names a Windows system library"),
            new Mark(Pattern.compile("\"(cmd|powershell|pwsh)\\.exe\\b"), "names a Windows shell"),
            new Mark(Pattern.compile("\"/bin/(ba|z|da)?sh\""), "names a Unix shell"),
            new Mark(Pattern.compile("\"(/dev/null|NUL)\""), "names an OS's null device"),
            new Mark(Pattern.compile("\\bPlatform\\.(WINDOWS|LINUX|MACOS)\\b"), "branches on a platform constant"));

    private PlatformMarks() {
    }

    /** Every mark in {@code source}, by line. */
    static List<Found> scan(String source) {
        String code = withoutComments(source);
        List<Found> found = new ArrayList<>();
        String[] lines = code.split("\n", -1);
        String[] original = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            for (Mark mark : MARKS) {
                Matcher m = mark.pattern().matcher(lines[i]);
                if (m.find()) {
                    found.add(new Found(i + 1, mark.what(), original[i].strip()));
                }
            }
        }
        return found;
    }

    /**
     * {@code source} with its comments blanked out and everything else, string literals and line breaks included,
     * where it was: so a line number here is a line number in the file.
     */
    static String withoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int n = source.length();
        int i = 0;
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else if (c == '"' && source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                out.append(source, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && source.charAt(j) != c && source.charAt(j) != '\n') {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                j = Math.min(n, j + 1);
                out.append(source, i, j);
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
