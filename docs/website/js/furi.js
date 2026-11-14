/*
 * furi.js — a mini-practical JavaScript port of studio.phaseshift.metatron.furi.fURI
 *
 * fURI is metatron's address/URI type: one immutable value that spells a scheme, an
 * authority (host:port), a path, a [poly] bracket, a {coefficient}, and a ?query —
 * with a canonical string form so that equal uris render identically.
 *
 * Ported faithfully from the Java contract (fURI.java + AbstractfURI.java + cInt.java).
 * Deliberately ELIDED (documented, not hidden): the full branch/sum stream-ring algebra
 * (Helper.sum / pathSum / plus), ${…} template *evaluation* (templates are parsed and
 * rendered, never run), the deep poly bracket-matching rule, coefficient variable names
 * beyond parsing, and the intern pool / memoization caches. Everything a caller uses to
 * BUILD and MATCH addresses is here.
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 */

/* ---------------------------------------------------------------------------
 * Small utilities
 * ------------------------------------------------------------------------- */

const hashString = (s) => {
    let h = 5381;
    for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0;
    return h;
};
const hashArray = (arr) => arr.reduce((h, x) => 31 * h + hashString(x), 1) | 0;
const arraysEqual = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);
const mapsEqual = (a, b) => {
    if (a.size !== b.size) return false;
    for (const [k, v] of a) if (b.get(k) !== v) return false;
    return true;
};
const isAlpha = (c) => /[A-Za-z]/.test(c);
const isUpper = (c) => /[A-Z]/.test(c);
const isLower = (c) => /[a-z]/.test(c);

/* A coefficient body is exactly one cInt: a signed exact or range, or a shorthand.
 * A `{...}` whose body matches this is the trailing coefficient, not a path branch. */
const COEFFICIENT_BODY =
    String.raw`-?\d+,-?\d+|,-?\d+|-?\d+,|,|-?\d+|\*\*|-\*|-\?|\?\?|\*|\+|\?|-`;
const COEFFICIENT_BODY_RE = new RegExp('^(?:' + COEFFICIENT_BODY + ')$');

const LOOPBACK_HOSTS = new Set([
    '0.0.0.0', '127.0.0.1', 'localhost', '::1', '[::1]',
    '0:0:0:0:0:0:0:1', '[0:0:0:0:0:0:0:1]',
]);

const HOST_PATTERN = /((?:[^.]+\\.)+)([^.]+\.[^.]+)(?::(\d+))?/;
const TEMPLATE_RE = /\$\{([^}]+)\}/g;

/* ---------------------------------------------------------------------------
 * Query ordering — the canonical spelling of a uri's query.
 * rng, dom, args lead (the instruction-signature order), then every other key in
 * REVERSE alphabetical order. This is what makes `parse(toString()).toString()`
 * an identity: one fURI has exactly one spelling.
 * ------------------------------------------------------------------------- */
const Q_LEAD = ['rng', 'dom', 'args'];

function compareQKeys(lhs, rhs) {
    const lr = Q_LEAD.indexOf(lhs);
    const rr = Q_LEAD.indexOf(rhs);
    if (lr >= 0 || rr >= 0) {
        if (lr < 0) return 1;         // a lead key precedes everything else
        if (rr < 0) return -1;
        return lr - rr;
    }
    // descending natural string order
    return rhs < lhs ? -1 : rhs > lhs ? 1 : 0;
}

function canonicalQ(query) {
    if (query == null) return new Map();
    const entries = query instanceof Map ? [...query.entries()] : Object.entries(query);
    if (entries.length === 0) return new Map();
    entries.sort((a, b) => compareQKeys(a[0], b[0]));
    return new Map(entries);
}

/* Percent-decode a query component: only %XX is special (a space is %20, + is a
 * literal plus — metatron never percent-encodes its own components on render). */
function decodeQueryComponent(value) {
    if (value == null || value.indexOf('%') < 0) return value;
    const bytes = [];
    let out = '';
    for (let i = 0; i < value.length; i++) {
        const c = value[i];
        const hex = c === '%' && i + 2 < value.length ? value.slice(i + 1, i + 3) : null;
        if (hex && /^[0-9A-Fa-f]{2}$/.test(hex)) {
            bytes.push(parseInt(hex, 16));
            i += 2;
        } else {
            if (bytes.length) {
                out += decodeUtf8(bytes);
                bytes.length = 0;
            }
            out += c;
        }
    }
    if (bytes.length) out += decodeUtf8(bytes);
    return out;
}

function decodeUtf8(bytes) {
    const buf = new Uint8Array(bytes);
    return new TextDecoder('utf-8').decode(buf);
}

/* ---------------------------------------------------------------------------
 * cInt — the {coefficient}: an integer interval [min, max], unbounded at either
 * end, with the shorthand spellings *, +, ?, ??, **, -, -?, -*, and ranges.
 * ------------------------------------------------------------------------- */
class cInt {
    constructor(min, max, minName = null, maxName = null) {
        if (min != null && max != null && min > max)
            throw new Error('c min is greater than c max: ' + min + ' > ' + max);
        this.min = min;
        this.max = max;
        this.minName = minName;
        this.maxName = maxName;
    }

    static ZERO() {
        return cInt.ZERO_;
    }

    static ONE() {
        return cInt.ONE_;
    }

    static SOME() {
        return cInt.SOME_;
    }

    static MAYBE() {
        return cInt.MAYBE_;
    }

    static MAYBESOME() {
        return cInt.MAYBESOME_;
    }

    static ANY() {
        return cInt.ANY_;
    }

    static ANTIONE() {
        return cInt.of(-1, -1);
    }

    static of(min, max) {
        if (typeof min === 'string') return cInt.parse(min);
        if (min instanceof cInt) return min;
        if (arguments.length >= 2) return new cInt(min, max);
        return new cInt(min, min);
    }

    static parse(parse) {
        if (parse == null || parse === '') return cInt.ONE_;
        const comma = parse.indexOf(',');
        const low = comma === -1 ? parse : parse.slice(0, comma);
        const high = comma === -1 ? parse : parse.slice(comma + 1);
        const isName = (b) => b !== '' && /[A-Za-z]/.test(b[0]) && [...b].every((c) => /[A-Za-z0-9_]/.test(c));
        if (isName(low) || isName(high)) {
            return new cInt(
                isName(low) || low === '' ? null : Number(low),
                isName(high) || high === '' ? null : Number(high),
                isName(low) ? low : null,
                isName(high) ? high : null);
        }
        if (parse === '*') return cInt.of(0, null);        // maybeSome
        if (parse === ',' || parse === '**') return cInt.of(null, null); // any
        if (parse === '?') return cInt.of(0, 1);          // maybe
        if (parse === '??') return cInt.of(-1, 1);        // anyMaybe
        if (parse === '+') return cInt.of(1, null);       // some
        if (parse === '-?') return cInt.of(-1, 0);        // antiMaybe
        if (parse === '-') return cInt.of(null, -1);      // antiSome
        if (parse === '-*') return cInt.of(null, 0);      // antiMaybeSome
        if (!parse.includes(',')) return cInt.of(Number(parse));
        const split = parse.split(',');
        if (parse[0] === ',')
            return parse.length === 1 ? cInt.of(null, null) : cInt.of(null, Number(split[1]));
        if (split.length === 1) return cInt.of(Number(split[0]), null);
        return cInt.of(Number(split[0]), Number(split[1]));
    }

