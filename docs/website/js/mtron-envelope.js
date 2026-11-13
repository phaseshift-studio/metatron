/*
 * mtron-envelope — read the addresses of mtron values without parsing the values.
 *
 *   <tid>::[ … ]@<vid>      /m/instset::[pattern=>/m/web/#, …]@/m/web
 *   <tid>::<scalar>@<vid>    nat::29@/usr/marko/age
 *   <scalar>@<vid>           3@/x
 *
 * parse() takes the OUTER envelope: the first top-level `::` ends the type (tid), the last
 * top-level `@` starts the location (vid). objects() walks the whole text and reports every
 * nested `<tid>::[ … ]` (optionally `@<vid>`) — the clickable objects inside a value.
 *
 * Brackets ([ ( {) and single/double quotes shield both separators, so a nested rec, a
 * string containing `@`, or a uri containing `::` cannot split an envelope.
 *
 * Machine-to-machine values come from `out=application/x-mtron`. The UI serializer
 * (`x-mtron-ui`) clips and elides, so it is for human reading and NOT for this parser.
 *
 * UMD: `window.MtronEnvelope` in a browser, `module.exports` under node (for tests).
 */
(function (root, factory) {
    if (typeof module === "object" && module.exports) module.exports = factory();
    else root.MtronEnvelope = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
    "use strict";

    const OPENERS = { "[": true, "(": true, "{": true };
    const CLOSERS = { "]": true, ")": true, "}": true };

    /** first top-level `::` (the type separator) and last top-level `@` (the vid separator) */
    function split(text) {
        let depth = 0;
        let quote = null;
        let typeAt = -1;
        let vidAt = -1;
        for (let i = 0; i < text.length; i++) {
            const c = text[i];
            if (quote) {
                if (c === "\\") { i++; continue; }
                if (c === quote) quote = null;
                continue;
            }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (OPENERS[c]) { depth++; continue; }
            if (CLOSERS[c]) { if (depth > 0) depth--; continue; }
            if (depth !== 0) continue;
            if (c === ":" && text[i + 1] === ":" && typeAt < 0) { typeAt = i; i++; continue; }
            if (c === "@") vidAt = i;
        }
        return { typeAt: typeAt, vidAt: vidAt };
    }

    /**
     * Parse a value's envelope. Answers null when the text carries neither a type nor a
     * location (a bare rec, a number, `noobj`, an html string, … — all of which still have a
     * widget, just not one keyed by an address).
     */
    function parse(text) {
        const raw = String(text == null ? "" : text).trim();
        if (!raw || raw === "noobj")
            return null;
        const splitAt = split(raw);

        let tid = null;
        if (splitAt.typeAt > 0) {
            const candidate = raw.slice(0, splitAt.typeAt).trim();
            if (candidate && !/\s/.test(candidate))
                tid = candidate;
        }

        let vid = null;
        if (splitAt.vidAt > splitAt.typeAt && splitAt.vidAt < raw.length - 1) {
            const candidate = raw.slice(splitAt.vidAt + 1).trim();
            // a location is a path, an angle-uri, or — only when the value already names a type —
            // a relative name (`protocol::T@http`). a bare `http://h/a@b` must not become vid `b`.
            if (candidate && (tid || candidate[0] === "/" || candidate[0] === "<"))
                vid = candidate;
        }

        if (!tid && !vid)
            return null;
        return {
            tid: tid,
            vid: vid,
            shortTid: tid ? tid.split("/").pop().replace(/\{[^}]*\}$/, "") : null,
            raw: raw
        };
    }

    function skipString(text, i) {
        const quote = text[i];
        for (i += 1; i < text.length; i++) {
            if (text[i] === "\\") { i++; continue; }
            if (text[i] === quote) return i + 1;
        }
        return i;
    }

    /** the index just past the bracket that opens at `open` */
    function closeOf(text, open) {
        let depth = 0;
        let quote = null;
        for (let i = open; i < text.length; i++) {
            const c = text[i];
            if (quote) {
                if (c === "\\") { i++; continue; }
                if (c === quote) quote = null;
                continue;
            }
            if (c === "'" || c === '"') { quote = c; continue; }
            if (OPENERS[c]) depth++;
            else if (CLOSERS[c]) {
                depth--;
                if (depth === 0) return i + 1;
            }
        }
        return text.length;
    }

    /** the index just past a vid that starts at `i` (a path/uri token) */
    function vidEnd(text, i) {
        while (i < text.length && !/[\s,\]})'"]/.test(text[i])) i++;
        return i;
    }

    const TYPE_CHAR = /[A-Za-z0-9_./#?&=+~-]/;

    /** the start of the type token ending at `end`, including a trailing `{…}` coefficient */
    function typeStart(text, end) {
        let i = end;
        while (i > 0) {
            const c = text[i - 1];
            if (c === "}") {
                let depth = 1;
                let j = i - 1;
                while (j > 0 && depth > 0) {
                    j -= 1;
                    if (text[j] === "}") depth += 1;
                    else if (text[j] === "{") depth -= 1;
                }
                if (depth !== 0) return i;
                i = j;
                continue;
            }
            if (TYPE_CHAR.test(c)) { i -= 1; continue; }
            break;
        }
        return i;
    }

    /**
     * Every nested `<tid>::[ … ]` (with an optional `@vid`) in the text, in source order.
     * Each answer carries its `start`/`end` offsets and the exact `raw` slice, so a renderer
     * can put a control at the object's edge. A scalar (`tid::T`, `nat::29`) is not reported:
     * the shape this serves is the bracket-valued object.
     */
    function objects(text) {
        const source = String(text == null ? "" : text);
        const found = [];
        let i = 0;
        while (i < source.length) {
            const c = source[i];
            if (c === "'" || c === '"') { i = skipString(source, i); continue; }
            if (c === ":" && source[i + 1] === ":") {
                const start = typeStart(source, i);
                const tid = source.slice(start, i);
                const open = i + 2;
                if (tid && source[open] === "[" && /^[A-Za-z/]/.test(tid)) {
                    const end = closeOf(source, open);
                    let vid = null;
                    let stop = end;
                    if (source[end] === "@") {
                        const ve = vidEnd(source, end + 1);
                        if (ve > end + 1) {
                            vid = source.slice(end + 1, ve);
                            stop = ve;
                        }
                    }
                    found.push({
                        tid: tid,
                        vid: vid,
                        shortTid: tid.split("/").pop().replace(/\{[^}]*\}$/, ""),
                        start: start,
                        end: stop,
                        raw: source.slice(start, stop)
                    });
                }
                i += 2;
                continue;
            }
            i++;
        }
        return found;
    }

    return { parse: parse, objects: objects };
});
