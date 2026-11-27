/*
 * mathjax — lazy MathJax v4 for the portal page. a str::T carrying \(…\) (inline)
 * or \[…\] (display) is typeset in place; the ~1.5MB engine loads only when a
 * rendered value actually carries math. mirrors the docs loader (same CDN, same
 * tex-mml-chtml build) but typesets on demand, for content added after load.
 *
 *   hasMath(text)            does the text carry \(…\) / \[…\]
 *   typesetMath(el, text)    typeset any math in el (loads MathJax if needed)
 */
const CDN = "https://cdn.jsdelivr.net/npm/mathjax@4/tex-mml-chtml.js";
let pending = null;

/* MathJax's own delimiters — the portal page gets \(…\) already serialized, so
   there is no authored $…$ translation (that is MarkdownMath.java's job, on the
   docs/markdown side). */
export function hasMath(text) {
    return typeof text === "string" && (text.indexOf("\\(") >= 0 || text.indexOf("\\[") >= 0);
}

function load() {
    if (window.MathJax && window.MathJax.typesetPromise) return Promise.resolve(window.MathJax);
    if (pending) return pending;
    pending = new Promise((resolve) => {
        /* typeset on demand: don't let startup auto-typeset the whole page */
        window.MathJax = Object.assign({}, window.MathJax, {startup: {typeset: false}});
        const script = document.createElement("script");
        script.src = CDN;
        script.async = true;
        script.onload = () => {
            const M = window.MathJax;
            const ready = (M && M.startup && M.startup.promise) || Promise.resolve();
            ready.then(() => resolve(M && M.typesetPromise ? M : null), () => resolve(null));
        };
        script.onerror = () => { pending = null; resolve(null); };
        document.head.appendChild(script);
    });
    return pending;
}

export function typesetMath(el, text) {
    if (!el || !hasMath(text)) return Promise.resolve();
    return load().then((MathJax) => {
        if (MathJax && MathJax.typesetPromise) return MathJax.typesetPromise([el]).catch(() => {});
    });
}
