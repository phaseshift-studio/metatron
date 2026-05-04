#!/usr/bin/env python3
"""
Wire-format probe for metatron WebSocket endpoints.

Sends a battery of mtron queries against both the native and JSON endpoints
and pretty-prints the raw responses so we can understand the exact wire format
before rewriting instset_doc_generator.py.

Usage:
    python probe_wire_format.py [instset_vid]
    python probe_wire_format.py /m/mach
"""

import asyncio
import json
import sys
import textwrap

import websockets

NATIVE_URL = "ws://localhost:8555/mtron?in=application/mtron&out=application/mtron"
JSON_URL   = "ws://localhost:8555/mtron?in=application/mtron&out=application/json"

TIMEOUT = 10

# ── helpers ──────────────────────────────────────────────────────────────────

def hr(title: str = "", width: int = 72) -> str:
    if title:
        pad = width - len(title) - 2
        return f"\n{'─' * (pad // 2)} {title} {'─' * (pad - pad // 2)}"
    return "─" * width


async def ask(ws, expr: str) -> str:
    await ws.send(expr)
    try:
        raw = await asyncio.wait_for(ws.recv(), timeout=TIMEOUT)
        return raw if isinstance(raw, str) else raw.decode()
    except (asyncio.TimeoutError, TimeoutError):
        return "<TIMEOUT>"
    except Exception as e:
        return f"<ERROR: {e}>"


def show(label: str, expr: str, result: str, truncate: int = 600) -> None:
    print(hr(label))
    print(f"  expr   : {expr}")
    display = result if len(result) <= truncate else result[:truncate] + f"  …[{len(result)} chars total]"
    print(f"  result : {display}")


async def probe(vid: str) -> None:
    print(f"\n{'═' * 72}")
    print(f"  PROBING  {vid}")
    print(f"{'═' * 72}")

    # ── native endpoint ───────────────────────────────────────────────────────
    print(hr("NATIVE WS  (out=application/mtron)"))
    async with websockets.connect(NATIVE_URL) as ws:

        # 1. top-level instset obj (raw)
        show("*vid", f"*{vid}", await ask(ws, f"*{vid}"))

        # 2. instset obj with block-apply
        show("|*<vid>", f"|*<{vid}>", await ask(ws, f"|*<{vid}>"))

        # 3. direct children (URI stream)
        show("*vid/+/.dom()", f"*{vid}/+/.dom()", await ask(ws, f"*{vid}/+/.dom()"))

        # 4. direct children with values (relation stream)
        show("*vid/+/", f"*{vid}/+/", await ask(ws, f"*{vid}/+/"))

        # 5. instruction URIs
        show("*vid/inst/+/.dom()", f"*{vid}/inst/+/.dom()", await ask(ws, f"*{vid}/inst/+/.dom()"))

        # 6. instruction relations
        show("*vid/inst/+/", f"*{vid}/inst/+/", await ask(ws, f"*{vid}/inst/+/"))

        # 7. space URIs
        show("*vid/space/+/.dom()", f"*{vid}/space/+/.dom()", await ask(ws, f"*{vid}/space/+/.dom()"))

        # 8. rewrite URIs
        show("*vid/inst/rewrite/+/.dom()", f"*{vid}/inst/rewrite/+/.dom()",
             await ask(ws, f"*{vid}/inst/rewrite/+/.dom()"))

        # 9. doc query on the instset itself
        show("*<vid?docq>", f"*<{vid}?docq>", await ask(ws, f"*<{vid}?docq>"))

        # 10. pick first child, block-deref it to see type definition
        children_raw = await ask(ws, f"*{vid}/+/.dom()")
        first_child = None
        if children_raw and children_raw not in ("<TIMEOUT>",) and not children_raw.startswith("<ERROR"):
            stripped = children_raw.strip()
            if stripped.startswith("{") and stripped.endswith("}"):
                parts = stripped[1:-1].split(",")
                if parts and parts[0].strip():
                    first_child = parts[0].strip()

        if first_child:
            show(f"|*<first_child>  ({first_child})", f"|*<{first_child}>",
                 await ask(ws, f"|*<{first_child}>"))
            show(f"*<first_child?docq>", f"*<{first_child}?docq>",
                 await ask(ws, f"*<{first_child}?docq>"))

        # 11. first instruction
        insts_raw = await ask(ws, f"*{vid}/inst/+/.dom()")
        first_inst = None
        if insts_raw and not insts_raw.startswith("<"):
            stripped = insts_raw.strip()
            if stripped.startswith("{") and stripped.endswith("}"):
                parts = stripped[1:-1].split(",")
                if parts and parts[0].strip():
                    first_inst = parts[0].strip()

        if first_inst:
            show(f"|*<first_inst>  ({first_inst})", f"|*<{first_inst}>",
                 await ask(ws, f"|*<{first_inst}>"))
            show(f"*<first_inst?docq>", f"*<{first_inst}?docq>",
                 await ask(ws, f"*<{first_inst}?docq>"))

    # ── JSON endpoint ─────────────────────────────────────────────────────────
    print(hr("JSON WS  (out=application/json)"))
    async with websockets.connect(JSON_URL) as ws:

        for expr in [
            f"|*<{vid}>",
            f"*{vid}/+/.dom()",
            f"*{vid}/+/",
            f"*{vid}/inst/+/.dom()",
            f"*{vid}/inst/+/",
            f"*{vid}/space/+/.dom()",
            f"*<{vid}?docq>",
        ]:
            raw = await ask(ws, expr)
            # pretty-print if valid JSON
            try:
                parsed = json.loads(raw)
                pretty = json.dumps(parsed, indent=2)
                display = pretty if len(pretty) <= 800 else pretty[:800] + f"\n  …[{len(pretty)} chars]"
            except Exception:
                display = raw[:600] if len(raw) > 600 else raw
            show(f"JSON: {expr}", expr, display)

        # doc query on first inst via JSON
        insts_raw = await ask(ws, f"*{vid}/inst/+/.dom()")
        first_inst_json = None
        try:
            parsed = json.loads(insts_raw)
            if isinstance(parsed, list) and parsed:
                first_inst_json = parsed[0]
            elif isinstance(parsed, str):
                stripped = parsed.strip()
                if stripped.startswith("{"):
                    parts = stripped[1:-1].split(",")
                    if parts:
                        first_inst_json = parts[0].strip()
        except Exception:
            stripped = insts_raw.strip()
            if stripped.startswith("{"):
                parts = stripped[1:-1].split(",")
                if parts:
                    first_inst_json = parts[0].strip()

        if first_inst_json:
            show(f"JSON: *<first_inst?docq> ({first_inst_json})",
                 f"*<{first_inst_json}?docq>",
                 await ask(ws, f"*<{first_inst_json}?docq>"))


async def main() -> None:
    vid = sys.argv[1] if len(sys.argv) > 1 else "/m/mach"
    await probe(vid)
    print(hr())
    print("Done.")


if __name__ == "__main__":
    asyncio.run(main())
