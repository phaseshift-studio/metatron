/**
 * metatron-mirror — a deepseek-harness (cordis) plugin. DURABLE.
 *
 * Routes dsh chat-lifecycle events into metatron's message ledger so the
 * harness conversation is stored in metatron's uri space:
 *
 *   user/message       -> kind=user            (name carries the producer for injected context)
 *   assistant/message  -> kind=ai              (text blocks)  + kind=thinking (reasoning blocks)
 *                         tool-call blocks     -> ai.tool_requests [{name,args,contents,text}]
 *   tool/result        -> kind=tool_result     (name=tool, contents=call id, text=result)
 *   request/header     -> kind=system          (header.system, deduped per session)
 *   compaction/summary -> kind=compaction      (the metatron compaction SENTINEL: text =
 *                                              the resume summary; compaction/start|end
 *                                              are bracket bookkeeping and stay out)
 *
 * Transport is plain JSON-RPC over WebSocket against metatron's mcp_message
 * (default ws://127.0.0.1:8555/message), the same handler dsh-mcp-client
 * bridges through websocat.
 *
 * Durable delivery (at-least-once):
 *   - every event carries a monotonic dsh session seq; a per-session
 *     WATERMARK (last acked seq) persists in a small json file and advances
 *     ONLY after metatron acks the write (add_message returns the written rec)
 *   - a send failure leaves the watermark behind; the gap is replayed — in
 *     seq order, from the watermark — when the connection recovers (handshake
 *     ack) and on a timer, so the ledger converges even across metatron
 *     downtime and plugin restarts
 *   - catch-up reads the session's durable log (session.events — a resumed
 *     session carries its full stored log as its seed), so first contact with
 *     a session bootstraps everything not yet mirrored
 *   - every write is stamped with attributes {source: "dsh-mirror",
 *     dsh_seq: <n>} so an at-least-once redelivery is recognizable (a session
 *     id is already in the ledger's session envelope)
 *   - dsh compaction is mirrored at the sentinel: a compaction/summary event
 *     becomes a kind=compaction record at its log position, which keeps the
 *     ledger's stopAt(compaction) live-window semantics correct (the shadowed
 *     raw events were already delivered before the sentinel — append-only)
 *   - the mirror is fire-and-forget from the chat loop's view: queued,
 *     self-healing, never blocks or rejects dsh
 *
 * config (optional; env fallbacks METATRON_MIRROR_WS / METATRON_MIRROR_ROOT /
 * METATRON_MIRROR_STATE_DIR / METATRON_MIRROR_READY_TIMEOUT_MS):
 *   url             — websocket endpoint of the metatron mcp message server
 *   root            — ledger root (records land at <root>/message/_?incrq)
 *   enabled         — set false to load the plugin inert
 *   stateDir        — where watermark.json lives (default ~/.metatron/metatron-mirror)
 *   readyTimeoutMs  — how long one write may wait for a live connection (default 30000)
 *   retryEveryMs    — catch-up re-wake interval (default 15000)
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

export const name = 'metatron-mirror';
export const inject: string[] = [];

// ─────────────────────────────────────────────────────────────── config

const DEFAULT_URL = 'ws://127.0.0.1:8555/message';
const DEFAULT_ROOT = '/usr/dsh';
const ADD_MESSAGE_TOOL = 'm_llm_mcp_mcp_message_add_message';

interface Config {
  url: string;
  root: string;
  enabled: boolean;
  stateDir: string;
  readyTimeoutMs: number;
  retryEveryMs: number;
}

function resolveConfig(config: unknown): Config {
  const raw = (config ?? {}) as Record<string, unknown>;
  const url = String(raw.url ?? process.env.METATRON_MIRROR_WS ?? DEFAULT_URL);
  const root = String(raw.root ?? process.env.METATRON_MIRROR_ROOT ?? DEFAULT_ROOT).replace(/\/+$/, '');
  const enabled = raw.enabled === undefined ? true : raw.enabled === true;
  const stateDir = String(
    raw.stateDir ?? process.env.METATRON_MIRROR_STATE_DIR ?? path.join(os.homedir(), '.metatron', 'metatron-mirror'),
  );
  const num = (v: unknown, fallback: number): number => {
    const n = typeof v === 'string' ? Number(v) : (v as number);
    return typeof n === 'number' && Number.isFinite(n) && n > 0 ? Math.floor(n) : fallback;
  };
  const readyTimeoutMs = num(
    raw.readyTimeoutMs ?? process.env.METATRON_MIRROR_READY_TIMEOUT_MS,
    30_000,
  );
  const retryEveryMs = num(raw.retryEveryMs, 15_000);
  return { url, root, enabled, stateDir, readyTimeoutMs, retryEveryMs };
}

// ─────────────────────────────────────────────────────────── logging

function makeLog(ctx: unknown): (line: string) => void {
  const c = (ctx ?? {}) as {
    log?: { info?: (m: string) => void; warn?: (m: string) => void; error?: (m: string) => void };
  };
  return (line: string) => {
    const l = `[metatron-mirror] ${line}`;
    try {
      c.log?.warn?.(l);
    } catch {
      /* noop */
    }
  };
}

