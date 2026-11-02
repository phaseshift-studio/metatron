/**
 * offline-check.mjs — verifies the metatron-mirror plugin end-to-end without
 * a live metatron: spins up fake mcp message servers (json-rpc over
 * websocket), loads the plugin's real apply() with a minimal cordis ctx,
 * feeds synthetic chat-lifecycle streams, and reads the ledger back.
 *
 *   phase 1 — live path: server up from the start; an 8-event stream
 *             delivers in order; ids join; system dedupes; every write
 *             carries the idempotency stamp; the watermark file lands.
 *   phase 2 — durability: metatron down while events fire (write fails,
 *             watermark holds); on recovery the gap replays in seq order —
 *             including a compaction summary as the kind=compaction
 *             sentinel at its log position; a second outage/hold/replay
 *             round on the next live event.
 *
 *   node offline-check.mjs
 */
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { apply } from './metatron-mirror.ts';

const require = createRequire(import.meta.url);
const { Server: WsServer } = require('ws');

let failures = 0;
const fail = (msg) => {
  failures++;
  console.error(`[offline] FAIL: ${msg}`);
};
const ok = (msg) => console.log(`[offline] ${msg}`);

// ── fake metatron mcp message server (start/stop on demand) ───────────
function makeFakeMetatron() {
  const ledger = [];
  const server = createServer();
  let wss = null;
  const onConnection = (socket) => {
    socket.on('message', (raw) => {
      let frame;
      try {
        frame = JSON.parse(String(raw));
      } catch {
        return;
      }
      const reply = (result, error) =>
        socket.send(JSON.stringify(error ? { jsonrpc: '2.0', id: frame.id, error } : { jsonrpc: '2.0', id: frame.id, result }));
      if (!frame.id) return; // notification
      if (frame.method === 'initialize') {
        reply({ protocolVersion: '2025-03-26', capabilities: { tools: {} }, serverInfo: { name: 'fake-metatron', version: '0' } });
        return;
      }
      if (frame.method === 'tools/call') {
        const { name, arguments: args } = frame.params ?? {};
        if (name === 'm_llm_mcp_mcp_message_add_message') {
          ledger.push(args);
          reply({ content: [{ type: 'text', text: `user::written ${args.session}` }] });
        } else {
          reply(undefined, { code: -32601, message: `tool not found: ${name}` });
        }
        return;
      }
      reply(undefined, { code: -32601, message: `method not found: ${frame.method}` });
    });
  };
  let port = null;
  return {
    ledger,
    url: null,
    async start() {
      await new Promise((r) => server.listen(port ?? 0, '127.0.0.1', r));
      port = server.address().port; // keep the same address across stop/start
      this.url = `ws://127.0.0.1:${port}/message`;
      // a fresh WsServer each start — a closed one detaches its upgrade hook
      wss = new WsServer({ server });
      wss.on('connection', onConnection);
    },
    async stop() {
      if (wss) {
        for (const c of wss.clients) {
          try {
            c.terminate();
          } catch {
            /* noop */
          }
        }
        await new Promise((r) => wss.close(r));
        wss = null;
      }
      await new Promise((r) => server.close(r));
    },
  };
}

// ── minimal cordis ctx (just what the plugin consumes) ───────────────
const makeCtx = () => {
  const listeners = new Map();
  return {
    on(name, fn) {
      const arr = listeners.get(name) ?? [];
      arr.push(fn);
      listeners.set(name, arr);
    },
    emit(name, ...args) {
      for (const fn of listeners.get(name) ?? []) fn(...args);
    },
  };
};

const stateDir1 = await mkdtemp(path.join(tmpdir(), 'metron-mirror-1-'));
const stateDir2 = await mkdtemp(path.join(tmpdir(), 'metron-mirror-2-'));
const readWatermark = async (dir, sid) => {
  // no file yet = nothing ever acked (the plugin only persists on success)
  let obj = {};
  try {
    obj = JSON.parse(await readFile(path.join(dir, 'watermark.json'), 'utf8'));
  } catch {
    /* absent — the watermark is still -1 */
  }
  return obj[sid] ?? -1;
};