    isZero() {
        return this.max === 0 && this.min === 0;
    }

    isOne() {
        return this.min === 1 && this.max === 1;
    }

    isAny() {
        return this.min == null && this.max == null;
    }

    isMaybe() {
        return this.min === 0 && this.max === 1;
    }

    isSome() {
        return this.min === 1 && this.max == null;
    }

    isMaybeSome() {
        return this.min === 0 && this.max == null;
    }

    isExact() {
        return this.min != null && this.min === this.max;
    }

    isAnyMaybe() {
        return this.min === -1 && this.max === 1;
    }

    isAntiMaybe() {
        return this.min === -1 && this.max === 0;
    }

    isAntiSome() {
        return this.min == null && this.max === -1;
    }

    isAntiMaybeSome() {
        return this.min == null && this.max === 0;
    }

    isNeg() {
        return (this.min == null || this.min < 0) && this.max != null && this.max < 0;
    }

    isZeroable() {
        return (this.min == null || this.min <= 0) && (this.max == null || this.max >= 0);
    }

    isVariable() {
        return this.minName != null || this.maxName != null;
    }

    plus(rhs) {
        return new cInt(
            this.min == null || rhs.min == null ? null : this.min + rhs.min,
            this.max == null || rhs.max == null ? null : this.max + rhs.max);
    }

    neg() {
        return new cInt(
            this.max == null ? null : -this.max,
            this.min == null ? null : -this.min);
    }

    mult(rhs) {
        if (this.isOne()) return rhs;
        if (rhs.isOne()) return this;
        const product = (x, y) => {
            if (x === 0 || y === 0) return 0;
            if (x === -Infinity || x === Infinity || y === -Infinity || y === Infinity)
                return (x < 0) === (y < 0) ? Infinity : -Infinity;
            return x * y;
        };
        const a = this.min == null ? -Infinity : this.min;
        const b = this.max == null ? Infinity : this.max;
        const c = rhs.min == null ? -Infinity : rhs.min;
        const d = rhs.max == null ? Infinity : rhs.max;
        const low = Math.min(product(a, c), product(a, d), product(b, c), product(b, d));
        const high = Math.max(product(a, c), product(a, d), product(b, c), product(b, d));
        return new cInt(low === -Infinity ? null : low, high === Infinity ? null : high);
    }

    within(rhs) {
        const thisMin = this.min == null ? -Infinity : this.min;
        const thisMax = this.max == null ? Infinity : this.max;
        const rhsMin = rhs.min == null ? -Infinity : rhs.min;
        const rhsMax = rhs.max == null ? Infinity : rhs.max;
        return thisMin >= rhsMin && thisMax <= rhsMax;
    }

    contains(rhs) {
        const thisMin = this.min == null ? -Infinity : this.min;
        const thisMax = this.max == null ? Infinity : this.max;
        const rhsMin = rhs.min == null ? -Infinity : rhs.min;
        const rhsMax = rhs.max == null ? Infinity : rhs.max;
        return thisMin <= rhsMin && thisMax >= rhsMax;
    }

    toString() {
        if (this.isVariable())
            return (this.minName != null && this.minName === this.maxName ? this.minName
                : (this.minName != null ? this.minName : this.min == null ? '' : '' + this.min) + ',' +
                (this.maxName != null ? this.maxName : this.max == null ? '' : '' + this.max));
        if (this.isAny()) return '**';
        if (this.isMaybe()) return '?';
        if (this.isSome()) return '+';
        if (this.isMaybeSome()) return '*';
        if (this.isExact()) return '' + this.min;
        if (this.isAnyMaybe()) return '??';
        if (this.isAntiMaybe()) return '-?';
        if (this.isAntiSome()) return '-';
        if (this.isAntiMaybeSome()) return '-*';
        return (this.min == null ? '' : this.min) + ',' + (this.max == null ? '' : this.max);
    }

    equals(other) {
        return other instanceof cInt &&
            this.min === other.min && this.max === other.max &&
            this.minName === other.minName && this.maxName === other.maxName;
    }

    hashCode() {
        let h = 1;
        h = 31 * h + (this.min == null ? 0 : this.min);
        h = 31 * h + (this.max == null ? 0 : this.max);
        h = 31 * h + (this.minName == null ? 0 : hashString(this.minName));
        h = 31 * h + (this.maxName == null ? 0 : hashString(this.maxName));
        return h | 0;
    }
}

cInt.ZERO_ = new cInt(0, 0);
cInt.ONE_ = new cInt(1, 1);
cInt.SOME_ = new cInt(1, null);
cInt.MAYBE_ = new cInt(0, 1);
cInt.MAYBESOME_ = new cInt(0, null);
cInt.ANY_ = new cInt(null, null);

/* ---------------------------------------------------------------------------
 * Path / branch helpers (the string algebra shared by parse and render)
 * ------------------------------------------------------------------------- */

/* Split a branch body on its top-level commas: a comma inside a nested branch or
 * poly stays in its element, so `a[x,y],{b,c}/d` is two elements. */
function splitBranchElements(inner) {
    const elements = [];
    let depth = 0;
    let sb = '';
    for (let i = 0; i < inner.length; i++) {
        const ch = inner[i];
        if (ch === '{' || ch === '[') depth++;
        else if (ch === '}' || ch === ']') depth = Math.max(0, depth - 1);
        if (ch === ',' && depth === 0) {
            elements.push(sb);
            sb = '';
        } else sb += ch;
    }
    elements.push(sb);
    return elements;
}

/* Where a text's leading coefficient closes (`{2}b` → index of `}`), or -1. */
function leadingCoefficient(text) {
    const end = text.startsWith('{') ? text.indexOf('}') : -1;
    return end > 0 && COEFFICIENT_BODY_RE.test(text.slice(1, end)) ? end : -1;
}

/* Normalize one path segment as a set of paths: a singleton branch collapses to
 * its atom; a leading branch of absolute elements hoists its marker into a leading
 * empty segment (`{/a,/b}` → `["", {a,b}]`), unless those would read as a coefficient. */
function normalizeBranch(segment, leading) {
    if (segment.length < 2 || segment[0] !== '{' || segment[segment.length - 1] !== '}')
        return [segment];
    const elements = splitBranchElements(segment.slice(1, -1));
    const relative = elements.map((e) => (e.startsWith('/') ? e.slice(1) : e));
    const absolute = leading && elements.every((e) => e.startsWith('/')) &&
        (elements.length === 1 || !COEFFICIENT_BODY_RE.test(relative.join(',')));
    const atoms = absolute ? relative : elements;
    const branch = atoms.length === 1 ? atoms[0] : '{' + atoms.join(',') + '}';
    return absolute ? ['', branch] : [branch];
}

