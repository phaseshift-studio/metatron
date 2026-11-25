import * as MtronEnvelope from '../sdk/mtron-envelope.js';
import { parse as parseValue } from '../sdk/obj_mtron_serializer.js';
import * as MtronIO from '../sdk/mtron-io.js';
import * as MtronObjPanels from '../ui/mtron-obj-panels.js';
import { typesetMath } from '../ui/mathjax.js';

"use strict";

/* ── configuration ─────────────────────────────────────────────────────── */
const params = new URLSearchParams(location.search);
const metaContent = (name) => {
    const el = document.querySelector('meta[name="' + name + '"]');
    return el ? el.content || "" : "";
};
const host = location.hostname || "localhost";
const secure = location.protocol === "https:";
const offline = location.protocol === "file:";
const httpBase = params.get("http") ||
    (offline ? "http://" + host + ":8777" : location.origin);

/* bin/wsplus appends the codecs it speaks to the endpoint; so does the console, so the uri in the
   page is the uri you would hand the CLI. a ?ws= that already names codecs keeps its own. */
function codecUrl(base) {
    try {
        const url = new URL(base, location.href);
        /* a MIME type is a query value with a slash. the websocket server matches the codec
           string LITERALLY, and wsplus writes it unescaped (python's quote() leaves '/'),
           so this writes it unescaped too — URLSearchParams would spell %2F and miss. */
        const add = (key, value) => {
            if (url.searchParams.has(key)) return;
            const pair = key + "=" + encodeURIComponent(value).replace(/%2F/gi, "/");
            url.search += (url.search ? "&" : "?") + pair;
        };
        add("in", "application/x-mtron");
        add("out", "application/x-mtron-ui");   // human form: pretty, indented, readable
        return url.toString();
    } catch (ignored) {
        return base;
    }
}

/* the page object is the APP's, never the instance's: a deployment names it in
   metatron:page, else it sits beside the app (the served directory), else the
   shipped path. an instance only ever supplies the machine's objects. */
function defaultPage() {
    const declared = metaContent("metatron:page");
    if (declared) return declared;
    const at = location.pathname || "/";
    const mfs = at.match(/^\/mfs\/(.*)$/);
    if (mfs) return "mfs:" + mfs[1] + "default.mtron";
    return "mfs:boot/space/web/default.mtron";
}

/* this page's own machine: the roster is read here, and it is the machine the
   "local" link returns to. everything else — the page object, the data, the
   console — belongs to the instance a tab stands for. */
const localBase = (secure ? "wss:" : "ws:") + "//" + host + ":8555/mtron";
const localUrl = codecUrl(localBase);

/* the machine the page boots on: ?ws= (or the meta tag), else this machine.
   activating a tab rewrites ?ws=, so a reload reopens the same machine. */
const bootBase = params.get("ws") || metaContent("metatron:ws") || localBase;

const rootAddr = metaContent("metatron:root");

const $ = (sel, root) => (root || document).querySelector(sel);
const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

/* every instance wears its own copy of this page's <main>, cloned here — before
   any console output or layout is rendered — so each starts from the same clean
   page. only the active instance's copy is in the document, so ids stay unique
   and $("#out") / $("#page-loaded") always resolve to the machine on screen; the
   others keep theirs detached with their connections open. */
const PAGE_PANEL = $("#page-panel");
const PAGE_FOOTER = PAGE_PANEL.querySelector("footer");
const MAIN_TEMPLATE = PAGE_PANEL.querySelector("main").cloneNode(true);

/* the instance on screen; its <main> is the one in the document */
let active = null;

/* ── small utilities ───────────────────────────────────────────────────── */
function withTimeout(ms) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), ms);
    return {signal: ctrl.signal, done: () => clearTimeout(timer)};
}

function setDot(el, state) {
    if (!el) return;
    el.classList.remove("busy", "up", "warn", "bad");
    if (state) el.classList.add(state);
}

const ms = (n) => n + " ms";

/* ── card span ──────────────────────────────────────────────────────────
   a splash_card's span is [col=>n,row=>n]; a field the page leaves out is
   the UI's choice (1). the grid is 4 columns, so col is clamped to it. */
/* a PlatonicObj collapsed to a plain JS value (uris -> strings, recs -> objects).
   the page config (splash_page/card) is consumed through this; the browsed objs
   stay PlatonicObj. */
function plain(obj) {
    if (obj == null) return undefined;
    if (typeof obj !== "object") return obj;
    if (obj.isNoObj()) return null;
    if (obj.isUri()) return obj.js.toString();
    if (obj.isStr() || obj.isInt() || obj.isReal() || obj.isBool() || obj.isBytes() || obj.isFail()) return obj.js;
    if (obj.isLst()) return obj.js.map(plain);
    if (obj.isRel()) return [plain(obj.js[0]), plain(obj.js[1])];
    if (obj.isRec()) {
        const o = {};
        for (const [k, v] of obj.js) o[k.toString()] = plain(v);
        return o;
    }
    return obj.js;
}

function spanOf(value) {
    const rec = value && value.isRec ? plain(value) : (value && typeof value === "object" ? value : {});
    return {
        col: Math.max(1, Math.min(4, Number(rec.col) || 1)),
        row: rec.row == null ? null : Math.max(1, Number(rec.row) || 1)
    };
}

function wordOf(value) {
    if (value == null) return null;
    const p = plain(value);
    return p == null ? null : String(p);
}

/* one place that turns a col/row pair into the grid placement */
function applySpan(el, col, row) {
    const span = spanOf({col: col, row: row});
    el.dataset.col = String(span.col);
    if (span.row != null) {
        el.dataset.row = String(span.row);
        el.style.setProperty("--rows", String(span.row));
    } else {
        delete el.dataset.row;
        el.style.removeProperty("--rows");
    }
    el.style.gridColumn = span.col > 1 ? "span " + span.col : "";
    el.style.gridRow = (span.row != null && span.row > 1) ? "span " + span.row : "";
    el.style.alignSelf = (span.row != null && span.row > 1) ? "stretch" : "";
}

/* ── card move + resize ─────────────────────────────────────────────────
   a card is moved by dragging its header grip to reorder it in its grid (the
   grid is auto-flow, so position IS dom order — the order savePage() keeps);
   a card is resized by dragging its corner grip to change the col/row span.
   both touch only the card's dataset and dom order, which pageCards() already
   reads back, so drag + resize persist through the existing save path. */
function nearestObjCard(grid, root, x, y) {
    let best = null, bestD = Infinity, bestBefore = false;
    Array.from(grid.children).forEach((objCard) => {
        if (objCard === root || !(objCard.dataset && objCard.dataset.tid)) return;
        const r = objCard.getBoundingClientRect();
        const cx = r.left + r.width / 2;
        const cy = r.top + r.height / 2;
        const d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
        if (d < bestD) {
            bestD = d;
            best = objCard;
            bestBefore = y < r.top ? true : (y > r.bottom ? false : (x < cx));
        }
    });
    return best ? {objCard: best, before: bestBefore} : null;
}

function makeDraggable(root) {
    const grip = root.querySelector(".obj-card-grip");
    if (!grip) return;
    grip.addEventListener("pointerdown", (ev) => {
        if (ev.button !== 0) return;
        ev.preventDefault();
        ev.stopPropagation();
        const grid = root.parentElement;
        let target = null;
        let before = false;
        root.classList.add("dragging");
        grip.setPointerCapture(ev.pointerId);
        const move = (e) => {
            const found = nearestObjCard(grid, root, e.clientX, e.clientY);
            if (found) {
                before = found.before;
                if (found.objCard !== target) {
                    if (target) target.classList.remove("drop-target");
                    target = found.objCard;
                    target.classList.add("drop-target");
                }
            } else if (target) {
                target.classList.remove("drop-target");
                target = null;
            }
        };
        const up = () => {
            if (target) {
                target.classList.remove("drop-target");
                if (before) grid.insertBefore(root, target);
                else target.insertAdjacentElement("afterend", root);
            }
            root.classList.remove("dragging");
            grip.removeEventListener("pointermove", move);
            grip.removeEventListener("pointerup", up);
            grip.removeEventListener("pointercancel", up);
        };
        grip.addEventListener("pointermove", move);
        grip.addEventListener("pointerup", up);
        grip.addEventListener("pointercancel", up);
    });
}

function makeResizable(root) {
    const grip = root.querySelector(".obj-card-resize");
    if (!grip) return;
    grip.addEventListener("pointerdown", (ev) => {
        if (ev.button !== 0) return;
        ev.preventDefault();
        ev.stopPropagation();
        const grid = root.parentElement;
        const rect = root.getBoundingClientRect();
        const startX = ev.clientX;
        const startY = ev.clientY;
        const startW = rect.width;
        const startH = rect.height;
        const startCol = Number(root.dataset.col) || 1;
        const startRow = Number(root.dataset.row) || 1;
        const gs = getComputedStyle(grid);
        const gap = parseFloat(gs.columnGap) || parseFloat(gs.rowGap) || 14;
        /* columns are uniform (4 per row); rows are auto and uneven, so a row
           is the card's own current height. each stride also adds one gap. */
        const colStride = (grid.clientWidth - gap * 3) / 4 + gap;
        const rowStride = startH / startRow + gap;
        const MAX_ROW = 6;
        root.classList.add("resizing");
        grip.setPointerCapture(ev.pointerId);
        const move = (e) => {
            const col = Math.max(1, Math.min(4, Math.round((startW + (e.clientX - startX)) / colStride)));
            const row = Math.max(1, Math.min(MAX_ROW, Math.round((startH + (e.clientY - startY)) / rowStride)));
            if (col !== Number(root.dataset.col) || row !== Number(root.dataset.row)) {
                applySpan(root, col, row);
            }
        };
        const up = () => {
            root.classList.remove("resizing");
            grip.removeEventListener("pointermove", move);
            grip.removeEventListener("pointerup", up);
            grip.removeEventListener("pointercancel", up);
        };
        grip.addEventListener("pointermove", move);
        grip.addEventListener("pointerup", up);
        grip.addEventListener("pointercancel", up);
    });
}

/* ── http probes ───────────────────────────────────────────────────────── */
async function probe(path, msTimeout) {
    const t = performance.now();
    const guard = withTimeout(msTimeout || 3000);
    try {
        const r = await fetch(new URL(path, httpBase), {
            method: "GET",
            cache: "no-store",
            redirect: "follow",
            headers: {"Accept": "application/json, text/plain;q=0.9, */*;q=0.8"},
            signal: guard.signal
        });
        const body = await r.text();
        return {reachable: true, status: r.status, body: body, ms: Math.round(performance.now() - t)};
    } catch (e) {
        return {reachable: false, status: 0, body: "", ms: Math.round(performance.now() - t), error: e.name};
    } finally {
        guard.done();
    }
}

/* ── websocket probe ───────────────────────────────────────────────────── */
function probeWs(url, msTimeout) {
    return new Promise((resolve) => {
        const t = performance.now();
        let settled = false;
        let socket = null;
        const finish = (ok, why) => {
            if (settled) return;
            settled = true;
            try {
                if (socket) socket.close();
            } catch (ignored) {
            }
            resolve({ok: ok, ms: Math.round(performance.now() - t), why: why});
        };
        const timer = setTimeout(() => finish(false, "timeout"), msTimeout || 2200);
        try {
            socket = new WebSocket(url);
        } catch (e) {
            clearTimeout(timer);
            finish(false, "unsupported");
            return;
        }
        socket.onopen = () => {
            clearTimeout(timer);
            finish(true);
        };
        socket.onerror = () => {
            clearTimeout(timer);
            finish(false, "error");
        };
        socket.onclose = (e) => {
            clearTimeout(timer);
            finish(false, "closed " + e.code);
        };
    });
}

