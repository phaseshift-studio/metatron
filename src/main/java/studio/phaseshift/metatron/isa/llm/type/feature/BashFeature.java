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

package studio.phaseshift.metatron.isa.llm.type.feature;

import org.buildobjects.process.ProcBuilder;
import org.buildobjects.process.ProcResult;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_BASH_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BashFeature extends AbstractFeature {

    private static final Real DEFAULT_TIMEOUT = real(30.0, MATH_SECOND_TID, null);

    public BashFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_TOOL_FEATURE_TID);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final ToolFeature toolFeature = agent.feature(LLM_TOOL_FEATURE_TID).asOrThrow("%s requires %s", LLM_BASH_FEATURE_TID, LLM_TOOL_FEATURE_TID);
        final Inst bashTool = instC(LLM_BASH_FEATURE_TID.extend("bash").dom(ALL.maybe()).rng(LST_TID.poly(STR_TID)), rec(
                        uri(CMD), STR_TYPE,
                        uri(TIMEOUT).maybe(), TIME_TYPE),
                (lhs, inst) -> {
                    //final StringBuilder errors = new StringBuilder();
                    //final StringBuilder outputs = new StringBuilder();
                    final String command = inst.arg(CMD, 0).strValue();
                    final List<String> allow = this.at(ALLOW).orElse(lst()).elements().map(Obj::strValue).toList();
                    final List<String> reject = this.at(REJECT).orElse(lst()).elements().map(Obj::strValue).toList();
                    final String workingDirectory = this.at(DIR).orElse(uri(System.getProperty("user.dir"))).toCleanString();
                    if (!allow.isEmpty()) {
                        if (allow.stream().noneMatch(a -> Pattern.compile(a).matcher(command).matches()))
                            throw MTronException.of("allowed patterns do not match command: %s %s", command, allow);
                    }
                    if (!reject.isEmpty()) {
                        final Optional<String> p = reject.stream().filter(a -> Pattern.compile(a).matcher(command).find()).findFirst();
                        if (p.isPresent())
                            throw MTronException.of("reject patterns match command: %s %s", command, p.get());
                    }
                    final ProcResult result = new ProcBuilder("bash")
                            .withArg("-c")
                            .withArg(command)
                            .withWorkingDirectory(new File(workingDirectory))
                            .withTimeoutMillis(inst.arg(TIMEOUT, 1).orElse(this.at(TIMEOUT)).orElse(DEFAULT_TIMEOUT).tid(MATH_MILLIS_TID).realValue().longValue())
                            //.withErrorConsumer(error -> errors.append(new String(error.readAllBytes())))
                            //.withOutputConsumer(output ->   outputs.append(new String(output.readAllBytes())))
                            .run();
                    if (0 != result.getExitValue())
                        throw MTronException.of("bash exited %d after %s: [stderr] %s [stdout] %s",
                                result.getExitValue(),
                                mathInstSet.normalizeTime(real((double) result.getExecutionTime(), MATH_MILLIS_TID, null)),
                                result.getErrorString(),
                                result.getOutputString());
                    return Arrays.stream(result.getOutputString().split("\n")).map(MStr::str).collect(new CommonUtil.LstCollector());
                });
        toolFeature.addTool(mTool.tool(QCollection.docWrapDocs(bashTool,
                "maybe an obj",
                "a lst[str] of results",
                Map.of(
                        uri(CMD), "the terminal command to evaluate",
                        uri(TIMEOUT).maybe(), "timeout of the process (default: %s)".formatted(this.at(TIMEOUT).orElse(DEFAULT_TIMEOUT))),
                "evaluate bash command")));
        return noobj();
    }
}
