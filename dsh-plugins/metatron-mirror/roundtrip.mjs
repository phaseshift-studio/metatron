/**
 * roundtrip.mjs — end-to-end check for the metatron-mirror plugin.
 *
 * Feeds a synthetic dsh chat-lifecycle sequence through the plugin's real
 * mapEvent + LedgerClient into the LIVE metatron ledger, then reads the
 * ledger back over the same mcp message server.
 *
 *   node roundtrip.mjs
 *
 * env: METATRON_MIRROR_WS (default ws://127.0.0.1:8555/message)
 *      METATRON_MIRROR_ROOT (default /usr/dsh)
 */
import { mapEvent, sanitizeSegment, LedgerClient } from './metatron-mirror.ts';

const WS = process.env.METATRON_MIRROR_WS ?? 'ws://127.0.0.1:8555/message';
const ROOT = process.env.METATRON_MIRROR_ROOT ?? '/usr/dsh';
const SESSION = 'metatron-mirror-roundtrip';
const SESSION_VID = `${ROOT}/session/${sanitizeSegment(SESSION)}`;
const log = (line) => console.log(`[roundtrip] ${line}`);
const die = (message) => {
  console.error(`[roundtrip] FAIL: ${message}`);
  process.exit(1);
};

// ── a synthetic dsh session/event stream (the exact shapes dsh emits) ──
const now = Date.now();
const events = [
  { type: 'turn/start', seq: 1, time: now, data: { turn: 1 } },
  {
    type: 'request/header', seq: 2, time: now,
    data: { header: { config: {}, system: 'you are the keeper of the harbor light ledger' }, reason: 'initial' },
  },
  {
    type: 'user/message', seq: 3, time: now,
    data: { id: 'm1', role: 'user', content: [{ type: 'text', text: 'count the spare bulbs in the store shed' }], source: { kind: 'user' } },
  },
  {
    type: 'assistant/message', seq: 4, time: now,
    data: { turn: 1, step: 1, message: { id: 'm2', role: 'assistant', content: [
        { type: 'reasoning', text: 'the keeper keeps a tally behind the lamp room' },
        { type: 'text', text: 'let me count the spare bulbs' },
        { type: 'tool-call', id: 'call_roundtrip_1', name: 'm_probe_buoy', arguments: '{"0":"select count(*) from bulbs"}' },
      ], source: { kind: 'model', provider: 'deepseek', model: 'x' } } },
  },
  { type: 'tool/call', seq: 5, time: now, data: { turn: 1, step: 1, callId: 'call_roundtrip_1', name: 'm_probe_buoy', arguments: '{"0":"select count(*) from bulbs"}' } },
  {
    type: 'tool/result', seq: 6, time: now,
    data: { turn: 1, step: 1, message: { id: 'm3', role: 'user', content: [
        { type: 'tool-result', toolCallId: 'call_roundtrip_1', content: [{ type: 'text', text: 'two spare bulbs on the shelf' }] },
      ], source: { kind: 'tool', callId: 'call_roundtrip_1' } } },
  },
  {
    type: 'assistant/message', seq: 7, time: now,
    data: { turn: 1, step: 2, message: { id: 'm4', role: 'assistant', content: [{ type: 'text', text: 'two spare bulbs, keeper' }], source: { kind: 'model', provider: 'deepseek', model: 'x' } } },
  },
  { type: 'turn/end', seq: 8, time: now, data: { turn: 1, reason: { kind: 'done' } } },
];

// ── map + send through the plugin itself ──
const state = { callNames: new Map(), lastSystem: null, lastTurn: null };
const sends = [];
for (const event of events) {
  for (const s of mapEvent(state, event) ?? []) sends.push({ root: ROOT, session: SESSION_VID, ...s });
}
console.log(`[roundtrip] mapped ${sends.length} ledger sends`);
if (sends.length < 5) die(`expected >=5 sends (system, user, ai+tool_requests, thinking, tool_result), got ${sends.length}`);

const client = new LedgerClient(WS, log);
for (const send of sends) {
  try {
    await client.send(send);
    console.log(`[roundtrip] sent kind=${send.kind}${send.name ? ` name=${send.name}` : ''}`);
  } catch (e) {
    die(`send failed for kind=${send.kind}: ${e.message}`);
  }
}

// ── read the ledger back through get_messages ──
await new Promise((r) => setTimeout(r, 300));
const ws = new WebSocket(WS);
let seq = 900;
const pending = new Map();

const rpc = (method, params, timeoutMs) =>
  new Promise((resolve, reject) => {
    const id = ++seq;
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error(`timeout after ${timeoutMs}ms: ${method}`));
    }, timeoutMs);
    pending.set(id, (err, result) => {
      clearTimeout(timer);
      if (err) reject(err);
      else resolve(result);
    });
    ws.send(JSON.stringify({ jsonrpc: '2.0', id, method, params }));
  });

const finish = (code) => {
  try {
    ws.close();
  } catch {
    /* noop */
  }
  process.exit(code);
};

ws.addEventListener('open', () => {
  rpc('tools/call', { name: 'm_llm_mcp_mcp_message_get_messages', arguments: { root: ROOT, session: SESSION_VID, max: 20 } }, 8000)
    .then((result) => {
      const text = result?.content?.[0]?.text ?? JSON.stringify(result);
      console.log('\n════ ledger read-back ════');
      console.log(text);
      console.log('════════════════════════');
      const mustAppear = [
        'harbor light ledger',
        'count the spare bulbs in the store shed',
        'call_roundtrip_1',
        'two spare bulbs on the shelf',
        'two spare bulbs, keeper',
        'the keeper keeps a tally behind the lamp room',
      ];
      const missing = mustAppear.filter((needle) => !text.includes(needle));
      if (missing.length > 0) die(`read-back missing: ${missing.join(' ; ')}`);
      console.log('[roundtrip] PASS: all five kinds landed in the ledger with their payloads');
      finish(0);
    })
    .catch((e) => die(`read-back failed: ${e.message}`));
});
ws.addEventListener('message', (m) => {
  let frame;
  try {
    frame = JSON.parse(typeof m.data === 'string' ? m.data : String(m.data));
  } catch {
    return;
  }
  if (frame?.id == null || !pending.has(Number(frame.id))) return;
  const done = pending.get(Number(frame.id));
  pending.delete(Number(frame.id));
  if (frame.error) done(new Error(`rpc error: ${frame.error.message ?? frame.error.code ?? 'unknown'}`), frame.result);
  else done(null, frame.result);
});
ws.addEventListener('close', () => {
  for (const p of pending.values()) p(new Error('connection closed'), undefined);
  pending.clear();
});
ws.addEventListener('error', () => die(`websocket error (is metatron running on ${WS}?)`));
setTimeout(() => die('no ledger read-back within 15s'), 15000);
