#!/usr/bin/env python3
"""
Self-tests for assets/dsh_memory_loader.py — stdlib unittest, no VM needed.

Pins the mapping contract that the live metatron type checker enforces:
  * user/message -> user:: ; assistant reasoning+text -> thinking:: + ai::
  * tool/call + tool/result -> ai::(tool_requests) + correlated tool_result::
  * unknown event types are preserved, never dropped
  * every emitted string is a triple-quoted ("three double quotes") literal --
    mtron single-quote strings have NO escape mechanism (verified live)
  * name fields emit as bare uri tokens (uri::T), never quoted
"""

import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSET = HERE.parent / "dsh_memory_loader.py"

if not shutil.which("zstd"):
    sys.exit("skip: zstd CLI not installed")


def load_module():
    spec = importlib.util.spec_from_file_location("dl", ASSET)
    dl = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(dl)
    return dl


def event(t, seq, time=1000, **data):
    return {"type": t, "seq": seq, "time": time, "data": data}


class TestDshMemoryLoader(unittest.TestCase):
    def setUp(self):
        self.dl = load_module()
        self.tmp = Path(tempfile.mkdtemp(prefix="dsh_loader_"))
        session = {
            "type": "session", "version": 0, "id": "session-test-0001",
            "createdAt": 900, "cwd": "/tmp/x", "delegationDepth": 0, "agentPreset": "standard",
        }
        events = [
            session,
            event("request/context", 2, provider="ollama", model="test-model"),
            event("turn/start", 3, turn=1),
            event("user/message", 4, content=[{"type": "text", "text": "hello agent"}], role="user", id="m1"),
            event("assistant/message", 5, turn=1, step=1,
                  message={"role": "assistant", "content": [
                      {"type": "reasoning", "text": "thinking about the 'quote' problem"},
                      {"type": "text", "text": "here is my answer"},
                  ]}),
            event("tool/call", 6, turn=1, step=1, callId="call_abc", name="glob",
                  arguments='{"pattern":"*.md"}'),
            event("tool/result", 7, turn=1, step=1,
                  message={"content": [{"type": "tool-result", "toolCallId": "call_abc",
                                        "content": [{"type": "text", "text": "SKILL.md\nline2"}]}]}),
            event("turn/start", 8, turn=2),
            event("user/message", 9, content=[{"type": "text", "text": "second turn"}], role="user", id="m2"),
            event("totally/unknown-event", 10, turn=2, secret={"k": "v"}),
        ]
        bundle = self.tmp / "session.jsonl.zstd"
        with open(self.tmp / "session.jsonl", "w") as fh:
            for e in events:
                fh.write(json.dumps(e) + "\n")
        subprocess.run(["zstd", "-q", "--rm", self.tmp / "session.jsonl", "-o", str(bundle)], check=True)
        self.bundle = bundle
        self.out = self.tmp / "out.mtron"

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_loader(self):
        r = subprocess.run(
            [sys.executable, str(ASSET), "--bundle", str(self.bundle),
             "--out", str(self.out), "--agent", "testagent", "--session", "7"],
            capture_output=True, text=True, check=True)
        return r.stdout

    def test_audit_reports_counts(self):
        out = self.run_loader().splitlines()
        # system(provenance) + user + thinking + ai(text) + ai(tool_requests)
        # + tool_result + user + system([unmapped event])  = 8
        self.assertIn("wrote 8 message recs", out[0])
        self.assertIn("session=/usr/testagent/session/7", out[1])

    def test_contract_shape(self):
        self.run_loader()
        text = self.out.read_text()
        # variant census
        self.assertEqual(text.count("user::["), 2)
        self.assertEqual(text.count("thinking::["), 1)
        self.assertEqual(text.count("ai::["), 2)
        self.assertEqual(text.count("tool_result::["), 1)
        self.assertEqual(text.count("system::["), 2)  # 1 provenance + 1 preserved unknown event
        # correlation key
        self.assertIn("contents=>", text)
        self.assertEqual(text.count("call_abc"), 2)  # tool_request + tool_result
        # envelope
        self.assertTrue(text.count("session=>/usr/testagent/session/7") >= 6)
        # name as bare uri token (uri::T) — never quoted
        self.assertIn("name=>glob,", text)  # in tool_request
        self.assertIn("name=>glob,", text)  # in tool_result
        # thinking carries the apostrophe-bearing reasoning text
        self.assertIn("the 'quote' problem", text)
        # all strings triple-quoted (no bare ' ' output strings anywhere)
        for line in text.splitlines():
            line = line.strip()
            if not line or line in ("[", "]"):
                continue
            self.assertNotIn("=>'", line, f"a '...' string was emitted: {line[:100]}")

    def test_unknown_event_preserved(self):
        self.run_loader()
        text = self.out.read_text()
        self.assertIn("[unmapped event]", text)
        self.assertIn("totally/unknown-event", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
