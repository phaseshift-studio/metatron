/*
 * explain-flow — an explain() EXTENSION as data, and the widget that draws it.
 *
 * explain() answers an explanation::T: [format, desc, per_inst, ext?]. `per_inst`
 * is the COMPILED chain (the table the panel renders), `desc.rewrite` is the
 * explanation of what the user SUBMITTED, and each extension writes its own rec
 * beside them — a rewrite registered next to explain_analysis puts it at
 * `ext/<name>`, so `x.explain()>>ext/topology` is that extension's view.
 *
 * The panel for the explanation type renders the table and hands every ext rec to
 * a renderer registered here. A NEW extension is therefore a registration, not a
 * change to the panel: registerExplainExtension(name, (rec, api) => Element).
 *
 * Today there is one extension: topology — the distribution plan's flow. Its rec
 * is [flow => <segments>, format => <an inst>], the segments in chain order:
 *
 *   run   ['{1}']                                  one place's run
 *   fan   [[place=>ws://localhost:1111,            the lanes a branch splits into
 *           stage=>['{1}','{7}','{1}'],
 *           topology=><that machine's own flow>]]
 *   loop  [loop => <inner flow>]                   a repeat, drawn once + a back edge
 *
 * `format` arrives as an INST, not a string, so the drawing is never read: as
 * Topology.java says, the topology is data and its drawing is derived — this
 * module is the derivation. The model functions are pure (no DOM) so node:test
 * holds them; the widget is the only browser part.
 */
import { write } from '../sdk/obj_mtron_serializer.js';

/* one field of a rec by key name — a rec's keys are fURIs, so compare the path */
function field(rec, name) {
    if (!rec || !rec.isRec || !rec.isRec()) return null;
    for (const [k, v] of rec.js) {
        const s = k.toString();
        if (s === name || (k.name && k.name() === name)) return v;
    }
    return null;
}

/* an obj as the text a cell shows: a str unquoted, a uri by its address, anything
   else (an inst, a code, a lst of them) through the serializer */
function text(obj) {
    if (obj == null || obj.isNoObj()) return '';
    if (obj.isStr()) return obj.js;
    if (obj.isUri()) return obj.js.toString();
    return write(obj);
}

/* a type field as the table spells it, or null when the machine did not RECORD it.
   absent is NOT the same as noobj: a present no-object type is a type (a literal
   start's dom) and reads noobj::T — the machine records it as the uri `noobj` or as
   the empty obj, and both are the same answer. */
function typeName(obj) {
    if (obj == null) return null;          // the key is absent: an older answer
    if (obj.isNoObj()) return 'noobj::T';  // present, and the type IS no-object
    if (!obj.isUri()) return null;
    return obj.js.toString() + '::T';
}

/* a coefficient as the rec records it — a LEAST COUNT, not the drawn {*}/{n,m} */
function coef(obj) {
    if (obj == null || obj.isNoObj()) return null;
    if (obj.isInt()) return '{' + obj.js + '}';
    if (obj.isStr()) return obj.js;
    return text(obj);
}

function intOf(obj) {
    return obj && obj.isInt() ? Number(obj.js) : null;
}

/* the extension recs of an explanation, however the machine spelled the key: one
   key `ext/topology` (what the rewrite writes) or a nested rec `ext => [topology
   => …]` */
function extensionsOf(rec) {
    const out = [];
    for (const [k, v] of rec.js) {
        const parts = String(k).split('/').filter(Boolean);
        if (parts[0] !== 'ext') continue;
        if (parts.length > 1) {
            out.push({name: parts[parts.length - 1], rec: v});
        } else if (v && v.isRec && v.isRec()) {
            for (const [k2, v2] of v.js) out.push({name: String(k2).split('/').pop(), rec: v2});
        }
    }
    return out;
}

/**
 * An explanation::T as the panel consumes it: the expression-level desc, the rows
 * of the compiled chain, the submitted chain's own model (when the machine wired
 * one), and the extension recs.
 */
function explanationModel(explanation) {
    if (!explanation || !explanation.isRec || !explanation.isRec()) return null;
    const desc = field(explanation, 'desc');
    const perInst = field(explanation, 'per_inst');
    if (!perInst || !perInst.isLst()) return null;
    const rows = perInst.js.map((row) => ({
        op: text(field(row, 'op')),
        dom: typeName(field(row, 'dom')),
        rng: typeName(field(row, 'rng')),
        args: text(field(row, 'args')),
        f: text(field(row, 'f')),
        form: text(field(row, 'form')),
        cDom: coef(field(row, 'c_dom')),
        cRng: coef(field(row, 'c_rng')),
        cOut: coef(field(row, 'c_out'))
    }));
    const submittedRec = desc ? field(desc, 'rewrite') : null;
    const submitted = submittedRec && submittedRec.isRec() && field(submittedRec, 'per_inst')
        ? explanationModel(submittedRec) : null;
    return {
        desc: {
            dom: desc ? typeName(field(desc, 'dom')) : null,
            rng: desc ? typeName(field(desc, 'rng')) : null,
            insts: desc ? intOf(field(desc, 'insts')) : rows.length
        },
        rows: rows,
        submitted: submitted,
        ext: extensionsOf(explanation)
    };
}

