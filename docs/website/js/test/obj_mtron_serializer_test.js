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
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parse, write, real, int, quote } from '../sdk/obj_mtron_serializer.js';
import { f } from '../sdk/furi.js';
import { Int, Real, Str, Uri, Lst, Rec, Rel, Type, Inst, noobj } from '../sdk/mtron-obj.js';

function eq(actual, expected, label) { assert.equal(actual, expected, label); }
function t(cond, label) { assert.ok(cond, label); }

/* structural equality — parse(write(o)) must answer the same tree */
function same(a, b) {
    if (a == null || b == null) return a === b;
    if (a.constructor !== b.constructor) return false;
    if ((a.tid == null) !== (b.tid == null)) return false;
    if (a.tid != null && a.tid.toString() !== b.tid.toString()) return false;
    if ((a.vid == null) !== (b.vid == null)) return false;
    if (a.vid != null && a.vid.toString() !== b.vid.toString()) return false;
    if (a.isNoObj()) return b.isNoObj();
    if (a.isBool() || a.isInt() || a.isReal() || a.isStr() || a.isFail()) return a.js === b.js;
    if (a.isUri()) return a.js.toString() === b.js.toString();
    if (a.isBytes()) return a.js.length === b.js.length && a.js.every((x, i) => x === b.js[i]);
    if (a.isRel()) return same(a.js[0], b.js[0]) && same(a.js[1], b.js[1]);
    if (a.isLst() || a.isObjs())
        return a.js.length === b.js.length && a.js.every((x, i) => same(x, b.js[i]));
    if (a.isRec()) {
        if (a.js.size !== b.js.size) return false;
        for (const [k, v] of a.js) {
            const bk = [...b.js.keys()].find((kk) => kk.toString() === k.toString());
            if (bk === undefined || !same(v, b.js.get(bk))) return false;
        }
        return true;
    }
    if (a.isType()) return same(a.js[0], b.js[0]) && same(a.js[1], b.js[1]);
    if (a.isInst()) {
        if (!same(a.args(), b.args())) return false;
        if ((a.f() == null) !== (b.f() == null)) return false;
        return a.f() == null || same(a.f(), b.f());
    }
    if (a.isCode()) return a.js === b.js;
    return true;
}
function rt(text, label) {
    const a = parse(text);
    const w = write(a);
    const b = parse(w);
    assert.ok(same(a, b), 'round-trip ' + label + '\n  in:  ' + text + '\n  out: ' + w);
}
/* write(text) is the exact Java spelling */
function exact(text, label) {
    assert.equal(write(parse(text)), text, label);
}

