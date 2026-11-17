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
import { read, write } from './mtron-io.js';
import { parseSchema, renderForm, collectForm } from './mtron-form.js';
import { serialize } from './obj_mtron_serializer.js';
import { f } from './furi.js';
import { parse as envelope } from './mtron-envelope.js';

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

    /* a spinner + label for slow panel reads (schema discovery, table load) */
    function spinner(label) {
        const wrap = node("div", "tble-loading");
        wrap.appendChild(node("span", "tble-spinner"));
        wrap.appendChild(document.createTextNode(label || "loading…"));
        return wrap;
    }

    /* /m/instset — the pattern and the q table every space shows. */
    register("/m/instset", async function (env, api) {
        const box = node("div", "mpanel");
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

        function tile(label, metric, sub, inherited) {
            const el = node("div", "machine-tile");
            el.appendChild(node("span", "machine-tile-label", label));
            el.appendChild(node("b", "machine-tile-metric", metric));
            if (sub) el.appendChild(node("span", "machine-tile-sub", sub + (inherited ? " · inherited" : "")));
            return el;
        }

        /* ── instset tables: type · inst · rewrite · const ───────────────────
           one read per table, over the JSON codec (the entries come back as
           address strings — the signatures, complete and unclipped); the first
           open of a table fetches, its promise is kept for the panel's life. */
        const SIG_TABLES = ["type", "inst", "rewrite", "const"];
        const sigCache = new Map();
        let activeDrop = null;

        function sigRead(key) {
            if (!sigCache.has(key))
                sigCache.set(key, api.mtron("*~/instset/" + key).then(signaturesOf));
            return sigCache.get(key);
        }

        /* split an x-mtron table list into signature strings. a type entry is
           sig::T[...] — its signature is the tid before the top-level ::T — and
           an inst / const entry is its own serialization. */
        function signaturesOf(text) {
            const source = String(text == null ? "" : text).trim();
            if (!source || source === "noobj") return [];
            let inner = source;
            if (inner.charAt(0) === "[" && inner.charAt(inner.length - 1) === "]") inner = inner.slice(1, -1);
            const out = [];
            let depth = 0;
            let quote = null;
            let start = 0;
            for (let i = 0; i < inner.length; i++) {
                const c = inner[i];
                if (quote) { if (c === "\\") i++; else if (c === quote) quote = null; continue; }
                if (c === "'" || c === '"') { quote = c; continue; }
                if (c === "[" || c === "(" || c === "{") { depth++; continue; }
                if (c === "]" || c === ")" || c === "}") { depth--; continue; }
                if (c === "," && depth === 0) {
                    const sig = sigOf(inner.slice(start, i).trim());
                    if (sig) out.push(sig);
                    start = i + 1;
                }
            }
            const sig = sigOf(inner.slice(start).trim());
            if (sig) out.push(sig);
            return out;
        }

        /* the signature of one table entry: the type's vid (its name), or its
           tid for a base type where tid == vid. */
        function sigOf(entry) {
            let depth = 0;
            let atIndex = -1;
            for (let i = 0; i < entry.length; i++) {
                const c = entry[i];
                if (c === "[" || c === "(" || c === "{") depth++;
                else if (c === "]" || c === ")" || c === "}") depth--;
                else if (depth === 0 && c === "@") atIndex = i;
            }
            if (atIndex >= 0) {
                let vid = entry.slice(atIndex + 1).trim();
                if (vid.charAt(0) === "<" && vid.charAt(vid.length - 1) === ">") vid = vid.slice(1, -1);
                return vid;
            }
            /* no vid: a base type (int::T, …) — the tid before the top-level ::T */
            let d = 0;
            for (let i = 0; i + 2 < entry.length; i++) {
                const c = entry[i];
                if (c === "[" || c === "(" || c === "{") d++;
                else if (c === "]" || c === ")" || c === "}") d--;
                else if (d === 0 && c === ":" && entry[i + 1] === ":" && entry[i + 2] === "T")
                    return entry.slice(0, i).trim();
            }
            return entry;
        }

        /* the signature the machine names — painted with the mtron grammar,
           plain text when the highlighter has not loaded yet */
        function paintSignature(el, text) {
            if (window.hljs && window.hljs.getLanguage && window.hljs.getLanguage("mtron")) {
                try {
                    el.innerHTML = window.hljs.highlight(text, {language: "mtron", ignoreIllegals: true}).value;
                    return;
                } catch (ignored) {
                }
            }
            el.textContent = text;
        }

        /* the doc/decq read of a signature, quoted the way the machine wants:
           *<plus?int<=int&args=[int]&decq>
           the furi supplies the name; the query and args stay as the machine
           spelled them, with the characters the angle cannot carry raw taken
           to their percent form (rec{?} → rec%7B%3F%7D, (10) → %2810%29, …) */
        function escapeArgs(args) {
            return args.replace(/[(){}[\]?@<>#&=-]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());
        }

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
            let name = null;
            try { name = f(namePath).name(); } catch (ignored) { }
            if (!name) {
                const slash = namePath.lastIndexOf("/");
                name = slash > 0 ? namePath.slice(slash + 1) : namePath;
            }
            const query = qAt > 0 ? entry.slice(qAt, openAt > 0 ? openAt : entry.length) : "";
            const args = openAt > 0 ? escapeArgs(entry.slice(openAt + 1, closeAt)) : null;
            return "*<" + name + query + (args ? "&args=[" + args + "]" : "") + "&" + q + ">";
        }

        /* one row's doc/dec: evaluate the read over the structural socket and
           wear the answer as the standard object card — a docs::/decs:: rec is
           a read result with its own address, so parse it, and fall back to a
           plain raw card when it carries no envelope. the panel never owns
           the socket or the dialog */
        function showRead(entry, label) {
            const readExpr = signatureRead(entry, label + "q");
            const wear = (text) => {
                const clean = String(text == null ? "" : text).replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "");
                /* a docs::/decs:: answer is a normal obj: parse its envelope and
                   wear it as the standard card (panel when one is registered),
                   the read it came from sitting in the card's expr line */
                const env = Object.assign({raw: clean, expr: readExpr}, envelope(clean) || {shortTid: label});
                if (api && typeof api.open === "function")
                    api.open(env);
            };
            if (api && typeof api.mtron === "function") {
                api.mtron(readExpr).then(wear)
                    .catch((problem) => wear(problem && problem.message ? problem.message : problem));
            } else {
                wear(readExpr);
            }
        }

        /* a pinned card clips its contents; a card with an open table unclips */
        function unclip(tileEl, on) {
            const host = tileEl.closest(".obj-card");
            if (host) host.classList.toggle("drop-open", on);
        }

        function closeDrop() {
            if (!activeDrop) return;
            activeDrop.drop.hidden = true;
            activeDrop.actions.querySelectorAll("button.on").forEach((button) => button.classList.remove("on"));
            unclip(activeDrop.tile, false);
            activeDrop.key = null;
            activeDrop = null;
        }

        function wireActions(tileEl) {
            const actions = node("div", "machine-tile-actions");
            const drop = node("div", "machine-drop");
            drop.hidden = true;
            const pair = {tile: tileEl, actions: actions, drop: drop, key: null};

            function open(key) {
                /* clicking the already-open table closes it — a toggle, not a re-open */
                if (activeDrop === pair && pair.key === key) {
                    closeDrop();
                    return;
                }
                closeDrop();
                pair.key = key;
                const button = actions.querySelector('[data-table="' + key + '"]');
                if (button) button.classList.add("on");
                activeDrop = pair;
                unclip(tileEl, true);
                drop.hidden = false;
                drop.textContent = "";
                drop.appendChild(node("div", "machine-drop-empty", "reading ~/instset/" + key + "…"));
                sigRead(key).then((list) => {
                    if (activeDrop !== pair) return;
                    drop.textContent = "";
                    if (!list.length) {
                        drop.appendChild(node("div", "machine-drop-empty", "the machine names no " + key));
                        return;
                    }
                    list.forEach((text) => {
                        const row = node("div", "machine-drop-row");
                        const cell = node("button", "machine-drop-cell");
                        cell.type = "button";
                        cell.title = text + " · click to open";
                        cell.setAttribute("aria-label", "open " + text);
                        paintSignature(cell, text);
                        cell.addEventListener("click", () => {
                            if (api && api.open)
                                api.open({
                                    isType: key === "type",
                                    vid: text,
                                    raw: text,
                                    shortTid: text.split("/").pop() || text
                                });
                        });
                        row.appendChild(cell);
                        ["doc", "dec"].forEach((label) => {
                            const button = node("button", "machine-drop-btn", label);
                            button.type = "button";
                            button.addEventListener("click", () => showRead(text, label));
                            row.appendChild(button);
                        });
                        drop.appendChild(row);
                    });
                }).catch((problem) => {
                    if (activeDrop !== pair) return;
                    drop.textContent = "";
                    drop.appendChild(node("div", "machine-drop-empty",
                        String(problem && problem.message ? problem.message : problem)));
                });
            }

            SIG_TABLES.forEach((key) => {
                const button = node("button", "", key);
                button.type = "button";
                button.dataset.table = key;
                button.addEventListener("click", () => open(key));
                actions.appendChild(button);
            });

            drop.addEventListener("mousedown", (ev) => ev.stopPropagation());
            tileEl.appendChild(actions);
            tileEl.appendChild(drop);
        }

        function setTiles(items) {
            tiles.textContent = "";
            items.forEach((item) => {
                const el = tile(item.label, item.metric, item.sub, item.inherited);
                if (item.actions) wireActions(el);
                tiles.appendChild(el);
            });
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
                    { label: "instset", metric: sets + " sets", sub: aliases + " aliases", inherited: instsetR.inherited, actions: true },
                    { label: "compiler", metric: stages + "/4 stages", sub: checksOn + "/" + (Object.keys(checks).length || 5) + " checks on", inherited: compilerR.inherited },
                    { label: "network", metric: peers + " peers", sub: listeners + " listeners", inherited: peerR.inherited || rosterR.inherited },
                    { label: "processor", metric: computers + " computers", sub: traceStacks + " trace stacks", inherited: computerR.inherited || tracerR.inherited }
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

        /* a mousedown outside the tile closes its table (bound once per panel) */
        if (!box._outsideBound) {
            box._outsideBound = true;
            document.addEventListener("mousedown", (ev) => {
                if (activeDrop && !activeDrop.tile.contains(ev.target)) closeDrop();
            });
        }

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
        /* the tree is the VM's; frames pushed from the console must show up here */
        setInterval(() => {
            if (box.isConnected && !document.hidden) loadTree();
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
        const readJson = (api && api.json) || (() => Promise.resolve(null));
        const readFresh = (vid) => (api && api.evalJson)
            ? api.evalJson("*<" + vid + ">")
            : readJson();
        const base = (((env && env.vid) || "/network").replace(/\/+$/, "")) || "/network";
        const root = "/network";
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
                const network = await readJson();
                if (my !== token) return;
                if (network && typeof network === "object" && network.pattern) pattern.textContent = String(network.pattern);
                const roster = await readFresh(root + "/roster");
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


    /* docs and decs — the result of ?docq / ?decq. the machine makes these in
       the course of a read (there is no door to re-ask), so the card receives
       them as the raw rec: render it like every other card — one row per key,
       an address value as the page's usual clickable (plain click opens it,
       shift-click opens its ?docq), everything else painted mtron. */
    function paintMtron(el, text) {
        const t = String(text == null ? "" : text);
        if (window.hljs && window.hljs.getLanguage("mtron"))
            el.innerHTML = window.hljs.highlight(t, {language: "mtron"}).value;
        else el.textContent = t;
    }

    /* the top-level key=>value pairs of a rec's body — arrows and quotes in
       the values never split a pair */
    function recEntries(text) {
        const l = text.indexOf("[");
        const r = text.lastIndexOf("]");
        if (l < 0 || r <= l + 1) return [];
        const body = text.slice(l + 1, r);
        const pieces = [];
        let depth = 0, quote = null, start = 0;
        for (let i = 0; i < body.length; i++) {
            const ch = body[i];
            if (quote) {
                if (ch === "\\") i++;
                else if (ch === quote) quote = null;
                continue;
            }
            if (ch === "'" || ch === '"') { quote = ch; continue; }
            const next = body[i + 1];
            if (ch === "[" || ch === "(" || ch === "{" || (ch === "<" && next !== "=" && next !== ">")) depth++;
            else if (ch === "]" || ch === ")" || ch === "}" || (ch === ">" && next !== "=" && next !== "-")) depth--;
            else if (ch === "," && depth === 0) { pieces.push(body.slice(start, i)); start = i + 1; }
        }
        pieces.push(body.slice(start));
        const pairs = [];
        pieces.forEach((piece) => {
            const arrow = piece.indexOf("=>");
            if (arrow < 0) return;
            const key = piece.slice(0, arrow).trim();
            const value = piece.slice(arrow + 2).trim();
            if (key) pairs.push([key, value]);
        });
        return pairs;
    }

    function fieldsPanel(env, api) {
        const box = node("div", "mpanel");
        const text = env && env.raw ? String(env.raw) : "";
        const pairs = recEntries(text);
        if (!pairs.length) {
            box.appendChild(node("pre", "mpanel-raw", text));
            return box;
        }
        const open = (uri) => {
            const s = String(uri);
            if (api && api.open) api.open({raw: s, shortTid: s.split("/").slice(-1)[0]});
        };
        pairs.forEach(([key, value]) => {
            const row = node("div", "mpanel-row");
            row.appendChild(node("span", "mpanel-k", key));
            const v = node("span", "mpanel-v", "");
            if (value.startsWith("/")) {
                try {
                    v.appendChild(f(value).toElement(open));
                    row.appendChild(v);
                    box.appendChild(row);
                    return;
                } catch (ignored) { /* not an address (a signature) — paint it */ }
            }
            paintMtron(v, value);
            row.appendChild(v);
            box.appendChild(row);
        });
        return box;
    }

    register("docs", fieldsPanel);
    register("decs", fieldsPanel);


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
                const button = f(text).toElement(openTarget, "route-target");
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

    /* the address an auto-at / auto-deref reference points at — what a clickable
       cell opens. */
    function refTarget(text) {
        if (typeof text !== "string") return null;
        const m = /^![@*]\s*(.+)$/.exec(text.trim());
        if (!m) return null;
        let t = m[1].trim();
        if (t.charAt(0) === "<" && t.charAt(t.length - 1) === ">") t = t.slice(1, -1);
        return t;
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
        function renderCell(value) {
            if (Array.isArray(value)) {
                const span = node("span", "tble-cell");
                value.forEach((item, i) => {
                    if (i) span.appendChild(document.createTextNode(" · "));
                    span.appendChild(renderCell(item));
                });
                return span;
            }
            const target = refTarget(value);
            if (target) {
                const prefix = String(value).slice(0, 2);
                const b = node("button", "tble-ref", prefix + target.split("/").pop());
                b.type = "button";
                b.title = String(value) + " → " + target + " · shift-click for ?docq";
                b.addEventListener("click", (ev) => {
                    if (ev.shiftKey) {
                        api.open({ vid: target + "?docq", shortTid: "docs" });
                    } else {
                        api.open({ vid: target, raw: String(value) });
                    }
                });
                return b;
            }
            return document.createTextNode(brief(value));
        }

        function renderGrid(result) {
            let rows = null;
            if (Array.isArray(result))
                rows = result.length && result.every((r) => r && typeof r === "object" && !Array.isArray(r)) ? result : null;
            else if (result && typeof result === "object")
                rows = [result];
            if (!rows) {
                grid.textContent = "";
                grid.appendChild(node("div", "tble-empty", result == null ? "noobj" : (typeof result === "string" ? result : brief(result))));
                return;
            }
            const columns = Object.keys(rows[0]);
            const table = node("table", "tble-sheet");
            const thead = document.createElement("thead");
            const hr = document.createElement("tr");
            hr.appendChild(node("th", "tble-idx", "#"));
            columns.forEach((col) => hr.appendChild(node("th", "", col)));
            thead.appendChild(hr);
            table.appendChild(thead);
            const tbody = document.createElement("tbody");
            rows.forEach((row, idx) => {
                const tr = document.createElement("tr");
                tr.appendChild(node("td", "tble-idx", String(idx + 1)));
                columns.forEach((col) => {
                    const td = document.createElement("td");
                    td.appendChild(renderCell(row[col]));
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
            try {
                result = await api.evalJson(source);
            } catch (problem) {
                grid.textContent = "";
                grid.appendChild(node("div", "tble-empty", String(problem && problem.message ? problem.message : problem)));
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
            else if (f.kind === 'int') text = serialize(parseInt(v, 10));
            else if (f.kind === 'real') text = serialize({ real: parseFloat(v) });
            else if (f.kind === 'rec' || f.kind === 'lst') text = v;
            else text = serialize(String(v));
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
       >> projections, so nothing here parses the doc rec. */
    function closeBracket(text, openIdx) {
        let depth = 0;
        let quote = null;
        for (let i = openIdx; i < text.length; i++) {
            const c = text[i];
            if (quote) { if (c === "\\") i++; else if (c === quote) quote = null; continue; }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (c === "[") depth++;
            else if (c === "]") { depth--; if (depth === 0) return i + 1; }
        }
        return text.length;
    }

    function hasTopLevelArrow(text) {
        let depth = 0;
        let quote = null;
        for (let i = 0; i + 1 < text.length; i++) {
            const c = text[i];
            if (quote) { if (c === "\\") i++; else if (c === quote) quote = null; continue; }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (c === "[" || c === "(" || c === "{") depth++;
            else if (c === "]" || c === ")" || c === "}") depth--;
            else if (depth === 0 && c === "=" && text[i + 1] === ">") return true;
        }
        return false;
    }

    function addField(text, out) {
        const s = text.trim();
        if (!s) return;
        let depth = 0;
        let quote = null;
        let arrowAt = -1;
        for (let i = 0; i + 1 < s.length; i++) {
            const c = s[i];
            if (quote) { if (c === "\\") i++; else if (c === quote) quote = null; continue; }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (c === "[" || c === "(" || c === "{") depth++;
            else if (c === "]" || c === ")" || c === "}") depth--;
            else if (depth === 0 && c === "=" && s[i + 1] === ">") { arrowAt = i; break; }
        }
        if (arrowAt < 0) return;
        const key = s.slice(0, arrowAt).trim();
        const type = s.slice(arrowAt + 2).trim();
        if (key) out.push({key: key, type: type});
    }

    function parseFields(text, out) {
        const source = String(text == null ? "" : text).trim();
        if (!source) return;
        let depth = 0;
        let quote = null;
        let start = 0;
        for (let i = 0; i < source.length; i++) {
            const c = source[i];
            if (quote) { if (c === "\\") i++; else if (c === quote) quote = null; continue; }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (c === "[" || c === "(" || c === "{") depth++;
            else if (c === "]" || c === ")" || c === "}") depth--;
            else if (depth === 0 && c === ",") {
                addField(source.slice(start, i), out);
                start = i + 1;
            }
        }
        addField(source.slice(start), out);
    }

    /* a type obj's x-mtron: tid::T[?predicate][constructor]@vid */
    function parseType(text) {
        const source = String(text == null ? "" : text).trim();
        const out = {tid: "", vid: null, predicate: [], constructor: ""};
        let depth = 0;
        let quote = null;
        let tidEnd = -1;
        for (let i = 0; i + 2 < source.length; i++) {
            const c = source[i];
            if (quote) { if (c === "\\") i++; else if (c === quote) quote = null; continue; }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (c === "[" || c === "(" || c === "{") depth++;
            else if (c === "]" || c === ")" || c === "}") depth--;
            else if (depth === 0 && c === ":" && source[i + 1] === ":" && source[i + 2] === "T") { tidEnd = i; break; }
        }
        if (tidEnd > 0) out.tid = source.slice(0, tidEnd).trim();
        let rest = tidEnd >= 0 ? source.slice(tidEnd + 3) : "";
        let atIdx = -1;
        depth = 0;
        quote = null;
        for (let i = 0; i < rest.length; i++) {
            const c = rest[i];
            if (quote) { if (c === "\\") i++; else if (c === quote) quote = null; continue; }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (c === "[" || c === "(" || c === "{") depth++;
            else if (c === "]" || c === ")" || c === "}") depth--;
            else if (depth === 0 && c === "@") atIdx = i;
        }
        if (atIdx >= 0) {
            let vid = rest.slice(atIdx + 1).trim();
            if (vid.charAt(0) === "<" && vid.charAt(vid.length - 1) === ">") vid = vid.slice(1, -1);
            out.vid = vid;
            rest = rest.slice(0, atIdx).trim();
        }
        let i = 0;
        while (i < rest.length) {
            while (i < rest.length && rest[i] !== "[") i++;
            if (i >= rest.length) break;
            const end = closeBracket(rest, i);
            const group = rest.slice(i, end);
            if (group.charAt(1) === "?") parseFields(group.slice(2, -1), out.predicate);
            else if (group !== "[]" && group !== "[,]") {
                if (hasTopLevelArrow(group.slice(1, -1))) parseFields(group.slice(1, -1), out.predicate);
                else out.constructor = group;
            }
            i = end;
        }
        return out;
    }

    function paintSig(el, text) {
        if (window.hljs && window.hljs.getLanguage && window.hljs.getLanguage("mtron")) {
            try {
                el.innerHTML = window.hljs.highlight(text, {language: "mtron", ignoreIllegals: true}).value;
                return;
            } catch (ignored) { }
        }
        el.textContent = text;
    }

    register("type", async function (env, api) {
        const box = node("div", "mpanel type-panel");
        const vid = String((env && env.vid) || "").trim();
        if (!vid) { box.appendChild(node("div", "mpanel-empty", "no type address")); return box; }

        const [typeText, tid, doc] = await Promise.all([
            (api && api.mtron ? api.mtron("*<" + vid + ">") : Promise.resolve("")).catch(() => ""),
            (api && api.evalJson ? api.evalJson("*<" + vid + ">.tid()") : Promise.resolve("")).catch(() => ""),
            (api && api.evalJson ? api.evalJson("*<" + vid + "?docq>.-<[>>desc,>>args.>-.as(lst::T).>-[,],>>example]") : Promise.resolve(null)).catch(() => null)
        ]);

        const parsed = parseType(typeText);
        const tidText = String(tid == null ? "" : tid).trim();
        const refined = (tidText && tidText !== "noobj" && !/^(fail|parse error)/i.test(tidText)) ? tidText : parsed.tid;
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
                    api.open({tid: "type", vid: refined, raw: refined, shortTid: refined.split("/").pop() || refined});
            });
            head.appendChild(ref);
        }
        box.appendChild(head);

        if (desc && desc !== "noobj") box.appendChild(node("p", "type-desc", desc));

        if (parsed.predicate.length) {
            box.appendChild(node("div", "type-section", "predicate"));
            const table = node("table", "type-pred");
            const tbody = document.createElement("tbody");
            parsed.predicate.forEach((field) => {
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

        if (parsed.constructor) {
            box.appendChild(node("div", "type-section", "constructor"));
            const ctor = node("pre", "type-ctor", "");
            paintSig(ctor, parsed.constructor.slice(1, -1));
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

export { register, registerParent, resolve, parentOf, panels, parents };