// ─────────────────────────────────────────────── session-id sanitization

/** One uri-safe segment per session id (ledger vids are <root>/session/<id>). */
export function sanitizeSegment(value: string): string {
  return String(value)
    .split('/')
    .filter((s) => s.length > 0)
    .map((s) => s.replace(/[^A-Za-z0-9._-]/g, '_'))
    .join('/') || 'default';
}

// ─────────────────────────────────────────────────────── event mapping

interface MirrorState {
  /** call id -> tool name (joined ai.tool_requests with tool_result). */
  callNames: Map<string, string>;
  /** last system prompt text sent for this session (dedupe request/header). */
  lastSystem: string | null;
  /** latest observed turn (chat_id envelope field). */
  lastTurn: number | null;
}

export interface LedgerSend {
  kind: 'user' | 'ai' | 'system' | 'thinking' | 'tool_result' | 'compaction';
  text: string;
  name?: string;
  contents?: string;
  chat_id?: number;
  tool_requests?: { name: string; args: string; contents: string; text: string }[];
  /** extra ledger rec fields (merge into the written message). */
  attributes?: Record<string, unknown>;
}

function blockText(blocks: readonly unknown[] | undefined | null): string {
  if (!Array.isArray(blocks)) return '';
  return blocks
    .filter((b): b is { type: string; text: string } => !!b && (b as { type?: string }).type === 'text')
    .map((b) => String((b as { text?: unknown }).text ?? ''))
    .filter((t) => t.length > 0)
    .join('\n');
}

/**
 * Map one dsh session event to zero or more metatron ledger sends.
 * Pure (no transport): mutates only the per-session `state`.
 */
