/*
 * mtron-io — a reader/writer for the mtron VALUE grammar, over PlatonicObj.
 *
 *   true | false          -> Bool
 *   1324  |  -5           -> Int
 *   12.345 | 1e3          -> Real
 *   0xaf3403              -> Bytes
 *   'x' | "x" | """x"""   -> Str
 *   <a/b/c>               -> Uri (js is an fURI)
 *   [1,3,4]               -> Lst (js is an array of objs)
 *   [a=>1,b=>2]           -> Rec (js is a Map<fURI,obj>)
 *   tid::value@vid        -> the obj retagged with that tid/vid
 *
 * read() answers a PlatonicObj — never a string, never a plain object. URIs are
 * fURI, rec keys are fURI. write() serializes a PlatonicObj back to mtron.
 *
 * ES module. The objs come from ../mtron-obj.js and the addresses from ../furi.js.
 */
import { fURI, f } from './furi.js';
import { PlatonicObj, Bool, Bytes, Int, Real, Str, Uri, Lst, Rec, Rel, Code, noobj } from './mtron-obj.js';

const WS = /\s/;

/* base tids render bare (no `tid::` prefix) */
const BASE_TID = new Set(['int', 'real', 'str', 'bool', 'bytes', 'uri', 'rec', 'lst', 'rel', 'fail', 'type', 'inst', 'code', 'objs']);

function quote(text) {
    const s = String(text);
    const multiline = /[\n\r]/.test(s);
    if (!multiline && s.indexOf("'") < 0) return "'" + s.replace(/\\/g, "\\\\") + "'";
    if (!multiline && s.indexOf('"') < 0) return '"' + s.replace(/\\/g, "\\\\") + '"';
    if (s.indexOf('"""') < 0) return '"""' + s + '"""';
    return '"' + s.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n").replace(/\r/g, "\\r") + '"';
}

function terminates(c) {
    return c === undefined || WS.test(c) || c === ',' || c === ']' || c === ')' || c === '}';
}

class Reader {
    constructor(text) {
        this.text = String(text == null ? '' : text);
        this.i = 0;
    }

    fail(message) { throw new Error(message + ' at ' + this.i); }
    skip() { while (this.i < this.text.length && WS.test(this.text[this.i])) this.i += 1; }
    peek() { return this.text[this.i]; }
    startsWith(token) { return this.text.startsWith(token, this.i); }

    read() {
        const v = this.value();
        this.skip();
        if (this.i !== this.text.length) this.fail('trailing input');
        return v;
    }

    value() {
        this.skip();
        const c = this.peek();
        if (c === undefined) this.fail('value expected');
        if (c === "'" || c === '"') return this.string();
        if (c === '<') return this.uri();
        if (c === '[') {
            if (this.text.startsWith('[==', this.i)) return this.rawBlock();
            return this.bracket();
        }
        if (c === '-' || c === '+' || c === '.' || (c >= '0' && c <= '9')) return this.number();
        if (c === '!' || c === '(') return this.code();
        if (c === '/') return this.barePath();
        if (this.startsWith('true') && terminates(this.text[this.i + 4])) { this.i += 4; return new Bool(true, f('bool'), null); }
        if (this.startsWith('false') && terminates(this.text[this.i + 5])) { this.i += 5; return new Bool(false, f('bool'), null); }
        if (this.startsWith('noobj') && terminates(this.text[this.i + 5])) { this.i += 5; return noobj; }

        const start = this.i;
        const token = this.symbol();
        if (token && this.startsWith('::')) {
            this.i += 2;
            const inner = this.value();
            this.skip();
            let vid = null;
            if (this.peek() === '@') { this.i += 1; vid = this.vid(); }
            inner.tid = f(token);
            if (vid) inner.vid = f(vid);
            return inner;
        }
        if (token && this.peek() === '(') { this.i = start; return this.code(); }
        this.i = start;
        const bare = this.symbol();
        if (bare) return new Str(bare, f('str'), null);
        this.fail('value expected');
    }

