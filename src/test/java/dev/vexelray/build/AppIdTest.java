package dev.vexelray.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppIdTest {

    @Test
    void theCoordinatesAreTheIdUnlessOverridden() {
        assertEquals("dev.sibarum.pix", AppId.resolve(null, "dev.sibarum", "pix"));
        assertEquals("dev.sibarum.pix", AppId.resolve("  ", "dev.sibarum", "pix"));
        assertEquals("dev.vexelray.calculator", AppId.resolve(" dev.vexelray.calculator ", "dev.vexelray.demo",
                "calculator-vexel-demo"));
    }

    @Test
    void anIdIsADesktopEntryName() {
        assertNull(AppId.invalid("dev.vexelray.demo.text-editor-vexel-demo"));
        assertNull(AppId.invalid("dev.mainframe.mainframe"));
        assertNotNull(AppId.invalid("pix"));                  // one part
        assertNotNull(AppId.invalid("dev.2d.pix"));           // a part starting with a digit
        assertNotNull(AppId.invalid("dev..pix"));
        assertNotNull(AppId.invalid("dev.sibarum.pix viewer"));
    }

    private static InstallerJson json(String text) {
        return InstallerJson.parse(text);
    }

    @Test
    void theInstallerConfigIsReadForWhatTheBuildChecks() {
        InstallerJson c = json("""
                { "linux": { "binaries": [ { "name": "pix", "appId": "dev.sibarum.pix",
                    "fileAssociations": [ { "id": "not-this-one" } ] } ] },
                  "replaces": [ { "id": "viewer", "repoPath": "apps/viewer" }, { "id": "pics" } ],
                  "requires": [ { "id": "runtime" } ],
                  "id": "pix", "name": "Pix \\"\\u0041\\"", "version": 1 }
                """);
        assertEquals("pix", c.id(), "the top-level id, wherever it is and whatever else has one");
        assertEquals(java.util.List.of("viewer", "pics"), c.replaces());
        assertEquals(java.util.List.of("dev.sibarum.pix"), c.appIds());
        assertTrue(c.linux());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> json("{ \"id\": "));
    }

    @Test
    void theInstallerConfigMustDeclareTheSameId() {
        String id = "dev.sibarum.pix";
        assertNull(AppId.mismatch(json("{ \"linux\": { \"binaries\": [ { \"name\": \"pix\", \"appId\": \"dev.sibarum.pix\" } ] } }"), id));
        assertNull(AppId.mismatch(json("{ \"windows\": { \"binaries\": [] } }"), id));    // nothing on Linux to match
        String other = AppId.mismatch(json("{ \"linux\": { \"binaries\": [ { \"appId\" : \"dev.sibarum.viewer\" } ] } }"), id);
        assertTrue(other != null && other.contains("dev.sibarum.viewer") && other.contains(id), other);
        String missing = AppId.mismatch(json("{ \"linux\": { \"binaries\": [ { \"name\": \"pix\" } ] } }"), id);
        assertTrue(missing != null && missing.contains("\"appId\": \"dev.sibarum.pix\""), missing);
    }
}
