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
import { parse } from '../sdk/obj_mtron_serializer.js';
import {
    explanationModel, topologyModel,
    registerExplainExtension, explainExtension, explainExtensionNames
} from '../ui/explain-flow.js';

/* the fixtures are REAL answers, captured from a live machine on 2026-11-24:
   `1.plus(2).explain()` and, with two peers dialed,
   `start?hop=2(0).{0,10}plus(1).sum()` with its topology extension. */
const PLAIN = `/m/inst/explain/explanation::[format=>!λ?/m/str<=(),desc=>[rng=>int,insts=>2,rewrite=>/m/inst/explain/explanation::[format=>!λ?/m/str<=(),desc=>[rng=>int,insts=>2],per_inst=>[[op=>start,rng=>int,args=>[1],f=>'<j>',form=>initial,c_dom=>0,c_rng=>1,c_out=>1],[op=>plus,dom=>int,rng=>int,args=>[2],f=>'<j>',form=>mapper,c_dom=>1,c_rng=>1,c_out=>1]]]],per_inst=>[[op=>start,rng=>int,args=>[1],f=>'<j>',form=>initial,c_dom=>0,c_rng=>1,c_out=>1],[op=>plus,dom=>int,rng=>int,args=>[2],f=>'<j>',form=>mapper,c_dom=>1,c_rng=>1,c_out=>1]]]`;

const DISTRIBUTED = `[[op=>branch,args=>[/m/mach/inst/cut(ws://localhost:1111?hop=1,/m/inst/start(0).{0,6}/m/inst/plus(1)./m/inst/sum()),/m/mach/inst/cut(ws://localhost:1112?hop=1,/m/inst/start(0).{7,10}/m/inst/plus(1)./m/inst/sum())],f=>'<j>',form=>standard,c_dom=>0,c_rng=>0,c_out=>0],[op=>sum,rng=>int,args=>[,],f=>'<j>',form=>reducer,c_dom=>0,c_rng=>1,c_out=>1]]`;

const TOPOLOGY = `[flow=>[['{1}'],[[place=>ws://localhost:1111,stage=>['{1}','{7}','{1}'],topology=>[flow=>[['{1}'],[[place=>ws://localhost:1113,stage=>['{1}','{5}','{1}']],[place=>ws://localhost:1114,stage=>['{1}','{2}','{1}']]],['{2}','{1}']],format=>!/m/inst()]],[place=>ws://localhost:1112,stage=>['{1}','{4}','{1}'],topology=>[flow=>[['{1}'],[[place=>ws://localhost:1115,stage=>['{1}','{3}','{1}']],[place=>ws://localhost:1116,stage=>['{1}','{1}','{1}']]],['{2}','{1}']],format=>!/m/inst()]]],['{2}','{1}']],format=>!/m/inst()]`;

const modelOf = (text) => explanationModel(parse(text));

test('explanation table — the recorded per_inst becomes the rows', () => {
    const model = modelOf(PLAIN);
    assert.equal(model.desc.rng, 'int::T');
    assert.equal(model.desc.dom, null);          // start has no dom, so the key is omitted
    assert.equal(model.desc.insts, 2);
    assert.equal(model.rows.length, 2);
    assert.deepEqual(model.rows[0], {
        op: 'start', dom: null, rng: 'int::T', args: '[1]', f: '<j>', form: 'initial',
        cDom: '{0}', cRng: '{1}', cOut: '{1}'
    });
    assert.equal(model.rows[1].dom, 'int::T');
    assert.equal(model.rows[1].form, 'mapper');
    assert.equal(model.rows[1].cOut, '{1}');
    assert.deepEqual(model.ext, []);
});

test('explanation table — a recorded no-object type reads as a type, never a dash', () => {
    /* the shape current machines send: dom recorded even when its least count is 0,
       so a literal start's dom is the no-object type (Marko, 2026-11-25) */
    const model = modelOf(`/m/inst/explain/explanation::[per_inst=>[[op=>start,dom=>noobj,rng=>int,args=>[1],f=>'<j>',form=>initial,c_dom=>0,c_rng=>1,c_out=>1],[op=>sum,dom=>int,rng=>int,args=>[,],f=>'<j>',form=>reducer,c_dom=>0,c_rng=>1,c_out=>1]]]`);
    assert.equal(model.rows[0].dom, 'noobj{0}::T');
    assert.equal(model.rows[0].rng, 'int::T');
    assert.equal(model.rows[1].dom, 'int::T');
});

