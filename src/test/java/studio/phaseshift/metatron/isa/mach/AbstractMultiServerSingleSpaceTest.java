package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;

/**
 * MANY SERVERS, ONE SPACE: the distributed contract, tested over whichever space a suite opts into.
 *
 * <p>A machine that distributes work mints addresses under {@link machInstSet#COMPUTE} and knows nothing about what
 * answers them. That is the property worth testing per space rather than per machine: whatever backs the namespace --
 * memory, a filesystem, a table, a document store, a broker, a graph -- the same scenarios must produce the same
 * numbers and leave the same evidence behind. So this class holds the scenarios and the harness, and a subclass
 * supplies the space. The spaces do not get pulled into the machine ISA; each opts in.
 *
 * <p>THE SPACE COMES FROM A SUPPLIER, and that is the point rather than a convenience: each server is its own JVM and
 * therefore needs its own space and its own connection, so the space must be constructed per registration. A single
 * instance handed across would be shared across the very boundary it exists to cross.
 *
 * <p>NO FALLBACK. A suite whose backing is unreachable -- no database, no broker, no docker -- fails here, in its own
 * class, saying what it could not reach. Degrading quietly to memory inside a machine contract is how a distributed
 * path stops being tested without anyone noticing.
 */
public abstract class AbstractMultiServerSingleSpaceTest extends AbstractMetatronTest {

    /**
     * the distributed namespace every machine mints into
     */
    public static final fURI COMPUTE_ROOT = machInstSet.COMPUTE;

    /**
     * where the space is STORED -- independent of the namespace it exposes
     */
    public fURI COMPUTE_VID;

    /**
     * vids are unique per registration: a fixed one would be shadowed, and evicting to clear it closes connections
     */
    private static final AtomicInteger COMPUTE_SPACES = new AtomicInteger(0);

    /**
     * THE ONE THING A SUITE SUPPLIES: its space. An INSTANCE method, called from {@code @BeforeEach}, because a space
     * belongs to the test that uses it -- the lifecycle that works in AbstractTbleSpaceTest, where the base invokes a
     * supplier rather than holding a registration for the class.
     */
    protected abstract Supplier<Space> computeSpace();

    /**
     * a fresh vid for a space about to be registered -- unique, because two spaces must never share one
     */
    protected fURI nextComputeVID() {
        return COMPUTE_VID.extend(String.valueOf(COMPUTE_SPACES.incrementAndGet()));
    }

    /**
     * ONCE PER CLASS, and no eviction. A fresh space per TEST would leave several claiming the same namespace, so a
     * subscribe and a read can land in different ones -- measured, as a reducer receiving a fail because a mailbox
     * nobody was watching. A space per class is enough: the topology is declared and cleared per test, and the space
     * outlives them. Nothing is ever removed, because removing closes.
     */
    /**
     * the space this test is using: created for it, released after it
     */
    private Space computeSpaceInstance;

    /**
     * CREATE PER TEST, REMOVE PER TEST -- the pairing AbstractSpaceTest and AbstractTbleSpaceTest use between them, and
     * the thing every one of my earlier versions got half of:
     *
     * <ul>
     *   <li>a space per TEST without removal leaves several claiming one namespace, so a subscribe and a read can land
     *       in different ones -- measured as a reducer receiving a fail</li>
     *   <li>a space per CLASS without removal is reused after its backing is torn down -- measured as "connection is
     *       closed", identically on MariaDB and Postgres</li>
     *   <li>creating without releasing, or releasing without creating, is the same mistake from either side</li>
     * </ul>
     * <p>
     * A released space must also leave the registry, or the next test resolves to one that is closed. removeSpace
     * does both, which is why the removal belongs at the END of the test rather than the start of the next one: by
     * then nothing is using it.
     */
    public AbstractMultiServerSingleSpaceTest(final fURI computeVID) {
        this.COMPUTE_VID = computeVID;
    }

    @BeforeEach
    public void registerComputeSpace() {
        computeSpaceInstance = computeSpace().get();
        // Machine.current().memory().addSpace(computeSpaceInstance);
    }

    @AfterEach
    public void releaseComputeSpace() {
        if (null != computeSpaceInstance) {
            Machine.current().memory().removeSpace(computeSpaceInstance.vid());
            computeSpaceInstance = null;
        }
    }

    /**
     * the home form's gathers, as addresses
     */
    private static List<String> gathersOf(final String source) {
        return Machine.current().compiler().rewrite().apply(ObjmtronSerializer.parse(source)).asCode()
                .insts().stream()
                .filter(inst -> inst.tid().basePath().equals(BARRIER_INST_TID))
                .map(inst -> inst.arg(0).toString())
                .toList();
    }

    @Test
    public void testTheRewriteIsInertWithoutPeers() {
        AbstractMachineTest.clearPeers();
        assertEquals(List.of(), gathersOf("{1,2,3}.plus(1).plus(2).sum()"),
                "a machine with no peers has nothing to gather from, so its reducer is left as written");
    }

    @Test
    public void testOneGatherPerPeerAddressedAtThatPeersMailbox() {
        AbstractMachineTest.declarePeers("b", "c");
        assertEquals(List.of("/usr/compute/a/barrier/b", "/usr/compute/a/barrier/c"),
                gathersOf("{1,2,3}.plus(1).plus(2).sum()"),
                "a gather per peer, addressed at the peer's mailbox -- and never at the home itself, which is in "
                        + "the roster but is not a peer of itself");
        AbstractMachineTest.clearPeers();
    }

    @Test
    public void testEachPeerIsShippedACodeCarryingItsOwnData() {
        AbstractMachineTest.declarePeers("b");
        gathersOf("{1,2,3}.plus(1).plus(2).sum()");   // compiling is what ships the worker forms
        final Code worker = Machine.read(COMPUTE_ROOT.extend("b").extend("recv")).asCode();
        assertEquals(START_INST_TID, worker.insts().getFirst().tid().basePath(),
                "the shard's data arrives IN its code, as an isInitial start, so it mints its own monads");
        assertEquals(TO_INST_TID, worker.insts().getLast().tid().basePath(),
                "and its last act is to report");
        assertEquals("/usr/compute/a/barrier/b", worker.insts().getLast().arg(0).toString(),
                "to the very mailbox the home's gather for it waits on -- both minted from one expression");
        AbstractMachineTest.clearPeers();
    }
}