export function mapEvent(state: MirrorState, event: { type?: string; data?: any }): LedgerSend[] | null {
  const type = event?.type;
  const data = event?.data ?? {};
  switch (type) {
    case 'turn/start': {
      state.lastTurn = data.turn ?? state.lastTurn;
      return null;
    }
    case 'user/message': {
      const text = blockText(data.content);
      if (!text) return null;
      const send: LedgerSend = { kind: 'user', text };
      const source = data.source as { kind?: string; plugin?: string } | undefined;
      if (source?.kind === 'plugin') send.name = `plugin:${source.plugin ?? 'unknown'}`;
      if (state.lastTurn != null) send.chat_id = state.lastTurn;
      return [send];
    }
    case 'assistant/message': {
      if (data.turn != null) state.lastTurn = data.turn;
      const message = data.message ?? {};
      const blocks: readonly any[] = Array.isArray(message.content) ? message.content : [];
      const text = blocks
        .filter((b) => b?.type === 'text')
        .map((b) => String(b?.text ?? ''))
        .filter((t) => t.length > 0)
        .join('\n');
      const reasoning = blocks
        .filter((b) => b?.type === 'reasoning')
        .map((b) => String(b?.text ?? ''))
        .filter((t) => t.length > 0)
        .join('\n');
      const calls = blocks
        .filter((b) => b?.type === 'tool-call')
        .map((b) => ({
          name: String(b?.name ?? 'unknown'),
          args: String(b?.arguments ?? ''),
          contents: String(b?.id ?? ''),
          text: `${b?.name ?? 'unknown'}(${b?.arguments ?? ''})`,
        }));
      for (const c of calls) if (c.contents) state.callNames.set(c.contents, c.name);

      // ledger order follows the conversation: reasoning precedes the reply
      const sends: LedgerSend[] = [];
      if (reasoning.length > 0) {
        const send: LedgerSend = { kind: 'thinking', text: reasoning };
        if (state.lastTurn != null) send.chat_id = state.lastTurn;
        sends.push(send);
      }
      if (text.length > 0 || calls.length > 0) {
        const send: LedgerSend = { kind: 'ai', text: text || calls.map((c) => c.text).join(', ') };
        if (calls.length > 0) send.tool_requests = calls;
        if (state.lastTurn != null) send.chat_id = state.lastTurn;
        sends.push(send);
      }
      return sends.length > 0 ? sends : null;
    }
    case 'tool/call': {
      // name registry for the matching tool_result (the call itself already
      // travels inside the ai message's tool_requests)
      if (data.callId) state.callNames.set(String(data.callId), String(data.name ?? 'unknown'));
      if (data.turn != null) state.lastTurn = data.turn;
      return null;
    }
    case 'tool/result': {
      if (data.turn != null) state.lastTurn = data.turn;
      const message = data.message ?? {};
      const block = Array.isArray(message.content) ? message.content[0] : undefined;
      const callId = block?.toolCallId ? String(block.toolCallId) : '';
      const body = block ? blockText(block.content) : '';
      const err = data.error ? ` [${data.error.name ?? 'error'}: ${data.error.code ?? '?'}]` : '';
      if (!body && !err) return null;
      const send: LedgerSend = {
        kind: 'tool_result',
        text: body + err,
        name: (callId && state.callNames.get(callId)) || 'unknown_tool',
      };
      if (callId) send.contents = callId;
      if (state.lastTurn != null) send.chat_id = state.lastTurn;
      return [send];
    }
    case 'request/header': {
      const system = data.header?.system;
      if (typeof system !== 'string' || system.length === 0) return null;
      if (state.lastSystem === system) return null;
      state.lastSystem = system;
      return [{ kind: 'system', text: system }];
    }
    case 'compaction/summary': {
      // the compaction sentinel — dsh summarized the shadowed range; the
      // ledger writes the same sentinel shape metatron's own compaction uses
      // (message/compaction, text = resume summary), so stopAt(compaction)
      // bounds the mirrored live window exactly as it does a native one
      const text = blockText(data.summary);
      return text.length > 0 ? [{ kind: 'compaction', text }] : null;
    }
    case 'compaction/start':
    case 'compaction/end':
    case 'compaction/prune':
      // bracket bookkeeping / model-free prunes — not ledger material in v1
      return null;
    default:
      // turn/end, step/*, assistant/chunk, todo/write, request/context,
      // session/create, session/end-seed — structural or redundant
      return null;
  }
}

// ─────────────────────────────────────────────────────── watermark store

/**
 * Per-session last-acked dsh seq, durably (json, atomic rename). The ledger
 * is at-least-once: the watermark only ever advances on a genuine ack, so
 * anything below it is guaranteed delivered and anything above it is replayed
 * on the next wake. A lost write just means more replay, never loss.
 */
export class WatermarkStore {
  private readonly file: string;
  private readonly wms = new Map<string, number>();

  constructor(dir?: string) {
    this.file = path.join(dir ?? os.homedir(), 'watermark.json');
    this.load();
  }

  /** last acked seq for the session; -1 for nothing yet. */
  get(sid: string): number {
    return this.wms.get(sid) ?? -1;
  }

  /** record an ack; ignores backward moves; persists. */
  set(sid: string, seq: number): void {
    if (!Number.isFinite(seq) || seq <= (this.wms.get(sid) ?? -1)) return;
    this.wms.set(sid, Math.floor(seq));
    this.save();
  }

  get size(): number {
    return this.wms.size;
  }

  private load(): void {
    try {
      const obj: Record<string, unknown> = JSON.parse(fs.readFileSync(this.file, 'utf8'));
      for (const [k, v] of Object.entries(obj)) {
        if (typeof v === 'number' && Number.isFinite(v)) this.wms.set(k, v);
      }
    } catch {
      /* fresh start — nothing mirrored yet */
    }
  }

  private save(): void {
    try {
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      const tmp = `${this.file}.tmp`;
      fs.writeFileSync(tmp, JSON.stringify(Object.fromEntries(this.wms), null, 2));
      fs.renameSync(tmp, this.file);
    } catch {
      /* best effort — losing a watermark only costs an extra replay later */
    }
  }
}

// ─────────────────────────────────────────────── websocket json-rpc client

interface PendingCall {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
}

export interface LedgerClientOptions {
  /** how long one write may wait for a live, handshaken connection. */
  readyTimeoutMs?: number;
  /** fires on every successful handshake (first connect + reconnects). */
  onReady?: () => void;
}

