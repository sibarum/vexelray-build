package dev.vexelray.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the build checks in an application's {@code installer.json}: its {@code id}, the ids it {@code replaces}, and
 * every binary's {@code appId}. Read with just enough JSON for that, since a Maven plugin that carries no JSON library
 * should not take one on for three fields: objects become maps, arrays lists, strings strings, and every other value
 * its text.
 *
 * @param id       the top-level {@code id}, or {@code null}
 * @param replaces the {@code id} of every {@code replaces} entry
 * @param appIds   every binary's {@code appId}, Windows and Linux
 * @param linux    whether there is a {@code linux} section
 */
record InstallerJson(String id, List<String> replaces, List<String> appIds, boolean linux) {

    static InstallerJson parse(String json) {
        Object top = new Reader(json).document();
        if (!(top instanceof Map<?, ?> config)) {
            throw new IllegalArgumentException("not a JSON object");
        }
        List<String> replaces = new ArrayList<>();
        if (config.get("replaces") instanceof List<?> list) {
            for (Object r : list) {
                if (r instanceof Map<?, ?> m && m.get("id") instanceof String s) {
                    replaces.add(s);
                }
            }
        }
        List<String> appIds = new ArrayList<>();
        for (String platform : List.of("windows", "linux")) {
            if (config.get(platform) instanceof Map<?, ?> p && p.get("binaries") instanceof List<?> bins) {
                for (Object b : bins) {
                    if (b instanceof Map<?, ?> m && m.get("appId") instanceof String s) {
                        appIds.add(s);
                    }
                }
            }
        }
        return new InstallerJson(config.get("id") instanceof String s ? s : null, replaces, appIds,
                config.get("linux") instanceof Map<?, ?>);
    }

    private static final class Reader {
        private final String s;
        private int i;

        Reader(String s) {
            this.s = s;
        }

        Object document() {
            Object v = value();
            space();
            if (i != s.length()) {
                throw error("text after the value");
            }
            return v;
        }

        private Object value() {
            space();
            if (i >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                space();
                if (peek('}')) {
                    return m;
                }
                do {
                    space();
                    String k = string();
                    space();
                    expect(':');
                    m.put(k, value());
                    space();
                } while (peek(','));
                expect('}');
                return m;
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                space();
                if (peek(']')) {
                    return l;
                }
                do {
                    l.add(value());
                    space();
                } while (peek(','));
                expect(']');
                return l;
            }
            if (c == '"') {
                return string();
            }
            int start = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            if (start == i) {
                throw error("expected a value");
            }
            return s.substring(start, i);
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    break;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw error("short \\u escape");
                        }
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
            throw error("unterminated string");
        }

        private void space() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private boolean peek(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error("expected '" + c + "'");
            }
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException(what + " at character " + i);
        }
    }
}