/* Split a path on top-level `/` only — a `/` inside a {branch} or ${template} stays
 * in its segment (mimics String.split("/"): a trailing empty segment is dropped). */
function splitPath(pathStr) {
    const raw = [];
    let depth = 0;
    let sb = '';
    for (let i = 0; i < pathStr.length; i++) {
        const ch = pathStr[i];
        if (ch === '$' && pathStr[i + 1] === '{') {
            const close = pathStr.indexOf('}', i + 2);
            sb += pathStr.slice(i, close + 1);
            i = close;
            continue;
        }
        if (ch === '{') depth++;
        else if (ch === '}') depth = Math.max(0, depth - 1);
        if (ch === '/' && depth === 0) {
            raw.push(sb);
            sb = '';
        } else sb += ch;
    }
    if (sb.length > 0 || raw.length === 0) raw.push(sb);
    const segments = [];
    for (let i = 0; i < raw.length; i++) segments.push(...normalizeBranch(raw[i], i === 0));
    return segments;
}

/* The last top-level `{...}` pair, as [open, close], or null. */
function lastTopLevelBrace(text) {
    let depth = 0;
    let close = -1, open = -1;
    for (let i = text.length - 1; i >= 0; i--) {
        const ch = text[i];
        if (ch === '}') {
            if (depth === 0 && close === -1) close = i;
            depth++;
        } else if (ch === '{') {
            depth--;
            if (depth === 0 && close !== -1) {
                open = i;
                break;
            }
        }
    }
    return open === -1 || close === -1 ? null : [open, close];
}

/* The first `[` at brace-depth 0 (a poly open), skipping ${…}. */
function firstTopLevelBracket(text) {
    let brace = 0;
    for (let i = 0; i < text.length; i++) {
        const ch = text[i];
        if (ch === '$' && text[i + 1] === '{') {
            i = text.indexOf('}', i + 2);
            continue;
        }
        if (ch === '{') brace++;
        else if (ch === '}') brace = Math.max(0, brace - 1);
        else if (ch === '[' && brace === 0) return i;
    }
    return -1;
}

/* The first `?` at brace- and bracket-depth 0, skipping ${…}. */
function topLevelQuestion(text) {
    let brace = 0, bracket = 0;
    for (let i = 0; i < text.length; i++) {
        const ch = text[i];
        if (ch === '$' && text[i + 1] === '{') {
            i = text.indexOf('}', i + 2);
            continue;
        }
        if (ch === '{') brace++;
        else if (ch === '}') brace = Math.max(0, brace - 1);
        else if (ch === '[') bracket++;
        else if (ch === ']') bracket = Math.max(0, bracket - 1);
        else if (ch === '?' && brace === 0 && bracket === 0) return i;
    }
    return -1;
}

function foldDotSegments(path) {
    let folded = null;
    for (let i = 0; i < path.length; i++) {
        const seg = path[i];
        if (seg === '.') {
            if (folded == null) folded = path.slice(0, i);
            continue;
        }
        if (seg === '..') {
            if (folded == null) folded = path.slice(0, i);
            if (folded.length && folded[folded.length - 1] !== '..') folded.pop();
            else folded.push(seg); // a leading `..` is kept (a displacement)
            continue;
        }
        if (folded != null) folded.push(seg);
    }
    return folded == null ? null : folded;
}

function extractTemplates(schemeStr, hostStr, portStr, pathStr, queryStr) {
    const templates = [];
    const scan = (component, str) => {
        if (str == null) return;
        const re = /\$\{([^}]+)\}/g;
        let m;
        while ((m = re.exec(str))) templates.push({component, expr: m[1]});
    };
    scan('SCHEME', schemeStr);
    scan('HOST', hostStr);
    scan('PORT', portStr);
    scan('PATH', pathStr);
    scan('QUERY', queryStr);
    return templates.length === 0 ? null : templates;
}

function parseQuery(query) {
    const map = new Map();
    if (query == null || query === '') return map;
    for (const pair of query.split('&')) {
        const idx = pair.indexOf('=');
        const k = idx === -1 ? pair : pair.slice(0, idx);
        const v = idx === -1 ? '' : pair.slice(idx + 1);
        const dk = decodeQueryComponent(k);
        const dv = decodeQueryComponent(v);
        if (map.has(dk)) map.set(dk, map.get(dk) + ';' + dv);
        else map.set(dk, dv);
    }
    return map;
}

/* ---------------------------------------------------------------------------
 * fURI — the immutable uri value
 * ------------------------------------------------------------------------- */
class fURI {
    constructor(span, scheme, host, port, path, point, poly, query, templates) {
        this._span = span == null ? cInt.ONE_ : span;   // the span {b}… (type slots)
        this._scheme = scheme;                          // string | null
        this._host = host;                              // string | null
        this._port = port == null ? -1 : port;          // int, -1 = absent
        this._path = path == null ? [] : path;          // string[]
        this._point = point == null ? cInt.ONE_ : point; // the point …{d}
        this._poly = poly == null ? [] : poly;          // string[] (the [bracket])
        this._query = canonicalQ(query);                // Map (canonical order)
        this._templates = templates == null ? null : templates; // [{component, expr}]
    }

    /* ---- the builder factory: f().scheme(HTTP).host(host).port(port) ---- */
    static of(b, scheme, host, port, path, d, poly, query, templates) {
        return new fURI(b, scheme, host, port, path, d, poly, query, templates);
    }

    static empty() {
        return fURI.EMPTY;
    }

    static parse(text) {
        return _parse(text);
    }

    /* ---- component getters/setters (one method each; no args = get) ---- */
    scheme(s) {
        if (arguments.length === 0) return this._scheme;
        return _of(this._span, s, this._host, this._port, this._path, this._point, this._poly, this._query, this._templates);
    }

    host(h) {
        if (arguments.length === 0) return this._host;
        return _of(this._span, this._scheme, h, this._port, this._path, this._point, this._poly, this._query, this._templates);
    }

    port(p) {
        if (arguments.length === 0) return this._port;
        return _of(this._span, this._scheme, this._host, p, this._path, this._point, this._poly, this._query, this._templates);
    }

    path(arg) {
        if (arguments.length === 0) return this._path;
        if (typeof arg === 'string') return this.path(arg.split('/'));
        let p = arg;
        if (this._host != null && p.length && p[0] !== '' && !p[0].startsWith('/'))
            p = ['', ...p];
        return _of(this._span, this._scheme, this._host, this._port, p, this._point, this._poly, this._query, this._templates);
    }

    poly(...args) {
        if (args.length === 0) return this._poly;
        const list = (args.length === 1 && Array.isArray(args[0])) ? args[0] : args;
        const strings = list.map((x) => String(x));
        if (arraysEqual(this._poly, strings)) return this;
        return _of(this._span, this._scheme, this._host, this._port, this._path, this._point, strings, this._query, this._templates);
    }

