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

package studio.phaseshift.metatron.isa.ide;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The command runner — the single Java piece behind the {@code cs_command} wrapper.  Runs a
 * shell command, applies the user's {@code to} conduit to each output line as it's produced
 * (the drstynx {@code to} convention), and assembles the standardized {@code cs_result::T}
 * rec: {@code status} (exit code → success/failure), {@code runtime} ({@code time::millis::T}),
 * {@code output} (the {@code str{*}} line-stream), and any caught exception as a {@code fail::T}
 * in {@code fails}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class CommandRunner {

    private CommandRunner() {
        // do nothing
    }

    /**
     * Run {@code command}, streaming each output line through {@code to} (if an inst), and return
     * the {@code cs_result::T} rec.  Absent {@code to} → no streaming; the rec is still complete.
     */
    public static Obj run(final String command, final Obj to) {
        final long start = System.currentTimeMillis();
        final List<Obj> lines = new ArrayList<>();
        final List<Obj> fails = new ArrayList<>();
        int exit = -1;
        try {
            final Process process = new ProcessBuilder(command.trim().split("\\s+"))
                    .redirectErrorStream(true)
                    .start();
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final Obj lineObj = str(line, STR_TID, null);
                    // the to conduit may arrive rec-wrapped ([code=>inst]) — the arg machinery
                    // applies plain inst args at resolve time, so the wrapper keeps it data
                    Obj toCode = to;
                    if (toCode != null && toCode.isRec() && toCode.asRec().has(uri("code")))
                        toCode = toCode.asRec().at(uri("code"));
                    if (toCode != null && toCode.isInst() && !toCode.isNoObj())
                        toCode.asInst().apply(lineObj);
                    lines.add(lineObj);
                }
            }
            exit = process.waitFor();
        } catch (final Exception e) {
            fails.add(fail(e));
        }
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri("status"), uri(exit == 0 && fails.isEmpty() ? "success" : "failure"));
        map.put(uri("runtime"), mathInstSet.normalizeTime(real((double) (System.currentTimeMillis() - start), MATH_MILLIS_TID, null)));
        if (!command.isBlank()) map.put(uri("command"), str(command));
        // the output is stored at a minted temp uri and referenced lazily — only >>output (the
        // !* auto_from deref) materializes the str{*} line-stream, keeping the result rec compact
        if (!lines.isEmpty()) {
            final fURI outputURI = CommonUtil.mintShortUUID(f("/sys/tmp"), true);
            Router.writeToSpace(outputURI, objs(lines));
            map.put(uri("output"), auto_from_(outputURI).tryToInst());
        }
        if (!fails.isEmpty()) map.put(uri("fails"), lst(fails));
        return rec(map, ideInstSet.IDE_RESULT_TID, null);
    }
}
