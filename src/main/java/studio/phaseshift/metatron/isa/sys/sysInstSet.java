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

import org.buildobjects.process.ProcBuilder;
import org.buildobjects.process.ProcResult;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.JREService;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * The system instruction set — the machine bridge: spawn processes, touch the environment, and do raw
 * terminal I/O. Instructions live under {@code /m/sys/inst/...}.
 *
 * <h3>future ideas</h3>
 * <p>Process instructions that would round out the bridge, each following the {@code bash} pattern — typed
 * args, an optional timeout, and {@code allow}/{@code reject}/{@code env}/{@code dir} as q-params:
 * <ul>
 *   <li>{@code exec} — non-shell exec. {@code exec(cmd=>str::T, args=>lst[str::T], timeout?=>time::T) →
 *       lst[str::T]}: argv as literal tokens, so no shell metacharacter interpretation (a {@code ;} or
 *       {@code &&} is data, not control). The corrected {@code native}.</li>
 *   <li>{@code pwd} — {@code pwd() → uri}: read the current working directory, the read counterpart to
 *       {@code bash}'s {@code dir} modulator.</li>
 *   <li>{@code which} — {@code which(cmd=>str::T) → uri}: resolve a command name to its absolute path, so a
 *       caller can inspect what {@code exec}/{@code bash} will actually run.</li>
 * </ul>
 */
@JREService(vid = "/m/sys")
public class sysInstSet extends AbstractInstSet {
    public static final fURI SYS_ISA_TID = M_ISA_TID.extend("sys");
    public static final fURI SYS_INST_TID = SYS_ISA_TID.extend("inst");
    public static final fURI SYS_BASH_INST_TID = SYS_INST_TID.extend("bash");

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

    private static final Real DEFAULT_TIMEOUT = real(30.0, MATH_SECOND_TID, null);

    public sysInstSet() {
        super(mutableMap(Map.of(uri(PATTERN), uri(SYS_ISA_TID.extend("#")))), INSTSET_TID, SYS_ISA_TID);
    }

    /*
        lst(() ->{ Space sys = new sysInstSet();
               Router.global().addSpace(sys);
               Router.writeToSpace(sys); 
               }(),() -> {
               System.getenv().entrySet().stream()
                    .map(kv -> new AbstractMap.SimpleEntry<>(SYS_VID.extend("env").extend(kv.getKey()), str(kv.getValue())))
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(fURI::name)))
                    .collect(new CommonUtil.recCollector());
                    .forEach(kv -> sysSpace.write(kv.getKey(), kv.getValue()),REC_TID,f("/sys/env"))
               }(),() -> { ThreadExecutor.instance() }())
     */

    public void setup() {
        this.jvm().putAll(Map.of(
                uri(CONST), lst(ThreadExecutor.instance()),
                uri(INST), lst(
                        docWrap(instC(SYS_BASH_INST_TID.dom(ALL.maybe()).rng(LST_TID.poly(STR_TID)), rec(
                                                uri(CMD), STR_TYPE,
                                                uri(TIMEOUT).maybe(), TIME_TYPE),
                                        (lhs, inst) -> {
                                            LOG.status(TRACE, "bash: %s", inst);
                                            //final StringBuilder errors = new StringBuilder();
                                            //final StringBuilder outputs = new StringBuilder();
                                            final String command = inst.arg(CMD, 0).strValue();
                                            final Lst allow = Optional.ofNullable(inst.tid().qValue(ALLOW, Lst.class)).orElse(lst());
                                            final Lst reject = Optional.ofNullable(inst.tid().qValue(REJECT, Lst.class)).orElse(lst());
                                            final String workingDirectory = Optional.ofNullable(inst.tid().qValue(DIR, String.class)).orElse(System.getProperty("user.dir"));
                                            if (!allow.isEmpty()) {
                                                if (allow.elements().map(Obj::strValue).noneMatch(a -> Pattern.compile(a).matcher(command).matches()))
                                                    throw MTronException.of("allowed patterns do not match command: %s %s", command, allow);
                                            }
                                            if (!reject.isEmpty()) {
                                                final Optional<String> p = reject.elements().map(Obj::strValue).filter(a -> Pattern.compile(a).matcher(command).find()).findFirst();
                                                if (p.isPresent())
                                                    throw MTronException.of("reject patterns match command: %s %s", command, p.get());
                                            }
                                            final Map<String, String> envVars = new HashMap<>();
                                            final Rec env = inst.tid().qValue(ENV, Rec.class);
                                            if (null != env)
                                                env.elements().forEach(rel -> envVars.put(rel.first().toCleanString(), rel.second().toCleanString()));
                                            final ProcResult result = new ProcBuilder("bash")
                                                    .withArg("-c")
                                                    .withArg(command)
                                                    .withWorkingDirectory(new File(workingDirectory))
                                                    .withVars(envVars)
                                                    .withTimeoutMillis(inst.arg(TIMEOUT, 1).orElse(DEFAULT_TIMEOUT).tid(MATH_MILLIS_TID).realValue().longValue())
                                                    .run();
                                            if (0 != result.getExitValue())
                                                throw MTronException.of("bash exited %d after %s: [stderr] %s [stdout] %s",
                                                        result.getExitValue(),
                                                        mathInstSet.normalizeTime(real((double) result.getExecutionTime(), MATH_MILLIS_TID, null)),
                                                        result.getErrorString(),
                                                        result.getOutputString());
                                            return Arrays.stream(result.getOutputString().split("\n")).map(MStr::str).collect(new CommonUtil.LstCollector());
                                        }), "maybe an obj",
                                "a lst[str] of results",
                                Map.of(
                                        uri(CMD), "the terminal command to evaluate (uses bash('-c',${cmd}) behind the scenes)",
                                        uri(TIMEOUT).maybe(), """
                                                              a real number denoting timeout of the process (default: %s).
                                                              """.formatted(DEFAULT_TIMEOUT)),
                                """
                                evaluate bash command. *important* the timeout argument takes a real not an int -- e.g. millis::1000.0 or second::1.0. 
                                note that this field is optional, so when in doubt, just don't fill it out
                                """,
                                """
                                bash('ls')                                           [-- return lst containing each file/dir as str::T --]
                                {"ls","whoami","df -h"}.-<[_ => _]==[_ => bash(_)]   [-- batch bash results indexed by cmd             --]
                                ["ls","whoami","df -h"].mapp(-<[_ => bash(_)]).sum() [-- same as above but with lst of cmds            --]
                                """),
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
