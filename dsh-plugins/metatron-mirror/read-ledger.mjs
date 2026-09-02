#!/usr/bin/env node
// read-ledger.mjs — read a dsh-mirrored conversation back from the live metatron
// ledger without any other moving parts.
//
//   node read-ledger.mjs <sessionId> [max]
//
// env: METATRON_MIRROR_WS (default ws://127.0.0.1:8555/message)
//      METATRON_MIRROR_ROOT (default /usr/dsh)
import { setTimeout as sleep } from 'node:timers/promises';

const ROOT = (process.env.METATRON_MIRROR_ROOT ?? '/usr/dsh').replace(/\/+$/, '');
const URL = process.env.METATRON_MIRROR_WS ?? 'ws://127.0.0.1:8555/message';
const SESSION = process.argv[2];
const MAX = Number(process.argv[3] ?? 50);
if (!SESSION) {
  console.error('usage: node read-ledger.mjs <sessionId> [max]');
  process.exit(2);
}

const ws = new WebSocket(URL);
let seq = 0;
const pending = new Map();
const wait = (id, ms) => new Promise((res, rej) => {
  const t = setTimeout(() => rej(new Error(`rpc ${id} timed out`)), ms);
  pending.set(id, (v) => { clearTimeout(t); pending.delete(id); res(v); });
});
let ready = null;
const readyResolve = (v) => (ready ??= v);

const send = (method, params, ms = 10000) => {
  const id = ++seq;
  return new Promise((resolve, reject) => {
    pending.set(id, (v) => { clearTimeout(t); pending.delete(id); resolve(v); });
    const t = setTimeout(() => { pending.delete(id); reject(new Error(`${method} timed out`)); }, ms);
    try { ws.send(JSON.stringify({ jsonrpc: '2.0', id, method, params })); } catch (e) { clearTimeout(t); reject(e); }
  });
};
const rpc = (method, params, ms) => send(method, params, ms).then((r) => {
  if (r.error) throw new Error(`${method}: ${JSON.stringify(r.error)}`);
  return r.result;
});
const text = (result) => (result?.content ?? []).map((c) => c?.text ?? '').join('\n');

ws.onopen = () => {
  void send('initialize', { clientInfo: { name: 'read-ledger', version: '1' }, protocol: 'mcp' }, 8000)
    .then(() => readyResolve('ready'))
    .catch((e) => readyResolve(e));
};
const settleReady = () => new Promise((res) => {
  let n = 0;
  const iv = setInterval(() => {
    if (ready !== null || ready === undefined && n++ > 50) { clearInterval(iv); res(ready); }
  }, 100);
  setTimeout(() => { clearInterval(iv); res(ready); }, 5000);
});
ws.onmessage = (m) => {
  const frame = JSON.parse(String(m.data));
  if (frame.id != null && pending.has(frame.id)) pending.get(frame.id)(frame);
};
ws.onerror = (e) => console.error('[ws] error', e?.message ?? e);
ws.onclose = (e) => console.error(`[ws] closed code=${e.code}`);

const result = await new Promise((resolve, reject) => {
  const t = setTimeout(() => reject(new Error('socket did not open')), 8000);
  ws.addEventListener('open', () => {
    setTimeout(async () => {
      try {
        await send('initialize', { clientInfo: { name: 'read-ledger', version: '1' }, protocol: 'mcp' }, 8000);
        resolve();
      } catch (e) { reject(e); } finally { clearTimeout(t); }
    }, 50);
  });
  ws.addEventListener('error', () => { clearTimeout(t); reject(new Error('ws error during open')); });
});

const out = await rpc('tools/call', {
  name: 'm_llm_mcp_mcp_message_get_messages',
  arguments: { root: ROOT, session: `${ROOT}/session/${SESSION}`, max: MAX },
}, 15000);
const body = text(out);
if (!body.trim()) {
  console.log(`(no ledger entries for session ${SESSION} under ${ROOT})`);
} else {
  console.log(body);
}
ws.close();
await sleep(200);
