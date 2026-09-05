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

package studio.phaseshift.metatron.isa.ide.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.ide.parser.ObjJavaIDESerializer;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.ide.ideInstSet.IDE_PROJECT_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_at_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Project extends MRec {

    public Project(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static Project project(final Rec projectRec) {
        if (projectRec instanceof Project)
            return (Project) projectRec;
        return new Project(projectRec.jvm(), projectRec.tid(), projectRec.vid());
    }

    public static Project of(final Uri root, final Type projectType) {
        final Project project = new Project(mutableMap(
                uri(ROOT), root.vid(null)),
                REC_TID, root.vid());
        project.refreshSrc(root.uriValue());
        ////////////////////////////////////////////////////////////////
        project.at(CODE, lst(), MUTABLE);
        project.at("idx", rec(), MUTABLE);

        ObjmtronSerializer.parse("""
                                 %s/code/#?subq -> sub::[code=> >>0.as(rec::T)>>path==[_,_,_,_,_,_].to(temp).
                                                         as?uri<=lst(uri::T).to(x).*(_).>>=[location=>none].as(web:java::T).
                                                         to(*(*x.>>location).side(-<[location=>_,status=>saved,time=>!math:datetime_now()].print("saved ", _, "\\n"))).
                                                         map(%s/src.mult(*temp.reverse().>-.take(1)))];
                                 """.formatted(root.vid(), root.vid())).apply();
        return project(project.selfTID(IDE_PROJECT_TID).asRec());
    }

    public void addSubscription() {
        
    }

    public void addCommand() {

    }

    public void addIdx() {

    }

    public void refreshSrc(final fURI branch) {
        try (final CommonUtil.Spinner spinner = CommonUtil.spinner("loading project", true);
             final Stream<Path> walk = Files.find(
                     Path.of(branch.scheme(null).toString()),
                     100,
                     (a, b) -> (b.isDirectory() && !a.toFile().isHidden()))) {
            final String scheme = branch.scheme();
            this.at(SRC, walk
                    .filter(d -> d.toFile().isDirectory())
                    .flatMap(d -> Arrays.stream(Objects.requireNonNull(d.toFile().listFiles(f -> f.getName().endsWith(".java")))))
                    .filter(f -> f.toPath().startsWith("src"))
                    .map(f -> f(f.getPath()))
                    //.peek(f -> LOG.info("{{-X-&|0&y}}processing {{b}}%s{{^1}}", f))
                    .map(e -> rel(uri(e.name().replace(".java", "")), instLambda((lhs2, inst2) -> {
                        final Obj javaSource = Router.readFromSpace(e.scheme(scheme));
                        final Rec ideJava = ObjJavaIDESerializer.parse(javaSource.strValue()).asRec().at(uri("location"), uri(e.scheme(scheme)), MUTABLE);
                        final Lst codeLst = Router.readFromSpace(this.vid().extend(CODE)).orElse(lst());
                        final int c = (int) codeLst.count();
                        final fURI codeID = this.vid().extend(CODE).extend(c);
                        spinner.setMessage("\rloading project: %s", e.name());
                        Router.writeToSpace(this.vid().extend(CODE), codeLst.add(ideJava, MUTABLE));
                        //////////////////////////////////////////////////////////////////////////
                        final Rec idx = Router.readFromSpace(this.vid().extend("idx")).orElse(rec());
                        for (int cc = 0; cc < 1000; cc++) {
                            final fURI classSegment = f("classes").extend("+").extend(cc);
                            final Obj classStream = ideJava.at(classSegment);
                            if (classStream.isNoObj())
                                break;
                            final int finalCC = cc;
                            classStream.stream()
                                    //.peek(o -> LOG.info("H1: %s", o))
                                    .map(Obj::asRec)
                                    .forEach(r -> {
                                        final String className = r.at(NAME).strValue();
                                        Rec members = rec();
                                        for (int mc = 0; mc < 1000; mc++) {
                                            final Obj memberStream = r.at("members/" + mc + "/+");
                                            if (memberStream.isNoObj())
                                                break;
                                            final fURI memberSegment = classSegment.retract(2).extend(className).extend(finalCC).extend("members").extend(mc);
                                            memberStream.stream().map(Obj::asRec).forEach(m -> {
                                                final fURI kind = m.at(KIND).uriValue();
                                                final Rec kindRec = members.at(kind).orElse(rec());
                                                final Obj membersObjs = kindRec.at(m.at(NAME).strValue());
                                                members.at(kind, kindRec.at(m.at(NAME).strValue(),
                                                        membersObjs.append(auto_at_(codeID.extend(memberSegment).extend(m.at(NAME).strValue()))),
                                                        MUTABLE), MUTABLE);
                                            });
                                        }
                                        idx.at(f(r.at(NAME).strValue()), members, MUTABLE);
                                    });
                        }
                        return Router.writeToSpace(this.vid().extend("idx"), idx);
                    }))).collect(new CommonUtil.RecCollector()), MUTABLE);
                                           /* project.at(CODE, lst(start_(lhs).repeat_(rshift_(), BOOL_FALSE, BOOL_TRUE).apply()
                                                    .stream()
                                                    .filter(e -> e.uriValue().toString().contains(".java"))
                                                    .map(e -> Tuple.Pair.with(e, Router.readFromSpace(e.uriValue())))
                                                    .map(e -> Tuple.Pair.with(e.get0(), start_(e.get1()).as_(JAVA_TYPE).apply()))
                                                    .map(e -> (Obj) start_(e.get1()).as_(IDE_JAVA_TYPE).apply().asRec().at(uri("location"), e.get0(), MUTABLE))
                                                    .toList()), MUTABLE);*/
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        //return this;
    }
}
