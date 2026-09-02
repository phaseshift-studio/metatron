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
import studio.phaseshift.metatron.isa.ide.parser.ObjJavaIDESerializer;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.TIME_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.JAVA_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.JAVA_TYPE;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The agent IDE instset.  Storage is a plain {@code fsSpace} with
 * {@code addQ(lineq) addQ(subq) addQ(lockq)}; the intelligence lives here:
 * Java for the heavy lifting ({@link CommandRunner}), thin mtron insts for the
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
 * instruction that runs it through {@link CommandRunner}, applies the user's {@code to} conduit per
 * output line, and returns a {@code cs_result::T}.  The user names the produced inst anything
 * and curates their own palette (e.g. {@code clean -> cs_command(command=>'mvn clean')}).</p>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/ide")
public class ideInstSet extends AbstractInstSet {

    public static final fURI IDE_ISA_TID = M_ISA_TID.extend("ide");
    public static final fURI IDE_RESULT_TID = IDE_ISA_TID.extend("result");
    public static final fURI IDE_PROJECT_TID = IDE_ISA_TID.extend("project");
    public static final fURI IDE_INST_TID = IDE_ISA_TID.extend("inst");
    public static final fURI IDE_COMMAND_TID = IDE_INST_TID.extend("command");

    // LANGUAGES

    public static final fURI IDE_JAVA_TID = IDE_ISA_TID.extend("java");
    private static final fURI OBJ_SERIALIZER_TID = IDE_ISA_TID.extend("serializer");
    // the coarse-schema (cs) serializer family — /m/web/serializer/cs/{lang}
    public static final fURI OBJ_IDE_JAVA_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_ide_java");
    public static Type OBJ_IDE_JAVA_SERIALIZER_TYPE;

    // ── Types ────────────────────────────────────────────────────────

    public static final Type IDE_RESULT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(IDE_RESULT_TID)
            .isaPredicate(rec(
                    uri(STATUS), union_(lst(uri(SUCCESS), uri(ERROR), uri(HALTED))),
                    uri(RUNTIME), TIME_TYPE,
                    uri(COMMAND).maybe(), STR_TYPE,
                    uri(PROJECT).maybe(), T(IDE_PROJECT_TID),
                    uri(RESULT).maybe(), T(STR_TID.maybeSome()), // str{*} — an auto_from !* ref type-matches true
                    uri(ERROR).maybe(), lst(T(FAIL_TID.maybe())).maybe()))
            .create();

    public static final Type IDE_PROJECT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(IDE_PROJECT_TID)
            .isaPredicate(rec(
                    uri(ROOT).asUri(), URI_TYPE,
                    uri(NAME).maybe().asUri(), STR_TYPE,
                    uri(DESC).maybe(), STR_TYPE,
                    uri(COMMAND).maybe(), rec(URI_TYPE, IDE_RESULT_TYPE),
                    uri(CODE).maybe().asUri(), T(ALL)))// a !* ref to the source tree
            .create();

    public static final Type IDE_JAVA_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(IDE_JAVA_TID)
            // top-level coarse-schema verification — the cs_java rec must expose classes (lst);
            // package/imports/preamble/postscript are optional addressing/write views
            .isaPredicate(rec(
                    uri("classes").asUri(), LST_TYPE,
                    uri("package").maybe().asUri(), STR_TYPE,
                    uri("imports").maybe().asUri(), LST_TYPE,
                    uri("preamble").maybe().asUri(), STR_TYPE,
                    uri("postscript").maybe().asUri(), STR_TYPE))
            .create();

