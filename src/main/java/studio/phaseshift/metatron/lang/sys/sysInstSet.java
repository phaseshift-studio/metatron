package studio.phaseshift.metatron.lang.sys;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.net.remote.remoteSpace;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.sys.console.Console;
import studio.phaseshift.metatron.lang.sys.fs.fileSpace;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.q.DocQ.DOCQ_TYPE;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.furi.q.PubSubQ.SUBQ_TYPE;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class sysInstSet extends MInstSet {

    public static final fURI SYS_TID = f("/sys");
    public static final fURI ROUTER_TID = SYS_TID.extend("router");
    public static final fURI SPACE_TID = SYS_TID.extend("space");
    public static final fURI Q_TID = SPACE_TID.extend("q");

    public sysInstSet(final fURI vid) {
        super(SYS_TID, vid);
    }

    public static sysInstSet create() {
        return new sysInstSet(fURI.fnull);
    }

    public static sysInstSet create(final fURI vid) {
        return new sysInstSet(vid);
    }

    @Override
    public Set<Type> types() {
        return Set.of(
                T(ROUTER_TID),
                T(SPACE_TID),
                Console.CONSOLE_TYPE,
                fileSpace.FS_TYPE,
                fileSpace.FILE_TYPE,
                T(fileSpace.DIR_TID),
                remoteSpace.REMOTE_TYPE, DOCQ_TYPE, SUBQ_TYPE);
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                docWrap(instC(SYS_TID.extend("inst").extend("less").dom(STR_TID).rng(NOOBJ_TID.zero()), lst(isa_(T(INT_TID)).else_(jnt(10))), (lhs, inst) -> {
                    Scanner scanner = new Scanner(System.in);
                    final int pageSize = inst.arg(0).orElse(jnt(100)).intValue().intValue();
                    final AtomicInteger page = new AtomicInteger(0);
                    final AtomicInteger counter = new AtomicInteger(0);
                    Arrays.stream(lhs.strValue().split("\n")).forEach(line -> {
                        if (counter.getAndIncrement() < pageSize) {
                            LOG.none(line + "\n");
                        } else {
                            LOG.none("{{g}}<{{m}}page %s{{g}}>{{X}}\n", page.incrementAndGet());
                            scanner.nextLine();
                            LOG.none("{{^2&-X-&v1}}");
                            counter.set(0);
                        }
                    });
                    return noobj();
                }),"an str to page","noobj terminal",Map.of(jnt(0),"number of lines per page"),"an f(x)->0 terminal page through the lines of an str")
        ));
    }
}
