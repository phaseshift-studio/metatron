/*
 * obj-mtron-serializer — serialize plain JavaScript values to mtron value text.
 *
 * The inverse of the reader in mtron-io.js: it takes PLAIN JS values (strings,
 * numbers, booleans, arrays, Maps/objects, fURI instances, Uint8Array) and emits
 * the mtron VALUE grammar, with optional tid/vid for typed values. It does NOT
 * understand the PlatonicObj classes — that job is mtron-io.js's write().
 *
 *   true | false          bool
 *   1324  |  -5           int
 *   12.345 | 1e3          real   (integral reals keep ".0", e.g. 12.0)
 *   0xaf3403              bytes
 *   'x' | "x" | """x"""   str
 *   <a/b/c>               uri
 *   [1,3,4]               lst
 *   [a=>1,b=>2]           rec
 *   tid::value@vid        typed value
 *   noobj                 null/undefined
 *
 * A bare JS number cannot carry the int/real distinction (12 === 12.0), so an
 * integral number is an int and every non-integral number is a real. To force a
 * real when the value is integral, pass {real: 12.0} or serialize.real(12.0);
 * to force an int, pass {int: n} or serialize.int(n).
 *
 * ES module. The only import is ./furi.js, to detect fURI values.
 */
import { fURI } from './furi.js';

/* quote a string the way mtron-io's reader expects: single quotes when there is
   no single quote, double quotes when a single quote is present, triple quotes
   for multiline, and escaped double quotes as the last resort. */
function quote(text) {
    const s = String(text);
    const multiline = /[\n\r]/.test(s);
    if (!multiline && s.indexOf("'") < 0) return "'" + s.replace(/\\/g, "\\\\") + "'";
    if (!multiline && s.indexOf('"') < 0) return '"' + s.replace(/\\/g, "\\\\") + '"';
    if (s.indexOf('"""') < 0) return '"""' + s + '"""';
    return '"' + s.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n").replace(/\r/g, "\\r") + '"';
}

/* a forced real: always a real spelling, integral values keep their ".0" */
function real(n) {
    const v = Number(n);
    if (!Number.isFinite(v)) return String(v);
    const s = String(v);
    return /[.eE]/.test(s) ? s : s + '.0';
}

/* a forced int: truncate toward zero so the spelling is always integral */
function int(n) {
    return String(Math.trunc(Number(n)));
}

function bytes(v) {
    return '0x' + Array.from(v).map((b) => b.toString(16).padStart(2, '0')).join('');
}

/* rec entries render as `key=>value`; keys are bare, values recurse */
function rec(entries) {
    return '[' + entries.map(([k, v]) => String(k) + '=>' + base(v)).join(',') + ']';
}

/* a {real:n}/{int:n} tagged object forces that kind; otherwise null */
function forced(value) {
    if (typeof value.real === 'number') return real(value.real);
    if (typeof value.int === 'number') return int(value.int);
    return null;
}

function base(value) {
    if (value == null) return 'noobj';
    if (typeof value === 'boolean') return value ? 'true' : 'false';
    if (typeof value === 'number') return Number.isInteger(value) ? String(value) : real(value);
    if (typeof value === 'string') return quote(value);
    if (value instanceof Uint8Array) return bytes(value);
    if (value instanceof fURI) return '<' + value.toString() + '>';
    if (Array.isArray(value)) return '[' + value.map(base).join(',') + ']';
    if (value instanceof Map) return rec([...value.entries()]);
    if (typeof value === 'object') {
        const forcedValue = forced(value);
        if (forcedValue !== null) return forcedValue;
        return rec(Object.entries(value));
    }
    return String(value);
}

function serialize(value, opts) {
    let s = base(value);
    const tid = opts && opts.tid;
    const vid = opts && opts.vid;
    if (tid != null && tid !== '') s = tid.toString() + '::' + s;
    if (vid != null && vid !== '') s += '@' + vid.toString();
    return s;
}

serialize.real = real;
serialize.int = int;

export { serialize, quote, real, int };
export default serialize;