test('explanation table — the submitted chain is its own model, and it terminates', () => {
    const model = modelOf(PLAIN);
    assert.ok(model.submitted);
    assert.equal(model.submitted.rows.length, 2);
    assert.equal(model.submitted.rows[1].op, 'plus');
    assert.equal(model.submitted.submitted, null);
});

test('explanation table — a distributed chain keeps its cut args and records no type', () => {
    const model = modelOf('[per_inst=>' + DISTRIBUTED + ']');
    assert.equal(model.rows.length, 2);
    assert.equal(model.rows[0].op, 'branch');
    assert.equal(model.rows[0].dom, null);       // the key is dropped; the panel renders noobj{0}::T for it
    assert.equal(model.rows[0].rng, null);
    assert.equal(model.rows[0].form, 'standard');
    assert.ok(model.rows[0].args.includes('cut(ws://localhost:1111?hop=1'), model.rows[0].args);
    assert.equal(model.rows[1].op, 'sum');
    assert.equal(model.rows[1].form, 'reducer');
    assert.equal(model.rows[1].cDom, '{0}');     // the LEAST COUNT, not the drawn {*}
});

test('explanation table — a coefficient the machine cannot bound is absent, never zero', () => {
    const model = modelOf('[per_inst=>[[op=>plus,dom=>int,rng=>int,args=>[1]]]]');
    assert.equal(model.rows[0].cDom, null);
    assert.equal(model.rows[0].cRng, null);
    assert.equal(model.rows[0].cOut, null);      // c_out is READ, never recomputed
});

test('topology — runs and fans, with a nested plan inside each lane', () => {
    const model = topologyModel(parse(TOPOLOGY));
    assert.deepEqual(model.segments.map((s) => s.kind), ['run', 'fan', 'run']);
    assert.deepEqual(model.segments[0].labels, ['{1}']);
    assert.deepEqual(model.segments[2].labels, ['{2}', '{1}']);

    const lanes = model.segments[1].lanes;
    assert.equal(lanes.length, 2);
    assert.equal(lanes[0].place, 'ws://localhost:1111');
    assert.deepEqual(lanes[0].stage, ['{1}', '{7}', '{1}']);
    assert.ok(lanes[0].topology);
    assert.deepEqual(lanes[0].topology.segments.map((s) => s.kind), ['run', 'fan', 'run']);
    assert.deepEqual(lanes[0].topology.segments[1].lanes.map((l) => l.place),
        ['ws://localhost:1113', 'ws://localhost:1114']);
    assert.equal(lanes[1].place, 'ws://localhost:1112');
    assert.deepEqual(lanes[1].topology.segments[1].lanes.map((l) => l.place),
        ['ws://localhost:1115', 'ws://localhost:1116']);
});

test('topology — a repeat is a loop segment holding its body', () => {
    const model = topologyModel(parse(`[flow=>[[loop=>[['{2}'],[[place=><~>,stage=>['{2}','{3}','{2}']]]]]],format=>!/m/inst()]`));
    assert.deepEqual(model.segments.map((s) => s.kind), ['loop']);
    assert.deepEqual(model.segments[0].flow.segments.map((s) => s.kind), ['run', 'fan']);
    assert.equal(model.segments[0].flow.segments[1].lanes[0].place, '~');
});

test('topology — a rec with no flow has no model', () => {
    assert.equal(topologyModel(parse('[format=>!/m/inst()]')), null);
    assert.equal(topologyModel(null), null);
});

test('extensions — both spellings of the key name the same extension', () => {
    const nested = modelOf(`/m/inst/explain/explanation::[per_inst=>[],ext=>[topology=>[flow=>[],format=>!/m/inst()]]]`);
    assert.deepEqual(nested.ext.map((e) => e.name), ['topology']);
    const flat = modelOf(`/m/inst/explain/explanation::[per_inst=>[],ext/topology=>[flow=>[],format=>!/m/inst()]]`);
    assert.deepEqual(flat.ext.map((e) => e.name), ['topology']);
});

test('extensions — a renderer is found by name, an unknown one answers null', () => {
    const demo = () => 'x';
    registerExplainExtension('demo', demo);
    assert.equal(explainExtension('demo'), demo);
    assert.equal(explainExtension('never-registered'), null);
    assert.ok(explainExtensionNames().includes('topology'));
});
