const vscode = require("vscode");
const fs = require("fs");
const path = require("path");

function readJsonSafe(p) {
  try {
    if (!p) return null;
    if (!fs.existsSync(p)) return null;
    return JSON.parse(fs.readFileSync(p, "utf8"));
  } catch {
    return null;
  }
}

function getConfig() {
  const cfg = vscode.workspace.getConfiguration("mldsl");
  return {
    apiAliasesPath: cfg.get("apiAliasesPath"),
    docsRoot: cfg.get("docsRoot"),
  };
}

function loadApi() {
  const { apiAliasesPath } = getConfig();
  return readJsonSafe(apiAliasesPath) || {};
}

function findModuleAndPrefix(lineText, positionChar) {
  const left = lineText.slice(0, positionChar);
  const m = left.match(/([a-zA-Z_][\\w]*)\\.([\\w\\u0400-\\u04FF]*)$/);
  if (!m) return null;
  return { module: m[1], prefix: m[2] || "" };
}

function findQualifiedWord(document, position) {
  const range = document.getWordRangeAtPosition(position, /[\\w\\u0400-\\u04FF\\.]+/);
  if (!range) return null;
  const text = document.getText(range);
  if (!text.includes(".")) return null;
  const parts = text.split(".");
  if (parts.length !== 2) return null;
  return { module: parts[0], func: parts[1], range };
}

function specToMarkdown(spec) {
  const lines = [];
  if (spec.sign1) lines.push(`**sign1:** ${spec.sign1}`);
  if (spec.sign2) lines.push(`**sign2:** ${spec.sign2}`);
  if (spec.gui) lines.push(`**gui:** ${spec.gui}`);
  if (spec.params && spec.params.length) {
    lines.push("");
    lines.push("**params:**");
    for (const p of spec.params) {
      lines.push(`- \`${p.name}\` (${p.mode}) slot ${p.slot}`);
    }
  }
  if (spec.enums && spec.enums.length) {
    lines.push("");
    lines.push("**enums:**");
    for (const e of spec.enums) {
      lines.push(`- \`${e.name}\` slot ${e.slot}`);
    }
  }
  return new vscode.MarkdownString(lines.join("\n"));
}

function activate(context) {
  const api = loadApi();

  const completionProvider = vscode.languages.registerCompletionItemProvider(
    { language: "mldsl" },
    {
      provideCompletionItems(document, position) {
        const line = document.lineAt(position.line).text;
        const info = findModuleAndPrefix(line, position.character);
        if (!info) return;

        const mod = api[info.module];
        if (!mod) return;

        const items = [];
        for (const [funcName, spec] of Object.entries(mod)) {
          if (info.prefix && !funcName.startsWith(info.prefix)) continue;
          const item = new vscode.CompletionItem(funcName, vscode.CompletionItemKind.Function);
          const params = (spec.params || []).map((p) => p.name).join(", ");
          item.detail = `${info.module}.${funcName}(${params})`;
          item.documentation = specToMarkdown(spec);
          items.push(item);
        }
        return items;
      },
    },
    "."
  );

  const hoverProvider = vscode.languages.registerHoverProvider({ language: "mldsl" }, {
    provideHover(document, position) {
      const q = findQualifiedWord(document, position);
      if (!q) return;
      const mod = api[q.module];
      if (!mod) return;
      const spec = mod[q.func];
      if (!spec) return;
      return new vscode.Hover(specToMarkdown(spec), q.range);
    }
  });

  const defProvider = vscode.languages.registerDefinitionProvider({ language: "mldsl" }, {
    provideDefinition(document, position) {
      const q = findQualifiedWord(document, position);
      if (!q) return;
      const mod = api[q.module];
      if (!mod) return;
      const spec = mod[q.func];
      if (!spec) return;
      const { docsRoot } = getConfig();
      const docPath = path.join(docsRoot || "", q.module, `${q.func}.md`);
      if (!docsRoot || !fs.existsSync(docPath)) return;
      const uri = vscode.Uri.file(docPath);
      return new vscode.Location(uri, new vscode.Position(0, 0));
    }
  });

  context.subscriptions.push(completionProvider, hoverProvider, defProvider);
}

function deactivate() {}

module.exports = { activate, deactivate };

