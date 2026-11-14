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

import {cInt, f} from '../furi.js';

let pass = 0, fail = 0;

function eq(actual, expected, label) {
    const ok = actual === expected;
    if (ok) pass++;
    else {
        fail++;
        console.log('FAIL', label, '\n  expected:', JSON.stringify(expected), '\n  actual:  ', JSON.stringify(actual));
    }
}

function t(cond, label) {
    if (cond) pass++;
    else {
        fail++;
        console.log('FAIL', label);
    }
}

/* ---- builder ---- */
eq(f().scheme('http').host('localhost').port(8555).path(['a', 'b']).toString(),
    'http://localhost:8555/a/b', 'builder scheme/host/port/path');
eq(f('').scheme('http').host('localhost').port(8555).toString(), 'http://localhost:8555', 'builder authority only');
eq(f().scheme('http').host('ginger').toString(), 'http://ginger', 'builder no port');

/* ---- parse / round-trip ---- */
for (const s of [
    'http://localhost:8555/a/b',
    'ws://hostB:8555/usr/x',
    'loop:/x',
    'fs:README.txt',
    '/usr/marko/age',
    'relative/path',
    'person[ninja]',
    '/a/{b,c}/d',
    '/m/int{3}',
    '{2}int{3}',
    '//ginger/mtron',
    '[::1]:8555/x',
    '/a/',
]) {
    eq(f(s).toString(), s, 'round-trip ' + s);
}

/* ---- canonical query ordering (rng, dom, args, then reverse-alpha) ---- */
eq(f('inst?dom=int&rng=int&z=9&a=1').toString(), 'inst?rng=int&dom=int&z=9&a=1', 'query canonical order');
eq(f('inst?int<=int').toString(), 'inst?rng=int&dom=int', 'rng<=dom shorthand canonicalizes');
eq(f('inst?rng=int&dom=int&args=[int,rec]').toString(), 'inst?rng=int&dom=int&args=[int,rec]', 'args component');
eq(f('/a?k=v').q('k2', 'v2').toString(), '/a?k2=v2&k=v', 'addQ sorts into canonical position');
eq(f('/a?k=v').removeQ('k').toString(), '/a', 'removeQ');

/* ---- matching ---- */
t(f('/a/b').test(f('/a/+')), 'path + wildcard matches one');
t(f('/a/b/c').test(f('/a/#')), 'path # wildcard matches rest');
t(!f('/a').test(f('/a/b')), 'shorter does not match longer');
t(!f('/a/b').test(f('/a')), 'longer does not match shorter');
t(f('/a/b').test(f('#')), '# matches anything');
t(f('/a/b').bimatches(f('/a/+')), 'bimatches');
t(f('ws://host:8555/a').test(f('ws://+:8555/a')), 'host + wildcard');
t(f('/a{2}').test(f('/a{1,3}')), 'coefficient within');

/* ---- path ops ---- */
eq(f('a/b').extend('c').toString(), 'a/b/c', 'extend');
eq(f('a/b').extend('c/').toString(), 'a/b/c/', 'extend branch');
eq(f('a/b/c').retract(1).toString(), 'a/b', 'retract');
eq(f('a/b/c').pretract(1).toString(), 'b/c', 'pretract');
eq(f('a/b').prepend('x').toString(), 'x/a/b', 'prepend');
eq(f('a/b/c').head(2).toString(), 'a/b', 'head');
eq(f('a/b/c').tail(2).toString(), 'b/c', 'tail');
eq(f('a/b').asAbsolute().toString(), '/a/b', 'asAbsolute');
eq(f('/a/b').asRelative().toString(), 'a/b', 'asRelative');
eq(f('a/b').asBranch().toString(), 'a/b/', 'asBranch');
eq(f('a/b/').asNode().toString(), 'a/b', 'asNode');
eq(f('a//b').trim().toString(), 'a/b', 'trim');

/* ---- dot resolution / identity ---- */
eq(f('a/b/../c').resolve().toString(), 'a/c', 'resolve dot fold');
eq(f('a/./b').resolve().toString(), 'a/b', 'resolve . fold');
t(f('.').isId(), '`.` is the identity');
t(f('a/b/../c').equals(f('a/c')), 'equality after resolve');
t(f('a/{c,b}').equals(f('a/{b,c}')) === false, 'unresolved branches differ by spelling');

/* ---- coefficients ---- */
eq(f('/a').any().toString(), '/a{**}', 'any coefficient');
eq(f('/a').some().toString(), '/a{+}', 'some coefficient');
eq(f('/a').maybe().toString(), '/a{?}', 'maybe coefficient');
eq(f('/a').zero().toString(), '/a{0}', 'zero coefficient');
eq(f('/a{2}').c().toString(), '2', 'count');
eq(cInt.of('{1,3}'.slice(1, -1)).toString(), '1,3', 'cInt range');
eq(cInt.of('*').toString(), '*', 'cInt star');
eq(cInt.of('+').toString(), '+', 'cInt plus');

/* ---- misc ---- */
eq(f('ws://hostB:8555/usr/x').localize().toString(), '/usr/x', 'localize');
eq(f('ws://peer.local:8555/mtron?clientq=me').pathEncoding().toString(), 'ws/peer/local/8555/mtron', 'pathEncoding');
eq(f('a/b').name(), 'b', 'name');
eq(f('http://a.b.c/').domain(), 'b', 'domain');
eq(f('http://a.b.c/').tldomain(), 'c', 'tldomain');
eq(f('http://a.b.c/').segments().join('/'), '', 'segments of root');
eq(f('/a/b/').segmentLength(), 2, 'segmentLength');
t(f('@a/b').hasAnchor(), 'hasAnchor');
eq(f('@a/b').stripAnchor().toString(), 'a/b', 'stripAnchor');
t(f('127.0.0.1').isLoopbackHost() === false && f('//localhost/a').isLoopbackHost(), 'loopback');
eq(f('').isEmpty(), true, 'empty isEmpty');

console.log('\n' + pass + ' passed, ' + fail + ' failed');
process.exit(fail === 0 ? 0 : 1);