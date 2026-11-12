/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

// ── asciinema casts ──────────────────────────────────────────────────────────
// Mount every `.asciinema-cast` container on the page.  The player itself is
// vendored (pinned, Apache-2.0, no CDN) under lib/asciinema-player/ and is
// fetched only when the page actually carries a cast — the same lazy pattern
// the shared header uses for MathJax.
//
// A container names a recording relative to the page it sits in, and may set
// the player options the docs pipeline exposes:
//
//     <div class="asciinema-cast" data-cast="asciinema/repl-basics.cast"
//          data-cols="100" data-rows="24" data-autoplay data-loop></div>
//
// `data-cols`/`data-rows` pre-size the first paint; the recording's own header
// takes over once it loads. A page can be built before the cast is regenerated,
// so those copies can lag — the header is read from the fetched text and the
// dialog is re-sized before mounting, instead of trusting the page's copy.
//
// ── mtron syntax highlighting ───────────────────────────────────────────────
// The player paints terminal CELLS, not DOM text, so highlight.js cannot touch
// it directly.  Instead, when the page already carries highlight.js and the
// mtron grammar (the site header loads both), a recording is colourised before
// it reaches the player: each plain-text line is run through the SAME mtron
// grammar the listings use, its token classes are mapped to ANSI SGR, and the
// recording is re-emitted character-for-character with the original timings.
// The result is the site's colour scheme inside the terminal, with typing still
// animated; a recording that already carries ANSI is passed through untouched.
// `data-highlight="off"` disables the pass for one container.

