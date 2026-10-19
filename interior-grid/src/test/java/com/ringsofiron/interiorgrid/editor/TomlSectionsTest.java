package com.ringsofiron.interiorgrid.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.moddingdata.DataFileParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

class TomlSectionsTest {

    private static final String FILE = String.join("\n",
            "# Header",
            "",
            "[thing.a]",
            "# about the name",
            "name = \"A\"",
            "list = [",
            "  [1, 2],",
            "  [3, 4]",
            "]",
            "future = 7",
            "",
            "# Introduces b",
            "[thing.b]",
            "name = \"B\"",
            "");

    @Test
    void replacesOneSectionInPlace() {
        String out = TomlSections.put(FILE, "thing", "a", Arrays.asList(
                new TomlSections.Entry("list", "[]"),
                new TomlSections.Entry("name", TomlSections.str("A2"))),
                new HashSet<>(Arrays.asList("name", "list")));
        assertEquals(String.join("\n",
                "# Header",
                "",
                "[thing.a]",
                "list = []",
                "# about the name",
                "name = \"A2\"",
                "future = 7",
                "",
                "# Introduces b",
                "[thing.b]",
                "name = \"B\"",
                ""), out);
        Map<String, Object> tree = DataFileParser.parseToml(out, "test");
        assertNotNull(tree.get("thing"));
    }

    @Test
    void dropsKnownKeysThatAreGone() {
        String out = TomlSections.put(FILE, "thing", "a",
                Collections.singletonList(new TomlSections.Entry("name", "\"A\"")),
                new HashSet<>(Arrays.asList("name", "list")));
        assertFalse(out.contains("list"));
        assertTrue(out.contains("future = 7"), "unknown keys are kept");
    }

    @Test
    void appendsNewSections() {
        String out = TomlSections.put(FILE, "thing", "c",
                Collections.singletonList(new TomlSections.Entry("name", "\"C\"")), Collections.emptySet());
        assertTrue(out.startsWith(FILE));
        assertTrue(out.endsWith("\n[thing.c]\nname = \"C\"\n"));
    }

    @Test
    void removesSections() {
        String out = TomlSections.remove(FILE, "thing", "a");
        assertEquals("# Header\n\n# Introduces b\n[thing.b]\nname = \"B\"\n", out);
        assertFalse(TomlSections.has(out, "thing", "a"));
        assertTrue(TomlSections.has(out, "thing", "b"));
    }

    @Test
    void writesNumbersThatReadBackAsFloats() {
        assertEquals("1.0", TomlSections.num(1f));
        assertEquals("-9.5", TomlSections.num(-9.5f));
        assertEquals("1.4", TomlSections.num(1.4f));
        assertEquals("1.0e7", TomlSections.num(1e7));
        assertEquals("\"a\\\"b\"", TomlSections.str("a\"b"));
    }
}
