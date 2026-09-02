/**
 * compaction-probe.mjs — one-shot live probe: does the running metatron
 * server (8555) accept kind=compaction yet? Writes a sentinel to a scratch
 * session and reports.
 *
 *   node compaction-probe.mjs
 */
import { LedgerClient } from './metatron-mirror.ts';

const WS = process.env.METATRON_MIRROR_WS ?? 'ws://127.0.0.1:8555/message';
const ROOT = process.env.METATRON_MIRROR_ROOT ?? '/usr/dsh';
const log = (l) => console.log('[probe]', l);
const client = new LedgerClient(WS, log);
try {
  await client.send({
    root: ROOT,
    session: `${ROOT}/session/compaction-probe`,
    kind: 'compaction',
    text: 'resume — the keeper tallied two spare bulbs and the log is safe',
    attributes: { source: 'probe', dsh_seq: 99 },
  });
  console.log('[probe] live server ACCEPTS kind=compaction');
} catch (e) {
  console.log(`[probe] live server REJECTS kind=compaction: ${e.message}`);
}
process.exit(0);
