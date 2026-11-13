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
 * UMD: `window.MtronPanels` in a page, `module.exports` under node (for tests).
 */
(function (root, factory) {
    if (typeof module === "object" && module.exports) module.exports = factory();
    else root.MtronPanels = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
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

        function setTiles(items) {
            tiles.textContent = "";
            items.forEach((item) => tiles.appendChild(tile(item.label, item.metric, item.sub, item.inherited)));
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
                    { label: "compiler", metric: stages + "/4 stages", sub: checksOn + "/" + (Object.keys(checks).length || 5) + " checks on", inherited: compilerR.inherited },
                    { label: "network", metric: peers + " peers", sub: listeners + " listeners", inherited: peerR.inherited || rosterR.inherited },
                    { label: "processor", metric: computers + " computers", sub: traceStacks + " trace stacks", inherited: computerR.inherited || tracerR.inherited },
                    { label: "instset", metric: sets + " sets", sub: aliases + " aliases", inherited: instsetR.inherited }
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
        /* the tree is the VM's; frames pushed from the console must show up here */
        setInterval(() => {
            if (box.isConnected && !document.hidden) loadTree();
        }, 4000);
        return box;
    }

    register("/m/mach/machine", machinePanel);
    register("machine", machinePanel);


    /* the web surfaces: the server obj's own route table, not a probe */
    function routePanel(title, value) {
        const box = node("div", "mpanel");
        const head = node("div", "mpanel-head");
        head.appendChild(node("span", "mpanel-k", title));
        const target = value && (value.origin || value.host);
        if (target) head.appendChild(node("span", "mpanel-pattern", brief(target)));
        box.appendChild(head);
        const routes = value && value.route && typeof value.route === "object" ? value.route : {};
        const paths = Object.keys(routes);
        if (!paths.length) box.appendChild(node("div", "mpanel-empty", "no routes"));
        paths.forEach((path) => {
            const row = node("div", "mpanel-row");
            row.appendChild(node("span", "mpanel-k", path));
            row.appendChild(node("span", "mpanel-v", brief(routes[path])));
            box.appendChild(row);
        });
        return box;
    }

    register("/m/web/http/http_server", async function (env, api) {
        return routePanel("http_server", await api.json());
    });

    register("/m/web/ws/ws_server", async function (env, api) {
        return routePanel("ws_server", await api.json());
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

    return { register, registerParent, resolve, parentOf, panels, parents };
});