    b(span) {
        if (arguments.length === 0) return this._span;
        if (this._span.equals(span == null ? cInt.ONE_ : span)) return this;
        return _of(span, this._scheme, this._host, this._port, this._path, this._point, this._poly, this._query, this._templates);
    }

    d(point) {
        if (arguments.length === 0) return this._point;
        if ((this._point).equals(point)) return this;
        return _of(this._span, this._scheme, this._host, this._port, this._path, point, this._poly, this._query, this._templates);
    }

    c(coefficient) {
        if (arguments.length === 0) {
            // the COUNT: product of in-place coefficients, summed over branches
            if (this.hasBranches())
                return this.branches().map((t) => t.b().mult(t.d())).reduce((a, b) => a.plus(b), cInt.ZERO_);
            return this._span.isOne() ? this._point : this._span.mult(this._point);
        }
        if (this._span.isOne() && !this.hasBranches()) return this.d(coefficient);
        return this.d(coefficient);
    }

    qMap() {
        return this._query;
    }

    templates() {
        return this._templates;
    }

    pathLength() {
        return this._path.length;
    }

    pathString() {
        return this._path.join('/');
    }

    hasScheme() {
        return this._scheme != null;
    }

    hasHost() {
        return this._host != null;
    }

    hasPort() {
        return this._port !== -1;
    }

    hasAuthority() {
        return this._host != null && this._port !== -1;
    }

    hasPoly() {
        return this._poly.length !== 0;
    }

    hasQ() {
        return this._query.size !== 0;
    }

    hasQ(key) {
        return this._query.has(typeof key === 'object' ? key.toString() : key);
    }

    hasTemplates() {
        return this._templates != null && this._templates.length !== 0;
    }

    isEmpty() {
        return !this.hasScheme() && !this.hasHost() && !this.hasPort() &&
            this._path.length === 0 && !this.hasQ();
    }

    isRelative() {
        return this._path.length !== 0 && this._path[0] !== '';
    }

    isAbsolute() {
        return !this.isRelative();
    }

    isBranch() {
        return this._path.length !== 0 && this._path[this._path.length - 1] === '';
    }

    isNode() {
        return !this.isBranch();
    }

    isLocal() {
        return !this.hasScheme() && !this.hasHost();
    }

    isAddressed() {
        return this.hasHost();
    }

    isHosted() {
        return !this.hasScheme() && this.hasHost() && !this.hasPort();
    }

    isPrefixed() {
        return this.hasScheme() && !this.hasHost();
    }

    isLoopbackHost() {
        return LOOPBACK_HOSTS.has(this._host);
    }

    name() {
        return this._path.length === 0 ? '' : this._path[this._path.length - 1];
    }

    authority() {
        if (this.hasHost()) return this.hasPort() ? this._host + ':' + this._port : this._host;
        return null;
    }

    segments(index, defaultSegment) {
        if (arguments.length > 0)
            return this.segmentLength() > index ? this.segments()[index] : defaultSegment;
        if (this.isEmpty()) return [];
        const p = this._path;
        if (p.length === 0 || (p[0] !== '' && p[p.length - 1] !== '')) return p;
        if (p[0] === '' && p[p.length - 1] === '' && p.length > 1) return p.slice(1, -1);
        if (p[0] === '') return p.slice(1);
        if (p[p.length - 1] === '') return p.slice(0, -1);
        return p;
    }

    segmentLength() {
        if (this.isEmpty()) return 0;
        const p = this._path;
        if (p.length === 0 || (p[0] !== '' && p[p.length - 1] !== '')) return p.length;
        if (p[0] === '' && p[p.length - 1] === '' && p.length > 1) return p.length - 2;
        return p.length - 1;
    }

    domain() {
        if (this.hasHost() && this._host.includes('.')) {
            const split = this._host.split('.');
            if (split.length > 2) return split[1];
            return split[0];
        }
        if (this.hasHost()) return this._host;
        return null;
    }

    tldomain() {
        if (this.hasHost() && this._host.includes('.'))
            return this._host.slice(this._host.lastIndexOf('.') + 1);
        return null;
    }

    subdomain() {
        const h = this._host;
        if (h == null || h.startsWith('[')) return null;
        const m = HOST_PATTERN.exec(h);
        if (m && m[1] != null) return m[1].replace(/\./g, '');
        return null;
    }

    q(keyOrQuery, value) {
        if (arguments.length >= 2) {
            const newQ = canonicalQ(this._query);
            newQ.set(typeof keyOrQuery === 'object' ? keyOrQuery.toString() : keyOrQuery, value == null ? '' : String(value));
            return _of(this._span, this._scheme, this._host, this._port, this._path, this._point, this._poly, newQ, this._templates);
        }
        const newQ = canonicalQ(keyOrQuery);
        if (mapsEqual(this._query, newQ)) return this;
        return _of(this._span, this._scheme, this._host, this._port, this._path, this._point, this._poly, newQ, this._templates);
    }

    qString() {
        let sb = '';
        for (const [k, v] of this._query) {
            if (sb) sb += '&';
            sb += k;
            if (v !== '') sb += '=' + v;
        }
        return sb;
    }

    qValue(key, type) {
        if (!this.hasQ(key)) return null;
        const v = this._query.get(key);
        if (type === 'furi') return f(v);
        if (type === 'number' || type === Number) return Number(v);
        if (type === 'boolean' || type === Boolean) return v === 'true';
        return v; // string (default)
    }

    addQ(key, value) {
        return this.q(key, value);
    }

    removeQ(key) {
        if (this._query.size === 0 || !this._query.has(key)) return this;
        const m = canonicalQ(this._query);
        m.delete(key);
        return this.q(m);
    }

    copyQ(other) {
        if (!other.hasQ()) return this;
        const m = canonicalQ(other.qMap());
        for (const [k, v] of this._query) m.set(k, v);
        return this.q(m);
    }

    qLess(...except) {
        if (!this.hasQ()) return this;
        const m = new Map();
        for (const [k, v] of this._query) if (except.includes(k)) m.set(k, v);
        return this.q(m);
    }

    noQ() {
        return this.q(new Map());
    }

    qprocLess() {
        if (this._query.size === 0) return this;
        const m = new Map();
        for (const [k, v] of this._query) if (!k.endsWith('q')) m.set(k, v);
        return this.q(m);
    }

    dom() {
        return this.hasQ('dom') ? f(this.q('dom')) : fURI.ALL;
    }

    rng() {
        return this.hasQ('rng') ? f(this.q('rng')) : fURI.ALL;
    }

    dom(d) {
        return this.q('dom', d);
    }

    rng(r) {
        return this.q('rng', r);
    }

    hasDom() {
        return this.hasQ('dom');
    }

    hasRng() {
        return this.hasQ('rng');
    }

    args() {
        return fURI.argsList(this.q('args'));
    }

    hasArgs() {
        return this.hasQ('args');
    }

    argsList(list) {
        if (list == null || list.length === 0)
            return this.hasQ('args') ? this.removeQ('args') : this;
        const m = canonicalQ(this._query);
        m.set('args', '[' + list.map((x) => x.toString()).join(',') + ']');
        return this.q(m);
    }

