package dev.vexelray.build;

import java.util.regex.Pattern;

/**
 * The name an application's windows go by on the desktop, so the desktop can tell whose they are: {@code WM_CLASS} on
 * X11, the {@code app_id} on Wayland, and the name of the application's desktop entry, which a taskbar or dock matches
 * against them to show the right icon. By default {@code <groupId>.<artifactId>}; the property
 * {@code vexelray.app.id} overrides it, for an application whose coordinates are awkward as a name or have changed
 * since it was first installed.
 *
 * <p>It is a desktop entry name as well, so it follows the rules for one: dot-separated parts of letters, digits,
 * {@code _} and {@code -}, none starting with a digit, at least two of them.
 */
final class AppId {

    /** What a desktop entry name may be (the Desktop Entry Specification, "Desktop File ID"). */
    static final Pattern VALID = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*)+");

    /** Where an application keeps its installer configuration, beside its pom. */
    static final String INSTALLER_CONFIG = "installer.json";

    private AppId() {
    }

    /** The override when there is one, else {@code group.artifact}. */
    static String resolve(String override, String group, String artifact) {
        return override == null || override.isBlank() ? group + "." + artifact : override.trim();
    }

    /** Why {@code id} cannot be an application id, or {@code null} when it can. */
    static String invalid(String id) {
        return VALID.matcher(id).matches() ? null : "'" + id + "' cannot be an application id: it names the"
                + " application's desktop entry, so it is dot-separated parts of letters, digits, _ and -, none starting"
                + " with a digit (set vexelray.app.id to one that is)";
    }

    /**
     * Whether an {@value #INSTALLER_CONFIG} agrees with the id its application was built with: the installer names the
     * desktop entry after its {@code appId}, and a dock matches that against the windows. So a config with a
     * {@code linux} section must declare it, and every {@code appId} in it must be this one.
     *
     * @return what is wrong, or {@code null} when nothing is
     */
    static String mismatch(InstallerJson installer, String id) {
        for (String d : installer.appIds()) {
            if (!d.equals(id)) {
                return INSTALLER_CONFIG + " declares \"appId\": \"" + d + "\", but the application is built as " + id
                        + ", so its windows would not match its desktop entry. Make them the same: change the appId,"
                        + " or set vexelray.app.id in the pom";
            }
        }
        if (installer.appIds().isEmpty() && installer.linux()) {
            return INSTALLER_CONFIG + " has a linux section but no appId, so the installed desktop entry would not match"
                    + " the application's windows, and the dock would show them without its icon. Add"
                    + " \"appId\": \"" + id + "\" to the linux binary that has the shortcuts";
        }
        return null;
    }
}