/** A topology::T rec's flow as segments; null for a rec that carries no flow. */
function topologyModel(topology) {
    if (!topology || !topology.isRec || !topology.isRec()) return null;
    return flowModel(field(topology, 'flow'));
}

function flowModel(flow) {
    if (!flow || !flow.isLst()) return null;
    const segments = [];
    flow.js.forEach((segment) => {
        if (!segment) return;
        /* a repeat: its body walked as a flow of its own, drawn once + a back edge */
        if (segment.isRec()) {
            const inner = flowModel(field(segment, 'loop'));
            if (inner) segments.push({kind: 'loop', flow: inner});
            return;
        }
        if (!segment.isLst()) return;
        const parts = segment.js;
        /* a fan: a lst of lane recs; an empty one is a branch keyed by nothing */
        if (!parts.length) {
            segments.push({kind: 'fan', lanes: []});
            return;
        }
        if (parts[0].isRec()) {
            segments.push({
                kind: 'fan',
                lanes: parts.map((lane) => {
                    const stage = field(lane, 'stage');
                    const nested = field(lane, 'topology');
                    return {
                        place: text(field(lane, 'place')),
                        stage: stage && stage.isLst() ? stage.js.map((s) => text(s)) : [],
                        topology: nested && nested.isRec() ? topologyModel(nested) : null
                    };
                })
            });
            return;
        }
        /* a run: one place's coefficients */
        segments.push({kind: 'run', labels: parts.map((s) => text(s))});
    });
    return {segments: segments};
}

/* ── the extension registry ──────────────────────────────────────────────
   the panel asks for a renderer by the extension's name; an unregistered one
   still shows its data (the panel falls back to the rec's own text). */
const EXPLAIN_EXTENSIONS = new Map();

function registerExplainExtension(name, factory) {
    EXPLAIN_EXTENSIONS.set(String(name), factory);
    return factory;
}

function explainExtension(name) {
    return EXPLAIN_EXTENSIONS.get(String(name)) || null;
}

function explainExtensionNames() {
    return Array.from(EXPLAIN_EXTENSIONS.keys());
}

/* ── the widget ───────────────────────────────────────────────────────── */

function el(tag, cls, body) {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (body != null) node.textContent = body;
    return node;
}

/* a place as the drawing names it: this machine reads //local, a peer reads by the
   address the plan wrote (the network's own name for a peer is the server's to
   give, so the authority stands in for it) */