// ═══════════════════════════════════════════ phase 1 — live path
{
  const fake = makeFakeMetatron();
  await fake.start();
  const ctx = makeCtx();
  apply(ctx, { url: fake.url, root: '/usr/dsh', stateDir: stateDir1 });

  const deliver = (event) => ctx.emit('session/event', { id: 'offline-probe-1' }, event);
  const now = Date.now();
  deliver({ type: 'turn/start', seq: 1, time: now, data: { turn: 1 } });
  deliver({
    type: 'request/header', seq: 2, time: now,
    data: { header: { config: {}, system: 'you are the keeper of the harbor light ledger' }, reason: 'initial' },
  });
  deliver({
    type: 'user/message', seq: 3, time: now,
    data: { id: 'm1', role: 'user', content: [{ type: 'text', text: 'count the spare bulbs in the store shed' }], source: { kind: 'user' } },
  });
  deliver({
    type: 'assistant/message', seq: 4, time: now,
    data: { turn: 1, step: 1, message: { id: 'm2', role: 'assistant', content: [
      { type: 'reasoning', text: 'the keeper keeps a tally behind the lamp room' },
      { type: 'text', text: 'let me count the spare bulbs' },
      { type: 'tool-call', id: 'call_offline_1', name: 'm_probe_buoy', arguments: '{"0":"select count(*) from bulbs"}' },
    ], source: { kind: 'model' } } },
  });
  deliver({ type: 'tool/call', seq: 5, time: now, data: { turn: 1, step: 1, callId: 'call_offline_1', name: 'm_probe_buoy', arguments: '{"0":"select count(*) from bulbs"}' } });
  deliver({
    type: 'tool/result', seq: 6, time: now,
    data: { turn: 1, step: 1, message: { id: 'm3', role: 'user', content: [
      { type: 'tool-result', toolCallId: 'call_offline_1', content: [{ type: 'text', text: 'two spare bulbs on the shelf' }] },
    ], source: { kind: 'tool', callId: 'call_offline_1' } } },
  });
  deliver({
    type: 'assistant/message', seq: 7, time: now,
    data: { turn: 1, step: 2, message: { id: 'm4', role: 'assistant', content: [{ type: 'text', text: 'two spare bulbs, keeper' }], source: { kind: 'model' } } },
  });
  deliver({ type: 'turn/end', seq: 8, time: now, data: { turn: 1, reason: { kind: 'done' } } });
  // dedupe probe: same header again must not re-send
  deliver({
    type: 'request/header', seq: 9, time: now,
    data: { header: { config: {}, system: 'you are the keeper of the harbor light ledger' }, reason: 'change' },
  });

  await new Promise((r) => setTimeout(r, 600));
  const ledger = fake.ledger;
  const kinds = ledger.map((r) => r.kind);
  ok(`phase1 ledger order: ${JSON.stringify(kinds)}`);

  const expectedOrder = ['system', 'user', 'thinking', 'ai', 'tool_result', 'ai'];
  if (expectedOrder.length !== ledger.length || expectedOrder.some((k, i) => ledger[i]?.kind !== k))
    fail(`phase1: expected kinds ${JSON.stringify(expectedOrder)}, got ${JSON.stringify(kinds)}`);

  const ai = ledger[3];
  const tr = ai?.tool_requests?.[0];
  if (!Array.isArray(ai?.tool_requests) || ai.tool_requests.length !== 1 || tr?.name !== 'm_probe_buoy' || tr?.contents !== 'call_offline_1')
    fail(`phase1: ai tool_request identity wrong: ${JSON.stringify(ai?.tool_requests)}`);
  if (ledger[4]?.name !== 'm_probe_buoy' || ledger[4]?.contents !== 'call_offline_1')
    fail(`phase1: tool_result join broken: ${JSON.stringify(ledger[4])}`);

  const needles = [
    'keeper of the harbor light ledger',
    'count the spare bulbs in the store shed',
    'let me count the spare bulbs',
    'the keeper keeps a tally behind the lamp room',
    'two spare bulbs on the shelf',
    'two spare bulbs, keeper',
  ];
  for (const needle of needles)
    if (!ledger.some((r) => String(r.text).includes(needle))) fail(`phase1: ledger missing text: ${needle}`);

  for (const bad of ledger.filter((r) => r.session !== '/usr/dsh/session/offline-probe-1' || r.root !== '/usr/dsh'))
    fail(`phase1: envelope malformed: ${JSON.stringify(bad)}`);

  // the idempotency stamp — every write, the event that produced it
  const expectedSeqs = [2, 3, 4, 4, 6, 7];
  ledger.forEach((r, i) => {
    if (r.attributes?.source !== 'dsh-mirror' || r.attributes?.dsh_seq !== expectedSeqs[i])
      fail(`phase1: stamp wrong on row ${i} (${r.kind}): ${JSON.stringify(r.attributes)} (want dsh_seq=${expectedSeqs[i]})`);
  });

  const wm = await readWatermark(stateDir1, 'offline-probe-1');
  if (wm !== 9) fail(`phase1: watermark should be 9 after the full stream, got ${wm}`);

  await fake.stop();
}

