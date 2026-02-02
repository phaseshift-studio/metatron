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

package studio.phaseshift.metatron.isa.sys;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MInstSet;
import studio.phaseshift.metatron.isa.sys.space.file.fsSpace;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.isa.sys.type.console.Console;
import studio.phaseshift.metatron.isa.sys.type.console.Editor;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.ImageUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.else_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.sys.space.file.fsSpace.FS_TYPE;
import static studio.phaseshift.metatron.isa.sys.type.console.Console.CONSOLE_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class sysInstSet extends MInstSet {

    public static final fURI SYS_ISA_TID = MTRON_TID.extend("sys");
    public static final fURI SYS_TID = f("/sys");
    public static final fURI SYS_INST_TID = SYS_ISA_TID.extend("inst");
    public static final fURI ROUTER_TID = SYS_ISA_TID.extend("router");
    public static final fURI SYS_SPACE_TID = SYS_ISA_TID.extend("space");
    public static final fURI FILE_TID = SYS_ISA_TID.extend("file");
    public static final fURI IMAGE_TID = FILE_TID.extend("image");
    public static final fURI Q_TID = SYS_SPACE_TID.extend("q");
    public static final fURI REWRITE_INST_TID = SYS_INST_TID.extend("rewrite");


    public sysInstSet(final fURI vid) {
        super(SYS_ISA_TID, vid);
    }

    public static sysInstSet create() {
        return new sysInstSet(fURI.fnull);
    }

    public static sysInstSet create(final fURI vid) {
        return new sysInstSet(vid);
    }

    @Override
    public Set<Type> types() {
        final Set<Type> types = new HashSet<>(List.of(
                T(ROUTER_TID),
                T(SYS_SPACE_TID),
                CONSOLE_TYPE,
                SUBSCRIPTION_TYPE,
                DOCQ_TYPE,
                SUBQ_TYPE,
                FS_TYPE));
        final Type FILE_TYPE = Type.Builder.build()
                .tid(URI_TID).vid(FILE_TID)
                .constructor(instC(INST_TID.dom(ALL.maybe()).rng(FILE_TID),
                        lst(T(URI_TID)),
                        (lhs, inst) -> fsSpace.makeFile(Path.of(inst.arg(0).uriValue().toString())))).create();
        final Type IMAGE_FILE_TYPE = Type.Builder.build()
                .tid(FILE_TID)
                .vid(IMAGE_TID).create();
        types.add(FILE_TYPE);
        types.add(IMAGE_FILE_TYPE);
        return types;
    }

    @Override
    public Set<Inst> insts() {
        final LinkedHashSet<Inst> insts = new LinkedHashSet<>();
        insts.addAll(Router.RouterType.insts());
        insts.addAll(List.of(
                instC(SYS_INST_TID.extend("close").dom(ROUTER_TID).rng(NOOBJ_TID), lst(), (lhs, inst) -> Stream.of(noobj()).peek(o -> System.exit(0)).iterator().next()),
                instC(SYS_INST_TID.extend("beep").dom(A.maybe()).rng(A.maybe()), lst(isa_(T(INT_TID)).else_(jnt(10))), (lhs, inst) -> {
                    for (int i = 0; i < inst.arg(0).intValue().intValue(); i++) {
                        Toolkit.getDefaultToolkit().beep();
                        CommonUtil.sleepThread(15);
                    }
                    return lhs;
                }),
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
                }), "an str to page", "noobj terminal", Map.of(jnt(0), "number of lines per page"), "an f(x)->0 terminal page through the lines of an str"),
                /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                instC(AS_INST_TID.dom(URI_TID).rng(FILE_TID), lst(T(FILE_TID)), (lhs, inst) -> fsSpace.makeFile(Path.of(lhs.uriValue().toString()))),
                instC(AS_INST_TID.dom(BYTES_TID).rng(IMAGE_TID), lst(T(IMAGE_TID), else_(real(1.0d))),
                        (lhs, inst) -> str(ImageUtil.convertToAscii(lhs.bytesValue(), inst.arg(1).realValue())).tid(IMAGE_TID)),
                instC(AS_INST_TID.dom(URI_TID).rng(FILE_TID), lst(T(FILE_TID)), (lhs, inst) -> fsSpace.makeFile(Path.of(lhs.uriValue().toString())).vid(lhs.vid())),
                instC(AS_INST_TID.dom(FILE_TID).rng(BYTES_TID), lst(T(BYTES_TID)), (lhs, inst) -> {
                    try {
                        final File file = fsSpace.resolveFile(lhs);
                        final byte[] data = new byte[(int) file.length()];
                        try (final FileInputStream fis = new FileInputStream(file)) {
                            fis.read(data);
                        }
                        return bytes(ByteBuffer.wrap(data));
                    } catch (final Exception e) {
                        throw MTronException.of(e);
                    }
                })));
        return insts;
    }
}
