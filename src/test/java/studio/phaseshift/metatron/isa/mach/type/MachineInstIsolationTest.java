package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;
import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import studio.phaseshift.metatron.isa.Space;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import java.util.concurrent.atomic.AtomicReference;
import studio.phaseshift.metatron.isa.m.type.Type;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import studio.phaseshift.metatron.isa.m.type.Inst;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/**
 * Isolation and coordinate semantics for the machine's INSTRUCTION SET, with INSTS as the kind under test.
 * <p>
 * Same space, different kind: the framework is unchanged, which is the point -- isolation is a property of the SPACE,
 * not of what you happen to put in it. Types, consts and insts all go in through the same {@code write} and come back
 * through the same {@code read}, and the typed accessor agrees with the space view for each.
 */
@SuppressWarnings("MEASURED: an inst is indexed BY ITS TYPE, not by the address it is written at. AbstractInstSet.write "
        + "files it under inst.tid().basePath() and turns the written address into a small->big REDIRECT "
        + "(registerRedirect(f(vid.name()), vid)); a type, by contrast, goes straight into TYPE_TABLE keyed by the vid "
        + "you wrote. So read(address) tests the inst's TID against the address and finds nothing when the two do not "
        + "correspond. Memory, types and consts index by the written address; insts do not. This is the ISA's model "
        + "(you dispatch to an inst BY TYPE), not a defect -- but this framework's independent key(name)/value(n) "
        + "hooks cannot express it: for insts the READ ADDRESS must be derived from the VALUE. Resolve before the "
        + "monad work, which is inst-heavy. Everything else in this instance passes once the address matches the tid.")
/**
 * HOW AN INSTRUCTION SET STORES AN INSTRUCTION -- which is NOT how it stores a type or a const, and deliberately so.
 * <p>
 * An instruction IS A TYPE: its NAME is its tid (dom/rng), and it has no meaningful vid at registration time. A vid
 * arrives only once the instruction is fully resolved -- and then it is the instruction's LOCATION IN THE CODE being
 * executed, which exists after compilation, when the processor starts processing it. So there is nothing to file an
 * unresolved instruction under, and {@code write} does the principled thing:
 * <ul>
 *     <li>files it in INST_TABLE under {@code inst.tid().basePath()} -- instructions are dispatched BY TYPE;</li>
 *     <li>turns the address you wrote into a small->big REDIRECT, {@code registerRedirect(f(vid.name()), vid)} -- the
 *     link from a written address to the type-scoped definition.</li>
 * </ul>
 * This is why this class does NOT extend {@link AbstractSpaceIsolationTest}: that base is for spaces that index by the
 * address you write (a memory, a type, a const). Asking a raw {@code read(address)} for an unresolved instruction is a
 * category error -- the read wants a VALUE and the instruction is not one yet. An instruction AS a value lives in code,
 * which is the processor's domain, not the ISA's.
 */
public class MachineInstIsolationTest extends AbstractMetatronTest {

    @AfterEach
    public void unwind() {
        while (null != Machine.frame())
            Machine.frame().machine().pop();
    }

    private Obj anInst(final fURI tid) {
        return instC(tid, lst(), (lhs, inst) -> lhs);
    }

    /** The instruction is named BY ITS TID: that is what the ISA files it under and what you dispatch by. */
    @Test
    public void testAnInstructionIsNamedByItsTid() {
        final Machine machine = Machine.defaultMachine();
        final fURI tid = f("/m/isoi1");
        final Obj inst = anInst(tid);
        machine.instset().write(f("/m/isoi1"), inst);
        assertEquals(true, machine.instset().insts().contains((Inst) inst),
                "the ISA holds the instruction, found by its tid");
        assertEquals(tid, ((Inst) inst).tid(), "and its name IS its tid -- dom/rng, not a location");
    }

    /*
     * UNRESOLVED, MEASURED, and worth resolving before the migration work:
     *
     * AbstractInstSet.write DOES call Machine.authority().registerRedirect(f(vid.name()), vid) for a non-rewrite
     * instruction -- read from the source, not inferred. But Machine.authority().redirect(f("isoi2"), true) AND
     * ...(..., false) BOTH return the relative small side, so the route registered by registerRedirect is not
     * findable through redirect(). Either redirect's contract differs from what registerRedirect establishes, or the
     * two are reached by different calls. This is the ROUTING seam -- the thing Network currently names and the thing
     * the migration work will lean on -- so it is worth one look at redirect()'s body before it matters.
     *
     * Also measured, in the same method: write files the instruction in INST_TABLE on THIS (the ISA you wrote to)
     * while registering the redirect on Machine.authority() -- one method, two machines. That is the
     * machine-to-authority link again, and it is why a route is not found through the machine that owns the ISA.
     */
}
