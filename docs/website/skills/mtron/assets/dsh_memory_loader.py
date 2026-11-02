#!/usr/bin/env python3
"""
dsh_memory_loader.py — the DSH→metatron memory adapter (the first of the bus).

Reads DSH harness session transcripts (zstd-compressed JSONL event logs) and
emits an mtron file whose contents EVALUATE to a lst of metatron message recs:

    user::[=>], system::[=>], thinking::[=>], ai::[=>], tool_result::[=>]

The target contract is the live agent memory model (see /usr/dr/message):
a typed envelope (text, time, session, depth, chat_id) with tid-discriminated
variants, plus tool_request:: nested under ai:: and the `contents` call-id
correlating tool_request <-> tool_result.

Afterwards, in metatron:

    *<<the emitted file uri>>         [-- evaluates to the lst of message recs --]
    ... .to(/usr/dr/message)          [-- and the migrated agent is resident     --]

Usage:
    python3 dsh_memory_loader.py [--out FILE] [--agent dr] [--session 1]
                                 [--bundle PATH]...
                                 [--root DSH_HOME] [--min-chunks N]

Defaults: all sessions under $DSH_HOME/sessions (or ~/.dsh/sessions),
out= <this assets dir>/dsh_memory.mtron
"""

import argparse
import datetime
import json
import os
import subprocess
import sys
from pathlib import Path

# --- the message envelope ------------------------------------------------------
ENVELOPE_NOTE = "session envelope: text, time, session, depth, chat_id; " \
                "variants: user, system, thinking, ai (tool_requests nested), tool_result"

DROP_TYPES = {
    "reasoning-chunks",      # streamed deltas — reconstructed from the final assistant/message (optionally joined)
    "assistant/chunk",       # streaming — same
    "text-chunks",           # streaming — same
    "step/start", "step/end",
    "turn/start", "turn/end",# grouping is carried in chat_id
    "session",               # folded into the synthesized system:: message
    "request/header", "request/context",  # folded into the synthesized system:: provenance
    "permission/preset", "sandbox/mode", "approval/policy", "approval/asked",
    "command/run", "command/done",
    "agent/inbox/spliced",   # harness message edits, not conversation
    "compaction/prune", "compaction/start", "compaction/end", "compaction/summary",
    "session/end-seed",
}


def read_bundle(bundle: Path):
    """Yield parsed JSON events from a session.jsonl.zstd bundle."""
    try:
        proc = subprocess.run(["zstd", "-dc", str(bundle)],
                              capture_output=True, timeout=600, check=True)
    except FileNotFoundError:
        # fall back to the python bindings if the CLI is absent
        try:
            import zstandard  # type: ignore
            with open(bundle, "rb") as fh:
                data = zstandard.ZstdDecompressor().decompress(fh.read(),
                                                               max_output_size=1 << 32)
        except ImportError:
            sys.exit("zstd CLI not found and the 'zstandard' python module is unavailable — install zstd")
    else:
        data = proc.stdout
    for line in data.decode("utf-8", "replace").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            yield json.loads(line)
        except json.JSONDecodeError:
            continue