/**
 * Ordered, self-healing json-rpc sender over one websocket.
 * A single promise chain preserves ledger order. `send` resolves when
 * metatron acks the write and REJECTS when it could not be delivered — the
 * durable layer uses that rejection to hold the watermark.
 */
export class LedgerClient {
  private readonly url: string;
  private readonly log: (line: string) => void;
  private readonly readyTimeoutMs: number;
  private readonly onReady?: () => void;
  private ws: { readyState?: number; send: (s: string) => void; close?: () => void } | null = null;
  private handshakeId: number | null = null;
  private readonly pending = new Map<number, PendingCall>();
  private nextId = 1;
  private chain: Promise<void> = Promise.resolve();
  private ready: { resolve: () => void; reject: (e: Error) => void } | null = null;
  private readyPromise = new Promise<unknown>((resolve, reject) => {
    this.ready = { resolve, reject };
  });
  private backoffMs = 250;
  private disposed = false;
  private connectAttempts = 0;

  constructor(url: string, log: (line: string) => void, opts?: LedgerClientOptions) {
    this.url = url;
    this.log = log;
    this.readyTimeoutMs = opts?.readyTimeoutMs ?? 30_000;
    this.onReady = opts?.onReady;
    void this.connect();
  }

  /**
   * Enqueue one ledger write. Resolves on metatron's ack; rejects when the
   * write could not be delivered (timeout, rpc error, tool error). The
   * internal chain swallows the rejection, so one failure never wedges later
   * sends — the durable layer retries from the watermark instead.
   */
  send(args: Record<string, unknown>): Promise<void> {
    const step = this.chain.then(() => this.call(ADD_MESSAGE_TOOL, args));
    this.chain = step.then(
      () => undefined,
      (error: unknown) => {
        this.log(`write not delivered (watermark held, will replay): ${String(error)}`);
      },
    );
    return step;
  }

  dispose(): void {
    this.disposed = true;
    try {
      this.ws?.close?.();
    } catch {
      /* noop */
    }
    this.ready?.reject(new Error('disposed'));
  }

  private async call(tool: string, args: Record<string, unknown>): Promise<void> {
    await Promise.race([
      this.readyPromise,
      new Promise<never>((_, reject) => setTimeout(() => reject(new Error(`server unavailable (${this.readyTimeoutMs}ms)`)), this.readyTimeoutMs)),
    ]);
    const id = this.nextId++;
    const body = JSON.stringify({ jsonrpc: '2.0', id, method: 'tools/call', params: { name: tool, arguments: args } });
    const result = await new Promise<unknown>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`tools/call timed out (10s): ${tool}`));
      }, 10_000);
      this.pending.set(id, {
        resolve: (v) => {
          clearTimeout(timer);
          resolve(v);
        },
        reject: (e) => {
          clearTimeout(timer);
          reject(e);
        },
      });
      try {
        this.ws?.send(body);
      } catch (e) {
        this.pending.delete(id);
        clearTimeout(timer);
        reject(e instanceof Error ? e : new Error(String(e)));
      }
    });
    // mcp tool-level errors ride in the result (isError + content text)
    const payload = (result ?? {}) as {
      isError?: boolean;
      content?: { type?: string; text?: string }[];
    };
    if (payload.isError) {
      const detail = (payload.content ?? []).map((b) => b?.text).filter(Boolean).join(' ').trim();
      throw new Error(`metatron tool error${detail ? `: ${detail}` : ''}`);
    }
  }

  private connect(): void {
    if (this.disposed) return;
    let socket: any;
    try {
      socket = new WebSocket(this.url);
    } catch (e) {
      this.log(`connect failed: ${String(e)} — retrying`);
      setTimeout(() => void this.connect(), this.nextBackoff());
      return;
    }
    this.ws = socket;
    const arm = () => {
      socket.addEventListener?.('open', () => this.onOpen());
      socket.addEventListener?.('message', (m: { data?: unknown }) => this.onMessage(m?.data));
      socket.addEventListener?.('close', () => this.onDown());
      socket.addEventListener?.('error', () => {
        try {
          socket.close?.(1011);
        } catch {
          /* noop */
        }
      });
    };
    if (typeof socket.addEventListener === 'function') arm();
    else {
      socket.onopen = () => this.onOpen();
      socket.onmessage = (m: { data?: unknown }) => this.onMessage(m?.data);
      socket.onclose = () => this.onDown();
      socket.onerror = () => {
        try {
          socket.close?.(1011);
        } catch {
          /* noop */
        }
      };
    }
  }

  private nextBackoff(): number {
    const next = Math.min(this.backoffMs, 15_000);
    this.backoffMs = Math.min(this.backoffMs * 2, 15_000);
    this.connectAttempts++;
    return next;
  }

  private onOpen(): void {
    this.connectAttempts = 0;
    this.backoffMs = 250;
    this.handshakeId = this.nextId++;
    const body = JSON.stringify({
      jsonrpc: '2.0',
      id: this.handshakeId,
      method: 'initialize',
      params: { protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'metatron-mirror', version: '1' } },
    });
    try {
      this.ws?.send(body);
    } catch (e) {
      this.log(`initialize send failed: ${String(e)}`);
    }
  }

  private onDown(): void {
    this.ws = null;
    for (const p of this.pending.values()) p.reject(new Error('connection closed'));
    this.pending.clear();
    this.readyPromise = new Promise<unknown>((resolve, reject) => {
      this.ready = { resolve, reject };
    });
    if (!this.disposed) {
      this.log('connection lost — reconnecting');
      setTimeout(() => void this.connect(), this.nextBackoff());
    }
  }

  private onMessage(data: unknown): void {
    let frame: any;
    try {
      frame = typeof data === 'string' ? JSON.parse(data) : (data as any);
    } catch {
      return;
    }
    if (frame?.id == null) return;
    const id = Number(frame.id);
    // the initialize handshake ack — resolves the ready gate for all sends
    if (id === (this.handshakeId as number | null) && this.ready) {
      const ready = this.ready;
      this.ready = null;
      if (frame.error) ready.reject(new Error(`initialize failed: ${frame.error.message ?? 'unknown'}`));
      else {
        ready.resolve(true);
        try {
          this.onReady?.();
        } catch (e) {
          this.log(`onReady hook error (continued): ${String(e)}`);
        }
      }
      return;
    }
    const p = this.pending.get(id);
    if (!p) return;
    this.pending.delete(id);
    if (frame.error) p.reject(new Error(`rpc error ${frame.error.code ?? ''}: ${frame.error.message ?? 'unknown'}`));
    else p.resolve(frame.result);
  }
}

