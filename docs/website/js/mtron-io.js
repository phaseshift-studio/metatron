/*
 * mtron-io — a v1 reader/writer for the mtron VALUE grammar.
 *
 *   true | false          bool
 *   1324  |  -5           int
 *   12.345 | 1e3          real
 *   0xaf3403              bytes
 *   'x' | "x" | """x"""   str  (single/double single-line; triple multi-line; \ escapes)
 *   <a/b/c>               uri
 *   [1,3,4]               lst
 *   [a=>1,b=>2]           rec
 *   tid::value@vid        typed value (vid optional); nested typed values are values too
 *
 * The reader answers {tid, vid, value}. uri, sym, bytes and typed are tagged
 * values so a writer never confuses a string with a uri or a typed value:
 *
 *   uri("/m/web")             -> </m/web>
 *   sym("panel")              -> panel
 *   bytes([0xaf,0x34,0x03])   -> 0xaf3403
 *   typed("splash_card", rec) -> splash_card::[...]
 *
 * A plain JS object is a rec, an array is a lst, null/undefined is noobj.
 * UMD: window.MtronIO in a page, module.exports under node (for tests).
 */
(function (root, factory) {
    if (typeof module === "object" && module.exports) module.exports = factory();
    else root.MtronIO = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
    "use strict";

    const WS = /\s/;

    function uri(value) {
        return { $uri: String(value) };
    }

    function sym(value) {
        return { $sym: String(value) };
    }

    function bytes(value) {
        return { $bytes: value instanceof Uint8Array ? value : Uint8Array.from(value) };
    }

    function typed(tid, value, vid) {
        return { $typed: { tid: String(tid), value: value, vid: vid == null ? null : String(vid) } };
    }

    function terminates(c) {
        return c === undefined || WS.test(c) || c === "," || c === "]" || c === ")" || c === "}";
    }

    class Reader {
        constructor(text) {
            this.text = String(text == null ? "" : text);
            this.i = 0;
        }

        fail(message) {
            throw new Error(message + " at " + this.i);
        }

        skip() {
            while (this.i < this.text.length && WS.test(this.text[this.i])) this.i += 1;
        }

        peek() {
            return this.text[this.i];
        }

        startsWith(token) {
            return this.text.startsWith(token, this.i);
        }

        read() {
            const value = this.value();
            this.skip();
            if (this.i !== this.text.length) this.fail("trailing input");
            return value;
        }

        value() {
            this.skip();
            const c = this.peek();
            if (c === undefined) this.fail("value expected");
            if (c === "'" || c === '"') return this.string();
            if (c === "<") return this.uri();
            if (c === "[") return this.bracket();
            if (c === "-" || c === "+" || c === "." || (c >= "0" && c <= "9")) return this.number();
            if (this.startsWith("true") && terminates(this.text[this.i + 4])) { this.i += 4; return true; }
            if (this.startsWith("false") && terminates(this.text[this.i + 5])) { this.i += 5; return false; }
            if (this.startsWith("noobj") && terminates(this.text[this.i + 5])) { this.i += 5; return null; }

            const start = this.i;
            const token = this.symbol();
            if (token && this.startsWith("::")) {
                this.i += 2;
                const inner = this.value();
                this.skip();
                let vid = null;
                if (this.peek() === "@") {
                    this.i += 1;
                    vid = this.vid();
                }
                return typed(token, inner, vid);
            }
            this.i = start;
            const bare = this.symbol();
            if (bare) return sym(bare);
            this.fail("value expected");
        }

        symbol() {
            const start = this.i;
            while (this.i < this.text.length) {
                const c = this.text[this.i];
                if (WS.test(c)) break;
                if (",[](){}<>'\"=:".includes(c)) break;
                this.i += 1;
            }
            return this.text.slice(start, this.i);
        }

        vid() {
            const start = this.i;
            while (this.i < this.text.length) {
                const c = this.text[this.i];
                if (WS.test(c) || c === "," || c === "]" || c === ")" || c === "}" || c === "'" || c === '"') break;
                this.i += 1;
            }
            return this.text.slice(start, this.i);
        }

        string() {
            const quote = this.peek();
            if (quote === '"' && this.startsWith('"""')) {
                this.i += 3;
                const end = this.text.indexOf('"""', this.i);
                if (end < 0) this.fail("unterminated triple string");
                const text = this.text.slice(this.i, end);
                this.i = end + 3;
                return text;
            }
            this.i += 1;
            let out = "";
            while (this.i < this.text.length) {
                const c = this.text[this.i];
                this.i += 1;
                if (c === "\\") {
                    const esc = this.text[this.i];
                    this.i += 1;
                    if (esc === "n") out += "\n";
                    else if (esc === "t") out += "\t";
                    else if (esc === "r") out += "\r";
                    else if (esc === "\\" || esc === "'" || esc === '"') out += esc;
                    else this.fail("bad escape \\" + esc);
                } else if (c === quote) {
                    return out;
                } else if (c === "\n" || c === "\r") {
                    this.fail("newline in single-line string");
                } else {
                    out += c;
                }
            }
            this.fail("unterminated string");
        }

        uri() {
            this.i += 1;
            const end = this.text.indexOf(">", this.i);
            if (end < 0) this.fail("unterminated uri");
            const text = this.text.slice(this.i, end);
            this.i = end + 1;
            return uri(text);
        }

        number() {
            const match = /^[+-]?(?:0x[0-9a-fA-F]+|(?:\d+\.\d*|\.\d+|\d+)(?:[eE][+-]?\d+)?)/.exec(this.text.slice(this.i));
            if (!match) this.fail("bad number");
            const raw = match[0];
            this.i += raw.length;
            if (/^[+-]?0x/.test(raw)) {
                const hex = raw.replace(/^[+-]?0x/, "");
                if (hex.length % 2 !== 0) this.fail("odd hex byte count");
                return bytes(Uint8Array.from(hex.match(/../g).map((pair) => parseInt(pair, 16))));
            }
            if (/[.eE]/.test(raw)) return Number(raw);
            return parseInt(raw, 10);
        }

        bracket() {
            this.i += 1;
            this.skip();
            if (this.peek() === "]") { this.i += 1; return []; }
            if (this.startsWith("=>")) {
                this.i += 2;
                this.skip();
                if (this.peek() !== "]") this.fail("] expected after =>");
                this.i += 1;
                return {};
            }
            if (this.peek() === "," && this.text[this.i + 1] === "]") { this.i += 2; return []; }
            return this.pairAhead() ? this.record() : this.list();
        }

        /* does a top-level => arrive before the first top-level , or ]? */
        pairAhead() {
            let i = this.i;
            let depth = 0;
            let quote = null;
            for (; i < this.text.length; i += 1) {
                const c = this.text[i];
                if (quote) {
                    if (c === "\\") { i += 1; continue; }
                    if (c === quote) quote = null;
                    continue;
                }
                if (c === "'" || c === '"') {
                    if (c === '"' && this.text.startsWith('"""', i)) {
                        const end = this.text.indexOf('"""', i + 3);
                        if (end < 0) return false;
                        i = end + 2;
                        continue;
                    }
                    quote = c;
                    continue;
                }
                if (c === "<") {
                    const end = this.text.indexOf(">", i + 1);
                    if (end < 0) return false;
                    i = end;
                    continue;
                }
                if (c === "[" || c === "(" || c === "{") { depth += 1; continue; }
                if (c === "]" || c === ")" || c === "}") { if (depth === 0) return false; depth -= 1; continue; }
                if (depth !== 0) continue;
                if (c === ",") return false;
                if (c === "=" && this.text[i + 1] === ">") return true;
            }
            return false;
        }

        record() {
            const out = {};
            while (true) {
                this.skip();
                if (this.peek() === "]") { this.i += 1; return out; }
                if (this.peek() === ",") this.fail("empty rec entry");
                const key = this.key();
                this.skip();
                if (!this.startsWith("=>")) this.fail("=> expected");
                this.i += 2;
                out[key] = this.value();
                this.skip();
                if (this.peek() === ",") { this.i += 1; continue; }
                if (this.peek() === "]") { this.i += 1; return out; }
                this.fail("] expected");
            }
        }

        key() {
            this.skip();
            const c = this.peek();
            if (c === "'" || c === '"') return this.string();
            const start = this.i;
            while (this.i < this.text.length && !(this.text[this.i] === "=" && this.text[this.i + 1] === ">")) this.i += 1;
            const key = this.text.slice(start, this.i).trim();
            if (!key) this.fail("key expected");
            return key;
        }

        list() {
            const out = [];
            while (true) {
                this.skip();
                if (this.peek() === "]") { this.i += 1; return out; }
                if (this.peek() === ",") this.fail("empty list entry");
                out.push(this.value());
                this.skip();
                if (this.peek() === ",") { this.i += 1; continue; }
                if (this.peek() === "]") { this.i += 1; return out; }
                this.fail("] expected");
            }
        }
    }

    function split(text) {
        let depth = 0;
        let quote = null;
        let typeAt = -1;
        let vidAt = -1;
        for (let i = 0; i < text.length; i += 1) {
            const c = text[i];
            if (quote) {
                if (c === "\\") { i += 1; continue; }
                if (c === quote) quote = null;
                continue;
            }
            if (c === "'" || c === '"') {
                if (c === '"' && text.startsWith('"""', i)) {
                    const end = text.indexOf('"""', i + 3);
                    if (end < 0) return { typeAt: -1, vidAt: -1 };
                    i = end + 2;
                    continue;
                }
                quote = c;
                continue;
            }
            if (c === "<") {
                const end = text.indexOf(">", i + 1);
                if (end < 0) return { typeAt: -1, vidAt: -1 };
                i = end;
                continue;
            }
            if (c === "[" || c === "(" || c === "{") { depth += 1; continue; }
            if (c === "]" || c === ")" || c === "}") { if (depth > 0) depth -= 1; continue; }
            if (depth !== 0) continue;
            if (c === ":" && text[i + 1] === ":" && typeAt < 0) { typeAt = i; i += 1; continue; }
            if (c === "@") vidAt = i;
        }
        return { typeAt, vidAt };
    }

    /** read text -> {tid, vid, value}; tid/vid are null for a bare value */
    function read(text) {
        const source = String(text == null ? "" : text);
        const at = split(source);
        const start = at.typeAt >= 0 ? at.typeAt + 2 : 0;
        const end = at.vidAt >= 0 ? at.vidAt : source.length;
        const valueText = source.slice(start, end);
        const reader = new Reader(valueText);
        const value = reader.read();
        return {
            tid: at.typeAt > 0 ? source.slice(0, at.typeAt).trim() : null,
            vid: at.vidAt >= 0 ? source.slice(at.vidAt + 1).trim() : null,
            value
        };
    }

    function value(text) {
        return read(text).value;
    }

    function quote(text) {
        const s = String(text);
        const multiline = /[\n\r]/.test(s);
        if (!multiline && s.indexOf("'") < 0) return "'" + s.replace(/\\/g, "\\\\") + "'";
        if (!multiline && s.indexOf('"') < 0) return '"' + s.replace(/\\/g, "\\\\") + '"';
        if (s.indexOf('"""') < 0) return '"""' + s + '"""';
        return '"' + s.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n").replace(/\r/g, "\\r") + '"';
    }

    function writeKey(key) {
        const s = String(key);
        if (/^[^\s,[\](){}<>@'\"=]+$/.test(s) && s.indexOf("=>") < 0) return s;
        return quote(s);
    }

    function write(value) {
        if (value === null || value === undefined) return "noobj";
        if (value instanceof Uint8Array) return "0x" + Array.from(value).map((b) => b.toString(16).padStart(2, "0")).join("");
        const type = typeof value;
        if (type === "boolean") return value ? "true" : "false";
        if (type === "number") {
            if (!Number.isFinite(value)) throw new Error("cannot write a non-finite number");
            return String(value);
        }
        if (type === "string") return quote(value);
        if (Array.isArray(value)) return "[" + value.map(write).join(",") + "]";
        if (type === "object") {
            if ("$uri" in value) {
                const s = String(value.$uri);
                if (/[<>\s]/.test(s)) throw new Error("uri contains a delimiter");
                return "<" + s + ">";
            }
            if ("$sym" in value) {
                const s = String(value.$sym);
                if (!/^[^\s,[\](){}<>@'\"=:]+$/.test(s)) throw new Error("symbol contains a delimiter");
                return s;
            }
            if ("$bytes" in value) {
                const b = value.$bytes instanceof Uint8Array ? value.$bytes : Uint8Array.from(value.$bytes);
                return "0x" + Array.from(b).map((x) => x.toString(16).padStart(2, "0")).join("");
            }
            if ("$typed" in value) {
                const t = value.$typed;
                const tail = t.vid ? "@" + String(t.vid) : "";
                return String(t.tid) + "::" + write(t.value) + tail;
            }
            const keys = Object.keys(value);
            if (!keys.length) return "[=>]";
            return "[" + keys.map((key) => writeKey(key) + "=>" + write(value[key])).join(",") + "]";
        }
        throw new Error("cannot write a " + type);
    }

    function writeEnvelope(tid, value, vid) {
        const head = tid ? String(tid) + "::" : "";
        const tail = vid ? "@" + String(vid) : "";
        return head + write(value) + tail;
    }

    return { read, value, write, writeEnvelope, uri, sym, bytes, typed, Reader };
});