    static argsList(value) {
        if (value == null) return [];
        let body = value.trim();
        if (body.startsWith('[') && body.endsWith(']')) body = body.slice(1, -1);
        if (body.trim() === '') return [];
        const out = [];
        let depth = 0, start = 0;
        for (let i = 0; i < body.length; i++) {
            const ch = body[i];
            if (ch === '{') depth++;
            else if (ch === '}') depth = Math.max(0, depth - 1);
            else if (ch === ',' && depth === 0) {
                out.push(f(body.slice(start, i).trim()));
                start = i + 1;
            }
        }
        out.push(f(body.slice(start).trim()));
        return out;
    }

    constant() {
        return this.q('constq', null);
    }

    type(t) {
        return this.q('T', t);
    }

    /* ---- coefficient sugar ---- */
    any() {
        return this.c(cInt.ANY_);
    }

    zero() {
        return this.c(cInt.ZERO_);
    }

    one() {
        return this.c(cInt.ONE_);
    }

    maybe() {
        return this.c(cInt.MAYBE_);
    }

    maybeMaybe() {
        return this.c(cInt.of(-1, 1));
    }

    some() {
        return this.c(cInt.SOME_);
    }

    maybeSome() {
        return this.c(cInt.MAYBESOME_);
    }

    isZero() {
        return this.c().isZero();
    }

    isOne() {
        return this.c().isOne();
    }

    isAny() {
        return this.c().isAny();
    }

    isSome() {
        return this.c().isSome();
    }

    isMaybe() {
        return this.c().isMaybe();
    }

    isMaybeSome() {
        return this.c().isMaybeSome();
    }

    isZeroable() {
        return this.c().isZeroable();
    }

    cLess() {
        return this.d(null);
    }

    polyLess() {
        return this.poly([]);
    }

    /* ---- path shape ops ---- */
    asAbsolute() {
        if (this._path.length && this._path[0] === '') return this;
        const p = [...this._path];
        if (p.length && p[0] !== '') p.unshift('');
        return _of(this._span, this._scheme, this._host, this._port, p, this._point, this._poly, this._query, this._templates);
    }

    asRelative() {
        if (this._path.length === 0 || this._path[0] !== '') return this;
        const p = [...this._path];
        if (p.length && p[0] === '') p.shift();
        return _of(this._span, this._scheme, this._host, this._port, p, this._point, this._poly, this._query, this._templates);
    }

    asNode() {
        if (this._path.length === 0 || this._path[this._path.length - 1] !== '') return this;
        const p = [...this._path];
        if (p.length && p[p.length - 1] === '') p.pop();
        return _of(this._span, this._scheme, this._host, this._port, p, this._point, this._poly, this._query, this._templates);
    }

    asBranch() {
        if (this._path.length && this._path[this._path.length - 1] === '') return this;
        const p = [...this._path];
        if (p.length && p[p.length - 1] !== '') p.push('');
        return _of(this._span, this._scheme, this._host, this._port, p, this._point, this._poly, this._query, this._templates);
    }

    trim() {
        const trimmed = this._path.filter((s) => s !== '');
        if (trimmed.length !== this._path.length)
            return this.isAbsolute() ? this.path(trimmed).asAbsolute() : this.path(trimmed).asRelative();
        return this;
    }

    extend(segment) {
        if (segment == null) return this;
        if (typeof segment === 'number') return this.extend('' + segment);
        if (segment instanceof fURI) return this.extend(segment.pathString()).addQ(Object.fromEntries(segment.qMap()));
        if (segment === '' && this._path.length && this._path[this._path.length - 1] === '') return this.asBranch();
        const newPath = [...this._path];
        let prefix = segment.split('/');
        if (newPath.length && newPath[newPath.length - 1] === '') newPath.pop();
        if (newPath.length && prefix.length && prefix[0] === '') prefix = prefix.slice(1);
        newPath.push(...prefix);
        if (this._path.length === 1 && this._path[0] === '') newPath.unshift('');
        let f;
        if (segment.includes('?')) {
            const qs = canonicalQ(this._query);
            for (const [k, v] of _parse(segment).qMap()) qs.set(k, v);
            f = _of(this._span, this._scheme, this._host, this._port, newPath, this._point, this._poly, qs, this._templates);
        } else {
            f = _of(this._span, this._scheme, this._host, this._port, newPath, this._point, this._poly, this._query, this._templates);
        }
        return f.hasHost() ? f.asAbsolute() : f;
    }

    extendBranch(...siblings) {
        if (siblings.length < 2) return this.extend(siblings[0]);
        return this.extend('{' + siblings.join(',') + '}');
    }

    prepend(segment) {
        if (segment == null) return this;
        if (segment === '' && this._path.length && this._path[0] === '') return this;
        const newPath = [];
        const prefix = segment.split('/');
        if ((segment.startsWith('/') && !this.pathString().startsWith('/')) || this.hasHost())
            newPath.push('');
        newPath.push(...prefix);
        const rest = this._path.length && this._path[0] === '' ? this._path.slice(1) : this._path;
        newPath.push(...rest);
        return _of(this._span, this._scheme, this._host, this._port, newPath, this._point, this._poly, this._query, this._templates);
    }

    _hasBlankCap(prefix) {
        return this._path.length !== 0 &&
            (prefix ? this._path[0] === '' : this._path[this._path.length - 1] === '');
    }

    head(steps) {
        if (steps === 0) return _of(this._span, this._scheme, this._host, this._port, [], this._point, this._poly, this._query, this._templates);
        if (steps >= this.pathLength()) return this;
        const hasBlankRight = this._hasBlankCap(false);
        const hasBlankLeft = this._hasBlankCap(true);
        const newPath = this._path.slice(0, steps + (hasBlankLeft ? 1 : 0));
        if (hasBlankRight && newPath.length && newPath[newPath.length - 1] !== '') newPath.push('');
        return _of(this._span, this._scheme, this._host, this._port, newPath, this._point, this._poly, this._query, this._templates);
    }

    tail(steps) {
        if (steps === 0) return _of(this._span, this._scheme, this._host, this._port, [], this._point, this._poly, this._query, this._templates);
        if (steps >= this.pathLength()) return this;
        const hasBlankRight = this._hasBlankCap(false);
        const hasBlankLeft = this._hasBlankCap(true);
        const newPath = this._path.slice(this._path.length - steps - (hasBlankRight ? 1 : 0));
        if (hasBlankLeft && newPath.length && newPath[0] !== '') newPath.unshift('');
        return _of(this._span, this._scheme, this._host, this._port, newPath, this._point, this._poly, this._query, this._templates);
    }

