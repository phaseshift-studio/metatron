/*
 * mtron-dom — the clickable DOM rendering of a single mtron fURI, split from the
 * pure sdk (furi.js / mtron-obj.js) so the sdk runs without a browser.
 *
 *   toElement(uri, open, cls)   one fURI as a <button> (shift-click opens ?docq)
 *   iconButton(kind, open)      one icon of the page-wide vocabulary (below)
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

/* ── the icon vocabulary ─────────────────────────────────────────────────
   one meaning per mark, page-wide, so a panel's AUXILIARY actions read the
   same everywhere. a panel's MAIN action stays a text button (compile, the
   explain button on a code card, push on the machine panel); these are the
   marks:

     ?     explain — the explanation of this code      (explain())
     book  docq    — the documentation attached to it   (docs::T,  ?docq)
     a*b   decq    — its algebraic declaration          (decs::T,  ?decq)

   a new auxiliary meaning joins HERE, not in one panel's markup, so the marks
   cannot drift apart. */
const BOOK_SVG = '<svg viewBox="0 0 16 16" width="11" height="11" aria-hidden="true" focusable="false">'
    + '<path d="M8 4.1C6.7 3.1 5 2.7 2.9 2.8v9.2C5 11.9 6.7 12.3 8 13.3c1.3-1 3-1.4 5.1-1.3V2.8C11 2.7 9.3 3.1 8 4.1Z" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>'
    + '<path d="M8 4.1v9.2" fill="none" stroke="currentColor" stroke-width="1.2"/></svg>';

const ICONS = {
    explain: {mark: '?', title: 'explain'},
    docq: {mark: BOOK_SVG, title: 'documentation (?docq)', html: true},
    decq: {mark: 'a*b', title: 'algebraic declaration (?decq)'}
};

/** One icon button, by meaning: iconButton('docq', () => open(...)). */
function iconButton(kind, open) {
    const icon = ICONS[kind];
    if (!icon) return null;
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'icon-btn ic-' + kind;
    b.title = icon.title;
    b.setAttribute('aria-label', icon.title);
    if (icon.html) b.innerHTML = icon.mark;
    else b.textContent = icon.mark;
    b.addEventListener('click', (ev) => {
        ev.preventDefault();
        ev.stopPropagation();
        if (open) open(ev);
    });
    return b;
}

export { toElement, iconButton, ICONS };
export default { toElement, iconButton };
