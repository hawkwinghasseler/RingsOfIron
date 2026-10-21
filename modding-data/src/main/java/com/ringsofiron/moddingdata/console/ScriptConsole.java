package com.ringsofiron.moddingdata.console;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The developer console's engine, without any drawing: a Groovy shell whose variables last from
 * one line to the next, the commands registered with {@link #define}, the scrollback, the command
 * history and tab completion. The game puts a window over it (ui-hud's {@code DevConsoleWindow})
 * and registers the commands that act on the world.
 *
 * <p>Every line typed is a Groovy script. Commands are closures in the shell's binding, so
 * {@code kill()}, {@code damage 40} and {@code damage(40, someone)} all call one, and a command
 * typed on its own ({@code heal}) runs with no arguments. A variable assigned without {@code def}
 * ({@code x = selected}) stays for later lines; {@code println} writes to the scrollback.
 *
 * <p>Script files run from {@link #setScriptDir the script folder} with {@code run "name"}, in the
 * same binding, so a file can define commands for the lines after it. Not sandboxed: a script can
 * do anything the game can (see {@code ModTrustPolicy} for mods; the console only runs what its user
 * types or names).
 */
public class ScriptConsole {
    /** Lines kept in the scrollback before the oldest go. */
    public static final int MAX_LINES = 2_000;
    /** Commands kept in the history. */
    public static final int MAX_HISTORY = 300;
    /** Candidates printed for one Tab before the rest are counted rather than listed. */
    public static final int MAX_LISTED = 40;

    /** What a scrollback line is, for its colour. */
    public enum Kind { INPUT, OUTPUT, RESULT, INFO, ERROR }

    public static final class Line {
        public final Kind kind;
        public final String text;

        Line(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** A command's body: its arguments as typed, already evaluated. */
    @FunctionalInterface
    public interface Command {
        Object run(Object[] args) throws Exception;
    }

    /** A registered command, as {@link #help} lists it. */
    public static final class CommandInfo {
        public final String name;
        /** How to call it, such as {@code damage(points, [target])}. */
        public final String usage;
        public final String help;
        public final String group;
        final Command body;

        CommandInfo(String name, String usage, String help, String group, Command body) {
            this.name = name;
            this.usage = usage;
            this.help = help;
            this.group = group;
            this.body = body;
        }
    }

    /**
     * Thrown by a command for a plain error line without a stack's worth of class names, such as
     * "Select a crew member first".
     */
    public static class ConsoleException extends RuntimeException {
        public ConsoleException(String message) {
            super(message);
        }
    }

    /** What one Tab press found. */
    public static final class Completion {
        /** The text with the completion put in, and where the cursor goes. */
        public final String text;
        public final int cursor;
        /** Every name that fits, sorted; empty when nothing did. */
        public final List<String> candidates;

        Completion(String text, int cursor, List<String> candidates) {
            this.text = text;
            this.cursor = cursor;
            this.candidates = candidates;
        }
    }

    /** Variables read fresh each time a line names them, such as the selection. */
    private final Map<String, Supplier<?>> live = new TreeMap<>();
    private final Binding binding = new Binding() {
        @Override
        public Object getVariable(String name) {
            Supplier<?> s = live.get(name);
            return s != null ? s.get() : super.getVariable(name);
        }

        @Override
        public boolean hasVariable(String name) {
            return live.containsKey(name) || super.hasVariable(name);
        }

        @Override
        public void setVariable(String name, Object value) {
            if (live.containsKey(name)) throw new ConsoleException(name + " can't be assigned; it is read from the game each time");
            super.setVariable(name, value);
        }
    };
    private final PrintWriter out = new PrintWriter(new LineWriter(Kind.OUTPUT), true);
    private final ClassLoader parent;
    private final Set<String> imports = new LinkedHashSet<>();
    private final Set<String> starImports = new LinkedHashSet<>();
    private GroovyShell shell;
    private int scriptCount;

    private final Map<String, CommandInfo> commands = new TreeMap<>();
    private final Map<String, String> variableHelp = new TreeMap<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<Line> view = Collections.unmodifiableList(lines);
    private final List<Consumer<Line>> listeners = new ArrayList<>();
    private int revision;

    private final List<String> history = new ArrayList<>();
    /** Where Up has got to in {@link #history}; {@code history.size()} is the line being typed. */
    private int historyAt;
    /** What was being typed before Up first went back, for Down to come back to. */
    private String draft = "";
    private Path historyFile;
    private Path scriptDir;

    private Function<Object, String> formatter = String::valueOf;

    public ScriptConsole() {
        this(ScriptConsole.class.getClassLoader());
    }

    /** @param parent the class loader scripts see the game's classes through */
    public ScriptConsole(ClassLoader parent) {
        this.parent = parent;
        binding.setVariable("out", out);
        binding.setVariable("console", this);
        variableHelp.put("console", "this console: console.define(...) adds a command, console.print(...) writes a line");
        builtins();
    }

    // --- Setting it up ---------------------------------------------------------------

    /**
     * Registers a command, callable from any line as {@code name(...)}. {@code usage} is how
     * {@link #help} shows the call; the arguments arrive evaluated, as an array.
     */
    public CommandInfo define(String name, String usage, String help, Command body) {
        return define(name, usage, help, "General", body);
    }

    /** {@link #define(String, String, String, Command)}, listed under {@code group} by {@link #help}. */
    public CommandInfo define(String name, String usage, String help, String group, Command body) {
        CommandInfo info = new CommandInfo(name, usage, help, group, body);
        commands.put(name, info);
        binding.setVariable(name, new CommandClosure(this, info));
        return info;
    }

    /** A Groovy closure as a command, for scripts: {@code console.define('boom', 'boom()', 'Bang', { ... })}. */
    public CommandInfo define(String name, String usage, String help, Closure<?> body) {
        return define(name, usage, help, "Scripts", args -> body.call(args));
    }

    /** Sets a variable every line sees; {@code help} lists it with {@code description} when given. */
    public void set(String name, Object value, String description) {
        binding.setVariable(name, value);
        if (description != null) variableHelp.put(name, description);
    }

    /**
     * A variable whose value {@code value} gives each time a line reads it, such as
     * {@code selected}; lines can't assign it.
     */
    public void live(String name, Supplier<?> value, String description) {
        live.put(name, value);
        if (description != null) variableHelp.put(name, description);
    }

    public void set(String name, Object value) {
        set(name, value, null);
    }

    public Object get(String name) {
        return binding.hasVariable(name) ? binding.getVariable(name) : null;
    }

    /** Classes every line can name without a package: {@code "com.badlogic.ashley.core.Entity"}. */
    public void addImports(String... classNames) {
        Collections.addAll(imports, classNames);
        shell = null;
    }

    /** Packages every line can use classes from without naming them: {@code "com.ringsofiron.aicrew"}. */
    public void addStarImports(String... packages) {
        Collections.addAll(starImports, packages);
        shell = null;
    }

    /** How results are written back, such as an entity as its name. Collections are written element by element. */
    public void setFormatter(Function<Object, String> formatter) {
        this.formatter = formatter != null ? formatter : String::valueOf;
    }

    /** Where {@code run "name"} finds script files; null turns script files off. */
    public void setScriptDir(Path dir) {
        scriptDir = dir;
    }

    public Path getScriptDir() {
        return scriptDir;
    }

    /** Keeps the history in {@code file} between runs: reads it now, and writes it after every line. */
    public void setHistoryFile(Path file) {
        historyFile = file;
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            history.clear();
            for (String s : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!s.isEmpty()) history.add(s);
            }
            while (history.size() > MAX_HISTORY) history.remove(0);
        } catch (IOException e) {
            error("Couldn't read the history: " + e.getMessage());
        }
        historyAt = history.size();
    }

    public void addListener(Consumer<Line> listener) {
        listeners.add(listener);
    }

    // --- Running lines ---------------------------------------------------------------

    /**
     * Runs one line as typed: it goes into the history and the scrollback, then runs, and its
     * result (when it has one) or its error is written back. Returns the result, or null.
     */
    public Object submit(String input) {
        String line = input == null ? "" : input.strip();
        historyAt = history.size();
        draft = "";
        if (line.isEmpty()) return null;
        if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) {
            history.add(line);
            while (history.size() > MAX_HISTORY) history.remove(0);
            saveHistory();
        }
        historyAt = history.size();
        add(Kind.INPUT, "> " + line);
        return evaluate(line, true);
    }

    /**
     * Runs {@code source} without echoing it or keeping it in the history, as a script file or
     * key binding would; errors are still written back. Returns the result, or null.
     */
    public Object execute(String source) {
        return evaluate(source, false);
    }

    private Object evaluate(String source, boolean echoResult) {
        try {
            Object result = shell().evaluate(source, "console" + (++scriptCount) + ".groovy");
            // A command typed on its own, "heal", runs rather than printing the closure
            if (result instanceof CommandClosure) result = ((CommandClosure) result).call();
            out.flush();
            if (echoResult && result != null) add(Kind.RESULT, format(result));
            return result;
        } catch (Throwable t) {
            out.flush();
            // A script file's name typed as a command runs the file: "repair_all" or "spawn_wing 3"
            String script = missingName(t);
            if (script != null && resolveScript(script) != null) {
                try {
                    Object result = runFile(script, missingArgs(t));
                    out.flush();
                    if (echoResult && result != null) add(Kind.RESULT, format(result));
                    return result;
                } catch (Throwable inScript) {
                    out.flush();
                    error(script + ": " + describe(inScript));
                    return null;
                }
            }
            error(describe(t));
            return null;
        } finally {
            out.flush();
        }
    }

    /** Runs a script file from the script folder (or any path), in the console's binding. */
    public Object runFile(String name) {
        return runFile(name, new Object[0]);
    }

    /**
     * Runs a script file with {@code args} as its {@code args} variable, in the console's
     * binding, so what it sets without {@code def} stays for later lines.
     */
    public Object runFile(String name, Object[] args) {
        Path file = resolveScript(name);
        if (file == null) throw new ConsoleException("No script called " + name
                + (scriptDir == null ? "" : " in " + scriptDir) + "; scripts() lists them");
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConsoleException("Couldn't read " + file + ": " + e.getMessage());
        }
        Object before = binding.hasVariable("args") ? binding.getVariable("args") : null;
        binding.setVariable("args", args);
        try {
            String className = file.getFileName().toString().replaceAll("\\.groovy$", "").replaceAll("[^A-Za-z0-9_]", "_");
            return shell().evaluate(source, "script_" + className + ".groovy");
        } finally {
            binding.setVariable("args", before);
        }
    }

    /** The command or variable name a script didn't find, when that is why it failed. */
    private static String missingName(Throwable t) {
        while ((t instanceof InvokerInvocationException || t instanceof InvocationTargetException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof MissingMethodException) {
            MissingMethodException m = (MissingMethodException) t;
            return m.getType() != null && groovy.lang.Script.class.isAssignableFrom(m.getType()) ? m.getMethod() : null;
        }
        if (t instanceof MissingPropertyException) return ((MissingPropertyException) t).getProperty();
        return null;
    }

    private static Object[] missingArgs(Throwable t) {
        while ((t instanceof InvokerInvocationException || t instanceof InvocationTargetException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof MissingMethodException && ((MissingMethodException) t).getArguments() != null) {
            return ((MissingMethodException) t).getArguments();
        }
        return new Object[0];
    }

    /** Runs {@code name} from the script folder if it is there, writing any error back; for an autoexec script. */
    public boolean runIfPresent(String name) {
        if (resolveScript(name) == null) return false;
        try {
            runFile(name);
            return true;
        } catch (Throwable t) {
            error(name + ": " + describe(t));
            return false;
        }
    }

    Path resolveScript(String name) {
        if (name == null || name.isBlank()) return null;
        Path direct = Path.of(name);
        if (direct.isAbsolute()) return Files.isRegularFile(direct) ? direct : null;
        if (scriptDir == null) return null;
        for (String candidate : new String[]{name, name + ".groovy"}) {
            Path p = scriptDir.resolve(candidate).normalize();
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    /**
     * The script files in the script folder and the folders in it, by path from the folder
     * without {@code .groovy} ({@code "crew/heal_all"}).
     */
    public List<String> scripts() {
        if (scriptDir == null || !Files.isDirectory(scriptDir)) return List.of();
        try (Stream<Path> files = Files.walk(scriptDir)) {
            return files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".groovy"))
                    .map(p -> scriptDir.relativize(p).toString().replace('\\', '/'))
                    .map(n -> n.substring(0, n.length() - ".groovy".length())).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    private GroovyShell shell() {
        if (shell != null) return shell;
        CompilerConfiguration config = new CompilerConfiguration();
        ImportCustomizer customizer = new ImportCustomizer();
        for (String c : imports) customizer.addImports(c);
        for (String p : starImports) customizer.addStarImports(p);
        config.addCompilationCustomizers(customizer);
        shell = new GroovyShell(parent, binding, config);
        return shell;
    }

    /** One line saying what went wrong, without the stack. */
    String describe(Throwable t) {
        while ((t instanceof InvokerInvocationException || t instanceof InvocationTargetException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof ConsoleException) return t.getMessage();
        // The game's own refusals ("not a hull tile") read as they are
        if ((t instanceof IllegalArgumentException || t instanceof IllegalStateException) && t.getMessage() != null
                && t.getClass().getPackageName().startsWith("java.")) {
            return t.getMessage();
        }
        if (t instanceof MultipleCompilationErrorsException) {
            List<? extends Message> errors = ((MultipleCompilationErrorsException) t).getErrorCollector().getErrors();
            if (errors != null && !errors.isEmpty() && errors.get(0) instanceof SyntaxErrorMessage) {
                String m = ((SyntaxErrorMessage) errors.get(0)).getCause().getOriginalMessage();
                return "Syntax error: " + m.split("\n")[0];
            }
            return "Syntax error: " + firstLine(t.getMessage());
        }
        if (t instanceof MissingMethodException) {
            MissingMethodException m = (MissingMethodException) t;
            String name = m.getMethod();
            if (m.getType() != null && !groovy.lang.Script.class.isAssignableFrom(m.getType())) {
                return "No " + name + "(...) on " + m.getType().getSimpleName() + " for those arguments";
            }
            String near = closest(name, names());
            return "No command " + name + "()" + (near != null ? "; did you mean " + near + "?" : "") + " help() lists them";
        }
        if (t instanceof MissingPropertyException) {
            MissingPropertyException m = (MissingPropertyException) t;
            String near = closest(m.getProperty(), names());
            return "Nothing called " + m.getProperty() + (near != null ? "; did you mean " + near + "?" : "");
        }
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message != null ? ": " + firstLine(message) : "");
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    // --- Scrollback ------------------------------------------------------------------

    public void print(Object text) {
        add(Kind.OUTPUT, text == null ? "null" : format(text));
    }

    public void info(String text) {
        add(Kind.INFO, text);
    }

    public void error(String text) {
        add(Kind.ERROR, text);
    }

    public void clear() {
        lines.clear();
        revision++;
    }

    private void add(Kind kind, String text) {
        // A multi-line result is a line each, so the window can colour and wrap them alike
        for (String s : text.split("\n", -1)) {
            Line line = new Line(kind, s);
            lines.add(line);
            for (Consumer<Line> l : listeners) l.accept(line);
        }
        while (lines.size() > MAX_LINES) lines.remove(0);
        revision++;
    }

    /** The scrollback, oldest first. */
    public List<Line> lines() {
        return view;
    }

    /** Goes up by one whenever the scrollback changes, for a window to know when to redraw it. */
    public int revision() {
        return revision;
    }

    public String format(Object value) {
        if (value instanceof Collection) {
            Collection<?> c = (Collection<?>) value;
            if (c.isEmpty()) return "(none)";
            return c.stream().map(this::format).collect(Collectors.joining("\n"));
        }
        if (value instanceof Object[]) return format(java.util.Arrays.asList((Object[]) value));
        if (value == null) return "null";
        return formatter.apply(value);
    }

    // --- History ---------------------------------------------------------------------

    public List<String> history() {
        return Collections.unmodifiableList(history);
    }

    /** Up: the line before the one shown; {@code current} is what is typed now, kept for Down to come back to. */
    public String historyUp(String current) {
        if (history.isEmpty()) return current;
        if (historyAt == history.size()) draft = current;
        historyAt = Math.max(0, historyAt - 1);
        return history.get(historyAt);
    }

    /** Down: the line after the one shown, and past the newest, what was being typed. */
    public String historyDown() {
        if (historyAt >= history.size()) return draft;
        historyAt++;
        return historyAt == history.size() ? draft : history.get(historyAt);
    }

    private void saveHistory() {
        if (historyFile == null) return;
        try {
            if (historyFile.getParent() != null) Files.createDirectories(historyFile.getParent());
            Files.write(historyFile, history, StandardCharsets.UTF_8);
        } catch (IOException e) {
            historyFile = null;
            error("Couldn't keep the history: " + e.getMessage());
        }
    }

    // --- Completion ------------------------------------------------------------------

    private static final Pattern PATH = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final List<String> KEYWORDS = List.of("def", "for", "while", "if", "else", "return", "true",
            "false", "null", "new", "in", "it");

    /**
     * Tab at {@code cursor} in {@code text}: the name being typed is finished as far as every
     * name that fits agrees. After a dot, the names are the properties and methods of what the
     * text before it is (only a plain {@code a.b.c} path is looked at, never a call).
     */
    public Completion complete(String text, int cursor) {
        cursor = Math.max(0, Math.min(cursor, text.length()));
        int start = cursor;
        while (start > 0 && isNamePart(text.charAt(start - 1))) start--;
        String word = text.substring(start, cursor);
        int dot = word.lastIndexOf('.');
        String owner = dot < 0 ? null : word.substring(0, dot);
        String prefix = dot < 0 ? word : word.substring(dot + 1);
        int replaceFrom = start + (dot < 0 ? 0 : dot + 1);

        Set<String> pool = new TreeSet<>();
        Set<String> callable = new TreeSet<>();
        if (owner == null) {
            pool.addAll(names());
            callable.addAll(commands.keySet());
            // Script files in the folder itself can be typed by name
            for (String sc : scripts()) {
                if (sc.indexOf('/') < 0 && PATH.matcher(sc).matches() && !pool.contains(sc)) {
                    pool.add(sc);
                    callable.add(sc);
                }
            }
            pool.addAll(KEYWORDS);
        } else if (PATH.matcher(owner).matches()) {
            Object target = resolvePath(owner);
            if (target != null) members(target, pool, callable);
        }
        List<String> fits = new ArrayList<>();
        for (String name : pool) {
            if (name.startsWith(prefix)) fits.add(name);
        }
        if (fits.isEmpty()) return new Completion(text, cursor, List.of());
        String common = fits.get(0);
        for (String f : fits) {
            int n = 0;
            while (n < common.length() && n < f.length() && common.charAt(n) == f.charAt(n)) n++;
            common = common.substring(0, n);
        }
        String insert = common;
        // One name left and nothing after the cursor: a command or method gets its bracket
        if (fits.size() == 1 && callable.contains(common) && (cursor == text.length() || text.charAt(cursor) != '(')) {
            insert += "(";
        }
        String out = text.substring(0, replaceFrom) + insert + text.substring(cursor);
        return new Completion(out, replaceFrom + insert.length(), fits);
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    /** The value of a plain {@code a.b.c} path from the binding, or null. Getters run; nothing else does. */
    private Object resolvePath(String path) {
        String[] parts = path.split("\\.");
        if (!binding.hasVariable(parts[0])) return null;
        Object value = binding.getVariable(parts[0]);
        try {
            for (int i = 1; i < parts.length && value != null; i++) {
                value = InvokerHelper.getProperty(value, parts[i]);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return value;
    }

    private static void members(Object target, Set<String> pool, Set<String> callable) {
        if (target instanceof Map) {
            for (Object k : ((Map<?, ?>) target).keySet()) pool.add(String.valueOf(k));
        }
        MetaClass meta = InvokerHelper.getMetaClass(target);
        for (MetaProperty p : meta.getProperties()) {
            if (Modifier.isPublic(p.getModifiers()) && !p.getName().equals("class")) pool.add(p.getName());
        }
        for (MetaMethod m : meta.getMethods()) {
            String n = m.getName();
            if (!m.isPublic() || n.contains("$") || n.equals("<init>")) continue;
            pool.add(n);
            callable.add(n);
        }
    }

    /** Every command and variable name. */
    public Set<String> names() {
        Set<String> names = new TreeSet<>(commands.keySet());
        names.addAll(live.keySet());
        for (Object k : binding.getVariables().keySet()) {
            String n = String.valueOf(k);
            if (!n.equals("out") && !n.startsWith("_")) names.add(n);
        }
        return names;
    }

    /** The name in {@code names} nearest {@code typo}, when one is near enough to be what was meant. */
    static String closest(String typo, Collection<String> names) {
        if (typo == null) return null;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        String lower = typo.toLowerCase(Locale.ROOT);
        for (String n : names) {
            int d = distance(lower, n.toLowerCase(Locale.ROOT));
            if (d < bestDistance) {
                bestDistance = d;
                best = n;
            }
        }
        return bestDistance <= Math.max(1, typo.length() / 3) ? best : null;
    }

    /** Edits from one name to the other, a swap of two letters side by side counting as one. */
    private static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    // --- Help and the built-in commands ----------------------------------------------

    public Map<String, CommandInfo> commands() {
        return Collections.unmodifiableMap(commands);
    }

    /** Every command by group, and the variables. */
    public String help() {
        Map<String, List<CommandInfo>> groups = new TreeMap<>();
        for (CommandInfo c : commands.values()) groups.computeIfAbsent(c.group, g -> new ArrayList<>()).add(c);
        StringBuilder sb = new StringBuilder();
        sb.append("Each line is Groovy. Commands act on the selection when given no target. Tab completes, Up/Down recall.");
        // General first, then the rest by name
        List<String> order = new ArrayList<>(groups.keySet());
        if (order.remove("General")) order.add(0, "General");
        for (String g : order) {
            sb.append("\n").append(g).append(":");
            for (CommandInfo c : groups.get(g)) sb.append("\n  ").append(pad(c.usage, 30)).append(c.help);
        }
        if (!variableHelp.isEmpty()) {
            sb.append("\nVariables:");
            for (Map.Entry<String, String> v : variableHelp.entrySet()) {
                sb.append("\n  ").append(pad(v.getKey(), 30)).append(v.getValue());
            }
        }
        return sb.toString();
    }

    /** One command's or variable's help. */
    public String help(String name) {
        CommandInfo c = commands.get(name);
        if (c != null) return c.usage + "  " + c.help;
        String v = variableHelp.get(name);
        if (v != null) return name + "  " + v;
        if (binding.hasVariable(name)) return name + " = " + format(binding.getVariable(name));
        String near = closest(name, names());
        return "Nothing called " + name + (near != null ? "; did you mean " + near + "?" : "");
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        if (sb.length() == s.length()) sb.append("  ");
        return sb.toString();
    }

    private void builtins() {
        define("help", "help([name])", "every command, or one command's help", args -> {
            info(args.length == 0 ? help() : help(nameOf(args[0])));
            return null;
        });
        define("clear", "clear()", "empties the scrollback", args -> {
            clear();
            return null;
        });
        define("history", "history([n])", "the last n lines typed (20)", args -> {
            int n = args.length > 0 ? ((Number) args[0]).intValue() : 20;
            List<String> h = history.subList(Math.max(0, history.size() - n), history.size());
            info(String.join("\n", h));
            return null;
        });
        define("vars", "vars()", "every variable set, with its value", args -> {
            StringBuilder sb = new StringBuilder();
            for (Object k : new TreeSet<>(binding.getVariables().keySet())) {
                Object v = binding.getVariable(String.valueOf(k));
                if (k.equals("out") || v instanceof CommandClosure) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(k).append(" = ").append(v == this ? "the console" : format(v));
            }
            for (Map.Entry<String, Supplier<?>> v : live.entrySet()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(v.getKey()).append(" = ").append(format(v.getValue().get()));
            }
            info(sb.length() == 0 ? "(none)" : sb.toString());
            return null;
        });
        define("run", "run(name, [args...])", "runs a script file from the script folder (.groovy optional);"
                + " a script in the folder itself also runs by typing its name", args -> {
            if (args.length == 0) throw new ConsoleException("run(name): which script? scripts() lists them");
            Object[] rest = new Object[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            return runFile(String.valueOf(args[0]), rest);
        });
        define("scripts", "scripts()", "the script files run() can run", args -> {
            List<String> s = scripts();
            info(scriptDir == null ? "No script folder" : s.isEmpty() ? "No scripts in " + scriptDir
                    : String.join("  ", s) + "    (in " + scriptDir + ")");
            return null;
        });
    }

    private static String nameOf(Object arg) {
        if (arg instanceof CommandClosure) return ((CommandClosure) arg).info.name;
        return String.valueOf(arg);
    }

    /** A command in the binding: calling it runs the command's body. */
    static final class CommandClosure extends Closure<Object> {
        final CommandInfo info;

        CommandClosure(ScriptConsole owner, CommandInfo info) {
            super(owner);
            this.info = info;
        }

        public Object doCall(Object... args) throws Exception {
            return info.body.run(args == null ? new Object[0] : args);
        }

        @Override
        public String toString() {
            return info.usage;
        }
    }

    /** Text a script prints, a scrollback line per line. */
    private final class LineWriter extends Writer {
        private final Kind kind;
        private final StringBuilder pending = new StringBuilder();

        LineWriter(Kind kind) {
            this.kind = kind;
        }

        @Override
        public void write(char[] buf, int off, int len) {
            for (int i = off; i < off + len; i++) {
                char c = buf[i];
                if (c == '\n') {
                    add(kind, pending.toString());
                    pending.setLength(0);
                } else if (c != '\r') {
                    pending.append(c);
                }
            }
        }

        @Override
        public void flush() {
            if (pending.length() == 0) return;
            add(kind, pending.toString());
            pending.setLength(0);
        }

        @Override
        public void close() {
            flush();
        }
    }
}