def iso(ts_ms):
    if not isinstance(ts_ms, (int, float)):
        return None
    return datetime.datetime.fromtimestamp(ts_ms / 1000.0, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def mtext(value) -> str:
    # Render a python value as an mtron str.
    #
    # mtron ' ' strings have NO escape mechanism (verified live: both the
    # backslash and doubled-quote forms fail to parse) -- so every string is
    # emitted as a triple-quoted """ """ literal (three double quotes around
    # the text), which provably carries apostrophes, backslashes, newlines,
    # and quotes. An in-text triple-quote run would terminate the literal, so
    # it is defused to smart quotes.
    if value is None:
        value = ""
    s = str(value).replace('"""', '\u201C\u201D')
    if s.endswith("\\"):          # a trailing backslash could swallow into the close
        s += " "
    return '"""' + s + '"""'


def content_text(blocks, kind="text"):
    """Join the text of content blocks of a given kind."""
    out = []
    for b in blocks or []:
        if isinstance(b, dict) and b.get("type") == kind and b.get("text"):
            out.append(b["text"])
    return "\n".join(out)


def tool_result_text(result_event):
    try:
        outer = result_event["data"]["message"]["content"]
        for o in outer:
            if o.get("type") == "tool-result":
                inner = [b.get("text", "") for b in (o.get("content") or [])
                         if isinstance(b, dict) and b.get("type") == "text"]
                return "\n".join(t for t in inner if t)
        return json.dumps(result_event["data"]["message"])
    except (KeyError, TypeError):
        return json.dumps(result_event.get("data", {}))


def load(session: dict, events: list, agent: str, session_no: int, min_chunks: int):
    """Map one DSH session (header + events) to a list of metatron message dicts."""
    sid = session.get("id", "dsh-session")
    short = sid.split("-")[-1][:8] if "-" in sid else sid[-8:]
    session_uri = f"/usr/{agent}/session/{session_no}"
    depth = int(session.get("delegationDepth", 0)) or 1
    context = [e["data"] for e in events if e["type"] == "request/context"]
    prov = ", ".join(f"{c.get('provider', '?')}/{c.get('model', '?')}" for c in context) or "harness-internal"

    msgs = []
    chat = 0
    last_turn = None

    def env(m, ts=None):
        m["time"] = iso(ts)
        m["session"] = session_uri
        m["depth"] = depth
        m["chat_id"] = chat if chat else 1
        return m

    # 1) the synthesized system:: — provenance of the migrated agent
    msgs.append(env({
        "tid": "system",
        "text": (f"migrated agent memory: DSH harness session {sid}\n"
                 f"workspace: {session.get('cwd', '?')}\n"
                 f"model: {prov}\n"
                 f"agent preset: {session.get('agentPreset', '?')}\n"
                 f"mapping: {ENVELOPE_NOTE}\n"
                 f"note: chunks were joined into finals; harness bookkeeping was dropped during migration."),
    }, session.get("createdAt")))

    # 2) conversation, in transcript order -----------------------------------------
    pending_calls = []          # tool/call awaiting its result within the same step

    for e in sorted(events, key=lambda x: x.get("seq", x.get("seq0", 0))):
        t = e.get("type")
        d = e.get("data", {})
        if t in DROP_TYPES:
            continue
        turn = d.get("turn")
        if turn is not None and turn != last_turn:
            last_turn = turn
            chat = turn
        if t == "user/message":
            txt = content_text(d.get("content"))
            if not txt and isinstance(d.get("text"), str):
                txt = d["text"]
            msgs.append(env({"tid": "user", "text": txt or mtext(d.get("text", ""))}, e.get("time")))
        elif t == "assistant/message":
            inner = d.get("message", d)
            blocks = inner.get("content", [])
            reasoning = content_text(blocks, "reasoning")
            if not reasoning and min_chunks:
                # reconstruct from the streamed deltas of this step (fallback only)
                pieces = []
                for c in events:
                    if c.get("type") == "reasoning-chunks" and c["data"].get("turn") == turn:
                        pieces.extend(c["data"].get("texts", []))
                reasoning = "".join(pieces)
            text = content_text(blocks, "text")
            if reasoning:
                msgs.append(env({"tid": "thinking", "text": reasoning}, e.get("time")))
            if text:
                msgs.append(env({"tid": "ai", "text": text}, e.get("time")))
            pending_calls = []
        elif t == "tool/call":
            call_id = d.get("callId", f"call_{e.get('seq', 0)}")
            name = d.get("name", "tool")
            args = d.get("arguments", "{}")
            if isinstance(args, (dict, list)):
                args = json.dumps(args)
            pending_calls.append({"callId": call_id, "name": name, "args": args})
        elif t == "tool/result":
            # find the correlated call; emit ai::tool_requests then tool_result::
            call_id = None
            try:
                call_id = d["message"]["content"][0].get("toolCallId")
            except (KeyError, IndexError, TypeError):
                pass
            match = next((c for c in pending_calls if c["callId"] == call_id), None) \
                   or (pending_calls.pop() if pending_calls else None)
            name = match["name"] if match else "tool"
            args = match["args"] if match else "{}"
            effective_id = call_id or (match["callId"] if match else "call")
            # tool_request::T is a message::T refinement — carry the full envelope,
            # name as a bare uri (uri::T, not str), text required
            tr = env({"name": name, "args": args, "contents": effective_id,
                      "text": f"{name}({args})" if args not in ("{}", "") else f"{name}()"},
                     e.get("time"))
            msgs.append(env({"tid": "ai", "tool_requests": [tr]}, e.get("time")))
            msgs.append(env({
                "tid": "tool_result", "name": name, "contents": effective_id,
                "text": tool_result_text(e),
            }, e.get("time")))
        else:
            # unknown future event — preserve, don't drop silently
            msgs.append(env({
                "tid": "system", "text": f"[unmapped event] type={t} data={json.dumps(d)[:400]}",
            }, e.get("time")))

    return msgs, sid, session_uri, chat


def to_mtron(msgs):
    lines = ["["]
    import re as _re
    for m in msgs:
        fields = []
        for key in ("text", "time", "session", "depth", "chat_id", "name", "contents"):
            if key in m and m[key] is not None:
                if key in ("session",):
                    fields.append(f"{key}=>{m[key]}")
                elif key in ("depth", "chat_id"):
                    fields.append(f"{key}=>{m[key]}")
                elif key == "name" and _re.fullmatch(r"[A-Za-z_][A-Za-z0-9_:./-]*", str(m[key])):
                    fields.append(f"name=>{m[key]}")   # uri::T — bare token, unquoted
                else:
                    fields.append(f"{key}=>{mtext(m[key])}")
        if "tool_requests" in m:
            import re as _re
            trs = ", ".join(
                "tool_request::["
                + f"name=>{tr['name'] if _re.fullmatch(r'[A-Za-z_][A-Za-z0-9_:./-]*', tr['name']) else mtext(tr['name'])}, "
                + f"args=>{mtext(tr['args'])}, "
                + f"contents=>{mtext(tr['contents'])}, "
                + f"text=>{mtext(tr['text'])}, "
                + f"time=>{mtext(tr.get('time', ''))}, "
                + f"session=>{tr.get('session', '')}, "
                + f"depth=>{tr.get('depth', 1)}, "
                + f"chat_id=>{tr.get('chat_id', 1)}"
                + "]"
                for tr in m["tool_requests"])
            fields.append(f"tool_requests=>[{trs}]")
        lines.append(f"    {m['tid']}::[{', '.join(f for f in fields if f)}],")
    lines.append("]")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default=None, help="output mtron file (default: <assets>/dsh_memory.mtron)")
    ap.add_argument("--agent", default="dr", help="target agent name (session uri /usr/<agent>/session/N)")
    ap.add_argument("--session", type=int, default=1, help="metatron session number for the migrated history")
    ap.add_argument("--bundle", action="append", default=[], help="a single session.jsonl.zstd (repeatable)")
    ap.add_argument("--root", default=None, help="DSH HOME (default $DSH_HOME or ~/.dsh)")
    ap.add_argument("--min-chunks", type=int, default=0,
                    help="fallback: reconstruct thinking from streamed chunks when a final message lacks it")
    a = ap.parse_args()

    root = Path(a.root or os.environ.get("DSH_HOME") or (Path.home() / ".dsh"))
    bundles = [Path(b) for b in a.bundle]
    if not bundles:
        sessions = root / "sessions"
        if sessions.is_dir():
            bundles = sorted(sessions.glob("*/*/session.jsonl.zstd"))
    if not bundles:
        sys.exit(f"no bundles found under {root}/sessions — pass --bundle explicitly")

    out = Path(a.out) if a.out else Path(__file__).parent / "dsh_memory.mtron"
    all_msgs = []
    audit = []
    for i, b in enumerate(sorted(bundles, key=lambda p: p.name)):
        events = list(read_bundle(b))
        session = next((e for e in events if e.get("type") == "session"), {})
        counts = {}
        for e in events:
            counts[e.get("type", "?")] = counts.get(e.get("type", "?"), 0) + 1
        msgs, sid, session_uri, chat = load(session, events, a.agent, a.session + i if len(bundles) > 1 else a.session, a.min_chunks)
        kept = sum(1 for m in msgs)
        dropped = sum(v for k, v in counts.items() if k in DROP_TYPES)
        audit.append(f"{b.name}: {len(events)} events | kept {kept} msgs (system 1 synthesized) | "
                     f"dropped {dropped} bookkeeping | session={session_uri} | last chat_id={chat}")
        all_msgs.extend(msgs)
        if a.bundle:
            audit.append(f"   source types: " + ", ".join(f"{k}={v}" for k, v in sorted(counts.items())))

    out.write_text(to_mtron(all_msgs) + "\n")
    print(f"wrote {len(all_msgs)} message recs -> {out}")
    for line in audit:
        print("  " + line)


if __name__ == "__main__":
    main()
