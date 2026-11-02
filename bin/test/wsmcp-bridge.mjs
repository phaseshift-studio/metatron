#!/usr/bin/env node
/**
 * wsmcp-bridge.mjs — a minimal stdio <-> WebSocket byte bridge, i.e. the
 * `websocat <ws-url>` shim that @deepseek-ai/dsh-mcp-client needs for a
 * WebSocket MCP server (metatron exposes MCP over ws://...:8555/mcp, and the
 * client's stdio transport spawns a process and speaks newline-delimited
 * JSON-RPC on its stdin/stdout).
 *
 * This exists because the container has no websocat, no cargo and no pip, but
 * Node 24 ships a global WebSocket. It is deliberately tiny: every line read
 * from stdin is sent as one WebSocket text frame, and every frame received is
 * written back as one line on stdout.
 *
 * stdout carries the JSON-RPC stream ONLY — never log or print diagnostics
 * there or the protocol is corrupted. Errors go to stderr.
 *
 * usage: node wsmcp-bridge.mjs <ws-url>
 */

const url = process.argv[2];
if (url === undefined) {
	process.stderr.write("usage: wsmcp-bridge.mjs <ws-url>\n");
	process.exit(2);
}

const socket = new WebSocket(url);
let open = false;
let buffered = "";
let queued = [];

const send = (line) => {
	if (open) socket.send(line);
	else queued.push(line);
};

socket.addEventListener("open", () => {
	open = true;
	for (const line of queued) socket.send(line);
	queued = [];
});

socket.addEventListener("message", (event) => {
	const data = typeof event.data === "string" ? event.data : Buffer.from(event.data).toString("utf8");
	process.stdout.write(data.endsWith("\n") ? data : `${data}\n`);
});

socket.addEventListener("close", () => process.exit(0));

socket.addEventListener("error", (event) => {
	process.stderr.write(`wsmcp-bridge: ${url}: ${event.message ?? String(event)}\n`);
	process.exit(1);
});

process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
	buffered += chunk;
	let newline = buffered.indexOf("\n");
	while (newline !== -1) {
		const line = buffered.slice(0, newline);
		buffered = buffered.slice(newline + 1);
		if (line.trim().length > 0) send(line);
		newline = buffered.indexOf("\n");
	}
});

process.stdin.on("end", () => {
	if (buffered.trim().length > 0) send(buffered);
	try {
		socket.close();
	} catch {
		/* already closing */
	}
	process.exit(0);
});
