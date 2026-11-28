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

/*
 * obj_mtron_serializer — the mtron string format, both directions, over PlatonicObj.
 *
 * A faithful JavaScript port of the Java ObjmtronSerializer (the C2C serializer):
 *
 *   parse(text)  -> a tree of PlatonicObj instances (mtron-obj.js), structurally
 *                   decomposed — no caller ever hand-parses a string, and parse
 *                   never evaluates mtron, dereferences an address, or computes.
 *   write(obj)   -> mtron text such that parse(write(obj)) round-trips to the
 *                   same structure.
 *
 * The spelling matches the Java writer byte-for-byte: base tids elided, a
 * {point}value{span} coefficient layout for base-typed values, `tid::` for a
 * non-base tid, `@vid` outside the uri brackets, real `.0`, `0x` bytes, the
 * rng<=dom typing pair, `{span}name::T[?pred][ctor]@vid` types, and the `*`/`!`/
 * `!*`/`!@` sugar. Every URI — uri values, rec keys, tids, vids — is an fURI,
 * built and spelled by furi.js, never hand-concatenated.
 *
 * ES module. The objs come from ./mtron-obj.js and the addresses from ./furi.js.
 */
import { fURI, cInt, f } from './furi.js';
import {
    PlatonicObj, NoObj, Bool, Bytes, Int, Real, Str, Uri, Lst, Rec, Rel,
    Fail, Type, Inst, Code, Objs, noobj, BASE_TID
} from './mtron-obj.js';

/* the sugar operations' full tids (mirror mInstSet.java) */
const FROM_INST_TID = '/m/inst/from';
const AUTO_INST_TID = '/m/inst/auto';
const AUTO_FROM_INST_TID = '/m/inst/auto_from';
const AUTO_AT_INST_TID = '/m/inst/auto_at';
const ISA_NAMES = new Set(['isa', '/m/inst/isa']);

/* the four code presentations, richest first — the name toggle walks this
   cycle: full (D) → short names (C) → no vids (B) → bare (A) → full … */
const FULL_FORM = Object.freeze({ short: false, query: true, vid: true });
const FORMS = Object.freeze([
    FULL_FORM,
    Object.freeze({ short: true, query: true, vid: true }),
    Object.freeze({ short: true, query: true, vid: false }),
    Object.freeze({ short: true, query: false, vid: false }),
]);
function nextForm(form) {
    for (let i = 0; i < FORMS.length; i++) {
        const f = FORMS[i];
        if (f.short === form.short && f.query === form.query && f.vid === form.vid)
            return FORMS[(i + 1) % FORMS.length];
    }
    return FULL_FORM;
}

const WS = /\s/;
const WORD = /[A-Za-z0-9_]/;

/* a {coefficient} body — the same shapes furi.js's cInt reads */
const COEFFICIENT_BODY = String.raw`-?\d+,-?\d+|,-?\d+|-?\d+,|,|-?\d+|\*\*|-\*|-\?|\?\?|\*|\+|\?|-`;
const COEFFICIENT_BODY_RE = new RegExp('^(?:' + COEFFICIENT_BODY + ')$');

/* ---------------------------------------------------------------------------
 * Small scalar spellers (kept from the original obj_mtron_serializer.js)
 * ------------------------------------------------------------------------- */

/* escape a string for its closing delimiter — the two characters the reader
   decodes made unambiguous: the delimiter and the escape character itself. a
   backslash is doubled only before \, ', " or the end, so a regex's \s and a
   path's C:\temp keep their spelling. */
function escaped(string, quote) {
    let out = '';
    for (let i = 0; i < string.length; i++) {
        const c = string[i];
        if (c === '\\') {
            const next = i + 1 < string.length ? string[i + 1] : '';
            out += '\\';
            if (next === '' || next === '\\' || next === "'" || next === '"') out += '\\';
        } else {
            if (c === quote) out += '\\';
            out += c;
        }
    }
    return out;
}

/* the str body: quoting carries the value, escaping is the fallback */
function writeStrBody(string) {
    const s = String(string);
    const hasSingle = s.includes("'");
    const hasDouble = s.includes('"');
    if ((s.includes('\n') || (hasSingle && hasDouble)) &&
            !s.includes('"""') && !s.endsWith('"'))
        return '"""' + s + '"""';
    const q = hasSingle ? '"' : "'";
    return q + escaped(s, q) + q;
}

