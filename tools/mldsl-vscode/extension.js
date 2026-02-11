const vscode = require("vscode");
const fs = require("fs");
const path = require("path");
const os = require("os");
const cp = require("child_process");

const output = vscode.window.createOutputChannel("MLDSL Helper");
let seq = 0;

function readJsonSafe(p) {
  try {
    if (!p) return null;
    if (!fs.existsSync(p)) return null;
    return JSON.parse(fs.readFileSync(p, "utf8"));
  } catch {
    return null;
  }
}

function firstWorkspaceRoot() {
  const w = vscode.workspace.workspaceFolders;
  if (!w || w.length === 0) return null;
  return w[0].uri.fsPath;
}

function autoDetectApiAliasesPath(rawCfg) {
  const configured = String(rawCfg.apiAliasesPath || "").trim();
  if (configured) return configured;

  const candidates = [];

  const ws = firstWorkspaceRoot();
  if (ws) {
    candidates.push(path.join(ws, "out", "api_aliases.json"));
    candidates.push(path.join(ws, "tools", "out", "api_aliases.json"));
  }

  const localAppData = process.env.LOCALAPPDATA || "";
  if (localAppData) {
    candidates.push(path.join(localAppData, "MLDSL", "out", "api_aliases.json"));
  }

  const userProfile = process.env.USERPROFILE || "";
  if (userProfile) {
    candidates.push(path.join(userProfile, ".mldsl", "out", "api_aliases.json"));
  }

  for (const p of candidates) {
    if (fs.existsSync(p)) return p;
  }
  return "";
}

function autoDetectDocsRoot(rawCfg, apiAliasesPath) {
  const configured = String(rawCfg.docsRoot || "").trim();
  if (configured) return configured;
  if (!apiAliasesPath) return "";
  const dir = path.dirname(String(apiAliasesPath));
  const p = path.join(dir, "docs");
  return p;
}

function getConfig() {
  const cfg = vscode.workspace.getConfiguration("mldsl");
  const raw = {
    apiAliasesPath: cfg.get("apiAliasesPath"),
    docsRoot: cfg.get("docsRoot"),
    pythonPath: cfg.get("pythonPath"),
    compilerPath: cfg.get("compilerPath"),
    planPath: cfg.get("planPath"),
  };
  const apiAliasesPath = autoDetectApiAliasesPath(raw);
  const docsRoot = autoDetectDocsRoot(raw, apiAliasesPath);
  return { ...raw, apiAliasesPath, docsRoot };
}

function stripMcColors(s) {
  return String(s || "")
    .replace(/\u00a7./g, "")
    .replace(/[\x00-\x1f]/g, "");
}

// Best-effort transliteration helpers used for keyword-arg aliases:
// - compiler uses translit identifiers like `rezhim_igry`
// - users often type Cyrillic: `режим_игры`
function ruToTranslitIdent(s) {
  const m = {
    а: "a",
    б: "b",
    в: "v",
    г: "g",
    д: "d",
    е: "e",
    ё: "yo",
    ж: "zh",
    з: "z",
    и: "i",
    й: "y",
    к: "k",
    л: "l",
    м: "m",
    н: "n",
    о: "o",
    п: "p",
    р: "r",
    с: "s",
    т: "t",
    у: "u",
    ф: "f",
    х: "h",
    ц: "ts",
    ч: "ch",
    ш: "sh",
    щ: "sch",
    ы: "y",
    э: "e",
    ю: "yu",
    я: "ya",
    ь: "",
    ъ: "",
  };
  let out = "";
  for (const ch of String(s || "")) {
    const lo = ch.toLowerCase();
    if (Object.prototype.hasOwnProperty.call(m, lo)) {
      const tr = m[lo];
      out += ch === lo ? tr : tr.toUpperCase();
    } else out += ch;
  }
  return out;
}

function translitToRuIdent(s) {
  const src = String(s || "");
  const lower = src.toLowerCase();
  const pairs = [
    ["sch", "щ"],
    ["sh", "ш"],
    ["ch", "ч"],
    ["zh", "ж"],
    ["yo", "ё"],
    ["yu", "ю"],
    ["ya", "я"],
    ["ts", "ц"],
  ];
  const single = {
    a: "а",
    b: "б",
    v: "в",
    g: "г",
    d: "д",
    e: "е",
    z: "з",
    i: "и",
    y: "й",
    k: "к",
    l: "л",
    m: "м",
    n: "н",
    o: "о",
    p: "п",
    r: "р",
    s: "с",
    t: "т",
    u: "у",
    f: "ф",
    h: "х",
    _: "_",
  };
  let out = "";
  let i = 0;
  while (i < src.length) {
    const rest = lower.slice(i);
    let matched = false;
    for (const [latin, ru] of pairs) {
      if (rest.startsWith(latin)) {
        const orig = src.slice(i, i + latin.length);
        out += orig === orig.toUpperCase() ? ru.toUpperCase() : ru;
        i += latin.length;
        matched = true;
        break;
      }
    }
    if (matched) continue;
    const ch = src[i];
    const lo = lower[i];
    if (Object.prototype.hasOwnProperty.call(single, lo)) {
      const ru = single[lo];
      out += ch === ch.toUpperCase() ? ru.toUpperCase() : ru;
      i += 1;
      continue;
    }
    out += ch;
    i += 1;
  }
  return out;
}

function normKey(s) {
  return stripMcColors(s)
    .toLowerCase()
    .replace(/[\s_\\-]+/g, "")
    .trim();
}

function isSelectSpec(spec) {
  if (!spec) return false;
  const s1 = normKey(spec.sign1 || "");
  // Some servers use "обьект" typo; accept both.
  return s1 === normKey("Выбрать объект") || s1 === normKey("Выбрать обьект");
}

function selectDomain(spec) {
  const blob = `${spec.sign2 || ""} ${spec.gui || ""} ${spec.menu || ""}`.toLowerCase();
  if (blob.includes("моб") || blob.includes("сущност")) return "entity";
  if (blob.includes("игрок")) return "player";
  return "player";
}

function selectHintsFromRawModule(rawModule) {
  const raw = String(rawModule || "").toLowerCase();
  const parts = raw.split(".").filter(Boolean);
  const has = (needle) => parts.some((p) => p.replace(/[\s_\\-]/g, "").includes(needle));
  return {
    wantPlayer: has("player") || has("игрок"),
    wantEntity: has("entity") || has("mob") || has("моб") || has("сущность") || has("существо"),
  };
}

function loadApi() {
  const { apiAliasesPath } = getConfig();
  return readJsonSafe(apiAliasesPath) || {};
}