    retract(arg) {
        if (typeof arg === 'string') {
            if (this.hasPrefix(arg))
                return _of(this._span, this._scheme, this._host, this._port,
                    this._path.slice(0, this._path.length - arg.split('/').length),
                    this._point, this._poly, this._query, this._templates);
            return this;
        }
        const steps = arg;
        if (steps === 0) return this;
        if (steps >= this.pathLength())
            return _of(this._span, this._scheme, this._host, this._port, [], this._point, this._poly, this._query, this._templates);
        const hasBlank = this._hasBlankCap(false);
        const newPath = [...this._path];
        if (hasBlank) newPath.pop();
        for (let i = 0; i < steps; i++) newPath.pop();
        if (hasBlank) newPath.push('');
        if (newPath.length && newPath.every((s) => s === '')) newPath.length = 0;
        return _of(this._span, this._scheme, this._host, this._port, newPath, this._point, this._poly, this._query, this._templates);
    }

    retractPattern() {
        if (this._path.length === 0) return this;
        const newPath = [...this._path];
        const hasBlank = this._hasBlankCap(false);
        while (newPath.length &&
        (newPath[newPath.length - 1] === '' || newPath[newPath.length - 1] === '#' || newPath[newPath.length - 1] === '+'))
            newPath.pop();
        if (hasBlank && newPath.length && newPath[newPath.length - 1] !== '') newPath.push('');
        return _of(this._span, this._scheme, this._host, this._port, newPath, this._point, this._poly, this._query, this._templates);
    }

    pretract(arg) {
        if (typeof arg === 'string') {
            if (arg == null) return this;
            if (arg === '' && this._path.length && this._path[0] === '')
                return _of(this._span, this._scheme, this._host, this._port, this._path.slice(1),
                    this._point, this._poly, this._query, this._templates);
            if (this.hasPrefix(arg)) return this.pretract(arg.split('/').length);
            return this;
        }
        const steps = arg;
        if (steps === 0) return this;
        if (steps >= this.pathLength())
            return _of(this._span, this._scheme, this._host, this._port, [], this._point, this._poly, this._query, this._templates);
        const hasBlank = this._hasBlankCap(true);
        const newPath = [...this._path];
        if (hasBlank) newPath.shift();
        for (let i = 0; i < steps; i++) newPath.shift();
        if (hasBlank && newPath.length && newPath[0] !== '') newPath.unshift('');
        return _of(this._span, this._scheme, this._host, this._port, newPath, this._point, this._poly, this._query, this._templates);
    }

    hasPrefix(prefix) {
        if (prefix == null) return false;
        if (typeof prefix === 'string') return this.hasPrefix(f(prefix));
        if (prefix.hasPattern()) {
            let running = this;
            while (!running.isEmpty() && running.segmentLength() > 0) {
                if (running.bimatches(prefix)) return this.hasPrefix(running);
                running = running.isBranch() ? running.asNode() : running.retract(1).asBranch();
            }
            return false;
        }
        if (prefix.hasScheme() && (!this.hasScheme() || this.scheme() !== prefix.scheme())) return false;
        if (prefix.hasAuthority() && (!this.hasAuthority() || this.authority() !== prefix.authority())) return false;
        let prefixLen = prefix.pathLength();
        if (prefixLen > 0 && prefix.path()[prefixLen - 1] === '') prefixLen--;
        for (let i = 0; i < prefixLen; i++) {
            if (this.pathLength() <= i) return false;
            if (this._path[i] !== prefix.path()[i]) return false;
        }
        return true;
    }

    hasPostfix(postfix) {
        if (postfix == null) return false;
        return this.toString().endsWith(typeof postfix === 'string' ? postfix : postfix.toString());
    }

    removePrefix(prefix) {
        if (prefix == null) return this;
        if (prefix.hasPattern()) {
            let running = this;
            while (!running.isEmpty() && running.segmentLength() > 0) {
                if (running.bimatches(prefix)) return this.removePrefix(running);
                running = running.isBranch() ? running.asNode() : running.retract(1).asBranch();
            }
            return this;
        }
        const newPath = this.toString();
        const pre = prefix.toString();
        if (!newPath.startsWith(pre)) return this;
        if (f(pre).equals(this)) return f('');
        return _parse(newPath.slice(pre.length + (newPath[pre.length] === '/' ? 1 : 0)));
    }

    origin() {
        return _of(null, this._scheme, this._host, this._port, [], null, null, new Map(), null);
    }

    origin(o) {
        return _of(this._span, o.scheme(), o.host(), o.port(), this._path, this._point, this._poly, this._query, this._templates);
    }

    localize() {
        if (this._scheme == null && this._host == null) return this;
        if (this._path.length > 2 && this._path[0] === '' && this._path[1] === '')
            return _of(this._span, null, null, -1, this._path.slice(2), this._point, this._poly, this._query, this._templates);
        return _of(this._span, null, null, -1, this._path, this._point, this._poly, this._query, this._templates);
    }