/* ── mtron syntax ────────────────────────────────────────────────────────
   The docs' highlighter is a standard highlight.js core plus a grammar
   GENERATED from conf/nanorc/mtron.nanorc (NanorcToHljs) — the nanorc is
   the standard of truth. It is loaded from a mount the boot provides
   (`metatron:highlight`, default /highlight/); if it is not there the
   console stays plain text, never broken. */
const hlBase = params.get("hl") || metaContent("metatron:highlight") || "/highlight/";
let HL = null;

const CONTROLS = /\u001b\[[0-9;?]*[ -/]*[@-~]/g;   // CSI: SGR colour, cursor moves, erase

function paint(el, text, kind) {
    text = String(text == null ? "" : text).replace(CONTROLS, "");
    el._text = text;
    el._kind = kind;
    if (HL && kind !== "sys" && text.length < 500000) {
        const language = kind === "json" ? "json" : "mtron";
        if (language !== "json" || (HL.getLanguage && HL.getLanguage("json"))) {
            try {
                el.innerHTML = HL.highlight(text, {language: language, ignoreIllegals: true}).value;
                typesetMath(el, text);
                return;
            } catch (ignored) {
                // a grammar that cannot read a fragment is not a reason to lose the fragment
            }
        }
    }
    el.textContent = text;
    typesetMath(el, text);
}

function retint(el) {
    if (undefined !== el._text)
        paint(el, el._text, el._kind);
}

function highlighterReady() {
    HL = window.hljs && window.hljs.getLanguage && window.hljs.getLanguage("mtron") ? window.hljs : null;
    /* the grammar arrived after some consoles had already printed: re-tint every
       instance's output, not just the one on screen */
    INSTANCES.forEach((instance) => {
        if (instance.hlNote) instance.hlNote.textContent = HL ? "syntax: mtron" : "syntax: plain";
        if (HL) $$(".line", instance.out).forEach(retint);
    });
    if (HL && modalObjCard.views.mtron) renderObjCard(modalObjCard, modalHost);
}

function loadScript(src) {
    return new Promise((resolve) => {
        const script = document.createElement("script");
        script.src = src;
        script.onload = () => resolve(true);
        script.onerror = () => resolve(false);
        document.head.appendChild(script);
    });
}

async function loadHighlighter() {
    if (!hlBase) return;
    try {
        if (!(await loadScript(hlBase + "highlight.min.js"))) return;
        if (!(await loadScript(hlBase + "languages/mtron.min.js"))) return;
        await loadScript(hlBase + "languages/json.min.js");   // the JSON views, when present
    } finally {
        highlighterReady();
    }
}

/* ── console: one socket per instance, one answer per frame, in order ─────
   every connected machine's console is live at once: its own socket, output,
   history, references and machine frame. the ACTIVE instance's console is what
   the module-level names below (line, run, evaluateThen) drive, so the page
   code and the panels keep calling them unchanged. */

/* a reply frame is text OR bytes: application/x-mtron comes back as a text frame, the UI
   (and other binary serializations) as a binary frame — bin/wsplus decodes bytes before it
   prints, so the browser does the same rather than reporting a frame it cannot read. */
async function frameText(data) {
    if (typeof data === "string") return data;
    if (data instanceof Blob) return await data.text();
    if (data instanceof ArrayBuffer) return new TextDecoder().decode(data);
    return String(data);
}

function shortVid(vid) {
    if (!vid) return "(no vid)";
    return vid.length > 30 ? "…" + vid.slice(-27) : vid;
}

function createConsole(instance) {
    const out = instance.out;
    const REPL = {
        socket: null,
        chain: Promise.resolve(),
        /* the machine frame this console stands in; null means the root. every
           evaluation is framed so the console and its panels act in one machine. */
        frame: null,
        references: [],
        commands: [],
        cursor: 0
    };
    /* history — persisted so a reload keeps the last few expressions; per
       instance, so each machine's console remembers its own */
    const historyKey = "metatron.console.history" + (instanceIsLocal(instance) ? "" : ":" + instance.name);
    try {
        REPL.commands = JSON.parse(localStorage.getItem(historyKey) || "[]");
    } catch (ignored) {
        REPL.commands = [];
    }
    REPL.cursor = REPL.commands.length;

    function line(kind, text) {
        const el = document.createElement("span");
        el.className = "line " + kind;
        paint(el, text, kind);
        if (kind === "res")
            annotateObjects(el, text, (obj) => openNested(obj));
        out.appendChild(el);
        while (out.childElementCount > 500) out.removeChild(out.firstElementChild);
        out.scrollTop = out.scrollHeight;
    }

    function welcome() {
        out.textContent = "";
        line("sys", "metatron console — one message is one expression, evaluated on this machine");
        line("sys", "same endpoint as bin/wsplus:  " + instance.wsBase);
        line("sys", "in application/x-mtron  ·  out application/x-mtron-ui (pretty)  ·  one frame in, one frame out");
        line("sys", "try  *</memory/space>   or   !*boot/args");
    }

    function connect() {
        if (REPL.socket && REPL.socket.readyState === WebSocket.OPEN) return Promise.resolve(REPL.socket);
        if (REPL.socket && REPL.socket.readyState === WebSocket.CONNECTING) {
            return new Promise((resolve, reject) => {
                REPL.socket.addEventListener("open", () => resolve(REPL.socket), {once: true});
                REPL.socket.addEventListener("error", () => reject(new Error("cannot reach " + instance.wsUrl)), {once: true});
            });
        }
        return new Promise((resolve, reject) => {
            const ws = new WebSocket(instance.wsUrl);
            const timer = setTimeout(() => reject(new Error("connect timeout")), 4000);
            ws.onopen = () => {
                clearTimeout(timer);
                REPL.socket = ws;
                setDot(instance.dConsole, "up");
                instance.mConsole.textContent = "connected";
                resolve(ws);
            };
            ws.onerror = () => {
                clearTimeout(timer);
                setDot(instance.dConsole, "bad");
                instance.mConsole.textContent = "not connected";
                reject(new Error("cannot reach " + instance.wsUrl));
            };
            ws.onclose = () => {
                if (REPL.socket === ws) REPL.socket = null;
                setDot(instance.dConsole, "down");
                instance.mConsole.textContent = "closed";
            };
        });
    }

    function framePrefix() {
        return REPL.frame ? "move(<" + REPL.frame + ">)." : "";
    }

    function evaluate(expr) {
        const framed = framePrefix() + String(expr == null ? "" : expr);
        return connect().then((ws) => new Promise((resolve, reject) => {
            const done = (value, error) => {
                clearTimeout(timer);
                ws.removeEventListener("message", answer);
                cleanup();
                if (error) reject(error);
                else resolve(value);
            };
            const cleanup = settleOnClose(ws, () => done(null, new Error("connection closed")));
            const timer = setTimeout(() => done(null, new Error("no answer in 30 s")), 30000);
            const answer = async (ev) => {
                try {
                    done(await frameText(ev.data));
                } catch (error) {
                    done(null, error);
                }
            };
            ws.addEventListener("message", answer);
            ws.send(framed);
        }));
    }

    function evaluateThen(expr) {
        const pending = REPL.chain.then(() => evaluate(expr));
        REPL.chain = pending.catch(() => {
        });
        return pending;
    }

    /* ── references: what the console just named ──────────────────────────
       a result's envelope carries its type (tid) and location (vid); the pill row
       becomes those references, and clicking one reads the object's JSON form.
       x-mtron is complete (the ui form clips), so this is the machine protocol. */
    function renderReferences() {
        const row = instance.chips;
        if (!row) return;
        row.textContent = "";
        REPL.references.forEach((env) => {
            const chip = document.createElement("button");
            chip.type = "button";
            chip.className = "chip ref";
            const tid = document.createElement("span");
            tid.className = "tid";
            tid.textContent = env.shortTid || "value";
            const vid = document.createElement("span");
            vid.className = "vid";
            vid.textContent = " · " + shortVid(env.vid);
            chip.appendChild(tid);
            chip.appendChild(vid);
            chip.title = [env.tid, env.vid].filter(Boolean).join(" @ ");
            chip.addEventListener("click", () => inspectReference(env));
            row.appendChild(chip);
        });
    }

    function reference(text, expr) {
        const env = MtronEnvelope ? MtronEnvelope.parse(text) : null;
        if (!env) return;
        env.expr = expr ? String(expr).trim() : null;
        /* one chip per (tid, vid) — and two answers can share both: every explain()
           answer is an explanation at NO vid, so the chip keeps its place but takes
           the LATEST expression and text, or a card opened from it would re-run the
           expression that produced the previous answer. */
        const same = REPL.references.find((r) => r.tid === env.tid && r.vid === env.vid);
        if (same) {
            same.expr = env.expr;
            same.raw = env.raw;
        } else {
            REPL.references.unshift(env);
            if (REPL.references.length > 8) REPL.references.pop();
        }
        renderReferences();
    }

    function run(expr, echo) {
        expr = String(expr || "").trim();
        if (!expr) return Promise.resolve();
        if (echo !== false) line("cmd", expr);
        return evaluateThen(expr).then((result) => {
            line("res", result);
            reference(result, expr);
            return result;
        }).catch((error) => {
            line("err", error && error.message ? error.message : String(error));
        });
    }

    function remember(expr) {
        expr = String(expr || "").trim();
        if (!expr || REPL.commands[REPL.commands.length - 1] === expr) return;
        REPL.commands.push(expr);
        if (REPL.commands.length > 100) REPL.commands = REPL.commands.slice(-100);
        REPL.cursor = REPL.commands.length;
        try {
            localStorage.setItem(historyKey, JSON.stringify(REPL.commands));
        } catch (ignored) {
        }
    }

    instance.replForm.addEventListener("submit", (ev) => {
        ev.preventDefault();
        const value = instance.expr.value;
        instance.expr.value = "";
        remember(value);
        run(value);
    });

    instance.clearButton.addEventListener("click", () => {
        welcome();
        instance.expr.focus();
    });

    instance.expr.addEventListener("keydown", (ev) => {
        if (ev.key === "ArrowUp") {
            if (!REPL.commands.length) return;
            ev.preventDefault();
            REPL.cursor = Math.max(0, REPL.cursor - 1);
            instance.expr.value = REPL.commands[REPL.cursor];
            instance.expr.setSelectionRange(instance.expr.value.length, instance.expr.value.length);
        } else if (ev.key === "ArrowDown") {
            if (!REPL.commands.length) return;
            ev.preventDefault();
            REPL.cursor = Math.min(REPL.commands.length, REPL.cursor + 1);
            instance.expr.value = REPL.cursor === REPL.commands.length ? "" : REPL.commands[REPL.cursor];
        } else if (ev.key === "l" && (ev.ctrlKey || ev.metaKey)) {
            ev.preventDefault();
            welcome();
        }
    });

    setDot(instance.dConsole, "down");
    instance.mConsole.textContent = "not connected";
    welcome();
    return Object.assign(REPL, {line, welcome, connect, evaluate, evaluateThen, run, remember});
}

/* the ACTIVE instance's console — the names the page code and the panels call */
const line = (kind, text) => {
    if (active) active.console.line(kind, text);
};
const evaluateThen = (expr) => active.console.evaluateThen(expr);
const run = (expr, echo) => active.console.run(expr, echo);

/* readers: one socket per output codec. x-mtron is the machine form, x-mtron-ui the
   human form (clipped), json the structure. each keeps its own queue and cache —
   per instance, so one cache per machine; the local pool serves only the roster. */
const localReaders = new Map();

function readerUrlFor(base, mime) {
    const url = new URL(base, location.href);
    const query = url.search.replace(/^\?/, "").split("&")
        .filter((part) => part && !/^in=/.test(part) && !/^out=/.test(part));
    query.push("in=application/x-mtron");
    query.push("out=" + mime);
    url.search = "?" + query.join("&");
    return url.toString();
}

function readerFor(mime, pool) {
    let reader = pool.get(mime);
    if (!reader) {
        reader = {socket: null, chain: Promise.resolve(), cache: new Map()};
        pool.set(mime, reader);
    }
    return reader;
}

function connectReader(mime, base, pool) {
    const reader = readerFor(mime, pool);
    if (reader.socket && reader.socket.readyState === WebSocket.OPEN) return Promise.resolve(reader.socket);
    return new Promise((resolve, reject) => {
        const socket = new WebSocket(readerUrlFor(base, mime));
        const timer = setTimeout(() => reject(new Error(mime + " reader timeout")), 5000);
        socket.onopen = () => {
            clearTimeout(timer);
            reader.socket = socket;
            resolve(socket);
        };
        socket.onerror = () => {
            clearTimeout(timer);
            reject(new Error("cannot open the " + mime + " reader"));
        };
        socket.onclose = () => {
            if (reader.socket === socket) reader.socket = null;
        };
    });
}

/* A PENDING FRAME SETTLES WHEN ITS SOCKET GOES AWAY. the readers are queued (one
   socket per codec), so a request whose connection dies must answer at once: a
   machine whose sys_stat aborts the connection otherwise holds every panel read
   behind it for the full timeout (15 s), which reads as a page that loads but never
   reacts. returns the cleanup the normal settle paths call. */
function settleOnClose(socket, settle) {
    const gone = () => settle();
    socket.addEventListener("close", gone, {once: true});
    socket.addEventListener("error", gone, {once: true});
    return () => {
        socket.removeEventListener("close", gone);
        socket.removeEventListener("error", gone);
    };
}

function readVia(mime, vid, base, pool) {
    const reader = readerFor(mime, pool);
    if (reader.cache.has(vid)) return Promise.resolve(reader.cache.get(vid));
    const run = reader.chain.then(() => connectReader(mime, base, pool)).then((socket) => new Promise((resolve, reject) => {
        const done = (value, error) => {
            clearTimeout(timer);
            socket.removeEventListener("message", answer);
            cleanup();
            if (error) reject(error);
            else resolve(value);
        };
        const cleanup = settleOnClose(socket, () => done(null, new Error(mime + " reader closed")));
        const timer = setTimeout(() => done(null, new Error("no " + mime + " answer in 15 s")), 15000);
        const answer = async (ev) => {
            const text = await frameText(ev.data);
            let value;
            try {
                value = JSON.parse(text);
            } catch (ignored) {
                value = text;
            }
            reader.cache.set(vid, value);
            done(value);
        };
        socket.addEventListener("message", answer);
        socket.send(vid.charAt(0) === "<" ? "*" + vid : "*<" + vid + ">");
    }));
    reader.chain = run.catch(() => {
    });
    return run;
}

/* one instance's data */
function readAsOn(instance, mime, vid) {
    return readVia(mime, vid, instance.wsBase, instance.readers);
}

/* the active instance's data — the names the page code and the panels call */
function readAs(mime, vid) {
    return readAsOn(active, mime, vid);
}

/* the app's own page object */
function localRead(mime, vid) {
    return readVia(mime, vid, localBase, localReaders);
}

function readJsonOn(instance, vid) {
    return readAsOn(instance, "application/json", vid);
}

function readJson(vid) {
    return readJsonOn(active, vid);
}

function readPrettyOn(instance, vid) {
    return readAsOn(instance, "application/x-mtron-ui", vid)
        .then((value) => typeof value === "string" ? value : String(value));
}

function readPretty(vid) {
    return readPrettyOn(active, vid);
}

/* evaluate an arbitrary expression on the JSON socket — a panel that needs a
   projection (a tree, a count) instead of one object's value. */
function evalJsonOn(instance, expr) {
    const mime = "application/json";
    const reader = readerFor(mime, instance.readers);
    const run = reader.chain.then(() => connectReader(mime, instance.wsBase, instance.readers)).then((socket) => new Promise((resolve, reject) => {
        const done = (value, error) => {
            clearTimeout(timer);
            socket.removeEventListener("message", answer);
            cleanup();
            if (error) reject(error);
            else resolve(value);
        };
        const cleanup = settleOnClose(socket, () => done(null, new Error("connection closed")));
        const timer = setTimeout(() => done(null, new Error("no json answer in 15 s")), 15000);
        const answer = async (ev) => {
            const text = await frameText(ev.data);
            try {
                done(JSON.parse(text));
            } catch (ignored) {
                done(text);
            }
        };
        socket.addEventListener("message", answer);
        socket.send(String(expr));
    }));
    reader.chain = run.catch(() => {
    });
    return run;
}

function evalJson(expr) {
    return evalJsonOn(active, expr);
}

/* ── the object card ──────────────────────────────────────────────────────
   one algorithm, two hosts: the dialog (expression line, copy, close) and a
   pinned card (title left, vid right, unpin). the card owns its views and
   reads; the codecs are shared. */
function emptyObjCard() {
    return {
        views: {},
        tab: "mtron",
        env: null,
        prettyLoaded: false,
        jsonLoaded: false,
        objPanel: null,
        history: []
    };
}

function objCardButton(host, tab) {
    return host.tabs.querySelector('[data-tab="' + tab + '"]');
}

/* a card reads from the instance that owns it: a page's cards from their own
   machine (they may mount while another instance is active), the dialog's from
   whichever machine is active when it opens. */
function instOf(host) {
    return (host && host.instance) || active;
}

function objCardDot(host, state) {
    if (host.dot) setDot(host.dot, state);
}

function resetObjCard(objCard, host, keepHistory) {
    objCard.views = {};
    objCard.tab = "mtron";
    objCard.env = null;
    objCard.prettyLoaded = false;
    objCard.jsonLoaded = false;
    objCard.objPanel = null;
    if (!keepHistory) objCard.history = [];
    host.tabs.querySelectorAll(".popup-tab").forEach((button) => {
        button.disabled = button.dataset.tab === "json" || button.dataset.tab === "panel";
        button.setAttribute("aria-selected", String(button.dataset.tab === "mtron"));
    });
    const panelTab = objCardButton(host, "panel");
    if (panelTab) panelTab.hidden = true;
    host.out.textContent = "";
    if (host.expr) host.expr.textContent = "";
}

function renderObjCard(objCard, host) {
    const view = objCard.views[objCard.tab];
    host.tabs.querySelectorAll(".popup-tab").forEach((button) => {
        button.setAttribute("aria-selected", String(button.dataset.tab === objCard.tab));
    });
    if (!view) {
        host.out.textContent = "";
        host.out.classList.remove("panel-view");
        return;
    }
    host.out.classList.toggle("panel-view", view.kind === "dom");
    if (view.kind === "dom") {
        host.out.replaceChildren(view.node);
        return;
    }
    paint(host.out, view.text, view.kind);
    if (objCard.tab === "mtron")
        annotateObjects(host.out, view.text, host.openObject);
}

function setObjCardView(objCard, host, tab, value, kind) {
    if (kind === "dom") objCard.views[tab] = {node: value, kind: "dom"};
    else objCard.views[tab] = {text: value, kind: kind || "sys"};
    const button = objCardButton(host, tab);
    if (button) {
        button.disabled = false;
        button.hidden = false;
    }
    if (objCard.tab === tab) renderObjCard(objCard, host);
}

function enableJson(host) {
    const button = objCardButton(host, "json");
    if (button) button.disabled = false;
}

function ensurePretty(objCard, host) {
    const env = objCard.env;
    if (!env) return;
    if (!env.vid) {
        if (!objCard.views.mtron) setObjCardView(objCard, host, "mtron", env.raw, "res");
        return;
    }
    if (objCard.prettyLoaded) return;
    objCard.prettyLoaded = true;
    setObjCardView(objCard, host, "mtron", "pretty-printing…", "sys");
    objCardDot(host, "busy");
    readPrettyOn(instOf(host), env.vid).then((text) => {
        objCardDot(host, "up");
        setObjCardView(objCard, host, "mtron", text, "res");
    }).catch(() => {
        objCardDot(host, "bad");
        setObjCardView(objCard, host, "mtron", env.raw, "res");
    });
}

function ensureJson(objCard, host) {
    const env = objCard.env;
    if (!env || !env.vid) return;
    if (objCard.jsonLoaded) return;
    objCard.jsonLoaded = true;
    setObjCardView(objCard, host, "json", "reading " + env.vid + " as json…", "sys");
    objCardDot(host, "busy");
    readJsonOn(instOf(host), env.vid).then((value) => {
        objCardDot(host, "up");
        if (value === null) setObjCardView(objCard, host, "json", "noobj", "sys");
        else if (typeof value === "string") setObjCardView(objCard, host, "json", value, "res");
        else setObjCardView(objCard, host, "json", JSON.stringify(value, null, 2), "json");
    }).catch((error) => {
        objCardDot(host, "bad");
        setObjCardView(objCard, host, "json", String(error && error.message ? error.message : error), "err");
    });
}

function objCardTabTo(objCard, host, tab) {
    if (tab === "mtron") ensurePretty(objCard, host);
    else if (tab === "json") ensureJson(objCard, host);
    if (!objCard.views[tab]) return;
    objCard.tab = tab;
    renderObjCard(objCard, host);
}

function resolveObjPanel(tid) {
    return MtronObjPanels && MtronObjPanels.resolve ? MtronObjPanels.resolve(tid) : null;
}

function mountObjPanel(objCard, host, env) {
    const factory = objCard.objPanel;
    if (!factory) return;
    /* the card's own machine: a page's cards mount against their instance even
       when another instance is the active one at that moment */
    const owner = instOf(host);
    setObjCardView(objCard, host, "panel", "panel…", "sys");
    const api = {
        json: () => env.vid ? readJsonOn(owner, env.vid) : Promise.resolve(null),
        pretty: () => env.vid ? readPrettyOn(owner, env.vid) : Promise.resolve(env.raw),
        /* a panel that acts on the machine does it over that machine's mtron socket */
        eval: (expr) => owner.console.evaluateThen(expr),
        evalJson: (expr) => evalJsonOn(owner, expr),
        read: (vid) => vid ? readJsonOn(owner, vid) : Promise.resolve(null),
        view: (url) => activateEndpoint(url),
        instance: owner.wsBase,
        fsspaces: () => owner.fsspaces || [],
        mtron: (expr) => evalMtronOn(owner, expr),
        open: (env) => inspectReference(env || {}),
        at: (text) => {
            if (host.at) host.at.textContent = text;
        },
        frame: (vid) => {
            const next = (!vid || vid === "/.") ? null : String(vid);
            if (next !== owner.console.frame) {
                owner.console.frame = next;
                document.dispatchEvent(new CustomEvent("metatron:frame", {detail: next}));
            }
        },
        currentFrame: () => owner.console.frame,
        paint: (el, text, kind) => paint(el, text, kind),
        announce: (tools) => announceMcpTools(tools)
    };
    Promise.resolve().then(() => factory(env, api)).then((node) => {
        if (node) setObjCardView(objCard, host, "panel", node, "dom");
    }).catch((error) => {
        setObjCardView(objCard, host, "panel", String(error && error.message ? error.message : error), "err");
    });
}

/* a dot under every nested object in the mtron view; `open` names the host */
/* make every address in the mtron view clickable: each object (a typed rec/lst,
   a type reference, or a bare <uri>) is wrapped in a transparent button — the
   text keeps its look, only the cursor changes. shift-click opens its ?docq. */
function annotateObjects(root, text, open) {
    const clean = root && root._text ? root._text : String(text == null ? "" : text);
    const objects = MtronEnvelope && MtronEnvelope.objects
        ? MtronEnvelope.objects(clean) : [];
    if (!objects.length) return;
    const filtered = objects.filter((o) => !(o.start === 0 && o.end === clean.length));
    if (!filtered.length) return;
    const sorted = filtered.slice().sort((a, b) => a.start - b.start || b.end - a.end);

    /* a containment tree: the stack's top is the smallest object still open */
    const roots = [];
    const stack = [];
    sorted.forEach((obj) => {
        while (stack.length && stack[stack.length - 1].end <= obj.start) stack.pop();
        if (stack.length) (stack[stack.length - 1].kids = stack[stack.length - 1].kids || []).push(obj);
        else roots.push(obj);
        stack.push(obj);
    });

    function highlightInto(el, seg) {
        if (!seg) return;
        if (window.hljs) {
            try {
                el.innerHTML = window.hljs.highlight(seg, {language: "mtron", ignoreIllegals: true}).value;
                return;
            } catch (ignored) {
            }
        }
        el.textContent = seg;
    }

    function render(parent, start, end, objs) {
        let cursor = start;
        objs.forEach((obj) => {
            if (obj.start > cursor) {
                const seg = document.createElement("span");
                highlightInto(seg, clean.slice(cursor, obj.start));
                parent.appendChild(seg);
            }
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "obj-link";
            btn.title = (obj.vid ? obj.vid : obj.raw) + (obj.vid ? " · shift-click for ?docq" : "");
            btn.addEventListener("click", (ev) => {
                ev.preventDefault();
                ev.stopPropagation();
                if (ev.shiftKey && obj.vid) {
                    open({tid: null, vid: obj.vid + "?docq", raw: "!*<" + obj.vid + "?docq>", shortTid: "docs"});
                } else {
                    open(obj);
                }
            });
            render(btn, obj.start, obj.end, obj.kids || []);
            parent.appendChild(btn);
            cursor = obj.end;
        });
        if (cursor < end) {
            const seg = document.createElement("span");
            highlightInto(seg, clean.slice(cursor, end));
            parent.appendChild(seg);
        }
    }

    root.textContent = "";
    render(root, 0, clean.length, roots);
}

function updateBack(objCard, host) {
    if (host.back) host.back.hidden = objCard.history.length === 0;
}

function inspectObjCard(objCard, host, env, options) {
    resetObjCard(objCard, host, options && options.keepHistory);
    objCard.env = env;
    /* a type obj serializes as tid::T[…]@vid — route it to the type panel
       regardless of which tid (base type) it refines */
    objCard.objPanel = resolveObjPanel(env.isType ? "type" : env.tid);
    if (objCard.objPanel) {
        const button = objCardButton(host, "panel");
        button.hidden = false;
        button.disabled = false;
    }
    /* a type's label is its vid's last segment (its name), not its tid */
    if (host.title) {
        const label = (env.isType && env.vid)
            ? String(env.vid).split("/").pop()
            : (env.shortTid || "value");
        host.title.textContent = label;
    }
    if (host.at) host.at.textContent = env.vid || "no vid";
    if (host.expr) {
        if (options && options.expr) host.expr.textContent = options.expr;
        else host.expr.textContent = env.expr ? "mtron> " + env.expr : "";
    }
    setObjCardView(objCard, host, "mtron", env.vid ? "pretty-printing…" : env.raw, env.vid ? "sys" : "res");
    if (env.vid) enableJson(host);
    objCard.tab = objCard.objPanel ? "panel" : "mtron";
    if (objCard.objPanel) mountObjPanel(objCard, host, env);
    renderObjCard(objCard, host);
    if (!objCard.objPanel && env.vid) ensurePretty(objCard, host);
    updateBack(objCard, host);
    if (options && options.open && host.dialog && !host.dialog.open) host.dialog.showModal();
}

function nestedObjCard(objCard, host, obj) {
    objCard.history.push({
        title: host.title ? host.title.textContent : "",
        at: host.at ? host.at.textContent : "",
        expr: host.expr ? host.expr.textContent : "",
        views: objCard.views,
        tab: objCard.tab,
        env: objCard.env,
        prettyLoaded: objCard.prettyLoaded,
        jsonLoaded: objCard.jsonLoaded,
        objPanel: objCard.objPanel
    });
    inspectObjCard(objCard, host, {
            tid: obj.tid,
            vid: obj.vid,
            raw: obj.raw,
            shortTid: obj.shortTid,
            isType: obj.isType
        },
        {open: true, keepHistory: true, expr: "nested " + obj.tid});
}

function backObjCard(objCard, host) {
    const state = objCard.history.pop();
    if (!state) return;
    objCard.views = state.views;
    objCard.tab = state.tab;
    objCard.env = state.env;
    objCard.prettyLoaded = state.prettyLoaded;
    objCard.jsonLoaded = state.jsonLoaded;
    objCard.objPanel = state.objPanel;
    if (host.title) host.title.textContent = state.title;
    if (host.at) host.at.textContent = state.at;
    if (host.expr) host.expr.textContent = state.expr;
    renderObjCard(objCard, host);
    updateBack(objCard, host);
}

const modalHost = {
    dialog: $("#popup"),
    title: $("#popup-title"),
    at: $("#popup-at"),
    expr: $("#popup-expr"),
    tabs: $("#popup .popup-tabs"),
    out: $("#popup-out"),
    dot: $("#popup-dot"),
    back: $("#popup-back"),
    openObject: (obj) => openNested(obj)
};
const modalObjCard = emptyObjCard();

function inspectReference(env) {
    inspectObjCard(modalObjCard, modalHost, env, {open: true});
}

function openNested(obj) {
    nestedObjCard(modalObjCard, modalHost, obj);
}

function popupBack() {
    backObjCard(modalObjCard, modalHost);
}

/* one pinned card factory: the dialog's pin and a loaded page's cards both use it.
   `opts.instance` names the machine the card reads from — a page's cards belong to
   their own instance, the dialog's to whichever machine is active. */
function mountPinnedObjCard(env, options) {
    const opts = options || {};
    const owner = opts.instance || active;
    const root = document.createElement("article");
    root.className = "obj-card";
    root.dataset.tid = env.tid || "";
    root.dataset.vid = env.vid || "";
    root.dataset.label = opts.label || env.shortTid || "value";
    root.dataset.view = opts.view || "panel";
    applySpan(root, opts.col, opts.row);
    root.innerHTML = '<header class="obj-card-head">' +
        '<button type="button" class="obj-card-grip" title="drag to move" aria-label="move">⠿</button>' +
        '<span class="obj-card-title"></span>' +
        '<div class="popup-tabs" role="tablist">' +
        '<button type="button" class="popup-tab" data-tab="panel" hidden>panel</button>' +
        '<button type="button" class="popup-tab" data-tab="mtron">mtron</button>' +
        '<button type="button" class="popup-tab" data-tab="json" disabled>json</button></div>' +
        '<span class="obj-card-vid"></span>' +
        '<button type="button" class="obj-card-unpin" title="unpin">×</button></header>' +
        '<div class="popup-out"></div>' +
        '<button type="button" class="obj-card-resize" title="drag to resize" aria-label="resize">◢</button>';
    const host = {
        instance: owner,
        dialog: null,
        title: root.querySelector(".obj-card-title"),
        at: root.querySelector(".obj-card-vid"),
        expr: null,
        tabs: root.querySelector(".popup-tabs"),
        out: root.querySelector(".popup-out"),
        dot: null,
        back: null,
        openObject: (obj) => inspectReference({
            tid: obj.tid,
            vid: obj.vid,
            raw: obj.raw,
            shortTid: obj.shortTid,
            isType: obj.isType
        })
    };
    const objCard = emptyObjCard();
    root.querySelector(".obj-card-unpin").addEventListener("click", () => {
        const parent = root.parentElement;
        root.remove();
        if (parent && parent.id === "pinned" && parent.childElementCount === 0) parent.hidden = true;
    });
    root.querySelectorAll(".popup-tab").forEach((button) => {
        button.addEventListener("click", () => {
            objCardTabTo(objCard, host, button.dataset.tab);
            root.dataset.view = button.dataset.tab;
        });
    });
    makeDraggable(root);
    makeResizable(root);
    inspectObjCard(objCard, host, env, {open: false});
    if (host.title) host.title.textContent = root.dataset.label;
    if (opts.view) objCardTabTo(objCard, host, opts.view);
    const parent = opts.parent || owner.pinned;
    parent.appendChild(root);
    if (parent.id === "pinned") parent.hidden = false;
    if (opts.close && modalHost.dialog.open) modalHost.dialog.close();
    return root;
}

function pinObjCard() {
    const env = modalObjCard.env;
    if (!env) return;
    const loaded = active.page.loadedGrid;
    const target = loaded && !loaded.hidden ? loaded : active.pinned;
    mountPinnedObjCard(env, {
        label: env.shortTid,
        view: modalObjCard.tab || "panel",
        span: 1,
        instance: active,
        parent: target,
        close: true
    });
}

/* the console's input, output and history are per instance; only the keyboard
   shortcut is the page's: "/" focuses whichever machine's console is on screen */
document.addEventListener("keydown", (ev) => {
    const tag = (document.activeElement || {}).tagName || "";
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(tag);
    if (ev.key === "/" && !typing && active) {
        ev.preventDefault();
        active.expr.focus();
    }
});

/* ── input history: up/down on any text field ─────────────────────────────
   every text field on the page keeps its own little shell history — up for the
   older entry, down for the newer, and down past the end restores what was being
   typed. delegated, so a field a panel mints later has it too. a field names its
   own key with data-history="<key>" (data-history="off" opts out, as the console's
   REPL does: it keeps its own per-server history). otherwise the key is the panel
   the field stands in and its place among that panel's fields. entries persist,
   like the console's. */
const FIELD_HISTORY_PREFIX = "metatron.field.history.";
const FIELD_HISTORY_MAX = 50;

function fieldHistoryKey(field) {
    const declared = field.dataset ? field.dataset.history : null;
    if (declared) return declared === "off" ? null : declared;
    const host = field.closest(".mpanel, .obj-card, dialog, section, form") || document.body;
    const owner = (host.dataset && (host.dataset.tid || host.id))
        || String(host.className || "").trim() || host.id || "page";
    const peers = Array.from(host.querySelectorAll("input, textarea"))
        .filter((el) => !(el.dataset && el.dataset.history === "off"))
        .filter((el) => el.tagName === "TEXTAREA" || !el.type || el.type === "text" || el.type === "search");
    return owner + "#" + peers.indexOf(field);
}

function fieldHistory(key) {
    try {
        const entries = JSON.parse(localStorage.getItem(FIELD_HISTORY_PREFIX + key) || "[]");
        return Array.isArray(entries) ? entries : [];
    } catch (ignored) {
        return [];
    }
}

function rememberFieldHistory(key, value) {
    const text = String(value == null ? "" : value).trim();
    if (!text) return;
    const entries = fieldHistory(key);
    if (entries[entries.length - 1] === text) return;
    entries.push(text);
    while (entries.length > FIELD_HISTORY_MAX) entries.shift();
    try {
        localStorage.setItem(FIELD_HISTORY_PREFIX + key, JSON.stringify(entries));
    } catch (ignored) {
        /* a full or disabled store keeps the session's history only */
    }
}

document.addEventListener("keydown", (ev) => {
    const field = ev.target;
    if (!field || !field.tagName) return;
    const textual = field.tagName === "TEXTAREA"
        || (field.tagName === "INPUT" && (!field.type || field.type === "text" || field.type === "search"));
    if (!textual) return;
    if (ev.key === "Enter" && !ev.shiftKey && !ev.altKey && !ev.ctrlKey && !ev.metaKey) {
        const key = fieldHistoryKey(field);
        if (key) {
            rememberFieldHistory(key, field.value);
            field.__history = null;   /* the next up starts from the newest entry */
        }
        return;
    }
    if (ev.key !== "ArrowUp" && ev.key !== "ArrowDown") return;
    if (ev.altKey || ev.ctrlKey || ev.metaKey) return;
    /* in a textarea the arrows are the caret's until they reach the edge */
    if (field.tagName === "TEXTAREA") {
        if (field.selectionStart !== field.selectionEnd) return;
        if (ev.key === "ArrowUp" && field.selectionStart !== 0) return;
        if (ev.key === "ArrowDown" && field.selectionStart !== field.value.length) return;
    }
    const key = fieldHistoryKey(field);
    if (!key) return;
    const entries = fieldHistory(key);
    if (!entries.length) return;
    /* the cursor and the draft live on the ELEMENT (a cloned card is a new field
       with a new cursor), while the entries come from the key */
    const state = field.__history || (field.__history = {cursor: entries.length, draft: field.value});
    if (ev.key === "ArrowUp") {
        if (state.cursor >= entries.length) state.draft = field.value;
        if (state.cursor === 0) {
            ev.preventDefault();
            return;
        }
        state.cursor -= 1;
        field.value = entries[state.cursor];
    } else {
        if (state.cursor >= entries.length) return;
        state.cursor += 1;
        field.value = state.cursor === entries.length ? state.draft : entries[state.cursor];
    }
    field.setSelectionRange(field.value.length, field.value.length);
    ev.preventDefault();
});

/* a value committed on blur (a press of compile, a table pick) is history too */
document.addEventListener("change", (ev) => {
    const field = ev.target;
    if (!field || !field.tagName) return;
    const textual = field.tagName === "TEXTAREA"
        || (field.tagName === "INPUT" && (!field.type || field.type === "text" || field.type === "search"));
    if (!textual) return;
    const key = fieldHistoryKey(field);
    if (key) rememberFieldHistory(key, field.value);
});

/* the mcp tools card is the first card rendered through the panel mapping */
/* the mcp_server card is the surface readout of an mcp_client's tool catalog */
function announceMcpTools(tools) {
    const count = tools.length;
    const set = (sel, text) => {
        const el = $(sel);
        if (el) el.textContent = text;
    };
    set("#m-tools", count + (count === 1 ? " tool" : " tools"));
    set("#m-mcp", count + " tools");
    set("#note-tools", count + " exposed");
    set("#r-tools", "ok");
    const evalTool = tools.some((tool) => /eval_mtron/.test(tool.name || ""));
    const evalCell = $("#r-eval");
    if (evalCell) {
        evalCell.textContent = evalTool ? "available" : "—";
        evalCell.style.color = evalTool ? "var(--ok)" : "";
    }
    setDot($("#d-toolpanel"), "up");
    setDot($("#d-mcp"), "up");
}

/* the static mcp_client card (the fallback page and the pre-load paint) has no vid,
   so it asks for one; a page card with a vid gets the real runner through mountObjPanel */
(function mountStaticMcpPanel() {
    const factory = MtronObjPanels && MtronObjPanels.resolve
        ? MtronObjPanels.resolve("mcp_client") : null;
    const host = $("#mcp-panel-host");
    if (!factory || !host) return;
    host.appendChild(factory(
        {tid: "mcp_client", vid: null, raw: ""},
        {
            eval: (expr) => evaluateThen(expr),
            paint: (el, text, kind) => paint(el, text, kind),
            announce: (tools) => announceMcpTools(tools)
        }
    ));
})();

/* ── actions: an expression and the surface it lands on ──────────────────
   the console's url protocol, carried on any page: ?mtron=<expr>&at=<surface>.
   every clickable action renders as a real anchor, so copy-link works. */
const SURFACES = {
    "web/console": (action) => run(action.expr),
    "popup": (action) => showPopup(action)
};

function act(expr, at) {
    expr = String(expr || "").trim();
    if (!expr) return;
    const surface = at && SURFACES[at] ? at : "web/console";
    if (at && !SURFACES[at]) line("sys", "unknown at=" + at + " — using web/console");
    SURFACES[surface]({expr: expr, at: surface});
    if (surface === "web/console")
        $("#console-card").scrollIntoView({behavior: "smooth", block: "center"});
}

function actionHref(expr, at) {
    const url = new URL(location.href);
    url.searchParams.delete("mtron");
    url.searchParams.delete("at");
    url.searchParams.set("mtron", expr);
    if (at && at !== "web/console") url.searchParams.set("at", at);
    return url.pathname + url.search + url.hash;
}

function actionLink(label, expr, at, cls) {
    const anchor = document.createElement("a");
    anchor.className = "act" + (cls ? " " + cls : "");
    anchor.href = actionHref(expr, at);
    anchor.dataset.act = "1";
    anchor.dataset.expr = expr;
    anchor.dataset.at = at || "web/console";
    anchor.textContent = label;
    anchor.title = "mtron: " + expr + "   →   at=" + (at || "web/console");
    return anchor;
}

function showPopup(action) {
    const host = modalHost;
    resetObjCard(modalObjCard, host, false);
    if (host.title) host.title.textContent = "result";
    if (host.at) host.at.textContent = "at=" + action.at;
    if (host.expr) host.expr.textContent = "mtron> " + action.expr;
    setObjCardView(modalObjCard, host, "mtron", "evaluating…", "sys");
    objCardDot(host, "busy");
    if (!host.dialog.open) host.dialog.showModal();
    evaluateThen(action.expr).then((result) => {
        const env = MtronEnvelope ? MtronEnvelope.parse(result) : null;
        objCardDot(host, "up");
        inspectObjCard(modalObjCard, host, env || {raw: result}, {open: true, expr: "mtron> " + action.expr});
    }).catch((error) => {
        objCardDot(host, "bad");
        setObjCardView(modalObjCard, host, "mtron", "!!! " + (error && error.message ? error.message : String(error)), "err");
    });
}

document.addEventListener("click", (ev) => {
    const anchor = ev.target && ev.target.closest ? ev.target.closest("a[data-act]") : null;
    if (!anchor) return;
    if (ev.button !== 0 || ev.metaKey || ev.ctrlKey || ev.shiftKey || ev.altKey) return;
    ev.preventDefault();
    act(anchor.dataset.expr, anchor.dataset.at);
});

function cleanActionParams() {
    const url = new URL(location.href);
    url.searchParams.delete("mtron");
    url.searchParams.delete("at");
    history.replaceState(null, "", url.pathname + url.search + url.hash);
}

function showPending(expr, at) {
    if (!active) return;
    const bar = active.pending;
    bar.hidden = false;
    active.pendingExpr.textContent = expr;
    active.pendingAt.textContent = "→ at=" + (at || "web/console");
    bar.querySelector("#pending-run").onclick = () => {
        bar.hidden = true;
        cleanActionParams();
        act(expr, at);
    };
    bar.querySelector("#pending-dismiss").onclick = () => {
        bar.hidden = true;
        cleanActionParams();
    };
}

/* ── boot ──────────────────────────────────────────────────────────────── */
/* ── physical machine stats ─────────────────────────────────────────────
   evaluate sys_stat() on the x-mtron socket, so the byte values keep their
   unit — the json codec drops it. */
const BYTE_UNITS = {bB: 1, kB: 1024, mB: 1048576, gB: 1073741824, tB: 1099511627776, pB: 1125899906842624};

function statVal(v) {
    return v && typeof v === "object" && "js" in v ? v.js : v;
}

function statBytes(v) {
    if (!v || typeof v !== "object" || !("js" in v) || !v.tid) return NaN;
    const unit = String(v.tid.toString()).split("/").pop();
    return (v.js || 0) * (BYTE_UNITS[unit] || 1);
}

function humanBytes(n) {
    if (!Number.isFinite(n)) return "—";
    if (n >= 1099511627776) return (n / 1099511627776).toFixed(1) + "tB";
    if (n >= 1073741824) return (n / 1073741824).toFixed(1) + "gB";
    if (n >= 1048576) return (n / 1048576).toFixed(1) + "mB";
    if (n >= 1024) return (n / 1024).toFixed(1) + "kB";
    return n + "B";
}

function statChip(k, v, title) {
    const chip = document.createElement("span");
    chip.className = "stat";
    const key = document.createElement("span");
    key.className = "k";
    key.textContent = k;
    const val = document.createElement("span");
    val.className = "v";
    val.textContent = v;
    chip.appendChild(key);
    chip.appendChild(val);
    if (title) chip.title = title;
    return chip;
}

/* the stats strip is the ACTIVE machine's, not the page's own host: the `at` pill
   names that machine (the authority its tab shows) and sys_stat() is read on it.
   the numbers are cached per instance, so a switch repaints at once and the fresh
   read lands a beat later. */
function renderStats(s, instance) {
    const el = $("#topbar-stats");
    if (!el || !instance) return;
    const chips = [statChip("at", instance.name, instance.wsBase)];
    const rec = s && typeof s.isRec === "function" && s.isRec() ? s : null;
    if (rec) {
        const g = (key) => {
            for (const [k, v] of rec.js) if (k.toString() === key) return v;
            return undefined;
        };
        const memFree = statBytes(g("free_mem_phys"));
        const memTotal = statBytes(g("total_mem_phys"));
        const diskFree = statBytes(g("disk_free"));
        const diskTotal = statBytes(g("disk_total"));
        const cpu = statVal(g("cpu_load"));
        const model = statVal(g("cpu_model"));
        const cores = statVal(g("processor"));
        if (Number.isFinite(memFree) && Number.isFinite(memTotal))
            chips.push(statChip("mem", humanBytes(memFree) + " / " + humanBytes(memTotal)));
        if (Number.isFinite(diskFree) && Number.isFinite(diskTotal))
            chips.push(statChip("disk", humanBytes(diskFree) + " / " + humanBytes(diskTotal)));
        if (cpu != null && cpu !== "")
            chips.push(statChip("load", (typeof cpu === "number" ? Math.round(cpu) : cpu) + "%"));
        if (model)
            chips.push(statChip("cpu", (cores ? cores + " cores · " : "") + String(model).split(" w/ ")[0]));
    }
    el.replaceChildren(...chips);
}

/* repaint the strip from the active machine's cached numbers */
function renderActiveStats() {
    if (active) renderStats(active.stats, active);
}

/* ── the machine's file systems ───────────────────────────────────────────
   a fsspace names a SCHEME (its pattern, e.g. mfs:#) over a host directory (its
   route). the page searches for them when it loads, so a load/save picker can offer
   the choice; the query answers an OBJS of fsspace recs, which the serializer parses.
   the scheme is the machine's own name for its file system (mfs: on one, fs: on
   another), so it is discovered, never assumed. */
const FSSPACE_QUERY = "/memory/space.{1,3}mult(+).*_.??(fsspace::T)";

function fsspaceOf(rec) {
    const field = (name) => {
        for (const [key, value] of rec.js) if (key.toString() === name) return value;
        return null;
    };
    const pattern = field("pattern");
    if (!pattern) return null;
    const scheme = String(pattern).replace(/#.*$/, "").replace(/:$/, "").trim();
    if (!scheme) return null;
    const route = field("route");
    const root = route && route.isRec && route.isRec() ? [...route.js.values()].map((value) => String(value))[0] : null;
    const vid = rec.vid ? rec.vid.toString() : null;
    return {scheme: scheme, root: root || null, vid: vid, name: vid ? vid.split("/").pop() : scheme};
}

async function loadFsspaces(instance) {
    const text = String((await evalMtronOn(instance, FSSPACE_QUERY)) || "").trim();
    if (!text || text === "noobj" || /^fail::/.test(text)) return [];
    const value = parseValue(text);
    if (!value) return [];
    const members = value.isObjs && value.isObjs() ? [...value.js] : [value];
    return members.map(fsspaceOf).filter(Boolean);
}

/* evaluate one expression on the x-mtron (structural) socket — unit- and
   vid-preserving; the json codec drops tids/vids, so the stats strip and
   the panels that need the raw machine form read through here. */
function evalMtronOn(instance, expr) {
    const mime = "application/x-mtron";
    const reader = readerFor(mime, instance.readers);
    const run = reader.chain.then(() => connectReader(mime, instance.wsBase, instance.readers)).then((socket) => new Promise((resolve) => {
        /* a dead socket answers "" at once, not at the timeout: the reader is
           queued, so one aborted request (a sys_stat the machine drops) must not
           stall every panel read behind it */
        const done = (text) => {
            clearTimeout(timer);
            socket.removeEventListener("message", answer);
            cleanup();
            resolve(text);
        };
        const cleanup = settleOnClose(socket, () => done(""));
        const timer = setTimeout(() => done(""), 15000);
        const answer = (ev) => {
            frameText(ev.data).then((text) => done(text)).catch(() => done(""));
        };
        socket.addEventListener("message", answer);
        socket.send(String(expr));
    }));
    reader.chain = run.catch(() => {
    });
    return run;
}

function evalMtron(expr) {
    return evalMtronOn(active, expr);
}

/* sys_stat() on the ACTIVE machine: its numbers, read over its own socket */
async function loadStats() {
    if (!active) return;
    const instance = active;
    try {
        const raw = await evalMtronOn(instance, "sys_stat()");
        if (!raw) return;
        instance.stats = MtronIO.read(String(raw));
        if (instance === active) renderStats(instance.stats, instance);
    } catch (ignored) {
        /* stats are decorative — a failed read leaves the strip empty, never broken */
    }
}

/* the topbar's identity: the machine the active tab stands for */
function renderIdentity() {
    if (!active) return;
    active.wsEp.textContent = active.wsBase;
    active.wsEp.title = active.wsUrl;
    const instanceEl = $("#instance");
    if (instanceEl) {
        instanceEl.textContent = active.name || "this machine";
        instanceEl.title = active.wsUrl;
    }
    const localEl = $("#instance-local");
    if (localEl) {
        localEl.hidden = instanceIsLocal(active);
        localEl.onclick = (ev) => {
            ev.preventDefault();
            activateEndpoint("");
        };
    }
    const boot = metaContent("metatron:boot");
    const bootEl = $("#boot-file");
    if (boot && bootEl) bootEl.textContent = boot;
}

/* a bind wildcard (0.0.0.0 / ::) is the machine's own address, not a dialable
   one — swap it for the machine's name so the jump actually lands */
function resolveEndpoint(name, endpoint) {
    try {
        const url = new URL(endpoint, location.href);
        if (url.hostname === "0.0.0.0" || url.hostname === "::" || url.hostname === "[::]" || url.hostname === "0:0:0:0:0:0:0:1")
            url.hostname = String(name);
        return url.toString();
    } catch (ignored) {
        return endpoint;
    }
}

/* ── instances: one native view per connected metatron machine ────────────
   the tab strip is the cluster: a greyed tab is a machine nobody opened yet, a
   live one is an instance whose connections are open. every instance keeps its
   own websocket(s), console, page object and <main>; only the active instance's
   <main> is in the document, so ids stay unique and $("#out") keeps resolving to
   the machine on screen. */
const INSTANCES = new Map();   // authority -> instance

/* the authority a ws endpoint names: host:port (the roster's tab key) */
function authorityOf(base) {
    try {
        return new URL(base, location.href).host || String(base);
    } catch (ignored) {
        return String(base);
    }
}

/* the machine this page is served from (the roster's reader)? */
function instanceIsLocal(instance) {
    return !!instance && authorityOf(instance.wsBase) === authorityOf(localBase);
}

/* one machine's whole view — its own <main>, console, readers and page object.
   the console connects lazily, on the first read or expression. */
function createInstance(name, endpoint) {
    const base = String(endpoint);
    const main = MAIN_TEMPLATE.cloneNode(true);
    const instance = {
        name: String(name || authorityOf(base)),
        wsBase: base,
        wsUrl: codecUrl(base),
        readers: new Map(),
        /* the last sys_stat() this machine answered — the strip repaints from it */
        stats: null,
        main: main,
        /* DOM handles are cached per instance: detaching a <main> does not
           invalidate a node reference, so they survive every attach/detach. */
        out: main.querySelector("#out"),
        expr: main.querySelector("#expr"),
        replForm: main.querySelector("#repl"),
        clearButton: main.querySelector("#clear"),
        chips: main.querySelector(".chips"),
        pending: main.querySelector("#pending"),
        pendingExpr: main.querySelector("#pending-expr"),
        pendingAt: main.querySelector("#pending-at"),
        dConsole: main.querySelector("#d-console"),
        mConsole: main.querySelector("#m-console"),
        hlNote: main.querySelector("#hl-note"),
        wsEp: main.querySelector("#ws-ep"),
        loadedGrid: main.querySelector("#page-loaded"),
        pinned: main.querySelector("#pinned")
    };
    main.dataset.instance = instance.name;
    /* the static cards carry their span in data-col/data-row, and the example
       chips are real action links — both are per <main>, so a clone needs them */
    $$("[data-col]", main).forEach((el) => applySpan(el, el.dataset.col, el.dataset.row));
    $$(".chips .chip", main).forEach((chip) => {
        const expr = chip.dataset.expr || chip.textContent;
        chip.replaceWith(actionLink(chip.textContent, expr, "web/console", "chip"));
    });
    instance.console = createConsole(instance);
    instance.page = createPage(instance);
    /* the grammar is page-wide and may already be in: a fresh console says so */
    if (instance.hlNote) instance.hlNote.textContent = HL ? "syntax: mtron" : (hlBase ? "syntax: loading" : "syntax: plain");
    INSTANCES.set(instance.name, instance);
    return instance;
}

/* attach the instance's <main> (the previous one leaves the document) and make
   it the active one: the shell's chrome, page view and title follow it */
function activate(instance) {
    if (!instance) return;
    if (active === instance) {
        syncChrome();
        return;
    }
    PAGE_PANEL.replaceChildren(instance.main, PAGE_FOOTER);
    active = instance;
    syncChrome();
    /* the stats strip is this machine's: its cached numbers now, a fresh read next */
    renderActiveStats();
    loadStats();
    /* its file systems: discovered once, so a load/save picker can offer them */
    if (!instance.fsspaces) {
        instance.fsspaces = [];
        loadFsspaces(instance).then((spaces) => {
            instance.fsspaces = spaces;
        }).catch(() => {
        });
    }
    instance.page.show(instance.page.view);
    restyleTabs();
    pointAt(instance);
}

/* the shell's chrome — the identity line, the page vid, the save button, the
   title — always describes the active instance */
function syncChrome() {
    if (!active) return;
    renderIdentity();
    active.page.sync();
    document.title = active.page.docTitle || "metatron";
}

/* ?ws= is the instance the page is looking at: a switch is URL state, so a
   reload reopens the same machine (the local machine is the unnamed default) */
function pointAt(instance) {
    try {
        const url = new URL(location.href);
        const named = url.searchParams.get("ws") || "";
        const wanted = instanceIsLocal(instance) ? "" : instance.wsBase;
        if (named === wanted) return;
        if (wanted) url.searchParams.set("ws", wanted);
        else url.searchParams.delete("ws");
        history.replaceState(null, "", url.pathname + url.search + url.hash);
    } catch (ignored) {
    }
}

/* the same machine can be named by its roster authority or by an endpoint */
function instanceFor(name, endpoint) {
    const known = INSTANCES.get(String(name));
    if (known) return known;
    let same = null;
    INSTANCES.forEach((instance) => {
        if (!same && instance.wsBase === String(endpoint)) same = instance;
    });
    return same;
}

/* a greyed tab: open the machine natively — its own console, readers and page
   object — and put it on screen. the console connects on its first use. */
async function activateServer(name, endpoint) {
    const known = instanceFor(name, endpoint);
    if (known) {
        activate(known);
        return known;
    }
    const instance = createInstance(name, endpoint);
    activate(instance);
    try {
        await instance.page.loadPage(instance.page.PAGE);
    } catch (error) {
        instance.console.line("err", "page load: " + (error && error.message ? error.message : error));
    }
    restyleTabs();
    return instance;
}

/* the local link and a panel's api.view(url): the same native switch, no reload */
function activateEndpoint(url) {
    const base = url ? String(url) : localBase;
    return activateServer(authorityOf(base), base);
}

/* restyle every server tab: the active machine is current, the open ones are
   live, everything else is greyed. covers the nested menu entries too. */
function restyleTabs() {
    const bar = $("#servers");
    if (!bar) return;
    const current = active ? active.name : null;
    bar.querySelectorAll(".server-tab[data-name]").forEach((tab) => {
        const n = tab.dataset.name;
        tab.classList.toggle("current", n === current);
        tab.classList.toggle("live", n !== current && INSTANCES.has(n));
    });
    bar.querySelectorAll(".server-summary").forEach((summary) => {
        const menu = summary.parentElement ? summary.parentElement.querySelector(".server-menu") : null;
        const entries = menu ? menu.querySelectorAll(".server-tab[data-name]") : [];
        let hasCurrent = false;
        let hasLive = false;
        entries.forEach((e) => {
            if (e.dataset.name === current) hasCurrent = true;
            else if (INSTANCES.has(e.dataset.name)) hasLive = true;
        });
        summary.classList.toggle("current", hasCurrent);
        summary.classList.toggle("live", !hasCurrent && hasLive);
        summary.textContent = (hasCurrent && current ? current : (summary.dataset.host || "group")) + " ▾";
    });
}

async function loadServers() {
    const bar = $("#servers");
    if (!bar) return;
    let machines = {};
    try {
        const roster = await localRead("application/json", "/network/roster");
        machines = roster && roster.machine && typeof roster.machine === "object" ? roster.machine : {};
    } catch (ignored) { }

    const tabButton = (authority, endpoint, after) => {
        const isCurrent = !!active && authority === active.name;
        const isLive = !isCurrent && INSTANCES.has(authority);
        const b = document.createElement("button");
        b.type = "button";
        b.className = "server-tab" + (isCurrent ? " current" : isLive ? " live" : "");
        b.dataset.name = authority;
        b.textContent = authority;
        b.title = isCurrent ? authority + " · this instance" : (isLive ? "view " + authority : "connect to " + authority + " · " + endpoint);
        b.addEventListener("click", () => {
            if (after) after();
            activateServer(authority, endpoint);
        });
        return b;
    };

    /* collect authorities, grouped by host */
    const groups = new Map();   // host -> [{authority, endpoint}]
    const seen = new Set();
    const remember = (host, authority, endpoint) => {
        if (!authority || seen.has(authority)) return;
        seen.add(authority);
        if (!groups.has(host)) groups.set(host, []);
        groups.get(host).push({authority, endpoint});
    };
    Object.keys(machines).sort().forEach((name) => {
        const machine = machines[name] && typeof machines[name] === "object" ? machines[name] : {};
        const mtron = machine.mtron && typeof machine.mtron === "object" ? machine.mtron : {};
        const wsList = Array.isArray(mtron.ws) ? mtron.ws : (mtron.ws ? [mtron.ws] : []);
        wsList.forEach((ws) => {
            const endpoint = resolveEndpoint(name, String(ws));
            let url;
            try { url = new URL(endpoint, location.href); } catch (ignored) { return; }
            const port = url.port || "";
            /* one authority per (host:port) — the same port over several
               interfaces collapses to one entry */
            remember(name, name + (port ? ":" + port : ""), endpoint);
        });
    });
    /* a machine we can already reach is always present, even when the roster
       omits it: the page's own, the local one, and every instance opened so far */
    const ensure = (authority, endpoint) => {
        let host = authority;
        try { host = new URL(endpoint, location.href).hostname || authority; } catch (ignored) { }
        remember(host, authority, endpoint);
    };
    ensure(authorityOf(bootBase), bootBase);
    ensure(authorityOf(localBase), localBase);
    INSTANCES.forEach((instance) => ensure(instance.name, instance.wsBase));

    bar.textContent = "";
    Array.from(groups.keys()).sort().forEach((host) => {
        const list = groups.get(host);
        if (list.length === 1) {
            bar.appendChild(tabButton(list[0].authority, list[0].endpoint));
            return;
        }
        /* a host with several authorities: a button that opens a menu of host:port */
        const group = document.createElement("div");
        group.className = "server-group";
        const summary = document.createElement("button");
        summary.type = "button";
        summary.className = "server-tab server-summary";
        summary.dataset.host = host;
        summary.textContent = host + " ▾";
        summary.title = list.length + " servers on " + host;
        const menu = document.createElement("div");
        menu.className = "server-menu";
        menu.hidden = true;
        list.forEach((e) => menu.appendChild(tabButton(e.authority, e.endpoint, () => { menu.hidden = true; })));
        summary.addEventListener("click", (ev) => {
            ev.stopPropagation();
            const wasOpen = !menu.hidden;
            bar.querySelectorAll(".server-menu").forEach((m) => { m.hidden = true; });
            menu.hidden = wasOpen;
        });
        group.appendChild(summary);
        group.appendChild(menu);
        bar.appendChild(group);
    });
    restyleTabs();
}

let refreshing = false;

async function refresh() {
    if (refreshing) return;
    refreshing = true;
    try {
        renderIdentity();
        renderActiveStats();
        loadStats();
    } finally {
        refreshing = false;
    }
}

/* the static cards' spans and the example chips are per <main>, so every
   instance does them for its own clone in createInstance(); the highlighter (one
   grammar for the whole page) loads once, and re-tints whatever is already out. */

modalHost.tabs.querySelectorAll(".popup-tab").forEach((button) => {
    button.addEventListener("click", () => objCardTabTo(modalObjCard, modalHost, button.dataset.tab));
});
$("#popup-back").addEventListener("click", popupBack);
$("#popup-pin").addEventListener("click", pinObjCard);
$("#popup-close").addEventListener("click", () => $("#popup").close());
/* clicking the backdrop — outside the dialog's box — also closes it */
$("#popup").addEventListener("click", (ev) => {
    const rect = ev.currentTarget.getBoundingClientRect();
    if (ev.clientX < rect.left || ev.clientX > rect.right ||
        ev.clientY < rect.top || ev.clientY > rect.bottom) {
        ev.currentTarget.close();
    }
});
$("#popup").addEventListener("close", () => {
    setDot($("#popup-dot"), "");
    modalObjCard.history = [];
    updateBack(modalObjCard, modalHost);
});
$("#popup-copy").addEventListener("click", async () => {
    const button = $("#popup-copy");
    try {
        await navigator.clipboard.writeText($("#popup-out").textContent);
        button.textContent = "copied";
    } catch (ignored) {
        line("err", "clipboard unavailable");
    }
    setTimeout(() => {
        button.textContent = "copy";
    }, 1200);
});

/* ── boot ────────────────────────────────────────────────────────────────
   the page's own machine is the first instance; every other tab opens on demand
   and keeps its connections once it has them. */
const home = createInstance(authorityOf(bootBase), bootBase);
activate(home);

/* an action carried on this page's own url waits for one click */
const actionExpr = params.get("mtron");
const actionAt = params.get("at") || "web/console";
if (actionExpr) showPending(actionExpr, actionAt);

loadHighlighter();

const homeSource = params.get("page") || params.get("vid");
if (homeSource) {
    home.page.loadPage(homeSource).catch((error) => {
        line("err", "page load: " + (error && error.message ? error.message : error));
    });
} else {
    /* no source in the url: load the saved default page, falling back to the static page.
       a failure MUST be said out loud: an empty grid with no message is indistinguishable
       from a page still loading, and it hid a real one (a grouped card that failed the
       splash_card type check, so the default page parsed nowhere on a machine whose typer
       checks type predicates) */
    home.page.loadPage(home.page.PAGE).catch((error) => {
        line("err", "page load: " + (error && error.message ? error.message : error));
    });
}

refresh();
loadServers();
setInterval(() => {
    if (!document.hidden) {
        refresh();
        loadServers();
    }
}, 15000);
document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
        refresh();
        loadServers();
    }
});
document.addEventListener("click", (ev) => {
    /* close any open server menu when clicking outside its group */
    if (!ev.target.closest(".server-group")) {
        const bar = $("#servers");
        if (bar) bar.querySelectorAll(".server-menu").forEach((m) => { m.hidden = true; });
    }
});

