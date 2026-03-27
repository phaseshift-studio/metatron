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

package studio.phaseshift.metatron.isa.mach;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.space.file.fsSpace;
import studio.phaseshift.metatron.isa.mach.type.Monad;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Editor;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.ImageUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

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

import static studio.phaseshift.metatron.Tokens.MONAD;
import static studio.phaseshift.metatron.Tokens.TOOL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.DocQ.DOCQ_TYPE;
import static studio.phaseshift.metatron.furi.q.DocQ.DOC_TYPE;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_TYPE;
import static studio.phaseshift.metatron.furi.q.QCollection.SUB_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.else_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjFactory.M_FACTORY_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.space.file.fsSpace.FS_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.mach.io.space.file.fsSpace.makeFile;
import static studio.phaseshift.metatron.isa.mach.io.space.serial.serialSpace.SERIAL_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.mach.type.Machine.MACH_MACHINE_TYPE;
import static studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine.MACH_SWARM_MACHINE_TYPE;
import static studio.phaseshift.metatron.isa.mach.type.net.MServer.MSERVER_TID;
import static studio.phaseshift.metatron.isa.mach.type.net.mcp.MetatronMcpServer.MACH_SERVER_MCP_SERVER_TID;
import static studio.phaseshift.metatron.isa.mach.type.net.protocol.MServerProtocolHandler.MACH_SERVER_PROTOCOL_TID;
import static studio.phaseshift.metatron.isa.mach.type.net.protocol.McpProtocolHandler.MACH_SERVER_MCP_PROTOCOL_TID;
import static studio.phaseshift.metatron.isa.mach.type.net.protocol.NativeMetatronProtocolHandler.MACH_SERVER_NATIVE_PROTOCOL_TID;
import static studio.phaseshift.metatron.isa.mach.type.ui.console.Console.CONSOLE_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(tid = "/m/mach")
public class machInstSet extends AbstractInstSet {

    public static final fURI MACH_ISA_TID = M_ISA_TID.extend("mach");
    public static final fURI MACH_MACHINE_TID = MACH_ISA_TID.extend("machine");
    public static final fURI MACH_MONAD_TID = MACH_ISA_TID.extend("monad");
    public static final fURI MACH_INST_TID = MACH_ISA_TID.extend("inst");
    public static final fURI DROP_TID = MACH_INST_TID.extend("drop");
    public static final fURI INJECT_TID = MACH_INST_TID.extend("inject"); // inj ?
    public static final fURI RING_ZERO_TID = MACH_INST_TID.extend("ring").extend("const").extend("zero");
    public static final fURI RING_ONE_TID = MACH_INST_TID.extend("ring").extend("const").extend("one");
    public static final fURI RING_BINARY = MACH_INST_TID.extend("ring").extend("op").extend("+");
    public static final fURI WHICH_INST_TID = MACH_INST_TID.extend("which");

    public static final fURI ROUTER_TID = MACH_ISA_TID.extend("router");
    public static final fURI MACH_SPACE_TID = MACH_ISA_TID.extend("space");
    public static final fURI FILE_TID = MACH_ISA_TID.extend("file");
    public static final fURI DIR_TID = MACH_ISA_TID.extend("dir");
    public static final fURI IMAGE_TID = FILE_TID.extend("image");
    public static final fURI Q_TID = MACH_SPACE_TID.extend("q");
    public static final fURI FACTORY_TID = MACH_ISA_TID.extend("factory");
    public static final fURI REWRITE_INST_TID = MACH_INST_TID.extend("rewrite");
    public static final Rec SPACE_CONFIG = rec(uri(Tokens.PATTERN), T(URI_TID));

    public static final Type SPACE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(SPACE_TID)
            .create();
    public static final Type FACTORY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(FACTORY_TID)
            .create();
    public static final Type ROUTER_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(ROUTER_TID)
            .create();
    public static final Type FILE_TYPE = Type.Builder.build()
            .tid(URI_TID)
            .vid(FILE_TID)
            .constructor(instC(INST_TID.dom(ALL.maybe()).rng(FILE_TID),
                    lst(T(URI_TID)),
                    (lhs, inst) -> makeFile(Path.of(inst.arg(0).uriValue().basePath().toString())))).create();
    public static final Type DIR_TYPE = Type.Builder.build()
            .tid(FILE_TID)
            .vid(DIR_TID)
            .predicate((uri, x) -> uri.uriValue().isBranch() ? uri : noobj())
            .constructor(instC(INST_TID.dom(ALL.maybe()).rng(DIR_TID.maybe()),
                    lst(T(URI_TID)),
                    (lhs, inst) -> inst.arg(0).uriValue().isBranch() ? makeFile(Path.of(inst.arg(0).uriValue().basePath().toString())) : noobj())).create();
    public static final Type IMAGE_FILE_TYPE = Type.Builder.build()
            .tid(FILE_TID)
            .vid(IMAGE_TID).create();