function loadEventsCatalog() {
  const { apiAliasesPath } = getConfig();
  if (!apiAliasesPath) return { all: [], byPrefix: {} };
  const dir = path.dirname(String(apiAliasesPath));
  const p = path.join(dir, "actions_catalog.json");
  const catalog = readJsonSafe(p);
  if (!Array.isArray(catalog)) return { all: [], byPrefix: {} };

  function parseItemDisplayName(raw) {
    if (!raw) return "";
    let s = stripMcColors(raw);
    if (s.includes("]")) s = s.split("]", 2)[1];
    s = String(s || "").trim();
    if (s.includes("|")) s = s.split("|", 2)[0].trim();
    return s;
  }

  const all = [];
  for (const rec of catalog) {
    const signs = (rec && rec.signs) || [];
    const sign1 = stripMcColors(signs[0] || "").trim();
    const sign2 = stripMcColors(signs[1] || "").trim();
    const menu = parseItemDisplayName((rec && (rec.subitem || rec.category)) || "") || sign2;
    if (!menu) continue;
    let kind = null;
    if (sign1 === "Событие игрока") kind = "player";
    else if (sign1 === "Событие мира") kind = "world";
    if (!kind) continue;
    all.push({ kind, name: menu, sign2 });
  }

  // simple prefix index (normalized by lower + remove spaces/_)
  function norm(x) {
    return String(x || "").toLowerCase().replace(/[\s_\\-]/g, "");
  }
  const byPrefix = {};
  for (const e of all) {
    // index by GUI-item name and by sign2 (so typing "Правый клик" still suggests the event)
    byPrefix[norm(e.name)] = e;
    if (e.sign2) byPrefix[norm(e.sign2)] = e;
  }
  return { all, byPrefix };
}

function loadGameValuesCatalog() {
  const { apiAliasesPath } = getConfig();
  if (!apiAliasesPath) return { byLocName: {}, byKey: {}, path: null, exists: false };
  const dir = path.dirname(String(apiAliasesPath));
  let p = path.join(dir, "gamevalues.json");
  let gv = readJsonSafe(p);
  if (!gv || typeof gv !== "object") {
    // fallback: sometimes apiAliasesPath points elsewhere; try docsRoot sibling
    const { docsRoot } = getConfig();
    if (docsRoot) {
      const p2 = path.join(path.dirname(String(docsRoot)), "gamevalues.json");
      const gv2 = readJsonSafe(p2);
      if (gv2 && typeof gv2 === "object") {
        p = p2;
        gv = gv2;
      }
    }
  }
  if (!gv || typeof gv !== "object") return { byLocName: {}, byKey: {}, path: p, exists: fs.existsSync(p) };
  const byLocName = (gv && gv.byLocName) || {};
  const byKey = (gv && gv.byKey) || {};
  return { byLocName, byKey, path: p, exists: fs.existsSync(p) };
}

function buildLookup(api) {
  const lookup = {};
  for (const [moduleName, funcs] of Object.entries(api)) {
    lookup[moduleName] = { byName: {}, canonical: funcs };
    for (const [funcName, spec] of Object.entries(funcs)) {
      lookup[moduleName].byName[funcName] = { funcName, spec };
      const aliases = Array.isArray(spec.aliases) ? spec.aliases : [];
      for (const a of aliases) {
        if (!a) continue;
        lookup[moduleName].byName[a] = { funcName, spec };
      }
    }
  }
  // hardcoded module aliases (draft)
  const moduleAliases = {
    "игрок": "player",
    "player": "player",
    "если_игрок": "if_player",
    "еслиигрок": "if_player",
    "if_player": "if_player",
    "если_игра": "if_game",
    "еслиигра": "if_game",
    "if_game": "if_game",
    "игра": "game",
    "game": "game",
    "перем": "var",
    "переменная": "var",
    "var": "var",
    "массив": "array",
    "array": "array",
    "если_значение": "if_value",
    "еслизначение": "if_value",
    "if_value": "if_value",
    "misc": "misc",
    "select": "misc",
    "vyborka": "misc",
    "выборка": "misc",
  };
  for (const [alias, target] of Object.entries(moduleAliases)) {
    if (lookup[target] && !lookup[alias]) lookup[alias] = lookup[target];
  }
  return lookup;
}

function normalizeModuleName(name) {
  const s = String(name || "");
  if (s === "SelectObject.player.IfPlayer") return "if_player";
  if (s === "IfGame") return "if_game";
  const low = s.toLowerCase();
  // Selection sugar: allow chains like select.player.ifplayer.<leaf>
  if (low.startsWith("select.") || low.startsWith("vyborka.") || low.startsWith("выборка.")) return "select";
  return s;
}