// ──────────────────────────────────────────────────── durable core + apply

interface SessionBook {
  /** sanitized sid — the ledger envelope key. */
  sid: string;
  /** raw dsh session id — used to re-locally the Session object when one is
   *  available without a live event (SessionStore.get). */
  rawId: string;
  state: MirrorState;
  /** latest dsh Session seen for the book (its events = the durable log). */
  session: any;
  /** serialized delivery — one session's events ack in seq order. */
  queue: Promise<void>;
  /** a delivery is failing (metatron down); stop re-queueing from live
   *  events until a wake (handshake/timeout) resumes it. */
  paused: boolean;
}

export function apply(ctx: unknown, config?: unknown): void {
  const cfg = resolveConfig(config);
  const log = makeLog(ctx);
  if (!cfg.enabled) {
    log('disabled by config');
    return;
  }

  const store = new WatermarkStore(cfg.stateDir);
  const books = new Map<string, SessionBook>();

  const c = ctx as { on?: (name: string, listener: (session: any, event: any) => void) => unknown };
  if (typeof c?.on !== 'function') {
    log('ctx.on unavailable — cannot subscribe to session events');
    return;
  }

  // the durable core — delivery with watermark, catch-up, and wakes

  const bookFor = (rawId: string): SessionBook => {
    const sid = sanitizeSegment(rawId);
    let book = books.get(sid);
    if (!book) {
      book = { sid, rawId, state: { callNames: new Map(), lastSystem: null, lastTurn: null }, session: null, queue: Promise.resolve(), paused: false };
      books.set(sid, book);
    }
    return book;
  };

  const deliverEvent = async (book: SessionBook, ev: any): Promise<void> => {
    let sends: LedgerSend[] | null = null;
    try {
      sends = mapEvent(book.state, ev);
    } catch (e) {
      log(`mapping skipped for ${String(ev?.type ?? '?')}: ${String(e)}`);
    }
    if (sends) {
      for (const send of sends) {
        // the idempotency stamp — at-least-once redeliveries stay recognizable
        await client.send({
          root: cfg.root,
          session: `${cfg.root}/session/${book.sid}`,
          ...send,
          attributes: { ...(send.attributes ?? {}), source: 'dsh-mirror', dsh_seq: Number(ev?.seq ?? -1) },
        });
      }
    }
    // the event is consumed whether or not it produced writes — the watermark
    // must pass structural and skipped events, not just ledger-visible ones
    store.set(book.sid, Number(ev?.seq ?? -1));
  };

  const deliverRange = async (book: SessionBook, trigger: any | null): Promise<void> => {
    const w = store.get(book.sid);
    const seq = (e: any): number => Number((e as { seq?: unknown })?.seq);
    const snapshot: any[] = Array.isArray(book.session?.events) ? book.session.events : [];
    // the unacked window: (watermark, trigger] — from the durable log (the
    // trigger may not be in the snapshot yet, so add it if it is beyond it)
    const candidates = snapshot
      .filter((e) => Number.isFinite(seq(e)) && seq(e) > w && (trigger == null ? true : seq(e) < seq(trigger)))
      .sort((a, b) => seq(a) - seq(b));
    if (trigger != null && Number.isFinite(seq(trigger)) && seq(trigger) > w) candidates.push(trigger);
    if (candidates.length > 0)
      log(`catch-up ${book.sid}: playing ${candidates.length} unacked event(s) after seq ${w}`);
    for (const ev of candidates) await deliverEvent(book, ev);
  };

  const enqueue = (book: SessionBook, task: () => Promise<void>): void => {
    if (book.paused) return; // a wake (onReady/timer) owns the retry
    book.queue = book.queue.then(task).catch((error: unknown) => {
      book.paused = true;
      log(`delivery paused for ${book.sid} — will replay on recovery: ${String(error)}`);
    });
  };

  const lastSeqOf = (book: SessionBook): number => {
    const snapshot: any[] = Array.isArray(book.session?.events) ? book.session.events : [];
    let last = -1;
    for (const e of snapshot) {
      const s = Number((e as { seq?: unknown })?.seq);
      if (Number.isFinite(s) && s > last) last = s;
    }
    return last;
  };

  const catchUpAll = (): void => {
    // re-locate a lost Session reference when the dsh store still holds it
    const sessionsService = (ctx as { get?: (id: string) => unknown })?.get?.('sessions') as {
      get?: (id: string) => unknown;
    } | undefined;
    for (const book of books.values()) {
      if (!book.session && sessionsService?.get) {
        try {
          book.session = sessionsService.get(book.rawId) ?? null;
        } catch {
          /* keep the book as-is */
        }
      }
      const gap = lastSeqOf(book) > store.get(book.sid);
      if (!gap) continue;
      book.paused = false; // resume — the queue replays the entire gap
      book.queue = book.queue
        .then(() => deliverRange(book, null))
        .catch((error: unknown) => {
          book.paused = true;
          log(`catch-up failed for ${book.sid} — will retry: ${String(error)}`);
        });
    }
  };

  const client = new LedgerClient(cfg.url, log, {
    readyTimeoutMs: cfg.readyTimeoutMs,
    onReady: () => {
      log('metatron reachable — replaying unacked events');
      catchUpAll();
    },
  });

  c.on.call(ctx, 'session/event', (session: any, event: any) => {
    try {
      const book = bookFor(String(session?.id ?? 'default'));
      book.session = session ?? book.session;
      enqueue(book, () => deliverRange(book, event));
    } catch (e) {
      log(`listener error (session continued): ${String(e)}`);
    }
  });

  // re-wake on a timer (covers a recovery the handshake hook missed) — unref'd
  // so a quiet process can still exit
  const timer = setInterval(() => {
    try {
      catchUpAll();
    } catch (e) {
      log(`catch-up tick error (continued): ${String(e)}`);
    }
  }, cfg.retryEveryMs);
  (timer as { unref?: () => void })?.unref?.();

  // close the socket when the plugin fiber unloads (hmr, profile switch),
  // and as a fallback when the process itself exits
  const dispose = (): void => {
    clearInterval(timer);
    client.dispose();
  };
  const c2 = ctx as { effect?: (execute: () => unknown) => unknown };
  if (typeof c2?.effect === 'function') {
    try {
      c2.effect(() => dispose);
    } catch (e) {
      log(`effect registration failed (continuing): ${String(e)}`);
    }
  }
  (process as any)?.exitHooks?.on?.('beforeExit', () => dispose());

  log(`routing chat lifecycle -> metatron ledger (ws=${cfg.url}, root=${cfg.root}, watermark=${store.size} session(s) on file)`);
}