    public ideInstSet() {
        super(mutableMap(uri(PATTERN), uri(IDE_ISA_TID.extend(ALL))), INSTSET_TID, IDE_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(TYPE), lst(
                        OBJ_IDE_JAVA_SERIALIZER_TYPE = Type.Builder.build()
                                .tid(OBJ_SERIALIZER_TID)
                                .vid(OBJ_IDE_JAVA_SERIALIZER_TID)
                                .constructor(arg -> ObjJavaIDESerializer.single())
                                .create(),
                        docWrap(IDE_JAVA_TYPE, "a coarse rec encoding of a java source file optimized for semantic editing"),
                        docWrap(IDE_RESULT_TYPE,
                                "the standardized build/test/status outcome — rec::T with a union status verdict",
                                "x>>status",
                                "x>>output.take(10)",
                                "x>>runtime.normalize()"),
                        docWrap(IDE_PROJECT_TYPE,
                                "the project descriptor (the pom.xml of a metatron ide) — a project.mtron file at the project root",
                                "cs_project::[name=>metatron,root=><fs:/foo>,code=>!*<fs:/foo/src>]")),
                uri(INST), lst(
                        docWrap(instC(AS_INST_TID.dom(URI_TID).rng(IDE_PROJECT_TID), lst(IDE_PROJECT_TYPE), (lhs, inst) -> {
                                    final Rec project = inst.arg(0).isType() ? rec() : inst.arg(0).asRec();
                                    if (!project.has(CODE)) {
                                        project.at(CODE, lst(start_(lhs).repeat_(rshift_(), BOOL_FALSE, BOOL_TRUE).apply()
                                                .stream()
                                                .filter(e -> e.uriValue().toString().contains(".java"))
                                                .map(e -> Tuple.Pair.with(e, Router.readFromSpace(e.uriValue())))
                                                .map(e -> Tuple.Pair.with(e.get0(), start_(e.get1()).as_(JAVA_TYPE).apply()))
                                                .map(e -> (Obj) start_(e.get1()).as_(IDE_JAVA_TYPE).apply().asRec().at(uri("location"), e.get0(), MUTABLE))
                                                .toList()), MUTABLE);
                                        //   .map(e -> (Obj) auto_(from_(e).as_(JAVA_TYPE).as_(IDE_JAVA_TYPE).update_(rec(LOCATION, e))).tryToInst())
                                    }
                                    if (!project.has(NAME))
                                        project.at(NAME, str(lhs.uriValue().basePath().name()), MUTABLE);
                                    if (!project.has(ROOT))
                                        project.at(ROOT, lhs.vid(null), MUTABLE);
                                    return project.selfTID(IDE_PROJECT_TID);
                                }),
                                "a project source root",
                                "a project obj",
                                Map.of(IDE_PROJECT_TYPE, "the project type"),
                                "generate an project obj used to orient monads through project source"),
                        // the as-view, not a refinement - the project keeps its mutable-workspace
                        // lifecycle, and the LLM-facing contract keeps its stable-skill shape;
                        // the bridge is a projection at the point of use.
                        docWrap(instC(AS_INST_TID.dom(IDE_PROJECT_TID).rng(LLM_SKILL_TID), lst(ALL_TYPE), (lhs, inst) -> {
                                    final Rec project = lhs.asRec();
                                    final Rec skill = inst.arg(0).isType() ? rec() : inst.arg(0).asRec();
                                    try {
                                        if (project.has(NAME))
                                            skill.at(NAME, uri(project.at(NAME).strValue()), MUTABLE);
                                        if (project.has(DESC))
                                            skill.at(DESC, project.at(DESC), MUTABLE);
                                        if (project.has(CONTENT))
                                            skill.at(CONTENT, project.at(CONTENT), MUTABLE);
                                        if (project.has(TOOL))
                                            skill.at(TOOL, project.at(TOOL), MUTABLE);
                                        // the project files, viewed as the skill resources
                                        // atDirect for the raw list (a toggle at() would auto-resolve the !* refs into contents)
                                        if (project.has(CODE))
                                            skill.at(RESOURCE, lst(project.atDirect(uri(CODE)).asLst().jvm().stream()
                                                    .map(e -> {
                                                        final Obj text;
                                                        final fURI fileUri;
                                                        // a raw element: keep or mint its !* reference as the lazy text
                                                        if (e.isUri()) {
                                                            fileUri = e.uriValue();
                                                            text = (Obj) auto_from_(e.uriValue());
                                                        } else {
                                                            fileUri = e.asCode().insts().getFirst().arg(0).uriValue();
                                                            text = e;
                                                        }
                                                        return (Obj) rec(uri(URI), uri(fileUri), uri(TEXT), text);
                                                    })
                                                    .toList()), MUTABLE);
                                        return skill;
                                    } catch (final Exception e) {
                                        throw MTronException.of(e);
                                    }
                                }),
                                "a project",
                                "the skill view of the project",
                                Map.of(ALL_TYPE, "the skill::T target of the view"),
                                "as engineering becomes agent-dependent, the workspace manifest is no longer \"how to build\" but \"how to work here\" — and \"how to work here\" is the skill shape; what stays distinct is the lifecycle, which is precisely why the bridge is an as-view and not a refinement.",
                                "*scratch.as(skill::T)   [-- the project, viewed as a skill --]"),
                        instC(AS_INST_TID.dom(JAVA_TID).rng(IDE_JAVA_TID), lst(IDE_JAVA_TYPE), (lhs, inst) -> ObjJavaIDESerializer.parse(lhs.strValue())),
                        // the write direction — serialize the coarse rec back to java source.
                        // writeMember composes header + body + footer, so surgical body edits
                        // round-trip without the derived text field going stale.
                        instC(AS_INST_TID.dom(IDE_JAVA_TID).rng(JAVA_TID), lst(JAVA_TYPE), (lhs, inst) -> str(ObjJavaIDESerializer.single().write(lhs.asRec()))),
                        //instC(AS_INST_TID.dom(REC_TID).rng(IDE_JAVA_TID), lst(IDE_JAVA_TYPE), (lhs, inst) -> lhs.tid(IDE_JAVA_TID)),
                        docWrap(instC(IDE_INST_TID.extend("find").dom(URI_TID).rng(URI_TID.maybeSome()), lst(URI_TYPE, T(STR_TID.maybe())),
                                        // *scratch/root.repeat(code=>>>,until=>false,emit=>has(<#>)) 
                                        (lhs, inst) -> {
                                            return start_(lhs).rshift_(uri(CODE)).rshift_().
                                                    filter_(from_(id_()).rshift_(uri("location")).has_(inst.arg(0))).
                                                    repeat_(rec(CODE, rshift_(), EMIT, BOOL_TRUE)).
                                                    filter_(from_(id_()).rshift_(uri(NAME)).has_(inst.arg(1).orElse(str(".*")))).tryToInst().apply();
                                            /*return from_(uri(lhs.uriValue().extend(f(CODE).extend("/")))).
                                                    filter_(rshift_().rshift_(uri("location")).has_(inst.arg(0))).
                                                    split_(lst(lshift_(), rshift_(uri("location")))).
                                                    filter_(rshift_(jnt(0)).repeat_(rec("code", rshift_(), "emit", BOOL_TRUE)).filter_(from_(id_()).rshift_(uri("name")).has_(inst.arg(0))));*/
                                            //from_(lhs.asRec().at(ROOT)).repeat_(rshift_(), BOOL_FALSE, has_(inst.arg(0))).tryToInst().apply()
                                        }),
                                "a project",
                                "the location of found resources",
                                Map.of(URI_TYPE, "a uri fragment to match"),
                                "finds project resources that contain provided uri fragment",
                                "*m_proj.find('Rec.java')"),
                        docWrap(instC(IDE_INST_TID.extend("search").dom(URI_TID).rng(URI_TID.maybeSome()), lst(STR_TYPE),
                                        // *scratch/root.repeat(code=>>>,until=>false,emit=>has(<#>)) 
                                        //(lhs, inst) -> from_(lhs.asRec().at(ROOT)).repeat_(rshift_(), BOOL_FALSE, from_(id_()).isa_(IDE_JAVA_TYPE).has_(inst.arg(0))).tryToInst().apply()),
                                        (lhs, inst) -> start_(lhs).repeat_(rec("code", rshift_(), "emit", BOOL_TRUE)).filter_(from_(id_()).rshift_(uri("name")).has_(inst.arg(0)))
                                ),
                                "a project",
                                "the location of found resources",
                                Map.of(STR_TYPE, "a regex string to match in resource contents"),
                                "searches project resources whose contents match provided regex",
                                "*m_proj.search('rec()')"),
                        docWrap(cs_command(),
                                "noobj — cs_command is a factory (the lhs is unused)",
                                "an enriched instruction that runs the command and returns ide:result::T",
                                Map.of(uri("command"), "the shell command to wrap"),
                                "wrap a command into an enriched instruction that runs it and returns ide:result::T",
                                "ide:command(command=>'mvn compile')                   [-- an enriched build instruction --]",
                                "ide:command([command=>'mvn -q test'])                 [-- the command as a rec          --]",
                                "my_build -> cs_command(command=>'mvn clean install')  [-- name it anything, curate a palette --]"))));
        super.setup();
        docWrap(this, "the agent ide — project::T definition, build result::T and the links between them.",
                "ide:command(command=>'mvn compile')");
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
        return instC(IDE_COMMAND_TID.dom(ALL.maybe()).rng(M_ISA_INST_TID), rec(uri(COMMAND), STR_TYPE),
                (lhs, inst) -> {
                    final Obj arg = inst.arg(0).isNoObj() ? inst.args() : inst.arg(0);
                    final String command = arg.isRec() ? arg.asRec().at(uri(COMMAND)).strValue() : arg.strValue();
                    return enrich(command);
                });
    }

    /**
     * The enriched instruction: a Java inst closing over the command that, on apply, runs it
     * through {@link CommandRunner} — accepting the call-time {@code to} conduit and returning
     * {@code cs_result::T}.
     */
    private static Inst enrich(final String command) {
        return instC(IDE_COMMAND_TID.extend("runner").dom(ALL.maybe()).rng(IDE_RESULT_TID),
                rec(uri(TO), TO_CODE_TYPE),
                (lhs, inst) -> CommandRunner.run(command, inst.args().at(uri(TO))));
    }
}
