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
import { f } from '../furi.js';
import { PlatonicObj, NoObj, Bool, Bytes, Int, Real, Str, Uri, Lst, Rec, Rel, Fail, noobj, of } from '../mtron-obj.js';

let pass = 0, fail = 0;
function eq(actual, expected, label) {
    if (actual === expected) pass++;
    else { fail++; console.log('FAIL', label, '\n  expected:', JSON.stringify(expected), '\n  actual:  ', JSON.stringify(actual)); }
}
function t(cond, label) { if (cond) pass++; else { fail++; console.log('FAIL', label); } }

/* ---- the triple (js, tid, vid) ---- */
const age = new Int(29, f('int'), f('/usr/marko/age'));
t(age.isInt(), 'Int isInt');
t(!age.isReal() && !age.isStr(), 'Int is only Int');
eq(age.asInt(), 29, 'Int asInt');
eq(age.tid.toString(), 'int', 'Int tid');
eq(age.vid.toString(), '/usr/marko/age', 'Int vid');

const name = new Str('code', f('str'), null);
t(name.isStr(), 'Str isStr');
eq(name.asStr(), 'code', 'Str asStr');

const b = new Bool(true, f('bool'), null);
t(b.isBool() && b.asBool() === true, 'Bool');

const r = new Real(3.5, f('real'), null);
t(r.isReal() && r.asReal() === 3.5, 'Real');

const bytes = new Bytes(Uint8Array.from([0xaf, 0x34]), f('bytes'), null);
t(bytes.isBytes(), 'Bytes');

const uri = new Uri(f('/usr/dr/concept/today'), f('uri'), null);
t(uri.isUri(), 'Uri isUri');
eq(uri.asUri().toString(), '/usr/dr/concept/today', 'Uri asUri (fURI)');

/* ---- poly types: js is the native structure ---- */
const lst = new Lst([age, name], f('lst'), null);
t(lst.isLst(), 'Lst isLst');
t(lst.asLst() === lst, 'Lst asLst returns the Obj');
eq(lst.js.length, 2, 'Lst js is an array');

const rec = new Rec(new Map([[f('name'), name]]), f('rec'), null);
t(rec.isRec(), 'Rec isRec');
t(rec.asRec() === rec, 'Rec asRec returns the Obj');
t(rec.js instanceof Map, 'Rec js is a Map');
eq([...rec.js.keys()][0].toString(), 'name', 'Rec key is an fURI');

const rel = new Rel([f('/a'), age], f('rel'), null);
t(rel.isRel(), 'Rel isRel');
eq(rel.js.length, 2, 'Rel js is a pair');

/* ---- specials ---- */
t(noobj.isNoObj(), 'noobj isNoObj');
eq([...noobj.stream()].length, 0, 'noobj stream is empty');
t(new Fail('boom', f('fail'), null).isFail(), 'Fail');

/* ---- stream: one or many, the same code ---- */
eq([...age.stream()].length, 1, 'single obj stream yields itself');
const objs = of([age, name, lst]);
t(objs.isObjs(), 'of(many) is an Objs');
eq([...objs.stream()].length, 3, 'Objs stream yields members');
t(of([age]) === age, 'of(one) collapses to the value');
t(of([]) === noobj, 'of(empty) is noobj');

/* ---- every obj is a PlatonicObj ---- */
t(age instanceof PlatonicObj && rec instanceof PlatonicObj && noobj instanceof PlatonicObj, 'all are PlatonicObj');

/* ---- render ---- */
eq(age.toString(), '29', 'render Int');
eq(name.toString(), 'code', 'render Str');
eq(noobj.toString(), 'noobj', 'render noobj');

console.log('\n' + pass + ' passed, ' + fail + ' failed');
process.exit(fail === 0 ? 0 : 1);
