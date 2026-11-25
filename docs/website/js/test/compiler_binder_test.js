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
import { binderRows } from '../ui/mtron-obj-panels.js';

/* the binder works with a Map<fURI,fURI>: an all-caps generic bound to the type
   it met. the compiler panel renders whatever the component publishes at
   ~/compiler/resolver/binder; these are the shapes that read may answer. */
test('binder rows — a rec of generic => type becomes one row each', () => {
    assert.deepEqual(binderRows('[A=>int,B=>str]'), [
        {key: 'A', value: 'int::T'},
        {key: 'B', value: 'str::T'}
    ]);
});

test('binder rows — a type-valuing rec keeps the type as written', () => {
    const rows = binderRows(`generic_binder::[A=>int,B=>lst]`);
    assert.deepEqual(rows.map((r) => r.key), ['A', 'B']);
    assert.equal(rows[0].value, 'int::T');
    assert.equal(rows[1].value, 'lst::T');
});

test('binder rows — the empty binder (what the machine publishes today) answers no rows', () => {
    assert.deepEqual(binderRows('generic_binder::[=>]'), []);
    assert.deepEqual(binderRows('noobj'), []);
    assert.deepEqual(binderRows(''), []);
    assert.deepEqual(binderRows(null), []);
});

test('binder rows — an answer that does not parse answers no rows rather than throwing', () => {
    assert.deepEqual(binderRows('[A=>'), []);
});
