/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang.sys;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.sys.console.Console;
import studio.phaseshift.metatron.lang.sys.console.Editor;
import studio.phaseshift.metatron.util.Common;
import studio.phaseshift.metatron.util.MTronException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.q.DocQ.DOCQ_TYPE;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.furi.q.PubSubQ.SUBQ_TYPE;
import static studio.phaseshift.metatron.furi.q.PubSubQ.SUBSCRIPTION_TYPE;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class sysInstSet extends MInstSet {

    public static final fURI SYS_TID = f("/sys");
    public static final fURI SYS_TYPE_TID = SYS_TID.extend("type");
    public static final fURI SYS_OBJ_TID = SYS_TID.extend("obj");
    public static final fURI SYS_INST_TID = SYS_TID.extend("inst");
    public static final fURI ROUTER_TID = SYS_TYPE_TID.extend("router");
    public static final fURI SPACE_TID = SYS_TYPE_TID.extend("space");

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
        return new HashSet<Type>(Set.of(
                T(ROUTER_TID),
                T(SPACE_TID),
                Console.CONSOLE_TYPE,
                SUBSCRIPTION_TYPE,
                DOCQ_TYPE,
                SUBQ_TYPE));
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(SYS_INST_TID.extend("close").dom(ROUTER_TID).rng(NOOBJ_TID), lst(), (lhs, inst) -> Stream.of(noobj()).peek(o -> System.exit(0)).iterator().next()),
                instC(SYS_INST_TID.extend("beep").dom(A.maybe()).rng(A.maybe()), lst(isa_(T(INT_TID)).else_(jnt(10))), (lhs, inst) -> {
                            for (int i = 0; i < inst.arg(0).intValue().intValue(); i++) {
                                Toolkit.getDefaultToolkit().beep();
                                Common.sleepThread(15);
                            }
                            return lhs;
                        }
                ),
                instC(SYS_INST_TID.extend("nano").dom(ALL.maybe()).rng(ALL.maybe()), lst(), (lhs, inst) -> {
                    try {
                        final File file = Editor.createObjFile(lhs);
                        Editor.of(Console.LOCAL_INSTANCE, file);
                        return mParser.parse(Files.readString(file.toPath()).trim());
                    } catch (final IOException e) {
                        throw MTronException.of(e);
                    }
                }),
                docWrap(instC(SYS_INST_TID.extend("less").dom(STR_TID).rng(NOOBJ_TID.zero()), lst(isa_(T(INT_TID)).else_(jnt(10))), (lhs, inst) -> {
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
                }), "an str to page", "noobj terminal", Map.of(jnt(0), "number of lines per page"), "an f(x)->0 terminal page through the lines of an str")
        ));
    }
}
