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
import { fURI } from './furi.js';

/*
 * PlatonicObj — the foundation of metatron. There are objs.
 * An obj's form is grounded in JavaScript (js). It has a reference to its type
 * (tid) and a reference to its location (vid). It is the triple (js, tid, vid),
 * aligned with Java's PlatonicObj { jvm(), tid(), vid() }.
 *
 *   Int  -> js:number        Bool -> js:boolean    Str -> js:string
 *   Real -> js:number        Bytes-> js:Uint8Array Uri -> js:fURI
 *   Lst  -> js:Array<Obj>    Rec  -> js:Map<fURI,Obj>  Rel -> js:[Obj,Obj]
 */
class PlatonicObj {
    constructor(js, tid, vid) {
        this.js = js;
        this.tid = tid;   // fURI — the type
        this.vid = vid;   // fURI — the location
    }

    /* ── type predicates ── */
    isNoObj() { return false; }
    isBool() { return false; }
    isBytes() { return false; }
    isInt() { return false; }
    isReal() { return false; }
    isStr() { return false; }
    isUri() { return false; }
    isLst() { return false; }
    isRec() { return false; }
    isRel() { return false; }
    isFail() { return false; }
    isType() { return false; }
    isInst() { return false; }
    isCode() { return false; }
    isObjs() { return false; }

    /* ── casts ──
       mono types answer their native js; poly types answer themselves (the Obj),
       matching Java's asInt() -> long but asRec() -> Rec. */
    asBool() { return this.js; }
    asBytes() { return this.js; }
    asInt() { return this.js; }
    asReal() { return this.js; }
    asStr() { return this.js; }
    asUri() { return this.js; }
    asLst() { return this; }
    asRec() { return this; }
    asRel() { return this; }

    /* ── stream: one or many, the same code ──
       a single obj yields itself; an objs yields its members. */
    *stream() { yield this; }

    /* every obj is a function that maps an obj to an obj: this(a) -> b */
    apply(lhs) { return this; }

    toString() { return Obj.render(this); }
}

class NoObj extends PlatonicObj {
    constructor() { super(null, fURI.NOOBJ, null); }
    isNoObj() { return true; }
    *stream() { /* the empty stream */ }
}

class Bool extends PlatonicObj { isBool() { return true; } }
class Bytes extends PlatonicObj { isBytes() { return true; } }
class Int extends PlatonicObj { isInt() { return true; } }
class Real extends PlatonicObj { isReal() { return true; } }
class Str extends PlatonicObj { isStr() { return true; } }
class Uri extends PlatonicObj { isUri() { return true; } }
class Lst extends PlatonicObj { isLst() { return true; } }
class Rec extends PlatonicObj { isRec() { return true; } }
class Rel extends PlatonicObj { isRel() { return true; } }
class Fail extends PlatonicObj { isFail() { return true; } }
class Type extends PlatonicObj { isType() { return true; } }
class Inst extends PlatonicObj { isInst() { return true; } }
class Code extends PlatonicObj { isCode() { return true; } }

/* Objs — a multiplicity (int{4}::T in the language). js is an array of objs and
   stream() yields the members, so one code path covers one and many. */
class Objs extends Lst {
    isObjs() { return true; }
    *stream() { yield* this.js; }
}

const noobj = new NoObj();

/* a single-value stream collapses to the value itself; an empty one to noobj */
function of(objs) {
    if (!objs || objs.length === 0) return noobj;
    if (objs.length === 1) return objs[0];
    return new Objs(objs, null, null);
}

/* the serializer lives beside this file; a placeholder keeps toString() honest */
const Obj = {
    render(obj) {
        if (obj == null) return 'noobj';
        if (obj.isNoObj()) return 'noobj';
        if (obj.isBool()) return String(obj.js);
        if (obj.isInt()) return String(obj.js);
        if (obj.isReal()) return String(obj.js);
        if (obj.isStr()) return obj.js;
        if (obj.isUri()) return obj.js.toString();
        if (obj.isFail()) return 'fail::' + obj.js;
        return (obj.tid ? obj.tid.toString() : 'obj') + '@' + (obj.vid ? obj.vid.toString() : '');
    }
};

/* base tids render bare (no `tid::` prefix, no clickable type) */
const BASE_TID = new Set(['int', 'real', 'str', 'bool', 'bytes', 'uri', 'rec', 'lst', 'rel', 'fail', 'type', 'inst', 'code', 'objs']);

/* render a PlatonicObj as clickable DOM. every fURI — a Uri value, a rec key, a
   vid, a non-base tid — becomes a transparent button (fURI.toElement), so the
   click/pin/shift-?docq behavior is the fURI's, once, not each widget's. */
function clickable(parent, uri, open) {
    const b = uri.toElement(open);
    b.textContent = uri.toString();
    parent.appendChild(b);
}

function renderInto(parent, obj, open) {
    const t = (s) => parent.appendChild(document.createTextNode(s));
    if (obj == null || obj.isNoObj()) { t('noobj'); return; }

    const tn = obj.tid ? obj.tid.name() : null;
    if (obj.tid && tn && !BASE_TID.has(tn)) { clickable(parent, obj.tid, open); t('::'); }

    if (obj.isBool()) t(obj.js ? 'true' : 'false');
    else if (obj.isInt()) t(String(obj.js));
    else if (obj.isReal()) t(String(obj.js));
    else if (obj.isStr()) t("'" + String(obj.js).replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'");
    else if (obj.isBytes()) t('0x' + Array.from(obj.js).map((b) => b.toString(16).padStart(2, '0')).join(''));
    else if (obj.isUri()) { const b = obj.js.toElement(open); b.textContent = '<' + obj.js.toString() + '>'; parent.appendChild(b); }
    else if (obj.isRel()) { renderInto(parent, obj.js[0], open); t('=>'); renderInto(parent, obj.js[1], open); }
    else if (obj.isLst()) { t('['); obj.js.forEach((x, i) => { if (i) t(','); renderInto(parent, x, open); }); t(']'); }
    else if (obj.isRec()) {
        t('[');
        let i = 0;
        for (const [k, v] of obj.js) { if (i++) t(','); clickable(parent, k, open); t('=>'); renderInto(parent, v, open); }
        t(']');
    }
    else if (obj.isFail()) t('fail::' + obj.js);
    else t(String(obj.js));

    if (obj.vid) { t('@'); clickable(parent, obj.vid, open); }
}

function render(obj, open) {
    const frag = document.createDocumentFragment();
    renderInto(frag, obj, open);
    return frag;
}

export {
    PlatonicObj, NoObj, Bool, Bytes, Int, Real, Str, Uri, Lst, Rec, Rel,
    Fail, Type, Inst, Code, Objs, noobj, of, BASE_TID, render
};
export default PlatonicObj;
