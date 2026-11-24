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
import { parseInsts } from './obj_mtron_serializer.js';

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

    /* the instructions this call composes: a code's chain, a lone inst itself,
       nothing otherwise (mirrors Java Call.insts()) */
    insts() { return []; }

    /* every obj is a function that maps an obj to an obj: this(a) -> b */
    apply(lhs) { return this; }

    /* whether this obj carries a location (vid) */
    hasVID() { return this.vid != null; }

    /* whether this obj's tid is a base type (int, real, rec, …) — the writer
       elides a base tid, so a caller asks this instead of comparing strings */
    isBaseTid() {
        if (this.tid == null) return false;
        const n = this.tid.name();
        return n != null && n !== '' && BASE_TID.has(n);
    }

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
class Fail extends PlatonicObj {
    constructor(js, tid, vid, cause = null) {
        super(js, tid, vid);
        this.cause = cause;   // the next Fail in the cause chain, or null
    }
    isFail() { return true; }
}

/* Type — a type refines a base type (tid) under a name (vid). Its predicate and
   constructor travel as the pair (js = [predicate, constructor]), mirroring the
   Java jvm() = Pair<Call predicate, Call constructor>. A predicate is either an
   isa(rec) instruction (the `[?rec]` sugar) or a raw predicate code; the accessors
   below let a caller read tid/pred/ctor directly instead of regexing strings. */
class Type extends PlatonicObj {
    isType() { return true; }
    /* the predicate Call (an Inst for the isa form, a Code otherwise), or null */
    predicate() { return this.js[0]; }
    /* the constructor Call, or null */
    ctor() { return this.js[1]; }
    hasPredicate() { return this.js[0] != null && !this.js[0].isNoObj(); }
    hasConstructor() { return this.js[1] != null && !this.js[1].isNoObj(); }
    /* an isa predicate is a single-instruction call whose operation is `isa` */
    isIsaPredicate() {
        if (!this.hasPredicate()) return false;
        const p = this.js[0];
        if (!p.isInst()) return false;
        const n = p.name() ? p.name().toString() : '';
        return n === 'isa' || n === '/m/inst/isa';
    }
    /* the rec argument of an isa predicate (the field map), else null */
    isPredicateObj() {
        return this.isIsaPredicate() ? this.js[0].arg(0) : null;
    }
}

/* Inst — an instruction: name + typing (rng/dom) in the tid, the argument poly in
   js (a Lst or a Rec), and the body in _f (a Code, an Inst, or null). */
class Inst extends PlatonicObj {
    constructor(js, tid, vid, f = null) {
        super(js, tid, vid);
        this._f = f;
    }
    isInst() { return true; }
    /* the operation's address — the tid without its coefficient, query and poly */
    name() { return this.tid ? this.tid.basePath() : null; }
    rng() { return this.tid ? this.tid.rng() : null; }
    dom() { return this.tid ? this.tid.dom() : null; }
    args() { return this.js; }
    arg(i) {
        const a = this.js;
        if (a == null) return noobj;
        if (a.isLst()) return a.js.length > i ? a.js[i] : noobj;
        if (a.isRec()) {
            const vals = [...a.js.values()];
            return vals.length > i ? vals[i] : noobj;
        }
        return noobj;
    }
    /* the body (mtron code or another instruction), or null */
    f() { return this._f; }
    insts() { return [this]; }
}
class Code extends PlatonicObj {
    isCode() { return true; }
    insts() { return parseInsts(this.js); }
}

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

export {
    PlatonicObj, NoObj, Bool, Bytes, Int, Real, Str, Uri, Lst, Rec, Rel,
    Fail, Type, Inst, Code, Objs, noobj, of, BASE_TID
};
export default PlatonicObj;