/* a forced real: integral values keep their `.0` */
function real(n) {
    const v = Number(n);
    if (!Number.isFinite(v)) return String(v);
    const s = String(v);
    return /[.eE]/.test(s) ? s : s + '.0';
}

/* a forced int */
function int(n) {
    return String(Math.trunc(Number(n)));
}

/* bytes as lowercase hex */
function bytes(v) {
    return '0x' + Array.from(v).map((b) => b.toString(16).padStart(2, '0')).join('');
}

/* ---------------------------------------------------------------------------
 * URI quoting (mirror ObjmtronSerializer.requiresQuoting / wrapUri)
 * ------------------------------------------------------------------------- */

function requiresQuoting(furi) {
    if (furi.isEmpty()) return true;
    const s = furi.toString();
    const start = s[0];
    return furi.hasTemplates() || furi.hasBranches()
        || !furi.b().isOne()
        || (start >= '0' && start <= '9')
        || s.includes(' ')
        || start === 'T' || start === '+' || start === '#'
        || s.includes('@')
        || s.includes('.');
}

function wrapUri(furi) {
    if (furi.isEmpty()) return '<' + furi.toString() + '>';
    return requiresQuoting(furi) ? ('<' + furi.toString() + '>') : furi.toString();
}

/* ---------------------------------------------------------------------------
 * TID / VID rendering
 * ------------------------------------------------------------------------- */

function isBaseName(tid) {
    const n = tid.name();
    return n != null && n !== '' && BASE_TID.has(n);
}

function handleTID(obj, hideBaseTID) {
    let s = '';
    const tid = obj.tid;
    if (!obj.isFail() && hideBaseTID && tid != null && !tid.hasPoly() && isBaseName(tid)) {
        /* a base tid is elided; its point leads the value */
        if (!tid.d().isOne()) s += '{' + tid.d().toString() + '}';
        return s;
    }
    if (tid != null) s += tid.toString();
    if (!obj.isInst()) s += '::';
    return s;
}

function handleVID(obj, form) {
    if ((form && !form.vid) || obj.vid == null) return '';
    return '@' + wrapUri(obj.vid);
}

function handleIds(obj, objString) {
    let s = '';
    if (obj.isInst()) {
        s += generateInstHead(obj);
        s += objString;
    } else {
        s += handleTID(obj, true);
        s += objString;
        /* a base-typed value trails its span */
        if (obj.tid != null && !obj.tid.b().isOne() && !obj.tid.hasPoly() && isBaseName(obj.tid))
            s += '{' + obj.tid.b().toString() + '}';
    }
    s += handleVID(obj);
    return s;
}

/* ---------------------------------------------------------------------------
 * Writers
 * ------------------------------------------------------------------------- */

function writeBool(obj) { return handleIds(obj, obj.js ? 'true' : 'false'); }
function writeInt(obj) { return handleIds(obj, String(obj.js)); }
function writeReal(obj) { return handleIds(obj, real(obj.js)); }
function writeBytes(obj) { return handleIds(obj, bytes(obj.js)); }
function writeStr(obj) { return handleIds(obj, writeStrBody(String(obj.js))); }
function writeUri(obj) { return handleIds(obj, wrapUri(obj.js)); }

function writeFail(obj) {
    let body = '';
    let cur = obj;
    while (cur != null) {
        body += '[' + writeStrBody(cur.js == null ? '' : String(cur.js)) + ']';
        cur = cur.cause;
    }
    return handleIds(obj, body);
}

function writeLst(obj, form) {
    let s = handleTID(obj, true);
    if (obj.js.length === 0) s += '[,]';
    else s += '[' + obj.js.map((v) => write(v, form)).join(',') + ']';
    return s + handleVID(obj);
}

function writeRec(obj, form) {
    let s = handleTID(obj, true);
    const entries = [...obj.js.entries()];
    if (entries.length === 0) s += '[=>]';
    else s += '[' + entries.map(([k, v]) => wrapUri(k) + '=>' + write(v, form)).join(',') + ']';
    return s + handleVID(obj);
}

