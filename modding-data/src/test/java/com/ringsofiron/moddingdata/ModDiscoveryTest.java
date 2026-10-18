package com.ringsofiron.moddingdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModDiscoveryTest {

    @TempDir
    Path mods;

    private void mod(String folder, String manifest) throws IOException {
        Path dir = Files.createDirectories(mods.resolve(folder));
        if (manifest != null) Files.writeString(dir.resolve("mod.toml"), manifest);
    }

    private static List<String> ids(List<ModManifest> list) {
        List<String> out = new ArrayList<>();
        for (ModManifest m : list) out.add(m.id);
        return out;
    }

    @Test
    void ordersByDependenciesThenAlphabetically() throws IOException {
        mod("a_ui", "id = \"ui\"\ndepends = [\"lib\"]\n");
        mod("b_lib", "id = \"lib\"\n");
        mod("c_extra", "id = \"extra\"\nload_after = [\"ui\", \"not_installed\"]\n");
        mod("z_plain", null);
        LoadReport report = new LoadReport();
        List<ModManifest> found = new ModDiscovery(report).discover(mods);
        assertEquals(List.of("lib", "ui", "extra", "z_plain"), ids(found));
        assertFalse(report.hasErrors(), report.toString());
    }

    @Test
    void skipsMissingDependenciesCyclesAndDisabledMods() throws IOException {
        mod("needs_missing", "depends = [\"nope\"]\n");
        mod("needs_needs_missing", "depends = [\"needs_missing\"]\n");
        mod("cycle_a", "depends = [\"cycle_b\"]\n");
        mod("cycle_b", "depends = [\"cycle_a\"]\n");
        mod("off", "enabled = false\n");
        mod("fine", null);
        LoadReport report = new LoadReport();
        List<ModManifest> found = new ModDiscovery(report).discover(mods);
        assertEquals(List.of("fine"), ids(found));
        assertEquals(3, report.errors().size(), report.toString());
    }

    @Test
    void badManifestIsReportedNotThrown() throws IOException {
        mod("broken", "id = = 3\n");
        LoadReport report = new LoadReport();
        assertTrue(new ModDiscovery(report).discover(mods).isEmpty());
        assertTrue(report.hasErrors());
    }
}
