---
name: mcp-server-notifications
description: >
  Implementing MCP server→client notifications in pure mtron using subq subscriptions on WebSocket spaces.
  TRIGGER: When working with MCP servers, WebSocket space subq processors, server→client push patterns,
  JSON-RPC 2.0 envelope construction, json:: type prefix, or declarative pub/sub in mtron spaces.
  Also trigger when the user mentions MCP notifications, server-initiated messages, or subq-based routing.
---

# MCP Server→Client Notifications in mtron

## Architecture

MCP is client-initiated, but metatron's `mcp_wsServer` can send server→client notifications via the `send()` method inherited from `WebSocketRec` → `WebSocketObj`. The entire implementation is declarative mtron — zero Java.

**Pattern**: A `subq` subscription on the WebSocket space intercepts writes to a per-instance notification URI subtree, builds a JSON-RPC 2.0 envelope, and sends it to the MCP client via the WebSocket connection.

```
                   wsspace::[=>]@/sys/space/web/ws
anywhere            ┌──────────────────────────┐                mcp client
┌─────────┐  write  │ ?subq:            ws://# │  send()      ┌────────────┐
│ payload ├────────►│ +/notifications/#        ├─────────────►│ intellij/  │
└─────────┘         │                          │ JSON-RPC 2.0 │ claude/... │
                    │ on_recv:                 │              └────────────┘
/mcp/0/notifications│..-<json::[jsonrpc=>...]  │
                    │    *srv>>>send(*payload) │
                    └──────────────────────────┘
```

## Canonical Form: `.inst()` Split-Pattern

```mtron
<ws://localhost:8555/mcp/+/notifications/#?subq> -> sub::[
   on_recv => inst?#<=lst(message=>?lst::T){
     -<json::[jsonrpc => '2.0',
        method  => >>0 - <ws://localhost:8555/mcp/${*message>>0>>1}${/}>,
        params  => *message>>1].inst(payload=>_){
          *<ws://localhost:8555/mcp/${*message>>0>>1}>>>send.apply(*payload)}}]
```

This lives in `boot/boot.mtron` lines 82-88 and is injected at boot time.

## Key Mechanics

### 1. Per-instance URI extraction
```mtron
${*message>>0>>1}
```
Extracts the MCP client instance ID from the URI path. Writing to `/mcp/0/notifications/abc` yields instance `0`.

### 2. Method name subtraction
```mtron
>>0 - <ws://localhost:8555/mcp/${*message>>0>>1}${/}>
```
Subtracts the server prefix from the full URI to produce a bare method name (e.g., `notifications/resources/updated`).

### 3. `-<` forces evaluation
The `-<` prefix on `json::[...]` is **critical**. Without it, inner template interpolations (`>>0 - ...`, `*message>>1`) are captured as LITERAL SOURCE CODE, not evaluated values.

### 4. `.inst()` split-pattern
The `-<json::[...]` evaluates to a JSON rec, `.inst(payload=>_){...}` scopes it to a reference variable, and `*payload` passes it cleanly to `send.apply()`. This split avoids the evaluator's nested-call limitation.

### 5. `json::` type prefix
Ensures JSON serialization (`{"jsonrpc":"2.0",...}`) rather than mtron record syntax.

## Development Pitfalls

### Pitfall 1: `json::[...]` without `-<`
```mtron
# WRONG — sends literal source code, not evaluated JSON
json::[jsonrpc => '2.0', params => *message>>1]
```
**Symptom**: Client receives raw expression text instead of evaluated values.
**Fix**: Use `-<json::[...]` to force evaluation.

### Pitfall 2: Nested `-<json::[...]` in `.apply()`
```mtron
# WRONG — parser can't resolve nested call structure
*srv>>>send.apply(-<json::[...])
```
**Symptom**: `fail::[unable to determine inst function:]`
**Fix**: Separate JSON builder from send call using `.inst()` split-pattern.

### Pitfall 3: Subscriptions are in-memory
`subq` subscriptions live only in runtime memory. They must be recreated at boot. Parser crashes also require re-injection:
```mtron
# Recover without full restart:
*/sys/space/web/ws>>=[q=>[subq::[=>]]]              # re-enable subq processor
<ws://.../?subq> -> sub::[...]                       # re-register subscription
```

## Boot Integration

The `subq` framework must be enabled on the WebSocket space before subscriptions can be registered:
```mtron
wsspace::[q => [subq::[=>]], ...]@/sys/space/web/ws
```

The `q => [subq::[=>]]` adds declarative pub/sub semantics to the space.

## MCP Notification Methods

| Method | Description |
|--------|-------------|
| `notifications/resources/updated` | Subscribed resource content changed |
| `notifications/resources/list_changed` | Available resource list changed |
| `notifications/tools/list_changed` | Available tool list changed |
| `notifications/prompts/list_changed` | Available prompt list changed |
| `notifications/logging/message` | Server log output for client |
| `notifications/roots/list_changed` | Filesystem roots accessible to server changed |

The `/` in method names is namespacing, not hierarchy. All signal "re-read what you already know."

## Quick Reference

| Task | Expression |
|------|------------|
| Enable subq on wsspace | `wsspace::[q=>[subq::[=>]]]@/sys/space/web/ws` |
| Inject subscription | `<ws://.../mcp/+/notifications/#?subq> -> sub::[...]` |
| Write notification | Write to `ws://.../mcp/0/notifications/xyz` |
| Recover from crash | Re-run enable + inject (no restart needed) |