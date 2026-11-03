package studio.phaseshift.metatron.isa.m.type.impl;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.ARGS;
import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The predicate's SLOT DOCS travel with the declaration: {@code isaPredicate(DocArg…)} states the shape AND what each
 * slot is, and {@code create()} publishes both at {@code <vid>?docq} — the {@code args} slot keyed by the predicate's
 * own keys, so a doc can never describe a slot the predicate does not have.
 */
public class TypeBuilderDocArgsTest extends AbstractMetatronTest {

    @Test
    void predicateArgDocsShipWithTheDocqWrite() {
        final Obj slot = uri("instruction_set_architecture");
        final Type built = TypeBuilder.build()
                .tid(REC_TID)
                .vid(f("/m/typebuilder/predicate_doc_probe"), "a probe type whose slots are documented")
                .isaPredicate(TypeBuilder.DocArg.of(slot, T(REC_TID), "the machine's instruction set architecture"))
                .create();
        PendingWrites.drainUnder(f("/m/typebuilder"));
        final Obj doc = Machine.read(PendingWrites.docq(built.vid()));
        assertTrue(doc.isRec(), "the type's documentation reached ?docq: " + doc);
        final Obj args = doc.asRec().at(uri(ARGS));
        assertTrue(args.isRec(), "the doc carries an args slot: " + doc);
        assertEquals("the machine's instruction set architecture", args.asRec().at(slot).asStr().strValue(),
                "the slot doc is keyed by the predicate's own key: " + args);
        assertEquals("a probe type whose slots are documented", doc.asRec().at(uri("desc")).asStr().strValue(),
                "the object's own description travels beside the slot docs: " + doc);
    }
}
