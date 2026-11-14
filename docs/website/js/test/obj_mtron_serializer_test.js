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
import { serialize } from '../obj_mtron_serializer.js';
import { read } from '../mtron-io.js';
import { f } from '../furi.js';

let pass = 0, fail = 0;
function eq(actual, expected, label) {
    if (actual === expected) pass++;
    else { fail++; console.log('FAIL', label, '\n  expected:', JSON.stringify(expected), '\n  actual:  ', JSON.stringify(actual)); }
}
function t(cond, label) { if (cond) pass++; else { fail++; console.log('FAIL', label); } }

/* ---- scalars ---- */
eq(serialize(true), 'true', 'bool true');
eq(serialize(false), 'false', 'bool false');
eq(serialize(123), '123', 'int positive');
eq(serialize(-5), '-5', 'int negative');
eq(serialize(0), '0', 'int zero');
eq(serialize(12.345), '12.345', 'real fraction');

/* ---- int/real disambiguation ---- */
eq(serialize(12.0), '12', 'a bare 12.0 is indistinguishable from the int 12');
eq(serialize.real(12.0), '12.0', 'serialize.real keeps .0 on integral');
eq(serialize.real(12.5), '12.5', 'serialize.real leaves a fraction');
eq(serialize({real: 12.0}), '12.0', 'tagged {real} keeps .0 on integral');
eq(serialize({int: 12.9}), '12', 'tagged {int} forces integer form');
eq(serialize.int(-5), '-5', 'serialize.int forces integer form');
eq(serialize(1e-7), '1e-7', 'exponent real is preserved');

/* ---- strings: single / double / triple / escapes ---- */
eq(serialize('hello'), "'hello'", 'string prefers single quotes');
eq(serialize("a'b"), '"a\'b"', 'string with a single quote uses double quotes');
eq(serialize('a"b'), "'a\"b'", 'string with a double quote still single-quoted');
eq(serialize('line one\nline two'), '"""line one\nline two"""', 'multiline uses triple quotes');
eq(serialize("a'b\"c\nd"), '"""a\'b"c\nd"""', 'quotes and newline coexist in triple quotes');
eq(serialize('back\\slash'), "'back\\\\slash'", 'backslash is escaped');
eq(serialize('a"""b'), "'a\"\"\"b'", 'a triple quote in a single-line string stays single-quoted');

/* ---- bytes ---- */
eq(serialize(Uint8Array.from([0xaf, 0x34, 0x03])), '0xaf3403', 'bytes hex lowercase');
eq(serialize(Uint8Array.from([0x00, 0xff])), '0x00ff', 'bytes pad to two digits');

/* ---- uri ---- */
eq(serialize(f('/a/b')), '</a/b>', 'fURI renders angle-bracketed');
eq(serialize(f('a/b/c')), '<a/b/c>', 'relative fURI');

/* ---- lst ---- */
eq(serialize([1, 'x']), "[1,'x']", 'lst of mixed values');
eq(serialize([]), '[]', 'empty lst');
eq(serialize([1, [2, 3]]), '[1,[2,3]]', 'nested lst');

/* ---- rec: Map and plain object ---- */
eq(serialize(new Map([['k', 'v']])), "[k=>'v']", 'Map becomes a rec');
eq(serialize(new Map([['a', 1], ['b', 2]])), '[a=>1,b=>2]', 'Map with several entries');
eq(serialize({a: 1, b: 2}), '[a=>1,b=>2]', 'plain object becomes a rec');
eq(serialize({}), '[]', 'empty object');

/* ---- typed values ---- */
eq(serialize(29, {tid: 'nat'}), 'nat::29', 'tid prefix');
eq(serialize(29, {vid: '/usr/marko/age'}), '29@/usr/marko/age', 'vid suffix');
eq(serialize(29, {tid: 'nat', vid: '/usr/marko/age'}), 'nat::29@/usr/marko/age', 'tid and vid');
eq(serialize(29, {tid: f('nat')}), 'nat::29', 'fURI tid stringifies via toString');

/* ---- null / undefined ---- */
eq(serialize(null), 'noobj', 'null is noobj');
eq(serialize(undefined), 'noobj', 'undefined is noobj');

/* ---- round-trips through read() ---- */
let r = read(serialize(123));
t(r.isInt() && r.js === 123, 'round-trip int');

r = read(serialize("a'b"));
t(r.isStr() && r.js === "a'b", 'round-trip str with single quote');

r = read(serialize('line one\nline two'));
t(r.isStr() && r.js === 'line one\nline two', 'round-trip multiline str');

r = read(serialize([1, 'x']));
t(r.isLst() && r.js.length === 2 && r.js[0].isInt() && r.js[1].isStr(), 'round-trip lst');

r = read(serialize(new Map([['k', 'v']])));
{
    const [[rk, rv]] = r.js.entries();
    t(r.isRec() && r.js instanceof Map && rk.toString() === 'k' &&
      rv.isStr() && rv.js === 'v', 'round-trip rec');
}

r = read(serialize(f('/a/b')));
t(r.isUri() && r.js.toString() === '/a/b', 'round-trip uri');

r = read(serialize(null));
t(r.isNoObj(), 'round-trip noobj');

r = read(serialize(true));
t(r.isBool() && r.js === true, 'round-trip bool');

r = read(serialize({real: 12.0}));
t(r.isReal() && r.js === 12, 'round-trip forced real');

r = read(serialize(Uint8Array.from([0xaf, 0x34])));
t(r.isBytes() && r.js[0] === 0xaf && r.js[1] === 0x34, 'round-trip bytes');

console.log('\n' + pass + ' passed, ' + fail + ' failed');
process.exit(fail === 0 ? 0 : 1);
