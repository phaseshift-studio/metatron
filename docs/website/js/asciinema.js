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
// `data-cols`/`data-rows` should match the cast header: without them the player
// paints at 80x24 and resizes once the recording loads.

(function () {
    "use strict";

    // the player's location, derived from THIS script's URL (…/js/asciinema.js
    // → …/lib/asciinema-player/), so the page's depth never has to be known here
    var script = document.currentScript;
    var base = script ? new URL("../lib/asciinema-player/", script.src).href
                      : "lib/asciinema-player/";
    var loading = null;

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
            fit: "width",
            controls: "auto",
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

    // the caption bar lives beside the cast, in the same embed/figure
    function captionFor(el) {
        var host = el.closest
            ? el.closest(".asciinema-embed, .asciinema-figure, .asciinema-modal")
            : null;
        return host ? host.querySelector(".asciinema-caption") : null;
    }

    function mount(el, player) {
        if (!el.dataset.cast || el.dataset.mounted) return null;
        el.dataset.mounted = "1";
        var instance = player.create(el.dataset.cast, el, options(el));
        el.__player = instance;
        var caption = captionFor(el);
        if (caption && instance && instance.addEventListener) {
            // markers carry the doc's [-- ... --] annotations (and [HEADER] lines)
            instance.addEventListener("marker", function (marker) {
                if (marker && marker.label) {
                    caption.textContent = marker.label;
                    caption.classList.add("show");
                }
            });
        }
        return instance;
    }

    function unmount(el) {
        if (el.__player && el.__player.dispose) el.__player.dispose();
        delete el.__player;
        delete el.dataset.mounted;
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

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