function placeLabel(place) {
    if (!place || place === '~') return '//local';
    const authority = /^[a-z]+:\/\/([^/?#]+)/i.exec(place);
    return '//' + (authority ? authority[1] : String(place).replace(/^\/+/, ''));
}

function runElement(segment) {
    const run = el('div', 'ef-run');
    if (!segment.labels.length) run.appendChild(el('span', 'ef-coef ef-none', '—'));
    segment.labels.forEach((label) => run.appendChild(el('span', 'ef-coef', label)));
    return run;
}

function fanElement(segment, api, depth, inspector) {
    const fan = el('div', 'ef-fan');
    if (!segment.lanes.length) {
        fan.appendChild(el('div', 'ef-lane ef-lane-empty', 'no lanes'));
        return fan;
    }
    segment.lanes.forEach((lane) => fan.appendChild(laneElement(lane, api, depth, inspector)));
    return fan;
}

function laneElement(lane, api, depth, inspector) {
    const row = el('div', 'ef-lane');
    const body = el('div', 'ef-lane-body');
    const place = document.createElement('button');
    place.type = 'button';
    place.className = 'ef-place' + (lane.place === '~' || !lane.place ? ' ef-home' : '');
    place.textContent = placeLabel(lane.place);
    place.title = (lane.place === '~' || !lane.place ? 'this machine\'s own place' : lane.place)
        + ' · click to trace this lane';
    body.appendChild(place);
    const stage = el('div', 'ef-stage');
    lane.stage.forEach((label) => stage.appendChild(el('span', 'ef-coef', label)));
    body.appendChild(stage);
    if (lane.topology) body.appendChild(nestedElement(lane.topology, api, depth, inspector));
    /* a home lane is this machine: its place opens the machine's own card */
    if ((lane.place === '~' || !lane.place) && api && api.open) {
        const home = document.createElement('button');
        home.type = 'button';
        home.className = 'ef-open';
        home.textContent = '⌂';
        home.title = 'open this machine\'s card';
        home.addEventListener('click', (ev) => {
            ev.stopPropagation();
            api.open({tid: null, vid: '/.', raw: '/.', shortTid: 'machine'});
        });
        body.appendChild(home);
    }
    row.appendChild(body);
    row.addEventListener('click', (ev) => {
        ev.stopPropagation();
        selectLane(row, lane, inspector);
    });
    return row;
}

/* a lane that re-plans where it runs: that machine's own topology, folded in */
function nestedElement(topology, api, depth, inspector) {
    const sub = el('div', 'ef-sub');
    if (depth >= 8) {
        sub.appendChild(el('span', 'ef-none', '…'));
        return sub;
    }
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'ef-toggle';
    toggle.textContent = '▾';
    toggle.title = 'this machine\'s own topology';
    const body = el('div', 'ef-sub-body');
    body.appendChild(flowElement(topology, api, depth + 1, inspector));
    toggle.addEventListener('click', (ev) => {
        ev.stopPropagation();
        body.hidden = !body.hidden;
        toggle.textContent = body.hidden ? '▸' : '▾';
    });
    sub.appendChild(toggle);
    sub.appendChild(body);
    return sub;
}

function loopElement(segment, api, depth, inspector) {
    const box = el('div', 'ef-loop');
    const badge = el('div', 'ef-loop-badge');
    badge.appendChild(el('span', 'ef-loop-mark', '↺'));
    badge.appendChild(el('span', 'ef-loop-name', 'loop'));
    box.appendChild(badge);
    if (segment.flow) box.appendChild(flowElement(segment.flow, api, depth + 1, inspector));
    return box;
}

function flowElement(model, api, depth, inspector) {
    const flow = el('div', 'eflow' + (depth ? ' ef-nested' : ''));
    model.segments.forEach((segment) => {
        if (segment.kind === 'run') flow.appendChild(runElement(segment));
        else if (segment.kind === 'fan') flow.appendChild(fanElement(segment, api, depth, inspector));
        else flow.appendChild(loopElement(segment, api, depth, inspector));
    });
    return flow;
}

/* one lane traced at a time: the flow is a route table, so the selection is the
   reader's, never a fact about the machine */
function selectLane(row, lane, inspector) {
    const already = row.classList.contains('sel');
    const fan = row.parentElement;
    if (fan) Array.from(fan.children).forEach((sibling) => sibling.classList.remove('sel'));
    if (already) {
        if (inspector) inspector.textContent = 'click a lane to trace it';
        return;
    }
    row.classList.add('sel');
    if (!inspector) return;
    const route = lane.stage.length ? lane.stage.join(' → ') : '—';
    inspector.textContent = placeLabel(lane.place) + ' · ' + route
        + (lane.topology ? ' · re-plans on that machine' : '');
}

/** The topology extension as an interactive flow: runs, fans, nested plans, loops. */
function topologyWidget(topology, api) {
    const box = el('div', 'eflow-widget');
    const model = topologyModel(topology);
    if (!model || !model.segments.length) {
        box.appendChild(el('div', 'mpanel-empty', 'no flow'));
        return box;
    }
    const inspector = el('div', 'ef-inspect', 'click a lane to trace it');
    box.appendChild(flowElement(model, api || {}, 0, inspector));
    box.appendChild(inspector);
    return box;
}

/* ── the affordance: a `?` on a code::T ──────────────────────────────────
   every uri opens its card on click, so a code should open its EXPLANATION the
   same way. the difference is that a uri is self-describing in text (`<…>`,
   `tid::…@vid`) while a code's text carries no marker — `1.plus(2)` reads like
   any string — so this cannot be a lexical pass over rendered text the way
   annotateObjects is. It is applied by whoever HOLDS the code (a stage in the
   compiler panel, a code-valued cell, a renderer that is holding a Code obj),
   and it is the same call everywhere. */
function explainBadge(codeText, api) {
    const code = String(codeText == null ? '' : codeText).trim();
    if (!code || code === 'noobj' || code.indexOf('fail::') === 0) return null;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'code-ask';
    button.textContent = '?';
    button.title = 'explain this code';
    button.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        if (!api || !api.open) return;
        /* the panel reads env.expr, so it is handed the explain CHAIN; the field
           it seeds shows the expression (it strips the `.explain()`) */
        api.open({
            tid: '/m/inst/explain/explanation',
            vid: null,
            raw: '',
            shortTid: 'explanation',
            expr: code + '.explain()'
        });
    });
    return button;
}

registerExplainExtension('topology', topologyWidget);

export {
    explanationModel, topologyModel,
    registerExplainExtension, explainExtension, explainExtensionNames,
    topologyWidget, explainBadge
};