function findEventCallContext(lineText, positionChar) {
  const left = lineText.slice(0, positionChar);
  if ((left.match(/\"/g) || []).length % 2 === 1) return null;
  const m = left.match(/\b(?:event|событие)\s*\(\s*([^\)]*)$/i);
  if (!m) return null;
  const inside = m[1] || "";
  if (inside.includes(")")) return null;
  return { inside };
}

function findEventDotContext(lineText, positionChar) {
  const left = lineText.slice(0, positionChar);
  const m = left.match(/\b(?:event|событие)\.([\w\u0400-\u04FF]*)$/i);
  if (!m) return null;
  return { prefix: m[1] || "" };
}

function findModuleAndPrefix(lineText, positionChar) {
  const left = lineText.slice(0, positionChar);
  
  // Поддержка нового синтаксиса: if_player.function(), if_game.function()
  // Поддержка старого синтаксиса: SelectObject.player.IfPlayer.Function
  const re = /([a-zA-Z_\u0400-\u04FF][\w\u0400-\u04FF]*(?:\.[a-zA-Z_\u0400-\u04FF][\w\u0400-\u04FF]*)*)\.([\w\u0400-\u04FF]*)/g;
  let m;
  let last = null;
  while ((m = re.exec(left))) {
    last = { module: m[1], prefix: m[2] || "", end: m.index + m[0].length };
  }
  if (!last) return null;
  // only trigger when cursor is right after the match
  if (last.end !== left.length) return null;
  
  // Нормализация модуля для старого синтаксиса + сохранение rawModule (нужно для select.* цепочек)
  return { module: normalizeModuleName(last.module), rawModule: last.module, prefix: last.prefix };
}

function findSelectDotContext(lineText, positionChar) {
  const left = lineText.slice(0, positionChar);
  // If we are inside quotes, don't trigger.
  if ((left.match(/\"/g) || []).length % 2 === 1) return null;
  const m = left.match(/\b(?:select|vyborka|выборка)\.([\w\u0400-\u04FF]*)$/i);
  if (!m) return null;
  return { prefix: m[1] || "", rawModule: "select" };
}

function findGameValueDotContext(lineText, positionChar) {
  const left = lineText.slice(0, positionChar);
  if ((left.match(/\"/g) || []).length % 2 === 1) return null;
  // Match: gamevalue.<...> (allow multi-dot like gamevalue.category.value)
  const m = left.match(/\b(?:gamevalue|gameval|apple|яблоко|игровое_значение|игровоезначение)\.([\w\u0400-\u04FF\.]*)$/i);
  if (!m) return null;
  const raw = String(m[1] || "");
  const parts = raw.split(".").filter(Boolean);
  const prefix = parts.length ? parts[parts.length - 1] : "";
  return { prefix };
}

function findQualifiedAtPosition(document, position) {
  const line = document.lineAt(position.line).text;
  const re = /([a-zA-Z_\u0400-\u04FF][\w\u0400-\u04FF]*(?:\.[a-zA-Z_\u0400-\u04FF][\w\u0400-\u04FF]*)*)\.([\w\u0400-\u04FF]+)/g;
  let m;
  while ((m = re.exec(line))) {
    const start = m.index;
    const end = m.index + m[0].length;
    if (position.character >= start && position.character <= end) {
      const range = new vscode.Range(
        new vscode.Position(position.line, start),
        new vscode.Position(position.line, end)
      );
      
      // Нормализация модуля для старого синтаксиса
      return { module: normalizeModuleName(m[1]), func: m[2], range, text: m[0] };
    }
  }
  return null;
}

function findCallContext(lineText, positionChar) {
  const left = lineText.slice(0, positionChar);
  if ((left.match(/\"/g) || []).length % 2 === 1) return null;
  const re = /([a-zA-Z_\u0400-\u04FF][\w\u0400-\u04FF]*(?:\.[a-zA-Z_\u0400-\u04FF][\w\u0400-\u04FF]*)*)\.([\w\u0400-\u04FF]+)\s*\(/g;
  let m;
  let last = null;
  while ((m = re.exec(left))) {
    last = { module: m[1], func: m[2], openParen: m.index + m[0].length - 1 };
  }
  if (!last) return null;
  const inside = left.slice(last.openParen + 1);
  if (inside.includes(")")) return null;
  return { module: normalizeModuleName(last.module), func: last.func, inside };
}

function specToMarkdown(spec) {
  function escapeHtml(s) {
    return (s || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;");
  }

  function mcToHtml(s) {
    if (!s) return "";
    const colors = {
      "0": "#000000",
      "1": "#0000AA",
      "2": "#00AA00",
      "3": "#00AAAA",
      "4": "#AA0000",
      "5": "#AA00AA",
      "6": "#FFAA00",
      "7": "#AAAAAA",
      "8": "#555555",
      "9": "#5555FF",
      "a": "#55FF55",
      "b": "#55FFFF",
      "c": "#FF5555",
      "d": "#FF55FF",
      "e": "#FFFF55",
      "f": "#FFFFFF",
    };
    let color = null;
    let bold = false;
    let italic = false;
    let underline = false;
    let strike = false;

    function style() {
      const parts = [];
      if (color) parts.push(`color:${color}`);
      if (bold) parts.push("font-weight:700");
      if (italic) parts.push("font-style:italic");
      if (underline || strike) {
        const dec = [];
        if (underline) dec.push("underline");
        if (strike) dec.push("line-through");
        parts.push(`text-decoration:${dec.join(" ")}`);
      }
      return parts.join(";");
    }

    let out = "";
    let buf = "";
    function flush() {
      if (!buf) return;
      const st = style();
      const content = escapeHtml(buf).replace(/\n/g, "<br/>");
      out += st ? `<span style="${st}">${content}</span>` : content;
      buf = "";
    }

    function readFormatCode(str, idx) {
      const ch = str[idx];
      // §a, §b...
      if (ch === "§") return { code: str[idx + 1], skip: 1 };
      // Sometimes seen as "Â§a" after double-decoding UTF-8
      if (ch === "Â" && str[idx + 1] === "§") return { code: str[idx + 2], skip: 2 };
      return null;
    }

    for (let i = 0; i < s.length; i++) {
      const fmt = readFormatCode(s, i);
      if (fmt && fmt.code) {
        flush();
        const code = String(fmt.code).toLowerCase();
        i += fmt.skip;
        if (colors[code]) {
          color = colors[code];
        } else if (code === "l") {
          bold = true;
        } else if (code === "o") {
          italic = true;
        } else if (code === "n") {
          underline = true;
        } else if (code === "m") {
          strike = true;
        } else if (code === "r") {
          color = null;
          bold = false;
          italic = false;
          underline = false;
          strike = false;
        }
        continue;
      }
      buf += s[i];
    }
    flush();
    return out;
  }

  const lines = [];
  if (spec.menu) lines.push(`**item:** ${spec.menu}`);
  if (spec.sign1) lines.push(`**sign1:** ${spec.sign1}`);
  if (spec.sign2) lines.push(`**sign2:** ${spec.sign2}`);
  if (spec.gui) lines.push(`**gui:** ${spec.gui}`);
  if (spec.aliases && spec.aliases.length) lines.push(`**aliases:** ${spec.aliases.join(", ")}`);
  if (spec.description) {
    lines.push("");
    lines.push("**description:**");
    lines.push("```");
    lines.push(spec.description);
    lines.push("```");
  }
  if (spec.descriptionRaw) {
    const html = mcToHtml(spec.descriptionRaw);
    if (html) {
      lines.push("");
      lines.push("**description (mc colors):**");
      lines.push(`<div style="font-family: var(--vscode-editor-font-family); font-size: 12px; line-height: 1.35;">${html}</div>`);
    }
  }
  if (spec.params && spec.params.length) {
    lines.push("");
    lines.push("**params:**");
    for (const p of spec.params) {
      const ru = translitToRuIdent(p.name);
      const extra = ru && ru !== p.name ? ` (RU: \`${ru}\`)` : "";
      lines.push(`- \`${p.name}\`${extra} (${p.mode}) slot ${p.slot}`);
    }
  }
  if (spec.enums && spec.enums.length) {
    lines.push("");
    lines.push("**enums:**");
    for (const e of spec.enums) {
      const ru = translitToRuIdent(e.name);
      const extra = ru && ru !== e.name ? ` (RU: \`${ru}\`)` : "";
      lines.push(`- \`${e.name}\`${extra} slot ${e.slot}`);
      const opts = e.options || {};
      const keys = Object.keys(opts);
      if (keys.length) {
        const max = 12;
        for (const k of keys.slice(0, max)) {
          const v = opts[k];
          const suffix = v === 0 ? " (default)" : "";
          lines.push(`  - \`${k}\` → clicks=${v}${suffix}`);
        }
        if (keys.length > max) {
          lines.push(`  - … (+${keys.length - max})`);
        }
      }
    }
  }
  const md = new vscode.MarkdownString(lines.join("\n"));
  md.supportHtml = true;
  md.isTrusted = true;
  return md;
}

function activate(context) {
  let api = {};
  let lookup = {};
  let events = { all: [], byPrefix: {} };
  let gamevalues = { byLocName: {}, byKey: {} };
  let statusItem = null;
  let didWarnMissingApi = false;

  function reloadApi(reason) {
    const { apiAliasesPath, docsRoot } = getConfig();
    api = loadApi();
    lookup = buildLookup(api);
    events = loadEventsCatalog();
    gamevalues = loadGameValuesCatalog();
    output.appendLine(`[reload] ${reason || "manual"}`);
    output.appendLine(`  apiAliasesPath=${apiAliasesPath}`);
    output.appendLine(`  docsRoot=${docsRoot}`);
    output.appendLine(`  apiLoadedModules=${Object.keys(api).length}`);
    output.appendLine(`  eventsLoaded=${events.all.length}`);
    output.appendLine(`  gamevaluesLoaded=${Object.keys(gamevalues.byLocName || {}).length}`);
    output.appendLine(`  gamevaluesPath=${gamevalues.path || ""}`);
    output.appendLine(`  gamevaluesExists=${gamevalues.exists ? "yes" : "no"}`);
    if (apiAliasesPath && !fs.existsSync(apiAliasesPath)) {
      output.appendLine(`  WARN: apiAliasesPath does not exist`);
    }
    if (docsRoot && !fs.existsSync(docsRoot)) {
      output.appendLine(`  WARN: docsRoot does not exist`);
    }

    const apiMissing = !apiAliasesPath || !fs.existsSync(apiAliasesPath);
    if (apiMissing && !didWarnMissingApi) {
      didWarnMissingApi = true;
      vscode.window
        .showWarningMessage(
          "MLDSL Helper: не найден `api_aliases.json`. Сгенерируй `out/` (python tools/build_all.py) или укажи пути в настройках расширения.",
          "Открыть настройки",
          "Открыть лог"
        )
        .then((choice) => {
          if (choice === "Открыть настройки") {
            vscode.commands.executeCommand("workbench.action.openSettings", "mldsl.");
          } else if (choice === "Открыть лог") {
            output.show(true);
          }
        });
    }
  }

  reloadApi("activate");
  output.show(true);

  context.subscriptions.push(vscode.commands.registerCommand("mldsl.reloadApi", () => reloadApi("command")));

  function findCompilerPathLegacyPy() {
    const { compilerPath } = getConfig();
    if (compilerPath && fs.existsSync(compilerPath) && String(compilerPath).toLowerCase().endsWith(".py"))
      return compilerPath;

    const folders = vscode.workspace.workspaceFolders || [];
    if (!folders.length) return null;
    const root = folders[0].uri.fsPath;
    const auto = path.join(root, "tools", "mldsl_compile.py");
    if (fs.existsSync(auto)) return auto;
    return null;
  }

  function findCliPath() {
    const { compilerPath } = getConfig();
    if (compilerPath && fs.existsSync(compilerPath) && !String(compilerPath).toLowerCase().endsWith(".py"))
      return compilerPath;

    const candidates = [];
    const pf = process.env.ProgramFiles;
    const pf86 = process.env["ProgramFiles(x86)"];
    if (pf) candidates.push(path.join(pf, "MLDSL", "mldsl.exe"));
    if (pf86) candidates.push(path.join(pf86, "MLDSL", "mldsl.exe"));
    const lap = process.env.LOCALAPPDATA || process.env.localappdata;
    if (lap) {
      candidates.push(path.join(lap, "MLDSL", "mldsl.exe"));
      candidates.push(path.join(lap, "Programs", "MLDSL", "mldsl.exe"));
    }
    for (const p of candidates) {
      if (p && fs.existsSync(p)) return p;
    }

    // try PATH lookup (Windows)
    const pathVar = process.env.PATH || process.env.Path || "";
    const parts = String(pathVar)
      .split(path.delimiter)
      .map((x) => x.trim())
      .filter(Boolean);
    for (const dir of parts) {
      const exe = path.join(dir, "mldsl.exe");
      if (fs.existsSync(exe)) return exe;
    }

    return null;
  }

  function findCompiler() {
    const cli = findCliPath();
    if (cli) return { kind: "cli", cli };
    const py = findCompilerPathLegacyPy();
    if (py) return { kind: "py", py };
    return null;
  }

  function resolvePlanPath() {
    const { planPath } = getConfig();
    if (planPath && String(planPath).trim()) return String(planPath).trim();
    const appdata = process.env.APPDATA || process.env.appdata;
    if (appdata) return path.join(appdata, ".minecraft", "plan.json");
    return path.join(process.cwd(), "plan.json");
  }

  async function compileAndCopy() {
    const id = ++seq;
    const ed = vscode.window.activeTextEditor;
    if (!ed || !ed.document) {
      vscode.window.showWarningMessage("MLDSL: No active editor");
      return;
    }
    if (ed.document.languageId !== "mldsl") {
      vscode.window.showWarningMessage("MLDSL: Not an .mldsl file");
      return;
    }

    await ed.document.save();

    const compiler = findCompiler();
    const { pythonPath } = getConfig();
    if (!compiler) {
      vscode.window.showErrorMessage(
        "MLDSL: compiler not found. Install MLDSL (mldsl.exe) or set mldsl.compilerPath."
      );
      output.appendLine(`[compile#${id}] compiler missing (auto-detect failed; config.compilerPath not found)`);
      return;
    }

    const filePath = ed.document.uri.fsPath;
    if (compiler.kind === "cli") {
      output.appendLine(`[compile#${id}] ${compiler.cli} compile ${filePath}`);
    } else {
      output.appendLine(`[compile#${id}] ${pythonPath} ${compiler.py} ${filePath}`);
    }

    const env = Object.assign({}, process.env, { PYTHONIOENCODING: "utf-8" });

    const execFile = compiler.kind === "cli" ? compiler.cli : pythonPath || "python";
    const args = compiler.kind === "cli" ? ["compile", filePath] : [compiler.py, filePath];

    cp.execFile(execFile, args, { env }, async (err, stdout, stderr) => {
      if (stderr && String(stderr).trim()) {
        output.appendLine(`[compile#${id}] stderr: ${String(stderr).trim()}`);
      }
      if (err) {
        output.appendLine(`[compile#${id}] ERROR: ${err.message || String(err)}`);
        vscode.window.showErrorMessage("MLDSL: Compile failed (see Output → MLDSL Helper)");
        return;
      }
      const text = String(stdout || "").trim();
      if (!text) {
        vscode.window.showWarningMessage("MLDSL: Compiler produced empty output");
        return;
      }
      await vscode.env.clipboard.writeText(text);
      const lines = text.split(/\r?\n/).filter((x) => x.trim()).length;
      vscode.window.showInformationMessage(`MLDSL: Copied ${lines} command(s) to clipboard`);
      output.appendLine(`[compile#${id}] ok lines=${lines}`);
    });
  }

  async function compilePlan() {
    const id = ++seq;
    const ed = vscode.window.activeTextEditor;
    if (!ed || !ed.document) {
      vscode.window.showWarningMessage("MLDSL: No active editor");
      return;
    }
    if (ed.document.languageId !== "mldsl") {
      vscode.window.showWarningMessage("MLDSL: Not an .mldsl file");
      return;
    }

    await ed.document.save();

    const compiler = findCompiler();
    const { pythonPath } = getConfig();
    if (!compiler) {
      vscode.window.showErrorMessage(
        "MLDSL: compiler not found. Install MLDSL (mldsl.exe) or set mldsl.compilerPath."
      );
      output.appendLine(`[plan#${id}] compiler missing (config.compilerPath not found; auto-detect failed)`);
      return;
    }

    const filePath = ed.document.uri.fsPath;
    const outPlan = resolvePlanPath();
    if (compiler.kind === "cli") {
      output.appendLine(`[plan#${id}] ${compiler.cli} compile ${filePath} --plan ${outPlan}`);
    } else {
      output.appendLine(`[plan#${id}] ${pythonPath} ${compiler.py} --plan ${outPlan} ${filePath}`);
    }

    const env = Object.assign({}, process.env, { PYTHONIOENCODING: "utf-8" });

    const execFile = compiler.kind === "cli" ? compiler.cli : pythonPath || "python";
    const args = compiler.kind === "cli" ? ["compile", filePath, "--plan", outPlan] : [compiler.py, "--plan", outPlan, filePath];

    cp.execFile(execFile, args, { env }, async (err, stdout, stderr) => {
      if (stderr && String(stderr).trim()) {
        output.appendLine(`[plan#${id}] stderr: ${String(stderr).trim()}`);
      }
      if (err) {
        output.appendLine(`[plan#${id}] ERROR: ${err.message || String(err)}`);
        vscode.window.showErrorMessage("MLDSL: Compile plan failed (see Output → MLDSL Helper)");
        return;
      }
      await vscode.env.clipboard.writeText(`/mldsl run \"${outPlan}\"`);
      vscode.window.showInformationMessage(`MLDSL: Wrote plan.json and copied /mldsl run to clipboard`);
      output.appendLine(`[plan#${id}] ok wrote=${outPlan}`);
    });
  }

  function parseImports(text) {
    const out = [];
    const lines = String(text || "").split(/\r?\n/);
    for (const raw of lines) {
      const line = String(raw || "").trim();
      if (!line) continue;
      if (line.startsWith("#") || line.startsWith("//")) continue;
      const m = line.match(/^(import|use|использовать)\s+(.+?)\s*$/i);
      if (!m) continue;
      let spec = String(m[2] || "").trim();
      spec = spec.replace(/^["']|["']$/g, "").trim();
      if (!spec) continue;
      out.push(spec);
    }
    return out;
  }

  function resolveImportPath(fromFile, spec) {
    const baseDir = path.dirname(fromFile);
    let p = String(spec || "").trim();
    if (!p) return null;
    if (!p.toLowerCase().endsWith(".mldsl")) p = `${p}.mldsl`;
    return path.resolve(baseDir, p);
  }

  function collectDepsRecursive(entryFile) {
    const seen = new Set();
    const order = [];

    function visit(file) {
      const abs = path.resolve(file);
      if (seen.has(abs)) return;
      seen.add(abs);
      if (!fs.existsSync(abs)) {
        throw new Error(`Import not found: ${abs}`);
      }
      order.push(abs);
      const txt = fs.readFileSync(abs, "utf8");
      for (const spec of parseImports(txt)) {
        const dep = resolveImportPath(abs, spec);
        if (!dep) continue;
        visit(dep);
      }
    }

    visit(entryFile);
    return order;
  }

  async function publishModule() {
    const id = ++seq;
    const ed = vscode.window.activeTextEditor;
    if (!ed || !ed.document) {
      vscode.window.showWarningMessage("MLDSL: No active editor");
      return;
    }
    if (ed.document.languageId !== "mldsl") {
      vscode.window.showWarningMessage("MLDSL: Not an .mldsl file");
      return;
    }
    await ed.document.save();

    const compiler = findCompiler();
    const { pythonPath } = getConfig();
    if (!compiler) {
      vscode.window.showErrorMessage("MLDSL: compiler not found. Install MLDSL (mldsl.exe) or set mldsl.compilerPath.");
      output.appendLine(`[publish#${id}] compiler missing (auto-detect failed; config.compilerPath not found)`);
      return;
    }

    const entryFile = ed.document.uri.fsPath;
    let deps;
    try {
      deps = collectDepsRecursive(entryFile);
    } catch (e) {
      output.appendLine(`[publish#${id}] ERROR: ${e.message || String(e)}`);
      vscode.window.showErrorMessage(`MLDSL: ${e.message || String(e)}`);
      return;
    }

    const tmpRoot = path.join(os.tmpdir(), `mldsl-publish-${Date.now()}`);
    fs.mkdirSync(tmpRoot, { recursive: true });

    const entryBaseDir = path.dirname(entryFile);
    for (const abs of deps) {
      const rel = path.relative(entryBaseDir, abs);
      const safeRel = rel.startsWith("..") ? path.basename(abs) : rel;
      const dest = path.join(tmpRoot, safeRel);
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(abs, dest);
    }

    const planPath = path.join(tmpRoot, "plan.json");
    output.appendLine(`[publish#${id}] tmp=${tmpRoot}`);

    const env = Object.assign({}, process.env, { PYTHONIOENCODING: "utf-8" });
    const execFile = compiler.kind === "cli" ? compiler.cli : pythonPath || "python";
    const args =
      compiler.kind === "cli"
        ? ["compile", entryFile, "--plan", planPath]
        : [compiler.py, "--plan", planPath, entryFile];

    output.appendLine(`[publish#${id}] ${execFile} ${args.join(" ")}`);

    cp.execFile(execFile, args, { env }, async (err, _stdout, stderr) => {
      if (stderr && String(stderr).trim()) output.appendLine(`[publish#${id}] stderr: ${String(stderr).trim()}`);
      if (err) {
        output.appendLine(`[publish#${id}] ERROR: ${err.message || String(err)}`);
        vscode.window.showErrorMessage("MLDSL: Compile plan failed (see Output → MLDSL Helper)");
        return;
      }

      try {
        await vscode.env.openExternal(vscode.Uri.parse("https://mldsl-hub.vercel.app/"));
      } catch {}

      try {
        await vscode.commands.executeCommand("revealFileInOS", vscode.Uri.file(planPath));
      } catch {
        try {
          cp.execFile("explorer.exe", [tmpRoot]);
        } catch {}
      }

      vscode.window.showInformationMessage(
        `MLDSL: prepared publish bundle (${deps.length} file(s) + plan.json). Drag & drop files into Hub → Опубликовать.`
      );
      output.appendLine(`[publish#${id}] ok files=${deps.length} plan=${planPath}`);
    });
  }

  context.subscriptions.push(vscode.commands.registerCommand("mldsl.compileAndCopy", compileAndCopy));
  context.subscriptions.push(vscode.commands.registerCommand("mldsl.compilePlan", compilePlan));
  context.subscriptions.push(vscode.commands.registerCommand("mldsl.publishModule", publishModule));
  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (
        e.affectsConfiguration("mldsl.apiAliasesPath") ||
        e.affectsConfiguration("mldsl.docsRoot") ||
        e.affectsConfiguration("mldsl.pythonPath") ||
        e.affectsConfiguration("mldsl.compilerPath") ||
        e.affectsConfiguration("mldsl.planPath")
      ) {
        reloadApi("config");
      }
    })
  );

  const completionProvider = vscode.languages.registerCompletionItemProvider(
    { language: "mldsl" },
    {
      provideCompletionItems(document, position) {
        const id = ++seq;
        const line = document.lineAt(position.line).text;

        // gamevalue.<...> completion
        const gvDot = findGameValueDotContext(line, position.character);
        if (gvDot && gamevalues && gamevalues.byLocName) {
          const prefixRaw = String(gvDot.prefix || "");
          const prefix = prefixRaw.toLowerCase();
          const items = [];
          for (const [loc, meta] of Object.entries(gamevalues.byLocName)) {
            const display = stripMcColors((meta && meta.display) || "");
            const hay = `${loc} ${display}`.toLowerCase();
            if (prefix && !hay.includes(prefix)) continue;
            const it = new vscode.CompletionItem(display || loc, vscode.CompletionItemKind.EnumMember);
            it.detail = `gamevalue.${loc}`;
            it.insertText = loc;
            items.push(it);
          }
          if (items.length) {
            output.appendLine(`[completion#${id}] gamevalue.<...> items=${items.length}`);
            return items.slice(0, 120);
          }
        }

        // select.<...> completion (works even when generic module matcher fails)
        const selDot = findSelectDotContext(line, position.character);
        if (selDot) {
          const mod = lookup[moduleAliases["select"]] || lookup["misc"];
          if (!mod) return;
          const prefix = String(selDot.prefix || "");
          const items = [];

          // Common built-in english shorthands supported by the compiler.
          const builtins = [
            { label: "allplayers", detail: "Выбрать всех игроков", insertText: "allplayers" },
            { label: "allmobs", detail: "Выбрать всех мобов", insertText: "allmobs" },
            { label: "allentities", detail: "Выбрать всех сущностей", insertText: "allentities" },
            { label: "randomplayer", detail: "Выбрать случайного игрока", insertText: "randomplayer" },
            { label: "randommob", detail: "Выбрать случайного моба", insertText: "randommob" },
            { label: "randomentity", detail: "Выбрать случайную сущность", insertText: "randomentity" },
            { label: "defaultplayer", detail: "Выбрать игрока по умолчанию", insertText: "defaultplayer" },
            { label: "defaultentity", detail: "Выбрать сущность по умолчанию", insertText: "defaultentity" },
          ];
          for (const b of builtins) {
            if (prefix && !b.label.startsWith(prefix)) continue;
            const it = new vscode.CompletionItem(b.label, vscode.CompletionItemKind.Function);
            it.detail = `select.${b.label} · ${b.detail}`;
            it.insertText = b.insertText;
            items.push(it);
          }

          const hints = [
            { label: "player", insertText: "player." },
            { label: "entity", insertText: "entity." },
            { label: "mob", insertText: "mob." },
            { label: "ifplayer", insertText: "ifplayer." },
            { label: "ifmob", insertText: "ifmob." },
            { label: "ifentity", insertText: "ifentity." },
            { label: "игрок", insertText: "игрок." },
            { label: "сущность", insertText: "сущность." },
            { label: "моб", insertText: "моб." },
            { label: "еслиигрок", insertText: "еслиигрок." },
            { label: "еслисущество", insertText: "еслисущество." },
          ];
          for (const h of hints) {
            if (prefix && !h.label.startsWith(prefix)) continue;
            const item = new vscode.CompletionItem(h.label, vscode.CompletionItemKind.Keyword);
            item.detail = "Подсказка для select (не действие)";
            item.insertText = h.insertText;
            items.push(item);
          }

          for (const [alias, entry] of Object.entries(mod.byName)) {
            if (prefix && !alias.startsWith(prefix)) continue;
            const spec = entry.spec;
            if (!isSelectSpec(spec)) continue;
            const funcName = entry.funcName;
            const item = new vscode.CompletionItem(alias, vscode.CompletionItemKind.Function);
            const params = (spec.params || []).map((p) => p.name).join(", ");
            const menuName = spec.menu ? ` · ${spec.menu}` : "";
            item.detail = `select.${funcName}(${params})${menuName}`;
            item.documentation = specToMarkdown(spec);
            items.push(item);
          }

          output.appendLine(`[completion#${id}] select-dot prefix='${prefix}' items=${items.length}`);
          return items.slice(0, 120);
        }

        // event(...) completion
        const evCall = findEventCallContext(line, position.character);
        if (evCall && events.all.length) {
          const prefixRaw = String(evCall.inside || "").trim().replace(/^\"|\"$/g, "");
          const prefix = prefixRaw.toLowerCase();
          const items = [];
          for (const e of events.all) {
            const hay = `${e.name} ${e.sign2 || ""}`.toLowerCase();
            if (prefix && !hay.includes(prefix)) continue;
            const item = new vscode.CompletionItem(e.name, vscode.CompletionItemKind.EnumMember);
            item.detail = `${e.kind === "world" ? "Событие мира" : "Событие игрока"}${e.sign2 ? ` (sign2: ${e.sign2})` : ""}`;
            item.insertText = `"${e.name}"`;
            items.push(item);
          }
          if (items.length) {
            output.appendLine(`[completion#${id}] event(...) items=${items.length}`);
            return items.slice(0, 80);
          }
        }

        // event.<name> snippet completion
        const evDot = findEventDotContext(line, position.character);
        if (evDot && events.all.length) {
          const prefix = String(evDot.prefix || "").toLowerCase();
          const items = [];
          for (const e of events.all) {
            const hay = `${e.name} ${e.sign2 || ""}`.toLowerCase();
            if (prefix && !hay.includes(prefix)) continue;
            const item = new vscode.CompletionItem(e.name, vscode.CompletionItemKind.Snippet);
            item.detail = `${e.kind === "world" ? "Событие мира" : "Событие игрока"}${e.sign2 ? ` (sign2: ${e.sign2})` : ""}`;
            item.insertText = new vscode.SnippetString(`event("${e.name}") {\n\t$0\n}`);
            items.push(item);
          }
          if (items.length) {
            output.appendLine(`[completion#${id}] event.<...> items=${items.length}`);
            return items.slice(0, 80);
          }
        }

        // Argument completion: module.func(... here ...)
        const call = findCallContext(line, position.character);
        if (call) {
          const mod = lookup[call.module];
          if (!mod) return;
          const entry = mod.byName[call.func];
          if (!entry) return;
          const spec = entry.spec || {};

          const params = Array.isArray(spec.params) ? spec.params : [];
          const enums = Array.isArray(spec.enums) ? spec.enums : [];

          const parts = call.inside.split(",");
          const token = String(parts[parts.length - 1] || "").trim();
          const mEq = token.match(/^([\w\u0400-\u04FF]+)\s*=\s*(.*)$/);

          // Value completion for enum: key=value (suggest enum options)
          if (mEq) {
            const key = mEq[1];
            const valuePrefix = (mEq[2] || "").trim();

            let chosenEnum = null;
            if (enums.length === 1) {
              chosenEnum = enums[0];
            } else {
              const keyNorm = normKey(key);
              const keyTr = ruToTranslitIdent(key);
              const keyTrNorm = normKey(keyTr);
              chosenEnum =
                enums.find((e) => e && (normKey(e.name) === keyNorm || normKey(e.name) === keyTrNorm)) || null;
              if (!chosenEnum) {
                chosenEnum = enums.find((e) => e && normKey(translitToRuIdent(e.name)) === keyNorm) || null;
              }
            }

            if (chosenEnum && chosenEnum.options) {
              const opts = chosenEnum.options || {};
              const items = [];
              const alreadyQuoted = valuePrefix.startsWith("\"") || valuePrefix.startsWith("'");

              // Extra shorthands for common "separator" enums.
              const optText = Object.keys(opts).join(" ").toLowerCase();
              const hasNoSep = optText.includes("без");
              const hasSpaceSep = optText.includes("пробел");
              const hasNewlineSep = optText.includes("строк");
              if (hasNoSep && hasSpaceSep && hasNewlineSep) {
                const it0 = new vscode.CompletionItem("(default) \"\"", vscode.CompletionItemKind.EnumMember);
                it0.detail = "alias: empty string";
                it0.insertText = "\"\"";
                items.push(it0);
                const it1 = new vscode.CompletionItem("(space) \" \"", vscode.CompletionItemKind.EnumMember);
                it1.detail = "alias: space";
                it1.insertText = "\" \"";
                items.push(it1);
                const it2 = new vscode.CompletionItem("(newline) \"\\\\n\"", vscode.CompletionItemKind.EnumMember);
                it2.detail = "alias: newline";
                it2.insertText = "\"\\\\n\"";
                items.push(it2);
              }

              for (const [label, clicks] of Object.entries(opts)) {
                const item = new vscode.CompletionItem(label, vscode.CompletionItemKind.EnumMember);
                item.detail = `${call.module}.${entry.funcName} enum ${chosenEnum.name} clicks=${clicks}`;
                item.insertText = alreadyQuoted ? label : `"${label}"`;
                items.push(item);
              }
              if (items.length) {
                output.appendLine(
                  `[completion#${id}] enum values ${call.module}.${entry.funcName} key=${key} items=${items.length}`
                );
                return items;
              }
            }
          }

          // Key completion inside (...) : suggest param/enum names.
          const rawKeyPrefix = !token.includes("=") ? token : "";
          const keyPrefixNorm = normKey(rawKeyPrefix);
          const argItems = [];
          for (const p of params) {
            const name = String(p.name || "");
            if (!name) continue;
            const nameRu = translitToRuIdent(name);

            const addItem = (label, insertName, detailExtra) => {
              if (keyPrefixNorm && !normKey(label).startsWith(keyPrefixNorm)) return;
              const item = new vscode.CompletionItem(label, vscode.CompletionItemKind.Property);
              item.detail = `param ${p.mode} slot ${p.slot}${detailExtra ? ` (${detailExtra})` : ""}`;
              item.insertText = new vscode.SnippetString(`${insertName}=\${1}`);
              argItems.push(item);
            };

            addItem(name, name, null);
            if (nameRu && nameRu !== name) addItem(nameRu, nameRu, `alias for ${name}`);
          }
          for (const e of enums) {
            const name = String(e.name || "");
            if (!name) continue;
            const nameRu = translitToRuIdent(name);

            const addItem = (label, insertName, detailExtra) => {
              if (keyPrefixNorm && !normKey(label).startsWith(keyPrefixNorm)) return;
              const item = new vscode.CompletionItem(label, vscode.CompletionItemKind.Property);
              item.detail = `enum slot ${e.slot}${detailExtra ? ` (${detailExtra})` : ""}`;
              item.insertText = new vscode.SnippetString(`${insertName}=\${1}`);
              argItems.push(item);
            };

            addItem(name, name, null);
            if (nameRu && nameRu !== name) addItem(nameRu, nameRu, `alias for ${name}`);
          }
          if (argItems.length) {
            output.appendLine(`[completion#${id}] arg keys ${call.module}.${entry.funcName} items=${argItems.length}`);
            return argItems;
          }
        }

        const info = findModuleAndPrefix(line, position.character);
        
        // Автодополнение для ключевых слов if_player и if_game
        if (!info) {
          const wordRange = document.getWordRangeAtPosition(position, /[a-zA-Z_][a-zA-Z0-9_]*/);
          if (wordRange) {
            const word = document.getText(wordRange);
            if (word.startsWith("if_")) {
              const items = [];
              if ("if_player".startsWith(word)) {
                const item = new vscode.CompletionItem("if_player", vscode.CompletionItemKind.Keyword);
                item.detail = "Проверка условий игрока";
                item.documentation = "Проверка условий игрока: if_player.function_name(параметры) { ... }";
                items.push(item);
              }
              if ("if_game".startsWith(word)) {
                const item = new vscode.CompletionItem("if_game", vscode.CompletionItemKind.Keyword);
                item.detail = "Проверка условий игры";
                item.documentation = "Проверка условий игры: if_game.function_name(параметры) { ... }";
                items.push(item);
              }
              if ("SelectObject.player.IfPlayer".startsWith(word)) {
                const item = new vscode.CompletionItem("SelectObject.player.IfPlayer", vscode.CompletionItemKind.Keyword);
                item.detail = "Старый синтаксис проверки условий игрока";
                item.documentation = "Старый синтаксис: SelectObject.player.IfPlayer.FunctionName { ... }";
                items.push(item);
              }
              if ("IfGame".startsWith(word)) {
                const item = new vscode.CompletionItem("IfGame", vscode.CompletionItemKind.Keyword);
                item.detail = "Старый синтаксис проверки условий игры";
                item.documentation = "Старый синтаксис: IfGame.FunctionName { ... }";
                items.push(item);
              }
              if (items.length > 0) {
                output.appendLine(`[completion#${id}] keyword completion for '${word}' items=${items.length}`);
                return items;
              }
            }
          }
          output.appendLine(`[completion#${id}] no module prefix match`);
          return;
        }

        let mod = lookup[info.module];
        if (info.module === "select") {
          mod = lookup[moduleAliases["select"]] || lookup["misc"];
        }
        if (!mod) {
          output.appendLine(`[completion#${id}] module not found: ${info.module}`);
          return;
        }

        const items = [];
        if (info.module === "select") {
          const { wantPlayer, wantEntity } = selectHintsFromRawModule(info.rawModule);
          const hints = [
            { label: "player", insertText: "player." },
            { label: "entity", insertText: "entity." },
            { label: "mob", insertText: "mob." },
            { label: "ifplayer", insertText: "ifplayer." },
            { label: "ifmob", insertText: "ifmob." },
            { label: "игрок", insertText: "игрок." },
            { label: "сущность", insertText: "сущность." },
            { label: "моб", insertText: "моб." },
            { label: "еслиигрок", insertText: "еслиигрок." },
            { label: "еслисущество", insertText: "еслисущество." },
          ];
          for (const h of hints) {
            if (info.prefix && !h.label.startsWith(info.prefix)) continue;
            const item = new vscode.CompletionItem(h.label, vscode.CompletionItemKind.Keyword);
            item.detail = "Подсказка для select (не действие)";
            item.insertText = h.insertText;
            items.push(item);
          }
        }
        for (const [alias, entry] of Object.entries(mod.byName)) {
          if (info.prefix && !alias.startsWith(info.prefix)) continue;
          const funcName = entry.funcName;
          const spec = entry.spec;
          if (info.module === "select") {
            const { wantPlayer, wantEntity } = selectHintsFromRawModule(info.rawModule);
            if (!isSelectSpec(spec)) continue;
            const dom = selectDomain(spec);
            if (wantEntity && dom !== "entity") continue;
            if (wantPlayer && dom !== "player") continue;
          }
          const item = new vscode.CompletionItem(alias, vscode.CompletionItemKind.Function);
          const params = (spec.params || []).map((p) => p.name).join(", ");
          const menuName = spec.menu ? ` — ${spec.menu}` : "";
          item.detail = `${info.module}.${funcName}(${params})${menuName}`;
          if (alias !== funcName) {
            item.detail += `  (alias of ${funcName})`;
          }
          item.documentation = specToMarkdown(spec);
          items.push(item);
        }
        output.appendLine(
          `[completion#${id}] ${info.module}. prefix='${info.prefix}' items=${items.length}`
        );
        return items;
      },
    },
    ".",
    "(",
    ",",
    "=",
    ".",
    " ",
    "\"",
    "'"
  );

  const hoverProvider = vscode.languages.registerHoverProvider({ language: "mldsl" }, {
    provideHover(document, position) {
      const id = ++seq;
      // event(...) hover
      const line = document.lineAt(position.line).text;
      const m = line.match(/\b(?:event|событие)\s*\(\s*([^\)]*)\s*\)/i);
      if (m && events.all.length) {
        const md = new vscode.MarkdownString();
        md.isTrusted = true;
        md.supportHtml = true;
        const allPlayer = events.all.filter((x) => x.kind === "player").map((x) => x.name);
        const allWorld = events.all.filter((x) => x.kind === "world").map((x) => x.name);
        md.appendMarkdown(`**События (из regallactions_export):**\n\n`);
        md.appendMarkdown(`- Событие игрока: ${allPlayer.length}\n`);
        md.appendMarkdown(`- Событие мира: ${allWorld.length}\n\n`);
        const sample = events.all.slice(0, 30).map((x) => `- ${x.name}`).join("\n");
        md.appendMarkdown(`**Примеры:**\n${sample}\n`);
        output.appendLine(`[hover#${id}] event hover list items=${events.all.length}`);
        return new vscode.Hover(md);
      }

      const q = findQualifiedAtPosition(document, position);
      if (!q) return;

      // Hover for gamevalue.<LOCNAME>
      if ((q.module || "").toLowerCase() === "gamevalue" && gamevalues && gamevalues.byLocName) {
        const meta = gamevalues.byLocName[q.func];
        if (meta) {
          const md = new vscode.MarkdownString();
          md.isTrusted = true;
          md.appendMarkdown(`**gamevalue.${q.func}**\n\n`);
          if (meta.display) md.appendMarkdown(`- **name:** ${stripMcColors(meta.display)}\n`);
          if (meta.id) md.appendMarkdown(`- **item:** ${meta.id}${meta.meta != null ? `:${meta.meta}` : ""}\n`);
          if (meta.lore) md.appendMarkdown(`\n${stripMcColors(meta.lore)}\n`);
          output.appendLine(`[hover#${id}] gamevalue.${q.func}`);
          return new vscode.Hover(md, q.range);
        }
      }

      const mod = lookup[q.module];
      if (!mod) return;
      const entry = mod.byName[q.func];
      if (!entry) return;
      output.appendLine(`[hover#${id}] ${q.text} -> ${q.module}.${entry.funcName}`);
      return new vscode.Hover(specToMarkdown(entry.spec), q.range);
    }
  });

  const defProvider = vscode.languages.registerDefinitionProvider({ language: "mldsl" }, {
    provideDefinition(document, position) {
      const id = ++seq;
      const q = findQualifiedAtPosition(document, position);
      if (!q) return;
      const mod = lookup[q.module];
      if (!mod) {
        output.appendLine(`[def#${id}] module not found for ${q.text}`);
        return;
      }
      const entry = mod.byName[q.func];
      if (!entry) {
        output.appendLine(`[def#${id}] function not found for ${q.text}`);
        // show a few keys for debugging
        const keys = Object.keys(mod.byName).slice(0, 30);
        output.appendLine(`[def#${id}] known keys sample: ${keys.join(", ")}`);
        return;
      }
      const { docsRoot } = getConfig();
      const docPath = path.join(docsRoot || "", q.module, `${entry.funcName}.md`);
      if (!docsRoot) {
        output.appendLine(`[def#${id}] docsRoot not set`);
        return;
      }
      if (!fs.existsSync(docPath)) {
        output.appendLine(`[def#${id}] doc not found: ${docPath}`);
        return;
      }
      output.appendLine(`[def#${id}] ${q.text} -> ${docPath}`);
      const uri = vscode.Uri.file(docPath);
      return new vscode.Location(uri, new vscode.Position(0, 0));
    }
  });

  const diagnostics = vscode.languages.createDiagnosticCollection("mldsl");

  function updateDiagnostics(doc) {
    if (!doc || doc.languageId !== "mldsl") return;
    const diags = [];

    // Brace balance (very simple, best-effort; ignores braces inside string literals).
    let depth = 0;
    for (let lineNum = 0; lineNum < doc.lineCount; lineNum++) {
      const original = doc.lineAt(lineNum).text;
      const line = original.replace(/\"([^\"\\\\]|\\\\.)*\"/g, "\"\"");
      for (let i = 0; i < line.length; i++) {
        if (line[i] === "{") depth++;
        else if (line[i] === "}") depth--;
        if (depth < 0) {
          const range = new vscode.Range(
            new vscode.Position(lineNum, i),
            new vscode.Position(lineNum, i + 1)
          );
          diags.push(new vscode.Diagnostic(range, "Extra '}'", vscode.DiagnosticSeverity.Error));
          depth = 0;
        }
      }
    }
    if (depth > 0) {
      const lastLine = Math.max(0, doc.lineCount - 1);
      const lastLen = doc.lineAt(lastLine).text.length;
      const range = new vscode.Range(new vscode.Position(lastLine, lastLen), new vscode.Position(lastLine, lastLen));
      diags.push(
        new vscode.Diagnostic(range, `Missing '}' (unclosed blocks: ${depth})`, vscode.DiagnosticSeverity.Error)
      );
    }

    const re = /([a-zA-Z_][\w]*)\.([\w\u0400-\u04FF]+)/g;
    for (let lineNum = 0; lineNum < doc.lineCount; lineNum++) {
      const line = doc.lineAt(lineNum).text;
      let m;
      while ((m = re.exec(line))) {
        const moduleName = m[1];
        const funcName = m[2];
        const range = new vscode.Range(
          new vscode.Position(lineNum, m.index),
          new vscode.Position(lineNum, m.index + m[0].length)
        );
        const mod = lookup[moduleName];
        if (!mod) {
          diags.push(new vscode.Diagnostic(range, `Unknown module '${moduleName}'`, vscode.DiagnosticSeverity.Warning));
          continue;
        }
        const entry = mod.byName[funcName];
        if (!entry) {
          // Don't warn on partial typing: player.соо (prefix of player.сообщение)
          const keys = Object.keys(mod.byName);
          const hasPrefix = keys.some((k) => k.startsWith(funcName));
          if (hasPrefix) {
            continue;
          }
          diags.push(
            new vscode.Diagnostic(
              range,
              `Unknown function '${moduleName}.${funcName}'`,
              vscode.DiagnosticSeverity.Warning
            )
          );
        }
      }
    }
    diagnostics.set(doc.uri, diags);
  }

  context.subscriptions.push(diagnostics);
  context.subscriptions.push(vscode.workspace.onDidOpenTextDocument((doc) => updateDiagnostics(doc)));
  context.subscriptions.push(
    vscode.workspace.onDidChangeTextDocument((e) => updateDiagnostics(e.document))
  );
  context.subscriptions.push(vscode.window.onDidChangeActiveTextEditor((ed) => ed && updateDiagnostics(ed.document)));

  function updateStatusBar() {
    const ed = vscode.window.activeTextEditor;
    const ok = ed && ed.document && ed.document.languageId === "mldsl";
    if (!statusItem) {
      statusItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
      statusItem.command = "mldsl.compileAndCopy";
      context.subscriptions.push(statusItem);
    }
    if (ok) {
      statusItem.text = "MLDSL: Compile";
      statusItem.tooltip = "Compile current .mldsl and copy /placeadvanced command(s) to clipboard (or use MLDSL: Compile to plan.json)";
      statusItem.show();
    } else {
      statusItem.hide();
    }
  }

  context.subscriptions.push(vscode.window.onDidChangeActiveTextEditor(() => updateStatusBar()));
  updateStatusBar();

  context.subscriptions.push(completionProvider, hoverProvider, defProvider);
}

function deactivate() {}

module.exports = { activate, deactivate };
