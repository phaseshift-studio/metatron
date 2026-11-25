/*
 * mtron-panels — the domain view of an object, keyed by its type (tid).
 *
 * The CARD is the widget: it always carries the object's `mtron` (x-mtron-ui) and
 * `json` views. A registered PANEL adds a third view and becomes the default one —
 * `[panel][mtron][json]`. The mapping is type -> panel, walked from the exact tid
 * through its parents, so a panel for `/m/instset` also serves `/m/instset/foo`.
 *
 * A panel factory is `(env, api) -> HTMLElement | Promise<HTMLElement>`, where
 * `env` is `{tid, vid, raw}` and `api` is `{json(), pretty()}`. A panel must not
 * own sockets or dialogs; it renders the object and stops there.
 *
 * UMD: `window.MtronObjPanels` in a page, `module.exports` under node (for tests).
 */
import { read } from '../sdk/mtron-io.js';
import { parseSchema, renderForm, collectForm } from './mtron-form.js';
import { parse, write, quote, real, int, writeCodeForm, FORMS, FULL_FORM, nextForm } from '../sdk/obj_mtron_serializer.js';
import { f } from '../sdk/furi.js';
import { parse as envelope } from '../sdk/mtron-envelope.js';
import { toElement, iconButton } from './mtron-dom.js';
import { typesetMath } from './mathjax.js';
import { explanationModel, explainExtension, explainBadge } from './explain-flow.js';