function writeRel(obj, form) {
    const relWrap = !(obj.tid != null && obj.tid.toString() === 'rel');
    const first = obj.js[0];
    const second = obj.js[1];
    const firstRel = first.isRel();
    const secondRel = second.isRel();
    let body = '';
    if (relWrap) body += '(';
    if (firstRel) body += '(';
    body += write(first, form);
    if (firstRel) body += ')';
    body += '=>';
    if (secondRel) body += '(';
    body += write(second, form);
    if (secondRel) body += ')';
    if (relWrap) body += ')';
    return handleIds(obj, body);
}

function writeObjs(obj, form) {
    if (obj.js.length === 0) return 'noobj';
    return '{' + obj.js.map((v) => write(v, form)).join(',') + '}';
}

function writeCode(obj, form) {
    form = form || FULL_FORM;
    if (!form.short && form.query && form.vid) return String(obj.js);
    return obj.insts().map((i) => writeInst(i, form)).join('.');
}

/* the instruction head: {span}name?rng<=dom&params */
function defaultRng(tid) { return !tid.hasRng() || tid.rng().equals(fURI.ALL.maybeSome()); }
function defaultDom(tid) { return !tid.hasDom() || tid.dom().equals(fURI.ALL.maybe()); }

function generateInstHead(inst, form) {
    form = form || FULL_FORM;
    let s = '';
    const tid = inst.tid;
    if (!tid.b().isOne()) s += '{' + tid.b().toString() + '}';
    s += form.short ? tid.name() : tid.basePath().toString();
    if (!form.query) return s;
    let params = '';
    const rng = defaultRng(tid) ? '' : (tid.rng().isZero() ? '{0}' : (form.short ? tid.rng().name() : tid.rng().toString()));
    const dom = defaultDom(tid) ? '' : (tid.dom().isZero() ? '{0}' : (form.short ? tid.dom().name() : tid.dom().toString()));
    if (rng !== '' || dom !== '') params += rng + '<=' + dom;
    for (const [k, v] of tid.qMap()) {
        if (k === 'rng' || k === 'dom') continue;
        if (k === 'args' && !inst.args().isNoObj()) continue;
        if (params !== '') params += '&';
        params += k;
        if (v != null && v !== '') params += '=' + v;
    }
    if (params !== '') s += '?' + params;
    return s;
}

function renderInstArg(arg) {
    if (arg.isRec()) return writeRec(arg);
    if (arg.isLst()) return writeLst(arg);
    return write(arg);
}

function writeInst(inst, form) {
    form = form || FULL_FORM;
    const bp = inst.name() ? inst.name().toString() : '';
    if (bp === FROM_INST_TID) return '*' + renderInstArg(inst.arg(0));
    if (bp === AUTO_FROM_INST_TID) return '!*' + renderInstArg(inst.arg(0));
    if (bp === AUTO_AT_INST_TID && inst.arg(1).isNoObj()) return '!@' + renderInstArg(inst.arg(0));
    if (bp === AUTO_INST_TID) return '!' + renderInstArg(inst.arg(0));

    let body = '(';
    const a = inst.args();
    if (a != null && a.isLst()) body += a.js.map((v) => write(v, form)).join(',');
    else if (a != null && a.isRec())
        body += [...a.js.entries()].map(([k, v]) => wrapUri(k) + '=>' + write(v, form)).join(',');
    body += ')';
    if (inst.f() != null) body += '{' + write(inst.f(), form) + '}';
    return generateInstHead(inst, form) + body + handleVID(inst, form);
}

function writeType(type) {
    const name = type.tid;
    let s = '';
    if (!name.b().isOne()) s += '{' + name.b().toString() + '}';
    s += wrapUri(name.b(cInt.ONE())) + '::T';
    if (type.hasPredicate()) {
        if (type.isIsaPredicate()) {
            s += '[?' + renderInstArg(type.isPredicateObj()) + ']';
        } else {
            s += '[' + write(type.predicate()) + ']';
        }
    }
    if (type.hasConstructor()) {
        if (!type.hasPredicate()) s += '[]';
        s += '[' + renderInstArg(type.ctor()) + ']';
    }
    if (type.hasVID()) {
        if (type.vid.basePath().toString() === 'type' && !type.vid.c().isOne())
            s += '{' + type.vid.c().toString() + '}';
        else if (type.tid.basePath().toString() !== type.vid.basePath().toString())
            s += handleVID(type);
    }
    return s;
}

