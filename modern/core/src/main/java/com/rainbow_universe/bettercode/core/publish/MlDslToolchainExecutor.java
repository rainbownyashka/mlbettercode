package com.rainbow_universe.bettercode.core.publish;

import com.rainbow_universe.bettercode.core.settings.SettingsProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MlDslToolchainExecutor {
    public static final class ExecResult {
        public final boolean ok;
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        private ExecResult(boolean ok, int exitCode, String stdout, String stderr) {
            this.ok = ok;
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }

        public static ExecResult ok(int exitCode, String stdout, String stderr) {
            return new ExecResult(true, exitCode, stdout, stderr);
        }

        public static ExecResult fail(int exitCode, String stdout, String stderr) {
            return new ExecResult(false, exitCode, stdout, stderr);
        }
    }

    public interface Trace {
        void trace(String stage, String details);
    }

    private static final String DEV_COMPILER = "C:\\Users\\ASUS\\Documents\\mlctmodified\\mldsl_cli.py";

    private MlDslToolchainExecutor() {
    }

    public static String resolveCompilerPath(SettingsProvider settings) {
        boolean preferDev = settings == null || settings.getBoolean("mldsl.preferDevCompiler", true);
        String configured = settings == null ? "" : settings.getString("mldsl.compilerPath", "");
        if (preferDev) {
            if (isFile(DEV_COMPILER)) {
                return DEV_COMPILER;
            }
            String home = System.getProperty("user.home", "");
            String candidate = home + File.separator + "Documents" + File.separator + "mlctmodified" + File.separator + "mldsl_cli.py";
            if (isFile(candidate)) {
                return candidate;
            }
        }
        String custom = configured == null ? "" : configured.trim();
        if (!custom.isEmpty()) {
            if (isFile(custom) || isCommandAvailable(custom)) {
                return custom;
            }
        }
        if (isCommandAvailable("mldsl")) {
            return "mldsl";
        }
        return "";
    }

    public static ExecResult exportcodeToMldsl(String compilerPath, Path exportCodeFile, Path outMldsl, Trace trace) {
        if (isEmpty(compilerPath)) {
            return ExecResult.fail(-1, "", "compiler path is empty");
        }
        Path api = resolveApiAliasesPath(compilerPath, trace);
        if (api == null || !Files.isRegularFile(api)) {
            return ExecResult.fail(2, "", "api_aliases.json not found");
        }
        return runMlDslCommand(compilerPath, 60_000L, trace,
            "exportcode",
            exportCodeFile.toAbsolutePath().toString(),
            "--api",
            api.toAbsolutePath().toString(),
            "-o",
            outMldsl.toAbsolutePath().toString());
    }

    public static ExecResult compileToPlan(String compilerPath, Path moduleMldsl, Path outPlan, Trace trace) {
        if (isEmpty(compilerPath)) {
            return ExecResult.fail(-1, "", "compiler path is empty");
        }
        return runMlDslCommand(compilerPath, 60_000L, trace,
            "compile",
            moduleMldsl.toAbsolutePath().toString(),
            "--plan",
            outPlan.toAbsolutePath().toString());
    }

    private static Path resolveApiAliasesPath(String compilerPath, Trace trace) {
        ExecResult paths = runMlDslCommand(compilerPath, 10_000L, trace, "paths");
        if (paths.ok) {
            String[] lines = normalize(paths.stdout).split("\n");
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String t = line.trim();
                if (!t.startsWith("api_aliases=")) {
                    continue;
                }
                String p = t.substring("api_aliases=".length()).trim();
                if (!p.isEmpty()) {
                    Path out = new File(p).toPath();
                    if (Files.isRegularFile(out)) {
                        return out;
                    }
                }
            }
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.trim().isEmpty()) {
            return null;
        }
        String[] candidates = new String[] {
            local + "\\Programs\\MLDSL\\app\\out\\api_aliases.json",
            local + "\\Programs\\MLDSL\\seed_out\\api_aliases.json",
            local + "\\MLDSL\\app\\out\\api_aliases.json",
            local + "\\MLDSL\\seed_out\\api_aliases.json"
        };
        for (String c : candidates) {
            Path f = new File(c).toPath();
            if (Files.isRegularFile(f)) {
                return f;
            }
        }
        return null;
    }

    private static ExecResult runMlDslCommand(String compilerPath, long timeoutMs, Trace trace, String... args) {
        List<String[]> attempts = new ArrayList<String[]>();
        String cp = compilerPath == null ? "" : compilerPath.trim();
        if (cp.isEmpty()) {
            return ExecResult.fail(-1, "", "compiler path is empty");
        }
        boolean isPy = cp.toLowerCase(Locale.ROOT).endsWith(".py");
        if (isPy) {
            attempts.add(concat(new String[] {"py", "-3", cp}, args));
            attempts.add(concat(new String[] {"python", cp}, args));
            attempts.add(concat(new String[] {cp}, args));
        } else {
            attempts.add(concat(new String[] {cp}, args));
        }

        ExecResult last = ExecResult.fail(-1, "", "no attempts");
        for (String[] cmd : attempts) {
            if (trace != null) {
                trace.trace("publish.toolchain.exec", "cmd=" + safeCommand(cmd));
            }
            ExecResult r = runProcess(cmd, timeoutMs);
            if (r.ok) {
                return r;
            }
            last = r;
            String msg = normalize(r.stderr).toLowerCase(Locale.ROOT);
            if (msg.contains("cannot find the file") || msg.contains("is not recognized")
                || msg.contains("filenotfoundexception") || msg.contains("no such file")) {
                continue;
            }
            break;
        }
        return last;
    }

    private static ExecResult runProcess(String[] cmd, long timeoutMs) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Map<String, String> env = pb.environment();
            env.put("PYTHONUTF8", "1");
            env.put("PYTHONIOENCODING", "UTF-8");
            env.put("LC_ALL", "C.UTF-8");
            env.put("LANG", "C.UTF-8");
            pb.redirectErrorStream(false);
            Process p = pb.start();
            boolean done = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                return ExecResult.fail(-1, "", "timeout");
            }
            int code = p.exitValue();
            String out = readStream(p.getInputStream());
            String err = readStream(p.getErrorStream());
            if (code == 0) {
                return ExecResult.ok(code, out, err);
            }
            return ExecResult.fail(code, out, err);
        } catch (Exception e) {
            return ExecResult.fail(-1, "", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static String readStream(InputStream in) {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            while (true) {
                int n = in.read(buf);
                if (n < 0) {
                    break;
                }
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        } finally {
            try {
                in.close();
            } catch (Exception ignore) {
            }
            try {
                out.close();
            } catch (Exception ignore) {
            }
        }
    }

    private static boolean isFile(String p) {
        if (p == null || p.trim().isEmpty()) {
            return false;
        }
        return new File(p.trim()).isFile();
    }

    private static boolean isCommandAvailable(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "where", cmd);
            Process p = pb.start();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String[] concat(String[] prefix, String[] args) {
        int n1 = prefix == null ? 0 : prefix.length;
        int n2 = args == null ? 0 : args.length;
        String[] out = new String[n1 + n2];
        int k = 0;
        if (prefix != null) {
            for (String s : prefix) {
                out[k++] = s;
            }
        }
        if (args != null) {
            for (String s : args) {
                out[k++] = s;
            }
        }
        return out;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeCommand(String[] cmd) {
        if (cmd == null || cmd.length == 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(cmd[i] == null ? "" : cmd[i]);
        }
        return sb.toString();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").replace('\r', '\n');
    }
}
