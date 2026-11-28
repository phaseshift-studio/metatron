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
import { toElement, iconButton, ICONS } from './mtron-dom.js';
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
        /* A TABLE ANSWERS A REC: the rewrite table is { name => inst }, and each rule's
           body is code. writing the rec put every rule's code in ONE cell, and the
           highlighter hung on it — the browser froze on the [rewrite] click. the NAME is
           the signature; only non-inst values fall back to their written form. */
        if (parsed.isRec()) {
            for (const [key, value] of parsed.js) {
                if (value && value.isInst && value.isInst()) out.push(key.toString());
                else {
                    const sig = signatureOf(value);
                    if (sig) out.push(sig);
                }
            }
            return out;
        }
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
        /* THE INSTSET IS THE MACHINE'S OWN DATA (x-mtron), not JSON: JSON drops tids and
           hands each q entry back as an object of plain strings, which is exactly the
           brace dump this panel used to print. the serializer keeps the types. */
        let rec = null;
        try {
            const text = String((await api.pretty()) || "").trim();
            if (text && text !== "noobj" && !/^fail::/.test(text)) rec = parse(text);
        } catch (ignored) {
        }
        if (!rec || !rec.isRec || !rec.isRec()) {
            box.appendChild(node("div", "mpanel-empty", "no instset value"));
            return box;
        }
        const at = (name) => {
            for (const [key, value] of rec.js) if (key.toString() === name) return value;
            return null;
        };
        const head = node("div", "mpanel-head");
        head.appendChild(node("span", "mpanel-k", "instset"));
        head.appendChild(node("span", "mpanel-pattern", String(at("pattern") || "")));
        box.appendChild(head);
        const qrec = at("q");
        const q = qrec && qrec.isRec && qrec.isRec() ? [...qrec.js] : [];
        box.appendChild(node("div", "mpanel-count", q.length + (q.length === 1 ? " q" : " qs")));
        q.forEach(([key, value]) => {
            const row = node("div", "mpanel-row");
            row.appendChild(node("span", "mpanel-k", String(key)));
            const cell = node("span", "mpanel-v");
            const code = write(value);
            if (api && api.paint) api.paint(cell, code, "mtron");
            else cell.textContent = code;
            row.appendChild(cell);
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
        input.placeholder = "push a frame… (blank mints one; a call runs as-is)";
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
                const [compilerR, peerR, rosterR, listenerR, computerR, tracerR, instsetR] = await Promise.all([
                    effective("/compiler", (value) => !value || !value.typer),
                    effective("/network/peer", (value) => !value || !Object.keys(value).length),
                    effective("/network/roster", (value) => !value || !value.machine),
                    /* the LISTENERS are the machine's own server sockets, not the roster's
                       discovery entries: the listener table (/network/listener) has one row
                       per bound socket, so its size is the count */
                    effective("/network/listener", (value) => !value || !Object.keys(value).length),
                    effective("/processor/computer", (value) => !value || !Object.keys(value).length),
                    effective("/processor/tracer", (value) => !value || !value.stack),
                    effective("/instset", (value) => !value || !Array.isArray(value.reference) || !value.reference.length)
                ]);
                if (my !== token) return;
                const compiler = compilerR.value;
                const peer = peerR.value;
                const roster = rosterR.value;
                const listener = listenerR.value;
                const computer = computerR.value;
                const tracer = tracerR.value;
                const instset = instsetR.value;
                const checks = compiler && compiler.typer && compiler.typer.check ? compiler.typer.check : {};
                const checksOn = Object.keys(checks).filter((key) => checks[key] === true).length;
                const stages = compiler
                    ? ["parser", "rewriter", "resolver", "typer"].filter((key) => compiler[key]).length
                    : 0;
                const peers = peer && typeof peer === "object" ? Object.keys(peer).length : 0;
                const listeners = listener && typeof listener === "object" ? Object.keys(listener).length : 0;
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
                    { label: "network", metric: peers + " peers", sub: listeners + " listeners", inherited: peerR.inherited || rosterR.inherited || listenerR.inherited, tid: "/m/mach/component/network", vid: here + "/network" },
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
            const raw = input.value.trim();
            const name = raw.replace(/^\/+/, "");
            fail("");
            /* THE FIELD TAKES A SEGMENT OR A CALL: a bare name is wrapped as push(<name>);
               an EMPTY field is push() — the machine mints a uuid; anything already
               written as a call (`push(c)`, a pasted expression) is evaluated as-is, not
               wrapped into a uri literal (that is the `push(<push(c)>)` parse error). */
            const made = !raw ? "push()" : (/[()<>\s]/.test(raw) ? raw : "push(<" + name + ">)");
            const expr = made.indexOf("@~.vid()") >= 0 ? made : made + ".@~.vid()";
            run(expr).then(async (text) => {
                if (/^fail::/.test(String(text))) {
                    fail(String(text));
                    return;
                }
                /* a frame's vid is a PATH: an `at` expression's answer or a quoted uri
                   is not one, and putting it in the frame would drag every later
                   evaluation (and the tab's title) to a place that is not a machine */
                const landed = uiVids(text).find((vid) => /^\/[A-Za-z0-9_.\/-]+$/.test(vid));
                if (!landed) {
                    fail("push returned no usable frame: " + text);
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
           while the reader socket is still connecting) — one refresh at a time,
           backing off when the machine is slow */
        pollPanel(box, async () => {
            await loadTree();
            await load();
        }, 8000);
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
        /* THE NETWORK IS READ AS THE MACHINE'S OWN DATA (x-mtron), never JSON: JSON drops
           tids and hands every nested rec back as one string, which is why a roster row
           read as a brace dump instead of the endpoints the machine carries. */
        const evalMtron = (api && api.mtron) || (() => Promise.resolve(""));
        const plain = (obj) => {
            if (obj == null) return undefined;
            if (typeof obj !== "object") return obj;
            if (obj.isNoObj && obj.isNoObj()) return null;
            if (obj.isUri && obj.isUri()) return obj.js.toString();
            if (obj.isStr && obj.isStr()) return obj.js;
            if (obj.isInt && obj.isInt()) return obj.js;
            if (obj.isReal && obj.isReal()) return obj.js;
            if (obj.isBool && obj.isBool()) return obj.js;
            if (obj.isLst && obj.isLst()) return obj.js.map(plain);
            if (obj.isRec && obj.isRec()) {
                const rec = {};
                for (const [key, value] of obj.js) rec[key.toString()] = plain(value);
                return rec;
            }
            return obj.js;
        };
        const declared = String((env && env.vid) || "/network");
        const dynamic = declared.charAt(0) === "~";
        let frame = (api && api.currentFrame) ? api.currentFrame() : null;
        /* keep the ~ so the network's parental-prefix inheritance resolves against
           the frame: an absolute /a/b/network reads only the child's own slot, but
           the roster and peers are shared, so they live at the parent */
        const base = declared.replace(/\/+$/, "") || "/network";
        const readFresh = async (vid) => {
            const expr = (dynamic && frame)
                ? "move(<" + frame + ">).*<" + vid + ">"
                : "*<" + vid + ">";
            const text = String((await evalMtron(expr)) || "").trim();
            if (!text) return null;
            try {
                return plain(parse(text));
            } catch (ignored) {
                return null;
            }
        };
        let token = 0;

        const head = node("div", "mpanel-head");
        head.appendChild(node("span", "mpanel-k", "network"));
        const pattern = node("span", "mpanel-pattern", base);
        head.appendChild(pattern);
        const summary = node("span", "network-summary", "");
        head.appendChild(summary);
        box.appendChild(head);

        /* THE PREFIX TABLE COMES FIRST: it is the frame's own address book — the
           `authority => endpoint` rows a uri is resolved through (the table the
           network's prefixes() door answers), so it explains every row below it */
        const prefixHead = node("div", "network-section");
        box.appendChild(prefixHead);
        const prefixRows = node("div", "network-rows");
        box.appendChild(prefixRows);

        /* peers before the roster: what this machine is CONNECTED to is more immediate
           than what it merely knows, and the prefix table above both explains them */
        const peerHead = node("div", "network-section");
        box.appendChild(peerHead);
        const peerRows = node("div", "network-peers");
        box.appendChild(peerRows);

        const knownHead = node("div", "network-section");
        box.appendChild(knownHead);
        const knownRows = node("div", "network-rows");
        box.appendChild(knownRows);

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

        /* A MACHINE'S REACH IS NESTED: a protocol (mtron, mcp), each reachable over
           transports (ws, http, https), each transport at one or more uris. render that
           nesting — protocol, transport, uri — instead of flattening it into one line:
           "mtron / ws => ws://host:8555/mtron, mcp => https://…, ws://…, http://…". */
        function endpointsOf(value) {
            const table = node("div", "network-endpoints");
            /* THE TRANSPORT SAYS WHICH CLIENT THE ADDRESS IS FOR: a `mcp` endpoint is
               reached by mcp_client, a `ws` one by ws_client, and the modal shows the
               rec the machine builds for that host — mcp_client::[host=><ws://…>]. */
            const clientTypeOf = (transport, uri) => {
                const t = String(transport || "").toLowerCase();
                if (t === "mcp") return "mcp_client";
                if (t === "ws" || t === "wss") return "ws_client";
                const scheme = /^([a-z]+):\/\//i.exec(String(uri || ""));
                return (scheme ? scheme[1].toLowerCase() : "http") + "_client";
            };
            const line = (protocol, transport, uri) => {
                const el = node("div", "network-endpoint");
                el.appendChild(node("span", "network-protocol", protocol || ""));
                el.appendChild(node("span", "network-transport", transport || ""));
                const shown = uri == null ? "" : String(uri);
                const uriEl = node("span", "network-uri", shown);
                uriEl.title = shown;
                if (/^[a-z]+:\/\//i.test(shown) && !/[#{}]/.test(shown)
                    && api && typeof api.open === "function") {
                    const client = clientTypeOf(transport, shown);
                    uriEl.classList.add("door");
                    uriEl.title = "open " + client + " for this address";
                    uriEl.addEventListener("click", async (ev) => {
                        ev.stopPropagation();
                        /* THE CONSOLE'S OWN IDIOM, verbatim:
                             map([host=><ws://…>].as(ws_client::T))
                           `map` APPLIES the construction — that is what dials and mints the
                           peer at <frame>/network/peer. the bare literal
                           `ws_client::[host=><…>]` only annotates: it answered the same rec
                           and registered nothing (the difference Marko pointed at). when the
                           page console sits in a pushed frame, the move rides along — the
                           call seam is one dot (`move(/b).map(…)`). */
                        const frame = (typeof api.currentFrame === "function") ? api.currentFrame() : null;
                        const code = "map([host=><" + shown + ">].as(" + client + "::T))";
                        const expr = (frame && frame !== "/." && frame !== "/")
                            ? "move(" + frame + ")." + code
                            : code;
                        /* A DOOR DIALS — it does not open a modal: the construction
                           registers the peer, then the panel re-reads so the new member
                           shows up under `peer` straight away. */
                        uriEl.classList.add("busy");
                        try {
                            await api.mtron(expr);
                        } catch (ignored) {
                        }
                        uriEl.classList.remove("busy");
                        await load();
                        document.dispatchEvent(new CustomEvent("metatron:network-changed"));
                    });
                }
                el.appendChild(uriEl);
                table.appendChild(el);
            };
            const uris = (target) => (Array.isArray(target) ? target : [target]).filter((uri) => uri != null);
            const isRec = (v) => v != null && typeof v === "object" && !Array.isArray(v);
            if (value == null) {
                line("", "", "");
                return table;
            }
            if (typeof value === "string") {
                line("", "", value);
                return table;
            }
            if (isRec(value)) {
                const entries = Object.entries(value);
                const protocols = entries.filter(([, transports]) => isRec(transports));
                if (protocols.length) {
                    /* the protocol is written ONCE, beside its first transport: every
                       further line of that protocol leaves the cell blank, so the group
                       reads as "mtron: ws at …, mcp at …, …, …" */
                    protocols.forEach(([protocol, transports]) => {
                        let named = false;
                        Object.entries(transports).forEach(([transport, target]) => {
                            let carried = false;
                            uris(target).forEach((uri) => {
                                line(named ? "" : protocol, carried ? "" : transport, uri);
                                named = true;
                                carried = true;
                            });
                        });
                    });
                    return table;
                }
                entries.forEach(([transport, target]) => {
                    uris(target).forEach((uri) => line("", transport, uri));
                });
                return table;
            }
            uris(value).forEach((uri) => line("", "", uri));
            return table;
        }

        /* a row whose value is the endpoint nesting, not a one-line summary */
        function valueRow(label, value) {
            const el = node("div", "mpanel-row");
            el.appendChild(node("span", "mpanel-k", label));
            const cell = node("span", "mpanel-v");
            cell.appendChild(endpointsOf(value));
            el.appendChild(cell);
            return el;
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
            keyEl.title = key;
            rowEl.appendChild(keyEl);
            /* the tag has a TRACK of its own, between key and target, so every row's tag
               starts at the same x instead of trailing the key's varying width */
            if (template) {
                rowEl.appendChild(node("span", "network-tag", "template"));
            } else {
                /* WHICH SIDE DIALED: Network.Helper.mintPeer records `host` only when the
                   call site HAS a dialable address, and leaves it null on the inbound side
                   (an ephemeral client port is not a place to dial back to). host present
                   = outgoing (we dialed), host absent = incoming (it dialed us). */
                const outbound = member.host != null;
                /* the class carries the panel's prefix: a bare `out`/`in` would collide
                   with the console's log-line tones (.out is a padded block) */
                const dir = node("span", "network-tag network-dir network-dir-" + (outbound ? "out" : "in"),
                    outbound ? "out" : "in");
                dir.title = outbound
                    ? "outgoing: this machine dialed the peer"
                    : "incoming: the peer dialed this machine (no dialable host)";
                rowEl.appendChild(dir);
            }
            const origin = member.origin || member.host || "—";
            const targetEl = node("span", "network-target", String(origin));
            targetEl.title = String(origin);
            rowEl.appendChild(targetEl);
            const tools = client.tool && typeof client.tool === "object" ? Object.keys(client.tool).length : 0;
            const noteEl = node("span", "network-note");
            const part = (el) => {
                if (noteEl.childNodes.length) noteEl.appendChild(document.createTextNode(" · "));
                noteEl.appendChild(el);
            };
            part(document.createTextNode(schemeOf(key, member.origin)));
            /* the tool count is COUNT + icon, not the words "6 tools": the note is a row of
               signs, and a wrench says it in less width than a word */
            if (tools) {
                const toolTag = node("span", "network-tools");
                toolTag.title = tools + (tools === 1 ? " tool" : " tools");
                toolTag.appendChild(node("span", "network-tools-count", String(tools)));
                const glyph = node("span", "network-tools-icon", "");
                glyph.innerHTML = ICONS.tools.mark;
                toolTag.appendChild(glyph);
                part(toolTag);
            }
            const host = client.host || member.host;
            if (!template && host && String(host) !== String(origin))
                part(document.createTextNode(String(host).replace(/^[a-z]+:\/\/[^/]+/, "")));
            rowEl.appendChild(noteEl);
            /* A PEER CAN BE HUNG UP: writing noobj at its key retracts the member (the
               socket closes, the supervisor stops redialing it) — the panel refreshes so
               the row goes away at once. the KEY is the row's own name. */
            /* a template (`http/#`) is a SURFACE, not a connection: retracting it would
               take the route template away, so it gets no hang-up */
            if (!template) {
                const close = node("button", "network-close", "×");
                close.type = "button";
                close.title = "close this connection";
                close.addEventListener("click", async (ev) => {
                    ev.stopPropagation();
                    close.disabled = true;
                    /* THE CLOSING RECIPE (Marko, fully tested):
                         move(/a)../a/network/peer/<key>.-<[_ * client.@_.>>=noobj,_.@_.>>=noobj]
                       `.-<[…]` applies the two writes to the member: its client first, then
                       the member itself. the spaces around `*` matter (multiply, not deref),
                       and the machine rides in the move so the write lands at that level.
                       At the root there is nothing to move: the absolute path is enough. */
                    const machine = (dynamic && frame) ? String(frame).replace(/\/+$/, "") : "";
                    const peerPath = (machine || "") + base.replace(/^~/, "") + "/peer/" + key;
                    const recipe = peerPath + ".-<[_ * client.@_.>>=noobj,_.@_.>>=noobj]";
                    const expr = machine ? "move(" + machine + ").." + recipe : recipe;
                    try {
                        await api.mtron(expr);
                    } catch (ignored) {
                    }
                    await load();
                    document.dispatchEvent(new CustomEvent("metatron:network-changed"));
                });
                rowEl.appendChild(close);
            }
            /* THE PEER'S ANALYSIS: the socket standing at its origin answers ?analq — a
               read, never a dial — and the modal wears it with the analysis panel */
            const chart = node("button", "network-chart", "📊");
            chart.type = "button";
            chart.title = "open this peer's analysis";
            chart.setAttribute("aria-label", "open this peer's analysis");
            chart.addEventListener("click", async (ev) => {
                ev.stopPropagation();
                /* THE SOCKET STANDING AT THE PEER'S ORIGIN answers ?analq (a read, never
                   a dial). If nothing answers there — a peer whose socket is not a local
                   server — the machine's own analysis is the honest fallback and the
                   modal says which one it is showing. */
                const origin = member.origin || member.host;
                const own = "/network/analyzer/analysis";
                const read = async (expr) => {
                    try {
                        const text = String((await api.mtron(expr)) || "").trim();
                        return text && text !== "noobj" && !/^fail::/.test(text) ? text : "";
                    } catch (ignored) {
                        return "";
                    }
                };
                let address = origin ? String(origin).replace(/\/+$/, "") + "/#?analq" : own;
                let raw = origin ? await read("*<" + address + ">") : "";
                if (!raw) {
                    address = own;
                    raw = await read("*<" + own + ">");
                }
                if (api && api.open)
                    api.open({tid: "/m/isa/mach/analysis", vid: address, raw, shortTid: "analysis"});
            });
            /* the note column is the INTERACTIONS column: what this peer offers (its tools)
               and what the page can ask of it (its analysis) live together */
            noteEl.appendChild(chart);
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
                const prefixes = await readFresh(base + "/prefix");
                if (my !== token) return;
                const prefixRowsOf = prefixes && typeof prefixes === "object" ? Object.entries(prefixes) : [];
                sectionHead(prefixHead, "prefix", "resolved", prefixRowsOf.length);
                prefixRows.textContent = "";
                prefixRowsOf
                    .sort(([a], [b]) => String(a).localeCompare(String(b)))
                    .forEach(([key, value]) => prefixRows.appendChild(valueRow(String(key), value)));
                if (!prefixRowsOf.length) prefixRows.appendChild(node("div", "mpanel-empty", "no prefixes"));
                const roster = await readFresh(base + "/roster");
                if (my !== token) return;
                const machines = roster && roster.machine && typeof roster.machine === "object" ? roster.machine : {};
                const authorities = Object.keys(machines).sort();
                knownRows.textContent = "";
                const discover = roster && roster.discover && typeof roster.discover === "object" ? roster.discover : null;
                authorities.forEach((authority) => knownRows.appendChild(valueRow(authority, machines[authority])));
                /* discover is how the names got here, so it closes the section it explains */
                if (discover) {
                    const sources = Object.keys(discover).sort();
                    if (sources.length) knownRows.appendChild(row("discover", sources.join(" · ")));
                }
                if (!discover && !authorities.length) knownRows.appendChild(node("div", "mpanel-empty", "no roster"));
                sectionHead(knownHead, "roster", "known", authorities.length);

                const peers = await readFresh(base + "/peer");
                if (my !== token) return;
                const members = peers && typeof peers === "object" ? Object.entries(peers) : [];
                const connected = members.filter(([key]) => !String(key).includes("#"));
                const templates = members.filter(([key]) => String(key).includes("#"));
                /* the summary follows the section order below it: what is connected, then
                   what is known — each number stays with its own word */
                summary.textContent = connected.length + " connected · " + authorities.length + " known";
                sectionHead(peerHead, "peer", "connected", connected.length);
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

        sectionHead(prefixHead, "prefix", "resolved", null);
        sectionHead(knownHead, "roster", "known", null);
        sectionHead(peerHead, "peer", "connected", null);
        if (dynamic) {
            document.addEventListener("metatron:frame", (ev) => {
                if (!box.isConnected) return;
                frame = ev.detail;
                load();
            });
        }
        load();
        /* the roster and the peers move; the api.read cache would pin them, so the
           poll takes the uncached socket read — one at a time, backing off when the
           machine is slow */
        /* a panel read is a COMPILE on the machine (0.3-0.6 s on a dev box, more when it is
           busy), so the tick is slow on purpose: the network's membership rarely changes,
           and one page polling it every 5 s is what kept a loaded machine behind */
        /* ONE MACHINE, SEVERAL MOUNTS: the card and the obj modal are two panels reading
           the same network, so a change seen by one is announced to all — otherwise a
           door dialed in the modal refreshes the modal but leaves the card's list stale
           (and vice versa). */
        document.addEventListener("metatron:network-changed", () => {
            if (box.isConnected) load();
        });
        pollPanel(box, () => load(), 12000);
        return box;
    }

    /* A PANEL'S POLL MUST NEVER STACK: one read in flight, and when the machine is slow
       the next tick is pushed back (5 s → 10 s → 20 s → 40 s → 60 s) instead of queueing
       another read behind the one that is still running. a labouring machine otherwise
       drowns in its own polls — which is what "the page hangs" looks like. */
    function pollPanel(box, task, fast) {
        let delay = fast || 5000;
        let timer = null;
        let busy = false;
        const schedule = (wait) => {
            if (timer) clearTimeout(timer);
            timer = setTimeout(tick, wait);
        };
        const tick = async () => {
            if (!box.isConnected) return;
            if (document.hidden || busy) {
                schedule(delay);
                return;
            }
            busy = true;
            const began = Date.now();
            try {
                await task();
            } catch (ignored) {
                /* a panel read that fails is the machine's news to tell, not a crash */
            } finally {
                busy = false;
            }
            const spent = Date.now() - began;
            delay = spent > (fast || 5000) * 0.6 ? Math.min(delay * 2, 60000) : (fast || 5000);
            if (box.isConnected) schedule(delay);
        };
        /* a STAGGERED first tick: several panels mounting together would otherwise fire
           their first read in the same frame — the one burst a slowly-booting page pays
           for on every load */
        schedule(delay * (0.5 + Math.random()));
        return () => {
            if (timer) clearTimeout(timer);
        };
    }

    register("/m/mach/component/network", networkPanel);

    /* wire — THE MESSAGES THEMSELVES. The network's meters answer "how much"
       (~/network/analyzer/analysis/wire); this answers "what": every round trip and every
       unsolicited frame the VM's sockets saw, one lane per peer. The door computes it from
       the wire's ring (Java-side, bounded), so the page reads a snapshot and never a store. */
    function wirePanel(env, api) {
        const box = node("div", "mpanel wire-panel");
        const evalJson = (api && api.evalJson) || (() => Promise.resolve(null));
        let painted = "";
        /* the card's folds survive a repaint, exactly as the analysis panel's do */
        const openState = new Map();
        const remember = (details, key) =>
            details.addEventListener("toggle", () => openState.set(key, details.open));

        const brief = (text, limit) => {
            const full = String(text == null ? "" : text).replace(/\s+/g, " ").trim();
            return full.length > limit ? full.slice(0, limit - 1) + "…" : full;
        };

        const lane = (peer, data) => {
            const details = document.createElement("details");
            details.className = "wire-lane";
            const laneKey = "lane:" + String(peer);
            details.open = openState.has(laneKey) ? openState.get(laneKey) : true;
            remember(details, laneKey);
            const rows = Array.isArray(data && data.row) ? data.row : (data && data.row ? [data.row] : []);
            const summary = document.createElement("summary");
            summary.className = "wire-lane-head";
            summary.appendChild(node("span", "wire-peer", peer));
            summary.appendChild(node("span", "wire-counts",
                (data && data.exchanges != null ? data.exchanges : rows.length) + " exchanges"));
            if (data && data.timeouts)
                summary.appendChild(node("span", "wire-timeouts", data.timeouts + " timeouts"));
            details.appendChild(summary);

            const body = node("div", "wire-lane-body");
            rows.forEach((entry) => {
                const row = node("div", "wire-row");
                row.appendChild(node("span", "wire-outcome " + String(entry.outcome || ""),
                    String(entry.outcome || "")));
                /* the elapsed is a time::T (`millis::3.0`), not a bare number */
                const elapsed = entry.elapsed;
                const unit = elapsed && typeof elapsed === "object"
                    ? String(elapsed.vid || elapsed.tid || "").split("/").pop() || "millis"
                    : "ms";
                const amount = elapsed && typeof elapsed === "object" ? elapsed.value : elapsed;
                row.appendChild(node("span", "wire-ms", (amount != null ? amount : 0) + " " + unit));
                if (entry.tag) row.appendChild(node("span", "wire-tag", brief(entry.tag, 30)));
                const request = node("span", "wire-req", brief(entry.request, 70));
                request.title = String(entry.request || "");
                row.appendChild(request);
                const reply = node("span", "wire-reply", brief(entry.reply, 70));
                reply.title = String(entry.reply || "");
                row.appendChild(reply);
                body.appendChild(row);
            });
            details.appendChild(body);
            return details;
        };

        const load = async () => {
            try {
                const value = await evalJson();
                const text = JSON.stringify(value || {});
                if (text === painted) return;
                painted = text;
                const lanes = (value && value.peer) || {};
                const peers = Object.keys(lanes).sort();
                box.textContent = "";
                const head = node("div", "mpanel-head wire-head");
                head.appendChild(node("span", "mpanel-k", "wire"));
                head.appendChild(node("span", "wire-head-note",
                    peers.length + (peers.length === 1 ? " peer" : " peers")));
                if (value && value.paused) head.appendChild(node("span", "wire-paused", "paused"));
                if (value && value.dropped)
                    head.appendChild(node("span", "wire-dropped", value.dropped + " dropped"));
                box.appendChild(head);
                if (!peers.length) {
                    box.appendChild(node("div", "mpanel-empty", "no exchanges seen"));
                    return;
                }
                peers.forEach((peer) => box.appendChild(lane(peer, lanes[peer])));
            } catch (problem) {
                box.textContent = "";
                box.appendChild(node("div", "mpanel-empty",
                    "cannot read the wire: " + (problem && problem.message ? problem.message : problem)));
            }
        };

        pollPanel(box, () => load(), 4000);
        load();
        return box;
    }

    register("/m/mach/component/network/trace", wirePanel);

    /* analysis — a meter rec as a table. Groups nest (wire => [recv, sent]), a leaf wears
       its kind and its reading: counter is a number, gauge a point-in-time, timer the
       count/total/min/max it keeps. */
    function analysisPanel(env, api) {
        const box = node("div", "mpanel analysis-panel");
        const vid = String((env && env.vid) || "/network/analyzer/analysis").trim();
        /* A POLL REBUILDS THE PANEL; THE HUMAN'S FOLDS ARE NOT THE DATA'S TO RESET.
           every toggle is remembered by key (a meter group's path, a lane's peer) and
           re-applied when the next poll re-renders the same tree. */
        const openState = new Map();
        const openOf = (key, fallback) => (openState.has(key) ? openState.get(key) : fallback);
        const remember = (details, key) =>
            details.addEventListener("toggle", () => openState.set(key, details.open));

        /* THE REC AND ITS TRACE IN ONE READ, as Marko's query gives them:
             *<vid>.-<[_,>>trace]  ==>  [analysis::[…], trace::[…]]
           the first element is the meters (peer gauge, wire counters), the second is the
           wire's window. One read, two sections. */
        const brief = (obj, limit) => {
            if (obj == null || typeof obj !== "object") return String(obj == null ? "" : obj);
            const text = write(obj).replace(/^str::/, "");
            return text.length > limit ? text.slice(0, limit - 1) + "…" : text;
        };
        const fieldOf = (rec, name) => {
            let found = null;
            if (rec && rec.isRec && rec.isRec())
                rec.js.forEach((value, key) => {
                    /* a key may arrive as the bare spelling or as a uri; the last segment is
                       the name either way */
                    if (String(key) === name || String(key).split("/").pop() === name) found = value;
                });
            return found;
        };
        const KINDS = ["counter", "gauge", "timer"];
        const kindOf = (value) => {
            const tid = String((value && value.tid) || "");
            const tidKind = tid.split("/").pop();
            if (KINDS.includes(tidKind)) return tidKind;
            const vidKind = String((value && value.vid) || "").split("/").pop();
            return KINDS.includes(vidKind) ? vidKind : null;
        };
        const reading = (value, kind) => {
            if (kind !== "timer") return write(value);
            const count = fieldOf(value, "count");
            const total = fieldOf(value, "total");
            const min = fieldOf(value, "min");
            const max = fieldOf(value, "max");
            return (count ? write(count) : "?") + " obs · total " + (total ? write(total) : "?")
                + " · min " + (min ? write(min) : "?") + " · max " + (max ? write(max) : "?");
        };
        const rowOf = (key, kind, text) => {
            const row = node("div", "analysis-row");
            row.appendChild(node("span", "analysis-key", String(key)));
            row.appendChild(node("span", "analysis-kind" + (kind ? " analysis-" + kind : ""), kind || ""));
            row.appendChild(node("span", "analysis-value", text));
            return row;
        };
        /* the meters: every leaf of the analysis rec, groups folded open one level */
        const meters = (root, rec, depth, path) => {
            if (!rec || !rec.isRec || !rec.isRec()) return;
            rec.js.forEach((value, key) => {
                const name = String(key);
                if (name === "trace") return;          // the window is drawn below
                const kind = kindOf(value);
                if (kind) {
                    root.appendChild(rowOf(name, kind, reading(value, kind)));
                    return;
                }
                if (value && value.isRec && value.isRec() && depth < 2) {
                    const key = "meter:" + (path || "") + "/" + name;
                    const details = document.createElement("details");
                    details.className = "analysis-group";
                    details.open = openOf(key, true);
                    remember(details, key);
                    const summary = document.createElement("summary");
                    summary.className = "analysis-group-head";
                    summary.textContent = name;
                    details.appendChild(summary);
                    const inner = node("div", "analysis-group-body");
                    meters(inner, value, depth + 1, (path || "") + "/" + name);
                    details.appendChild(inner);
                    root.appendChild(details);
                    return;
                }
                root.appendChild(rowOf(name, "", write(value)));
            });
        };
        /* the window: a lane per peer, a row per exchange */
        const trace = (root, rec) => {
            if (!rec || !rec.isRec || !rec.isRec()) {
                root.appendChild(node("div", "mpanel-empty", "no trace"));
                return;
            }
            const paused = fieldOf(rec, "paused");
            const dropped = fieldOf(rec, "dropped");
            const head = node("div", "mpanel-head wire-head");
            head.appendChild(node("span", "mpanel-k", "trace"));
            if (paused && paused.js === true) head.appendChild(node("span", "wire-paused", "paused"));
            if (dropped) head.appendChild(node("span", "wire-dropped", write(dropped) + " dropped"));
            head.appendChild(node("span", "wire-legend", "↑ sent · ↓ recv"));
            root.appendChild(head);
            const lanes = fieldOf(rec, "peer");
            let count = 0;
            if (lanes && lanes.isRec && lanes.isRec()) lanes.js.forEach(() => { count += 1; });
            if (!count) {
                root.appendChild(node("div", "mpanel-empty", "no exchanges seen"));
                return;
            }
            lanes.js.forEach((lane, peer) => {
                const laneKey = "lane:" + String(peer);
                const details = document.createElement("details");
                details.className = "wire-lane";
                details.open = openOf(laneKey, true);
                remember(details, laneKey);
                const summary = document.createElement("summary");
                summary.className = "wire-lane-head";
                summary.appendChild(node("span", "wire-peer", String(peer)));
                const exchanges = fieldOf(lane, "exchanges");
                const timeouts = fieldOf(lane, "timeouts");
                if (exchanges) summary.appendChild(node("span", "wire-counts", write(exchanges)
                    + (exchanges.js === 1 ? " exchange" : " exchanges")));
                if (timeouts && timeouts.js > 0)
                    summary.appendChild(node("span", "wire-timeouts", write(timeouts) + " timeouts"));
                details.appendChild(summary);
                const body = node("div", "wire-lane-body");
                const rows = fieldOf(lane, "row");
                /* a lane's rows are a lst: read its members, not `stream()` on the lst itself
                   (that yields the list once, which is the one-row-per-peer symptom) */
                const rowList = rows && rows.isLst && rows.isLst() ? rows.js : (rows ? [rows] : []);
                rowList.forEach((row) => {
                    /* ONE ROW, READ AS ITS OWN TEXT: `[seq=>2, tag=>'0x…', outcome=>'send_recv',
                       elapsed=>millis::36.0, request=>'…', reply=>"…"]` — regexes over the
                       written form are exact where walking the parsed rec surprised me. */
                    const rowText = write(row);
                    const pick = (name) => {
                        const quoted = new RegExp(name + "=>'((?:[^'\\\\]|\\\\.)*)'").exec(rowText);
                        if (quoted) return quoted[1];
                        const doubled = new RegExp(name + '=>"((?:[^"\\\\]|\\\\.)*)"').exec(rowText);
                        if (doubled) return doubled[1];
                        const bare = new RegExp(name + "=>([^,\\]]+)").exec(rowText);
                        return bare ? bare[1].trim() : "";
                    };
                    const line = node("div", "wire-row");
                    line.appendChild(node("span", "wire-outcome " + pick("outcome"), pick("outcome")));
                    line.appendChild(node("span", "wire-ms", pick("elapsed")));
                    /* DIRECTION, marked per cell: ↑ what this machine SENT (the request),
                       ↓ what it RECEIVED (the reply, or an unsolicited push's frame) */
                    const requestText = pick("request");
                    const request = node("span", "wire-req");
                    if (requestText) request.appendChild(node("span", "wire-arrow wire-sent", "↑"));
                    request.appendChild(document.createTextNode(requestText.slice(0, 70)));
                    request.title = requestText;
                    line.appendChild(request);
                    const replyText = pick("reply");
                    const reply = node("span", "wire-reply");
                    if (replyText) reply.appendChild(node("span", "wire-arrow wire-recv", "↓"));
                    reply.appendChild(document.createTextNode(replyText.slice(0, 70)));
                    reply.title = replyText;
                    line.appendChild(reply);
                    body.appendChild(line);
                });
                details.appendChild(body);
                root.appendChild(details);
            });
        };

        const read = async (expr) => {
            const text = String((await api.mtron(expr)) || "").trim();
            if (!text || text === "noobj" || /^fail::/.test(text)) return null;
            try {
                return parse(text);
            } catch (ignored) {
                return null;
            }
        };
        const load = async () => {
            box.textContent = "";
            let analysis = null;
            let window = null;
            try {
                /* TWO READS, as the machine spells them:
                     *<vid>        the analysis rec (peer gauge, wire counters)
                     *<vid>/trace   the wire's window (lanes of exchanges) */
                analysis = await read("*<" + vid + ">");
                window = await read("*<" + vid + "/trace>");
            } catch (problem) {
                box.appendChild(node("div", "mpanel-empty",
                    "cannot read the analysis: " + (problem && problem.message ? problem.message : problem)));
                return;
            }
            if (!analysis && !window) {
                box.appendChild(node("div", "mpanel-empty", "no analysis"));
                return;
            }
            const head = node("div", "mpanel-head");
            head.appendChild(node("span", "mpanel-k", "analysis"));
            head.appendChild(node("span", "analysis-count", vid));
            box.appendChild(head);
            if (analysis && analysis.isRec && analysis.isRec()) meters(box, analysis, 0, "");
            else box.appendChild(node("div", "mpanel-empty", "no meters"));
            trace(box, window);
        };

        pollPanel(box, () => load(), 8000);
        load();
        return box;
    }

    register("/m/isa/mach/analysis", analysisPanel);
    register("analysis", analysisPanel);
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
                /* A CHILD MACHINE READS ITS ANCESTORS' MEMORY: /a/b/c sees /a/b, /a
                   and /. one CLOSED accordion per ancestor, read lazily on first open
                   (so opening the panel costs the one read the current level needs). */
                const here = String(mem).replace(/\/memory$/, "") || "/";
                const ancestors = [];
                for (let at = here; at && at !== "/";) {
                    at = at.replace(/\/[^/]+$/, "");
                    ancestors.push(at || "/");
                }
                ancestors.forEach((ancestor) => {
                    const details = document.createElement("details");
                    details.className = "memory-parent";
                    details.open = false;
                    const summary = document.createElement("summary");
                    summary.className = "memory-space-head memory-parent-head";
                    summary.appendChild(node("span", "memory-parent-mark", "↖"));
                    summary.appendChild(node("span", "memory-parent-name", ancestor));
                    details.appendChild(summary);
                    const body = node("div", "memory-parent-body");
                    details.appendChild(body);
                    let loaded = false;
                    details.addEventListener("toggle", async () => {
                        if (!details.open || loaded) return;
                        loaded = true;
                        body.textContent = "";
                        body.appendChild(spinner("reading spaces…"));
                        try {
                            const raw = await evalJson("*<" + (ancestor === "/" ? "" : ancestor)
                                + "/memory>>>space.>>.vid()");
                            const list = raw == null ? [] : (Array.isArray(raw) ? raw : [raw]);
                            const parentVids = list.map((v) => String(v))
                                .filter((v) => v && v !== "noobj").sort();
                            body.textContent = "";
                            if (!parentVids.length) {
                                body.appendChild(node("div", "mpanel-empty", "no registered spaces"));
                                return;
                            }
                            parentVids.forEach((vid) => body.appendChild(space(vid)));
                        } catch (problem) {
                            body.textContent = "";
                            body.appendChild(node("div", "mpanel-empty",
                                "cannot read " + ancestor + ": "
                                + (problem && problem.message ? problem.message : problem)));
                        }
                    });
                    box.appendChild(details);
                });
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

        /* AN MCP HANDLER OPENS A CLIENT, NOT THE HANDLER'S OWN ADDRESS: the value of a
           route like `/mcp => mcp_mtron` is an mcp server; the way a person drives it is
           an mcp_client of the same endpoint, so that is what the row opens (on the
           machine that serves this card, at this route's path). */
        const mcpHandler = (path, target) => {
            const text = target == null ? "" : String(target);
            if (!/^mcp/i.test(text) && !/^\/mcp/.test(path)) return null;
            const authority = String((api && api.instance) || "").replace(/^[a-z]+:\/\//i, "").split("/")[0];
            const hostName = authority.split(":")[0];
            const port = hostUri && hostUri.port && hostUri.port > 0
                ? hostUri.port
                : (httpResolve ? 8777 : 8555);
            const scheme = httpResolve ? "http" : "ws";
            if (!hostName) return null;
            return "mcp_client::[host=><" + scheme + "://" + hostName + ":" + port + path + ">]";
        };

        paths.forEach((path) => {
            const row = node("div", "mpanel-row");
            row.appendChild(node("span", "mpanel-k", path));
            const v = node("span", "mpanel-v");
            const target = routes[path];
            const text = target == null ? "" : String(target);
            const client = mcpHandler(path, target);
            if (client) {
                const button = toElement(f(text), () => {
                    if (api && api.open) api.open({
                        tid: "/m/web/mcp/mcp_client",
                        vid: null,
                        raw: "",
                        shortTid: "mcp client",
                        expr: client
                    });
                }, "route-target mcp-route");
                button.textContent = brief(target);
                button.title = "open an mcp client of " + path + " to drive this server";
                v.appendChild(button);
            } else if (text) {
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

    /* THE CONSOLE IS ONE ELEMENT WITH ONE OWNER. #console-card is the page's live
       terminal (its sockets and input are bound to it by id), so a view does not copy or
       gut it: the view MOVES THE ELEMENT and remembers where it came from, and hands it
       back there when the view goes away. Moving children was the old shape, and it lost
       them: the card and the modal each took the terminal from #console-card, so after a
       round trip the page's section was empty for good ("loads, then goes blank"). */
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
        /* where it lives right now: the card's panel, the modal's, or the page shell.
           the shell keeps #console-card HIDDEN until a view hosts it (the `hidden`
           attribute wins over any display rule), so hosting shows it and the hand-back
           restores the shell's own state */
        const from = section.parentNode;
        const wasHidden = section.hidden;
        section.hidden = false;
        box.appendChild(section);
        const giveBack = () => {
            if (!from || !from.isConnected) return;
            from.appendChild(section);
            section.hidden = wasHidden;
        };
        const dialog = document.querySelector("#popup");
        if (dialog) dialog.addEventListener("close", giveBack, {once: true});
        const out = document.querySelector("#popup-out");
        if (out && typeof MutationObserver !== "undefined") {
            const watcher = new MutationObserver(() => {
                if (box.isConnected) return;
                watcher.disconnect();
                giveBack();
            });
            watcher.observe(out, {childList: true});
        }
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

    /* ── an mcp client: drive an mcp server by hand ──────────────────────────
       the client's rec carries its TOOLS (name => tool::T, each holding the parameter
       rec the server announced), and a tool is called by applying the instruction it
       holds. The client does NOT carry resources or prompts — the mcp SERVER does
       (mcp_server::[tool, resource, prompt]) and nothing connects them through to the
       client yet — so those two menus are placeholders that say exactly that. */
    /* one probe batch per machine, cached: which prefix each rewrite rule lives under.
       the batch is capped as a whole (a slow machine leaves the flat table in place
       instead of holding the panel), and it borrows the background reader. */
    const PROFILE_PREFIXES = new Map();
    const PROFILE_RECENT = new Map();
    /* ONE PROFILE PER EXPRESSION AT A TIME: a second click while the first run is still
       in flight joins it instead of starting a second 20x chain on the machine */
    const PROFILE_INFLIGHT = new Map();
    /* how long a profile snapshot may be reused (close/reopen) before a fresh one is read */
    const PROFILE_TTL = 10000;
    function profilePrefixMap(api) {
        const apart = api && typeof api.mtronApart === "function" ? api.mtronApart : api.mtron;
        const key = String((api && api.instance) || "");
        if (PROFILE_PREFIXES.has(key)) return PROFILE_PREFIXES.get(key);
        const paths = (text) => {
            const seen = new Set();
            (String(text == null ? "" : text).match(/\/[A-Za-z0-9_.\/-]+/g) || []).forEach((path) => seen.add(path));
            return [...seen];
        };
        const cap = (promise) => Promise.race([
            promise.catch(() => ""),
            new Promise((resolve) => setTimeout(() => resolve(""), 2500))
        ]);
        const pending = (async () => {
            const map = new Map();
            const sets = paths(String((await cap(apart("*/instset>>reference"))) || ""));
            /* four lanes: the probes are independent reads, so they need not queue */
            const answers = await Promise.all(sets.map((set, index) =>
                cap(apart("*<" + set + "/inst/rewrite/+>", index))));
            answers.forEach((answer) => {
                paths(answer).forEach((vid) => {
                    /* a vid carries its signature as a query: the address is the path */
                    const path = vid.split("?")[0];
                    if (!/\/inst\/rewrite\/[^/]+$/.test(path)) return;
                    map.set(f(path).name(), f(path).retract(1).toString());
                });
            });
            return map;
        })();
        PROFILE_PREFIXES.set(key, pending);
        return pending;
    }

    /* ── profiling::T — where the time went ────────────────────────────────────
       the machine's own timings: a min/max per stage (rewrite, resolve, apply), the
       resolve and apply sub-stages with their share of their stage, every rewrite rule
       with the sizes it saw and what it cost, the per-instruction table, the flow and
       cache counters — and the machine's rendered `>>format` text, the same table the
       console prints. read by address when the card has one, else from the expression
       the card was opened with (how a compiler's profile mark opens it). */
    /* THE PANEL MOUNTS AT ONCE and fills when the machine answers. profile() runs the
       chain about twenty times, and a slow machine must not leave the card sitting on
       "panel…" with no sign of life: the box (and its heartbeat) is on screen while the
       read is in flight. */
    register("/m/inst/profile/profiling", (env, api) => {
        const box = node("div", "mpanel prof-panel");
        const note = node("div", "mpanel-empty", "reading the profile …");
        box.appendChild(note);
        const source = env.vid ? "*<" + env.vid + ">" : String(env.expr || env.raw || "").trim();
        if (!source) {
            note.textContent = "no profile to read";
            return box;
        }
        fillProfile(env, api, box, note, source);
        return box;
    });

    async function fillProfile(env, api, box, note, source) {
        const field = (rec, name) => {
            if (!rec || !rec.isRec || !rec.isRec()) return null;
            for (const [key, value] of rec.js) if (key.toString() === name) return value;
            return null;
        };
        const num = (obj) => {
            if (obj == null || (obj.isNoObj && obj.isNoObj())) return null;
            const n = Number(obj.js);
            return Number.isFinite(n) ? n : null;
        };
        const ms = (value) => (value == null ? "—" : value.toFixed(3));
        const pct = (value) => (value == null ? "" : value.toFixed(1) + "%");
        let profile = null;
        /* the snapshot's key, hoisted: the shelf countdown below reads the same entry */
        let cacheKey = "";
        try {
            /* PROFILE() IS THE EXPENSIVE HALF: it runs the chain ~20 times, and on a busy
               machine that is seconds. say so while it works instead of a bare "reading…". */
            const started = Date.now();
            const heartbeat = setInterval(() => {
                const seconds = Math.round((Date.now() - started) / 1000);
                note.textContent = "profiling … " + seconds + " s (the machine runs the chain about twenty times)";
            }, 1500);
            /* never the reason a node test run (or a closed tab) stays alive */
            if (heartbeat && typeof heartbeat.unref === "function") heartbeat.unref();
            /* OPENING THE SAME PANEL AGAIN IS NOT A REASON TO RUN THE PROFILER AGAIN: the
               rec is a snapshot, so a fresh one is reused for ten seconds — close, reopen,
               and the panel comes back without touching the machine. */
            cacheKey = String((api && api.instance) || "") + " " + source;
            const cached = PROFILE_RECENT.get(cacheKey);
            /* THE PROFILE READ RIDES A BACKGROUND LANE: on the page's shared chain it
               queues behind the panels' polls (a roster read can take seconds), which is
               exactly what the heartbeat then counts. */
            const read = (expr, lane) => (api && typeof api.mtronApart === "function")
                ? api.mtronApart(expr, lane)
                : (api && typeof api.mtron === "function" ? api.mtron(expr) : Promise.resolve(""));
            let text = "";
            try {
                if (cached && Date.now() - cached.at < PROFILE_TTL) {
                    text = cached.text;
                } else {
                    let pending = PROFILE_INFLIGHT.get(cacheKey);
                    if (!pending) {
                        pending = read(source, 0).then((answer) => String(answer || "").trim());
                        PROFILE_INFLIGHT.set(cacheKey, pending);
                        pending.then(() => PROFILE_INFLIGHT.delete(cacheKey),
                            () => PROFILE_INFLIGHT.delete(cacheKey));
                    }
                    text = await pending;
                    if (text) PROFILE_RECENT.set(cacheKey, {at: Date.now(), text: text});
                }
            } finally {
                clearInterval(heartbeat);
            }
            const value = parse(text);
            profile = value && value.isRec && value.isRec() ? value
                : (value && value.isObjs && value.isObjs() ? [...value.js].find((m) => m && m.isRec && m.isRec()) : null);
        } catch (problem) {
            note.textContent = "cannot read the profile: " + (problem && problem.message ? problem.message : problem);
            return box;
        }
        if (!profile) {
            note.textContent = "no profile at " + source;
            return box;
        }
        note.remove();

        const head = node("div", "prof-head");
        head.appendChild(node("span", "prof-title", "profiling"));
        const machine = field(profile, "machine");
        if (machine) head.appendChild(node("span", "prof-machine", String(machine)));
        const insts = num(field(profile, "insts"));
        if (insts != null) head.appendChild(node("span", "prof-insts", insts + " insts"));
        box.appendChild(head);

        const section = (title) => {
            box.appendChild(node("div", "prof-sec", title));
        };
        const tableOf = (headers, rows) => {
            const table = node("table", "prof-table");
            const thead = document.createElement("thead");
            const hr = document.createElement("tr");
            headers.forEach((label) => hr.appendChild(node("th", "", label)));
            thead.appendChild(hr);
            table.appendChild(thead);
            const tbody = document.createElement("tbody");
            rows.forEach((cells) => {
                const tr = document.createElement("tr");
                cells.forEach((cell, index) => {
                    const td = node("td", index ? "prof-num" : "prof-k");
                    /* a cell that carries an element is a uri the machine named */
                    if (cell && cell.nodeType === 1) td.appendChild(cell);
                    else td.textContent = String(cell == null ? "—" : cell);
                    tr.appendChild(td);
                });
                tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            return table;
        };

        /* EVERY URI IS CLICKABLE: a rule and an instruction are named by uri, so their
           names open like the table's uri cells do (shift-click goes to ?docq) */
        const uriLink = (text, vid) => {
            const label = String(text == null ? "" : text);
            const button = node("button", "prof-ref", label);
            button.type = "button";
            button.title = vid + " · click to open · shift-click for ?docq";
            button.addEventListener("click", (ev) => {
                if (ev.shiftKey) api.open({vid: vid + "?docq", shortTid: "docs"});
                else api.open({vid: vid, raw: vid});
            });
            return button;
        };

        /* stages: a min and a max each */
        const stage = field(profile, "stage");
        const stages = stage && stage.isRec && stage.isRec() ? [...stage.js] : [];
        if (stages.length) {
            section("stages");
            box.appendChild(tableOf(["stage", "min (ms)", "max (ms)"],
                stages.map(([key, value]) => [key.toString(), ms(num(field(value, "min"))), ms(num(field(value, "max")))])));
        }

        /* a sub-stage table: value + its share of the stage */
        const subStages = (rec, title) => {
            const entries = rec && rec.isRec && rec.isRec() ? [...rec.js] : [];
            if (!entries.length) return;
            section(title);
            const total = entries.reduce((sum, [, value]) => sum + (num(value) || 0), 0);
            box.appendChild(tableOf([title, "avg (ms)", "share"],
                entries.map(([key, value]) => {
                    const time = num(value);
                    return [key.toString(), ms(time), total ? pct((100 * (time || 0)) / total) : ""];
                })));
        };
        subStages(field(profile, "resolve"), "resolve sub-stages");
        subStages(field(profile, "apply"), "apply sub-stages");

        /* REWRITE RULES: drawn FLAT at once, then folded by prefix if the machine's own
           rewrite tables answer — the fold never sits on the render path. A rule's name is
           relative, so the instset reference lists the sets, each set's `inst/rewrite/+`
           answers its rules' vids, and a vid's retract(1) is the accordion key
           (/m/db/grph/inst/rewrite for gremlin_where). The probes ride a reader of their
           own (mtronApart) with a cap, so they cannot stall the page or the panel. */
        const rewrite = field(profile, "rewrite");
        const rules = rewrite ? field(rewrite, "rules") : null;
        const ruleRows = rules && rules.isLst && rules.isLst() ? [...rules.js] : [];
        if (ruleRows.length) {
            section("rewrite rules");
            const entries = ruleRows
                .map((rule) => ({
                    name: String(field(rule, "name") || "?"),
                    in: num(field(rule, "in")),
                    out: num(field(rule, "out")),
                    time: num(field(rule, "time"))
                }))
                .sort((a, b) => (b.time || 0) - (a.time || 0));
            const flat = tableOf(["rule", "insts in", "insts out", "time (ms)"],
                entries.map((rule) => [uriLink(rule.name, rule.name), rule.in, rule.out, ms(rule.time)]));
            box.appendChild(flat);
            /* THE PROBES ASK FOR PATHS THAT MAY NOT EXIST (a set without rewrite rules),
               and every such read fails and prints its apply stack at the machine's
               console. so folding is ASKED FOR: the flat table is what opening the panel
               costs, and the accordion is one deliberate click. */
            const foldBtn = node("button", "ghost", "fold by prefix");
            foldBtn.type = "button";
            foldBtn.title = "group the rules by the instruction set that owns them (reads the machine's rewrite tables)";
            box.appendChild(foldBtn);
            foldBtn.addEventListener("click", () => {
                foldBtn.remove();
                const folding = node("div", "prof-note", "folding by prefix…");
                box.appendChild(folding);
                profilePrefixMap(api).then((prefixByRule) => {
                folding.remove();
                if (!flat.isConnected || !prefixByRule.size) return;
                const groups = new Map();
                entries.forEach((rule) => {
                    const prefix = prefixByRule.get(rule.name) || "—";
                    if (!groups.has(prefix)) groups.set(prefix, []);
                    groups.get(prefix).push({
                        name: rule.name,
                        vid: prefixByRule.get(rule.name) ? prefix + "/" + rule.name : rule.name,
                        in: rule.in, out: rule.out, time: rule.time
                    });
                });
                const ordered = [...groups.entries()]
                    .map(([prefix, rows]) => ({
                        prefix: prefix, rows: rows,
                        time: rows.reduce((sum, row) => sum + (row.time || 0), 0)
                    }))
                    .sort((a, b) => b.time - a.time);
                const holder = node("div", "prof-rules");
                ordered.forEach((group) => {
                    const fold = document.createElement("details");
                    fold.className = "prof-group";
                    /* the biggest spender starts open; the rest are one click away */
                    fold.open = group === ordered[0];
                    const head = document.createElement("summary");
                    head.className = "prof-group-head";
                    head.appendChild(node("span", "prof-group-name", group.prefix));
                    head.appendChild(node("span", "prof-group-meta",
                        group.rows.length + (group.rows.length === 1 ? " rule" : " rules") + " · " + ms(group.time) + " ms"));
                    fold.appendChild(head);
                    fold.appendChild(tableOf(["rule", "insts in", "insts out", "time (ms)"],
                        group.rows.map((rule) => [uriLink(rule.name, rule.vid), rule.in, rule.out, ms(rule.time)])));
                    holder.appendChild(fold);
                });
                flat.replaceWith(holder);
                }).catch(() => {
                    folding.remove();
                });
            });
            const total = rewrite ? field(rewrite, "total") : null;
            if (total) {
                box.appendChild(node("div", "prof-note",
                    "total · insts in " + (num(field(total, "in")) ?? "—")
                    + " · out " + (num(field(total, "out")) ?? "—")
                    + " · " + ms(num(field(total, "time"))) + " ms"));
            }
        }

        /* the per-instruction table: what each instruction moved */
        const perInst = field(profile, "per_inst");
        const instRows = perInst && perInst.isLst && perInst.isLst() ? [...perInst.js] : [];
        if (instRows.length) {
            section("per instruction");
            box.appendChild(tableOf(["instruction", "monad in", "coeff in", "monad out", "coeff out", "time (ms)"],
                instRows.map((row) => [
                    uriLink(String(field(row, "name") || "?"), String(field(row, "name") || "?")),
                    num(field(row, "monad_in")), num(field(row, "c_in")),
                    num(field(row, "monad_out")), num(field(row, "c_out")),
                    ms(num(field(row, "time")))
                ])));
        }

        /* flow and cache: one line each */
        const flow = field(profile, "flow");
        if (flow) {
            section("flow");
            box.appendChild(node("div", "prof-note",
                "monads " + (num(field(flow, "monads")) ?? "—")
                + " · coeff-sum " + (num(field(flow, "c_sum")) ?? "—")
                + " · compression " + (num(field(flow, "compression")) ?? "—")
                + " · processors " + (num(field(flow, "processors")) ?? "—")));
        }
        const cache = field(profile, "cache");
        if (cache) {
            section("cache");
            const hit = num(field(cache, "hit_rate"));
            box.appendChild(node("div", "prof-note",
                "hits " + (num(field(cache, "hits")) ?? "—")
                + " · misses " + (num(field(cache, "misses")) ?? "—")
                + " · hit rate " + (hit == null ? "—" : (hit * 100).toFixed(1) + "%")));
        }

        /* the machine's own rendering, exactly the text the console's `>>format` prints —
           asked for, not fetched on open: one more run of the profiler for a table the
           panel already draws is the last thing a busy machine needs */
        section("format");
        const formatBox = node("pre", "prof-format", "");
        formatBox.hidden = true;
        const formatBtn = node("button", "ghost", "format");
        formatBtn.type = "button";
        formatBtn.title = "the machine's own text table (>>format)";
        formatBtn.addEventListener("click", async () => {
            formatBtn.disabled = true;
            formatBtn.textContent = "formatting…";
            try {
                const text = String((await read(env.vid ? "*<" + env.vid + ">>>format" : source + ">>format", 1)) || "").trim();
                const value = parse(text);
                formatBox.textContent = value && value.isStr && value.isStr() ? value.js : text;
                formatBox.hidden = false;
                formatBtn.remove();
            } catch (problem) {
                formatBox.textContent = "no formatted table: " + (problem && problem.message ? problem.message : problem);
                formatBox.hidden = false;
                formatBtn.disabled = false;
                formatBtn.textContent = "format";
            }
        });
        box.appendChild(formatBtn);
        box.appendChild(formatBox);
        note.remove();
        /* the snapshot's shelf life, counting down in the panel: a profile is a moment,
           and the panel reuses it for a few seconds so close/reopen costs nothing */
        const stamp = PROFILE_RECENT.get(cacheKey);
        if (stamp) {
            const shelf = node("div", "prof-note prof-shelf", "");
            box.insertBefore(shelf, box.firstChild);
            const paint = () => {
                const left = Math.max(0, Math.ceil((PROFILE_TTL - (Date.now() - stamp.at)) / 1000));
                shelf.textContent = left > 0
                    ? "snapshot · reusable for " + left + " s"
                    : "snapshot expired — reopening reads a fresh profile";
                if (left <= 0) clearInterval(tick);
            };
            const tick = setInterval(paint, 1000);
            /* never the reason a node test run (or a closed tab) stays alive */
            if (tick && typeof tick.unref === "function") tick.unref();
            paint();
        }
    }

    register("/m/web/mcp/mcp_client", async function (env, api) {
        const box = node("div", "mpanel mcp-panel");
        const note = node("div", "mpanel-empty", "reading the client …");
        box.appendChild(note);
        /* the client is read by ADDRESS when the card has one, else the expression the
           card was opened with constructs it (how a server's handler opens this card) */
        const source = env.vid ? "*<" + env.vid + ">" : String(env.expr || env.raw || "").trim();
        if (!source) {
            note.textContent = "no client to read";
            return box;
        }
        const field = (rec, name) => {
            if (!rec || !rec.isRec || !rec.isRec()) return null;
            for (const [key, value] of rec.js) if (key.toString() === name) return value;
            return null;
        };
        let client = null;
        try {
            const text = String((await api.mtron(source)) || "").trim();
            const value = parse(text);
            client = value && value.isRec && value.isRec() ? value
                : (value && value.isObjs && value.isObjs() ? [...value.js].find((m) => m && m.isRec && m.isRec()) : null);
        } catch (problem) {
            note.textContent = "cannot read the client: " + (problem && problem.message ? problem.message : problem);
            return box;
        }
        if (!client) {
            note.textContent = "no client at " + source;
            return box;
        }
        note.remove();

        const head = node("div", "mcp-head");
        const dot = node("span", "mcp-dot");
        const state = field(client, "status");
        const up = state && state.isBool && state.isBool() ? !!state.js : true;
        dot.classList.add(up ? "up" : "bad");
        dot.title = up ? "the client's health check answers" : "the client's health check does not answer";
        head.appendChild(dot);
        const host = field(client, "host");
        let address = env.vid ? String(env.vid) : "";
        head.appendChild(node("span", "mcp-title", address || "mcp client"));
        if (host) head.appendChild(node("span", "mcp-host", String(host)));
        box.appendChild(head);
        /* A TOOL IS CALLED THROUGH THE CLIENT'S ADDRESS (docs/skills: mcp_client tool
           invocation is `/usr/ai/mcp/a/tool/<name>/inst(args)`), so a client that only
           exists as an expression is written to one address, once, on the first call —
           the same write the docs create it with (`mcp_client::[…]@/usr/ai/mcp/<name>`). */
        const connect = async () => {
            if (address) return address;
            const name = String(host || "client").replace(/^[a-z]+:\/\//i, "").replace(/[^A-Za-z0-9.]+/g, "-");
            address = "/usr/ai/mcp/" + name;
            await api.mtron(source + "@" + address);
            head.querySelector(".mcp-title").textContent = address;
            return address;
        };

        /* tools: one collapsible row each, with an input per parameter the tool announced */
        const tools = field(client, "tool");
        const toolsBox = node("div", "mcp-tools");
        const entries = tools && tools.isRec && tools.isRec() ? [...tools.js] : [];
        toolsBox.appendChild(node("div", "mcp-sec", "tools · " + entries.length));
        if (!entries.length) toolsBox.appendChild(node("div", "mpanel-empty", "this client announced no tools"));
        entries.forEach(([key, spec]) => {
            const name = key.toString();
            const desc = field(spec, "desc");
            const row = document.createElement("details");
            row.className = "mcp-tool";
            const summary = document.createElement("summary");
            summary.className = "mcp-tool-row";
            summary.appendChild(node("span", "mcp-tool-name", name));
            if (desc) summary.appendChild(node("span", "mcp-tool-desc", String(desc)));
            row.appendChild(summary);
            const body = node("div", "mcp-tool-body");
            row.appendChild(body);
            /* the parameter rec: name => description (the server's own words) */
            const params = field(spec, "arg");
            const names = params && params.isRec && params.isRec() ? [...params.js.keys()].map((k) => k.toString()) : [];
            const inputs = new Map();
            const form = node("form", "mcp-call");
            names.forEach((param) => {
                const wrap = node("label", "mcp-arg");
                wrap.appendChild(node("span", "mcp-arg-name", param));
                const input = document.createElement("input");
                input.type = "text";
                input.className = "mcp-arg-input";
                input.spellcheck = false;
                input.placeholder = String(field(params, param) || "mtron value");
                wrap.appendChild(input);
                inputs.set(param, input);
                form.appendChild(wrap);
            });
            const call = node("button", "primary mcp-call-btn", "call");
            call.type = "submit";
            form.appendChild(call);
            const answer = node("pre", "mcp-answer", "");
            answer.hidden = true;
            body.appendChild(form);
            body.appendChild(answer);
            form.addEventListener("submit", async (ev) => {
                ev.preventDefault();
                /* a tool's instruction declares NAMED arguments (the parameter rec the
                   server announced), so the call passes them by name: p => value */
                const named = names
                    .map((param) => [param, String(inputs.get(param).value).trim()])
                    .filter(([, value]) => value !== "")
                    .map(([param, value]) => param + "=>" + value);
                /* the tool is applied ON the client's path — the client is only a transport */
                answer.hidden = false;
                answer.textContent = "calling " + name + " …";
                try {
                    const where = await connect();
                    const expr = where + "/tool/" + name + "/inst(" + named.join(",") + ")";
                    const text = String((await api.mtron(expr)) || "").trim();
                    answer.textContent = text || "(no answer)";
                } catch (problem) {
                    answer.textContent = "fail: " + (problem && problem.message ? problem.message : problem);
                }
            });
            toolsBox.appendChild(row);
        });
        box.appendChild(toolsBox);

        /* resources and prompts: the SERVER has them, the client does not — placeholders
           that say so, so the shape of the panel is already right when it connects */
        [["resources", "mcp_server::[resource] · not connected to the client yet"],
         ["prompts", "mcp_server::[prompt] · not connected to the client yet"]].forEach(([label, reason]) => {
            const section = node("div", "mcp-sec", label);
            const pick = document.createElement("select");
            pick.className = "mcp-pick";
            pick.disabled = true;
            pick.title = reason;
            const option = document.createElement("option");
            option.textContent = "— " + reason + " —";
            pick.appendChild(option);
            const line = node("div", "mcp-placeholder");
            line.appendChild(pick);
            box.appendChild(section);
            box.appendChild(line);
        });
        return box;
    });

    register("/m/db/tble/space/tblespace", async function (env, api) {
        const box = node("div", "mpanel tblespace");
        /* the space is read in the machine's own form (application/x-mtron) and taken
           apart with the obj serializer, not a json codec: a read of a space answers a
           STREAM of its instances, and a one-instance stream comes back as the instance
           itself, so the pattern is a field of the rec we hold either way. */
        let data = "";
        try {
            if (!env.vid) throw new Error("no address");
            const text = String((await api.mtron("*<" + env.vid + ">")) || "").trim();
            const value = parse(text);
            const members = value && value.isObjs && value.isObjs() ? [...value.js] : (value ? [value] : []);
            const patternKey = (rec) => rec && rec.isRec && rec.isRec()
                && [...rec.js.keys()].find((key) => key.toString() === "pattern");
            const space = members.find((rec) => patternKey(rec) !== undefined) || members[0] || null;
            let pattern = null;
            if (space && space.isRec && space.isRec()) {
                for (const [key, field] of space.js) if (key.toString() === "pattern") pattern = field;
            }
            data = pattern ? String(pattern).replace(/\/#.*$/, "").replace(/\/+$/, "") : "";
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
        /* PAGING: a table read is a stream, and `.range(offset, count)` takes a window of
           it — offset is the index of the first row, count how many to take. the pager
           stays hidden until a table is picked, and hides again for a hand-written
           expression (it no longer describes what the grid shows). */
        const page = {name: null, offset: 0, size: 10, count: 0, last: true};
        const pager = node("div", "tble-pager");
        const prevPage = node("button", "ghost tble-page", "\u2039");
        prevPage.type = "button";
        prevPage.title = "previous rows";
        const pageLabel = node("span", "tble-page-label", "");
        const nextPage = node("button", "ghost tble-page", "\u203a");
        nextPage.type = "button";
        nextPage.title = "next rows";
        const pageSize = document.createElement("select");
        pageSize.className = "tble-page-size";
        pageSize.title = "rows per page";
        [10, 25, 50, 100].forEach((count) => pageSize.appendChild(new Option(String(count), String(count))));
        pageSize.value = String(page.size);
        pager.appendChild(prevPage);
        pager.appendChild(pageLabel);
        pager.appendChild(nextPage);
        pager.appendChild(pageSize);
        pager.hidden = true;

        function paintPager() {
            if (!page.name) {
                pageLabel.textContent = "";
            } else if (page.count) {
                pageLabel.textContent = "rows " + (page.offset + 1) + "\u2013" + (page.offset + page.count);
            } else {
                pageLabel.textContent = "no rows at " + page.offset;
            }
            prevPage.disabled = !page.name || page.offset <= 0;
            nextPage.disabled = !page.name || page.last;
        }

        box.appendChild(pick);
        box.appendChild(pager);
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
                /* the x-mtron answer is already the machine's text: it is read, never
                   unwrapped from another codec */
                const text = String(raw == null ? "" : raw).trim();
                if (!text) throw new Error("no answer");
                result = parse(text);
            } catch (problem) {
                grid.textContent = "";
                const detail = raw == null ? "null" : String(raw).slice(0, 120);
                grid.appendChild(node("div", "tble-empty", String(problem && problem.message ? problem.message : problem) + " — raw: " + detail));
                return;
            }
            /* the pager's idea of the end: a page shorter than the page size is the last */
            page.count = result && result.isLst && result.isLst() ? result.js.length
                : (result && result.isRec && result.isRec() ? 1 : 0);
            page.last = page.count < page.size;
            paintPager();
            renderGrid(result);
        }

        /* one table, one window of it: the range is the read's own suffix */
        function loadTable(name, offset) {
            page.name = name;
            page.offset = Math.max(0, Number(offset) || 0);
            const source = "*<" + data + "/" + name + "/+>.range(" + page.offset + "," + page.size + ")";
            expr.value = source;
            pager.hidden = false;
            paintPager();
            runExpr(source);
        }

        prevPage.addEventListener("click", () => {
            if (page.name) loadTable(page.name, Math.max(0, page.offset - page.size));
        });
        nextPage.addEventListener("click", () => {
            if (page.name) loadTable(page.name, page.offset + page.size);
        });
        pageSize.addEventListener("change", () => {
            page.size = Number(pageSize.value) || 10;
            if (page.name) loadTable(page.name, 0);
        });

        expr.addEventListener("keydown", (ev) => {
            if (ev.key === "Enter") {
                ev.preventDefault();
                /* a hand-written read is its own view: the pager would be lying */
                page.name = null;
                pager.hidden = true;
                paintPager();
                runExpr(expr.value);
            }
        });

        pick.addEventListener("change", () => { if (pick.value) loadTable(pick.value, 0); });
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
        /* PLAIN, NOT HIGHLIGHTED: a signature is a uri — `name?rng=…&args=[…]` — and the
           mtron grammar's regexes do not terminate on those spellings (the renderer
           crashed on the [rewrite] click, whose table is 23 of them). the cell reads the
           same in the mono font, and the full signature is its title. */
        el.textContent = String(text == null ? "" : text);
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
    /* the compiler's opening example: a chain with a reducer and an identity, so the
       stages show a mapper, a combiner and an id in one compile */
    input.placeholder = "1.plus(2).minus(2).mult(1).id()";
    input.value = "1.plus(2).minus(2).mult(1).id()";
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
    /* the same chain, timed: the profile mark joins the explain mark, and the panel it
       opens behaves the same way (read the rec, table by table, plus `>>format`) */
    const profileBtn = iconButton("profile", () => {
        const expr = input.value.trim();
        if (!expr) return;
        api.open({tid: "/m/inst/profile/profiling", vid: null, raw: "", shortTid: "profiling", expr: expr + ".profile()"});
    });
    if (profileBtn) {
        profileBtn.disabled = true;
        profileBtn.classList.add("compiler-profile");
    }
    form.appendChild(input);
    form.appendChild(formBtn);
    form.appendChild(runBtn);
    form.appendChild(explainBtn);
    if (profileBtn) form.appendChild(profileBtn);
    box.appendChild(form);

    /* WHICH COMPONENTS THIS MACHINE COMPOSES: ~/compiler names the parser, rewriter,
       resolver (and its selector/binder) and typer in use — each a rec whose TYPE is
       the implementation (mtron_parser, scoring_resolver, specificity_selector,
       generic_binder, fixpoint_rewriter, typer). shown as a small list above the
       compile tree and echoed beside each stage's rail. */
    const components = node("div", "compiler-components");
    box.appendChild(components);
    const impl = {};
    const componentsReady = (async () => {
        try {
            const text = String((await api.mtron("*<~/compiler>")) || "").trim();
            const rec = text ? parse(text) : null;
            const at = (obj, name) => {
                if (!obj || !obj.isRec || !obj.isRec()) return null;
                for (const [key, value] of obj.js) if (key.toString() === name) return value;
                return null;
            };
            const nameOf = (obj) => {
                if (!obj || !obj.tid) return null;
                try {
                    return String(obj.tid.name());
                } catch (ignored) {
                    return null;
                }
            };
            const resolver = at(rec, "resolver");
            /* ONE WRAPPING STRIP, NOT A TWO-COLUMN TABLE: the card is four grid columns
               wide for the code, and a name/impl pair per row left most of that width
               blank. the components flow inline and wrap only when the card is narrow. */
            const base = String((env && env.vid) || "~/compiler").replace(/\/+$/, "");
            /* every pair OPENS the component it names: the modal reads that address and
               shows the rec (mtron/json), the same card any other uri gets */
            const pair = (label, value, sub, path) => {
                const el = node("span", "compiler-component" + (sub ? " sub" : ""));
                el.appendChild(node("span", "compiler-component-name", label));
                el.appendChild(node("span", "compiler-component-impl", value));
                const vid = path ? base + "/" + path : null;
                if (vid && api && typeof api.open === "function") {
                    el.classList.add("openable");
                    el.title = "open " + vid;
                    el.addEventListener("click", () => api.open({
                        vid: vid,
                        raw: "*<" + vid + ">",
                        shortTid: label,
                        expr: "*<" + vid + ">"
                    }));
                }
                return el;
            };
            const top = (label, value) => {
                if (!value) return false;
                impl[label] = value;
                components.appendChild(pair(label, value, false, label));
                return true;
            };
            const separator = () => {
                if (components.children.length) components.appendChild(node("span", "compiler-sep", "·"));
            };
            const first = top("parser", nameOf(at(rec, "parser")));
            separator();
            const second = top("rewriter", nameOf(at(rec, "rewriter")));
            separator();
            const third = top("resolver", nameOf(resolver));
            /* the resolver's parts sit in one parenthesised group after it */
            const selector = nameOf(at(resolver, "selector"));
            const binder = nameOf(at(resolver, "binder"));
            if (third && (selector || binder)) {
                const group = node("span", "compiler-subgroup");
                group.appendChild(node("span", "compiler-paren", "("));
                if (selector) {
                    impl.selector = selector;
                    group.appendChild(pair("selector", selector, true, "resolver/selector"));
                    if (binder) group.appendChild(node("span", "compiler-sep", "·"));
                }
                if (binder) {
                    impl.binder = binder;
                    group.appendChild(pair("binder", binder, true, "resolver/binder"));
                }
                group.appendChild(node("span", "compiler-paren", ")"));
                components.appendChild(group);
            }
            separator();
            top("typer", nameOf(at(rec, "typer")));
            if (components.lastChild && components.lastChild.classList.contains("compiler-sep"))
                components.lastChild.remove();
        } catch (ignored) {
            /* no composition to show: the tree still compiles */
        }
    })();

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
        /* the rail is one line wide: a stage named by a uri (a rewrite rule) shows the
           uri's NAME — its last segment — and the full uri is the hover title. the uri
           class answers the name, so no path slicing here. */
        const full = String(label == null ? "" : label);
        const named = full.includes("/") ? f(full).name() : "";
        const shown = named || full;
        const span = node("span", "cnode-label", shown);
        if (shown !== full) span.title = full;
        h.appendChild(span);
        /* the RAIL stays a name column: an implementation suffix here widened the rail past
           the label and ellipsized it (resolver → "resolv…"). the implementation is the
           components list above the tree. */
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
    /* A STAGE'S NAME IS A LEFT RAIL, NOT A ROW: parser, resolver, selector and binder
       each spent a whole line on a word, above the block they denote. the head and the
       content share one row now (one child sits beside the head, several sit in the
       bordered column), so the vertical space belongs to code and not to labels. */
    function branch(h, kids) {
        const root = node("div", "cnode cnode-inline");
        root.appendChild(h);
        if (kids && kids.length === 1) {
            root.appendChild(kids[0]);
        } else if (kids && kids.length) {
            const wrap = node("div", "cnode-kids");
            kids.forEach((k) => wrap.appendChild(k));
            root.appendChild(wrap);
        }
        return root;
    }

    /* THE RAIL IS AS WIDE AS THE WIDEST NAME IN ITS COLUMN. a fixed rail spent the
       difference between "parser" and a rewrite rule's name on every row: the selector's
       code began 350px into the panel because three 160px rails were stacked above it. a
       column measures its own rows once and hands them one width, so the codes still line
       up and the blank rail is gone. */
    function fitRails(root) {
        const columns = [root, ...root.querySelectorAll(".cnode-kids")];
        columns.forEach((column) => {
            const rows = [...column.children].filter((el) => el.classList.contains("cnode-inline"));
            let widest = 0;
            rows.forEach((row) => {
                const head = row.querySelector(":scope > .cnode-head");
                if (!head) return;
                const gap = parseFloat(getComputedStyle(head).gap || "0") || 0;
                let width = 0;
                [...head.children].forEach((part) => {
                    /* a label is ellipsized by its rail, so its RECT lies about the text */
                    width += part.classList.contains("cnode-label") ? part.scrollWidth : part.getBoundingClientRect().width;
                });
                width += gap * Math.max(0, head.children.length - 1);
                widest = Math.max(widest, width);
            });
            if (widest) column.style.setProperty("--cnode-rail", Math.ceil(widest) + "px");
        });
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
            await componentsReady;
            const src = "[" + quote1(expr) + "]";

            /* 1. parse — present it the moment it lands */
            const parsed = await evalObj(src + "==[!*/compiler/parser]");
            const parsedText = textOf(parsed);
            tree.appendChild(branch(head("_", "parser"), [code(parsedText || "noobj")]));
            explainBtn.disabled = false;   /* it compiled: now it can be explained */
            if (profileBtn) profileBtn.disabled = false;

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
            fitRails(tree);
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