"use strict";

    const panels = new Map();
    const parents = new Map();

    function register(tid, factory) {
        panels.set(tid, factory);
        return factory;
    }

    /** explicit type-chain step; without one, a tid's parent is its uri minus the last segment */
    function registerParent(tid, parent) {
        parents.set(tid, parent);
    }

    function parentOf(tid) {
        if (!tid) return null;
        if (parents.has(tid)) return parents.get(tid) || null;
        const cut = tid.lastIndexOf("/");
        return cut > 0 ? tid.slice(0, cut) : null;
    }

    /** the nearest registered panel on the tid's chain, or null (the card then shows mtron/json) */
    function resolve(tid) {
        let at = tid;
        for (let guard = 0; at && guard < 40; guard += 1) {
            if (panels.has(at)) return panels.get(at);
            at = parentOf(at);
        }
        return null;
    }

    function node(tag, cls, text) {
        const el = document.createElement(tag);
        if (cls) el.className = cls;
        if (text != null) el.textContent = text;
        return el;
    }

    function brief(value) {
        if (value == null) return "—";
        if (typeof value !== "object") return String(value);
        try {
            const text = JSON.stringify(value);
            return text.length > 150 ? text.slice(0, 149) + "…" : text;
        } catch (ignored) {
            return String(value);
        }
    }

    /* resolve a ~-prefixed address against the current frame (null, "/." and "/"
       are the root): ~/network at /foo is /foo/network, at the root is /network */
    function resolveFrame(vid, frame) {
        if (typeof vid !== "string" || vid.charAt(0) !== "~") return vid;
        const rest = vid.slice(1);
        return (frame && frame !== "/." && frame !== "/") ? frame + rest : rest;
    }

    /* a spinner + label for slow panel reads (schema discovery, table load) */
    function spinner(label) {
        const wrap = node("div", "tble-loading");
        wrap.appendChild(node("span", "tble-spinner"));
        wrap.appendChild(document.createTextNode(label || "loading…"));
        return wrap;
    }

    /* ── instset signature tables (type · inst · rewrite · const) ───────────
       the four tables an instset can be asked, shared by the instset panel's
       dropdowns. one read per table, over the x-mtron codec (entries come back
       as complete, unclipped signature strings). */
    const SIG_TABLES = ["type", "inst", "rewrite", "const"];

    function escapeArgs(args) {
        return args.replace(/[(){}[\]?@<>#&=-]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());
    }

    /* the doc/decq read of a signature, quoted the way the machine wants:
       *<plus?int<=int&args=[int]&decq> — the furi supplies the name; the query
       and args stay as the machine spelled them */
    function signatureRead(entry, q) {
        let depth = 0;
        let quote = null;
        let qAt = -1;
        let openAt = -1;
        let closeAt = entry.length;
        for (let i = 0; i < entry.length; i++) {
            const ch = entry[i];
            if (quote) {
                if (ch === "\\") i++;
                else if (ch === quote) quote = null;
                continue;
            }
            if (ch === "'" || ch === '"') { quote = ch; continue; }
            const next = entry[i + 1];
            if (depth === 0 && qAt < 0 && ch === "?") qAt = i;
            if (depth === 0 && openAt < 0 && ch === "(") openAt = i;
            /* < > are brackets only when they are not the <= >= => -> arrows */
            if (ch === "(" || ch === "[" || ch === "{" || (ch === "<" && next !== "=" && next !== ">")) depth++;
            else if (ch === ")" || ch === "]" || ch === "}" || (ch === ">" && next !== "=" && next !== "-")) {
                depth = Math.max(0, depth - 1);
                if (depth === 0 && openAt >= 0 && closeAt === entry.length) closeAt = i;
            }
        }
        const headEnd = openAt > 0 ? openAt : (qAt > 0 ? qAt : entry.length);
        const namePath = entry.slice(0, headEnd);
        const query = qAt > 0 ? entry.slice(qAt, openAt > 0 ? openAt : entry.length) : "";
        const args = openAt > 0 ? escapeArgs(entry.slice(openAt + 1, closeAt)) : null;
        /* the first query param opens with `?`, the rest join with `&` — a
           signature with no rng/dom (a base type) still needs `?decq`; the full
           namePath (its address, not its last segment) is what to read */
        let params = query;
        if (args) params += (params ? "&" : "?") + "args=[" + args + "]";
        params += (params ? "&" : "?") + q;
        return "*<" + namePath + params + ">";
    }

    /* split an x-mtron table list into signature strings: a type's vid (its
       name), else its tid for a base type where tid == vid; an inst/const
       writes back verbatim */
    function signatureOf(obj) {
        if (obj.isType()) {
            const name = obj.vid ? obj.vid : obj.tid;
            return name ? name.toString() : null;
        }
        return write(obj);
    }

    function signaturesOf(text) {
        const source = String(text == null ? "" : text).trim();
        if (!source || source === "noobj") return [];
        const parsed = parse(source);
        const out = [];
        if (parsed.isLst() || parsed.isObjs()) {
            for (const obj of parsed.js) {
                const sig = signatureOf(obj);
                if (sig) out.push(sig);
            }
        } else {
            const sig = signatureOf(parsed);
            if (sig) out.push(sig);
        }
        return out;
    }

    /* /m/instset — the pattern, the q table, and the type/inst/rewrite/const
       tables the machine can be asked. */
    register("/m/instset", async function (env, api) {
        const box = node("div", "mpanel instset-panel");
        const value = await api.json();
        if (!value || typeof value !== "object") {
            box.appendChild(node("div", "mpanel-empty", "no instset value"));
            return box;
        }
        const head = node("div", "mpanel-head");
        head.appendChild(node("span", "mpanel-k", "instset"));
        head.appendChild(node("span", "mpanel-pattern", value.pattern || ""));
        box.appendChild(head);
        const q = Array.isArray(value.q) ? value.q : [];
        box.appendChild(node("div", "mpanel-count", q.length + (q.length === 1 ? " q" : " qs")));
        q.forEach((entry) => {
            const row = node("div", "mpanel-row");
            row.appendChild(node("span", "mpanel-k", (entry && entry.pattern) || "?"));
            const target = entry && (entry.obj || entry.pattern || entry);
            row.appendChild(node("span", "mpanel-v", brief(target)));
            box.appendChild(row);
        });

        /* the four signature tables: side-by-side heads, one shared drop-down */
        const tables = node("div", "instset-tables");
        const heads = node("div", "instset-table-heads");
        const drop = node("div", "instset-drop");
        drop.hidden = true;
        tables.appendChild(heads);
        tables.appendChild(drop);
        const sigCache = new Map();
        let activeKey = null;
        const vid = String((env && env.vid) || "~/instset").replace(/\/+$/, "");

        function sigRead(key) {
            if (!sigCache.has(key))
                sigCache.set(key, api.mtron("*" + vid + "/" + key).then(signaturesOf));
            return sigCache.get(key);
        }

        /* one row's doc/dec: evaluate the read and wear the answer as the
           standard object card (a docs::/decs:: rec is a read result with its
           own address) */
        function showRead(entry, label) {
            const readExpr = signatureRead(entry, label + "q");
            const wear = (text) => {
                const clean = String(text == null ? "" : text).replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "");
                const opened = Object.assign({raw: clean, expr: readExpr}, envelope(clean) || {shortTid: label});
                opened.tid = label + "s";
                opened.isType = false;
                if (api && typeof api.open === "function") api.open(opened);
            };
            if (api && typeof api.mtron === "function")
                api.mtron(readExpr).then(wear).catch((problem) => wear(problem && problem.message ? problem.message : problem));
            else
                wear(readExpr);
        }

        SIG_TABLES.forEach((key) => {
            const toggle = node("button", "instset-table-head", key);
            toggle.type = "button";
            toggle.dataset.table = key;
            heads.appendChild(toggle);

            toggle.addEventListener("click", () => {
                const wasOpen = activeKey === key;
                activeKey = wasOpen ? null : key;
                heads.querySelectorAll("button").forEach((b) => b.classList.toggle("on", b.dataset.table === key && !wasOpen));
                if (wasOpen) {
                    drop.hidden = true;
                    drop.textContent = "";
                    return;
                }
                drop.hidden = false;
                drop.textContent = "";
                drop.appendChild(node("div", "instset-drop-empty", "reading " + vid + "/" + key + "…"));
                sigRead(key).then((list) => {
                    if (activeKey !== key) return;
                    drop.textContent = "";
                    if (!list.length) {
                        drop.appendChild(node("div", "instset-drop-empty", "the instset names no " + key));
                        return;
                    }
                    list.forEach((text) => {
                        const row = node("div", "instset-drop-row");
                        const cell = node("button", "instset-drop-cell");
                        cell.type = "button";
                        cell.title = text + " · click to open";
                        cell.setAttribute("aria-label", "open " + text);
                        paintSig(cell, text);
                        cell.addEventListener("click", () => {
                            if (api && api.open)
                                api.open({isType: key === "type", vid: text, raw: text, shortTid: text.split("/").pop() || text});
                        });
                        row.appendChild(cell);
                        /* auxiliary actions wear the page-wide marks: book = ?docq, a*b = ?decq */
                        ["docq", "decq"].forEach((kind) => {
                            const button = iconButton(kind, () => showRead(text, kind.slice(0, -1)));
                            if (button) row.appendChild(button);
                        });
                        drop.appendChild(row);
                    });
                }).catch((problem) => {
                    if (activeKey !== key) return;
                    drop.textContent = "";
                    drop.appendChild(node("div", "instset-drop-empty", String(problem && problem.message ? problem.message : problem)));
                });
            });
        });

        box.appendChild(tables);
        return box;
    });

    /* mcp_client — a tool runner over metatron, not a JSON-RPC client.
       the mcp_client obj names its tools; a tool is called by addressing its inst:
         <vid>/tool/<tool>/inst(<arg>=>"value", …)
       so the read and the call both go over the page's mtron socket (api.json/api.eval). */
    function mcpClientPanel(env, api) {
        const box = node("div", "mcp-client");
        const vid = env && env.vid;
        if (!vid) {
            box.appendChild(node("div", "mpanel-empty", "pin an mcp_client to run its tools"));
            return box;
        }
        const readJson = (api && api.json) || (() => Promise.resolve(null));
        const runMtron = (api && api.eval) || (() => Promise.reject(new Error("no mtron connection")));
        const paint = (api && api.paint) || ((el, text) => { el.textContent = text; });
        const announce = (tools) => { if (api && api.announce) api.announce(tools); };

        const head = node("div", "tool-head");
        const host = node("code", "", "");
        const status = node("span", "meta", "");
        head.appendChild(host);
        head.appendChild(status);
        box.appendChild(head);

        const hint = node("p", "tool-empty mcp-hint", "reading " + vid + "…");
        box.appendChild(hint);

        const body = node("div", "tool-split");
        body.hidden = true;
        const pick = node("div", "tool-pick");
        const filter = document.createElement("input");
        filter.type = "search";
        filter.className = "tools-filter";
        filter.placeholder = "filter…";
        filter.spellcheck = false;
        const list = document.createElement("ol");
        list.className = "tools";
        pick.appendChild(filter);
        pick.appendChild(list);

        const run = node("div", "tool-run");
        run.innerHTML =
            '<p class="tool-empty tool-prompt">select a tool</p>' +
            '<div class="tool-form-wrap" hidden>' +
            '<div class="tool-head"><code class="tool-name"></code><span class="meta tool-status"></span></div>' +
            '<p class="tool-desc"></p>' +
            '<form class="tool-form"></form>' +
            '<div class="tool-out"><pre class="tool-result"></pre></div>' +
            '<p class="tool-expr" hidden><code></code></p>' +
            '</div>';
        body.appendChild(pick);
        body.appendChild(run);
        box.appendChild(body);

        const $ = (sel) => box.querySelector(sel);
        const $$ = (sel) => Array.from(box.querySelectorAll(sel));
        const prompt = $(".tool-prompt");
        const wrap = $(".tool-form-wrap");
        const form = $(".tool-form");

        let tools = [];
        let current = null;
        let running = false;

        function literal(value) {
            return "'" + String(value).replace(/\\/g, "\\\\").replace(/'/g, "\\'")
                .replace(/\n/g, "\\n").replace(/\r/g, "\\r") + "'";
        }

        /* the tool's arg rec is name -> description/type; empty keys are not arguments */
        function argsOf(tool) {
            const args = tool && tool.arg;
            if (!args || typeof args !== "object") return [];
            return Object.keys(args)
                .filter((key) => key && key !== "<>" && key !== "")
                .map((key) => ({ key: key, note: String(args[key] == null ? "" : args[key]) }));
        }

        function markSelected(tool) {
            $$("li[data-name]").forEach((li) => li.classList.toggle("selected", li.dataset.name === tool.name));
        }

        function select(tool) {
            current = tool;
            markSelected(tool);
            prompt.hidden = true;
            wrap.hidden = false;
            $(".tool-name").textContent = tool.name;
            $(".tool-desc").textContent = tool.desc || "(no description)";
            $(".tool-status").textContent = "idle";
            $(".tool-result").textContent = "";
            $(".tool-expr").hidden = true;
            buildForm(tool);
        }

        function buildForm(tool) {
            form.textContent = "";
            const args = argsOf(tool);
            if (!args.length) form.appendChild(node("p", "tool-empty", "no parameters"));
            args.forEach((arg) => {
                const field = node("label", "field");
                field.appendChild(node("span", "field-name", arg.key));
                const input = document.createElement("input");
                input.type = "text";
                input.name = arg.key;
                input.placeholder = arg.note || "value";
                input.spellcheck = false;
                field.appendChild(input);
                if (arg.note) field.appendChild(node("span", "field-note", arg.note));
                form.appendChild(field);
            });
            const actions = node("div", "form-actions");
            const error = node("span", "form-error", "");
            const submit = document.createElement("button");
            submit.type = "submit";
            submit.className = "primary";
            submit.textContent = "run";
            actions.appendChild(error);
            actions.appendChild(submit);
            form.appendChild(actions);
            form.onsubmit = (ev) => {
                ev.preventDefault();
                if (running) return;
                error.textContent = "";
                const fields = $$(".tool-form [name]")
                    .map((input) => ({ key: input.name, value: input.value }));
                call(tool, fields).catch((problem) => {
                    error.textContent = problem && problem.message ? problem.message : String(problem);
                });
            };
        }

        function expression(tool, fields) {
            const args = fields
                .filter((field) => field.value !== "")
                .map((field) => field.key + "=>" + literal(field.value));
            return vid + "/tool/" + tool.name + "/inst(" + args.join(",") + ")";
        }

        async function call(tool, fields) {
            running = true;
            $(".tool-status").textContent = "running…";
            $(".tool-result").textContent = "";
            const expr = expression(tool, fields);
            $(".tool-expr").hidden = false;
            $(".tool-expr code").textContent = expr;
            try {
                const text = String(await runMtron(expr));
                if (/^fail::/.test(text)) throw new Error(text);
                $(".tool-status").textContent = "ok";
                paint($(".tool-result"), text, "mtron");
            } catch (problem) {
                $(".tool-status").textContent = "error";
                paint($(".tool-result"), String(problem && problem.message ? problem.message : problem), "err");
            } finally {
                running = false;
            }
        }

        function renderTools() {
            const query = (filter.value || "").toLowerCase().trim();
            list.textContent = "";
            if (!tools.length) {
                list.appendChild(node("li", "empty", "the mcp_client offers no tools"));
                return;
            }
            tools.forEach((tool) => {
                const hay = (tool.name + " " + (tool.desc || "")).toLowerCase();
                const li = document.createElement("li");
                li.dataset.name = tool.name;
                li.tabIndex = 0;
                li.setAttribute("role", "button");
                li.title = tool.name;
                li.style.display = !query || hay.indexOf(query) !== -1 ? "" : "none";
                li.appendChild(node("code", "name", tool.name));
                li.appendChild(node("span", "desc", tool.desc || "(no description)"));
                const choose = () => select(tool);
                li.addEventListener("click", choose);
                li.addEventListener("keydown", (ev) => {
                    if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); choose(); }
                });
                list.appendChild(li);
            });
            if (current) markSelected(current);
        }

        filter.addEventListener("input", renderTools);

        async function load() {
            try {
                const value = await readJson();
                if (!value || typeof value !== "object") throw new Error("no mcp_client value at " + vid);
                host.textContent = value.host ? String(value.host) : vid;
                host.title = vid;
                status.textContent = value.status ? "connected" : "—";
                const map = value.tool && typeof value.tool === "object" ? value.tool : {};
                tools = Object.keys(map).map((key) => {
                    const tool = map[key] && typeof map[key] === "object" ? map[key] : {};
                    return {
                        name: String(tool.name || key),
                        desc: tool.desc ? String(tool.desc) : "",
                        arg: tool.arg
                    };
                });
                hint.hidden = true;
                body.hidden = false;
                renderTools();
                announce(tools);
            } catch (problem) {
                hint.textContent = "cannot read " + vid + ": " +
                    (problem && problem.message ? problem.message : problem);
            }
        }

        load();
        return box;
    }

    register("/m/web/mcp/mcp_client", mcpClientPanel);
    register("mcp_client", mcpClientPanel);


    /* mcp_server — the server spec: the tools, resources and prompts it exposes.
       a tool's value is its inst signature (the address you would call), so the
       panel is a catalog, not a runner: running a tool is the client panel's job. */
    function mcpServerPanel(env, api) {
        const box = node("div", "mpanel mcp-server");
        const vid = env && env.vid;
        if (!vid) {
            box.appendChild(node("div", "mpanel-empty", "pin an mcp_server to see its catalog"));
            return box;
        }
        const readJson = (api && api.json) || (() => Promise.resolve(null));
        const head = node("div", "mpanel-head");
        head.appendChild(node("span", "mpanel-k", "mcp_server"));
        head.appendChild(node("span", "mpanel-pattern", vid));
        box.appendChild(head);
        const counts = node("div", "mpanel-count", "reading " + vid + "…");
        box.appendChild(counts);

        function entries(value) {
            if (value == null) return [];
            if (Array.isArray(value)) return value.map((item, index) => [String(index), item]);
            if (typeof value === "object") return Object.entries(value);
            return [[String(value), value]];
        }

        function short(value) {
            if (value == null) return "—";
            if (typeof value !== "object") return String(value);
            const text = value.desc || value.name || value.uri || value.text || "";
            return String(text);
        }

        function section(title, list, label, collapsed) {
            if (!list.length) return;
            let host = box;
            if (collapsed) {
                host = document.createElement("details");
                host.className = "mcp-server-section";
                const summary = document.createElement("summary");
                summary.textContent = title + " (" + list.length + ")";
                host.appendChild(summary);
            } else {
                const head = node("div", "mpanel-row mcp-server-section");
                head.appendChild(node("span", "mpanel-k", title));
                head.appendChild(node("span", "mpanel-v", String(list.length)));
                box.appendChild(head);
            }
            list.forEach(([key, value]) => {
                const row = node("div", "mpanel-row");
                row.appendChild(node("span", "mpanel-k", label ? label(key, value) : key));
                row.appendChild(node("span", "mpanel-v", short(value)));
                host.appendChild(row);
            });
            if (collapsed) box.appendChild(host);
        }

        readJson().then((value) => {
            if (!value || typeof value !== "object") throw new Error("no mcp_server value at " + vid);
            const tools = entries(value.tool);
            const resources = entries(value.resource);
            const prompts = entries(value.prompt);
            counts.textContent = tools.length + " tools · " +
                resources.length + " resources · " + prompts.length + " prompts";
            section("tools", tools, (key) => key);
            section("resources", resources, (key, res) => (res && res.name) || key, true);
            section("prompts", prompts, (key, prompt) => (prompt && prompt.name) || key, true);
        }).catch((problem) => {
            counts.textContent = "cannot read " + vid + ": " +
                (problem && problem.message ? problem.message : problem);
        });
        return box;
    }

    register("/m/web/mcp/mcp_server", mcpServerPanel);
    register("mcp_server", mcpServerPanel);


    /* machine — the frame card: the machine stack (push/pop/move) plus the four
       component tiles of the frame the page is standing in. the frame is page state;
       every action is one framed expression, so the console and panels follow it. */
    function machinePanel(env, api) {
        const box = node("div", "mpanel machine-panel");
        const readJson = (api && api.read) || (() => Promise.resolve(null));
        const run = (api && api.eval) || (() => Promise.reject(new Error("no mtron connection")));
        const setFrame = (api && api.frame) || (() => { });
        const evalJson = (api && api.evalJson) || (() => Promise.resolve([]));
        let frame = (env && env.vid) || "/.";
        let treeVids = ["/."];
        let treeToken = 0;
        let token = 0;

        const head = node("div", "mpanel-head");
        head.appendChild(node("span", "mpanel-k", "machine"));
        const vidLabel = node("span", "mpanel-pattern", frame);
        head.appendChild(vidLabel);
        box.appendChild(head);

        const stack = node("div", "machine-stack");
        stack.setAttribute("role", "tree");
        box.appendChild(stack);

        const form = document.createElement("form");
        form.className = "machine-push";
        const input = document.createElement("input");
        input.type = "text";
        input.placeholder = "push a frame…";
        input.spellcheck = false;
        const push = document.createElement("button");
        push.type = "submit";
        push.className = "primary";
        push.textContent = "push";
        const pop = document.createElement("button");
        pop.type = "button";
        pop.className = "ghost";
        pop.textContent = "pop";
        form.appendChild(input);
        form.appendChild(push);
        form.appendChild(pop);
        box.appendChild(form);

        const tiles = node("div", "machine-tiles");
        box.appendChild(tiles);

        const note = node("p", "machine-note", "");
        note.hidden = true;
        box.appendChild(note);

        function base() {
            return frame === "/." || frame === "/" ? "" : String(frame);
        }

        function ancestors(vid) {
            const path = String(vid || "/.").replace(/\/+$/, "") || "/.";
            if (path === "/." || path === "/" || path === "") return ["/."];
            const parts = path.split("/").filter(Boolean);
            const nodes = ["/."];
            let at = "";
            parts.forEach((part) => { at += "/" + part; nodes.push(at); });
            return nodes;
        }

        function parentOf(vid) {
            const nodes = ancestors(vid);
            return nodes.length > 1 ? nodes[nodes.length - 2] : "/.";
        }

        function fail(message) {
            note.hidden = !message;
            note.textContent = message || "";
        }

        function uiVids(text) {
            return String(text == null ? "" : text).match(/\/[A-Za-z0-9_.\/-]+/g) || [];
        }

        async function childrenOf(vid) {
            const value = (vid === "/." || vid === "/")
                ? await evalJson("*</.>.>>child.vid()")
                : await evalJson("move(<" + vid + ">).@~/child.vid()");
            if (value == null) return [];
            if (Array.isArray(value)) return value.map((kid) => String(kid));
            return [String(value)];
        }

        /* a node answers its children with @~/child once the session stands in it;
           the root's first child comes from its >>child field. */
        async function loadTree() {
            const my = ++treeToken;
            const root = "/.";
            const seen = new Set([root]);
            const vids = [root];
            const queue = [root];
            try {
                while (queue.length > 0) {
                    const parent = queue.shift();
                    const kids = await childrenOf(parent);
                    kids.forEach((kid) => {
                        if (kid && !seen.has(kid)) {
                            seen.add(kid);
                            vids.push(kid);
                            queue.push(kid);
                        }
                    });
                }
            } catch (problem) {
                vids.length = 0;
                ancestors(frame).forEach((vid) => vids.push(vid));
            }
            if (my !== treeToken) return;
            treeVids = vids;
            /* a frame popped elsewhere must not leave the page standing in it */
            if (!treeVids.includes(frame)) {
                const existing = ancestors(frame).slice().reverse().find((vid) => treeVids.includes(vid)) || "/.";
                frame = existing;
                setFrame(frame);
                vidLabel.textContent = frame;
                load();
            }
            drawTree();
        }

        function siblingsOf(vid) {
            const parent = parentOf(vid);
            return treeVids.filter((other) => other !== "/." && parentOf(other) === parent).sort();
        }

        function isLastOf(vid) {
            const siblings = siblingsOf(vid);
            return siblings.length === 0 || siblings[siblings.length - 1] === vid;
        }

        /* the connected set: a │ for every ancestor branch that continues below this
           node, and a ├/└ elbow into the node itself. */
        function treePrefix(vid) {
            if (vid === "/." || vid === "/") return "";
            const chain = ancestors(vid);
            let prefix = "";
            for (let level = 1; level < chain.length - 1; level += 1) {
                prefix += isLastOf(chain[level]) ? "   " : "│  ";
            }
            prefix += isLastOf(vid) ? "└─ " : "├─ ";
            return prefix;
        }

        function drawTree() {
            stack.textContent = "";
            const vids = (treeVids.length ? treeVids : ancestors(frame)).slice().sort();
            vids.forEach((vid) => {
                const button = document.createElement("button");
                button.type = "button";
                button.className = "machine-node" + (vid === frame ? " current" : "");
                button.dataset.vid = vid;
                const prefix = treePrefix(vid);
                if (prefix) {
                    const spine = node("span", "machine-spine", prefix);
                    spine.setAttribute("aria-hidden", "true");
                    button.appendChild(spine);
                }
                button.appendChild(node("span", "", vid));
                if (vid === "/.") button.appendChild(node("span", "machine-tag", "root"));
                if (vid === frame) button.appendChild(node("span", "machine-tag", "current"));
                button.addEventListener("click", () => {
                    if (vid === frame) return;
                    run("move(<" + vid + ">).@~.vid()").then((text) => {
                        const landed = uiVids(text)[0] || vid;
                        frame = landed;
                        setFrame(frame);
                        vidLabel.textContent = frame;
                        loadTree();
                        load();
                    }).catch((problem) => fail(problem && problem.message ? problem.message : String(problem)));
                });
                stack.appendChild(button);
            });
        }

        function tile(item) {
            const el = node("button", "machine-tile");
            el.type = "button";
            el.appendChild(node("span", "machine-tile-label", item.label));
            const stats = [item.metric, item.sub, item.inherited ? "inherited" : null].filter(Boolean).join(" · ");
            el.appendChild(node("span", "machine-tile-metric", stats));
            el.title = "open " + item.label;
            el.addEventListener("click", () => {
                if (api && api.open)
                    api.open({tid: item.tid, vid: item.vid, raw: "", shortTid: item.label});
            });
            return el;
        }

        function setTiles(items) {
            tiles.textContent = "";
            items.forEach((item) => tiles.appendChild(tile(item)));
        }

        /* a frame's unions fall through to the enclosing machine; the rec read only
           shows the frame's own level, so an empty component is shown from the root */
        async function effective(component, empty) {
            const here = base();
            const own = await readJson(here + component);
            if (!here || !empty(own)) return { value: own, inherited: false };
            return { value: await readJson(component), inherited: true };
        }

        async function load() {
            const my = ++token;
            const here = base();
            fail("");
            try {
                const [compilerR, peerR, rosterR, computerR, tracerR, instsetR] = await Promise.all([
                    effective("/compiler", (value) => !value || !value.typer),
                    effective("/network/peer", (value) => !value || !Object.keys(value).length),
                    effective("/network/roster", (value) => !value || !value.machine),
                    effective("/processor/computer", (value) => !value || !Object.keys(value).length),
                    effective("/processor/tracer", (value) => !value || !value.stack),
                    effective("/instset", (value) => !value || !Array.isArray(value.reference) || !value.reference.length)
                ]);
                if (my !== token) return;
                const compiler = compilerR.value;
                const peer = peerR.value;
                const roster = rosterR.value;
                const computer = computerR.value;
                const tracer = tracerR.value;
                const instset = instsetR.value;
                const checks = compiler && compiler.typer && compiler.typer.check ? compiler.typer.check : {};
                const checksOn = Object.keys(checks).filter((key) => checks[key] === true).length;
                const stages = compiler
                    ? ["parser", "rewriter", "resolver", "typer"].filter((key) => compiler[key]).length
                    : 0;
                const peers = peer && typeof peer === "object" ? Object.keys(peer).length : 0;
                const listeners = roster && roster.machine && typeof roster.machine === "object"
                    ? Object.values(roster.machine)
                        .reduce((count, ports) => count + (Array.isArray(ports) ? ports.length : 0), 0)
                    : 0;
                const computers = computer && typeof computer === "object" ? Object.keys(computer).length : 0;
                const traceStacks = tracer && tracer.stack
                    ? Object.keys(tracer.stack).filter((key) => tracer.stack[key] === true).length
                    : 0;
                const sets = instset && Array.isArray(instset.reference) ? instset.reference.length : 0;
                const aliases = instset && instset.prefix && typeof instset.prefix === "object"
                    ? Object.keys(instset.prefix).length
                    : 0;
                setTiles([
                    { label: "instset", metric: sets + " sets", sub: aliases + " aliases", inherited: instsetR.inherited, tid: "/m/instset", vid: here + "/instset" },
                    { label: "compiler", metric: stages + " stages", sub: checksOn + "/" + (Object.keys(checks).length || 5) + " checks on", inherited: compilerR.inherited, tid: "/m/mach/component/compiler", vid: here + "/compiler" },
                    { label: "network", metric: peers + " peers", sub: listeners + " listeners", inherited: peerR.inherited || rosterR.inherited, tid: "/m/mach/component/network", vid: here + "/network" },
                    { label: "processor", metric: computers + " computers", sub: traceStacks + " trace stacks", inherited: computerR.inherited || tracerR.inherited, tid: "/m/mach/component/processor", vid: here + "/processor" }
                ]);
            } catch (problem) {
                if (my !== token) return;
                fail("cannot read " + (here || "/") + ": " + (problem && problem.message ? problem.message : problem));
                setTiles([]);
            }
        }

        form.addEventListener("submit", (ev) => {
            ev.preventDefault();
            const name = input.value.trim().replace(/^\/+/, "");
            if (!name) {
                fail("a frame name is required");
                return;
            }
            fail("");
            run("push(<" + name + ">).@~.vid()").then(async (text) => {
                if (/^fail::/.test(String(text))) {
                    fail(String(text));
                    return;
                }
                const landed = uiVids(text)[0];
                if (!landed) {
                    fail("push failed: " + text);
                    return;
                }
                input.value = "";
                frame = landed;
                setFrame(frame);
                vidLabel.textContent = frame;
                await loadTree();
                load();
            }).catch((problem) => fail(problem && problem.message ? problem.message : String(problem)));
        });

        pop.addEventListener("click", () => {
            if (frame === "/.") {
                fail("the root machine cannot be popped");
                return;
            }
            const before = frame;
            fail("");
            run("move(<" + before + ">).pop().@~.vid()").then(async (text) => {
                if (/^fail::/.test(String(text))) {
                    fail(String(text));
                    return;
                }
                const landed = uiVids(text)[0];
                if (!landed) {
                    fail("pop failed: " + text);
                    return;
                }
                frame = landed;
                setFrame(frame);
                vidLabel.textContent = frame;
                await loadTree();
                load();
            }).catch((problem) => fail(problem && problem.message ? problem.message : String(problem)));
        });

        (async () => {
            try {
                const current = uiVids(await run("@~.vid()"))[0];
                if (current) frame = current;
            } catch (ignored) { }
            setFrame(frame);
            vidLabel.textContent = frame;
            drawTree();
            loadTree();
            load();
        })();
        /* the tree and the tiles are the VM's; frames pushed from the console
           and the tile statistics must both stay fresh (the first load can miss
           while the reader socket is still connecting) */
        setInterval(() => {
            if (box.isConnected && !document.hidden) {
                loadTree();
                load();
            }
        }, 4000);
        return box;
    }

    register("/m/mach/machine", machinePanel);
    register("machine", machinePanel);

    /* network — the machine's membership and reachability boundary: what it knows
       (the roster: discovery sources plus the machine authorities and the
       endpoints each reaches) and what it is connected to (the peers: origin,
       client and tools; the `#` route templates are rendered dimmed). */
    function networkPanel(env, api) {
        const box = node("div", "mpanel network-panel");
        const evalJson = (api && api.evalJson) || (() => Promise.resolve(null));
        const declared = String((env && env.vid) || "/network");
        const dynamic = declared.charAt(0) === "~";
        let frame = (api && api.currentFrame) ? api.currentFrame() : null;
        /* keep the ~ so the network's parental-prefix inheritance resolves against
           the frame: an absolute /a/b/network reads only the child's own slot, but
           the roster and peers are shared, so they live at the parent */
        const base = declared.replace(/\/+$/, "") || "/network";
        const readFresh = (vid) => evalJson((dynamic && frame)
            ? "move(<" + frame + ">).*<" + vid + ">"
            : "*<" + vid + ">");
        let token = 0;

        const head = node("div", "mpanel-head");
        head.appendChild(node("span", "mpanel-k", "network"));
        const pattern = node("span", "mpanel-pattern", base);
        head.appendChild(pattern);
        const summary = node("span", "network-summary", "");
        head.appendChild(summary);
        box.appendChild(head);

        const knownHead = node("div", "network-section");
        box.appendChild(knownHead);
        const knownRows = node("div", "network-rows");
        box.appendChild(knownRows);

        const peerHead = node("div", "network-section");
        box.appendChild(peerHead);
        const peerRows = node("div", "network-peers");
        box.appendChild(peerRows);

        function sectionHead(el, title, sub, count) {
            el.textContent = "";
            el.appendChild(node("span", "network-title", title));
            el.appendChild(node("span", "network-sub", sub));
            if (count != null) el.appendChild(node("span", "network-count", String(count)));
        }

        function row(label, value) {
            const el = node("div", "mpanel-row");
            el.appendChild(node("span", "mpanel-k", label));
            el.appendChild(node("span", "mpanel-v", value));
            return el;
        }

        function endpoints(value) {
            if (value == null) return "—";
            if (Array.isArray(value)) return value.map((item) => brief(item)).join(" · ");
            return brief(value);
        }

        function schemeOf(key, origin) {
            const match = typeof origin === "string" ? origin.match(/^([a-z]+):\/\//) : null;
            if (match) return match[1];
            return String(key).split("/")[0] || "";
        }

        function peerRow(key, value) {
            const member = value && typeof value === "object" ? value : {};
            const client = member.client && typeof member.client === "object" ? member.client : {};
            const template = String(key).includes("#");
            const rowEl = node("div", "network-peer" + (template ? " template" : ""));
            const keyEl = node("span", "network-key", key);
            if (template) keyEl.appendChild(node("span", "network-tag", "template"));
            rowEl.appendChild(keyEl);
            const origin = member.origin || member.host || "—";
            rowEl.appendChild(node("span", "network-target", String(origin)));
            const note = [schemeOf(key, member.origin)];
            const tools = client.tool && typeof client.tool === "object" ? Object.keys(client.tool).length : 0;
            if (tools) note.push(tools + (tools === 1 ? " tool" : " tools"));
            const host = client.host || member.host;
            if (!template && host && String(host) !== String(origin))
                note.push(String(host).replace(/^[a-z]+:\/\/[^/]+/, ""));
            rowEl.appendChild(node("span", "network-note", note.filter(Boolean).join(" · ")));
            /* a ws peer with an mtron endpoint is an instance: the page can wear its data */
            const endpoint = !template ? (client.host || member.host) : null;
            if (!template && endpoint && /^wss?:\/\//.test(String(member.origin || "")) && api && typeof api.view === "function") {
                rowEl.classList.add("instance");
                rowEl.title = "view this instance: " + endpoint;
                rowEl.addEventListener("click", () => api.view(endpoint));
            }
            return rowEl;
        }

        async function load() {
            const my = ++token;
            try {
                const network = await readFresh(declared);
                if (my !== token) return;
                if (network && typeof network === "object" && network.pattern) pattern.textContent = String(network.pattern);
                const roster = await readFresh(base + "/roster");
                if (my !== token) return;
                const machines = roster && roster.machine && typeof roster.machine === "object" ? roster.machine : {};
                const authorities = Object.keys(machines).sort();
                knownRows.textContent = "";
                const discover = roster && roster.discover && typeof roster.discover === "object" ? roster.discover : null;
                if (discover) {
                    const sources = Object.keys(discover).sort();
                    if (sources.length) knownRows.appendChild(row("discover", sources.join(" · ")));
                }
                authorities.forEach((authority) => knownRows.appendChild(row(authority, endpoints(machines[authority]))));
                if (!discover && !authorities.length) knownRows.appendChild(node("div", "mpanel-empty", "no roster"));
                sectionHead(knownHead, "known", "roster", authorities.length);

                const peers = await readFresh(base + "/peer");
                if (my !== token) return;
                const members = peers && typeof peers === "object" ? Object.entries(peers) : [];
                const connected = members.filter(([key]) => !String(key).includes("#"));
                const templates = members.filter(([key]) => String(key).includes("#"));
                summary.textContent = authorities.length + " known · " + connected.length + " connected";
                sectionHead(peerHead, "connected", "peer", connected.length);
                peerRows.textContent = "";
                connected.forEach(([key, value]) => peerRows.appendChild(peerRow(key, value)));
                templates.forEach(([key, value]) => peerRows.appendChild(peerRow(key, value)));
                if (!members.length) peerRows.appendChild(node("div", "mpanel-empty", "no peers"));
            } catch (problem) {
                if (my !== token) return;
                summary.textContent = "—";
                knownRows.textContent = "";
                knownRows.appendChild(node("div", "mpanel-empty", "cannot read the network: " + (problem && problem.message ? problem.message : problem)));
                peerRows.textContent = "";
            }
        }

        sectionHead(knownHead, "known", "roster", null);
        sectionHead(peerHead, "connected", "peer", null);
        if (dynamic) {
            document.addEventListener("metatron:frame", (ev) => {
                if (!box.isConnected) return;
                frame = ev.detail;
                load();
            });
        }
        load();
        /* the roster and the peers move; the api.read cache would pin them, so the
           poll takes the uncached socket read */
        setInterval(() => {
            if (box.isConnected && !document.hidden) load();
        }, 5000);
        return box;
    }

    register("/m/mach/component/network", networkPanel);
    register("network", networkPanel);

    /* memory — the machine's registered spaces: a gateway that lists every space
       at /memory/space as an accordion (the space uri is the header); opening one
       reads the space obj and lays its key/value fields out as an objTree. */
    function memoryPanel(env, api) {
        const box = node("div", "mpanel memory-panel");
        const evalJson = (api && api.evalJson) || (() => Promise.resolve(null));
        const evalPretty = (api && api.eval) || (() => Promise.resolve(""));
        const strip = (t) => String(t == null ? "" : t).replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "").trim();
        const declared = String((env && env.vid) || "/memory");
        const dynamic = declared.charAt(0) === "~";
        let frame = (api && api.currentFrame) ? api.currentFrame() : null;

        /* one space: a native details accordion, lazy-reading its pretty form */
        function space(vid) {
            const details = document.createElement("details");
            details.className = "memory-space";
            const summary = document.createElement("summary");
            summary.className = "memory-space-head";
            summary.textContent = vid;
            details.appendChild(summary);
            const body = node("div", "memory-space-body");
            body.appendChild(spinner("reading space…"));
            details.appendChild(body);

            let loaded = false;
            details.addEventListener("toggle", () => {
                if (!details.open || loaded) return;
                loaded = true;
                (async () => {
                    try {
                        const text = await evalPretty("*<" + vid + ">");
                        const clean = strip(text);
                        body.textContent = "";
                        if (!clean || clean === "noobj" || /^fail::/.test(clean)) {
                            body.appendChild(node("div", "mpanel-empty", "noobj"));
                            return;
                        }
                        const code = node("pre", "memory-space-code", "");
                        if (api && api.paint) api.paint(code, clean, "mtron");
                        else code.textContent = clean;
                        body.appendChild(code);
                    } catch (problem) {
                        body.textContent = "";
                        body.appendChild(node("div", "mpanel-empty", String(problem && problem.message ? problem.message : problem)));
                    }
                })();
            });
            return details;
        }

        async function loadSpaces() {
            const mem = resolveFrame(declared, frame);
            box.textContent = "";
            box.appendChild(spinner("reading spaces…"));
            try {
                const raw = await evalJson("*<" + mem + ">>>space.>>.vid()");
                /* one space answers a single uri (a bare string in JSON), several answer
                   a list/objs (an array) — normalize both to a list of vid strings */
                const list = raw == null ? [] : (Array.isArray(raw) ? raw : [raw]);
                const vids = list.map((v) => String(v)).filter((v) => v && v !== "noobj");
                box.textContent = "";
                if (!vids.length) {
                    box.appendChild(node("div", "mpanel-empty", "no registered spaces"));
                    return;
                }
                vids.sort();
                vids.forEach((vid) => box.appendChild(space(vid)));
            } catch (problem) {
                box.textContent = "";
                box.appendChild(node("div", "mpanel-empty", "cannot read memory spaces: " + (problem && problem.message ? problem.message : problem)));
            }
        }

        if (dynamic) {
            document.addEventListener("metatron:frame", (ev) => {
                if (!box.isConnected) return;
                frame = ev.detail;
                loadSpaces();
            });
        }
        loadSpaces();

        return box;
    }

    register("/m/mach/component/memory", memoryPanel);
    register("memory", memoryPanel);


    /* the web surfaces: the server obj's own route table, not a probe. the obj's
       origin/host is a bind wildcard, so the live host is read from /network/host;
       each route row's target is a metatron address — click it to resolve (read)
       the object it serves; a globe beside it resolves the route as an http url. */
    async function resolveHost(api, value) {
        try {
            const raw = await api.evalJson("*/network/host");
            const text = raw == null ? "" : String(raw).trim();
            if (text && text !== "noobj") return f(text);
        } catch (ignored) { /* no host — leave the header empty */ }
        const fb = value && (value.host || value.origin);
        const text = fb == null ? "" : String(fb).trim();
        if (text && text !== "_") {
            try { return f(text); } catch (ignored) { }
        }
        return null;
    }

    async function routePanel(title, value, api, httpResolve) {
        const box = node("div", "mpanel");
        const head = node("div", "mpanel-head");
        head.appendChild(node("span", "mpanel-k", title));
        const host = node("span", "mpanel-pattern", "");
        head.appendChild(host);
        box.appendChild(head);

        /* the host is read once up front so the globes are real links with an href */
        const hostUri = (api && api.evalJson) ? await resolveHost(api, value) : null;
        if (hostUri) host.textContent = hostUri.toString();

        const routes = value && value.route && typeof value.route === "object" ? value.route : {};
        const paths = Object.keys(routes);
        if (!paths.length) box.appendChild(node("div", "mpanel-empty", "no routes"));

        /* click a route's target -> resolve it: read the object at that address
           and wear it as the standard card (shift-click opens its ?docq) */
        const openTarget = (uri) => {
            const vid = uri.toString();
            if (api && api.open) api.open({vid: vid, raw: vid, shortTid: uri.name() || vid.split("/").pop() || vid});
        };

        paths.forEach((path) => {
            const row = node("div", "mpanel-row");
            row.appendChild(node("span", "mpanel-k", path));
            const v = node("span", "mpanel-v");
            const target = routes[path];
            const text = target == null ? "" : String(target);
            if (text) {
                const button = toElement(f(text), openTarget, "route-target");
                button.textContent = brief(target);
                v.appendChild(button);
            } else {
                v.textContent = brief(target);
            }
            if (httpResolve && hostUri) {
                /* a real <a> so the browser treats it as a link — right-click →
                   open in new tab, middle-click, native navigation. the host's own
                   name path is dropped (origin) before the route path is joined */
                const url = hostUri.origin().scheme("http").port(8777).extend(path).toString();
                const globe = node("a", "route-globe", "\u{1F310}");
                globe.href = url;
                globe.target = "_blank";
                globe.rel = "noopener noreferrer";
                globe.title = url;
                globe.setAttribute("aria-label", "open " + path + " over http");
                v.appendChild(globe);
            }
            row.appendChild(v);
            box.appendChild(row);
        });
        return box;
    }

    register("/m/web/http/http_server", async function (env, api) {
        return routePanel("http_server", await api.json(), api, true);
    });

    register("/m/web/ws/ws_server", async function (env, api) {
        return routePanel("ws_server", await api.json(), api);
    });

    /* the console is the page's console section; the obj supplies its header */
    register("/m/sys/ui/console", async function (env, api) {
        const box = node("div", "mpanel console-panel");
        const section = document.querySelector("#console-card");
        if (!section) {
            box.appendChild(node("div", "mpanel-empty", "no console"));
            return box;
        }
        let label = null;
        try {
            const value = await api.json();
            label = value && (value.header || value.title);
        } catch (ignored) { }
        while (section.firstChild) box.appendChild(section.firstChild);
        if (label) {
            const heading = box.querySelector(".console-head h2, h2");
            if (heading) heading.textContent = String(label);
        }
        return box;
    });

    /* ── tblespace: a spreadsheet over the sql tables ────────────────────────
       the space's schema (a wildcard read of <path>/+) lists one Type per table,
       and its vid is the table name; a table's rows (a wildcard read of
       <path>/<table>/+) are equal-sized recs, so the columns are the first
       row's keys. */
    /* a tblespace's schema read (*<space>/+) is an objs of rels: { vid => type, … },
       or one bare rel when there is a single table. the table name is the last path
       segment of each vid. */
    function schemaTableNames(text) {
        const source = String(text == null ? "" : text).trim();
        if (!source || source === "noobj") return [];
        const names = new Set();
        let depth = 0;
        let relDepth = null;
        let quote = null;
        for (let i = 0; i < source.length; i++) {
            const c = source[i];
            if (quote) { if (c === "\\") i++; else if (c === quote) quote = null; continue; }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (c === "[" || c === "(" || c === "{") { depth++; continue; }
            if (c === "]" || c === ")" || c === "}") { depth--; continue; }
            if (c === "=" && source[i + 1] === ">") {
                if (relDepth === null) relDepth = depth;
                if (depth === relDepth) {
                    let start = i - 1;
                    while (start >= 0 && /\s/.test(source[start])) start--;
                    const end = start + 1;
                    while (start >= 0 && source[start] !== "{" && source[start] !== ",") start--;
                    let vid = source.slice(start + 1, end).trim();
                    if (vid.charAt(0) === "<" && vid.charAt(vid.length - 1) === ">") vid = vid.slice(1, -1);
                    const segs = vid.split("/").filter(Boolean);
                    if (segs.length) names.add(segs[segs.length - 1]);
                }
                i++;
            }
        }
        return Array.from(names);
    }

    register("/m/db/tble/space/tblespace", async function (env, api) {
        const box = node("div", "mpanel tblespace");
        let data = "";
        try {
            const space = await api.json();
            const pattern = space && space.pattern ? String(space.pattern) : "";
            data = pattern.replace(/\/#.*$/, "").replace(/\/+$/, "");
        } catch (ignored) { }
        if (!data) {
            box.appendChild(node("div", "mpanel-empty", "no table path"));
            return box;
        }

        const pick = document.createElement("select");
        pick.className = "tble-pick";
        pick.appendChild(new Option("choose a table…", ""));
        const grid = node("div", "tble-grid");
        grid.appendChild(spinner("discovering tables…"));
        try {
            const schemaText = await api.mtron("*<" + data + "/+/>");
            const tables = schemaTableNames(schemaText);
            grid.textContent = "";
            tables.forEach((name) => pick.appendChild(new Option(name, name)));
            grid.appendChild(node("div", "tble-empty", tables.length ? "pick a table" : "no tables"));
        } catch (problem) {
            grid.textContent = "";
            grid.appendChild(node("div", "tble-empty", "cannot read schema: " + (problem && problem.message ? problem.message : problem)));
        }
        box.appendChild(pick);
        box.appendChild(grid);

        /* the mtron expression bar — pre-filled with the read that populated the
           grid; edit + Enter re-evaluates it (filter, delete, insert, drop, …). */
        const expr = document.createElement("input");
        expr.type = "text";
        expr.className = "tble-expr";
        expr.spellcheck = false;
        expr.autocomplete = "off";
        expr.placeholder = "mtron — e.g. *<path>/<table>/+";
        box.appendChild(expr);

        /* one cell: an auto-at reference becomes a clickable button (opens the
           popup, where it can be pinned); an array is its elements in a row; a
           plain value is text. */
        /* the vid a cell opens: a uri's path, or an !@/!* auto-reference's target. */
        function refVid(obj) {
            if (obj == null) return null;
            if (obj.isUri && obj.isUri()) {
                /* a plain uri is a reference; a refined uri (datetime, …) is a
                   scalar value, not a target to open */
                if (obj.isBaseTid && !obj.isBaseTid()) return null;
                return obj.js.toString();
            }
            if (obj.isInst && obj.isInst()) {
                const text = String(write(obj)).trim();
                const m = /^![@*]\s*(.+)$/.exec(text);
                if (!m) return null;
                let t = m[1].trim();
                if (t.charAt(0) === "<" && t.charAt(t.length - 1) === ">") t = t.slice(1, -1);
                return t;
            }
            return null;
        }

        /* the display text of a cell value — scalars plain, uri unbracketed,
           poly compact via the faithful serializer. */
        function cellText(obj) {
            if (obj == null || (obj.isNoObj && obj.isNoObj())) return "noobj";
            if ((obj.isInt && obj.isInt()) || (obj.isReal && obj.isReal()) || (obj.isBool && obj.isBool())) return String(obj.js);
            if (obj.isStr && obj.isStr()) return obj.js;
            if (obj.isUri && obj.isUri()) {
                if (obj.isBaseTid && !obj.isBaseTid()) return write(obj);
                return obj.js.toString();
            }
            if (obj.isBytes && obj.isBytes()) return "0x" + Array.from(obj.js).map((b) => b.toString(16).padStart(2, "0")).join("");
            const s = write(obj);
            return s.length > 150 ? s.slice(0, 149) + "…" : s;
        }

        function renderCell(obj) {
            if (obj == null || (obj.isNoObj && obj.isNoObj())) return document.createTextNode("noobj");
            if (obj.isLst && obj.isLst()) {
                const span = node("span", "tble-cell");
                obj.js.forEach((item, i) => {
                    if (i) span.appendChild(document.createTextNode(" · "));
                    span.appendChild(renderCell(item));
                });
                return span;
            }
            const vid = refVid(obj);
            if (vid) {
                let label = vid.split("/").pop() || vid;
                let title = vid;
                if (obj.isInst && obj.isInst()) {
                    const ref = String(write(obj)).trim();
                    label = ref.slice(0, 2) + label;
                    title = ref;
                }
                const b = node("button", "tble-ref", label);
                b.type = "button";
                b.title = title + " · click to open · shift-click for ?docq";
                b.addEventListener("click", (ev) => {
                    if (ev.shiftKey) {
                        api.open({ vid: vid + "?docq", shortTid: "docs" });
                    } else {
                        api.open({ vid: vid, raw: write(obj) });
                    }
                });
                return b;
            }
            const span = node("span", "tble-cell tble-plain");
            span.textContent = cellText(obj);
            span.title = "click to inspect";
            span.addEventListener("click", () => openCell(obj));
            return span;
        }

        /* a cell opens its value as an obj modal: a uri opens the obj at that vid;
           a plain value opens its raw value with its tid (e.g. /m/str). */
        function openCell(obj) {
            if (obj == null || (obj.isNoObj && obj.isNoObj())) {
                api.open({raw: "noobj"});
                return;
            }
            const vid = refVid(obj);
            if (vid) {
                api.open({vid: vid, raw: write(obj)});
                return;
            }
            const tid = obj.tid ? String(obj.tid) : null;
            const path = tid ? "/m/" + tid : null;
            api.open({raw: write(obj), shortTid: path ? "value " + path : "value"});
        }

        function renderGrid(result) {
            let rows = null;
            if (result && result.isLst && result.isLst())
                rows = result.js;
            else if (result && result.isRec && result.isRec())
                rows = [result];
            if (!rows || !rows.length) {
                grid.textContent = "";
                grid.appendChild(node("div", "tble-empty", result == null || (result.isNoObj && result.isNoObj()) ? "noobj" : cellText(result)));
                return;
            }
            const columns = [...rows[0].js.keys()].map((k) => k.toString());
            const table = node("table", "tble-sheet");
            const thead = document.createElement("thead");
            const hr = document.createElement("tr");
            hr.appendChild(node("th", "tble-idx", "#"));
            columns.forEach((col) => hr.appendChild(node("th", "", col)));
            thead.appendChild(hr);
            table.appendChild(thead);
            const tbody = document.createElement("tbody");
            rows.forEach((row, idx) => {
                const cells = new Map([...row.js.entries()].map(([k, v]) => [k.toString(), v]));
                const tr = document.createElement("tr");
                tr.appendChild(node("td", "tble-idx", String(idx + 1)));
                columns.forEach((col) => {
                    const td = document.createElement("td");
                    td.appendChild(renderCell(cells.get(col) || null));
                    tr.appendChild(td);
                });
                tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            grid.textContent = "";
            grid.appendChild(table);
        }

        async function runExpr(source) {
            source = String(source == null ? "" : source).trim();
            if (!source) return;
            grid.textContent = "";
            grid.appendChild(spinner("loading…"));
            let result;
            let raw = null;
            try {
                raw = await api.mtron(source);
                let text = String(raw == null ? "" : raw).trim();
                /* the reader may answer with a JSON-quoted string or a one-key
                   JSON envelope — unwrap the string out of either */
                try {
                    const parsed = JSON.parse(text);
                    if (typeof parsed === "string") {
                        text = parsed;
                    } else if (parsed && typeof parsed === "object") {
                        const strings = Object.values(parsed).filter((v) => typeof v === "string");
                        if (strings.length === 1) text = strings[0];
                    }
                } catch (ignored) { }
                if (!text) throw new Error("no answer");
                result = parse(text);
            } catch (problem) {
                grid.textContent = "";
                const detail = raw == null ? "null" : String(raw).slice(0, 120);
                grid.appendChild(node("div", "tble-empty", String(problem && problem.message ? problem.message : problem) + " — raw: " + detail));
                return;
            }
            renderGrid(result);
        }

        function loadTable(name) {
            const source = "*<" + data + "/" + name + "/+>";
            expr.value = source;
            runExpr(source);
        }

        expr.addEventListener("keydown", (ev) => {
            if (ev.key === "Enter") {
                ev.preventDefault();
                runExpr(expr.value);
            }
        });

        pick.addEventListener("change", () => { if (pick.value) loadTable(pick.value); });
        return box;
    });

    /* ── agent::T — a feature tree (spine + branches) ──────────────────────
       an agent is a rec with `feature => [ <feature>::[params] … ]`. each
       feature's tid names its type (chat_feature, tool_feature, …) and its
       value is the params rec. the [+] buttons are the "add" affordances:
       [+] on `feature` adds a feature (from /m/ai/llm/feature/+), [+] on a
       feature adds a param (keys dictated by that feature's type). the add /
       edit writes and the type-driven editors are the next step — here the
       tree is built and labelled. */

    /* a rec field by key string — Map keys are fURI (reference equality), so
       .get(f(key)) misses; walk and match on the name. */
    function recGet(rec, key) {
        if (!rec || !rec.js) return undefined;
        for (const [k, v] of rec.js) if (k.toString() === key) return v;
        return undefined;
    }

    /* the value leaf of a param — scalars plain, uri unbracketed, poly compact. */
    function shortVal(v) {
        if (v == null || v.isNoObj()) return "noobj";
        if (v.isCode()) return "[code]";
        if (v.isInt() || v.isReal() || v.isBool()) return String(v.js);
        if (v.isStr()) return v.js;
        if (v.isUri()) return v.js.toString();
        if (v.isBytes()) return "0x" + Array.from(v.js).map((b) => b.toString(16).padStart(2, "0")).join("");
        const s = write(v);
        return s.length > 70 ? s.slice(0, 69) + "…" : s;
    }

    function treeNode() {
        const li = document.createElement("li");
        const row = node("div", "tree-row");
        li.appendChild(row);
        return { li: li, row: row };
    }

    function treeChildren(li) {
        const ul = node("ul", "tree-children");
        li.appendChild(ul);
        return ul;
    }

    function treeToggle(childrenUl) {
        const b = node("button", "tree-toggle", "−");
        b.type = "button";
        b.title = "fold";
        if (childrenUl) {
            b.addEventListener("click", () => {
                const folded = childrenUl.hidden;
                childrenUl.hidden = !folded;
                b.textContent = folded ? "−" : "+";
                b.title = folded ? "fold" : "unfold";
            });
        } else {
            b.hidden = true;
        }
        return b;
    }

    function treeEdit(title, onClick) {
        const b = node("button", "tree-edit", "✎");
        b.type = "button";
        b.title = title;
        b.addEventListener("click", onClick);
        return b;
    }

    /* the form's values → a mtron rec, typed by the schema (stub: str/uri/ref
       quote as strings, rec/lst pass through raw). */
    function buildRecText(schema, values) {
        const parts = [];
        schema.forEach((f) => {
            const v = values[f.key];
            if (v === undefined || v === null || v === '' || v === false) return;
            let text;
            if (f.kind === 'bool') text = v ? 'true' : 'false';
            else if (f.kind === 'int') text = int(parseInt(v, 10));
            else if (f.kind === 'real') text = real(parseFloat(v));
            else if (f.kind === 'rec' || f.kind === 'lst') text = v;
            else text = quote(String(v));
            parts.push(f.key + '=>' + text);
        });
        return '[' + parts.join(',') + ']';
    }

    /* the pencil: a popup with a type-definition-driven form (stub — save builds
       the mtron and previews it; the write is not wired yet). */
    function openFeatureEditor(featName, feat, api) {
        const modal = document.createElement("div");
        modal.className = "agent-modal";
        const box = node("div", "agent-modal-box");
        box.appendChild(node("h3", "agent-modal-title", "edit " + featName));
        const body = node("div", "agent-modal-body");
        body.appendChild(spinner("reading " + featName + " type…"));
        box.appendChild(body);
        const result = node("pre", "agent-modal-result");
        result.hidden = true;
        box.appendChild(result);
        const actions = node("div", "agent-modal-actions");
        const saveBtn = node("button", "btn btn-save", "save");
        const cancelBtn = node("button", "btn", "cancel");
        actions.appendChild(saveBtn);
        actions.appendChild(cancelBtn);
        box.appendChild(actions);
        modal.appendChild(box);
        document.body.appendChild(modal);

        cancelBtn.addEventListener("click", () => modal.remove());
        modal.addEventListener("click", (ev) => { if (ev.target === modal) modal.remove(); });

        (async () => {
            let schema = [];
            try {
                const typeDef = await api.mtron("*</m/ai/llm/feature/" + featName + ">");
                schema = parseSchema(typeDef);
            } catch (ignored) { }
            body.textContent = "";
            const form = renderForm(schema, feat && feat.isRec() ? feat.js : null);
            body.appendChild(form);
            saveBtn.addEventListener("click", () => {
                const values = collectForm(form);
                const recText = buildRecText(schema, values);
                const expr = "</usr/dr/agent/feature/" + featName + "> -> " + featName + "::" + recText;
                result.hidden = false;
                result.textContent = "would write: " + expr;
            });
        })();
    }

    function agentTree(agent, api) {
        const root = node("ul", "agent-tree");
        const name = shortVal(recGet(agent, "name"));
        const featureList = recGet(agent, "feature");
        const features = featureList && featureList.isLst() ? featureList.js : [];

        const agentNode = treeNode();
        agentNode.row.appendChild(node("span", "tree-label", name && name !== "noobj" ? name : "agent"));
        root.appendChild(agentNode.li);

        const featUl = treeChildren(agentNode.li);
        const featNode = treeNode();
        featNode.row.appendChild(treeToggle(featUl));
        featNode.row.appendChild(node("span", "tree-label", "feature"));
        featUl.appendChild(featNode.li);

        const featsUl = treeChildren(featNode.li);
        features.forEach((feat) => {
            const featName = feat && feat.tid ? feat.tid.name() : "feature";
            const fNode = treeNode();
            const hasParams = feat && feat.isRec();
            const paramsUl = hasParams ? treeChildren(fNode.li) : null;
            fNode.row.appendChild(treeToggle(paramsUl));
            fNode.row.appendChild(node("span", "tree-label", featName));
            fNode.row.appendChild(treeEdit("edit " + featName, () => openFeatureEditor(featName, feat, api)));
            featsUl.appendChild(fNode.li);
            if (hasParams) {
                for (const [k, v] of feat.js) {
                    const pNode = treeNode();
                    pNode.row.appendChild(node("span", "tree-key", k.toString()));
                    pNode.row.appendChild(node("span", "tree-arrow", "=>"));
                    pNode.row.appendChild(node("span", "tree-val", shortVal(v)));
                    paramsUl.appendChild(pNode.li);
                }
            }
        });

        return root;
    }

    register("/m/ai/llm/agent", async function (env, api) {
        const box = node("div", "mpanel agent");
        let vid = String((env && env.vid) || "").trim();
        if (vid.charAt(0) === "<" && vid.charAt(vid.length - 1) === ">") vid = vid.slice(1, -1);
        if (!vid) {
            box.appendChild(node("div", "mpanel-empty", "no agent address"));
            return box;
        }
        box.appendChild(spinner("reading agent…"));
        try {
            const text = await api.mtron("*<" + vid + ">");
            const agent = read(text);
            box.textContent = "";
            box.appendChild(agentTree(agent, api));
        } catch (problem) {
            box.textContent = "";
            box.appendChild(node("div", "mpanel-empty", String(problem && problem.message ? problem.message : problem)));
        }
        return box;
    });

    /* ── type::T — a type's definition: its refinement, desc, predicate,
       constructor and examples. the ?docq data is read field-by-field with
       >> projections, so nothing here parses the doc rec. the predicate's
       fields come from the serializer: parse the isa([…]) code and read its
       rec argument — key (fURI) and the field's type, written back to text. */
    function predicateFields(code) {
        if (!code || code === "noobj") return [];
        const inst = parse(code);
        if (!inst || !inst.isInst()) return [];
        const rec = inst.arg(0);
        if (!rec || !rec.isRec()) return [];
        return [...rec.js.entries()].map(([k, v]) => ({key: k.toString(), type: write(v)}));
    }

    /* highlight code text with the mtron grammar, plain text when hljs is absent */
    function highlightCode(el, text) {
        if (window.hljs && window.hljs.getLanguage && window.hljs.getLanguage("mtron")) {
            try {
                el.innerHTML = window.hljs.highlight(text, {language: "mtron", ignoreIllegals: true}).value;
                typesetMath(el, text);
                return;
            } catch (ignored) { }
        }
        el.textContent = text;
        typesetMath(el, text);
    }

    /* the four presentation forms by name, parallel to FORMS */
    const FORM_LABELS = ["full", "short", "typed", "bare"];
    function formLabel(form) {
        const i = FORMS.indexOf(form);
        return i >= 0 ? FORM_LABELS[i] : FORM_LABELS[0];
    }

    /* render code/signature text in one of the FORMS. no gesture here — the
       toggle is an explicit control (shift/alt/ctrl clicks are the browser's on
       selectable text), so a caller cycles the form itself and re-renders. */
    function paintCode(el, text, form) {
        let shown = String(text == null ? "" : text);
        try { shown = writeCodeForm(shown, form || FORMS[2]); } catch (ignored) { }
        highlightCode(el, shown);
    }

    function paintSig(el, text) {
        paintCode(el, text, FORMS[2]);
    }

    register("type", async function (env, api) {
        const box = node("div", "mpanel type-panel");
        const vid = String((env && env.vid) || "").trim();
        if (!vid) { box.appendChild(node("div", "mpanel-empty", "no type address")); return box; }

        const [decon, doc] = await Promise.all([
            (api && api.evalJson ? api.evalJson("*<" + vid + ">.as(rec::T)") : Promise.resolve(null)).catch(() => null),
            (api && api.evalJson ? api.evalJson("*<" + vid + "?docq>.-<[>>desc,>>args.>-.as(lst::T).>-[,],>>example]") : Promise.resolve(null)).catch(() => null)
        ]);

        const refined = decon && decon.tid ? String(decon.tid).trim() : "";
        const fullVid = decon && decon.vid ? String(decon.vid).trim() : "";
        if (fullVid && api && api.at) api.at(fullVid);
        const ctorText = decon && decon.ctor ? String(decon.ctor).trim() : "";
        const predCode = decon && decon.pred && decon.pred.code ? String(decon.pred.code) : "";
        const predicate = predicateFields(predCode);
        const desc = Array.isArray(doc) ? String(doc[0] == null ? "" : doc[0]).trim() : "";
        const argPairs = Array.isArray(doc) && Array.isArray(doc[1]) ? doc[1] : [];
        const examples = Array.isArray(doc) && Array.isArray(doc[2]) ? doc[2] : [];
        const name = String(vid.split("/").pop() || vid);

        const head = node("div", "type-head");
        head.appendChild(node("span", "type-title", name + "::T"));
        if (refined && refined !== name) {
            head.appendChild(node("span", "type-refines", "refines"));
            const ref = node("button", "type-refines-link", refined);
            ref.type = "button";
            ref.title = "open " + refined;
            ref.addEventListener("click", () => {
                if (api && api.open)
                    api.open({isType: true, vid: refined, raw: refined, shortTid: refined.split("/").pop() || refined});
            });
            head.appendChild(ref);
        }
        box.appendChild(head);

        if (desc && desc !== "noobj") {
            const p = node("p", "type-desc", desc);
            typesetMath(p, desc);
            box.appendChild(p);
        }

        if (predicate.length) {
            box.appendChild(node("div", "type-section", "predicate"));
            const table = node("table", "type-pred");
            const tbody = document.createElement("tbody");
            predicate.forEach((field) => {
                const tr = document.createElement("tr");
                const kd = node("td", "type-pred-key", "");
                paintSig(kd, field.key);
                const td = node("td", "type-pred-type", "");
                paintSig(td, field.type);
                const dd = node("td", "type-pred-desc", "");
                const pair = argPairs.find((p) => Array.isArray(p) && String(p[0]) === field.key);
                dd.textContent = pair && pair.length > 1 ? String(pair[1]) : "";
                tr.appendChild(kd);
                tr.appendChild(td);
                tr.appendChild(dd);
                tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            box.appendChild(table);
        }

        if (ctorText && ctorText !== "noobj") {
            box.appendChild(node("div", "type-section", "constructor"));
            const ctor = node("pre", "type-ctor", "");
            paintSig(ctor, ctorText);
            box.appendChild(ctor);
        }

        if (examples.length) {
            box.appendChild(node("div", "type-section", "example(s)"));
            const ul = node("ul", "type-examples");
            examples.forEach((ex) => {
                const li = document.createElement("li");
                paintSig(li, String(ex));
                ul.appendChild(li);
            });
            box.appendChild(ul);
        }

        return box;
    });

    /* ── docs::T — the documentation attached to an obj (?docq). read the same
       ?docq answer as JSON (desc/dom/rng are str, args a rec of key=>str, example
       a lst of str) and lay it out like the type card's doc half. */
    register("docs", async function (env, api) {
        const box = node("div", "mpanel docs-panel");
        const expr = String((env && env.expr) || "").trim();
        if (!expr) { box.appendChild(node("div", "mpanel-empty", "no doc read")); return box; }

        /* x-mtron is the model (convention 14). the card usually carries the ?docq
           answer already, so prefer it; only a bare expr costs a read. the parsed
           rec is flattened into the same shape the JSON codec used to answer, so
           the layout below is unchanged. */
        let clean = String((env && env.raw) || "").replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "").trim();
        if (!clean && env && env.expr) {
            const text = await (api && api.mtron ? api.mtron(env.expr) : Promise.resolve("")).catch(() => "");
            clean = String(text == null ? "" : text).replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "").trim();
        }
        if (!clean || clean === "noobj") { box.appendChild(node("div", "mpanel-empty", "no documentation")); return box; }

        const flat = (obj) => {
            if (!obj || obj.isNoObj()) return "";
            if (obj.isStr()) return obj.js;
            if (obj.isUri()) return obj.js.toString();
            return write(obj);
        };
        const parsed = parse(clean);
        const doc = parsed.isRec() ? Object.fromEntries([...parsed.js.entries()].map(([k, v]) => {
            if (v.isRec()) return [k.toString(), Object.fromEntries([...v.js.entries()].map(([k2, v2]) => [k2.toString(), flat(v2)]))];
            if (v.isLst()) return [k.toString(), v.js.map(flat)];
            return [k.toString(), flat(v)];
        })) : null;
        if (!doc || typeof doc !== "object") { box.appendChild(node("div", "mpanel-empty", "no documentation")); return box; }

        const desc = doc.desc != null ? String(doc.desc).trim() : "";
        if (desc && desc !== "noobj") {
            const p = node("p", "docs-desc", desc);
            typesetMath(p, desc);
            box.appendChild(p);
        }

        const dom = doc.dom != null ? String(doc.dom).trim() : "";
        const rng = doc.rng != null ? String(doc.rng).trim() : "";
        if (dom || rng) {
            const sig = node("div", "docs-sig");
            if (dom) sig.appendChild(node("span", "docs-sig-dom", dom));
            if (dom && rng) sig.appendChild(node("span", "docs-sig-arrow", "<="));
            if (rng) sig.appendChild(node("span", "docs-sig-rng", rng));
            box.appendChild(sig);
        }

        if (doc.args && typeof doc.args === "object") {
            const entries = Object.entries(doc.args);
            if (entries.length) {
                box.appendChild(node("div", "type-section", "arguments"));
                const table = node("table", "type-pred");
                const tbody = document.createElement("tbody");
                entries.forEach(([key, value]) => {
                    const tr = document.createElement("tr");
                    const kd = node("td", "type-pred-key", "");
                    paintSig(kd, key);
                    const dd = node("td", "type-pred-desc", String(value == null ? "" : value));
                    tr.appendChild(kd);
                    tr.appendChild(dd);
                    tbody.appendChild(tr);
                });
                table.appendChild(tbody);
                box.appendChild(table);
            }
        }

        if (Array.isArray(doc.example) && doc.example.length) {
            box.appendChild(node("div", "type-section", "example(s)"));
            const ul = node("ul", "type-examples");
            doc.example.forEach((ex) => {
                const li = document.createElement("li");
                paintSig(li, String(ex));
                ul.appendChild(li);
            });
            box.appendChild(ul);
        }

        return box;
    });

    /* ── decs::T — the algebraic declaration attached to an obj (?decq). the
       facets are obj/theory/law/inverse/…, each holding a type or code, so they
       are read as x-mtron (not JSON, which drops type values) and split on the
       rec's top-level arrows. */
    register("decs", async function (env, api) {
        const box = node("div", "mpanel decs-panel");
        /* the raw ?decq answer is already on the card (env.raw, read by showRead);
           re-reading env.expr here is a second round-trip, so prefer the raw text */
        let clean = String((env && env.raw) || "").replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "").trim();
        if (!clean && env && env.expr) {
            const text = await (api && api.mtron ? api.mtron(env.expr) : Promise.resolve("")).catch(() => "");
            clean = String(text == null ? "" : text).replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "").trim();
        }
        if (!clean || clean === "noobj") { box.appendChild(node("div", "mpanel-empty", "no declaration")); return box; }

        const decs = parse(clean);
        if (!decs.isRec()) {
            /* not a rec of key=>value facets — a type can be its own decs —
               so show the declaration as-is */
            const raw = node("pre", "type-ctor", "");
            paintSig(raw, clean);
            box.appendChild(raw);
            return box;
        }

        /* browse like the web: evaluate an expression, parse its envelope, and open
           the result as a fresh modal obj card. every uri — key, tid, or leaf value —
           routes through this */
        const browse = (expr) => {
            (api && api.mtron ? api.mtron(expr) : Promise.resolve(""))
                .then((text) => {
                    const clean = String(text == null ? "" : text).replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "").trim();
                    if (!clean) return;
                    const env = Object.assign({raw: clean, expr: expr}, envelope(clean) || {shortTid: expr.split("/").pop() || expr});
                    if (api && api.open) api.open(env);
                }).catch(() => { });
        };

        /* render the rec tree recursively: one key per line, nested recs indented,
           a typed rec's tid beside its key. every uri is a clickable button */
        const render = (parent, obj, depth) => {
            const items = obj.isRec() ? [...obj.js.entries()] : obj.js.map((v, i) => [f(String(i)), v]);
            for (const [k, v] of items) {
                const row = node("div", "decs-row");
                row.style.paddingLeft = (depth * 14) + "px";
                /* toElement makes the button but not its text — set it, like
                   mtron-dom.js's clickable() does */
                const keyEl = toElement(k, (uri) => browse(String(uri)), "obj-link decs-key");
                keyEl.textContent = k.toString();
                row.appendChild(keyEl);
                if (v.isRec() || v.isLst()) {
                    if (v.isRec() && v.tid && !v.isBaseTid()) {
                        const tidEl = toElement(v.tid, (uri) => browse(String(uri)), "obj-link decs-tid");
                        tidEl.textContent = v.tid.toString();
                        row.appendChild(tidEl);
                    }
                    parent.appendChild(row);
                    render(parent, v, depth + 1);
                } else {
                    const expr = write(v);
                    const btn = node("button", "decs-val", expr);
                    btn.type = "button";
                    btn.title = "evaluate " + expr;
                    btn.addEventListener("click", () => browse(expr));
                    row.appendChild(btn);
                    parent.appendChild(row);
                }
            }
        };
        render(box, decs, 0);

        return box;
    });

