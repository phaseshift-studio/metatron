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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.instset.BasicInstSet;
import studio.phaseshift.metatron.isa.mach.type.machine.AbstractMachine;
import studio.phaseshift.metatron.isa.mach.type.machine.MachineSpace;
import studio.phaseshift.metatron.isa.mach.type.memory.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.memory.MemoryUnion;
import studio.phaseshift.metatron.isa.mach.type.network.BasicNetwork;
import studio.phaseshift.metatron.isa.mach.type.network.PeerSpawn;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyObjLogger;
import studio.phaseshift.metatron.isa.sys.LogObj;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.BootLoader.ROOT_MACHINE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

/**
 * Machine: a SPACE OF SPACES -- the address space resolution happens in. Its members ({@code instset}, {@code memory},
 * {@code network}, {@code compiler}, {@code processor}) are rec entries, the shape of {@code machine::T}; {@link Memory}
 * is the facade over the world of spaces, while {@link Compiler} lowers code and {@link Processor} runs it and neither
 * is an address space.
 * <p>
 * Three names, three questions: {@link #jvmRoot()} is this JVM's bootstrap; {@link #root()} is the top of the
 * containment this thread is in, and is what resolution reads; {@link #current()} is where the thread stands.
 * <p>
 * <p>
 * The members, by role:
 * <ol>
 *     <li><b>identity and place</b> -- {@code jvmRoot}, {@code root}, {@code mach0}, {@code defaultMachine}: where
 *     things are and who contains whom.</li>
 *     <li><b>the perspective</b> -- {@code CURRENT}, {@code current()}, {@code relativeToCurrent}: where THIS thread
 *     stands, which is what lets the machine be the machine.</li>
 *     <li><b>the address surface</b> -- {@code read}, {@code write}, {@code loaded}: the one way in, asking the
 *     machine's own sphere ({@link MachineSpace}: the machine, its components, its system information), then the
 *     network, then data memory, then instruction memory.</li>
 *     <li><b>the component contract</b> -- {@link Component} and its {@code machine()} walk.</li>
 *     <li><b>execution</b> -- {@code push}, {@code pop}, {@code move}. {@code apply} is inherited identity here: the
 *     re-homing of {@code CURRENT} as an apply side effect is disabled, so dereference stays pure and {@code move} is
 *     the only context switch.</li>
 *     <li><b>components</b> -- {@code compiler}, {@code processor}, {@code instset}, {@code memory}, {@code network},
 *     each with its setter and its {@code bootstrap}.</li>
 *     <li><b>inner types</b> -- {@link Component}, {@link Helper}, and {@link Helper.Machine0}, the zero machine.</li>
 * </ol>
 * <p>
 * The concrete machine is an address space ({@code AbstractMachine extends AbstractSpace}), but the space contract --
 * {@code addSpace}, {@code removeSpace}, {@code getSpaceFor}, {@code spaces}, and the route table -- is
 * {@link Memory}'s, reached through {@code memory()}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Machine extends Rec, AutoCloseable {

    // ======================== identity and place ========================

    static Machine mach0() {
        return Helper.Machine0.single();
    }

    /**
     * The machine ROOT — always, whatever perspective this thread is standing in. The bootstrap constant.
     */
    static Machine jvmRoot() {
        return null == ROOT_MACHINE ? mach0() : ROOT_MACHINE;
    }

    /**
     * The top of the containment this thread is in: the machine that owns its addresses and whose spaces answer.
     * Resolution reads THIS, never {@link #current()} -- a machine that EXECUTES code need not be able to RESOLVE it.
     */
    static Machine root() {
        return null == ROOT_MACHINE ? mach0() : ROOT_MACHINE;
    }

    // ======================== the perspective ========================

    /**
     * Where this thread stands: the machine it is evaluating in, or {@link #root()} when nothing moved it.
     * Moved by a machine dereference or {@link #current(Machine)}, never by an ordinary scope.
     */
    static Machine current() {
        final Machine machine = CURRENT.get();
        return null != machine ? machine : root();
    }

    /**
     * The machine of reference, per thread: the machine this thread is evaluating in, or {@link #root()} when nothing
     * moved it. A worker starts where its caller stood and publishes where it ended.
     */
    ThreadLocal<Machine> CURRENT = new ThreadLocal<>();

    /**
     * Move this thread's perspective and leave it there -- no lambda, no restore. Use this form when the move IS the
     * statement; use the two-arg form when it is scoped to one fragment.
     */
    static Machine current(final Machine machine) {
        CURRENT.set(machine);
        return machine;
    }

    // ======================== the address surface ========================
    // The one way in. A read asks the machine's own sphere (MachineSpace), then the network, then data memory, then
    // instruction memory; a write mirrors it. The
    // space contract itself -- addSpace/removeSpace/getSpaceFor/spaces and the route table -- is Memory's, reached
    // through memory().

    static Obj read(final fURI vid) {
        if (null == ROOT_MACHINE)
            return noobj();
        // Authority dispatch comes FIRST, before the handoff to Memory: an address carrying an authority a peer has
        // declared is that peer's to answer, and Memory only ever sees an address already localized. Network.read
        // answers noobj for an address with no authority, or one whose authority is not in the roster, so the local
        // path is untouched (an empty roster is the normal case).
        final Machine machine = Machine.current();
        // THE MACHINE'S OWN SPHERE ANSWERS FIRST (MachineSpace): the machine itself, its components and its system
        // information, from the innermost machine whose sphere covers the address. It is not in memory, and it declines
        // everything else — the machine's data among it — so the network and memory below answer as they always have.
        final Obj owned = Helper.readSphere(machine, vid);
        if (!owned.isNoObj())
            return owned;
        // THE ROUTER OF ROUTERS ALIGNS THE NAME ONCE. a prefixed spelling (ide:command) names the same
        // resource as its aligned form (/m/ide/inst/command), and each component keys by the canonical
        // form -- network authority, memory's index, the instset tables -- so the gatekeeper aligns HERE,
        // once, and every dispatch below sees one name. ONE DOOR: Helper.align IS that alignment, which is
        // why the compiler's move-extractor can canonicalize a spelling exactly as the runtime does -- a
        // name resolved one way at compile time and another at run time would ship a tail nowhere.
        // The fallback to instruction memory does not align on its own, and a raw spelling can never match
        // tables keyed by full identity. a scheme-less vid passes through untouched: alignPrefix normalizes
        // to coefficient one, which must not be imposed on an ordinary read, and a RELATIVE name stays
        // relative -- "in the machine" is memory's contract to interpret.
        final fURI name = Helper.align(machine, vid);
        // THE NETWORK'S Q-PROCS APPLY HERE, not inside Network: the layer is the one place a vid is handed to a
        // source, so a `?subq` on a peer address registers in THIS VM (the layer's pre_write consumes the write)
        // instead of riding the wire and registering on the peer.
        // THE GATE IS THE NETWORK'S OWN: it answers only addresses with a host (see Network.read/write). The layer
        // must not run for an address the network does not claim — otherwise the network's subq would capture a
        // purely local `?subq` registration that belongs to the space covering it.
        final Network network = machine.network();
        final Obj dispatched = null == name.host()
                ? noobj()
                : QProc.Layer.read(network, name, () -> network.read(name));
        if (!dispatched.isNoObj())
            return dispatched;
        // Local routing (~ resolution, relative→stack, absolute→space) lives in Memory.read, so there is exactly one
        // place a read is turned into an address. An address an instruction set holds is routed there too: an ISA is
        // not a space in memory's index (it lives in the instset), but memory's lookup weighs the instset's libraries
        // against its own spaces, so the most specific claimant answers, as it always did.
        final Obj inMemory = machine.memory().read(name);
        if (!inMemory.isNoObj())
            return inMemory;
        // ... AND A READ READS INSTRUCTION MEMORY. A read is the language's ONE way in, so it answers from the
        // machine's own instset when data memory has nothing: that is where an instruction, a type or a declared
        // name comes from once it is not also mounted as a space, and it is what makes a local declaration shadow an
        // import. Fetched through here, NOT by a caller reaching for instset() directly — a selector may do that as
        // an optimization, but the way instructions are fetched is this read.
        // It is asked on a MISS rather than before memory because an ISA read scans that ISA's tables and walks the
        // machine chain, and every read in the VM comes through this method; memory's lookup already answers each
        // library's own jurisdiction, so this union read is what answers the rest (a frame's own level).
        if (Helper.isReadingInstructionMemory())
            return noobj();
        Helper.readingInstructionMemory(true);
        try {
            // readDeclared, NOT read: this is a FALLBACK, and a fallback must not cause work on the reader's
            // behalf. A schema instset may implement read() as "materialize me, then answer"
            // (SQLSchemaInstSet populates a table's schema on demand, which is right for a deliberate read of
            // that schema) — asking that here triggered a catalog walk from a logger's type lookup while a
            // tbleSpace was still constructing, and the database connection was not open yet.
            return machine.instset().readDeclared(name);
        } finally {
            Helper.readingInstructionMemory(false);
        }
    }

    static Obj read(final String vid) {
        return Machine.read(f(vid));
    }

    static Obj write(final fURI vid, final Obj obj) {
        if (null == ROOT_MACHINE)
            return noobj();
        // Authority dispatch first, exactly as read: a declared peer's authority takes the write over the wire.
        // Network.write answers noobj for an undeclared authority, so the local path is untouched — but a declared
        // peer that is silent answers a fail, which must NOT be swallowed by the local fall-through.
        final Machine machine = Machine.current();
        // THE MACHINE'S OWN SPHERE FIRST, exactly as read — and a write the sphere OWNS is final, whatever it answers:
        // a member's deletion answers noobj, and handing that on as a decline would land a stray deletion in memory
        final Optional<Obj> owned = Helper.writeSphere(machine, vid, obj);
        if (owned.isPresent())
            return owned.get();
        // aligned once at the gatekeeper, exactly as read -- a prefixed spelling must land where its
        // aligned form lands, on the wire and in the index alike.
        final fURI name = Helper.align(machine, vid);
        // the same layer as read, under the same host gate: a write the network's q-procs answer (a `?subq`
        // registration) never reaches the wire, and the write's own answer is the network's reply
        final Network network = machine.network();
        final Obj dispatched = null == name.host()
                ? noobj()
                : QProc.Layer.write(network, name, obj, () -> network.write(name, obj));
        if (!dispatched.isNoObj())
            return dispatched;
        // Local pass-through to Memory (see read). An address an instruction set holds is written to it there:
        // memory's lookup weighs the instset's libraries, so "the most specific gains the write" (Marko, 2026-10-25)
        // — /m/math/cat/… to /m/math/cat, /m/math/… to /m/math, the rest of /m/… to /m.
        return machine.memory().write(name, obj);
    }

    static Obj write(final String vid, final Obj obj) {
        return Machine.write(f(vid), obj);
    }

    // ======================== the two entry points ========================

    /**
     * Parse {@code source} and nothing else — the {@code parser} stage on its own, as an obj.
     * No rewriting, no resolution, no typing, no evaluation.
     *
     * @param source the mtron source text
     * @return the parsed obj, or the {@code fail::T} that stopped it
     */
    public static Obj parse(final String source) {
        return Machine.current().compiler().parser().apply(str(source));
    }

    /**
     * Evaluate a source FILE as a program: read it, parse it, run each statement in turn.
     */
    public static Obj evaluate(final File file) {
        try {
            return evaluate(Files.readString(file.toPath()));
        } catch (final IOException e) {
            return MTronException.of(e).asFail();
        }
    }

    /**
     * Evaluate a source FILE statement by statement, reporting the statement that failed.
     * <p>
     * Parsing and applying are INTERLEAVED: a statement is parsed, applied, and only then is the
     * next one read. That ordering is required, not stylistic — a boot statement's construction can
     * have side effects (a {@code @vid} write mounts a space, e.g. the console), and those effects
     * must be in place before the next statement is read.
     */
    public static Stream<Obj> evaluate(final File file, final Consumer<FileParseError> exhandler) throws IOException {
        final List<String> lines = Files.readAllLines(file.toPath());
        final String source = lines.stream().reduce("", (a, b) -> a + b + "\n");
        final AtomicInteger lineNumber = new AtomicInteger(0);
        return CommonUtil.splitOnNonQuotedSequence(
                        studio.phaseshift.metatron.isa.m.parser.mParser.stripComments(source), ';', false)
                .stream()
                .map(piece -> Arrays.stream(piece.split("\n"))
                        .peek(x -> lineNumber.incrementAndGet())
                        .filter(x -> !x.isBlank())
                        .reduce("", (a, b) -> a + b + "\n"))
                .map(piece -> {
                    final String line = lines.isEmpty() ? piece : lines.get(Math.min(lineNumber.get(), lines.size() - 1));
                    final Obj parsedPiece = studio.phaseshift.metatron.isa.m.parser.mParser.parse(piece);
                    if (parsedPiece.stream().anyMatch(Obj::isFail)) {
                        // the statement did not parse: report it and take the next split
                        exhandler.accept(new FileParseError(lineNumber.get(), line, parsedPiece.asFail().asException()));
                        return noobj();
                    }
                    try {
                        return parsedPiece.apply();
                    } catch (final Exception e) {
                        exhandler.accept(new FileParseError(lineNumber.get(), line, e));
                        return noobj();
                    }
                })
                .filter(o -> !o.isNoObj());
    }

    /**
     * A statement of a source file that failed, with its line and the source it came from.
     */
    record FileParseError(int lineNumber, String lineString, Exception parseException) {
    }

    /**
     * Compile {@code source} (parse → rewrite → resolve → type) and run it on this machine.
     * The one call for "evaluate this source": parse is the parser's job, and running compiled
     * code is the machine's.
     *
     * @param source the mtron source text
     * @return the obj the program computed, or the {@code fail::T} that stopped it
     */
    public static Obj evaluate(final String source) {
        final Machine machine = Machine.current();
        return Machine.run(machine, machine.compiler().apply(str(source)));
    }

    /**
     * Compile and run a program that has ALREADY BEEN READ — the {@code code::T} a {@link #parse} lifts to
     * ({@link studio.phaseshift.metatron.isa.m.type.parser.Parser.Helper#toCode}) — on this machine. It is
     * {@link #evaluate(String)} without the text: what the compiler's parse stage would have produced is handed
     * in instead, so the program is evaluated exactly once whichever compiler is installed. The default
     * compiler reads text itself, but a compiler may accept only {@code code::T} (the test-only category
     * compiler does), and handed text such a compiler fails before anything runs.
     *
     * @param code the program, as code
     * @return the obj the program computed, or the {@code fail::T} that stopped it
     */
    public static Obj evaluate(final Code code) {
        final Machine machine = Machine.current();
        return Machine.run(machine, machine.compiler().apply(code));
    }

    private static Obj run(final Machine machine, final Obj compiled) {
        // a fail leaves the inst chain; it is returned as itself, never run
        if (compiled.isFail())
            return compiled;
        return machine.processor().code(compiled.asCode()).apply(noobj());
    }

    static Obj write(final Obj obj) {
        return Machine.write(obj.vid(), obj);
    }

    static boolean loaded() {
        return null != ROOT_MACHINE;
    }


    // ======================== the component contract ========================

    interface Component extends Rec {
        default Machine machine() {
            Obj mach = this.parent();
            while (!mach.isNoObj() && !(mach instanceof Machine))
                mach = mach.parent();
            return mach instanceof Machine ? mach.as() : mach0();
        }
    }

    // @Override  (disabled with the apply() below)
    // ======================== execution ========================
    // TEMPORARILY DISABLED: this apply() re-homed CURRENT (Machine.current(this)) as a side effect, which made a bare
    // machine dereference (*X) into a context switch. Dereference must stay pure; the explicit move() instruction in
    // /m/mach is now the ONLY context switch. Until that is re-integrated, Machine inherits Obj.apply() (identity).
    /*
    default Obj apply(final Obj call) {
        return this.apply(call.asCode(), noobj());
    }

    default Obj apply(final Code code, final Obj start) {
        final Machine previous = CURRENT.get();
        Machine.current(this);
        this.push();
        try {
            return this.processor().code(this.compiler().apply(code).asCode()).apply(start);
        } finally {
            this.pop();
            if (null == previous)
                CURRENT.remove();
            else
                CURRENT.set(previous);
        }
    }
    */

    /**
     * Apply code to a lhs <em>in this machine</em>: compile it against the machine's compiler, then run the compiled
     * form on its processor. This is the single place a {@link Code} hands off to a machine, which is what makes the
     * machine the unit of execution. Code reaches ANOTHER machine by being shipped there in a plan ({@code law_shard}:
     * {@code start([code]).select([to(<peer>)]).merge()}), never by a frame here that stands in it.
     */
    default Obj applyCode(final Obj lhs, final Code code) {
        final Call call = code.tryToInst();
        if (call.isCode()) {
            // wrapStart prepends start(value) for a value lhs; a monadic lhs passes through unchanged and rides
            // START so the monad's loop/state context survives into the processor.
            final Code compiled = this.compiler().apply(Code.wrapStart(lhs, call.as())).asCode();
            return this.processor().code(compiled).apply(lhs.isMonad() ? lhs : noobj());
        }
        // single inst: dispatch by the inst's own monad flag. A monadic inst (loop()) receives the monad; a value
        // inst is resolved and applied against the monad's obj.
        final boolean monadic = call.isInst() && call.resolve(lhs).tid().hasQ(MONAD_IN);
        final Obj arg = lhs.isMonad() && !monadic ? lhs.asMonad().obj() : lhs;
        return call.resolve(arg).apply(arg);
    }

    // ======================== components ========================

    /**
     * The machine's instruction set: instruction memory, and the ONE locus for every instset the machine uses.
     * <p>
     * The slot holds the ISA as <b>itself</b> — an instance bound at construction, the way {@link #memory()} and
     * {@link #network()} are, and never a template the accessor applies. That reversal is the point: an ISA is a
     * component the machine owns, so it is created WITH the machine rather than materialized from a lambda.
     * <p>
     * A machine with no ISA yet gets a stable empty one ({@code AbstractMachine} memoizes it) rather than a slot
     * write: a pushed child shares its parent's rec map, and a rec write on a machine rec fails its own type check.
     */
    default InstSet instset() {
        final Obj proto = this.atDirect(uri(INSTSET));
        return proto instanceof InstSet isa ? isa : new BasicInstSet();
    }

    /**
     * The machine's memory: the facade over the world of spaces, itself a {@link Space}, and the single interface
     * for reading and writing addresses.
     */
    default Memory memory() {
        final Obj proto = this.atDirect(uri(MEMORY));
        if (proto.isNoObj()) {
            final Memory memory = new BasicMemory();
            this.at(uri(MEMORY), memory, MUTABLE);
            return memory;
        } else if (proto instanceof Memory) {
            return (Memory) proto;
        } else {
            final Memory memory = (proto.isInst() || proto.isCall()) ? proto.apply().as() : proto.as();
            this.at(uri(MEMORY), memory, MUTABLE);
            return memory;
        }
    }

    /**
     * Bind this machine's memory — used by {@link #push()} to hand a child machine its {@link MemoryUnion} over the
     * parent's memory.
     */
    default Machine memory(final Memory memory) {
        CommonUtil.close(this.atDirect(uri(MEMORY)));
        return this.at(uri(MEMORY), memory, MUTABLE).as();
    }

    /**
     * Bind this machine's network — used by {@link #push(fURI)} to hand a child machine its own
     * {@link studio.phaseshift.metatron.isa.mach.type.network.NetworkUnion} level. The close on the
     * displaced value is the close-before-clear contract: whatever reach the old level held hangs up.
     */
    default Machine network(final Network network) {
        CommonUtil.close(this.atDirect(uri(NETWORK)));
        return this.at(uri(NETWORK), network, MUTABLE).as();
    }

    /**
     * This machine's own SPHERE ({@link MachineSpace}): the machine itself, its components and its system information,
     * answered by {@link #read} and {@link #write} before the network and memory. Held by the machine and never indexed
     * by memory; {@code null} for a machine with no vid, which has no address to answer for.
     */
    MachineSpace space();

    /**
     * Bind this machine's sphere — {@link #bootstrap()} does, once per machine.
     */
    Machine space(final MachineSpace space);

    /**
     * Mount this machine's own data level and bind its sphere. The data level is a space with pattern {@code <vid>/#},
     * so {@code ~/thread} and every other address of the machine's data resolve there as space values, not through
     * any vid intercept. The sphere ({@link MachineSpace}) — which {@link #read}/{@link #write} ask before the network
     * and memory — answers the machine itself and its components, and holds its system information
     * ({@code ~/sys/fail}, {@code ~/sys/log}, …) in its own store. Called for the root in {@link AbstractMachine}'s
     * constructor and for each child in {@link #push(fURI)}.
     * <p>
     * Everything here goes through {@code this.memory()} or the sphere itself at a resolved address, because bootstrap
     * runs before the machine is reachable through {@code Machine.write} or {@code ~}.
     */
    default Machine bootstrap() {
        if (this.hasVID()) {
            // The infra PATTERN is the resolved vid (/. → /#): /thread must match, and the root's "local" is
            // everything. But the machine rec is written at the UNRESOLVED node vid (/.): a rec at a node is a
            // single value, whereas at the branch / it would fan out into /q, /instset, … entries.
            final fURI resolved = this.vid().resolve();
            // This machine's OWN address space, at <vid>/# (the root's is /#): what a vid-less `#` cache used to
            // claim by accident, before it was made unregistered. It must be PER MACHINE — a single root /# also
            // matches /xyz/iso, so a child's write would land in the root and outlive its machine. The vid-scoped
            // pattern is more specific than /# and less specific than every mounted space, so mostSpecific gives
            // each machine its own level: own, no leak up, no leak across — by construction rather than by accident.
            final memSpace machineSpace = memSpace.unregistered(resolved.extend(ALL), resolved.extend(SPACE));
            // The machine's own address space carries the same q-processors as the sphere's ~/sys store: an address
            // that lands here (a machine that mints no sphere of its own — a peer machine, say — resolves ~/sys/fail
            // into this catch-all) must still find the processor its ?q names, or the write is refused as unattached.
            machineSpace.addQ(QCollection.docQ());
            machineSpace.addQ(QCollection.subQ());
            machineSpace.addQ(QCollection.incrQ());
            machineSpace.addQ(QCollection.mimeQ());
            machineSpace.addQ(QCollection.lockQ());
            this.memory().addSpace(machineSpace);
            // THE MACHINE'S OWN SPHERE (Marko, 2026-10-25): the machine itself, its components and its system
            // information, answered FIRST by Machine.read/write and never indexed by memory, which keeps the machine's
            // data (the catch-all above). One per machine, so a pushed frame's sphere is its own and leaves with it.
            // ~/sys/# is the sphere's own store ("sys/# simply go into MachineSpace"): there is no sys space in memory.
            this.space(new MachineSpace(this));
            // THE NETWORK ANSWERS ITS OWN BRANCH, through the sphere: the component answers <vid>/network/# from its rec
            // — every field at its own address, a peer or a listener at its key's path, and a noobj write on a member
            // that member's retraction. mount stamps the branch; the network is not a space in memory.
            if (this.network() instanceof BasicNetwork network)
                network.mount(this);
            // the machine rec embeds at its own vid: ~ → <vid> reaches it, and its entries are space embeddings.
            this.memory().write(this.vid(), this);
            // this machine's log sink, in its sphere's ~/sys: a machine with none answers noobj, and
            // GraphittyObjLogger.doLog then logs every level
            final fURI vid = this.vid().resolve().extend(Helper.SYS_DIR.extend(LOGG));
            GraphittyObjLogger.setLogger(Helper.LOG_SINK);
            this.space().write(vid, LogObj.sink(Helper.logLevels(this), vid));
        }
        return this;
    }

    /**
     * This thread's machine stack, not initialized eagerly: until {@link #push()} is used there is no machine, and every
     * component accessor falls through to the machine's own slot.
     */
    /**
     * Push a child machine and make it current, returning the child.
     */
    default Machine push() {
        return this.push(CommonUtil.mintShortUUID(null, false));
    }

    /**
     * Push a child machine whose vid strictly extends this machine's by {@code extension}, and return the child.
     * The extension is a uri because it is an expression: multi-segment, templated and composed steps all work.
     * <p>
     * An extension carrying an <b>authority</b> is refused: it names another mtron instance rather than another
     * machine, and a frame does not cross a process boundary. Code reaches another instance by being shipped there
     * in a plan ({@code law_shard}); spawning that instance ({@link PeerSpawn}) is a mechanism still waiting for its
     * instruction.
     */
    default Machine push(final fURI extension) {
        // AN AUTHORITY IS A PROCESS BOUNDARY, NOT A FRAME. A peer has its own vid, its own memory and its own
        // lifetime, so it can neither descend from this machine nor share its memory -- and nothing here may stand in
        // it either: the frame that did (it shipped every code application made in it) was a relic of the
        // distribution the law_shard plans replaced. A HOST is the test rather than hasAuthority(), which also
        // demands an explicit port.
        if (null != extension && extension.hasHost())
            throw MTronException.of("push(%s) names an authority: a machine is pushed within this process, and code reaches another one by being shipped in a plan (law_shard)", extension);
        final fURI parentVID = this.vid();
        // The ADDRESS ARITHMETIC IS SHARED with move and pop (Helper.machineAddress): `.`, `..` and `~` mean the
        // same thing to all three, so a spelling cannot mean "up" to one instruction and something else to
        // another. What differs is only what the instruction then DOES with the address — push mints it, move
        // walks to it, pop discards it. A machine with NO vid (test machines, and any rootless construct) has no
        // address to extend, so the extension becomes the child's address outright and the descendant rule below
        // does not apply: only a machine that HAS a parent address has to stay under it.
        final fURI childVID = Helper.machineAddress(this, extension);
        if (null != parentVID && !Helper.descends(parentVID, childVID))
            // print the RESOLVED forms, because the raw ones are what made this message hard to act on: the check
            // compares resolved segment lists, so a raw `/.` and a raw `aaa` say nothing about which comparison
            // failed. A machine's address must strictly extend its parent's, and now the message shows both sides.
            // This is the guard for a caller that hands push an escape DIRECTLY: the push INSTRUCTION no longer
            // reaches it for `..`, because it resolves the address and mints the leaf from the address's parent
            // (push(<../b>) == move(<..>).push(<b>)). So the caller here is Java, not a machine address.
            throw MTronException.of("push(%s) does not descend from %s: resolved %s is not a strict descendant of %s",
                    extension, parentVID, childVID, parentVID.resolve());
        // THE CHILD OWNS ITS REC. Passing null keeps the clone's OWN copy of the map instead of handing the child
        // this machine's map object. While the map was shared, `machine.at(<component key>)` — which is how an
        // ADDRESS read of `/xyz/instset` is answered — reported the ENCLOSING machine's component, while the
        // accessor (instset()) reported the child's: the same slot read two ways, disagreeing. A component belongs to
        // the machine that holds it, so the rec holding it must be the machine's too. The child still starts with a
        // SNAPSHOT of this machine's keys (clone() copies them), which is all the machine rec ever offered — `at()`
        // does not walk the parent chain.
        final Machine child = (Machine) this.clone(null, this.tid(), childVID);
        if (child == this)
            throw MTronException.of("push(%s) cannot mint a child: clone() returned this machine, so the child would BE the parent", extension);
        child.parent(this);
        // The child's memory is a union over this machine's memory: its own fresh level over the enclosing one.
        // Parent the union to the CHILD (not to the parent machine): Component.machine() walks parent(), and the
        // read path's getSpaceFor/readAbsolute/writeAbsolute use it to decide whether the machine answers for its
        // own rec keys. AbstractMachine.memory(Memory) stores only the resolvedMemory field, which is why the
        // parent link must be set explicitly here.
        final MemoryUnion memory = new MemoryUnion(this.memory());
        memory.parent(child);
        child.memory(memory);
        // The child's instruction set is its OWN level over this machine's — the same move as the memory union
        // above, and for the same reason: a component belongs to the machine that holds it, so a declaration or an
        // import made while standing in the child lands in the CHILD's level and the enclosing machine's vocabulary
        // is untouched.
        final BasicInstSet childInstSet = new BasicInstSet(this.instset());
        child.instset(childInstSet);
        // ... and the child's own components go into ITS rec, so that an ADDRESS read of a component slot
        // (`*</xyz/instset`) answers with the child's component exactly as the accessor does. Written into the map
        // directly rather than through at(): a rec write on a machine rec fails its own type check (objCheckAndSave
        // -> Lst.test, a null list element), which is why every component is seeded through the constructor map —
        // see AbstractMachine.withMemory — rather than assigned into a live machine.
        // The child's NETWORK is its OWN level too — the same move a third time, under the network's two
        // laws: KNOWLEDGE IS SHARED by addressing (every level reads the one roster at the root address, so
        // no plumbing here), and REACH IS NOT — peers are neither inherited from the parent (not even
        // shadowed) nor shared with children. A connection belongs to exactly the machine that dialed it,
        // so a pop retracts precisely this machine's dials and a sibling never sees them.
        final studio.phaseshift.metatron.isa.mach.type.network.NetworkUnion childNetwork =
                new studio.phaseshift.metatron.isa.mach.type.network.NetworkUnion();
        childNetwork.parent(child);
        // the SETTER, not just the rec: AbstractMachine caches resolvedNetwork, and the child's clone
        // carries the parent's cached field — without the setter the accessor answers the parent's
        // component forever and the rec slot is dead weight (measured: net= identity equal across push).
        child.network(childNetwork);
        child.jvm().put(uri(MEMORY), memory);
        child.jvm().put(uri(INSTSET), childInstSet);
        child.jvm().put(uri(NETWORK), childNetwork);
        // THE CHILD IS CURRENT WHILE IT BOOTSTRAPS. bootstrap writes the child's own infra (the machine rec at
        // its vid, the per-machine log sink, ~-relative state), and every one of those writes belongs to the
        // CHILD's level. Left on the parent, each self-write landed in the parent's catch-all space instead,
        // so the parent answered for the child's own address with a synthesized rec — and a sibling could not
        // read the child back at its vid, which is what `move(<child>)` resolves.
        CURRENT.set(child);
        child.bootstrap();
        // register the child rec in the ROOT's space (global): read(childVID) from ANY machine reaches it, so
        // move(childVID) works from a sibling at any depth. the child's data stays in its own infra, so isolation holds.
        Machine.root().memory().write(childVID, child);
        return child;
    }

    /**
     * Discard the current machine and release what it opened.
     */
    default Machine pop() {
        final Machine machine = CURRENT.get();
        if (null == machine)
            return this;
        // A spawned peer's lifetime is its machine's: a process this machine forked is reaped with the machine (a
        // no-op for a machine that forked none).
        PeerSpawn.reap(machine);
        final Obj parent = machine.parent();
        if (!(parent instanceof Machine parentMachine))
            return this;
        machine.close();
        CURRENT.set(parentMachine);
        return parentMachine;
    }

    @Override
    default void close() {
        this.logger().info("closing machine %s", this.vid());
        this.memory().close();
        this.instset().close();
        this.network().close();
        this.pop();
    }

    /**
     * The machine's roster of peers. Created rather than defaulted: an absent slot must become a real roster, or
     * every peer registered into it vanishes silently.
     * <p>
     * Resolved the same way {@link #memory()} is, and for the same three reasons. A slot that already holds a
     * network is handed back <b>as itself</b> — no apply, so a component can be bound at construction, before
     * there is any machine to compile with. Whatever is resolved is written <b>back into the slot</b>, so the
     * machine ends up holding its real components rather than a default that is re-minted on every read. And a
     * deferred seed (an inst) is applied, so the older spelling still works. Leaving the result unretained is what
     * kept this slot a template.
     */
    default Network network() {
        /*final Obj protoNetwork = this.atDirect(NETWORK).orThrow(MTronException.of("machine has no network: %s", this.type().vid()));
        if (protoNetwork.isCall())
            return protoNetwork.apply().as();
        return protoNetwork.clone().as();*/

        final Obj proto = this.atDirect(uri(NETWORK));
        if (proto.isNoObj()) {
            final Network network = new BasicNetwork();
            this.at(uri(NETWORK), network, MUTABLE);
            return network;
        } else if (proto instanceof Network) {
            return (Network) proto;
        } else {
            // an instLambda is an Inst whose isCall() is false, so the isInst() arm is what runs a deferred seed
            final Network network = (proto.isInst() || proto.isCall()) ? proto.apply().as() : proto.as();
            this.at(uri(NETWORK), network, MUTABLE);
            return network;
        }
    }

    /**
     * @return this machine with the given instruction set bound
     */
    default Machine instset(final InstSet instset) {
        // NO close of the outgoing value: it is a COMPONENT now rather than a template, and on a pushed child the
        // value being replaced is the ENCLOSING machine's ISA — closing it would release the parent's vocabulary.
        return this.at(uri(INSTSET), instset, MUTABLE).as();
    }

    /**
     * @return the machine's compiler, creating one if the slot is unbound
     */
    default Compiler compiler() {
        final Obj protoCompiler = this.atDirect(COMPILER).orThrow(MTronException.of("machine has no compiler: %s", this.type().vid()));
        if (protoCompiler.isCall())
            return protoCompiler.apply().as();
        return protoCompiler.as();
    }

    /**
     * @return this machine with the given compiler bound
     */
    default Machine compiler(final Compiler compiler) {
        return this.at(uri(COMPILER), compiler, MUTABLE).as();
    }

    default Machine compiler(final Call templateCompiler) {
        return this.at(uri(COMPILER), templateCompiler, MUTABLE).as();

    }


    /**
     * @return the machine's processor, creating one if the slot is unbound
     */
    default Processor processor() {
        final Obj protoProcessor = this.atDirect(PROCESSOR).orThrow(MTronException.of("machine has no processor: %s", this.type().vid()));
        if (protoProcessor.isCall())
            return protoProcessor.apply().as();
        return protoProcessor.clone().as();
    }

    /**
     * @return this machine with the given processor bound
     */
    default Machine processor(final Processor processor) {
        return this.at(uri(PROCESSOR), processor, MUTABLE).as();
    }

    default Machine processor(final Call templateProcessor) {
        return this.at(uri(PROCESSOR), templateProcessor, MUTABLE).as();
    }

    class Helper {

        /**
         * The machine-local system directory: {@code ~/sys}, one level below the component slots so {@code ~}'s direct children stay the components.
         */
        public static final fURI SYS_DIR = f("sys");

        private static final fURI TILDE = f("~");

        /**
         * The sphere that answers {@code vid} for a thread standing in {@code machine}: the innermost machine — this
         * one, then each enclosing machine — whose {@link MachineSpace} covers the address, or {@code null} when none
         * does. A child's sphere is its own vid, so {@code ~/network} is the child's network, while
         * {@code /network/roster}, outside every child's sphere, is the root's.
         */
        public static MachineSpace sphereOf(final Machine machine, final fURI vid) {
            Obj level = machine;
            while (level instanceof Machine at) {
                final MachineSpace space = at.space();
                if (null != space && space.covers(vid))
                    return space;
                final Obj parent = at.parent();
                if (parent == at)
                    return null;
                level = parent;
            }
            return null;
        }

        /**
         * The address a sphere would answer {@code vid} at: {@code ~} resolved against the machine the thread stands in,
         * exactly as memory resolves it on a write, and an absolute address as given. A relative name is never in a
         * sphere, so it answers {@code null} — as does {@code ~} on a machine with no vid.
         */
        static fURI sphereAddress(final Machine machine, final fURI vid) {
            if (vid.hasPrefix(TILDE))
                return machine.hasVID() ? machine.vid().extend(vid.pretract(1)).resolve() : null;
            return vid.isAbsolute() ? vid : null;
        }

        /**
         * The sphere's answer for {@code vid}, read by a thread standing in {@code machine} — or {@code noobj}, the
         * decline, when no sphere covers the address or the covering one has nothing there. The first step of
         * {@link Machine#read}, and of {@link #readLocal}.
         */
        static Obj readSphere(final Machine machine, final fURI vid) {
            final fURI here = sphereAddress(machine, vid);
            final MachineSpace sphere = null == here ? null : sphereOf(machine, here);
            return null == sphere ? noobj() : sphere.read(here);
        }

        /**
         * The sphere's write of {@code obj} at {@code vid}, when a sphere OWNS the address — empty when none does, and
         * the write goes on. Owned is final, whatever it answers: a member's deletion answers {@code noobj}, and handing
         * that on as a decline would land a stray deletion in memory. An obj written at its own {@code ~} vid is stamped
         * with the resolved one, as memory stamps it, so the obj and its address agree on where it lives.
         */
        static Optional<Obj> writeSphere(final Machine machine, final fURI vid, final Obj obj) {
            final fURI here = sphereAddress(machine, vid);
            final MachineSpace sphere = null == here ? null : sphereOf(machine, here);
            if (null == sphere || !sphere.ownsWrite(here))
                return Optional.empty();
            if (!here.equals(vid) && vid.equals(obj.vid()))
                obj.selfVID(here);
            return Optional.of(sphere.write(here, obj));
        }

        /**
         * A read IN THIS PROCESS: the machine's sphere, then its memory — where an address lives locally, without the
         * network's gate or instruction memory's union fallback. For code that re-reads an obj at its own vid (the log
         * sink, a reflected rec): it must find the obj wherever the machine keeps it, and {@code ~/sys} is the sphere's,
         * not memory's. (An address an instruction set holds is memory's lookup to route: it weighs the instset's
         * libraries.) A miss costs nothing here, where {@link Machine#read} would go on to scan instruction memory.
         */
        public static Obj readLocal(final Machine machine, final fURI vid) {
            final Obj owned = readSphere(machine, vid);
            return owned.isNoObj() ? machine.memory().read(vid) : owned;
        }

        /**
         * A write IN THIS PROCESS: to the sphere when it owns the address, else to memory — {@link #readLocal}'s pair.
         */
        public static Obj writeLocal(final Machine machine, final fURI vid, final Obj obj) {
            return writeSphere(machine, vid, obj).orElseGet(() -> machine.memory().write(vid, obj));
        }

        /**
         * every started discovery source, closed with the VM
         */
        private static final List<Closeable> SOURCES = new ArrayList<>();

        /**
         * stop every discovery source — a responder that outlives the VM keeps announcing a dead address
         */
        public static void closeSources() {
            synchronized (SOURCES) {
                SOURCES.forEach(CommonUtil::close);
                SOURCES.clear();
            }
        }

        /**
         * CANONICALIZE A SPELLING — the ONE door a short name is turned into the address it denotes, and the reason
         * it is a method rather than three inline expressions is that it has three callers with no other way to
         * agree: {@link Machine#read}, {@link Machine#write}, and the compiler's move-extractor.
         * <p>
         * Each component owns the slot it can match, and only that slot: the ISA keeps the <b>scheme</b> prefixes
         * ({@code math:cos} → {@code /m/math/cos}) because an import is what gave the name, and the network keeps
         * the <b>authority</b> prefixes ({@code //ginger/a} → {@code ws://ginger.local:8555/a}) because a peer is
         * what the name reaches. The two slots cannot collide, so the order they are asked in never matters — which
         * is what keeps this a canonicalization rather than a precedence ladder.
         * <p>
         * A scheme-less vid passes through untouched: {@code alignPrefix} normalizes to coefficient one, which must
         * not be imposed on an ordinary read, and a RELATIVE name stays relative — "in the machine" is memory's
         * contract to interpret, not this door's.
         *
         * @param machine the machine whose components hold the tables ({@code null} passes the vid through)
         * @param vid     the address as spelled
         * @return the address the spelling denotes, or {@code vid} itself when no table holds it
         */
        public static fURI align(final Machine machine, final fURI vid) {
            if (null == machine || null == vid || (!vid.hasScheme() && !vid.hasHost()))
                return vid;
            fURI name = vid;
            // isPrefixed() IS the scheme slot's predicate: a scheme with NO host names a vocabulary (math:cos), while a
            // scheme WITH a host already names a transport (loop://self) and can never be a prefix.
            if (vid.isPrefixed()) {
                final fURI aligned = machine.instset().alignPrefix(vid);
                // alignPrefix answers the coefficient-one form for an UNPREFIXED spelling too; the original must be
                // kept then, so a caller's coefficient survives an alignment that had nothing to align
                if (!aligned.equals(vid.one()))
                    name = aligned;
            }
            // ... AND THE AUTHORITY SLOT, after the scheme slot. The network's own guard narrows it to a name-shaped
            // authority -- no scheme, a host, no port -- because isAddressed() alone would also claim ws://host:8555. The two cannot fire on the same spelling (one needs a
            // scheme and no host, the other a host and no port), so the order is unobservable and no precedence rule
            // exists to get wrong -- which is what makes this a canonicalization and not a ladder.
            return name.hasHost() ? machine.network().alignPrefix(name) : name;
        }

        /**
         * Resolve a machine ADDRESS against the machine it is written in — the ONE definition of what {@code .},
         * {@code ..} and {@code ~} mean to a machine instruction, shared by {@link Machine#push(fURI) push},
         * {@code move} and {@code pop} so the three cannot drift apart.
         * <p>
         * The arithmetic is fURI's, deliberately. {@code .} and {@code ..} are kept at parse and folded HERE,
         * because this is the first place the BASE is known: {@code ..} reduces (it pops a segment of the machine's
         * address), so {@code ../b} is the sibling {@code b}, {@code ~/..} is the parent machine, and {@code .} is
         * the machine itself.
         * <p>
         * {@code ~} is consumed BEFORE the fold rather than left inside it: the fold pops whatever precedes a
         * {@code ..}, so a base left in the path would be eaten by its own displacement ({@code ~/..} would
         * collapse to the empty path instead of naming the parent).
         * <p>
         * An absolute base popped PAST the root <b>saturates</b> — it stays AT the root — rather than becoming a
         * displacement. A displacement ({@code ../../..}) is a direction, not a place, and a machine instruction
         * has to stand somewhere; saturation is also exactly the no-op {@link Machine#pop()} already performs
         * when a machine has no parent. A machine with no vid (a test machine, any rootless construct) has nothing
         * to descend from, so there the target becomes the address outright — which is what makes
         * {@code push(<a/b>)} mint a two-segment address on a vid-less machine.
         *
         * @param here   the machine the address is written in — the base for a relative target
         * @param target the address as spelled, with {@code .}/{@code ..}/{@code ~} still in it
         * @return the resolved address, or {@code here}'s own vid when the target is null
         */
        public static fURI machineAddress(final Machine here, final fURI target) {
            if (null == target)
                return null == here ? null : here.vid();
            // A SPELLING MAY BE A NAME, so canonicalize BEFORE the arithmetic: `push(<//worker/a>)` and
            // `move(<//worker/a>)` must mean the same place, and push/pop have no compile-time extractor to lean on
            // the way move does. One door (align), so a name resolves the same way wherever a frame is addressed.
            final fURI address = align(here, target);
            // a wire address names a peer across a process boundary, not a place in this tree: taken as written
            if (address.hasScheme() || address.hasHost())
                return address;
            final fURI home = null == here ? null : here.vid();
            final boolean fromHome = address.hasPrefix(f("~"));
            if (fromHome && address.equals(f("~")))
                return null == home ? f("/") : home.resolve();
            if (!fromHome && address.isAbsolute())
                return address.resolve();
            if (null == home)
                return address.resolve(); // no address to descend from: the extension IS the address
            final fURI base = home.resolve();
            final fURI composed = base.extend(fromHome ? address.pretract(1) : address).resolve();
            // saturation: an absolute base popped past the root is the root, never a displacement
            if (base.isAbsolute() && (composed.isEmpty() || composed.isRelative()))
                return f("/");
            return composed;
        }

        /**
         * Whether {@code candidate} is a STRICT DESCENDANT of {@code ancestor} — the candidate's segments begin
         * with the ancestor's and go at least one deeper. (Descendant rather than child, deliberately: the
         * extension is an expression, so {@code a/b} and {@code a/../b} both mint through the same path arithmetic,
         * and a multi-segment extension addresses a deeper node of the SAME branch.)
         * <p>
         * Expressed in fURI's own terms — {@code resolve()}, {@code asNode()}, {@code pathLength()} and
         * {@code hasPrefix()} — and that is the point: the string form this replaces
         * ({@code child.removeSubpath(parent).pathString().equals(child.name())}) answers TRUE for a
         * single-segment escape — {@code /esc-b} under {@code /esc-a} — because the failed replacement leaves the
         * WHOLE path to be compared against its own last segment, and they agree. The segment-wise test also keeps
         * {@code a/../b} legal (it reduces back inside the parent).
         * <p>
         * WHO THIS GUARDS NOW. A machine address that crosses a level is no longer a caller error: the push
         * INSTRUCTION resolves the address and mints its leaf from the machine at the address's PARENT, so
         * {@code push(<../b>) == move(<..>).push(<b>)} and never arrives here. This is the guard for
         * {@link Machine#push(fURI)} called DIRECTLY, where a single machine must still be the parent of what it
         * mints and an escape has no parent to be re-homed to.
         * <p>
         * {@code asNode()} on both sides, because the root's address is the bare absolute marker ({@code /} is the
         * one-element path {@code [""]}) whose empty segment is the branch slash rather than a segment; as a node it
         * has NO segments, which is what makes every real address its descendant.
         */
        public static boolean descends(final fURI ancestor, final fURI candidate) {
            final fURI base = ancestor.resolve().asNode();
            final fURI child = candidate.resolve().asNode();
            return child.pathLength() > base.pathLength() && child.hasPrefix(base);
        }

        /**
         * The {@code ~}-relative log sink, left unexpanded so {@code GraphittyObjLogger} reads each machine's own.
         */
        public static final fURI LOG_SINK = f("~/sys/log");

        /**
         * The levels a machine's log sink starts with — the enclosing machine's, else the boot profile's default.
         */
        static Rec logLevels(final Machine machine) {
            final Obj parent = machine.parent();
            if (parent instanceof Machine enclosing && enclosing.hasVID()) {
                // the enclosing machine's own sink, in its sphere (~/sys is not memory's)
                final Obj sink = Helper.readLocal(enclosing, enclosing.vid().resolve().extend(SYS_DIR.extend(LOGG)));
                if (sink.isRec()) {
                    final Obj levels = sink.asRec().at(uri(LEVEL));
                    if (levels.isRec())
                        return levels.asRec();
                }
            }
            return BootLoader.LOG_LEVELS;
        }

        /**
         * INSTRUCTION MEMORY IS BEING READ — this thread is inside the instset hop of {@link Machine#read(fURI)}. A
         * read made FROM an ISA read is a data read: answering one resolves the dom/rng types of the pattern, and
         * resolving a type is itself a read, so without this the hop re-entered itself until the stack died
         * (measured: seven StackOverflowErrors in MachPushTest, where reads happen while a machine is mid-construction
         * and memory has not been populated yet).
         */
        private static final ThreadLocal<Boolean> READING_INSTRUCTION_MEMORY = ThreadLocal.withInitial(() -> false);

        static boolean isReadingInstructionMemory() {
            return READING_INSTRUCTION_MEMORY.get();
        }

        static void readingInstructionMemory(final boolean reading) {
            READING_INSTRUCTION_MEMORY.set(reading);
        }

        /**
         * The zero machine: no spaces, components or route table, and every mutator a no-op, so nothing written
         * against it can reach a live machine. It is the fallback {@link Machine#root()} serves before boot, and
         * constructing it has no side effects.
         */
        public static final class Machine0 extends MRec implements Machine {
            private static final Machine0 INSTANCE = new Machine0();

            public static Machine0 single() {
                return INSTANCE;
            }

            private Machine0() {
                super(Map.of(), MACH_MACHINE_TID.zero(), null);
            }

            @Override
            public void close() {
            }

            private Memory memory = null;

            @Override
            public Memory memory() {
                if (null == this.memory)
                    this.memory = new BasicMemory();
                return this.memory;
            }

            @Override
            public MachineSpace space() {
                return null;
            }

            @Override
            public Machine space(final MachineSpace space) {
                return this;
            }

            private Network network = null;

            @Override
            public Network network() {
                if (null == this.network)
                    this.network = new BasicNetwork();
                return this.network;
            }

            private InstSet instset = null;

            @Override
            public InstSet instset() {
                if (null == this.instset)
                    this.instset = new BasicInstSet();
                return this.instset;
            }

            public Obj read(final fURI vid) {
                return noobj();
            }

            public Obj write(final fURI vid, final Obj obj) {
                return noobj();
            }

        }

    }
}