/* ── the page object: one per instance ────────────────────────────────────
   each machine wears its own splash_page, so the layout and its save follow the
   instance a tab stands for. the chrome (the topbar view tabs, the save button,
   the save dialog, the mtron/json view) is the shell's, and always describes the
   ACTIVE instance. ?vid=<vid> or ?page=<url with ?vid=> loads a named page. */
function createPage(instance) {
    const tabs = $$(".page-tabs .popup-tab");
    const pagePanel = PAGE_PANEL;
    const encoding = $("#page-encoding");
    const out = $("#page-encoding-out");
    const pageSave = $("#page-save");
    const loadedGrid = instance.loadedGrid;
    const pinned = instance.pinned;
    const saveDialog = $("#save-as");
    const saveVid = $("#save-as-vid");
    const saveError = $("#save-as-error");
    const page = {
        PAGE: defaultPage(),
        /* "instance": the layout came from the machine itself; "local": it was
           inherited from the local machine and is the machine's to adopt */
        origin: "local",
        title: "metatron",
        docTitle: "metatron",
        view: "panel",
        cache: new Map(),
        loadedGrid: loadedGrid,
        pinned: pinned
    };

    /* the instance's own page object — the local machine is only a fallback */
    function pageRead(mime, vid) {
        return readAsOn(instance, mime, vid);
    }

    function pageEval(expr) {
        return evalMtronOn(instance, expr);
    }

    const BEFORE = [
        {tid: "/m/web/http/http_server", label: "http server", col: 1, row: 1, vid: "/memory/space/web/http"},
        {tid: "/m/web/ws/ws_server", label: "ws server", col: 1, row: 1, vid: "/memory/space/web/ws"},
        {tid: "/m/sys/ui/console", label: "console", col: 4, row: 2, vid: "/usr/marko/console"}
    ];
    const KNOWN = {
        http_server: ["/m/web/http/http_server", "/memory/space/web/http"],
        ws_server: ["/m/web/ws/ws_server", "/memory/space/web/ws"],
        console: ["/m/sys/ui/console", "/usr/marko/console"]
    };
    const AFTER = [
        {tid: "mcp_client", label: "mcp tools", col: 2, row: 1}
    ];

    /* the shipped page is read-only: saving it asks where the copy should live.
       a machine that INHERITED the local layout has no page of its own yet, so
       there "save" writes it its own default.mtron — the inherited layout becomes
       that machine's own. */
    function isDefaultPage(vid) {
        const value = String(vid == null ? "" : vid).trim().replace(/^</, "").replace(/>$/, "");
        return value === "mfs:boot/space/web/default.mtron" ||
            value === "/mfs/boot/space/web/default.mtron";
    }

    function readOnly() {
        return isDefaultPage(page.PAGE) && !(page.origin === "local" && !instanceIsLocal(instance));
    }

    function saveLabel() {
        return readOnly() ? "save as…" : "save";
    }

    function updateSaveButton() {
        if (!pageSave || instance !== active) return;
        pageSave.textContent = saveLabel();
        pageSave.title = readOnly()
            ? "the default page is read-only — save this page somewhere else"
            : "write this page to " + page.PAGE;
    }

    /* the shell's page chrome — the bar's vid and the save button, active only */
    function sync() {
        if (instance !== active) return;
        const barVid = $(".page-vid");
        if (barVid) barVid.textContent = page.PAGE;
        updateSaveButton();
    }

    function suggestPage() {
        const value = String(page.PAGE == null ? "" : page.PAGE).replace(/^</, "").replace(/>$/, "");
        const cut = value.lastIndexOf("/");
        return (cut > 0 ? value.slice(0, cut + 1) : "mfs:boot/") + "my_splash_page.mtron";
    }

    function saveFail(message) {
        if (!saveError) return;
        saveError.textContent = message || "";
        saveError.hidden = !message;
    }

    function openSaveAs() {
        if (!saveDialog) return;
        saveVid.value = suggestPage();
        saveFail("");
        if (!saveDialog.open) saveDialog.showModal();
        saveVid.focus();
        saveVid.select();
    }

    function paintPage(text, view) {
        if (view === "json") {
            paint(out, text, "json");
            return;
        }
        paint(out, text, "res");
        annotateObjects(out, text, (obj) => openNested(obj));
    }

    /* the page view: [panel][mtron][json]. the tabs and the encoding panel are the
       shell's, so only the active instance paints them; a hidden instance just
       records the view it stands on, which activate() re-applies. */
    function show(view) {
        page.view = view;
        const isPanel = view === "panel";
        if (instance !== active) return;
        tabs.forEach((button) => button.setAttribute("aria-selected", String(button.dataset.pageView === view)));
        pagePanel.hidden = !isPanel;
        encoding.hidden = isPanel;
        if (isPanel) return;
        if (page.cache.has(view)) {
            paintPage(page.cache.get(view), view);
            return;
        }
        out.textContent = "reading " + page.PAGE + "…";
        const read = view === "json"
            ? pageRead("application/json", page.PAGE).then((value) => typeof value === "string" ? value : JSON.stringify(value, null, 2))
            : pageRead("application/x-mtron-ui", page.PAGE).then((value) => typeof value === "string" ? value : String(value));
        read.then((text) => {
            page.cache.set(view, text);
            if (instance === active && page.view === view) paintPage(text, view);
        }).catch((error) => {
            if (instance === active && page.view === view)
                out.textContent = String(error && error.message ? error.message : error);
        });
    }

    /* read one element as a leaf card or (a .multi-card) a group of cards */
    function readCards(container) {
        return Array.from(container.children).map((el) => {
            if (el.classList && el.classList.contains("multi-card")) {
                const body = el.querySelector(".multi-card-body");
                return {
                    label: el.dataset.label || null,
                    col: Number(el.dataset.col) || 1,
                    row: el.dataset.row != null ? (Number(el.dataset.row) || 1) : null,
                    card: body ? readCards(body) : []
                };
            }
            if (!(el.dataset && el.dataset.tid)) return null;
            return {
                tid: el.dataset.tid,
                vid: el.dataset.vid || null,
                label: el.dataset.label || null,
                view: el.dataset.view || "panel",
                col: Number(el.dataset.col) || 1,
                row: el.dataset.row != null ? (Number(el.dataset.row) || 1) : null
            };
        }).filter((card) => card != null);
    }

    function pageCards() {
        if (loadedGrid && !loadedGrid.hidden) {
            return readCards(loadedGrid);
        }
        const cards = BEFORE.map((entry) => ({...entry}));
        $$(".obj-card", pinned).forEach((root) => {
            if (!root.dataset.tid) return;
            cards.push({
                tid: root.dataset.tid,
                vid: root.dataset.vid || null,
                label: root.dataset.label || null,
                view: root.dataset.view || "panel",
                col: Number(root.dataset.col) || 1,
                row: root.dataset.row != null ? (Number(root.dataset.row) || 1) : null
            });
        });
        AFTER.forEach((entry) => cards.push({...entry}));
        return cards;
    }

    /* a layout entry: a leaf splash_card (tid/vid) or a group (card => [cards]) */
    function mountLayoutCard(rec, parent) {
        if (!rec) return null;
        if (Array.isArray(rec.card)) return mountMultiCard(rec, parent);
        const tid = rec.tid ? String(rec.tid) : null;
        const cvid = rec.vid ? String(rec.vid) : null;
        if (!tid) return null;
        const known = KNOWN[tid];
        const cardTid = known ? known[0] : tid;
        const cardVid = cvid || (known ? known[1] : null);
        const span = spanOf(rec.span);
        const view = wordOf(rec.view) || "panel";
        if (cardVid) {
            return mountPinnedObjCard({tid: cardTid, vid: cardVid, raw: ""}, {
                label: rec.label ? String(rec.label) : tid,
                view: view,
                col: span.col,
                row: span.row,
                instance: instance,
                parent: parent
            });
        }
        const objCard = $$("[data-tid]", instance.main).find((el) => el.dataset.tid === cardTid);
        if (!objCard) return null;
        const label = rec.label ? String(rec.label) : (objCard.dataset.label || tid);
        const heading = objCard.querySelector("header h2");
        if (heading) heading.textContent = label;
        objCard.dataset.label = label;
        applySpan(objCard, span.col, span.row);
        parent.appendChild(objCard);
        return objCard;
    }

    /* a group of cards under one collapsible header; its body is a nested 4-col
       grid, so each child card's own span still applies within the group */
    function mountMultiCard(rec, parent) {
        const root = document.createElement("section");
        root.className = "multi-card";
        const span = spanOf(rec.span || {col: 4});
        applySpan(root, span.col, span.row);
        root.dataset.label = rec.label ? String(rec.label) : "";

        const head = document.createElement("header");
        head.className = "multi-card-head";
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "multi-card-toggle";
        toggle.textContent = "▾";
        toggle.title = "collapse";
        const title = document.createElement("span");
        title.className = "multi-card-title";
        title.textContent = rec.label ? String(rec.label) : "group";
        head.appendChild(toggle);
        head.appendChild(title);
        root.appendChild(head);

        const body = document.createElement("div");
        body.className = "multi-card-body";
        root.appendChild(body);

        head.addEventListener("click", () => {
            const collapsed = body.hidden;
            body.hidden = !collapsed;
            toggle.textContent = collapsed ? "▾" : "▸";
            toggle.title = collapsed ? "collapse" : "expand";
        });

        (rec.card || []).forEach((child) => mountLayoutCard(child, body));
        parent.appendChild(root);
        return root;
    }

    async function loadPage(source) {
        let vid = String(source == null ? "" : source).trim();
        if (vid.charAt(0) === "<" && vid.charAt(vid.length - 1) === ">") vid = vid.slice(1, -1);
        try {
            const url = new URL(source, location.href);
            const named = url.searchParams.get("vid");
            if (named) vid = named;
            if (vid.charAt(0) === "<" && vid.charAt(vid.length - 1) === ">") vid = vid.slice(1, -1);
        } catch (ignored) {
        }
        if (!MtronIO) throw new Error("mtron-io.js is not loaded");
        /* the vid is used exactly as given: mfs:boot/... is the mfs scheme, not the /mfs space path.
           the INSTANCE is asked for the page object first — an older deployment names its
           source tree fs: where the current one says mfs:, so both spellings are tried — and
           when the machine has no page of its own the LOCAL machine's page is inherited (a
           layout the machine can then adopt by saving). when nothing answers, the app's
           built-in layout (the static page) carries the page. */
        const swapped = /^mfs:/.test(vid) ? "fs:" + vid.slice(4)
            : /^fs:/.test(vid) ? "mfs:" + vid.slice(3) : null;
        const spellings = swapped ? [vid, swapped] : [vid];
        const attempts = [];
        spellings.forEach((spelling) => attempts.push({origin: "instance", vid: spelling}));
        spellings.forEach((spelling) => attempts.push({origin: "local", vid: spelling}));
        let last = null;
        let config = null;
        let loaded = null;
        for (const attempt of attempts) {
            try {
                const raw = attempt.origin === "local"
                    ? await localRead("application/x-mtron", attempt.vid)
                    : await pageRead("application/x-mtron", attempt.vid);
                const sourceText = String(raw == null ? "" : raw).trim();
                if (!sourceText || sourceText === "noobj" || /^fail::/.test(sourceText))
                    throw new Error(sourceText || "noobj");
                const value = plain(MtronIO.read(sourceText));
                if (!value || !value.layout) throw new Error("no splash_page object at " + attempt.vid);
                config = value;
                loaded = attempt;
                break;
            } catch (error) {
                last = error;
            }
        }
        if (!config) throw last || new Error("no page at " + vid);
        const layout = Array.isArray(config.layout) ? config.layout : [];
        loadedGrid.textContent = "";
        layout.forEach((rec) => mountLayoutCard(rec, loadedGrid));
        Array.from(instance.main.children).forEach((el) => {
            if (el !== loadedGrid) el.hidden = true;
        });
        loadedGrid.hidden = false;
        page.PAGE = loaded.origin === "local" ? vid : loaded.vid;
        page.origin = loaded.origin;
        /* the page mounted against THIS instance: make it the active one so the
           shell's chrome (and the console panel's #console-card) are this machine's */
        activate(instance);
        sync();
        if (config.title) {
            page.title = String(config.title);
            page.docTitle = page.title;
            if (instance === active) document.title = page.docTitle;
            /* the machine's hostname prefixes the page title: "booger - metatron" */
            readJsonOn(instance, "/network/host").then((hostName) => {
                const name = String(hostName == null ? "" : hostName).trim();
                if (name && name !== "noobj" && !/^fail::/.test(name)) {
                    page.docTitle = name + " - " + page.title;
                    if (instance === active) document.title = page.docTitle;
                }
            }).catch(() => {});
        }
        page.cache.clear();
        if (page.view !== "panel") show(page.view);
    }

    /* a leaf card or a group back to its splash_card::[…] spelling */
    function serializeCard(card) {
        if (card.card) {
            const parts = [];
            if (card.label) parts.push("label=>'" + String(card.label).replace(/'/g, "\\'") + "'");
            if (card.col > 1 || card.row != null) {
                const sp = [];
                if (card.col > 1) sp.push("col=>" + card.col);
                if (card.row != null) sp.push("row=>" + card.row);
                parts.push("span=>[" + sp.join(",") + "]");
            }
            parts.push("card=>[" + card.card.map(serializeCard).join(",") + "]");
            return "splash_card::[" + parts.join(",") + "]";
        }
        const parts = ["tid=><" + card.tid + ">"];
        if (card.vid) parts.push("vid=><" + card.vid + ">");
        if (card.label) parts.push("label=>'" + String(card.label).replace(/'/g, "\\'") + "'");
        if (card.view && card.view !== "panel") parts.push("view=>" + card.view);
        if (card.col > 1 || card.row != null) {
            const sp = [];
            if (card.col > 1) sp.push("col=>" + card.col);
            if (card.row != null) sp.push("row=>" + card.row);
            parts.push("span=>[" + sp.join(",") + "]");
        }
        return "splash_card::[" + parts.join(",") + "]";
    }

    async function savePage(destination) {
        const vid = String(destination == null ? page.PAGE : destination);
        if (pageSave && instance === active) {
            pageSave.disabled = true;
            pageSave.textContent = "saving…";
        }
        try {
            const layout = pageCards().map(serializeCard);
            const value = "[title=>'" + String(page.title).replace(/'/g, "\\'") + "',layout=>[" + layout.join(",") + "]]";
            const target = String(vid).charAt(0) === "<" ? String(vid) : "<" + vid + ">";
            const expr = target + " -> splash_page::" + value;
            const result = await pageEval(expr);
            const text = String(result == null ? "" : result);
            if (/^fail::/.test(text)) throw new Error(text);
            page.origin = "instance";   // the machine holds its own page now
            page.cache.clear();
            if (pageSave && instance === active) pageSave.textContent = "saved";
            if (page.view !== "panel") show(page.view);
            return true;
        } catch (error) {
            if (pageSave && instance === active) pageSave.textContent = "save failed";
            line("err", "page save: " + (error && error.message ? error.message : error));
            return false;
        } finally {
            if (pageSave && instance === active) {
                pageSave.disabled = false;
                setTimeout(updateSaveButton, 1500);
            }
        }
    }

    /* the save-as dialog is the shell's: it opens for the active instance and
       writes through that instance's own socket */
    function submitSaveAs(value) {
        const vid = String(value == null ? "" : value).trim().replace(/^</, "").replace(/>$/, "");
        if (!vid) {
            saveFail("a save location is required");
            return Promise.resolve(false);
        }
        if (isDefaultPage(vid)) {
            saveFail("that is the default page — choose another location");
            return Promise.resolve(false);
        }
        saveFail("");
        return savePage(vid).then((ok) => {
            if (!ok) return false;
            page.PAGE = vid;
            sync();
            if (page.view !== "panel") show(page.view);
            if (saveDialog) saveDialog.close();
            return true;
        });
    }

    Object.assign(page, {
        loadPage: loadPage,
        show: show,
        sync: sync,
        savePage: savePage,
        openSaveAs: openSaveAs,
        submitSaveAs: submitSaveAs,
        readOnly: readOnly
    });
    return page;
}

/* ── the page card's chrome (the shell) ───────────────────────────────────
   the view tabs, the save button and the save-as dialog are one page's, and they
   always drive the ACTIVE instance's page. */
$$(".page-tabs .popup-tab").forEach((button) => button.addEventListener("click", () => {
    if (active) active.page.show(button.dataset.pageView);
}));
(function wirePageChrome() {
    const pageSave = $("#page-save");
    if (pageSave) pageSave.addEventListener("click", () => {
        if (!active) return;
        if (active.page.readOnly()) active.page.openSaveAs();
        else active.page.savePage(active.page.PAGE);
    });
    const saveForm = $("#save-as-form");
    if (saveForm) saveForm.addEventListener("submit", (ev) => {
        ev.preventDefault();
        if (active) active.page.submitSaveAs($("#save-as-vid").value);
    });
    const saveCancel = $("#save-as-cancel");
    if (saveCancel) saveCancel.addEventListener("click", () => {
        const dialog = $("#save-as");
        if (dialog) dialog.close();
    });
})();

/* ── open: a page from this machine's file systems ────────────────────────
   the fsspace panel mounts in the dialog with a SELECT hook, so a click picks an
   address and reads nothing; the dialog's open button loads what was picked. the
   tree starts at the directory of the page on screen, and its space picker is the
   list the page discovered when it loaded (api.fsspaces). */
(function wireOpenPage() {
    const trigger = $("#page-open");
    const dialog = $("#open-page");
    if (!trigger || !dialog) return;
    const host = $("#open-page-tree");
    const chosen = $("#open-page-path");
    const ok = $("#open-page-ok");
    let selected = null;

    const reset = () => {
        selected = null;
        if (host) host.textContent = "";
        if (ok) ok.disabled = true;
        if (chosen) chosen.textContent = "no page chosen";
    };

    trigger.addEventListener("click", async () => {
        if (!active) return;
        reset();
        if (!dialog.open) dialog.showModal();
        const factory = MtronObjPanels && MtronObjPanels.resolve ? MtronObjPanels.resolve("/m/sys/space/fsspace") : null;
        if (!factory) {
            if (chosen) chosen.textContent = "the file system panel is not available";
            return;
        }
        const page = String(active.page.PAGE || "");
        const dir = page.lastIndexOf("/") > 0 ? page.slice(0, page.lastIndexOf("/")) : ".";
        const panel = await factory({
            tid: "/m/sys/space/fsspace",
            vid: dir,
            raw: ""
        }, {
            mtron: (expr) => evalMtronOn(active, expr),
            fsspaces: () => active.fsspaces || [],
            select: (uri) => {
                selected = uri;
                if (ok) ok.disabled = false;
                if (chosen) chosen.textContent = uri;
            }
        });
        if (host) host.appendChild(panel);
    });

    const cancel = $("#open-page-cancel");
    if (cancel) cancel.addEventListener("click", () => {
        dialog.close();
        reset();
    });
    dialog.addEventListener("close", reset);

    const form = $("#open-page-form");
    if (form) form.addEventListener("submit", (ev) => {
        ev.preventDefault();
        const uri = selected;
        if (!uri || !active) return;
        dialog.close();
        reset();
        active.page.loadPage(uri).catch((error) => {
            line("err", "page load: " + (error && error.message ? error.message : error));
        });
    });
})();
