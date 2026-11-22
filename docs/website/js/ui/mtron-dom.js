/*
 * mtron-dom — the clickable DOM rendering of a single mtron fURI, split from the
 * pure sdk (furi.js / mtron-obj.js) so the sdk runs without a browser.
 *
 *   toElement(uri, open, cls)   one fURI as a <button> (shift-click opens ?docq)
 */
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

export { toElement };
export default { toElement };
