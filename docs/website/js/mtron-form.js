/*
 * mtron-form — a general-purpose obj creator/editor driven by a type definition.
 *
 * A type definition (what `*<tid>` answers) is a rec pattern:
 *
 *   feature::T[?[model=>rec::T[?[…]],
 *                {?}response=>{?}[…],
 *                {?}format=><#>::T]][ctor?…]@vid
 *
 * The `?[… ]` is the field schema: each entry is `key => type`, where `{?}key`
 * marks an OPTIONAL field (the `?` maybe-coefficient). The value type decides
 * the input widget. This module only PARSES the schema and RENDERS/COLLECTS a
 * form — it never talks to metatron; the panel owns the fetch and the write.
 *
 * ES module, no DOM at import time (DOM is used inside renderForm).
 */
import { write } from './mtron-io.js';

/* split on a top-level separator, respecting brackets + quoted strings */
function splitTop(str, sep) {
    const parts = [];
    let depth = 0;
    let quote = null;
    let cur = '';
    for (let i = 0; i < str.length; i++) {
        const c = str[i];
        if (quote) {
            cur += c;
            if (c === '\\') { cur += str[i + 1] || ''; i += 1; }
            else if (c === quote) quote = null;
            continue;
        }
        if (c === "'" || c === '"') { quote = c; cur += c; continue; }
        if (c === '[' || c === '(' || c === '{') depth += 1;
        else if (c === ']' || c === ')' || c === '}') depth -= 1;
        if (depth === 0 && str.startsWith(sep, i)) { parts.push(cur.trim()); cur = ''; i += sep.length - 1; continue; }
        cur += c;
    }
    if (cur.trim()) parts.push(cur.trim());
    return parts;
}

/* the coarse kind of a type expression — what widget it wants */
function typeKind(t) {
    const s = String(t || '').trim();
    if (s.startsWith('/m/inst/isa(')) return typeKind(s.slice('/m/inst/isa('.length));
    if (s.startsWith('isa(')) return typeKind(s.slice(4));
    if (s.startsWith('rec::') || s.startsWith('[') || s.startsWith('{?}[')) return 'rec';
    if (s.startsWith('lst')) return 'lst';
    if (s.startsWith('uri::')) return 'uri';
    if (s.startsWith('str::')) return 'str';
    if (s.startsWith('int::')) return 'int';
    if (s.startsWith('real::')) return 'real';
    if (s.startsWith('bool::')) return 'bool';
    if (s.startsWith('bytes::')) return 'bytes';
    if (s.startsWith('!')) return 'ref';
    if (s.startsWith('<#')) return 'type';
    return 'str';
}

/* a type definition's `?[… ]` rec pattern -> [{ key, type, optional, kind }] */
function parseSchema(typeDef) {
    const src = String(typeDef || '');
    const start = src.indexOf('[?[');
    if (start < 0) return [];
    let depth = 0;
    let end = -1;
    for (let i = start; i < src.length; i++) {
        if (src[i] === '[') depth += 1;
        else if (src[i] === ']') { depth -= 1; if (depth === 0) { end = i; break; } }
    }
    if (end < 0) return [];
    const content = src.slice(start + 3, end - 1);
    return splitTop(content, ',').map((pair) => {
        const kv = splitTop(pair, '=>');
        let key = (kv[0] || '').trim();
        const type = (kv[1] || '').trim();
        let optional = false;
        if (key.startsWith('{?}')) { optional = true; key = key.slice(3).trim(); }
        return { key: key, type: type, optional: optional, kind: typeKind(type) };
    }).filter((f) => f.key);
}

/* a PlatonicObj -> the plain string shown in an input (rec/lst stay mtron) */
function plainVal(v) {
    if (v == null) return '';
    if (v.isNoObj()) return '';
    if (v.isStr() || v.isInt() || v.isReal() || v.isBool()) return String(v.js);
    if (v.isUri()) return v.js.toString();
    if (v.isCode()) return v.js;
    return write(v);
}

/* existing value lookup — `existing` is a Map<fURI,Obj> or a plain object */
function existingVal(existing, key) {
    if (existing == null) return undefined;
    if (existing.get) {
        for (const [k, v] of existing) if (k.toString() === key) return plainVal(v);
        return undefined;
    }
    return existing[key] != null ? plainVal(existing[key]) : undefined;
}

/* render a form for a schema; `existing` (Map<fURI,Obj>|object) pre-fills it */
function renderForm(schema, existing) {
    const form = document.createElement('form');
    form.className = 'obj-form';
    form.setAttribute('novalidate', '');
    schema.forEach((f) => {
        const row = document.createElement('label');
        row.className = 'form-row';
        const key = document.createElement('span');
        key.className = 'form-key';
        key.textContent = f.key + (f.optional ? ' ?' : '');
        key.title = f.type || '';
        row.appendChild(key);

        const cur = existingVal(existing, f.key);
        let input;
        if (f.kind === 'bool') {
            input = document.createElement('input');
            input.type = 'checkbox';
            input.checked = cur === 'true' || cur === true;
        } else if (f.kind === 'int' || f.kind === 'real') {
            input = document.createElement('input');
            input.type = 'number';
            input.step = f.kind === 'real' ? 'any' : '1';
            input.value = cur != null ? cur : '';
        } else if (f.kind === 'rec' || f.kind === 'lst') {
            input = document.createElement('textarea');
            input.rows = 2;
            input.value = cur != null ? cur : '';
        } else {
            input = document.createElement('input');
            input.type = 'text';
            input.value = cur != null ? cur : '';
        }
        input.name = f.key;
        input.dataset.kind = f.kind;
        row.appendChild(input);
        form.appendChild(row);
    });
    return form;
}

/* collect the form into a plain { key: value } object (strings/numbers/bools) */
function collectForm(form) {
    const out = {};
    form.querySelectorAll('[name]').forEach((el) => {
        if (el.type === 'checkbox') out[el.name] = el.checked;
        else out[el.name] = el.value;
    });
    return out;
}

export { parseSchema, renderForm, collectForm, typeKind, splitTop, plainVal };
export default { parseSchema, renderForm, collectForm, typeKind, splitTop, plainVal };
