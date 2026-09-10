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
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
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
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.union_;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
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

    private static final Real DEFAULT_TIMEOUT = real(20.0, MATH_SECOND_TID, null);

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
                        docWrap(instC(SYS_INST_TID.extend("read_file").dom(A.maybe()).rng(LST_TID), rec(
                                        uri(FILE), URI_TYPE,
                                        uri(MIN).maybe(), INT_TYPE,
                                        uri(MAX).maybe(), INT_TYPE), (lhs, inst) -> {
                                    final fURI file = inst.arg(0).uriValue();
                                    final int min = inst.arg(1).orElse(jnt(-1)).intValue().intValue();
                                    final int max = inst.arg(2).orElse(jnt(-1)).intValue().intValue();
                                    final Obj fileObj = Router.readFromSpace(file);
                                    if (fileObj.isStr()) {
                                        final List<String> startLines = new ArrayList<>(Arrays.asList(fileObj.strValue().split("\n")));
                                        return min != -1 ? lst(IteratorUtil.indexedStream(startLines.subList(min, -1 == max ? startLines.size() : max).iterator()).map(pair -> lst(jnt(pair.get0() + min), str(pair.get1())))) :
                                                lst(IteratorUtil.indexedStream(startLines.iterator()).map(pair -> lst(jnt(pair.get0()), str(pair.get1()))));

                                    } else {
                                        throw MTronException.of("none str-based obj referenced: %s", file);
                                    }
                                }), "maybe an obj (optional)", "read file indexed lines",
                                Map.of(uri(FILE), "a reference to a fsspace file",
                                        uri(MIN).maybe(), "the start line to read",
                                        uri(MAX).maybe(), "the end line to read"),
                                """
                                read a file from an fsspace::T. the min and max values represent the range to read.
                                if no min, nor max is provided, then the entire file is read.
                                if only a min is provided, then the file is read from that line till the end.
                                """),
                        docWrap(instC(SYS_INST_TID.extend("edit_file").dom(A.maybe()).rng(REC_TID), rec(FILE, URI_TYPE, TEXT, STR_TYPE, MIN, INT_TYPE, uri(MAX).maybe(), INT_TYPE), (lhs, inst) -> {
                                    final fURI file = inst.arg(0).uriValue();
                                    final String text = inst.arg(1).strValue();
                                    final int min = inst.arg(2).intValue().intValue();
                                    final int max = inst.arg(3).orElse(jnt(-1)).intValue().intValue();
                                    final Obj fileObj = Router.readFromSpace(file);
                                    if (fileObj.isStr()) {
                                        final List<String> startLines = new ArrayList<>(Arrays.asList(fileObj.strValue().split("\n")));

                                        if (max != -1) {
                                            for (int i = min; i < max; i++) {
                                                startLines.set(i, "<DELETE>");
                                            }
                                        }
                                        startLines.add(min, text);
                                        final List<String> endLines = startLines.stream().filter(l -> !l.equals("<DELETE>")).toList();
                                        Router.writeToSpace(file, str(String.join("\n", endLines)));
                                        return rec(STATUS, uri(SUCCESS),
                                                OBJ, auto_from_(file).tryToInst(),
                                                "start_line_count", jnt(startLines.size()),
                                                "inserted_line_count", jnt(CommonUtil.countLines(text)),
                                                "end_line_count", jnt(endLines.size()));
                                    } else {
                                        return rec(
                                                STATUS, uri(ERROR),
                                                OBJ, auto_from_(file).tryToInst(),
                                                DESC, str("file reference did not yield a text-based obj (no changes)"));
                                    }
                                }), "maybe an obj (optional)", "a status report on the write",
                                Map.of(uri(FILE), "a reference to a fsspace file",
                                        uri(TEXT), "the text to add to the file",
                                        uri(MIN), "the location to insert (or start to overwrite)",
                                        uri(MAX).maybe(), "the location to stop overwriting"),
                                """
                                write text to a file in fsspace::T. the min and max values represent the range to write.
                                if no max is provided, then the text is inserted at the line number (shifting existing text down).
                                if both min and max are provided, then those lines are removed and the text is inserted at the min line.
                                """),
                        docWrap(instC(SYS_BASH_INST_TID.dom(ALL.maybe()).rng(LST_TID.poly(STR_TID)), rec(
                                                uri(CMD), STR_TYPE,
                                                uri(TIMEOUT).maybe(), union_(TIME_TYPE, INT_TYPE).tryToInst()),
                                        (lhs, inst) -> {
                                            //final StringBuilder errors = new StringBuilder();
                                            //final StringBuilder outputs = new StringBuilder();
                                            final String command = inst.arg(CMD, 0).strValue();
                                            final Lst allow = Optional.ofNullable(inst.tid().qValue(ALLOW, Lst.class)).orElse(lst());
                                            final Lst reject = Optional.ofNullable(inst.tid().qValue(REJECT, Lst.class)).orElse(lst());
                                            final String workingDirectory = Optional.ofNullable(inst.tid().qValue(DIR, String.class)).orElse(System.getProperty("user.dir"));
                                            if (!allow.isEmpty()) {
                                                if (allow.elements().map(Obj::strValue).noneMatch(a -> Pattern.compile(a).matcher(command).matches()))
                                                    throw MTronException.of("allowed patterns do not match command: %s {{r}}not in{{/r}} %s", command, allow);
                                            }
                                            if (!reject.isEmpty()) {
                                                final Optional<String> p = reject.elements().map(Obj::strValue).filter(a -> Pattern.compile(a).matcher(command).find()).findFirst();
                                                if (p.isPresent())
                                                    throw MTronException.of("reject patterns match command: %s {{r}}in{{/r}} %s", command, p.get());
                                            }
                                            final Map<String, String> envVars = new HashMap<>();
                                            final Rec env = inst.tid().qValue(ENV, Rec.class);
                                            if (null != env)
                                                env.elements().forEach(rel -> envVars.put(rel.first().toCleanString(), rel.second().toCleanString()));
                                            Obj timeout = inst.arg(TIMEOUT, 1).orElse(DEFAULT_TIMEOUT);
                                            if (timeout.isInt())
                                                timeout = real(timeout.intValue().doubleValue(), MATH_SECOND_TID, null);
                                            final ProcResult result = new ProcBuilder("bash")
                                                    .withArg("-c")
                                                    .withArg(command)
                                                    .withWorkingDirectory(new File(workingDirectory))
                                                    .withVars(envVars)
                                                    .withTimeoutMillis(timeout.tid(MATH_MILLIS_TID).realValue().longValue())
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
