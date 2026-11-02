// ```tikz fenced blocks -> inline diagram.
//
// The plugin does not type-set TeX in the browser. A fence is POSTed to the tikz
// sidecar (dsh-ctl-tikz-mcp), which renders it with the same TeX Live the article
// pipeline uses, and returns a URL under the static server (dsh-ctl-tikz-static).
// Until a fence has been rendered the component shows the source, so a block never
// disappears just because the renderer is unreachable.
window.__ModuleLoader__.load({
    id: "dsh-tikz",
    factory: (require) => {
        const module = { exports: {} };
        const exports = module.exports;
        const react = require("react");
        const renderer = require("@deepseek-ai/dsh-client-ui-renderer");

        const RENDER_URL = "http://tikz-static:8080/render";
        const cache = new Map(); // source -> url | "pending" | "error"

        function useDiagram(source) {
            const [state, setState] = react.useState(() => cache.get(source) ?? "idle");
            react.useEffect(() => {
                if (cache.has(source)) { setState(cache.get(source)); return; }
                let live = true;
                cache.set(source, "pending");
                fetch(RENDER_URL, {
                    method: "POST",
                    headers: { "content-type": "application/json" },
                    body: JSON.stringify({ tikz: source })
                })
                    .then((r) => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
                    .then((body) => {
                        const url = body?.url ?? body?.png ?? body;
                        if (live) { cache.set(source, url); setState(url); }
                    })
                    .catch(() => { if (live) { cache.set(source, "error"); setState("error"); } });
                return () => { live = false; };
            }, [source]);
            return state;
        }

        function TikzBlock({ code }) {
            const state = useDiagram(code);
            if (state === "error") {
                return react.createElement("div", { className: "dsh-tikz dsh-tikz-error" },
                    react.createElement("pre", null, code));
            }
            if (!state || state === "idle" || state === "pending") {
                return react.createElement("div", { className: "dsh-tikz dsh-tikz-pending" },
                    react.createElement("pre", null, code));
            }
            return react.createElement("img", { className: "dsh-tikz-img", src: state, alt: "tikz diagram" });
        }

        // registerFactory is the renderer's extension point for a fenced language.
        if (typeof renderer.registerFactory === "function") {
            renderer.registerFactory({ language: "tikz", block: true }, TikzBlock);
        }

        exports.TikzBlock = TikzBlock;
        return module.exports;
    }
});
