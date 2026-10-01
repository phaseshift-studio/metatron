/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.distributed;

import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.router.BasicRouter;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable.Style;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.TableWidget;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A cluster of peer VMs — one OS process each — plus the declarations that make them addressable from the test
 * VM. This is the Tier-2 infrastructure the whole distributed test family stands on: real processes, real
 * sockets, no shared {@code Router}.
 * <p>
 * Four concerns, in the order a test meets them:
 * <ol>
 *   <li><b>setup</b> — {@code PeerCluster.of(n)….start()}: pick free ports, write each peer's provisioning
 *       script, fork, and block until every peer reports ready. A peer that dies during provisioning (a bad
 *       space template) fails the setup with that peer's trace rather than a mystery timeout.</li>
 *   <li><b>teardown</b> — {@link #close()}, which also lands every peer's trace in {@code target/peer-N.log} so
 *       it outlives the JVM. It never throws: a trace that breaks the suite it is diagnosing is worse than none.</li>
 *   <li><b>declarations modulated to a peer's address</b> — {@link Builder#seed} takes mtron source written
 *       <em>once</em> with placeholders the cluster resolves per peer (see {@link #modulate}), and
 *       {@link TestSpace.Helper#attach} pushes a {@link TestSpace} at a set of peers the same way. That is what
 *       test description describe a whole cluster.</li>
 *   <li><b>tracers</b> — {@link #trace(int)} is one peer's narration (what it mounted, what it was provisioned
 *       with), {@link #dispatches()} is what this VM sent where, and {@link #report()} is both together. Every
 *       cross-host assertion should carry {@code report()} in its failure message.</li>
 * </ol>
 * Indices are 1-based so they line up with the {@code $i} placeholders in a test row.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PeerCluster implements AutoCloseable {

    /**
     * the namespace each peer owns by default
     */
    public static final String DEFAULT_ROOT = "/n";

    /**
     * the default port for the mtron endpoint on each peer
     */
    public static final String MTRON_ROUTE = "/mtron";

    /**
     * The standard seed matrix, root-relative and address-agnostic (it is the cluster that makes it concrete):
     * peer {@code i} ends up holding {@code <root>/a = i}, {@code <root>/b = i * 10}, {@code <root>/c = i * 100}
     * and {@code <root>/probe = "peer-i-store-probe"}. Use with {@code .seed(DEFAULT_SEED_MATRIX)}; the expected
     * values are {@link #seedA}, {@link #seedB}, {@link #seedC} and {@link #probe}.
     */
    public static final String DEFAULT_SEED_MATRIX =
            "$index.to($root/a); $index.mult(10).to($root/b); $index.mult(100).to($root/c); 'peer-$index-store-probe'.to($root/probe)";

    private static final MIME.MIMEType MTRON = MIME.MIMEType.APPLICATION_MTRON;
    private static final long REPLY_TIMEOUT_MS = 15_000L;
    private static final long READY_TIMEOUT_MS = 180_000L;

    /**
     * Warn, deliberately, for every lifecycle line this harness emits: a cluster is slow enough to boot that
     * silence reads as a hang, and {@code info} is filtered out of the test console — a progress message nobody
     * sees is not a progress message.
     */
    private static final GraphittyLogger LOG = Graphitty.log(PeerCluster.class);

    private static volatile PeerCluster active;

    /**
     * The cluster running in this JVM, or {@code null}. This is how a per-test hook reaches the peers without
     * every test handing its cluster around — {@code @TestData(peers = …)} uses it.
     */
    public static PeerCluster active() {
        return active;
    }

    // ========================================================================
    // the seed matrix — the single source of truth for the standard values
    // ========================================================================

    /**
     * the value at {@code <root>/a} on peer {@code i} under {@link #DEFAULT_SEED_MATRIX}
     */
    public static int seedA(final int i) {
        return i;
    }

    /**
     * the value at {@code <root>/b} on peer {@code i} under {@link #DEFAULT_SEED_MATRIX}
     */
    public static int seedB(final int i) {
        return i * 10;
    }

    /**
     * the value at {@code <root>/c} on peer {@code i} under {@link #DEFAULT_SEED_MATRIX}
     */
    public static int seedC(final int i) {
        return i * 100;
    }

    /**
     * the value at {@code <root>/probe} on peer {@code i} under {@link #DEFAULT_SEED_MATRIX}
     */
    public static String probe(final int i) {
        return "peer-" + i + "-store-probe";
    }

    // ========================================================================
    // builder — what the cluster IS
    // ========================================================================

    public static Builder of(final int count) {
        return new Builder(count);
    }

    /**
     * Declares a cluster before it exists. Templates are mtron source with {@code $} placeholders resolved once
     * per peer by {@link #modulate}, which is how one description becomes N differently-addressed VMs.
     */
    public static final class Builder {

        private final int count;
        private String root = DEFAULT_ROOT;
        private final List<String> seeds = new ArrayList<>();

        private Builder(final int count) {
            if (count < 1)
                throw new IllegalArgumentException("a cluster needs at least one peer");
            this.count = count;
        }

        /**
         * the namespace every peer owns (default {@value #DEFAULT_ROOT}); also supplied as {@code $root}
         */
        public Builder store(final String root) {
            this.root = root;
            return this;
        }

        /**
         * Data writes, evaluated on every peer after its spaces exist. Same placeholder rules as {@link #space}.
         */
        public Builder seed(final String... templates) {
            Collections.addAll(this.seeds, templates);
            return this;
        }

        public PeerCluster start() throws Exception {
            // ports first: $port/$self/$peers cannot be resolved until every peer's address is known
            final List<Integer> ports = new ArrayList<>();
            for (int i = 0; i < this.count; i++)
                ports.add(freePort());
            final PeerCluster cluster = new PeerCluster(ports, this.root, true);
            LOG.warn("forking %d peer(s) under %s: %s", this.count, this.root,
                    ports.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("<none>"));
            for (int i = 1; i <= this.count; i++) {
                final Path script = cluster.writeScript(i, this.seeds);
                cluster.processes.add(fork(i, cluster.port(i), this.root, script, cluster.logs.get(i - 1)));
            }
            cluster.awaitReady();
            active = cluster;
            return cluster;
        }
    }

    // ========================================================================
    // state
    // ========================================================================

    private final List<Integer> ports;
    private final String root;
    private final List<Process> processes = new ArrayList<>();
    private final List<Path> logs = new ArrayList<>();
    private final List<Peer> peers = new ArrayList<>();
    private final List<String> dispatches = Collections.synchronizedList(new ArrayList<>());

    private PeerCluster(final List<Integer> ports, final String root, final boolean withTraces) throws IOException {
        this.ports = ports;
        this.root = root;
        for (int i = 0; i < ports.size(); i++) {
            if (withTraces)
                this.logs.add(Files.createTempFile("metatron-peer-" + (i + 1) + "-", ".log"));
            this.peers.add(new Peer(this, i + 1));
        }
    }

    /**
     * A cluster that exists only as addresses: {@code n} peers at fixed ports, no processes, no sockets, no
     * temp files. This is the pure half of the harness — {@link #rewrite}, {@link #modulate}, {@link #prefix},
     * {@link #peers} — and it is what lets the transformations every row and every space definition depends on
     * be exercised <em>without booting anything</em>. Anything that talks to a peer ({@link #send},
     * {@link #peerAnalysis}) needs a {@link #of(int) started} cluster instead.
     */
    public static PeerCluster detached(final int count, final String root, final int basePort) throws IOException {
        final List<Integer> ports = new ArrayList<>();
        for (int i = 0; i < count; i++)
            ports.add(basePort + i);
        return new PeerCluster(ports, root, false);
    }

    /**
     * handles to every peer, for {@link TestSpace.Helper#attach} and for anything that addresses one directly
     */
    public List<Peer> peers() {
        return List.copyOf(this.peers);
    }

    // ========================================================================
    // modulation — one template, N addresses
    // ========================================================================

    /**
     * Resolve a template for one peer: {@link #rewrite} handles {@code $n} and {@code $self}, and this adds the
     * peer's own particulars — {@code $index}, {@code $port}, {@code $root} and {@code $peers}.
     * <p>
     * Substitution is <em>textual</em>, so a placeholder works inside a quoted mtron string
     * ({@code 'peer-$index'} becomes {@code 'peer-2'}). A {@link TestSpace} is evaluated on <em>every</em> peer,
     * so a {@code $n} reference names the same peer everywhere: that is what lets one definition describe a
     * whole cluster.
     */
    String modulate(final String template, final int oneBased) {
        return this.rewrite(template
                .replace("$peers", this.peersLiteral())
                .replace("$root", this.root)
                .replace("$port", String.valueOf(this.port(oneBased)))
                .replace("$index", String.valueOf(oneBased)), oneBased);
    }

    /**
     * {@code $n} or {@code $self}, optionally followed by a path — the unit both rows and space templates are
     * written against. The path stops at whatever ends an address in a row: {@code ,} {@code }} (multiplicity),
     * {@code (} {@code )} (call), {@code .} (chained mapper) and whitespace.
     */
    static final Pattern PEER_REFERENCE = Pattern.compile("\\$(self|\\d+)((?:/[^,}{)(\\s.<>]*)?)");

    /**
     * rewrite {@code $n} against peer 1 — for a row, where {@code $self} has no meaning
     */
    public String rewrite(final String row) {
        return this.rewrite(row, 1);
    }

    /**
     * Rewrite {@code $n} — and {@code $self}, which is this peer — to the referenced peer's uri prefix. The
     * referenced path is folded <em>inside</em> a uri literal, because mtron cannot append a path outside one:
     * {@code *<uri>/a} is a parse error while {@code *<uri/a>} is fine.
     * <pre>
     *   *$1/a     becomes  *&lt;ws://localhost:PORT/n/a&gt;     (brackets supplied)
     *   *&lt;$1/a&gt;   becomes  *&lt;ws://localhost:PORT/n/a&gt;     (brackets already there — not doubled)
     *   $self/v   becomes  &lt;ws://localhost:PORT/n/v&gt;
     * </pre>
     * Both spellings have to work: the bare form is what makes {@code {*$1/a,*$2/b}} readable, and the
     * bracketed form is the natural one when a placeholder sits inside a larger uri.
     * <p>
     * A placeholder naming a peer that does not exist is left exactly as written — a visible {@code $4} in a
     * failure is worth more than a silently wrong address.
     */
    String rewrite(final String row, final int selfIndex) {
        final Matcher matcher = PEER_REFERENCE.matcher(row);
        final StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            final int referenced = "self".equals(matcher.group(1))
                    ? selfIndex
                    : Integer.parseInt(matcher.group(1));
            if (referenced < 1 || referenced > this.size()) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            final String path = matcher.group(2);
            // inside an existing <...> literal? then the brackets are the author's business, not ours
            final boolean insideLiteral = matcher.start() > 0 && '<' == row.charAt(matcher.start() - 1)
                    && matcher.end() < row.length() && '>' == row.charAt(matcher.end());
            final String replacement = (insideLiteral ? "" : "<") + this.prefix(referenced) + path + (insideLiteral ? "" : ">");
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String peersLiteral() {
        final StringBuilder literal = new StringBuilder();
        for (int i = 1; i <= this.size(); i++) {
            if (literal.length() > 0)
                literal.append(", ");
            literal.append('<').append(this.prefix(i)).append('>');
        }
        return literal.toString();
    }

    /**
     * Write a peer's provisioning script: its seed templates, modulated to that peer. It is evaluated as mtron
     * by the peer, so it must be <em>pure mtron</em> — a {@code [-- … --]} header is stripped by the boot loader
     * and not by the parser, so a comment here is a parse error there, which is exactly how a cluster ends up
     * never reaching ready. The narration that would have gone in a header is the peer's own trace instead.
     */
    private Path writeScript(final int oneBased, final List<String> seeds) throws IOException {
        final List<String> statements = new ArrayList<>();
        for (final String seed : seeds)
            statements.add(this.modulate(seed, oneBased));
        final Path path = Files.createTempFile("metatron-peer-" + oneBased + "-", ".mtron");
        Files.writeString(path, String.join(";\n", statements));
        return path;
    }

    // ========================================================================
    // addressing
    // ========================================================================

    public int size() {
        return this.ports.size();
    }

    /**
     * the store root every peer owns
     */
    public String root() {
        return this.root;
    }

    /**
     * is the i-th peer's process still alive?
     */
    public boolean alive(final int oneBased) {
        final Process process = this.processes.isEmpty() ? null : this.processes.get(oneBased - 1);
        return null != process && process.isAlive();
    }

    /**
     * the mtron port of the i-th peer (1-based)
     */
    public int port(final int oneBased) {
        return this.ports.get(oneBased - 1);
    }

    /**
     * The i-th peer's connectable data-root prefix: {@code ws://localhost:<port><root>}. Address a value on
     * that peer by extending it — {@code prefix(2) + "/a"} is peer 2's {@code /n/a}.
     */
    public String prefix(final int oneBased) {
        return "ws://localhost:" + this.port(oneBased) + this.root;
    }

    // ========================================================================
    // declaration — the roster that makes peers reachable
    // ========================================================================

    /**
     * Install the declared peer roster ({@code /sys/peer}): one entry per peer, keyed by its authority, whose
     * value is the transport that reaches it. Membership is <em>declared</em> — nothing becomes a peer by being
     * addressed — so this is the step that turns a running process into a peer this VM may talk to.
     */
    public PeerCluster connect() {
        final Map<Obj, Obj> roster = new LinkedHashMap<>();
        for (int i = 1; i <= this.size(); i++)
            roster.put(uri("ws://localhost:" + this.port(i)), this.transport(i));
        Router.writeToSpace(BasicRouter.peerRosterPath(), rec(roster));
        LOG.warn("roster declared (%d peer(s)): %s", roster.size(),
                roster.keySet().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("<empty>"));
        this.dispatches.add("declared roster: " + roster.keySet().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("<empty>"));
        return this;
    }

    /**
     * The transport that reaches the i-th peer: open one {@code wsclient} to its {@code mtron_ws} endpoint, send
     * the message ({@code from(localized)} for a read, {@code start(localized).ref(obj)} for a write), return the
     * response — recording the dispatch so {@link #dispatches()} shows what went where.
     * <p>
     * Single-shot by design: the synchronous slice keeps exactly one request in flight, so the racy one-shot
     * handler swap in {@code sendRecv} is sufficient. The timeout overload is used so a dead peer fails rather
     * than hanging the caller forever.
     */
    // a noobj reply is ambiguous: the peer may be down, or the expression may simply have evaluated to
    // nothing. `transport` (the roster-facing inst, which is what BasicRouter dispatches through) resolves
    // the ambiguity by asking a second, store-free question -- so a peer that is merely empty never looks
    // like a peer that is dead. `send` reports the raw answer, because there noobj is a real result.
    public Inst transport(final int oneBased) {
        final int port = this.port(oneBased);
        return instLambda((lhs, inst) -> {
            final Obj response = this.rawSend(oneBased, lhs);
            return response.isNoObj() && !this.answers(oneBased)
                    ? fail("peer %d did not answer %s", port, lhs.toShortString())
                    : response;
        });
    }

    private Obj rawSend(final int oneBased, final Obj message) {
        final int port = this.port(oneBased);
        this.dispatches.add("peer " + oneBased + " @ " + port + " <= " + message.toShortString());
        WebSocketRecClient client = null;
        try {
            // the 2-arg (timeout) sendRecv lives on the WebSocketObj the client drives, so build the rec
            // first and let the client attach itself to it
            final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                    uri(HOST), uri("ws://localhost:" + port + MTRON_ROUTE + "?out=" + MTRON.value),
                    uri(IN), uri(MTRON.value),
                    uri(OUT), uri(MTRON.value)), WS_CLIENT_TID, null);
            client = new WebSocketRecClient(wsRec);
            return wsRec.sendRecv(message, REPLY_TIMEOUT_MS);
        } catch (final Exception e) {
            this.dispatches.add("peer " + oneBased + " @ " + port + " => " + e);
            return fail(e);
        } finally {
            if (null != client)
                client.close();
        }
    }

    /** a trivial round trip that needs no store at all, so it separates "nothing there" from "not answering" */
    private boolean answers(final int oneBased) {
        return !this.rawSend(oneBased, ObjmtronSerializer.parse("1.plus(1)")).isNoObj();
    }

    // ========================================================================
    // observation — what a peer actually did, from the peer's own profile()
    // ========================================================================

    /**
     * Ask a peer to evaluate {@code localCode} and report <em>its own</em> {@code profile()} of that
     * evaluation. {@code localCode} is peer-relative: the peer receives it with no authority attached, exactly
     * as the guard's {@code localize()} leaves it, so {@code from(/n/a)} or {@code {1,2,3}.plus(10)} are the
     * shapes to pass — not a {@code $i} row, which is this VM's addressing.
     * <p>
     * <b>What the numbers mean, measured:</b> {@code profile()} reports <em>monadic</em> flow, not I/O. A plain
     * scalar dereference (`from(/n/a)`) reports {@code monads=0, processors=0} — it compiled one instruction and
     * did no monadic work. A multiplicity does: {@code {1,2,3}.plus(10)} reports {@code monads>0} with
     * {@code c_sum} below {@code monads}, which is bulk compression made observable
     * ({@code start} emits 1 monad of coefficient 3; {@code plus} turns it into 3 monads). That distinction is
     * the point: "did this peer process data?" only has a meaningful answer when the peer did monadic work, and
     * answering it wrongly is exactly how a distribution bug hides.
     */
    public Obj profile(final int oneBased, final String localCode) {
        // bind to Obj first: ObjmtronSerializer.parse is generic, so letting it infer against the two send
        // overloads makes the call ambiguous
        final Obj message = ObjmtronSerializer.parse(localCode + ".profile()");
        return this.send(oneBased, message);
    }

    /**
     * send an arbitrary mtron expression to a peer and return its response
     */
    public Obj send(final int oneBased, final String source) {
        final Obj message = ObjmtronSerializer.parse(source);
        return this.send(oneBased, message);
    }

    /**
     * send an already-parsed message to a peer and return its raw answer — {@code noobj} means the peer
     * evaluated it to nothing, which is a real result and not the same thing as a peer that is down
     */
    public Obj send(final int oneBased, final Obj message) {
        return this.rawSend(oneBased, message);
    }

    /**
     * monads the peer propagated for {@code localCode} — 0 for a plain scalar dereference
     */
    public long monads(final int oneBased, final String localCode) {
        return this.flow(oneBased, localCode, MONADS);
    }

    /**
     * coefficient mass the peer moved for {@code localCode}
     */
    public long coeffSum(final int oneBased, final String localCode) {
        return this.flow(oneBased, localCode, C_SUM);
    }

    /**
     * processors the peer spawned for {@code localCode}
     */
    public long processors(final int oneBased, final String localCode) {
        return this.flow(oneBased, localCode, PROCESSORS);
    }

    /**
     * did the peer propagate any monads for {@code localCode}?
     */
    public boolean processed(final int oneBased, final String localCode) {
        return this.monads(oneBased, localCode) > 0;
    }

    /**
     * the peer's per-instruction in/out flow for {@code localCode} — {@code lst(rec(name, monad_in, …))}
     */
    public Obj perInst(final int oneBased, final String localCode) {
        final Obj profile = this.profile(oneBased, localCode);
        return profile.isRec() ? profile.asRec().at(uri(PER_INST)) : noobj();
    }

    long flow(final int oneBased, final String localCode, final String field) {
        final Obj value = this.flowField(oneBased, localCode, field);
        return value.isInt() ? value.intValue() : 0L;
    }

    /**
     * the raw {@code profile()>>flow/<field>} value, for fields that are not ints (e.g. compression)
     */
    Obj flowField(final int oneBased, final String localCode, final String field) {
        final Obj profile = this.profile(oneBased, localCode);
        if (!profile.isRec())
            return noobj();
        final Obj flow = profile.asRec().at(uri(FLOW));
        return flow.isRec() ? flow.asRec().at(uri(field)) : noobj();
    }

    // ========================================================================
    // tracers — what is happening, and where
    // ========================================================================

    /**
     * one peer's narration: what it mounted, what it was provisioned with, what it is serving
     */
    public String trace(final int oneBased) {
        try {
            if (this.logs.size() < oneBased)
                return "<no trace: peer " + oneBased + " was never forked>";
            final Path log = this.logs.get(oneBased - 1);
            return Files.exists(log) ? Files.readString(log) : "<no trace for peer " + oneBased + ">";
        } catch (final Exception e) {
            return "<unreadable trace for peer " + oneBased + ": " + e + ">";
        }
    }

    /**
     * every peer's trace, headed
     */
    public String traces() {
        final StringBuilder all = new StringBuilder();
        for (int i = 1; i <= this.size(); i++)
            all.append("--- peer ").append(i).append(" @ ").append(this.port(i)).append(" ---\n")
                    .append(this.trace(i));
        return all.toString();
    }

    /**
     * what this VM sent where, oldest first
     */
    public List<String> dispatches() {
        synchronized (this.dispatches) {
            return List.copyOf(this.dispatches);
        }
    }

    /**
     * Both halves of the story in one readable block: the dispatches this VM made, then each peer's own
     * narration. Put it in a cross-host assertion's failure message — without it, a wrong value says nothing
     * about whether the peer was consulted at all.
     */
    public String report() {
        final StringBuilder report = new StringBuilder("=== peer cluster: ")
                .append(this.size()).append(" peer(s), root ").append(this.root).append(" ===\n");
        for (int i = 1; i <= this.size(); i++)
            report.append("  peer ").append(i).append(": ").append(this.prefix(i)).append('\n');
        report.append("--- dispatches (test VM -> peers) ---\n");
        for (final String dispatch : this.dispatches())
            report.append("  ").append(dispatch).append('\n');
        return report.append(this.traces()).toString();
    }

    // ========================================================================
    // peerAnalysis — look at every peer at once
    // ========================================================================

    /**
     * A probe that reliably produces monadic work, for when you want to know whether a peer's engine is alive
     * rather than what it holds. {@code {1,2,3}} is a multiplicity of three, {@code plus} expands it and
     * {@code mult} keeps it moving, so the flow counters move on any healthy swarm.
     */
    public static final String FLOW_PROBE = "{1,2,3}.plus(1).mult(2)";

    /**
     * the flow columns {@link #peerProfile} shows when asked for none
     */
    public static final String[] DEFAULT_FLOW = {MONADS, C_SUM, COMPRESSION, PROCESSORS};

    /**
     * parse mtron source into a {@link Call} — the column type of {@link #peerAnalysis}
     */
    public static Call call(final String source) {
        final Obj parsed = ObjmtronSerializer.parse(source);
        return parsed.asCall();
    }

    /**
     * parse several mtron sources into calls
     */
    public static Call[] calls(final String... sources) {
        final Call[] parsed = new Call[sources.length];
        for (int i = 0; i < sources.length; i++)
            parsed[i] = call(sources[i]);
        return parsed;
    }

    /**
     * Evaluate each call <b>on every peer</b> and print one row per peer: the columns are what the peers said.
     * This is the first thing to reach for when a distributed run looks wrong, because the alternative — reading
     * N traces — does not tell you which peer disagrees.
     * <pre>{@code
     * peerAnalysis(cluster.peers(),
     *         call("*</sys/space/#>.count()"),     // did each peer take the declared spaces?
     *         call("*</n/#>"),                    // what does each peer actually hold?
     *         call("*</sys/peer>"));              // what roster does each peer have?
     * }</pre>
     * Calls are evaluated <em>on the peer</em>, so they are written peer-relative — {@code *</n/a>}, never
     * {@code *<$1/a>}: {@code $n} is this VM's addressing, not a peer's. A call that fails prints its failure
     * rather than throwing, because a diagnostic that breaks while you are diagnosing is worse than a blank
     * cell.
     */
    public static void peerAnalysis(final List<Peer> peers, final Call... calls) {
        System.out.print(peerAnalysisToString(peers, calls));
        System.out.flush();
    }

    /**
     * {@link #peerAnalysis} as a string, so a failure message can carry the same table
     */
    public static String peerAnalysisToString(final List<Peer> peers, final Call... calls) {
        final List<String> headers = new ArrayList<>(List.of("idx", "port", "live"));
        for (final Call call : calls)
            headers.add(abridge(source(call)));
        final List<List<Object>> rows = new ArrayList<>();
        for (final Peer peer : peers) {
            final List<Object> row = new ArrayList<>();
            row.add(identity(peer.index()));
            row.add(identity(peer.port()));
            row.add(peer.alive() ? "{{G}}yes{{X}}" : "{{R}}no{{X}}");
            for (final Call call : calls)
                row.add(cell(peer.evaluate(call)));
            rows.add(row);
        }
        return table("peer analysis", headers, rows);
    }

    /**
     * The same consolidation for {@code profile()}: each peer profiles its own evaluation of {@code code}, and
     * the requested {@code flow} fields become the columns — {@code monads}, {@code c_sum}, {@code compression},
     * {@code processors} by default. This is how "did peer 2 do any work?" and "how many monads did it provide
     * back?" are answered, and why they are worth answering from the peer rather than inferred: {@code profile}
     * measures <em>monadic</em> flow, not I/O, so a peer can serve a value while reporting no flow at all.
     * <pre>{@code
     * peerProfile(cluster.peers(), FLOW_PROBE);                       // is every engine doing work?
     * peerProfile(cluster.peers(), "{1,2,3}.sum()", MONADS, C_SUM);   // particular stats
     * }</pre>
     */
    public static void peerProfile(final List<Peer> peers, final String code, final String... fields) {
        System.out.print(peerProfileToString(peers, code, fields));
        System.out.flush();
    }

    /**
     * {@link #peerProfile} as a string, so a failure message can carry the same table
     */
    public static String peerProfileToString(final List<Peer> peers, final String code, final String... fields) {
        final String[] columns = 0 == fields.length ? DEFAULT_FLOW : fields;
        final List<String> headers = new ArrayList<>(List.of("idx", "port"));
        headers.addAll(List.of(columns));
        final List<List<Object>> rows = new ArrayList<>();
        for (final Peer peer : peers) {
            final List<Object> row = new ArrayList<>();
            row.add(identity(peer.index()));
            row.add(identity(peer.port()));
            for (final String column : columns)
                row.add(cell(peer.flowField(code, column)));
            rows.add(row);
        }
        return table("peer profile of " + abridge(code), headers, rows);
    }

    /**
     * a cell for a measured value: a failure is red, an absence is a dash, a value is plain
     */
    private static String cell(final Obj value) {
        if (null == value || value.isNoObj())
            return "{{R}}-{{X}}";
        final String text = abridge(value);
        return value.isFail() || value.isCaughtFail() ? "{{R}}" + text + "{{X}}" : text;
    }

    private static String source(final Call call) {
        try {
            return ObjmtronSerializer.single().write(call);
        } catch (final Exception e) {
            return "<call>";
        }
    }

    /**
     * Render a table the way {@code TestReport} renders its summary: a {@link TableWidget} with continuous
     * borders, under a bold title. The widget measures every cell through {@code Highlighter}, so a coloured
     * cell and a plain one occupy the same width — which is why cells may carry {@code {{G}}} markers but the
     * headers may not (header width is measured raw).
     */
    static String table(final String title, final List<String> headers, final List<List<Object>> rows) {
        final TableWidget widget = new TableWidget(headers);
        widget.style(Style.empty());
        widget.getStyle().border(Border.continuous).divider("│").headerDivider("│");
        rows.forEach(widget::addRow);
        return Graphitty.string("\n{{B}}%s{{X}}\n%s\n", title, widget.format());
    }

    /** the peer's own numbers — cyan, so identity reads as identity and never as a measured value */
    private static String identity(final int value) {
        return "{{C}}" + value + "{{X}}";
    }

    /**
     * keep a cell short enough that a whole cluster stays scannable on one screen
     */
    private static String abridge(final Obj obj) {
        return abridge(null == obj ? "noobj" : obj.toString());
    }

    private static String abridge(final String text) {
        final String flat = null == text ? "noobj" : text.replace('\n', ' ');
        return flat.length() <= 44 ? flat : flat.substring(0, 41) + "...";
    }

    /**
     * a peer trace line for the test VM's own log, so both sides land in one place
     */
    public void note(final String message) {
        this.dispatches.add(message);
    }

    // ========================================================================
    // teardown
    // ========================================================================

    /**
     * Stop every peer, and land each one's trace in {@code target/peer-N.log} first so it outlives the JVM.
     * Never throws — a diagnostic that can break the suite it is diagnosing is worse than no diagnostic.
     */
    @Override
    public void close() {
        if (active == this)
            active = null;
        for (int i = 1; i <= this.size(); i++) {
            try {
                final Path out = Path.of(System.getProperty("user.dir"), "target", "peer-" + i + ".log");
                Files.createDirectories(out.getParent());
                Files.writeString(out, this.report());
            } catch (final Exception ignored) {
                // a trace must never break teardown
            }
        }
        for (final Process process : this.processes) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS))
                    process.destroyForcibly();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        this.processes.clear();
    }

    // ========================================================================
    // forking
    // ========================================================================

    private static int freePort() throws IOException {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Process fork(final int index, final int port, final String root, final Path script,
                                final Path log) throws IOException {
        final List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        // inherit only the VM flags the child needs — the parent's full arg list can carry agents that fail
        for (final String arg : ManagementFactory.getRuntimeMXBean().getInputArguments())
            if (arg.startsWith("--add-opens") || arg.startsWith("--add-modules")
                    || arg.startsWith("--enable-native-access") || arg.startsWith("--sun-misc-unsafe-memory-access"))
                command.add(arg);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(PeerNode.class.getName());
        command.add(String.valueOf(index));
        command.add(String.valueOf(port));
        command.add(root);
        command.add(script.toString());
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private void awaitReady() throws Exception {
        final long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        for (int i = 0; i < this.processes.size(); i++) {
            final Process process = this.processes.get(i);
            final Path log = this.logs.get(i);
            final long started = System.currentTimeMillis();
            boolean ready = false;
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive())
                    throw new IllegalStateException(
                            "peer %d exited before it was ready:\n%s".formatted(i + 1, Files.readString(log)));
                if (Files.exists(log) && Files.readString(log).contains(PeerNode.READY)) {
                    ready = true;
                    break;
                }
                Thread.sleep(100L);
            }
            if (!ready)
                throw new IllegalStateException(
                        "peer %d did not become ready in time:\n%s".formatted(i + 1, Files.readString(log)));
            // warn, not info: a booting cluster is exactly when someone is watching, and info is filtered out
            // of the test console — silence here is indistinguishable from a hang
            LOG.warn("peer %d loaded [%s] in %dms", i + 1, this.prefix(i + 1), System.currentTimeMillis() - started);
        }
        LOG.warn("all peers up and ready (%d)", this.processes.size());
    }
}
