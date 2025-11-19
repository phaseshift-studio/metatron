package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.lang.core.m.type.*;

import static studio.phaseshift.metatron.Tokens.DESC;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DocQTest extends MetatronTest {

    public void analyzeDocs(final InstSet instSet) {
        for (final Inst inst : instSet.insts()) {
            Obj doc = instSet.read(inst.tid().qLess().cLess().query("doc"));
           // LOG.info("HERE %s:", doc.type());
            if(doc.c().equals(cInt.ONE())) {
                LOG.warn("%s has no associated documentation %s", inst, doc.<DocQ.Doc>as().at(DESC));
            }
        }
    }
}