function write(obj, form) {
    form = form || FULL_FORM;
    if (obj == null || obj.isNoObj()) return 'noobj';
    if (obj.isInst()) return writeInst(obj, form);
    if (obj.isType()) return writeType(obj);
    if (obj.isCode()) return writeCode(obj, form);
    if (obj.isObjs()) return writeObjs(obj, form);
    if (obj.isRec()) return writeRec(obj, form);
    if (obj.isLst()) return writeLst(obj, form);
    if (obj.isRel()) return writeRel(obj, form);
    if (obj.isUri()) return writeUri(obj);
    if (obj.isFail()) return writeFail(obj);
    if (obj.isStr()) return writeStr(obj);
    if (obj.isBytes()) return writeBytes(obj);
    if (obj.isBool()) return writeBool(obj);
    if (obj.isInt()) return writeInt(obj);
    if (obj.isReal()) return writeReal(obj);
    return String(obj.js);
}

/* ---------------------------------------------------------------------------
 * Reader
 * ------------------------------------------------------------------------- */

class Reader {
    constructor(text) {
        this.text = String(text == null ? '' : text);
        this.i = 0;
    }

    fail(message) { throw new Error(message + ' at ' + this.i); }
    skip() { while (this.i < this.text.length && WS.test(this.text[this.i])) this.i += 1; }
    peek() { return this.text[this.i]; }
    peekAt(k) { return this.text[this.i + k]; }
    startsWith(token) { return this.text.startsWith(token, this.i); }
    eof() { return this.i >= this.text.length; }

    read() {
        const v = this.value();
        this.skip();
        if (!this.eof()) this.fail('trailing input');
        return v;
    }

    /* the first top-level `::` (the type/value separator), or -1 */
    findColonColon() {
        let depth = 0;
        for (let i = this.i; i < this.text.length; i++) {
            const c = this.text[i];
            if (c === "'" || c === '"') { i = this.skipString(i) - 1; continue; }
            if (c === '<' && this.text[i + 1] !== '=') { const e = this.text.indexOf('>', i + 1); i = e < 0 ? i : e; continue; }
            if (c === '[' || c === '(' || c === '{') { depth += 1; continue; }
            if (c === ']' || c === ')' || c === '}') {
                if (depth === 0) return -1;   /* closed the value's own bracket — no top-level :: here */
                depth -= 1;
                continue;
            }
            if (depth === 0 && c === ',') return -1;   /* element separator — stop, don't scan the next element */
            if (depth === 0 && c === ':' && this.text[i + 1] === ':') return i;
        }
        return -1;
    }

    skipString(i) {
        const q = this.text[i];
        if (q === '"' && this.text.startsWith('"""', i)) {
            const e = this.text.indexOf('"""', i + 3);
            return e < 0 ? this.text.length : e + 3;
        }
        i += 1;
        while (i < this.text.length) {
            if (this.text[i] === '\\') { i += 2; continue; }
            if (this.text[i] === q) return i + 1;
            i += 1;
        }
        return i;
    }

    /* a full value: [tid ::] core [@vid], with base-typed point/span, or a type,
       or a rel chain */
    value() {
        this.skip();
        const cc = this.findColonColon();
        if (cc >= 0) {
            const tidText = this.text.slice(this.i, cc).trim();
            this.i = cc + 2;
            this.skip();
            const tid = f(tidText);
            if (this.peek() === 'T' && !WORD.test(this.peekAt(1) || '')) {
                return this.type(tid);
            }
            if (tid.name() === 'fail') {
                return this.fail(tid);
            }
            let obj = this.core();
            obj.tid = tid;
            this.skip();
            if (this.peek() === '@') { this.i += 1; obj.vid = f(this.vid()); }
            return obj;
        }

        /* a leading {point} before a base-typed value (not an objs) */
        let point = null;
        if (this.peek() === '{') {
            const save = this.i;
            const c = this.coefficient();
            if (c != null) {
                this.skip();
                const n = this.peek();
                if (n === undefined || n === ',' || n === '}' || n === '=') this.i = save;
                else point = c;
            } else {
                this.i = save;
            }
        }

        let obj = this.core();
        if (point != null) {
            /* for an instruction the leading {n} is its span; for a value it is the point */
            if (obj.isInst()) obj.tid = obj.tid.b(point);
            else obj.tid = obj.tid.d(point);
        }

        /* a rel: lhs => rhs */
        this.skip();
        if (this.peek() === '=' && this.peekAt(1) === '>') {
            this.i += 2;
            const rhs = this.value();
            return new Rel([obj, rhs], f('rel'), null);
        }

        /* a trailing {span} */
        this.skip();
        if (this.peek() === '{') {
            const c = this.coefficient();
            if (c != null) obj.tid = obj.tid.b(c);
        }

        this.skip();
        if (this.peek() === '@') { this.i += 1; obj.vid = f(this.vid()); }
        return obj;
    }

