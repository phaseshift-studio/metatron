/*
 * mtron-dom — the clickable DOM rendering of mtron objs, split from the pure sdk
 * (furi.js / mtron-obj.js) so the sdk runs without a browser.
 *
 *   toElement(uri, open, cls)   one fURI as a <button> (shift-click opens ?docq)
 *   render(obj, open)           a PlatonicObj as a clickable DocumentFragment
 */
import { BASE_TID } from '../sdk/mtron-obj.js';

/* a single fURI as a transparent button. normal click opens the uri, shift-click
   opens its ?docq. the text is set by the caller (a highlighted slice, a short
   label, …); this only adds the click behavior + pointer cursor. */
function toElement(uri, open, cls) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = cls || 'obj-link';
    b.title = uri.toString() + ' · shift-click for ?docq';
    b.addEventListener('click', (ev) => {
        ev.preventDefault();
        ev.stopPropagation();
        if (open) open(ev.shiftKey ? uri.doc() : uri);
    });
    return b;
}

/* render a PlatonicObj as clickable DOM. every fURI — a Uri value, a rec key, a
   vid, a non-base tid — becomes a transparent button (toElement), so the
   click/pin/shift-?docq behavior is the fURI's, once, not each widget's. */
function clickable(parent, uri, open) {
    const b = toElement(uri, open);
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
    else if (obj.isUri()) { const b = toElement(obj.js, open); b.textContent = '<' + obj.js.toString() + '>'; parent.appendChild(b); }
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

export { render, toElement };
export default { render, toElement };
