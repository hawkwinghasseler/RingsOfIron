package com.ringsofiron.moddingdata;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a {@code .toml} or {@code .json} file into plain Java maps, lists, strings,
 * numbers and booleans. Both formats produce the same tree, so every loader downstream
 * treats them alike.
 */
public final class DataFileParser {

    private static final Gson GSON = new Gson();

    private DataFileParser() {
    }

    public static boolean isDataFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".toml") || name.endsWith(".json");
    }

    /**
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if it is not valid TOML/JSON, with the line in the message
     */
    public static Map<String, Object> parse(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".json")) return parseJson(text, file.toString());
        return parseToml(text, file.toString());
    }

    public static Map<String, Object> parseToml(String text, String sourceName) {
        TomlParseResult result = Toml.parse(text);
        if (result.hasErrors()) {
            StringBuilder sb = new StringBuilder("Invalid TOML in ").append(sourceName).append(':');
            result.errors().forEach(e -> sb.append("\n  ").append(e.toString()));
            throw new IllegalArgumentException(sb.toString());
        }
        return fromToml(result);
    }

    public static Map<String, Object> parseJson(String text, String sourceName) {
        try {
            Map<String, Object> map = GSON.fromJson(text, new TypeToken<LinkedHashMap<String, Object>>() { }.getType());
            return map == null ? new LinkedHashMap<>() : map;
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid JSON in " + sourceName + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> fromToml(TomlTable table) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            out.put(key, fromTomlValue(table.get(List.of(key))));
        }
        return out;
    }

    private static Object fromTomlValue(Object value) {
        if (value instanceof TomlTable) return fromToml((TomlTable) value);
        if (value instanceof TomlArray) {
            TomlArray array = (TomlArray) value;
            List<Object> list = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) list.add(fromTomlValue(array.get(i)));
            return list;
        }
        if (value instanceof java.time.temporal.TemporalAccessor) return value.toString();
        return value;
    }
}