    /* an opaque code value — inst(…), !(…), !*ref, (…) — skip it balanced and
       keep the raw text; the reader does not understand the code ring. */
    code() {
        const start = this.i;
        let depth = 0;
        while (this.i < this.text.length) {
            const c = this.text[this.i];
            if (c === "'" || c === '"') { this.string(); continue; }
            if (c === '<') {
                if (this.text[this.i + 1] === '=') { this.i += 2; continue; } /* the rng<=dom arrow */
                this.uri(); continue;
            }
            if (c === '[' && this.text.startsWith('[==', this.i)) { this.rawBlock(); continue; }
            if (c === '[' || c === '(' || c === '{') { depth += 1; this.i += 1; continue; }
            if (c === ')' || c === '}') { depth -= 1; this.i += 1; continue; }
            if (c === ']') {
                if (depth > 0) { depth -= 1; this.i += 1; continue; }
                break; /* top-level delimiter — the enclosing rec/lst's closer */
            }
            if (c === ',' && depth === 0) break;
            this.i += 1;
        }
        return new Code(this.text.slice(start, this.i).trim(), f('code'), null);
    }

    /* a bare path (/a/b) is a uri; a path that carries a query/code is opaque. */
    barePath() {
        const start = this.i;
        let j = this.i;
        while (j < this.text.length && !',]'.includes(this.text[j])) j += 1;
        const raw = this.text.slice(start, j);
        if (/[?({<\[]/.test(raw)) { this.i = start; return this.code(); }
        this.i = j;
        return new Uri(f(raw.trim()), f('uri'), null);
    }

    /* a [== … ==] raw/literal block */
    rawBlock() {
        const start = this.i;
        const end = this.text.indexOf('==]', this.i + 3);
        this.i = end < 0 ? this.text.length : end + 3;
        return new Code(this.text.slice(start, this.i), f('code'), null);
    }

    symbol() {
        const start = this.i;
        while (this.i < this.text.length) {
            const c = this.text[this.i];
            if (WS.test(c)) break;
            if (",[](){}<>'\"=".includes(c)) break;
            if (c === ':' && this.text[this.i + 1] === ':') break; /* only :: (the type marker) */
            this.i += 1;
        }
        return this.text.slice(start, this.i);
    }

    vid() {
        const start = this.i;
        while (this.i < this.text.length) {
            const c = this.text[this.i];
            if (WS.test(c) || c === ',' || c === ']' || c === ')' || c === '}' || c === "'" || c === '"') break;
            this.i += 1;
        }
        return this.text.slice(start, this.i);
    }

    string() {
        const quote = this.peek();
        if (quote === '"' && this.startsWith('"""')) {
            this.i += 3;
            const end = this.text.indexOf('"""', this.i);
            if (end < 0) this.fail('unterminated triple string');
            const text = this.text.slice(this.i, end);
            this.i = end + 3;
            return new Str(text, f('str'), null);
        }
        this.i += 1;
        let out = '';
        while (this.i < this.text.length) {
            const c = this.text[this.i];
            this.i += 1;
            if (c === '\\') {
                const esc = this.text[this.i];
                this.i += 1;
                if (esc === 'n') out += '\n';
                else if (esc === 't') out += '\t';
                else if (esc === 'r') out += '\r';
                else if (esc === '\\' || esc === "'" || esc === '"') out += esc;
                else out += '\\' + esc; /* \b \d \s … — regex escapes keep their backslash */
            } else if (c === quote) {
                return new Str(out, f('str'), null);
            } else if (c === '\n' || c === '\r') {
                this.fail('newline in single-line string');
            } else {
                out += c;
            }
        }
        this.fail('unterminated string');
    }

    uri() {
        this.i += 1;
        const end = this.text.indexOf('>', this.i);
        if (end < 0) this.fail('unterminated uri');
        const text = this.text.slice(this.i, end);
        this.i = end + 1;
        return new Uri(f(text), f('uri'), null);
    }

    number() {
        const match = /^[+-]?(?:0x[0-9a-fA-F]+|(?:\d+\.\d*|\.\d+|\d+)(?:[eE][+-]?\d+)?)/.exec(this.text.slice(this.i));
        if (!match) this.fail('bad number');
        const raw = match[0];
        this.i += raw.length;
        if (/^[+-]?0x/.test(raw)) {
            const hex = raw.replace(/^[+-]?0x/, '');
            if (hex.length % 2 !== 0) this.fail('odd hex byte count');
            return new Bytes(Uint8Array.from(hex.match(/../g).map((pair) => parseInt(pair, 16))), f('bytes'), null);
        }
        if (/[.eE]/.test(raw)) return new Real(Number(raw), f('real'), null);
        return new Int(parseInt(raw, 10), f('int'), null);
    }

    bracket() {
        this.i += 1;
        this.skip();
        if (this.peek() === ']') { this.i += 1; return new Lst([], f('lst'), null); }
        if (this.startsWith('=>')) {
            this.i += 2;
            this.skip();
            if (this.peek() !== ']') this.fail('] expected after =>');
            this.i += 1;
            return new Rec(new Map(), f('rec'), null);
        }
        if (this.peek() === ',' && this.text[this.i + 1] === ']') { this.i += 2; return new Lst([], f('lst'), null); }
        return this.pairAhead() ? this.record() : this.list();
    }

    pairAhead() {
        let i = this.i;
        let depth = 0;
        let quote = null;
        for (; i < this.text.length; i += 1) {
            const c = this.text[i];
            if (quote) {
                if (c === '\\') { i += 1; continue; }
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
            if (c === '<') {
                const end = this.text.indexOf('>', i + 1);
                if (end < 0) return false;
                i = end;
                continue;
            }
            if (c === '[' || c === '(' || c === '{') { depth += 1; continue; }
            if (c === ']' || c === ')' || c === '}') { if (depth === 0) return false; depth -= 1; continue; }
            if (depth !== 0) continue;
            if (c === ',') return false;
            if (c === '=' && this.text[i + 1] === '>') return true;
        }
        return false;
    }

    record() {
        const out = new Map();
        while (true) {
            this.skip();
            if (this.peek() === ']') { this.i += 1; return new Rec(out, f('rec'), null); }
            if (this.peek() === ',') this.fail('empty rec entry');
            const key = this.key();
            this.skip();
            if (!this.startsWith('=>')) this.fail('=> expected');
            this.i += 2;
            out.set(f(key), this.value());
            this.skip();
            if (this.peek() === ',') { this.i += 1; continue; }
            if (this.peek() === ']') { this.i += 1; return new Rec(out, f('rec'), null); }
            this.fail('] expected');
        }
    }

    key() {
        this.skip();
        const c = this.peek();
        if (c === "'" || c === '"') return this.string().js;
        const start = this.i;
        while (this.i < this.text.length && !(this.text[this.i] === '=' && this.text[this.i + 1] === '>')) this.i += 1;
        const key = this.text.slice(start, this.i).trim();
        if (!key) this.fail('key expected');
        return key;
    }

    list() {
        const out = [];
        while (true) {
            this.skip();
            if (this.peek() === ']') { this.i += 1; return new Lst(out, f('lst'), null); }
            if (this.peek() === ',') this.fail('empty list entry');
            out.push(this.value());
            this.skip();
            if (this.peek() === ',') { this.i += 1; continue; }
            if (this.peek() === ']') { this.i += 1; return new Lst(out, f('lst'), null); }
            this.fail('] expected');
        }
    }
}

function read(text) {
    return new Reader(text).read();
}

function value(text) {
    return read(text);
}

/* serialize a PlatonicObj back to mtron (rec values recurse with their tids) */
function write(obj) {
    if (obj == null || obj.isNoObj()) return 'noobj';
    let s;
    if (obj.isBool()) s = obj.js ? 'true' : 'false';
    else if (obj.isInt()) s = String(obj.js);
    else if (obj.isReal()) s = Number.isInteger(obj.js) ? obj.js + '.0' : String(obj.js);
    else if (obj.isStr()) s = quote(obj.js);
    else if (obj.isUri()) s = '<' + obj.js.toString() + '>';
    else if (obj.isBytes()) s = '0x' + Array.from(obj.js).map((b) => b.toString(16).padStart(2, '0')).join('');
    else if (obj.isRel()) s = write(obj.js[0]) + '=>' + write(obj.js[1]);
    else if (obj.isLst()) s = '[' + obj.js.map(write).join(',') + ']';
    else if (obj.isRec()) s = '[' + [...obj.js.entries()].map(([k, v]) => k.toString() + '=>' + write(v)).join(',') + ']';
    else if (obj.isFail()) s = 'fail::' + obj.js;
    else s = String(obj.js);

    const tn = obj.tid ? obj.tid.name() : null;
    if (obj.tid && tn && !BASE_TID.has(tn)) s = obj.tid.toString() + '::' + s;
    if (obj.vid) s += '@' + obj.vid.toString();
    return s;
}

export { read, value, write, Reader, quote };
export default { read, value, write, Reader, quote };