    /* the bare value (no tid/vid wrapper) */
    core() {
        this.skip();
        const c = this.peek();
        if (c === undefined) this.fail('value expected');
        if (c === "'" || c === '"') return this.string();
        if (c === '<') return new Uri(this.bracketedUri(), f('uri'), null);
        if (c === '[') return this.bracket();
        if (c === '{') return this.objs();
        if (c === '(') return this.parenthesized();
        if (c === '-' || c === '+' || c === '.' || (c >= '0' && c <= '9')) return this.number();
        if (c === '*' || c === '!') return this.sugar();
        if (this.startsWith('true') && !WORD.test(this.peekAt(4) || '')) { this.i += 4; return new Bool(true, f('bool'), null); }
        if (this.startsWith('false') && !WORD.test(this.peekAt(5) || '')) { this.i += 5; return new Bool(false, f('bool'), null); }
        if (this.startsWith('noobj') && !WORD.test(this.peekAt(5) || '')) { this.i += 5; return noobj; }

        /* a bare symbol/path: an inst, a code chain, or a uri (which may carry a
           query, a poly, or a {point} — all read back by f()). A QUERY ARG CARRIES
           QUOTED TEXT AND POLYS (`?reject=['rm','git']`), so the token scan steps over
           strings and balances brackets — to stop at the `,` or `]` INSIDE an arg is
           to end the token mid-uri and close the caller's rec one bracket early. `(`
           still ends the token: that is how an inst call is detected. */
        const start = this.i;
        let j = this.i;
        let brace = 0;
        let square = 0;
        while (j < this.text.length) {
            const ch = this.text[j];
            if (ch === "'" || ch === '"') {
                if (ch === '"' && this.text.startsWith('"""', j)) {
                    const end = this.text.indexOf('"""', j + 3);
                    j = end < 0 ? j + 3 : end + 3;
                    continue;
                }
                j += 1;
                while (j < this.text.length) {
                    const q = this.text[j];
                    j += 1;
                    if (q === '\\') { j += 1; continue; }
                    if (q === ch) break;
                }
                continue;
            }
            if (ch === '[') { square += 1; j += 1; continue; }
            if (ch === ']') { if (square === 0) break; square -= 1; j += 1; continue; }
            if (ch === '{') { brace += 1; j += 1; continue; }
            if (ch === '}') { if (brace === 0) break; brace -= 1; j += 1; continue; }
            if (brace === 0 && square === 0 &&
                    (WS.test(ch) || ch === '(' || ch === '.' || ch === ',' || ch === ')' ||
                    (ch === '=' && this.text[j + 1] === '>'))) break;
            if (ch === '<' && this.text[j + 1] !== '=') { const e = this.text.indexOf('>', j + 1); j = e < 0 ? j + 1 : e + 1; continue; }
            j += 1;
        }
        const token = this.text.slice(start, j).trim();
        this.i = j;
        if (this.peek() === '(' || this.peek() === '.') {
            this.i = start;
            return this.instOrCode();
        }
        if (!token) this.fail('value expected');
        return new Uri(f(token), f('uri'), null);
    }

    /* the {n} coefficient before a value (or a type name) */
    coefficient() {
        if (this.peek() !== '{') return null;
        const end = this.text.indexOf('}', this.i + 1);
        if (end < 0) return null;
        const body = this.text.slice(this.i + 1, end);
        if (!COEFFICIENT_BODY_RE.test(body)) return null;
        let c;
        try { c = cInt.of(body); } catch (e) { return null; }
        this.i = end + 1;
        return c;
    }

