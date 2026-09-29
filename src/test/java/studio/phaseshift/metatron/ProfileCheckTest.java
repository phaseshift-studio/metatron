package studio.phaseshift.metatron;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

public class ProfileCheckTest extends AbstractMetatronTest {
    @Test
    public void dbg() {
        final Obj p = ObjmtronSerializer.eval("1.plus(2).profile()");
        System.out.println("DBG profile(): isRec=" + p.isRec() + " vid=" + p.vid() + " tid=" + p.tid());
        final Obj f = ObjmtronSerializer.eval("1.plus(2).profile()>>format");
        System.out.println("DBG >>format: isStr=" + f.isStr() + " val=" + (f.isStr() ? f.strValue().substring(0, Math.min(40, f.strValue().length())) : f));
        final Obj fl = ObjmtronSerializer.eval("1.plus(2).profile()>>flow");
        System.out.println("DBG >>flow: isRec=" + fl.isRec() + " val=" + fl);
        final Obj c = ObjmtronSerializer.eval("1.plus(2).profile()>>cache");
        System.out.println("DBG >>cache: isRec=" + c.isRec() + " val=" + c);
        final Obj s = ObjmtronSerializer.eval("1.plus(2).profile()>>stage");
        System.out.println("DBG >>stage: isRec=" + s.isRec() + " val=" + s);
    }
}
