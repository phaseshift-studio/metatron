package studio.phaseshift.metatron.isa.mach.network;

import studio.phaseshift.metatron.isa.mach.AbstractMachineTest;
import studio.phaseshift.metatron.isa.mach.AbstractMultiServerSingleSpaceTest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.Tokens.ROUTE;

/**
 * THE DISTRIBUTION REWRITE, end to end: a user writes a monoidic reduction the way a user writes it -- no barriers,
 * no addresses, no peers -- and the rewrite distributes it across the machines the row declares, the shards run and
 * report, the home folds, and the evidence is left in /sys/peer.
 *
 * <p>INHERITS THE MACHINE CONTRACT from {@link AbstractMachineTest} -- including the tests that cover what a result
 * cannot: that the rule is inert without peers, that each peer gets exactly one gather addressed at its own mailbox,
 * and that each is shipped a code carrying its own slice of the data. As that base grows its nitty-gritty analyses
 * of rosters and mailboxes, this class inherits them. What belongs here is what they cannot say -- a whole
 * computation, from source to folded result, with every machine's mailbox checked.
 *
 * <p>THE ROWS. The expectation is inline, and each row declares its own cluster: one barrier key per peer, so the
 * peer count is a property of the row. What a mailbox holds follows from the slicing, and is derivable rather than
 * guessed -- {@code machines = peers + 1}, {@code per = ceil(size / machines)}, taken in order, the home first and
 * the last machine taking the remainder; each machine applies the prefix to its own slice, and a peer's mailbox
 * holds the reducer applied to that slice. Four things these rows pin, none of which the result alone shows:
 *
 * <ul>
 *   <li>the fold is unchanged by distribution -- the same code gives the same number however it is sliced</li>
 *   <li>a nested inst argument rides along with the prefix, so each machine applies it to its own slice</li>
 *   <li>more data than machines: the slices are uneven and the last machine takes the remainder</li>
 *   <li>a reducer that is NOT last: the workers' partials carry no trace of the trailing work, which belongs to the
 *       home -- rows with the same mailboxes and different results, which is the boundary made visible</li>
 * </ul>
 */
public class DistributedRewriteTest extends AbstractMultiServerSingleSpaceTest {

    public DistributedRewriteTest() {
        super(f("/sys/space/compute"));
    }

    /**
     * THE SUITE SUPPLIES ITS BACKING. Memory here because it needs nothing installed; a space with an external
     * dependency supplies that instead, and this class names no space at all.
     */
    @Override
    protected java.util.function.Supplier<Space> computeSpace() {
        return () -> memSpace.of(rec(
                uri(PATTERN), uri("/usr/compute/#"),
                uri(QPROC), lst(QCollection.subq())), nextComputeVID());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // ONE peer, so per = ceil(3/2) = 2: the home keeps {1,2} -> 9 and the peer takes the remainder {3}
            // -> 6. Same total as the two-peer row below, different mailboxes -- which is the whole point of the
            // topology being the row's: add a barrier key and the slices, and so the mailboxes, change.
            "{1,2,3}.plus(1).plus(2).sum()           %     15  %  [a => [barrier => [b => 6]]]",
            // TWO peers, so per = ceil(3/3) = 1: home {1} -> 4, b {2} -> 5, c {3} -> 6. The SAME code and the
            // SAME 15 as the row above, sliced differently
            "{1,2,3}.plus(1).plus(2).sum()           %     15  %  [a => [barrier => [b => 5, c => 6]]]",
            // the same slices as the two-peer row above, with a nested inst argument: 1+1*3545, 2+2*3545,
            // 3+3*3545 = 3546, 7092, 10638
            "{1,2,3}.plus(mult(3545)).sum()          %  21276  %  [a => [barrier => [b => 7092, c => 10638]]]",
            // per = ceil(6/3) = 2: home {1,2} -> 9, b {3,4} -> 13, c takes the remainder {5,6} -> 17
            "{1,2,3,4,5,6}.plus(1).plus(2).sum()     %     39  %  [a => [barrier => [b => 13, c => 17]]]",
            // the SAME slices and so the SAME mailboxes as the TWO-peer row above, 25 instead of 15: the trailing
            // plus(10) happens on the home after the fold and never reaches a worker
            "{1,2,3}.plus(1).plus(2).sum().plus(10)  %     25  %  [a => [barrier => [b => 5, c => 6]]]",
            // the expression the hand-wired swarm test used, written the way a user writes it: +2 then +10 is +12
            // each, so home {1} -> 13, b {2} -> 14, c {3} -> 15, and the fold is 42
            "{1,2,3}.plus(2).plus(10).sum()           %     42  %  [a => [barrier => [b => 14, c => 15]]]",
    }, delimiter = '%')
    public void testDistributedCode(final String code, final String result, final String state) {
        // no cluster declared here: the row's expectation names one barrier per peer, so the row states its own
        // topology -- add a key to the rec and the machine count goes up. checkDistributedCode is inherited, along
        // with the contract tests that assert the plumbing these rows then exercise.
        AbstractMachineTest.checkDistributedCode(LOG, code, result, ObjmtronSerializer.parse(state).asRec());
    }
}