// ═══════════════════════════════ phase 2 — metatron down, then up
{
  const fake = makeFakeMetatron();
  await fake.start(); // up first, so the plugin's url is the real one
  const ctx = makeCtx();
  apply(ctx, { url: fake.url, root: '/usr/dsh', stateDir: stateDir2, readyTimeoutMs: 1200, retryEveryMs: 500 });

  // the durable session log — dsh hands the plugin the Session whose events
  // are the stored log; it grows as live events append. note the compaction
  // summary at seq 3 — its shadowed range (seq 1..2) was already delivered
  // before the sentinel, exactly the append-only shape metatron expects.
  const sessionEvents = [
    { type: 'user/message', seq: 1, time: Date.now(), data: { content: [{ type: 'text', text: 'count the spare bulbs in the store shed' }], source: { kind: 'user' } } },
    { type: 'assistant/message', seq: 2, time: Date.now(), data: { turn: 1, message: { content: [
      { type: 'reasoning', text: 'the keeper keeps a tally behind the lamp room' },
      { type: 'text', text: 'let me count the spare bulbs' },
    ] } } },
    { type: 'compaction/summary', seq: 3, time: Date.now(), data: { compactionId: 'c-durable-1', summary: [{ type: 'text', text: 'summary — one turn: counted two spare bulbs' }], shadowedRange: { start: 1, end: 2 }, shadowedSeqs: [1, 2] } },
    { type: 'user/message', seq: 4, time: Date.now(), data: { content: [{ type: 'text', text: 'keeper, light the fog lamp' }], source: { kind: 'user' } } },
  ];
  const session = { id: 'offline-durable-1', events: sessionEvents };
  const deliver = (event) => {
    if (!sessionEvents.includes(event)) sessionEvents.push(event); // live append
    ctx.emit('session/event', session, event);
  };

  // wait for the handshake, then take metatron DOWN
  await new Promise((r) => setTimeout(r, 500));
  await fake.stop();

  // metatron is DOWN: four live events fire, the first write blocks and
  // fails (ready timeout), the watermark stays at -1
  for (const ev of [...sessionEvents]) deliver(ev);
  await new Promise((r) => setTimeout(r, 1500));
  const downWm = await readWatermark(stateDir2, 'offline-durable-1');
  if (downWm !== -1) fail(`phase2: watermark after outage should still be -1 (nothing acked), got ${downWm}`);
  if (fake.ledger.length !== 0) fail(`phase2: nothing should have reached a down server (${fake.ledger.length} rows)`);

  // metatron comes back → handshake ack wakes the mirror → gap replays
  await fake.start();
  await new Promise((r) => setTimeout(r, 2500));

  const ledger = fake.ledger;
  const kinds = ledger.map((r) => r.kind);
  ok(`phase2 ledger after recovery: ${JSON.stringify(kinds)}`);
  const expectedKinds = ['user', 'thinking', 'ai', 'compaction', 'user'];
  if (expectedKinds.length !== ledger.length || expectedKinds.some((k, i) => ledger[i]?.kind !== k))
    fail(`phase2: expected ${JSON.stringify(expectedKinds)}, got ${JSON.stringify(kinds)}`);

  const sentinel = ledger.find((r) => r.kind === 'compaction');
  if (!sentinel) fail('phase2: compaction sentinel missing from the replay');
  else if (!String(sentinel.text).includes('counted two spare bulbs')) fail(`phase2: sentinel should carry the summary text: ${JSON.stringify(sentinel.text)}`);
  else if (sentinel.attributes?.dsh_seq !== 3) fail(`phase2: sentinel stamp should be seq 3: ${JSON.stringify(sentinel.attributes)}`);

  // the replay must be exactly once per event — no duplicates from the failed
  // live attempts (at-least-once, stamped, and here verifiably exactly-once)
  const dupes = ledger.filter((r) => r.attributes?.dsh_seq !== -1 && ledger.filter((o) => o.attributes?.dsh_seq === r.attributes?.dsh_seq && o.session === r.session && o.kind === r.kind).length > 1);
  if (dupes.length) fail(`phase2: duplicate replays detected: ${JSON.stringify(dupes)}`);

  let wm = await readWatermark(stateDir2, 'offline-durable-1');
  if (wm !== 4) fail(`phase2: watermark after full replay should be 4, got ${wm}`);

  // second outage: one more live event while metatron is down again
  await fake.stop();
  deliver({ type: 'user/message', seq: 5, time: Date.now(), data: { content: [{ type: 'text', text: 'and log it in the keeper ledger' }], source: { kind: 'user' } } });
  await new Promise((r) => setTimeout(r, 1500));
  wm = await readWatermark(stateDir2, 'offline-durable-1');
  if (wm !== 4) fail(`phase2: watermark must hold at 4 through the second outage, got ${wm}`);
  if (fake.ledger.length !== 5) fail(`phase2: no new rows during the second outage (${fake.ledger.length})`);

  // recovery again → the single missed event replays
  await fake.start();
  await new Promise((r) => setTimeout(r, 2500));
  wm = await readWatermark(stateDir2, 'offline-durable-1');
  if (wm !== 5) fail(`phase2: watermark should reach 5 after second recovery, got ${wm}`);
  if (fake.ledger.length !== 6) fail(`phase2: exactly one more row after second recovery (6), got ${fake.ledger.length}`);
  const last = fake.ledger[fake.ledger.length - 1];
  if (last?.kind !== 'user' || !String(last?.text).includes('keeper ledger')) fail(`phase2: second-recovery row wrong: ${JSON.stringify(last)}`);

  await fake.stop();
}

if (failures > 0) {
  console.error(`[offline] FAIL: ${failures} assertion(s) failed`);
  process.exit(1);
}
ok('PASS: live path ordered + stamped + watermarked; outages hold the watermark; gaps replay in order with the compaction sentinel exactly once');
process.exit(0);