test('obj_mtron_serializer', () => {
    /* ---- every mono type + noobj + fail ---- */
exact('29', 'int');
exact('-100', 'negative int');
exact('12.345', 'real');
exact('704.0', 'integral real keeps .0');
exact('true', 'bool true');
exact('false', 'bool false');
exact('0xaf34', 'bytes');
exact("'a simple string'", 'str');
exact('noobj', 'noobj');
exact("fail::['boom']", 'fail');
exact("fail::['boom']@a3f91c2b", 'fail with vid');

/* ---- base-tid elision ---- */
{
    const o = parse('29');
    t(o.isInt() && o.tid.toString() === 'int', '29 is Int with base tid int');
    t(o.isBaseTid(), 'Int isBaseTid');
}
{
    const o = parse('mB::704.0');
    t(o.isReal() && o.js === 704 && o.tid.toString() === 'mB', 'mB::704.0 is Real with tid mB');
    t(!o.isBaseTid(), 'non-base tid is not a base tid');
}
eq(write(parse('int{42}::7')), '{42}7', 'base tid with a point is elided to {point}value');
eq(write(parse('real::2.12')), '2.12', 'base real tid elided');
eq(write(parse('bool::true')), 'true', 'base bool tid elided');
eq(write(parse("str::'round;trip'")), "'round;trip'", 'base str tid elided');
eq(write(parse('uri::a/b/c')), 'a/b/c', 'base uri tid elided');

/* ---- tid::value@vid with and without vid ---- */
{
    const o = parse('nat::29@/usr/marko/age');
    t(o.isInt() && o.tid.toString() === 'nat' && o.vid.toString() === '/usr/marko/age', 'tid::value@vid');
    t(o.hasVID(), 'hasVID true');
}
{
    const o = parse('mB::704.0');
    t(!o.hasVID(), 'no vid -> hasVID false');
}
exact('nat::29@/usr/marko/age', 'tid::value@vid spelling');

/* ---- uri bracketing ---- */
exact('<>', 'empty uri');
exact('<{2}>', 'a uri that only counts');
exact('<{2}/a/b>', 'a value leading with its span');
exact('<a b/c>', 'a space forces the wrap');
exact('<+/x>', 'a leading wildcard is wrapped');
exact('<#/multi>', 'a leading # is wrapped');
exact('<a.b.c>', 'a dot is wrapped');
exact('<user@mail>', 'an @ is wrapped');
exact('<ws://user@host:8555/mtron>', 'user-info authority wrapped');
exact('a/b/c', 'a plain uri stays bare');
exact('a/b/c{3}', 'a uri trailing point stays bare');
exact('<user@mail>@abc', 'a wrapped value carries its vid outside the brackets');

/* ---- lst / rec / rel / objs ---- */
exact('[1,2,3]', 'lst');
exact('[,]', 'empty lst');
exact('[a=>1,b=>2]', 'rec');
exact('[=>]', 'empty rec');
exact('a=>b', 'rel');
exact('(a=>b)=>c', 'nested rel');
exact('{1,2,3}', 'objs');
exact('{true,false,{1,0},{-100,12.35,-12.35}}', 'objs of mixed values');
eq(write(parse('{,}')), 'noobj', 'empty objs is noobj');

/* ---- types ---- */
{
    const o = parse('uri::T');
    t(o.isType() && o.tid.toString() === 'uri' && !o.hasVID(), 'base type');
    t(!o.hasPredicate() && !o.hasConstructor(), 'no predicate/constructor');
}
{
    const o = parse('int::T[?>0]');
    t(o.isType() && o.hasPredicate() && !o.isIsaPredicate(), 'raw predicate');
    t(o.predicate().isCode() && o.predicate().js === '?>0', 'predicate is the raw code');
}
{
    const o = parse('rec::T[?[name=>str::T,age=>int::T]]@fox');
    t(o.isIsaPredicate(), 'isa predicate detected');
    t(o.vid.toString() === 'fox' && o.tid.toString() === 'rec', 'tid refines rec, vid names fox');
    const po = o.isPredicateObj();
    t(po != null && po.isRec() && po.js.size === 2, 'isPredicateObj is the field rec');
    t([...po.js.keys()].map((k) => k.toString()).join(',') === 'name,age', 'field keys name,age');
}
exact('int::T', 'base type spelling');
exact('uri::T', 'uri base type spelling');
exact('int::T[?>0]', 'refinement predicate spelling');
exact('rec::T[?[name=>str::T,age=>int::T]]@fox', 'isa predicate spelling');
exact('{2}int{3}::T', 'a type that leads with its span');
exact('lst[{3}int{2}]::T', 'a slot run span lives in the poly');
exact('int::T[][plus(1)]', 'constructor with the [] separator');
exact('int::T[?>0][plus(1)]', 'predicate then constructor');

/* ---- regression: a `::` inside a nested isa-predicate rec must not leak past
   the value's own closing bracket into the next list element ---- */
for (const s of [
    '[/m/space::T[?[host=>uri::T]],nat::T]',
    '[/m/space::T[?[host=>uri::T,driver=>uri::T]]@/m/db/tble/space/tblespace,nat::T]',
]) {
    const o = parse(s);
    t(o.isLst() && o.js.length === 2, 'two-element lst: ' + s);
    t(o.isLst() && o.js[0].isType() && o.js[0].isIsaPredicate(), 'first is a type with an isa predicate: ' + s);
    t(o.isLst() && o.js[1].isType() && o.js[1].tid.toString() === 'nat', 'second is nat::T: ' + s);
    eq(write(o), s, 'byte-identical round-trip: ' + s);
}

/* ---- regression: the `<=` arrow must not be read as an angle-uri opener, so a
   constructor carrying `<=` and a `{…}` body stays inside its list element ---- */
for (const s of [
    '[/m/space::T[ctor?x],int::T]',
    '[/m/space::T[ctor?x<=(y)],int::T]',
    '[/m/space::T[ctor?x{<j>}],int::T]',
    '[/m/space::T[ctor?x<=(y){<j>}],int::T]',
]) {
    const o = parse(s);
    t(o.isLst() && o.js.length === 2, 'two-element lst (<= arrow): ' + s);
    t(o.isLst() && o.js[0].isType() && o.js[0].hasPredicate(), 'first is a type with a predicate body: ' + s);
    t(o.isLst() && o.js[1].isType() && o.js[1].tid.toString() === 'int', 'second is int::T: ' + s);
    eq(write(o), s, 'byte-identical round-trip (<= arrow): ' + s);
}

/* ---- instruction signatures ---- */
{
    const o = parse('plus(1,2)');
    t(o.isInst() && o.name().toString() === 'plus', 'inst name');
    t(o.args().isLst() && o.args().js.length === 2, 'two args');
    t(o.arg(0).isInt() && o.arg(0).js === 1 && o.arg(1).js === 2, 'arg(0)/arg(1)');
}
{
    const o = parse('as?/m/bool<=/m/bool(bool::T)');
    t(o.name().toString() === 'as', 'typed inst name');
    t(o.rng().toString() === '/m/bool' && o.dom().toString() === '/m/bool', 'rng and dom');
    t(o.arg(0).isType() && o.arg(0).tid.toString() === 'bool', 'a type argument');
}
exact('plus(1,2)', 'inst spelling');
exact('as?/m/bool<=/m/bool(bool::T)', 'rng<=dom inst spelling');
exact('*x', 'from sugar');
exact('!x', 'auto sugar');
exact('!*x', 'auto_from sugar');
exact('!@x', 'auto_at sugar');
exact('plus(1){f}', 'inst body');
exact('{0,4}plus(1)', 'spanned inst keeps its span');
exact('start(0).plus(2)', 'a code chain');

/* ---- round-trip on a diverse set (parse(write(o)) ≡ o) ---- */
for (const s of [
    '29', '-100', '12.345', '704.0', 'true', 'false', 'noobj', '0x0a0b',
    "'a simple string'", '"a \'quoted\' word"', '"""line one\nline two"""',
    'a/b/c', '<a.b.c>', '<user@mail>', '<{2}/a/b>', '<>',
    'nat::29@/usr/marko/age', 'mB::704.0',
    '[1,2,3]', '[,]', '[a=>1,b=>2]', '[=>]', 'a=>b', '(a=>b)=>c', '{1,2,3}',
    '{3}5{3}', '5{3}', '{2}2.5{2}', '0x0102{2}',
    'int::T', 'uri::T', 'int::T[?>0]', 'rec::T[?[name=>str::T,age=>int::T]]@fox',
    '{2}int{3}::T', 'lst[{3}int{2}]::T',
    'plus(1,2)', 'as?/m/bool<=/m/bool(bool::T)', '*x', '!x', '!*x', '!@x',
    'plus(1){f}', 'start(0).plus(2)', 'rec::[op=>plus(2).mult(7),n=>3]',
]) rt(s, 'round-trip ' + s);

/* ---- string quoting: every shape survives write()->read() ---- */
for (const [lit, val] of [
    ["''", ''],
    ["'a'", 'a'],
    ["' '", ' '],
    ["'  x  '", '  x  '],
    ["'a b c'", 'a b c'],
    ['"""abc"""', 'abc'],
    ['"it\'s"', "it's"],
    ["'say \"hi\"'", 'say "hi"'],
    ['"""it\'s a "quoted" word"""', 'it\'s a "quoted" word'],
    ["'\"\"\"'", '"""'],
    ["'a\"\"\"b'", 'a"""b'],
    ["'ends in a quote\"'", 'ends in a quote"'],
    ["'\\\\'", '\\'],
    ["'a\\\\b'", 'a\\b'],
    ["'\\\\s'", '\\s'],
    ["'trailing\\\\'", 'trailing\\'],
]) {
    const o = parse(lit);
    const w = write(o);
    const back = parse(w);
    t(o.isStr() && back.isStr() && back.js === val, 'str round-trip ' + lit + ' => ' + w);
}

/* ---- the writer helpers stay exported for plain-JS callers ---- */
eq(real(12.0), '12.0', 'real helper keeps .0');
eq(int(12.9), '12', 'int helper truncates');
eq(quote('hello'), "'hello'", 'quote helper');
eq(quote("a'b"), '"a\'b"', 'quote helper with a single quote');

/* ---- built objs serialize structurally ---- */
eq(write(new Int(29, f('int'), null)), '29', 'write Int');
eq(write(new Int(29, f('nat'), f('/usr/marko/age'))), 'nat::29@/usr/marko/age', 'write typed Int');
eq(write(new Real(704, f('mB'), null)), 'mB::704.0', 'write Real keeps .0');
eq(write(new Str("a'b", f('str'), null)), '"a\'b"', 'write Str');
eq(write(new Uri(f('/a/b'), f('uri'), null)), '/a/b', 'write Uri (plain path stays bare)');
eq(write(new Uri(f('a.b.c'), f('uri'), null)), '<a.b.c>', 'write Uri (a dot forces the wrap)');
eq(write(new Lst([], f('lst'), null)), '[,]', 'write empty Lst');
eq(write(new Rec(new Map(), f('rec'), null)), '[=>]', 'write empty Rec');
{
    const rec = new Rec(new Map([[f('a'), new Int(1, f('int'), null)]]), f('rec'), null);
    eq(write(rec), '[a=>1]', 'write Rec');
    t(parse(write(rec)).isRec(), 'built rec round-trips');
}

});
