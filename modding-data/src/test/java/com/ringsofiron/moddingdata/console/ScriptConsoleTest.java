package com.ringsofiron.moddingdata.console;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.moddingdata.console.ScriptConsole.Kind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The developer console's engine: commands, variables, errors, history, completion and script files. */
class ScriptConsoleTest {
    final ScriptConsole console = new ScriptConsole();

    String last() {
        List<ScriptConsole.Line> lines = console.lines();
        return lines.get(lines.size() - 1).text;
    }

    Kind lastKind() {
        List<ScriptConsole.Line> lines = console.lines();
        return lines.get(lines.size() - 1).kind;
    }

    @Test
    void aCommandRunsWithOrWithoutBracketsAndArguments() {
        List<Object> calls = new ArrayList<>();
        console.define("hit", "hit(points)", "hits", args -> {
            calls.add(args.length == 0 ? "none" : args[0]);
            return null;
        });
        console.submit("hit()");
        console.submit("hit 40");
        console.submit("hit(12.5)");
        console.submit("hit");
        assertEquals(List.of("none", 40, 12.5, "none"), calls.stream()
                .map(o -> o instanceof java.math.BigDecimal ? ((java.math.BigDecimal) o).doubleValue() : o).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void variablesLastFromOneLineToTheNextAndResultsAreWrittenBack() {
        console.submit("x = 6");
        console.submit("x * 7");
        assertEquals("42", last());
        assertEquals(Kind.RESULT, lastKind());
        console.set("target", "Bea");
        console.submit("target.toUpperCase()");
        assertEquals("BEA", last());
    }

    @Test
    void printlnGoesToTheScrollback() {
        console.submit("println 'hello'; print 'there'");
        List<String> texts = console.lines().stream().map(l -> l.text).collect(java.util.stream.Collectors.toList());
        assertTrue(texts.contains("hello"), texts.toString());
        assertTrue(texts.contains("there"), texts.toString());
    }

    @Test
    void errorsAreOneLineAndSuggestTheNearestCommand() {
        console.define("heal", "heal()", "heals", args -> null);
        console.submit("hael()");
        assertEquals(Kind.ERROR, lastKind());
        assertTrue(last().contains("did you mean heal"), last());
        console.submit("1 +");
        assertTrue(last().startsWith("Syntax error"), last());
        console.define("fail", "fail()", "fails", args -> {
            throw new ScriptConsole.ConsoleException("Select a crew member first");
        });
        console.submit("fail()");
        assertEquals("Select a crew member first", last());
        console.submit("nobody.name");
        assertTrue(last().startsWith("Nothing called nobody"), last());
    }

    @Test
    void historyGoesBackAndForwardAndRemembersTheDraft() {
        console.submit("1");
        console.submit("2");
        console.submit("2");
        assertEquals(List.of("1", "2"), console.history(), "a repeat is kept once");
        assertEquals("2", console.historyUp("dra"));
        assertEquals("1", console.historyUp("2"));
        assertEquals("1", console.historyUp("1"), "stops at the oldest");
        assertEquals("2", console.historyDown());
        assertEquals("dra", console.historyDown(), "past the newest, what was being typed");
    }

    @Test
    void historyIsKeptInItsFile(@TempDir Path dir) {
        Path file = dir.resolve("history.txt");
        console.setHistoryFile(file);
        console.submit("x = 1");
        ScriptConsole again = new ScriptConsole();
        again.setHistoryFile(file);
        assertEquals(List.of("x = 1"), again.history());
    }

    @Test
    void tabFinishesCommandsVariablesAndMembers() {
        console.define("heal", "heal()", "heals", args -> null);
        console.define("health", "health()", "health", args -> null);
        console.define("kill", "kill()", "kills", args -> null);
        ScriptConsole.Completion c = console.complete("ki", 2);
        assertEquals("kill(", c.text, "one fit: finished, with its bracket");
        c = console.complete("hea", 3);
        assertEquals("heal", c.text, "two fits: as far as they agree");
        assertEquals(List.of("heal", "health"), c.candidates);

        console.set("ship", new StringBuilder("abc"));
        c = console.complete("ship.rev", 8);
        assertEquals("ship.reverse(", c.text);
        c = console.complete("x = 1; ki", 9);
        assertEquals("x = 1; kill(", c.text);
        assertTrue(console.complete("zzz", 3).candidates.isEmpty());
    }

    @Test
    void scriptFilesRunInTheSameBindingAndCanDefineCommands(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("boom.groovy"),
                "console.define('boom', 'boom()', 'goes bang', { -> 'bang' })\nloaded = true\n", StandardCharsets.UTF_8);
        console.setScriptDir(dir);
        console.submit("run 'boom'");
        assertEquals(true, console.get("loaded"));
        console.submit("boom()");
        assertEquals("bang", last());
        assertEquals(List.of("boom"), console.scripts());
        console.submit("run 'nothing'");
        assertTrue(last().startsWith("No script called nothing"), last());
        assertFalse(console.runIfPresent("autoexec"));
    }

    @Test
    void aScriptInTheFolderRunsByItsNameWithArguments(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("double_it.groovy"), "args ? args[0] * 2 : 'nothing'\n", StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("crew"));
        Files.writeString(dir.resolve("crew/hello.groovy"), "println 'hi from crew'\n", StandardCharsets.UTF_8);
        console.setScriptDir(dir);
        console.submit("double_it 21");
        assertEquals("42", last());
        console.submit("double_it");
        assertEquals("nothing", last());
        console.submit("run 'crew/hello'");
        assertEquals("hi from crew", last());
        assertEquals(List.of("crew/hello", "double_it"), console.scripts());
        assertEquals("double_it(", console.complete("doub", 4).text);
    }

    @Test
    void helpListsCommandsByGroupAndOneCommand() {
        console.define("kill", "kill([target])", "kills the selection", "Crew", args -> null);
        console.submit("help()");
        String all = console.lines().stream().map(l -> l.text).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(all.contains("Crew:"), all);
        assertTrue(all.contains("kill([target])"), all);
        console.submit("help kill");
        assertTrue(last().startsWith("kill([target])"), last());
    }

    @Test
    void theScrollbackKeepsItsNewestLines() {
        for (int i = 0; i < ScriptConsole.MAX_LINES + 50; i++) console.print(i);
        assertEquals(ScriptConsole.MAX_LINES, console.lines().size());
        assertEquals(String.valueOf(ScriptConsole.MAX_LINES + 49), last());
    }

    @Test
    void theFormatterWritesResultsAndListsElementByElement() {
        console.setFormatter(o -> "<" + o + ">");
        console.submit("[1, 2]");
        int n = console.lines().size();
        assertEquals("<1>", console.lines().get(n - 2).text);
        assertEquals("<2>", console.lines().get(n - 1).text);
    }
}
