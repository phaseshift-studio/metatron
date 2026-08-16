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
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.TIME_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.union_;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The agent IDE instset.  Storage is a plain {@code fsSpace} with
 * {@code addQ(lineq) addQ(subq) addQ(lockq)}; the intelligence lives here:
 * Java for the heavy lifting ({@link csRunner}), thin mtron insts for the
 * agent-facing surface.
 *
 * <p>Two types — the standard structure humans and agents work with:</p>
 * <ul>
 *   <li>{@code cs_result::T} — the standardized build/test/status outcome: a rec with a union
 *       {@code status} verdict, {@code runtime} ({@code time::T}), and the {@code output}
 *       {@code str{*}} line-stream.</li>
 *   <li>{@code cs_project::T} — the project descriptor (the "pom.xml" of a metatron ide): the
 *       project {@code root}, plus command palettes ({@code build}, {@code test}, …) mapping
 *       command-name uris to command insts.</li>
 * </ul>
 *
 * <p>One wrapper instruction — {@code cs_command}: given a command, produces the enriched
 * instruction that runs it through {@link csRunner}, applies the user's {@code to} conduit per
 * output line, and returns a {@code cs_result::T}.  The user names the produced inst anything
 * and curates their own palette (e.g. {@code clean -> cs_command(command=>'mvn clean')}).</p>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/ide")
public class ideInstSet extends AbstractInstSet {

    public static final fURI IDE_ISA_TID = M_ISA_TID.extend("ide");
    public static final fURI IDE_TYPE_TID = IDE_ISA_TID.extend("type");
    public static final fURI CS_RESULT_TID = IDE_TYPE_TID.extend("cs_result");
    public static final fURI CS_PROJECT_TID = IDE_TYPE_TID.extend("cs_project");
    public static final fURI IDE_INST_TID = IDE_ISA_TID.extend("inst");
    public static final fURI CS_COMMAND_TID = IDE_INST_TID.extend("cs_command");

    // ── Types ────────────────────────────────────────────────────────

    public static final Type CS_RESULT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(CS_RESULT_TID)
            .isaPredicate(rec(
                    uri("status").asUri(), union_(lst(uri("success"), uri("failure"), uri("skipped"))),
                    uri("runtime").asUri(), TIME_TYPE,
                    uri("command").maybe().asUri(), STR_TYPE,
                    uri("root").maybe().asUri(), URI_TYPE,
                    uri("output").maybe().asUri(), T(ALL_STAR), // str{*} — an auto_from !* ref type-matches true
                    uri("fails").maybe().asUri(), LST_TYPE))
            .create();

    public static final Type CS_PROJECT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(CS_PROJECT_TID)
            .isaPredicate(rec(
                    uri("root").asUri(), URI_TYPE,
                    uri("build").maybe().asUri(), rec(URI_TYPE, INST_TYPE),
                    uri("test").maybe().asUri(), rec(URI_TYPE, INST_TYPE)))
            .create();

    public ideInstSet() {
        super(mutableMap(uri(PATTERN), uri(IDE_ISA_TID.extend(ALL))), INSTSET_TID, IDE_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(TYPE), lst(
                        docWrap(CS_RESULT_TYPE,
                                "the standardized build/test/status outcome — rec::T with a union status verdict",
                                "x>>status",
                                "x>>output.limit(10)",
                                "x>>runtime.normalize()"),
                        docWrap(CS_PROJECT_TYPE,
                                "the project descriptor — command palettes + root (the pom.xml of a metatron ide)",
                                "cs_project::[root=><fs:/foo>,build=>[compile=><inst>]]")),
                uri(INST), lst(
                        docWrap(cs_command(),
                                "noobj — cs_command is a factory (the lhs is unused)",
                                "an enriched instruction that runs the command and returns cs_result::T",
                                Map.of(uri("command"), "the shell command to wrap"),
                                "wrap a command into an enriched instruction that runs it and returns cs_result::T",
                                "cs_command(command=>'mvn compile')         [-- an enriched build instruction --]",
                                "cs_command([command=>'mvn -q test'])       [-- the command as a rec          --]",
                                "my_build -> cs_command(command=>'mvn clean install')   [-- name it anything, curate a palette --]"))));
        super.setup();
        docWrap(this, "the agent IDE — cs_project::T descriptors, cs_result::T outcomes, cs_command wrapped instructions",
                "cs_command(command=>'mvn compile')");
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The {@code to} argument type — the user's output conduit (the drstynx convention): a code
     * applied to each output line as it's produced.  Absent → no streaming; the {@code cs_result}
     * rec is still returned.
     */
    private static final Type TO_CODE_TYPE = T(ALL.dom(STR_TID));

    /**
     * The wrapper: {@code cs_command(command=>str::T)} → the enriched command inst.
     */
    private static Inst cs_command() {
        return instC(CS_COMMAND_TID.dom(ALL.maybe()).rng(M_ISA_INST_TID), rec(uri("command"), STR_TYPE),
                (lhs, inst) -> {
                    final Obj arg = inst.arg(0).isNoObj() ? inst.args() : inst.arg(0);
                    final String command = arg.isRec() ? arg.asRec().at(uri("command")).strValue() : arg.strValue();
                    return enrich(command);
                });
    }

    /**
     * The enriched instruction: a Java inst closing over the command that, on apply, runs it
     * through {@link csRunner} — accepting the call-time {@code to} conduit and returning
     * {@code cs_result::T}.
     */
    private static Inst enrich(final String command) {
        return instC(CS_COMMAND_TID.extend("runner").dom(ALL.maybe()).rng(CS_RESULT_TID),
                rec(uri("to"), TO_CODE_TYPE),
                (lhs, inst) -> csRunner.run(command, inst.args().at(uri("to"))));
    }
}
