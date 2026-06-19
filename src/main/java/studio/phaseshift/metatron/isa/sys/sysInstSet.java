/*
 * metatron: a distributed virtual machine and language
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

import org.zeroturnaround.exec.ProcessExecutor;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.sys.type_.ThreadExecutor;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.block_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace.makeFile;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid="/m/sys")
public class sysInstSet extends AbstractInstSet {
    public static final fURI SYS_ISA_TID = M_ISA_TID.extend("sys");
    public static final fURI SYS_INST_TID = SYS_ISA_TID.extend("inst");

    /*public static final Type FILE_TYPE = Type.Builder.build()
            .tid(URI_TID)
            .vid(FILE_TID)
            .constructor(instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(FILE_TID),
                    lst(T(URI_TID)),
                    (lhs, inst) -> makeFile(Path.of(inst.arg(0).uriValue().basePath().toString())))).create();
    public static final Type DIR_TYPE = Type.Builder.build()
            .tid(URI_TID)
            .vid(DIR_TID)
            //.predicate((uri, x) -> fsSpace.resolveFile(uri.as()).isDirectory() ? uri : noobj())
            .constructor(instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(DIR_TID.maybe()),
                    lst(T(URI_TID)),
                    (lhs, inst) -> inst.arg(0).uriValue().isBranch() ? makeFile(Path.of(inst.arg(0).uriValue().basePath().toString())) : noobj())).create();
    public static final Type IMAGE_FILE_TYPE = Type.Builder.build()
            .tid(FILE_TID)
            .vid(IMAGE_TID).create();*/

    public sysInstSet() {
        super(mutableMap(Map.of(uri(PATTERN), uri(SYS_ISA_TID.extend("#")))), INSTSET_TID, SYS_ISA_TID);
    }

    public void setup() {
        this.jvm().putAll(Map.of(
                uri(CONST), lst(BootLoader.getExecutor()),
                uri(INST), lst(
                        instC(SYS_INST_TID.extend("native").dom(A.maybe()).rng(B.maybeSome()), lst(STR_TYPE), (lhs, inst) -> {
                            final Str script = inst.arg(0).asStr();
                            return MTronException.wrap(() -> ObjmtronSerializer.parseMulti(new String(new ProcessExecutor(script.strValue().split(" ")).execute().getOutput().getBytes())));
                        }),
                        docWrap(instC(SYS_INST_TID.extend("sleep").dom(A.maybe()).rng(A.maybe()), lst(TIME_TYPE), (lhs, inst) -> {
                            CommonUtil.sleepThread(inst.arg(0).as(MILLIS_TYPE).realValue().intValue());
                            return lhs;
                        }), "an obj", "the lhs obj", Map.of(jnt(0), "the amount of time to pause the current thread"), "pauses the current thread for arg amount of time"),
                        docWrap(instC(SYS_INST_TID.extend("stdout").dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> {
                            final Object arg = inst.arg(0).jvm();
                            if (arg != null)
                                System.out.println(arg);
                            return lhs;
                        }), "maybe an obj", "maybe an obj", Map.of(), "prints arg jvm object to the terminal and emits lhs obj as rhs obj"),
                        docWrap(instC(SYS_INST_TID.extend("stdin").dom(ALL.maybe()).rng(STR_TID), lst(), (lhs, inst) -> {
                            final Scanner scanner = new Scanner(System.in);
                            final String input = scanner.nextLine();
                            return str(input);
                        }), "maybe an obj", "a single line of input", Map.of(), "read a line of input from the running terminal"))));
        super.setup();
    }
}