/* ── compiler::T — the machine's lowering axis, source text to code::T ─────
   a tree: parse → resolve (selector/binder) → rewrite. each stage shows the
   code at that point; the rewrite stage lists the registered rewriters, and
   only those whose application changed the resolved code. everything reads
   application/x-mtron and parses it with the serializer — JSON drops tids. */
function compilerPanel(env, api) {
    const box = node("div", "mpanel compiler-panel");

    const form = node("form", "compiler-form");
    const input = document.createElement("input");
    input.type = "text";
    input.className = "compiler-expr";
    input.spellcheck = false;
    input.autocapitalize = "off";
    input.autocomplete = "off";
    input.placeholder = "1.plus(10).mult(20)";
    input.value = "1.plus(10).mult(20)";
    const runBtn = node("button", "primary compiler-run", "compile");
    runBtn.type = "submit";
    const formBtn = node("button", "ghost compiler-form-btn", formLabel(FULL_FORM));
    formBtn.type = "button";
    formBtn.title = "cycle the code name form (full → short → typed → bare)";
    /* auxiliary: the page's explain mark, not a word — compile is the main action */
    const explainBtn = iconButton("explain", () => {
        const expr = input.value.trim();
        if (!expr) return;
        /* the panel reads env.expr, so it is handed the explain chain */
        api.open({tid: "/m/inst/explain/explanation", vid: null, raw: "", shortTid: "explanation", expr: expr + ".explain()"});
    });
    explainBtn.disabled = true;
    explainBtn.classList.add("compiler-explain");
    form.appendChild(input);
    form.appendChild(formBtn);
    form.appendChild(runBtn);
    form.appendChild(explainBtn);
    box.appendChild(form);

    const tree = node("div", "compiler-tree");
    box.appendChild(tree);

    let busy = false;
    let currentForm = FULL_FORM;

    formBtn.addEventListener("click", () => {
        currentForm = nextForm(currentForm);
        formBtn.textContent = formLabel(currentForm);
        Array.from(tree.querySelectorAll(".cnode-code")).forEach((pre) => {
            if (pre.dataset.mtronText != null) paintCode(pre, pre.dataset.mtronText, currentForm);
        });
    });

    /* one mtron expression, read as x-mtron and parsed (never JSON) */
    function evalObj(expr) {
        return api.mtron(expr).then((text) => parse(String(text == null ? "" : text).trim()));
    }

    /* the code::T inside a pipeline result — each stage answers a lst [code] */
    function codeOf(obj) {
        if (obj && obj.isLst() && obj.js.length === 1) obj = obj.js[0];
        return obj;
    }
    function textOf(obj) {
        const c = codeOf(obj);
        return c ? write(c) : "";
    }

    /* escape a source expression into an mtron '…' string */
    function quote1(s) {
        return "'" + String(s).replace(/\\/g, "\\\\").replace(/'/g, "\\'") + "'";
    }

    function head(mark, label) {
        const h = node("div", "cnode-head");
        h.appendChild(node("span", "cnode-mark", mark));
        h.appendChild(node("span", "cnode-label", label));
        return h;
    }
    /* a stage's code, with the `?` that opens its explanation: every code::T in
       the tree is one click from its table (and its topology) */
    function code(text) {
        const wrap = node("div", "cnode-code-wrap");
        const pre = node("pre", "cnode-code", "");
        pre.dataset.mtronText = text;
        paintCode(pre, text, currentForm);
        wrap.appendChild(pre);
        const badge = explainBadge(text, api);
        if (badge) wrap.appendChild(badge);
        return wrap;
    }
    function branch(h, kids) {
        const root = node("div", "cnode");
        root.appendChild(h);
        if (kids && kids.length) {
            const wrap = node("div", "cnode-kids");
            kids.forEach((k) => wrap.appendChild(k));
            root.appendChild(wrap);
        }
        return root;
    }

    async function compile() {
        const expr = input.value.trim();
        if (!expr || busy) return;
        busy = true;
        runBtn.disabled = true;
        input.disabled = true;
        tree.textContent = "";
        const status = node("div", "compiler-status", "compiling …");
        tree.appendChild(status);

        try {
            const src = "[" + quote1(expr) + "]";

            /* 1. parse — present it the moment it lands */
            const parsed = await evalObj(src + "==[!*/compiler/parser]");
            const parsedText = textOf(parsed);
            tree.appendChild(branch(head("_", "parser"), [code(parsedText || "noobj")]));
            explainBtn.disabled = false;   /* it compiled: now it can be explained */

            /* 2. resolve */
            status.textContent = "resolving …";
            const resolved = await evalObj(src + "==[!*/compiler/parser]==[!*/compiler/resolver]");
            const resolvedText = textOf(resolved);
            /* the binder node reads the component's own map once the resolve has
               run: GenericBinder publishes nothing (its map is local to apply), so
               the node says what it found and what the map would be */
            const binderKids = node("div", "binder-kids");
            tree.appendChild(branch(head("|_", "resolver"), [
                branch(head("|__", "selector"), [code(resolvedText || "noobj")]),
                branch(head("|__", "binder"), [binderKids])
            ]));
            binderKids.appendChild(node("div", "compiler-status", "reading the binder …"));
            api.mtron("*<~/compiler/resolver/binder/generic_binding>").then((text) => {
                binderKids.textContent = "";
                const rows = binderRows(text);
                if (rows.length) {
                    binderKids.appendChild(binderTable(rows));
                } else {
                    binderKids.appendChild(node("div", "cnode-empty",
                        "no generic bound — this chain bound no generic (the binder publishes what its last "
                        + "resolve deduced at ~/compiler/resolver/binder/generic_binding)"));
                }
            }).catch((error) => {
                binderKids.textContent = "";
                binderKids.appendChild(node("div", "cnode-empty", String(error && error.message ? error.message : error)));
            });

            /* 3. the registered rewriters */
            status.textContent = "reading rewriters …";
            const rewriterObj = await evalObj("*/instset/rewrite");
            const names = [];
            if (rewriterObj && rewriterObj.isLst()) {
                rewriterObj.js.forEach((e) => {
                    if (e && e.isInst()) {
                        const n = e.name() ? e.name().toString() : null;
                        if (n) names.push(n);
                    }
                });
            }

            /* the top status has served its purpose; the rewrite churn lives
               inside the rewriter node — where the rules will land — so the
               panel does not jump */
            status.remove();

            /* 4. rewrite — a rule is shown the moment it changes the code */
            const rwRoot = node("div", "cnode");
            rwRoot.appendChild(head("|_", "rewriter"));
            const rwKids = node("div", "cnode-kids");
            rwRoot.appendChild(rwKids);
            tree.appendChild(rwRoot);

            const churn = node("div", "compiler-status", "");
            rwKids.appendChild(churn);

            let changedCount = 0;
            if (resolvedText) {
                for (let i = 0; i < names.length; i++) {
                    const name = names[i];
                    churn.textContent = "rewrite " + (i + 1) + "/" + names.length + " · " + name + " …";
                    try {
                        const out = await evalObj("[" + resolvedText + "]==[!*" + name + "]");
                        const outText = textOf(out);
                        if (outText && outText !== "noobj" && !/^fail::/.test(outText) && outText !== resolvedText) {
                            rwKids.appendChild(branch(head("|_", name), [code(outText)]));
                            changedCount += 1;
                        }
                    } catch (ignored) {
                        /* a rule that will not apply is not an error */
                    }
                }
            }
            churn.remove();
            if (changedCount === 0) {
                rwKids.appendChild(node("div", "cnode-empty", "no rewrite changed the resolved code"));
            }
        } catch (error) {
            status.remove();
            tree.appendChild(code("fail:: " + (error && error.message ? error.message : String(error))));
        } finally {
            busy = false;
            runBtn.disabled = false;
            input.disabled = false;
        }
    }

    form.addEventListener("submit", (ev) => {
        ev.preventDefault();
        compile();
    });

    return box;
}

/* ── the binder's map — a rec of generic => type, as rows ────────────────────
   the binder works with a Map<fURI,fURI> (A => int, B => str): an all-caps
   generic name bound to the type it met. GenericBinder publishes what its last
   resolve deduced into its own rec (ScoringResolver mints one binder per
   compiler), read at ~/compiler/resolver/binder/generic_binding. */
function binderRows(text) {
    const source = String(text == null ? "" : text).trim();
    if (!source || source === "noobj") return [];
    /* the reader loops on a truncated spelling (a half-written `[A=>`), and this
       reads whatever an opt-in probe chose to publish — so only a rec that closes
       is handed to it. */
    if (!/[[{]/.test(source) || !/[\]}]$/.test(source)) return [];
    let rec = null;
    try {
        rec = parse(source);
    } catch (ignored) {
        return [];
    }
    if (!rec || !rec.isRec()) return [];
    return [...rec.js.entries()].map(([key, value]) => ({
        key: key.toString(),
        /* a bound generic names a TYPE, so a uri value reads as `int::T` */
        value: value && value.isUri() ? value.js.toString() + "::T" : (value && value.isStr() ? value.js : write(value))
    }));
}

/* the rows as a table: the generic on the left, the type it bound on the right */
function binderTable(rows) {
    const table = node("table", "type-pred binder-table");
    const tbody = document.createElement("tbody");
    rows.forEach((row) => {
        const tr = document.createElement("tr");
        const key = node("td", "type-pred-key", "");
        paintSig(key, row.key);
        tr.appendChild(key);
        tr.appendChild(node("td", "type-pred-desc", "=> " + row.value));
        tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    return table;
}

/* ── fsspace::T — the machine's file system as a directory tree ─────────────
   LISTED BY ADDRESS, NEVER DEREFERENCED: a directory is
       <mfs:path>.{0,2}>>.take(n)
   — the space's uri spine, ONE read for the ground, its children and its
   grandchildren. so a child that has children is a DIRECTORY, and the rows are
   names only: nothing here reads an obj, and listing therefore never evaluates a
   file. a click is the only read, and a file that is a program is evaluated by it.

   THE SPACE'S NAME IS THE MACHINE'S: booger names its file system mfs:, ginger and
   chibi fs:. the page discovers the machine's fsspaces when it loads (api.fsspaces)
   and this panel offers them; a bare path is prefixed with the chosen scheme. */
function fsspacePanel(env, api) {
    const box = node("div", "mpanel fs-panel");
    const spaces = (api && api.fsspaces ? api.fsspaces() : []) || [];
    const schemeOf = (uri) => {
        const at = String(uri || "").indexOf(":");
        return at > 0 ? String(uri).slice(0, at) : null;
    };
    let scheme = schemeOf(env && env.vid) || (spaces[0] && spaces[0].scheme) || "mfs";

    /* the parent of an address on the spine: one segment up, or the space root (scheme:.) */
    const parentOf = (uri) => {
        const text = String(uri);
        const at = text.indexOf(":");
        const name = text.slice(0, at);
        const path = text.slice(at + 1).replace(/\/+$/, "");
        if (!path || path === ".") return null;
        const cut = path.lastIndexOf("/");
        return name + ":" + (cut > 0 ? path.slice(0, cut) : ".");
    };

    const picker = spaces.length ? document.createElement("select") : null;
    if (picker) {
        picker.className = "fs-space";
        picker.title = "this machine's file systems";
        spaces.forEach((space) => {
            const option = document.createElement("option");
            option.value = space.scheme;
            option.textContent = space.scheme + ":  " + (space.root || space.vid || "");
            picker.appendChild(option);
        });
        picker.value = scheme;
        picker.addEventListener("change", () => {
            scheme = picker.value;
            show(".");
        });
        box.appendChild(picker);
    }

    const form = node("form", "fs-path");
    const input = document.createElement("input");
    input.type = "text";
    input.spellcheck = false;
    input.autocapitalize = "off";
    input.dataset.history = "fs.path";
    input.placeholder = "boot/space/web";
    const go = node("button", "primary fs-go", "go");
    go.type = "submit";
    form.appendChild(input);
    form.appendChild(go);
    box.appendChild(form);
    const note = node("div", "fs-note", "");
    box.appendChild(note);
    const body = node("div", "fs-body");
    box.appendChild(body);

    const groundOf = (given) => {
        const text = String(given == null ? "" : given).trim();
        if (/^[a-z]+:/.test(text)) {
            scheme = schemeOf(text) || scheme;
            return text;
        }
        return scheme + ":" + (text || ".");
    };

    /* one spine read, ONE HOP: the ground and its children, as addresses. `{0,2}` is NOT
       used here: it walks INTO a file (default.mtron/title), which parses the file —
       listing must never do that. so a listing is names only, and whether an entry is a
       directory is asked per click, with the same one-hop read. */
    const spine = async (uri) => {
        try {
            const text = String((await api.mtron("<" + uri + ">.{0,1}>>.take(600)")) || "").trim();
            if (!text || text === "noobj" || /^fail::/.test(text)) return null;
            const value = parse(text);
            if (!value) return null;
            const members = value.isObjs && value.isObjs() ? [...value.js] : [value];
            return members.map((m) => m && m.isUri && m.isUri() ? m.js.toString() : String(m));
        } catch (ignored) {
            return null;
        }
    };

    /* the last path segment; a child of the space root has none, so the name is what
       follows the scheme (mfs:boot -> boot) */
    const nameOf = (uri) => {
        const at = uri.indexOf(":");
        const cut = uri.lastIndexOf("/");
        return cut > at ? uri.slice(cut + 1) : uri.slice(at + 1);
    };

    const entriesOf = async (uri) => {
        const all = await spine(uri);
        if (!all) return null;
        return all
            .filter((child) => child !== uri && parentOf(child) === uri)
            .map((child) => ({uri: child, name: nameOf(child)}))
            .filter((row) => row.name && row.name.charAt(0) !== ".")
            .sort((a, b) => a.name.localeCompare(b.name));
    };

    /* a directory's own one-hop spine holds more than itself; a file's holds only the file.
       the read is the same shape as a listing, so asking costs no parse. */
    const holdsEntries = async (uri) => {
        const all = await spine(uri);
        return !!all && all.length > 1;
    };

    const pick = (row, uri, name) => {
        if (api.select) {
            const tree = row.closest(".fs-tree");
            if (tree) tree.querySelectorAll(".fs-entry.picked").forEach((picked) => picked.classList.remove("picked"));
            row.classList.add("picked");
            api.select(uri);
            return;
        }
        if (api.open) api.open({vid: uri, raw: uri, shortTid: name});
    };

    const rowFor = (name, uri) => {
        const row = node("div", "fs-entry");
        /* a NAME decides first: an entry with an extension is a FILE, never probed, so a
           click on a page is a pick and not a read. a dotless entry is asked once, when
           it is clicked, because only a read can tell a directory from a leaf. */
        if (name.includes(".")) row.dataset.kind = "file";
        const line = node("button", "fs-row", "");
        line.type = "button";
        line.appendChild(node("span", "fs-name", name));
        const children = node("div", "fs-children");
        row.appendChild(line);
        row.appendChild(children);
        let grown = false;
        line.addEventListener("click", async () => {
            if (row.dataset.kind === "file") {
                pick(row, uri, name);
                return;
            }
            if (row.dataset.kind === "dir") {
                row.classList.toggle("open");
                return;
            }
            line.classList.add("busy");
            const dir = await holdsEntries(uri);
            line.classList.remove("busy");
            if (!dir) {
                row.dataset.kind = "file";
                pick(row, uri, name);
                return;
            }
            row.dataset.kind = "dir";
            row.classList.add("open");
            if (grown) return;
            grown = true;
            const rows = await entriesOf(uri);
            if (!rows) {
                children.appendChild(node("div", "mpanel-empty", "no listing"));
                return;
            }
            if (!rows.length) {
                children.appendChild(node("div", "mpanel-empty", "empty directory"));
                return;
            }
            rows.forEach((child) => children.appendChild(rowFor(child.name, child.uri)));
        });
        return row;
    };

    const show = async (given) => {
        const uri = groundOf(given);
        input.value = uri;
        body.textContent = "";
        note.textContent = "listing " + uri + " …";
        const rows = await entriesOf(uri);
        body.textContent = "";
        if (!rows) {
            note.textContent = "";
            body.appendChild(node("div", "mpanel-empty", "no listing at " + uri));
            return;
        }
        note.textContent = rows.length + " entries · addresses only; a click expands or picks";
        if (!rows.length) {
            body.appendChild(node("div", "mpanel-empty", "empty directory"));
            return;
        }
        const tree = node("div", "fs-tree");
        rows.forEach((row) => tree.appendChild(rowFor(row.name, row.uri)));
        body.appendChild(tree);
    };

    form.addEventListener("submit", (ev) => {
        ev.preventDefault();
        show(input.value.trim() || ".");
    });

    return show(env && env.vid ? env.vid : ".").then(() => box);
}

/* ── /m/code — a code::T card ───────────────────────────────────────────────
   a text dump never names its type, so the `?` mark belongs only to explicit code
   interaction panels; a code CARD is already that surface, and there the way to
   explain is a plain button rather than a small mark. the code is drawn in the
   compiler's own form, so a stage and a card read alike. */
function codePanel(env, api) {
    const box = node("div", "mpanel code-panel");

    const head = node("div", "code-head");
    head.appendChild(node("span", "code-kind", "code"));
    box.appendChild(head);

    const explain = node("button", "primary code-explain", "explain");
    explain.type = "button";
    explain.disabled = true;
    explain.title = "explain this code";
    let text = "";
    explain.addEventListener("click", () => {
        if (!text || !api.open) return;
        api.open({tid: "/m/inst/explain/explanation", vid: null, raw: "", shortTid: "explanation", expr: text + ".explain()"});
    });
    head.appendChild(explain);

    const body = node("div", "code-content");
    box.appendChild(body);

    const show = (code) => {
        text = String(code == null ? "" : code).trim();
        body.textContent = "";
        explain.disabled = !text || text === "noobj";
        if (!text || text === "noobj") {
            body.appendChild(node("div", "mpanel-empty", "no code"));
            return;
        }
        const pre = node("pre", "code-body", "");
        paintCode(pre, text, FULL_FORM);
        body.appendChild(pre);
    };

    /* the card may arrive with its code as text (a badge, a stage) or by address */
    const raw = String(env && env.raw ? env.raw : "").trim();
    if (raw) {
        show(raw);
        return box;
    }
    if (env && env.vid) {
        body.appendChild(node("div", "mpanel-empty", "reading " + env.vid + "…"));
        return Promise.resolve(api.mtron("*<" + String(env.vid).replace(/^</, "").replace(/>$/, "") + ">"))
            .then((value) => show(value))
            .catch((error) => {
                body.textContent = "";
                body.appendChild(node("div", "mpanel-empty", String(error && error.message ? error.message : error)));
                return box;
            });
    }
    show("");
    return box;
}

/* ── explanation::T — explain() as a table, its extensions as widgets ───────
   the machine answers explain() with [format, desc, per_inst, ext?]: per_inst is
   the COMPILED chain (the table), desc.rewrite is the SUBMITTED expression's own
   explanation (the second view), and each ext/<name> rec belongs to an extension
   (today: topology), drawn by the renderer registered in explain-flow.js. the
   panel only READS and renders — it never mints an explanation, so the console
   stays the one place an expression is evaluated. */
function explanationPanel(env, api) {
    const box = node("div", "mpanel explain-panel");

    /* the card is a TOOL, not a view of a stored obj: an explanation exists only
       once explain() runs, so the panel carries the expression and asks for one,
       exactly as the compiler card carries the expression it lowers. a card opened
       from a console answer arrives with that answer's expression already in it. */
    const form = node("form", "exp-tool");
    const input = document.createElement("input");
    input.type = "text";
    input.spellcheck = false;
    input.autocapitalize = "off";
    input.autocomplete = "off";
    input.placeholder = "1.plus(2)   ·   start?hop=2(0).{0,10}plus(1).sum()";
    const runButton = node("button", "primary exp-run", "explain");
    runButton.type = "submit";
    form.appendChild(input);
    form.appendChild(runButton);
    box.appendChild(form);
    const content = node("div", "exp-content");
    box.appendChild(content);

    /* the expression a card was opened FROM: an explain() chain minus its marker */
    const sourceOf = (expr) => {
        const text = String(expr == null ? "" : expr).trim();
        const cut = text.lastIndexOf(".explain()");
        return cut > 0 ? text.slice(0, cut).trim() : text;
    };

    /* the machine form is the complete one (the ui form clips, json drops tid/vid):
       read the obj by its vid when it has one, re-run the console's own expression
       when it has not, and fall back to the answer's text. */
    const readOne = async () => {
        /* a card the PAGE mounts stands on a machine address (~, ., /.): a root rec
           is not an explanation, and reading one whole is pointless and expensive,
           so only a real address is asked. */
        const rootAddress = /^(~|\.|\/\.|\/)$/;
        const attempts = [];
        if (env.vid && !rootAddress.test(String(env.vid).trim()))
            attempts.push(() => api.mtron("*<" + String(env.vid).replace(/^</, "").replace(/>$/, "") + ">"));
        if (env.expr) attempts.push(() => api.mtron(String(env.expr)));
        if (env.raw) attempts.push(() => Promise.resolve(env.raw));
        for (const attempt of attempts) {
            try {
                const text = String((await attempt()) || "").trim();
                if (!text || text === "noobj" || /^fail::/.test(text)) continue;
                const model = explanationModel(parse(text));
                if (model) return model;
            } catch (ignored) { /* the next source may answer */ }
        }
        return null;
    };

    const COLS = ["op", "dom", "rng", "args", "f", "desc", "c_dom", "c_rng", "c_out"];
    let model = null;
    let busy = false;

    function td(value, cls) {
        const cell = document.createElement("td");
        if (cls) cell.className = cls;
        cell.textContent = value == null ? "" : String(value);
        return cell;
    }

    /* a dom/rng the machine records as noobj is OMITTED from the rec (domRngValue drops a
       least count of 0), and reading that absent key answers noobj{0} — metatron's own
       spelling. the cell shows it rather than a glyph of mine. */
    function typeCell(value) {
        if (value) return td(value, "exp-type-cell");
        return td("noobj{0}::T", "exp-type-cell");
    }

    /* a coefficient as the rec records it: the LEAST COUNT it admits, so a {*} or
       a {0,3} in the terminal table may read as {0} here; absent is the machine's noobj */
    function coefCell(value) {
        if (value) return td(value, "exp-coef-cell");
        return td("noobj", "exp-coef-cell");
    }

    function argsCell(row) {
        const cell = td(null, "exp-args-cell");
        if (!row.args) return cell;
        const button = document.createElement("button");
        button.type = "button";
        button.className = "exp-args";
        button.textContent = row.args;
        button.title = "click to open these args as a card";
        button.addEventListener("click", (ev) => {
            ev.stopPropagation();
            if (api.open) api.open({tid: null, vid: null, raw: row.args, shortTid: "args"});
        });
        cell.appendChild(button);
        return cell;
    }

    function tableOf(m) {
        const wrap = node("div", "exp-table-wrap");
        const table = document.createElement("table");
        table.className = "exp-table";
        const thead = document.createElement("thead");
        const headRow = document.createElement("tr");
        COLS.forEach((col) => {
            const th = document.createElement("th");
            th.textContent = col;
            if (col === "desc") th.title = "the instruction's form (Inst.Form)";
            if (col.indexOf("c_") === 0) th.title = "the least count explain() records; the terminal table can show a wider coefficient ({*}, {0,3})";
            headRow.appendChild(th);
        });
        thead.appendChild(headRow);
        table.appendChild(thead);
        const tbody = document.createElement("tbody");
        m.rows.forEach((row) => {
            const tr = document.createElement("tr");
            tr.appendChild(td(row.op, "exp-op"));
            tr.appendChild(typeCell(row.dom));
            tr.appendChild(typeCell(row.rng));
            tr.appendChild(argsCell(row));
            tr.appendChild(td(row.f, "exp-f"));
            tr.appendChild(td(row.form, "exp-form"));
            tr.appendChild(coefCell(row.cDom));
            tr.appendChild(coefCell(row.cRng));
            tr.appendChild(coefCell(row.cOut));
            tr.addEventListener("click", () => tr.classList.toggle("sel"));
            tbody.appendChild(tr);
        });
        table.appendChild(tbody);
        wrap.appendChild(table);
        return wrap;
    }

    /* one section per extension the machine attached; a registered renderer draws
       it, and an unknown one still shows its rec rather than disappearing */
    function mountExtensions() {
        const exts = node("div", "exp-exts");
        if (!model.ext.length) {
            exts.appendChild(node("div", "exp-noext", "no explain extensions — a chain that fans out nowhere has no topology"));
            return exts;
        }
        model.ext.forEach((entry) => {
            const section = node("section", "exp-ext");
            const head = node("div", "exp-ext-head");
            head.appendChild(node("span", "exp-ext-name", entry.name));
            head.appendChild(node("span", "exp-ext-kind", "extension"));
            section.appendChild(head);
            const factory = explainExtension(entry.name);
            if (!factory) {
                section.appendChild(node("div", "mpanel-empty", "no renderer registered for the '" + entry.name + "' extension"));
                const raw = node("pre", "exp-raw", write(entry.rec));
                section.appendChild(raw);
            } else {
                try {
                    Promise.resolve(factory(entry.rec, api, {model: model, env: env}))
                        .then((rendered) => {
                            if (rendered) section.appendChild(rendered);
                        })
                        .catch((error) => {
                            section.appendChild(node("div", "mpanel-empty", String(error && error.message ? error.message : error)));
                        });
                } catch (error) {
                    section.appendChild(node("div", "mpanel-empty", String(error && error.message ? error.message : error)));
                }
            }
            exts.appendChild(section);
        });
        return exts;
    }

    /* one explanation, rendered: the desc head, the view switch, the table and its
       extensions. called for the card's own answer and again for every expression a
       reader asks through the field. */
    function render(loaded) {
        model = loaded;
        content.textContent = "";
        if (!model) {
            content.appendChild(node("div", "mpanel-empty", "no explanation — type an expression above and press explain"));
            return;
        }
        const head = node("div", "exp-head");
        const type = node("span", "exp-type");
        /* an absent desc.dom/rng is the machine's noobj{0}: show its spelling, not a glyph */
        type.appendChild(node("code", "exp-dom", model.desc.dom || "noobj{0}::T"));
        type.appendChild(node("span", "exp-arrow", "→"));
        type.appendChild(node("code", "exp-rng", model.desc.rng || "noobj{0}::T"));
        head.appendChild(type);
        head.appendChild(node("span", "exp-insts", (model.desc.insts == null ? model.rows.length : model.desc.insts) + " insts"));
        content.appendChild(head);

        const views = node("div", "exp-views");
        const body = node("div", "exp-body");
        const tabs = [];
        const show = (name) => {
            const which = name === "submitted" && model.submitted ? model.submitted : model;
            tabs.forEach((tab) => tab.setAttribute("aria-selected", String(tab.dataset.expView === name)));
            body.replaceChildren(tableOf(which));
        };
        const addTab = (name, label, enabled) => {
            const tab = document.createElement("button");
            tab.type = "button";
            tab.className = "exp-tab";
            tab.dataset.expView = name;
            tab.textContent = label;
            tab.disabled = !enabled;
            tab.addEventListener("click", () => show(name));
            views.appendChild(tab);
            tabs.push(tab);
        };
        addTab("compiled", "compiled", true);
        addTab("submitted", "submitted", !!model.submitted);
        content.appendChild(views);
        content.appendChild(body);
        content.appendChild(mountExtensions());
        show("compiled");
    }

    /* ask the machine to explain the field's expression */
    async function explainFrom(expr) {
        const source = sourceOf(expr);
        if (!source || busy) return;
        busy = true;
        runButton.disabled = true;
        input.disabled = true;
        content.textContent = "";
        content.appendChild(node("div", "compiler-status", "explaining " + source + " …"));
        try {
            const text = String((await api.mtron(source + ".explain()")) || "").trim();
            if (!text || text === "noobj" || /^fail::/.test(text)) throw new Error(text || "no answer");
            const next = explanationModel(parse(text));
            if (!next) throw new Error("not an explanation: " + text.slice(0, 120));
            render(next);
        } catch (error) {
            content.textContent = "";
            content.appendChild(node("div", "mpanel-empty", String(error && error.message ? error.message : error)));
        } finally {
            busy = false;
            runButton.disabled = false;
            input.disabled = false;
        }
    }

    form.addEventListener("submit", (ev) => {
        ev.preventDefault();
        explainFrom(input.value);
    });

    /* a console answer seeds the field with the expression it explained */
    const seeded = sourceOf(env.expr);
    if (seeded) input.value = seeded;
    return readOne().then((loaded) => {
        render(loaded);
        return box;
    });
}

register("/m/sys/space/fsspace", fsspacePanel);
register("fsspace", fsspacePanel);

register("/m/code", codePanel);
register("code", codePanel);

register("/m/inst/explain/explanation", explanationPanel);
register("explanation", explanationPanel);

register("/m/mach/component/compiler", compilerPanel);
register("compiler", compilerPanel);

export { register, registerParent, resolve, parentOf, panels, parents, binderRows };