    /// //////////////////////////////////////////////////////////////////////
    public static final Type SERVER_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MSERVER_TID)
            .create();
    public static final Type SERVER_PROTOCOL_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(f(MACH_SERVER_PROTOCOL_TID))
            .create();
    public static final Type NATIVE_SERVER_PROTOCOL_TYPE = Type.Builder.build()
            .tid(f(MACH_SERVER_PROTOCOL_TID))
            .vid(f(MACH_SERVER_NATIVE_PROTOCOL_TID))
            .create();
    public static final Type MCP_SERVER_PROTOCOL_TYPE = Type.Builder.build()
            .tid(f(MACH_SERVER_PROTOCOL_TID))
            .vid(f(MACH_SERVER_MCP_PROTOCOL_TID))
            .create();
    public static final Type MCP_SERVER_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(f(MACH_SERVER_MCP_SERVER_TID))
            .isaPredicate(rec(uri(f(TOOL).maybe()), T(INST_TID.maybeSome())))
            .create();
    public static final Type MACH_MONAD_TYPE = Type.Builder.build().tid(LST_TID).vid(MACH_MONAD_TID).create();

    public machInstSet() {
        super(MACH_ISA_TID, MACH_ISA_TID);
        // Router.global().registerPrefix(f("mach"), MACH_ISA_TID);
    }

    @Override
    public Set<Type> types() {
        return new LinkedHashSet<>(List.of(
                ROUTER_TYPE,
                SPACE_TYPE,
                CONSOLE_TYPE,
                SUB_TYPE,
                DOCQ_TYPE,
                DOC_TYPE,
                SUBQ_TYPE,
                SUB_TYPE,
                FS_SPACE_TYPE,
                SERIAL_SPACE_TYPE,
                FILE_TYPE,
                DIR_TYPE,
                IMAGE_FILE_TYPE,
                FACTORY_TYPE,
                M_FACTORY_TYPE,
                /// /////////////////////
                SERVER_TYPE,
                SERVER_PROTOCOL_TYPE,
                NATIVE_SERVER_PROTOCOL_TYPE,
                MCP_SERVER_PROTOCOL_TYPE,
                MCP_SERVER_TYPE,
                /// /////////////////////
                MACH_MONAD_TYPE,
                MACH_MACHINE_TYPE,
                MACH_SWARM_MACHINE_TYPE));
    }

    @Override
    public Set<Tuple.Triplet<Tuple.Pair<String, String>, List<fURI>, Integer>> sugars() {
        return new LinkedHashSet<>(List.of(
                Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(LIFT_INST_TID), 0)
        ));
    }

    @Override
    public Set<Inst> insts() {
        final Set<Inst> insts = new LinkedHashSet<>();
        insts.addAll(Router.RouterType.insts());
        insts.addAll(List.of(
                instC(LIFT_INST_TID.dom(ALL).rng(MACH_MONAD_TID).q(MONAD, "^"), lst(T(ALL.maybe())), (lhs, inst) -> {
                    final Monad monad = lhs.asMonad();
                    if (!inst.arg(0).isNoObj())
                        return inst.arg(0).apply(monad);
                    else
                        return monad;
                }),
                instC(REWRITE_INST_TID.dom(ALL.maybe()).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(Router.global().rewrite(inst.arg(0).uriValue(), true))),
                instC(MACH_INST_TID.extend("close").dom(ROUTER_TID).rng(NOOBJ_TID), lst(), (lhs, inst) -> Stream.of(noobj()).peek(o -> System.exit(0)).iterator().next()),
                instC(MACH_INST_TID.extend("beep").dom(A.maybe()).rng(A.maybe()), lst(isa_(T(INT_TID)).else_(jnt(10))), (lhs, inst) -> {
                    for (int i = 0; i < inst.arg(0).intValue().intValue(); i++) {
                        Toolkit.getDefaultToolkit().beep();
                        CommonUtil.sleepThread(15);
                    }
                    return lhs;
                }),
                instC(MACH_INST_TID.extend("nano").dom(ALL.maybe()).rng(ALL.maybe()), lst(), (lhs, inst) -> {
                    try {
                        final File file = Editor.createObjFile(lhs);
                        Editor.of(Console.LOCAL_INSTANCE, file);
                        return mParser.parse(Files.readString(file.toPath()).trim());
                    } catch (final IOException e) {
                        throw MTronException.of(e);
                    }
                }),
                docWrap(instC(MACH_INST_TID.extend("less").dom(STR_TID).rng(NOOBJ_TID.zero()), lst(isa_(T(INT_TID)).else_(jnt(10))), (lhs, inst) -> {
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
                instC(RSHIFT_INST_TID.dom(FILE_TID).rng(FILE_TID.maybeSome()), lst(isa_(URI_TYPE).else_(uri("#"))), (lhs, inst) -> {
                    final File file = fsSpace.resolveFile(lhs);
                    if (file.isDirectory()) {
                        if (f(file.getName()).test(inst.arg(0).orElse(uri("#")).uriValue())) { // TODO: need to recurse on name if it has path segments
                            if (null == file.listFiles()) return noobj();
                            final fsSpace space = Router.global().getSpace(lhs.uriValue());
                            return objs(Arrays.stream(Objects.requireNonNull(file.listFiles()))
                                    //.peek(ff -> LOG.info("reading file %s", f(f(ff.getName()).name())))
                                    .map(ff -> makeFile(ff.toPath()))
                                    .map(ff -> uri(space.rewrite(ff.uriValue().noQ(), true), ff.uriValue().isBranch() ? DIR_TID : FILE_TID)));
                        }
                    }
                    return noobj();
                }),
                instC(AS_INST_TID.dom(URI_TID).rng(FILE_TID), lst(T(FILE_TID)), (lhs, inst) -> makeFile(Path.of(lhs.uriValue().toString()))),
                instC(AS_INST_TID.dom(BYTES_TID).rng(IMAGE_TID), lst(T(IMAGE_TID), else_(real(1.0d))),
                        (lhs, inst) -> str(ImageUtil.convertToAscii(lhs.bytesValue(), inst.arg(1).realValue())).tid(IMAGE_TID)),
                instC(AS_INST_TID.dom(URI_TID).rng(FILE_TID), lst(T(FILE_TID)), (lhs, inst) -> makeFile(Path.of(lhs.uriValue().toString())).vid(lhs.vid())),
                instC(AS_INST_TID.dom(FILE_TID).rng(BYTES_TID), lst(T(BYTES_TID)), (lhs, inst) -> {
                    try {
                        final File file = fsSpace.resolveFile(lhs);
                        LOG.debug("translating file to bytes: %s", file);
                        final byte[] data;
                        try (final FileInputStream fis = new FileInputStream(file)) {
                            data = fis.readAllBytes();
                        } catch (final IOException e) {
                            throw MTronException.of(e);
                        }
                        return bytes(ByteBuffer.wrap(data));
                    } catch (final Exception e) {
                        throw MTronException.of(e);
                    }
                }),
                instC(RING_ZERO_TID.dom(A).rng(A), lst(), (lhs, inst) -> ((PlusMonoid.O<?>) lhs).zero()),
                instC(RING_ONE_TID.dom(A).rng(A), lst(), (lhs, inst) -> ((MultMonoid.O<?>) lhs).one()),
                // instC(RING_BINARY.dom(A).rng(ALL.dom(A).rng(A)), lst(), (lhs, inst) -> instB(mtronInstSet.INST_TID.extend(inst.tid().name()), lst(lhs.type())).resolve(lhs)),
                //instC(RING_BINARY.dom(A).rng(ALL.dom(A).rng(A)), lst(T(A)), (lhs, inst) -> instB(mtronInstSet.INST_TID.extend(inst.tid().name()), inst.args()).apply(lhs)),
                instC(WHICH_INST_TID.dom(ALL).rng(A), lst(URI_TYPE), (lhs, inst) -> {
                    if (inst.arg(0).uriValue().big().equals(SPACE_TID))
                        return null == lhs.vid() ? noobjSpace.single() : Router.global().getSpace(lhs.vid());
                    else
                        throw MTronException.of("unsupported which %s for %s", inst.arg(0), lhs);
                }),
                instC(INJECT_TID.dom(ALL).rng(ALL), lst(T(INT_TID), T(ALL)), (lhs, inst) -> {
                    if (lhs.jvm() instanceof Tuple)
                        return lhs.jvm(lhs.<Tuple>jvmAs().inject(inst.arg(0).intValue().intValue(), inst.arg(1)));
                    else if (inst.arg(0).intValue() == 0)
                        return lhs.jvm(inst.arg(1).jvm());
                    else
                        throw MTronException.of("injection larger than tuple: 1 < %d", inst.arg(0).intValue().intValue());
                })
        ));
        return insts;
    }
}