    toPath() {
        let temp = f('');
        temp = temp.extend(this.hasScheme() ? this._scheme.replace(/\[/g, '/').replace(/\]/g, '/') : null).asNode();
        temp = temp.extend(this.hasHost() ? this._host.replace(/\./g, '/').replace(/:/g, '/') : null);
        if (this._port !== -1) temp = temp.extend(this._port);
        temp = temp.extend(this.pathString()
            .replace(/\./g, '/').replace(/:/g, '/').replace(/\[/g, '/').replace(/\]/g, '/').replace(/\/\//g, '/'));
        return temp;
    }

    pathEncoding() {
        return (this.isLoopbackHost() ? this.host('localhost') : this).toPath();
    }

    basePath() {
        return this.cLess().qLess().polyLess().b(null);
    }

    hasDotSegments() {
        return this._path.some((s) => s === '.' || s === '..');
    }

    folded() {
        if (this.isEmpty()) return this;
        const folded = foldDotSegments(this._path);
        return (folded == null || arraysEqual(folded, this._path)) ? this : this.path(folded);
    }

    resolve() {
        // dot-segment normal form (branch sum-normalization is elided in the mini port)
        return this.folded();
    }

    isId() {
        return this.resolve().path().length === 0;
    }

    id() {
        return f('.');
    }

    hasBranches() {
        if (this.hasTemplates()) return false;
        for (const seg of this._path) if (seg.indexOf('{') >= 0) return true;
        return false;
    }

    branches() {
        if (!this.hasBranches()) return [this];
        const out = [];
        for (const {str, point} of _expand(this.pathString())) {
            const el = _parse(str);
            out.push(_of(
                el.b().isOne() ? this._span : el.b(),
                el.scheme() == null ? this._scheme : el.scheme(),
                el.host() == null ? this._host : el.host(),
                el.port() === -1 ? this._port : el.port(),
                el.path(),
                this._point.mult(point).mult(el.d()),
                el.poly().length === 0 ? this._poly : el.poly(),
                el.qMap().size === 0 ? this._query : el.qMap(),
                this._templates));
        }
        return out;
    }

    hasPattern(pattern) {
        if (pattern !== undefined) {
            if (this._scheme === pattern || this._host === pattern) return true;
            for (const seg of this._path) if (seg === pattern) return true;
            for (const [k, v] of this._query) if (v === pattern || k === pattern) return true;
            return false;
        }
        if (this._scheme === '#' || this._scheme === '+' || this._host === '#' || this._host === '+') return true;
        for (const seg of this._path) if (seg === '#' || seg === '+') return true;
        for (const [k, v] of this._query) if (v === '#' || v === '+' || k === '#' || k === '+') return true;
        return false;
    }

    isGeneric() {
        if (this.hasScheme() || this.hasAuthority() || this._path.length === 0) return false;
        if (this.segmentLength() === 1 && (this._path[0] === '#' || this._path[0] === '+')) return false;
        let hasCapitalGeneric = false;
        for (const seg of this._path) {
            if (seg === '') continue;
            let allUpper = true;
            for (const c of seg) {
                if ((c !== '#' && c !== '+' && !isAlpha(c)) || isLower(c)) return false;
                if (!isUpper(c)) allUpper = false;
            }
            if (allUpper) hasCapitalGeneric = true;
        }
        return hasCapitalGeneric;
    }

    hasAnchor() {
        if (this.hasScheme()) return this._scheme.startsWith('@');
        if (this.hasHost()) return this._host.startsWith('@');
        if (this.segments().length) return this.segments()[0].startsWith('@');
        return false;
    }

    stripAnchor() {
        return this.hasAnchor() ? f(this.toString().slice(1)) : this;
    }

    addAnchor() {
        return this.hasAnchor() ? this : f('@' + this.toString());
    }

    bimatches(other) {
        return this.test(other) || other.test(this);
    }

    test(rhs) {
        if (rhs == null) return false;
        if (rhs.hasBranches()) {
            if (this.hasBranches()) return this.branches().some((b) => rhs.branches().some((c) => b._testHarness(c)));
            return rhs.branches().some((c) => this._testHarness(c));
        }
        if (this.hasBranches()) return this.branches().some((b) => b._testHarness(rhs));
        return this._testHarness(rhs);
    }

    _testHarness(rhs) {
        if (rhs == null) return false;
        const point = this._point;
        const rhsPoint = rhs.d();
        if (point.isZero() && rhsPoint.isZero()) return true;
        if (point.within(rhsPoint)) {
            if (point.isZero()) return true;
        } else return false;
        // a bare `#` uri accepts everything
        if (rhs.path().length === 1 && rhs.path()[0] === '#' && rhs.scheme() == null && rhs.host() == null &&
            rhs.poly().length === 0 && rhs.qMap().size === 0 && !rhs.hasTemplates())
            return true;
        if (!rhs.hasPattern() && !this.hasPattern()) {
            if (this.name() !== rhs.name()) return false;
        }
        // the bracket rule (simplified: specialization needs an acceptor)
        const lhsPoly = this._poly, rhsPoly = rhs.poly();
        if (lhsPoly.length) {
            if (rhsPoly.length === 0) return false;
            for (let i = 0; i < Math.max(lhsPoly.length, rhsPoly.length); i++) {
                const rp = f(rhsPoly[Math.min(i, rhsPoly.length - 1)]);
                if (rp.equals(fURI.ALL)) break;
                if (i >= lhsPoly.length) {
                    if (!rp.b().isZeroable()) return false;
                    continue;
                }
                if (rp.equals(fURI.WILD_ONE)) continue;
                const lp = f(lhsPoly[i]);
                if (!lp.b().within(rp.b()) || !(rp.isGeneric() && !lp.isGeneric() ? lp.d().within(rp.d()) : lp.test(rp)))
                    return false;
            }
        }
        if (rhs.scheme() === '#') return true;
        if (this._scheme !== rhs.scheme() && rhs.scheme() !== '+') return false;
        if (rhs.host() === '#') return true;
        if (this._host !== rhs.host() && rhs.host() !== '+') return false;
        if (rhs.port() !== -1 || rhs.host() !== '+') {
            if (this._port !== -1 && (rhs.port() === -1 || (rhs.port() !== 0 && this._port !== rhs.port())))
                return false;
        }
        if (!rhs.hasPattern()) return arraysEqual(this._path, rhs.path());
        if (rhs.path().length === 1 && rhs.path()[0] === '#') return true;
        if (this.isAbsolute() !== rhs.isAbsolute()) return false;
        for (let i = 0; i < rhs.path().length; i++) {
            if (rhs.path()[i] === '#') return true;
            if (rhs.path()[i] !== '+') {
                if (this._path.length <= i) return false;
                if (this._path[i] !== rhs.path()[i]) return false;
            }
        }
        if (this._path.length !== rhs.path().length) return false;
        for (const [k, v] of rhs.qMap()) {
            let matched = false;
            for (const [xk, xv] of this._query) {
                if (f(xk).test(f(k)) && (v === '' || v === '+' || xv === v)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    mult(other) {
        if (other.isZero()) return fURI.NOOBJ;
        const newPath = [...this.folded().path()];
        const o = other.folded();
        if (o.path().length) {
            if (newPath.length && newPath[newPath.length - 1] === '') newPath.pop();
            newPath.push(...(o.path()[0] === '' ? o.path().slice(1) : o.path()));
        }
        const newQ = canonicalQ(this._query);
        for (const [k, v] of o.qMap()) newQ.set(k, v);
        return _of(this._span, this._scheme, this._host, this._port, newPath,
            this._point.mult(o.d()), this._poly, newQ, this._templates).folded();
    }

    neg() {
        return _of(this._span, this._scheme, this._host, this._port, this._path,
            this._point.neg(), this._poly, this._query, this._templates);
    }

    _template(component) {
        if (!this._templates) return undefined;
        const t = this._templates.find((t) => t.component === component);
        return t ? '${' + t.expr + '}' : undefined;
    }

    toString() {
        const sb = [];
        if (!this._span.isOne()) sb.push('{' + this._span.toString() + '}');
        if (this.hasTemplates()) {
            const sch = this._template('SCHEME') ?? this._scheme;
            if (sch != null) sb.push(sch + ':');
            const h = this._template('HOST') ?? this._host;
            if (h != null) sb.push('//' + h);
            const p = this._template('PORT') ?? (this._port === -1 ? null : '' + this._port);
            if (p != null) sb.push(':' + p);
            if (this._path.length === 1 && this._path[0] === '') sb.push('/');
            else sb.push(this._path.join('/'));
            const q = this._template('QUERY') ?? (this.qString() === '' ? null : this.qString());
            if (q != null) sb.push('?' + q);
        } else {
            if (this._scheme != null) sb.push(this._scheme + ':');
            if (this._host != null) {
                sb.push('//' + this._host);
                if (this._port !== -1) sb.push(':' + this._port);
            }
            if (this._path.length === 1 && this._path[0] === '') sb.push('/');
            else sb.push(this._path.join('/'));
            if (this._poly.length) sb.push('[' + this._poly.join(',') + ']');
            if (!this._point.isOne()) sb.push('{' + this._point.toString() + '}');
            if (this._query.size) sb.push('?' + this.qString());
        }
        return sb.join('');
    }

    equals(other) {
        if (!(other instanceof fURI)) return false;
        if (this === other) return true;
        const a = this.folded(), b = other.folded();
        return a._scheme === b._scheme &&
            a._host === b._host &&
            a._port === b._port &&
            arraysEqual(a._path, b._path) &&
            ((!a.hasPoly() && !b.hasPoly()) || arraysEqual(a._poly, b._poly)) &&
            a._point.equals(b._point) &&
            a._span.equals(b._span) &&
            ((!a.hasTemplates() && !b.hasTemplates()) || _templatesEqual(a._templates, b._templates)) &&
            ((!a.hasQ() && !b.hasQ()) || mapsEqual(a._query, b._query));
    }

    hashCode() {
        const a = this.folded();
        let h = 1;
        h = 31 * h + (a._scheme == null ? 0 : hashString(a._scheme));
        h = 31 * h + hashArray(a._path);
        h = 31 * h + a._point.hashCode();
        h = 31 * h + a._span.hashCode();
        h = 31 * h + (a._templates == null ? 0 : hashArray(a._templates.map((t) => t.component + '\u0000' + t.expr)));
        if (h === 0) h = -1;
        return h | 0;
    }

    /* the same address, documented: append the ?docq qproc */
    doc() {
        return this.q('docq', '');
    }

    /* the clickable element. the text is set by the caller (a highlighted slice,
       a short label, …); this only adds the click behavior + pointer cursor.
       normal click opens the uri, shift-click opens its ?docq. */
    toElement(open, cls) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = cls || 'obj-link';
        b.title = this.toString() + ' · shift-click for ?docq';
        b.addEventListener('click', (ev) => {
            ev.preventDefault();
            ev.stopPropagation();
            if (open) open(ev.shiftKey ? this.doc() : this);
        });
        return b;
    }
}

const _templatesEqual = (a, b) =>
    a.length === b.length && a.every((t, i) => t.component === b[i].component && t.expr === b[i].expr);

/* ---------------------------------------------------------------------------
 * Construction funnel + parser + factory
 * ------------------------------------------------------------------------- */

function _of(b, scheme, host, port, path, d, poly, query, templates) {
    return new fURI(b, scheme, host, port, path, d, poly, query, templates);
}

function _parse(raw) {
    let s = raw;
    if (s.startsWith('<') && s.endsWith('>')) s = s.slice(1, -1);
    if (s === '') return fURI.EMPTY;
    if (s === '{0}') return fURI.NOOBJ;
    if (s === '/') return _of(null, null, null, -1, ['', ''], cInt.ONE_, [], new Map(), null);

    const hasTemplates = s.includes('${');

    // 1. split query at the first top-level `?`
    let head = s, queryStr = null;
    const qIdx = topLevelQuestion(s);
    if (qIdx !== -1) {
        head = s.slice(0, qIdx);
        queryStr = s.slice(qIdx + 1);
    }

    // 2. parse the query (optional rng<=dom shorthand leads)
    let rng = null, dom = null, qRest = queryStr;
    const arrow = queryStr == null ? null : /^([^<&]+)<=([^&?]+)/.exec(queryStr);
    if (arrow) {
        rng = decodeQueryComponent(arrow[1]);
        dom = decodeQueryComponent(arrow[2]);
        qRest = queryStr.slice(arrow[0].length);
        if (qRest.startsWith('&')) qRest = qRest.slice(1);
    }
    let q = new Map();
    if (dom != null) q.set('dom', dom);
    if (rng != null) q.set('rng', rng);
    for (const [k, v] of parseQuery(qRest)) q.set(k, v);
    q = canonicalQ(q);

    // 3. leading span {b} before a `/` or a lone atom (type slots)
    let span = cInt.ONE_;
    const spanMatch = /^\{([^{}]+)\}(?=\/|[^/{}?&[\]:]+($|[\{[?]))/.exec(head);
    if (spanMatch && COEFFICIENT_BODY_RE.test(spanMatch[1])) {
        span = cInt.of(spanMatch[1]);
        head = head.slice(spanMatch[1].length + 2);
    }

    // 4. scheme: the `:` before any `/`
    let scheme = null, host = null, port = -1, rest = head;
    const colon = rest.indexOf(':');
    const slash = rest.indexOf('/');
    if (colon !== -1 && (slash === -1 || colon < slash)) {
        scheme = rest.slice(0, colon);
        rest = rest.slice(colon + 1);
    }

    // 5. authority `//host[:port]` (IPv6 IP-literal bracketed)
    if (rest.startsWith('//')) {
        rest = rest.slice(2);
        const slash2 = rest.indexOf('/');
        let auth;
        if (slash2 === -1) {
            auth = rest;
            rest = '';
        } else {
            auth = rest.slice(0, slash2);
            rest = rest.slice(slash2);
        }
        if (auth.startsWith('[')) {
            const close = auth.indexOf(']');
            host = auth.slice(0, close + 1);
            const after = auth.slice(close + 1);
            if (after.startsWith(':')) port = parseInt(after.slice(1), 10);
        } else {
            const c = auth.indexOf(':');
            if (c === -1) host = auth;
            else {
                host = auth.slice(0, c);
                port = parseInt(auth.slice(c + 1), 10);
            }
        }
        if (host === '') host = null;
    }

    // 6. path [poly] {coefficient}
    let poly = [];
    let coeffText = null;
    let pathStr = rest;
    const ps = firstTopLevelBracket(rest);
    if (ps !== -1) {
        const close = rest.indexOf(']', ps);
        poly = splitBranchElements(rest.slice(ps + 1, close)).map((x) => x.trim()).filter((x) => x !== '');
        coeffText = rest.slice(close + 1);
        pathStr = rest.slice(0, ps);
    }
    if (coeffText == null) {
        const brace = lastTopLevelBrace(pathStr);
        if (brace && COEFFICIENT_BODY_RE.test(pathStr.slice(brace[0] + 1, brace[1]))) {
            coeffText = pathStr.slice(brace[0], brace[1] + 1);
            pathStr = pathStr.slice(0, brace[0]);
        }
    }
    let d = cInt.ONE_;
    if (coeffText && coeffText.startsWith('{') && coeffText.endsWith('}'))
        d = cInt.of(coeffText.slice(1, -1));

    // 7. path segments (branch-aware)
    let path = pathStr === '' ? [] : splitPath(pathStr);
    if (pathStr !== '' && pathStr.endsWith('/')) path.push('');
    if (path.length && path.every((x) => x === '')) path = [];

    const templates = hasTemplates ? extractTemplates(scheme, host, pathStr, pathStr, queryStr) : null;
    return _of(span, scheme, host, port, path, d, poly, q, templates);
}

function f(arg) {
    if (arg == null) return fURI.EMPTY;
    if (arg instanceof fURI) return arg;
    return _parse(String(arg));
}

/* the empty uri is one shared value */
fURI.EMPTY = new fURI(cInt.ONE_, null, null, -1, [], cInt.ONE_, [], new Map(), null);
fURI.NOOBJ = new fURI(null, null, null, -1, ['noobj'], cInt.ZERO_, [], new Map(), null);
fURI.ALL = f('#');
fURI.WILD_ONE = f('+');

export {fURI, cInt, f};
export default fURI;