    /* a uri value: <...> or a bare path token */
    bracketedUri() {
        this.i += 1;
        const end = this.text.indexOf('>', this.i);
        if (end < 0) this.fail('unterminated uri');
        const text = this.text.slice(this.i, end);
        this.i = end + 1;
        return f(text);
    }

    /* a vid token after `@`: a bracketed uri or a bare token */
    vid() {
        this.skip();
        if (this.peek() === '<') return this.bracketedUri().toString();
        const start = this.i;
        while (this.i < this.text.length && !WS.test(this.text[this.i]) && !',]})\'\"'.includes(this.text[this.i]))
            this.i += 1;
        return this.text.slice(start, this.i);
    }

    number() {
        const m = /^[+-]?(?:0x[0-9a-fA-F]+|(?:\d+\.\d*|\.\d+|\d+)(?:[eE][+-]?\d+)?)/.exec(this.text.slice(this.i));
        if (!m) this.fail('bad number');
        const raw = m[0];
        this.i += raw.length;
        if (/^[+-]?0x/.test(raw)) {
            const hex = raw.replace(/^[+-]?0x/, '');
            if (hex.length % 2 !== 0) this.fail('odd hex byte count');
            return new Bytes(Uint8Array.from(hex.match(/../g).map((p) => parseInt(p, 16))), f('bytes'), null);
        }
        if (/[.eE]/.test(raw)) return new Real(Number(raw), f('real'), null);
        return new Int(parseInt(raw, 10), f('int'), null);
    }