(function () {
    "use strict";

    // the player's location, derived from THIS script's URL (…/js/asciinema.js
    // → …/lib/asciinema-player/), so the page's depth never has to be known here
    var script = document.currentScript;
    var base = script ? new URL("../lib/asciinema-player/", script.src).href
                      : "lib/asciinema-player/";
    var loading = null;

    // ── the palette comes from the PAGE, not from a table ───────────────────
    // A listing is styled by CSS (night-owl's generic tokens + metatron.css's
    // .hljs-mtron-* overrides); the terminal can only receive ANSI. So instead
    // of re-declaring colours, ask the browser what each token actually
    // computes to and emit that as 24-bit SGR: the terminal then renders the
    // very stylesheet the code blocks render, overrides, italics and all.
    var probe = null;
    var baseColor = null;
    var tokenSGR = {};

    function probeElement() {
        if (probe) return probe;
        probe = document.createElement("div");
        probe.className = "hljs";
        probe.setAttribute("aria-hidden", "true");
        probe.style.cssText = "position:absolute;left:-9999px;top:0;visibility:hidden;"
            + "white-space:nowrap;padding:0;border:0;";
        // inside the content area, so rules scoped to it still apply
        (document.querySelector(".markdown-body") || document.body).appendChild(probe);
        baseColor = window.getComputedStyle(probe).color;
        return probe;
    }

    function hexOf(cssColor) {
        var m = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/.exec(cssColor || "");
        if (!m) return null;
        return "#" + [m[1], m[2], m[3]].map(function (n) {
            var h = Number(n).toString(16);
            return h.length === 1 ? "0" + h : h;
        }).join("");
    }

    // the SGR prefix for one token class; null means "terminal default"
    function styleOf(className) {
        if (!className) return null;
        if (Object.prototype.hasOwnProperty.call(tokenSGR, className)) return tokenSGR[className];
        var sgr = null;
        try {
            var holder = probeElement();
            var span = document.createElement("span");
            span.className = className;
            holder.appendChild(span);
            var cs = window.getComputedStyle(span);
            var hex = hexOf(cs.color);
            var italic = cs.fontStyle === "italic";
            var bold = parseInt(cs.fontWeight, 10) >= 600;
            if (cs.color !== baseColor || italic || bold) {
                sgr = "0";
                if (hex) {
                    sgr += ";38;2;" + parseInt(hex.slice(1, 3), 16)
                        + ";" + parseInt(hex.slice(3, 5), 16)
                        + ";" + parseInt(hex.slice(5, 7), 16);
                }
                if (italic) sgr += ";3";
                if (bold) sgr += ";1";
            }
            holder.removeChild(span);
        } catch (e) {
            sgr = null;
        }
        tokenSGR[className] = sgr;
        return sgr;
    }

    // the recording's theme: the listing's own background and foreground
    function theme() {
        var holder = probeElement();
        var cs = window.getComputedStyle(holder);
        var fg = hexOf(cs.color) || "#d6deeb";
        var bg = hexOf(cs.backgroundColor) || "#011627";
        return {
            fg: fg,
            bg: bg,
            palette: [bg, "#ff5874", "#addb67", "#f78c6c",
                      "#82aaff", "#c792ea", "#7fdbca", fg,
                      "#637777", "#ff6363", "#ecc48d", "#ffcb8b",
                      "#dcdcaa", "#ff869a", "#80cbc4", "#ffffff"].join(":")
        };
    }

    function loadPlayer() {
        if (window.AsciinemaPlayer) return Promise.resolve(window.AsciinemaPlayer);
        if (loading) return loading;
        loading = new Promise(function (resolve, reject) {
            var css = document.createElement("link");
            css.rel = "stylesheet";
            css.href = base + "asciinema-player.css";
            document.head.appendChild(css);

            var js = document.createElement("script");
            js.src = base + "asciinema-player.min.js";
            js.onload = function () { resolve(window.AsciinemaPlayer); };
            js.onerror = function () { reject(new Error("could not load " + js.src)); };
            document.head.appendChild(js);
        });
        return loading;
    }

    function options(el) {
        var d = el.dataset;
        var opts = {
            fit: false,           // natural cell size: the dialog grows with the
                                  // recording (metatron.css) and the wrapper
                                  // scrolls instead of shrinking a wide table
            controls: false,      // the player's bar reserves ~2.5 rows inside the
                                  // terminal; our own strip controls playback instead
            // pin the family we measure: a late font swap changes cell metrics and
            // the wrapper keeps the pre-swap row budget (last rows fall off)
            terminalFontFamily: "'JetBrains Mono', 'Fira Mono', monospace",
            // budget and render the SAME row height: the player's default budget
            // is 1.3333 while the page's line-height renders 1.6, so the tail
            // rows of a tall recording fall outside the wrapper
            terminalLineHeight: 1.4333,
            preload: true,
            theme: d.theme || "auto/asciinema"
        };
        if (d.cols) opts.cols = Number(d.cols);
        if (d.rows) opts.rows = Number(d.rows);
        if (d.speed) opts.speed = Number(d.speed);
        if (d.idleTimeLimit) opts.idleTimeLimit = Number(d.idleTimeLimit);
        if (d.poster) opts.poster = d.poster;
        if (d.fit) opts.fit = d.fit;
        if (d.autoplay !== undefined) opts.autoplay = true;
        if (d.loop !== undefined) opts.loop = true;
        return opts;
    }

    // ── mtron highlighting: cast text → SGR-coloured cast text ──────────────

    // one line → one SGR prefix per character (null = terminal default)
    function lineStyles(line) {
        var styles = new Array(line.length);
        var html;
        try {
            html = window.hljs.highlight(line, { language: "mtron", ignoreIllegals: true }).value;
        } catch (e) {
            return styles;
        }
        var holder = document.createElement("div");
        holder.innerHTML = html;
        var pos = 0;
        (function walk(node, style) {
            for (var i = 0; i < node.childNodes.length; i++) {
                var child = node.childNodes[i];
                if (child.nodeType === 3) {
                    var text = child.nodeValue || "";
                    for (var k = 0; k < text.length; k++) {
                        if (pos < styles.length) styles[pos++] = style;
                    }
                } else if (child.nodeType === 1) {
                    walk(child, styleOf(child.getAttribute("class")) || style);
                }
            }
        })(holder, null);
        return styles;
    }

    // the whole recording, re-emitted with a style at every character
    function highlightCast(text) {
        var lines = text.split("\n");
        if (lines.length < 2) return text;
        var header = lines[0];
        var items = [];
        var time = 0;
        for (var i = 1; i < lines.length; i++) {
            var raw = lines[i].trim();
            if (!raw || raw.charAt(0) !== "[") continue;      // comments/blanks
            var ev;
            try { ev = JSON.parse(raw); } catch (e) { continue; }
            time += Number(ev[0]) || 0;
            items.push({ t: time, code: ev[1], data: ev[2] });
        }
        if (items.length === 0) return text;

        var plain = "";
        items.forEach(function (it) { if (it.code === "o") plain += it.data; });
        if (plain.indexOf("\u001b") !== -1) return text;       // already coloured

        var styles = [];
        var offset = 0;
        plain.split("\n").forEach(function (line, idx, all) {
            var st = lineStyles(line);
            for (var k = 0; k < line.length; k++) styles[offset + k] = st[k];
            offset += line.length;
            if (idx < all.length - 1) { styles[offset] = null; offset++; }   // newline resets
        });

        // the player resolves "auto/…" from the RECORDING's theme, so put the
        // listing's computed background/foreground in the header
        try {
            var head = JSON.parse(header);
            if (head && head.term && !head.term.theme) {
                head.term.theme = theme();
                header = JSON.stringify(head);
            }
        } catch (e) {
            // a malformed header is the player's problem, not the colouriser's
        }

        var out = [header];
        var last = 0, cursor = 0, current = null;
        items.forEach(function (it) {
            if (it.code !== "o") {
                out.push(JSON.stringify([it.t - last, it.code, it.data]));
                last = it.t;
                return;
            }
            for (var k = 0; k < it.data.length; k++) {
                var ch = it.data.charAt(k);
                var style = styles[cursor++] || null;
                var prefix = "";
                if (style !== current) {
                    prefix = "\u001b[" + (style || "0") + "m";
                    current = style;
                }
                out.push(JSON.stringify([it.t - last, "o", prefix + ch]));
                last = it.t;
            }
        });
        return out.join("\n") + "\n";
    }

    function canHighlight(el) {
        return el.dataset.highlight !== "off"
            && window.hljs && window.hljs.getLanguage
            && window.hljs.getLanguage("mtron");
    }

    // ── mounting ────────────────────────────────────────────────────────────

    // the caption bar lives beside the cast, in the same embed/figure
    function captionFor(el) {
        var host = el.closest
            ? el.closest(".asciinema-embed, .asciinema-figure, .asciinema-modal")
            : null;
        return host ? host.querySelector(".asciinema-caption") : null;
    }

    function bindCaption(instance, el) {
        var caption = captionFor(el);
        if (!caption || !instance || !instance.addEventListener) return;
        // markers carry the doc's [-- ... --] annotations (and [HEADER] lines)
        instance.addEventListener("marker", function (marker) {
            if (marker && marker.label) {
                caption.textContent = marker.label;
                caption.classList.add("show");
            }
        });
    }

    // ── playback speed ──────────────────────────────────────────────────────
    // The player takes a `speed` option but exposes no runtime setter, so a rate
    // change re-mounts the same source at the current position.
    function setSpeed(el, rate) {
        var player = window.AsciinemaPlayer;
        if (!player || !el.__source) return;
        var at = 0;
        try {
            at = (el.__player && el.__player.getCurrentTime) ? el.__player.getCurrentTime() : 0;
        } catch (e) {
            at = 0;
        }
        if (el.__player && el.__player.dispose) el.__player.dispose();
        var opts = Object.assign({}, el.__opts, { speed: rate, startAt: at });
        var instance = player.create(el.__source, el, opts);
        el.__player = instance;
        el.__rate = rate;
        bindCaption(instance, el);
        if (instance.play) instance.play();
        Array.prototype.forEach.call(el.__speedBar ? el.__speedBar.children : [], function (button) {
            var active = Number(button.getAttribute("data-speed")) === rate;
            button.classList.toggle("active", active);
            button.setAttribute("aria-pressed", active ? "true" : "false");
        });
    }

    function rates(el) {
        // data-speed-rates="0.25,0.5,1,2" — the knob; default 0.5/1/2
        var raw = el.dataset.speedRates;
        if (!raw) return [0.5, 1, 2];
        var out = raw.split(/[,\s]+/).map(Number).filter(function (n) {
            return isFinite(n) && n > 0;
        });
        return out.length ? out : [0.5, 1, 2];
    }

    function speedControls(el, instance) {
        if (el.dataset.speed === "off" || el.__speedBar) return;
        var bar = document.createElement("div");
        bar.className = "asciinema-controls";
        // the player's own control bar reserves rows inside the terminal, so it
        // is off; play/pause lives here instead
        var play = document.createElement("button");
        play.type = "button";
        play.className = "asciinema-speed asciinema-play";
        play.title = "play / pause";
        play.textContent = "\u23F8";
        play.addEventListener("click", function (ev) {
            ev.preventDefault();
            if (!el.__player) return;
            if (play.dataset.playing === "1") el.__player.pause();
            else el.__player.play();
        });
        if (instance && instance.addEventListener) {
            instance.addEventListener("play", function () {
                play.dataset.playing = "1";
                play.textContent = "\u23F8";
            });
            instance.addEventListener("pause", function () {
                play.dataset.playing = "0";
                play.textContent = "\u25B6";
            });
        }
        bar.appendChild(play);

        // copy the tutorial's pasteable script (the .txt sidecar beside the cast)
        var copy = document.createElement("button");
        copy.type = "button";
        copy.className = "asciinema-speed asciinema-copy";
        copy.title = "copy this tutorial's commands to the clipboard";
        copy.setAttribute("data-copy-script", el.dataset.cast.replace(/\.cast$/, ".txt"));
        copy.innerHTML = "<i class=\"bi bi-clipboard\" aria-hidden=\"true\"></i><span>copy</span>";
        bar.appendChild(copy);
        rates(el).forEach(function (rate) {
            var button = document.createElement("button");
            button.type = "button";
            button.className = "asciinema-speed" + (rate === el.__rate ? " active" : "");
            button.setAttribute("data-speed", rate);
            button.setAttribute("aria-pressed", rate === el.__rate ? "true" : "false");
            button.textContent = rate + "×";
            button.addEventListener("click", function (ev) {
                ev.preventDefault();
                if (rate !== el.__rate) setSpeed(el, rate);
            });
            bar.appendChild(button);
        });
        el.insertAdjacentElement("afterend", bar);
        el.__speedBar = bar;
    }

    // The page's data-cols/--cols is a snapshot taken when the page was built;
    // the recording's own header is the truth (a cast regenerated after its page
    // can be wider). Read it from the text we already fetched and re-size the
    // dialog before the player mounts, so a wide explain() table is never
    // clipped by a stale column count.
    function adoptCastSize(el, opts, text) {
        var term;
        try {
            term = JSON.parse(text.split("\n")[0]).term;
        } catch (e) {
            return;                              // malformed header: the player's problem
        }
        if (!term || !term.cols) return;
        el.dataset.cols = String(term.cols);
        opts.cols = Number(term.cols);
        if (term.rows) {
            el.dataset.rows = String(term.rows);
            opts.rows = Number(term.rows);
        }
        var modal = el.closest ? el.closest(".asciinema-modal") : null;
        if (modal) modal.style.setProperty("--cols", String(term.cols));
    }

    // Size the dialog from the player's REAL width. The stylesheet can only
    // estimate it (columns × px-per-cell), and a fraction of a pixel per column
    // adds up to a permanent scrollbar across the last couple of columns on a
    // wide recording. The measurement is capped by metatron.css (100vw - 2rem),
    // so anything wider than the browser still gets its scrollbar.
    function sizeDialog(el) {
        var opts = el.__opts;
        if (opts && opts.fit) return;          // an explicit fit mode scales the player
        var modal = el.closest ? el.closest(".asciinema-modal") : null;
        if (!modal) return;
        var dialog = modal.querySelector(".modal-dialog");
        var player = el.querySelector(".ap-player");
        if (!dialog || !player) return;
        var content = dialog.querySelector(".modal-content");
        var borders = 0;
        if (content) {
            var cs = window.getComputedStyle(content);
            borders = (parseFloat(cs.borderLeftWidth) || 0)
                + (parseFloat(cs.borderRightWidth) || 0);
        }
        var width = player.getBoundingClientRect().width;
        if (width > 0) dialog.style.width = Math.ceil(width + borders) + "px";
    }

    function mount(el, player) {
        if (!el.dataset.cast || el.dataset.mounted) return;
        el.dataset.mounted = "1";
        el.__alive = true;
        var opts = options(el);
        if (el.__rate === undefined) {
            // data-speed-default="1.5" — the knob; default 1x
            var start = Number(el.dataset.speedDefault);
            el.__rate = isFinite(start) && start > 0 ? start : 1;
        }
        opts.speed = el.__rate;

        function create(source) {
            if (!el.__alive) return;
            el.__source = source;
            el.__opts = opts;
            var instance = player.create(source, el, opts);
            el.__player = instance;
            bindCaption(instance, el);
            speedControls(el, instance);
            // the modal's final width lands after `shown`; the player otherwise
            // keeps a wrapper height computed from a pre-layout width, and the
            // terminal loses rows at the bottom. The dialog is then sized from
            // the player itself (not from the stylesheet's per-column guess).
            requestAnimationFrame(function () {
                window.dispatchEvent(new Event("resize"));
                sizeDialog(el);
            });
            // a URL source (no highlight pass) learns its real size only when
            // the recording's header arrives: size the dialog again then
            if (instance && instance.addEventListener) {
                instance.addEventListener("reset", function () {
                    requestAnimationFrame(function () { sizeDialog(el); });
                });
            }
        }

        if (!canHighlight(el)) {
            create(el.dataset.cast);
            return;
        }
        // fetch once (the player would fetch anyway), colour it, hand it over
        fetch(el.dataset.cast)
            .then(function (r) {
                if (!r.ok) throw new Error(r.status + " " + r.statusText);
                return r.text();
            })
            .then(function (txt) {
                adoptCastSize(el, opts, txt);
                create({ data: highlightCast(txt) });
            })
            .catch(function () { create(el.dataset.cast); });
    }

    function unmount(el) {
        el.__alive = false;
        if (el.__player && el.__player.dispose) el.__player.dispose();
        delete el.__player;
        delete el.dataset.mounted;
        if (el.__speedBar) {
            el.__speedBar.remove();
            delete el.__speedBar;
        }
        var caption = captionFor(el);
        if (caption) {
            caption.textContent = "";
            caption.classList.remove("show");
        }
    }

    function init() {
        var casts = document.querySelectorAll(".asciinema-cast");
        if (casts.length === 0) return;

        loadPlayer().then(function (player) {
            // mount after the mono font is ready, else the first frame's
            // metrics flip once the web font arrives
            var fonts = (document.fonts && document.fonts.load)
                ? document.fonts.load('1em "JetBrains Mono"').catch(function () {})
                : Promise.resolve();
            fonts.then(function () {
                return document.fonts && document.fonts.ready
                    ? document.fonts.ready : null;
            }).then(function () {
                // Bootstrap wants a modal at the top of the document: nested in
                // the page's content it can be trapped below the page's stacking
                // context — the dialog dims and the backdrop swallows every click
                Array.prototype.forEach.call(document.querySelectorAll(".asciinema-modal"),
                    function (modal) {
                        if (modal.parentElement !== document.body) document.body.appendChild(modal);
                    });

                Array.prototype.forEach.call(casts, function (el) {
                    // a modal's player is mounted when the modal opens: a page
                    // with a demo then costs nothing until somebody watches it
                    if (!el.closest || !el.closest(".modal")) mount(el, player);
                });

                Array.prototype.forEach.call(document.querySelectorAll(".asciinema-modal"),
                    function (modal) {
                        var cast = modal.querySelector(".asciinema-cast");
                        if (!cast) return;
                        modal.addEventListener("shown.bs.modal", function () {
                            mount(cast, player);
                        });
                        modal.addEventListener("hidden.bs.modal", function () {
                            unmount(cast);
                        });
                    });

                // deep link: page.html#cast-name opens that recording
                var id = location.hash.slice(1);
                var target = id ? document.getElementById(id) : null;
                if (target && target.classList.contains("asciinema-modal") && window.bootstrap) {
                    window.bootstrap.Modal.getOrCreateInstance(target).show();
                }
            });
        }).catch(function (e) {
            Array.prototype.forEach.call(casts, function (el) {
                el.textContent = "could not load the terminal player: " + e.message;
            });
        });
    }

    // ── copy a tutorial's pasteable script ──────────────────────────────────
    // the script is a .txt sidecar the docs pass writes beside the recording
    // (one ';'-terminated statement per line, no results)
    document.addEventListener("click", function (ev) {
        var button = ev.target.closest ? ev.target.closest("[data-copy-script]") : null;
        if (!button) return;
        ev.preventDefault();
        var path = button.getAttribute("data-copy-script");
        var done = function () {
            var span = button.querySelector("span");
            if (!span) return;
            var before = span.textContent;
            span.textContent = "copied";
            setTimeout(function () { span.textContent = before; }, 1400);
        };
        fetch(path)
            .then(function (r) { return r.ok ? r.text() : Promise.reject(r.status); })
            .then(function (text) {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    return navigator.clipboard.writeText(text);
                }
                var area = document.createElement("textarea");
                area.value = text;
                document.body.appendChild(area);
                area.select();
                document.execCommand("copy");
                area.remove();
            })
            .then(done)
            .catch(function () { button.title = "could not copy the script"; });
    });

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