    string() {
        const q = this.peek();
        if (q === '"' && this.startsWith('"""')) {
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
                else out += '\\' + esc;
            } else if (c === q) {
                return new Str(out, f('str'), null);
            } else if (c === '\n' || c === '\r') {
                this.fail('newline in single-line string');
            } else {
                out += c;
            }
        }
        this.fail('unterminated string');
    }

    /* [ … ] — a lst or a rec */
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
        for (; i < this.text.length; i++) {
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
            if (c === '<' && this.text[i + 1] !== '=') { const end = this.text.indexOf('>', i + 1); if (end < 0) return false; i = end; continue; }
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
        const k = this.text.slice(start, this.i).trim();
        if (!k) this.fail('key expected');
        return k;
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

    /* { a, b, c } — an objs (or the empty {,}) */
    objs() {
        this.i += 1;
        this.skip();
        if (this.peek() === ',' && this.text[this.i + 1] === '}') { this.i += 2; return noobj; }
        const out = [];
        while (true) {
            this.skip();
            if (this.peek() === '}') { this.i += 1; break; }
            out.push(this.value());
            this.skip();
            if (this.peek() === ',') { this.i += 1; continue; }
            if (this.peek() === '}') { this.i += 1; break; }
            this.fail('} expected');
        }
        return out.length === 0 ? noobj : (out.length === 1 ? out[0] : new Objs(out, f('objs'), null));
    }

    parenthesized() {
        this.i += 1;
        const inner = this.value();
        this.skip();
        if (this.peek() !== ')') this.fail(') expected');
        this.i += 1;
        return inner;
    }

    /* a type: the tid was already read before `::T` */
    type(tid) {
        this.i += 1;   /* the 'T' */
        this.skip();
        let predicate = null;
        let ctor = null;
        if (this.peek() === '[') {
            if (this.text[this.i + 1] === '?' && this.text[this.i + 2] === '[') {
                /* an isa predicate: [?rec] */
                this.i += 2;
                this.skip();
                const rec = this.value();
                predicate = new Inst(new Lst([rec], f('lst'), null), f('isa'), null);
                this.skip();
                if (this.peek() !== ']') this.fail('] expected after predicate');
                this.i += 1;
            } else {
                this.i += 1;
                this.skip();
                if (this.peek() === ']') {
                    /* the [] separator before a constructor */
                    this.i += 1;
                } else {
                    const content = this.predicateCode();
                    predicate = new Code(content, f('code'), null);
                }
            }
        }
        this.skip();
        if (this.peek() === '[') {
            this.i += 1;
            const content = this.predicateCode();
            ctor = new Code(content, f('code'), null);
        }
        this.skip();
        let vid = null;
        if (this.peek() === '@') {
            this.i += 1;
            vid = f(this.vid());
        } else if (this.peek() === '{') {
            const c = this.coefficient();
            if (c != null && !c.isOne()) vid = f('type').c(c);
        }
        return new Type([predicate, ctor], tid, vid);
    }

    /* the raw text of a predicate/constructor bracket, up to its closing `]` */
    predicateCode() {
        const start = this.i;
        let depth = 0;
        let quote = null;
        while (this.i < this.text.length) {
            const c = this.text[this.i];
            if (quote) {
                if (c === '\\') { this.i += 2; continue; }
                if (c === quote) quote = null;
                this.i += 1;
                continue;
            }
            if (c === "'" || c === '"') { quote = c; this.i += 1; continue; }
            if (c === '<' && this.text[this.i + 1] !== '=') { const e = this.text.indexOf('>', this.i + 1); this.i = e < 0 ? this.i + 1 : e + 1; continue; }
            if (c === '[' || c === '(' || c === '{') { depth += 1; this.i += 1; continue; }
            if (c === ']') {
                if (depth === 0) break;
                depth -= 1;
                this.i += 1;
                continue;
            }
            if (c === ')' || c === '}') { depth = Math.max(0, depth - 1); this.i += 1; continue; }
            this.i += 1;
        }
        const text = this.text.slice(start, this.i).trim();
        if (this.peek() === ']') this.i += 1;
        return text;
    }

    /* fail::[str][str]… — the message brackets */
    fail(tid) {
        let head = null;
        let tail = null;
        while (this.peek() === '[') {
            this.i += 1;
            this.skip();
            let msg = '';
            if (this.peek() === "'" || this.peek() === '"') msg = this.string().js;
            else {
                const s = this.predicateCode();
                msg = s;
            }
            this.skip();
            if (this.peek() === ']') this.i += 1;
            if (head == null) head = new Fail(msg, tid, null);
            else {
                const next = new Fail(msg, tid, null);
                if (tail == null) { head.cause = next; tail = next; }
                else { tail.cause = next; tail = next; }
            }
        }
        this.skip();
        if (this.peek() === '@') { this.i += 1; if (head != null) head.vid = f(this.vid()); }
        return head == null ? new Fail('', tid, null) : head;
    }

    /* an inst or a code chain. a resolved chain carries a vid on each inst
       (inst@vid.inst@vid …), so the chain's `.` sits after a `@vid` — look past
       one before deciding; a lone `inst@vid` stays an inst, its vid attached by
       value(). */
    instOrCode() {
        const start = this.i;
        const inst = this.inst();
        this.skip();
        if (this.peek() === '.') {
            this.i = start;
            return new Code(this.codeText(), f('code'), null);
        }
        if (this.peek() === '@') {
            const save = this.i;
            this.i += 1;
            this.vid();
            this.skip();
            if (this.peek() === '.') {
                this.i = start;
                return new Code(this.codeText(), f('code'), null);
            }
            this.i = save;
        }
        return inst;
    }

    /* a single instruction: sugar, or name(args){body} */
    inst() {
        this.skip();
        const c = this.peek();
        if (c === '!') {
            this.i += 1;
            if (this.peek() === '*') { this.i += 1; return this.sugarInst(AUTO_FROM_INST_TID); }
            if (this.peek() === '@') { this.i += 1; return this.sugarInst(AUTO_AT_INST_TID); }
            return this.sugarInst(AUTO_INST_TID);
        }
        if (c === '*') {
            this.i += 1;
            return this.sugarInst(FROM_INST_TID);
        }
        const tid = this.instHead();
        let args = new Lst([], f('lst'), null);
        if (this.peek() === '(') args = this.args();
        let fbody = null;
        if (this.peek() === '{') fbody = this.body();
        return new Inst(args, tid, null, fbody);
    }

    sugar() {
        this.skip();
        const c = this.peek();
        if (c === '!') {
            this.i += 1;
            if (this.peek() === '*') { this.i += 1; return this.sugarInst(AUTO_FROM_INST_TID); }
            if (this.peek() === '@') { this.i += 1; return this.sugarInst(AUTO_AT_INST_TID); }
            return this.sugarInst(AUTO_INST_TID);
        }
        if (c === '*') { this.i += 1; return this.sugarInst(FROM_INST_TID); }
        this.fail('sugar expected');
    }

    sugarInst(tid) {
        this.skip();
        const arg = this.core();
        return new Inst(new Lst([arg], f('lst'), null), f(tid), null, null);
    }

    /* the head of an inst: {span}name{point}?query, as an fURI */
    instHead() {
        const start = this.i;
        let brace = 0;
        while (this.i < this.text.length) {
            const c = this.text[this.i];
            if (c === '{') { brace += 1; this.i += 1; continue; }
            if (c === '}') { brace -= 1; this.i += 1; continue; }
            if (brace === 0 && (WS.test(c) || c === '(' || c === '.' || c === ',' || c === ']' || c === ')')) break;
            if (c === '<' && this.text[this.i + 1] !== '=') { const e = this.text.indexOf('>', this.i + 1); this.i = e < 0 ? this.i + 1 : e + 1; continue; }
            this.i += 1;
        }
        return f(this.text.slice(start, this.i).trim());
    }

    args() {
        this.i += 1;
        this.skip();
        const out = [];
        if (this.peek() === ')') { this.i += 1; return new Lst(out, f('lst'), null); }
        while (true) {
            out.push(this.value());
            this.skip();
            if (this.peek() === ',') { this.i += 1; continue; }
            if (this.peek() === ')') { this.i += 1; break; }
            this.fail(') expected');
        }
        return new Lst(out, f('lst'), null);
    }

    /* a { … } inst body — kept as code text */
    body() {
        this.i += 1;
        const start = this.i;
        let depth = 1;
        while (this.i < this.text.length && depth > 0) {
            const c = this.text[this.i];
            if (c === "'" || c === '"') { this.i = this.skipString(this.i); continue; }
            if (c === '<' && this.text[this.i + 1] !== '=') { const e = this.text.indexOf('>', this.i + 1); this.i = e < 0 ? this.i + 1 : e + 1; continue; }
            if (c === '{') depth += 1;
            else if (c === '}') depth -= 1;
            this.i += 1;
        }
        const content = this.text.slice(start, this.i - 1).trim();
        return content ? new Code(content, f('code'), null) : null;
    }

    /* raw text of a code chain, up to a top-level delimiter */
    codeText() {
        const start = this.i;
        let depth = 0;
        let quote = null;
        while (this.i < this.text.length) {
            const c = this.text[this.i];
            if (quote) {
                if (c === '\\') { this.i += 2; continue; }
                if (c === quote) quote = null;
                this.i += 1;
                continue;
            }
            if (c === "'" || c === '"') { quote = c; this.i += 1; continue; }
            if (c === '<' && this.text[this.i + 1] !== '=') { const e = this.text.indexOf('>', this.i + 1); this.i = e < 0 ? this.i + 1 : e + 1; continue; }
            if (c === '[' || c === '(' || c === '{') { depth += 1; this.i += 1; continue; }
            if (c === ']' || c === ')' || c === '}') {
                if (depth === 0) break;
                depth -= 1;
                this.i += 1;
                continue;
            }
            if (c === ',' && depth === 0) break;
            this.i += 1;
        }
        return this.text.slice(start, this.i).trim();
    }

    /* a code chain into its insts, each with its tid (name + dom/rng), args,
       body and vid — the JS mirror of Java's Code.insts() */
    insts() {
        const out = [];
        while (true) {
            this.skip();
            const inst = this.inst();
            this.skip();
            if (this.peek() === '@') { this.i += 1; inst.vid = f(this.vid()); }
            out.push(inst);
            this.skip();
            if (this.peek() === '.') { this.i += 1; continue; }
            break;
        }
        return out;
    }
}

function parse(text) {
    return new Reader(text).read();
}

/* a code chain into its insts — the parser Code.insts() leans on */
function parseInsts(text) {
    const s = String(text == null ? '' : text).trim();
    if (!s) return [];
    return new Reader(s).insts();
}

/* re-render code text (a bare chain, or a [chain] list, or a lone inst) in one
   of the FORMS — the name toggle's primitive */
function writeCodeForm(text, form) {
    return write(parse(String(text == null ? '' : text).trim()), form);
}

export { parse, write, writeStrBody as quote, real, int, bytes, escaped, parseInsts, writeCodeForm, FORMS, FULL_FORM, nextForm };
export default { parse, write, real, int, bytes };
