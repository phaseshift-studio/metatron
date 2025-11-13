/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.Space.PATTERN;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Common.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DocQ extends BaseQ {

    protected static final String DOC = "doc";
    public static final fURI DOC_TID = Q_TID.extend(DOC);
    protected final GraphittyLogger LOG = Graphitty.log(this);
    // <source,pattern,callback>
    public final Map<fURI, Obj> docSpace;


    public DocQ() {
        super(mutableMap(uri(PATTERN), uri(DOC)), f(DOC), DOC_TID);
        this.docSpace = new LinkedHashMap<>();
        this.onRead = new DocQ.OnRead();
        this.onWrite = new DocQ.OnWrite();
        super.put(ON_READ, this.onRead);
        super.put(ON_WRITE, this.onRead);
    }

    public static class Instiffy {
        private final StringBuilder sb = new StringBuilder();


        public Instiffy header(final int level, final String text) {
            this.sb.append(" ".repeat(level)).append("{{_}}").append(text).append("{{X}}");
            return this;
        }

        public Instiffy item(final int level, final int num, final String text) {
            this.sb.append(" ".repeat(level)).append(" ").append("{{g}}").append(num).append(". {{b}}").append(text).append("{{X}}");
            return this;
        }

        public Instiffy item(final int level, final String element, final String text) {
            this.sb.append(" ".repeat(level)).append(" ").append("{{g}}").append(element).append(": {{b}}").append(text).append("{{X}}");
            return this;
        }

        public Instiffy item(final int level, final Obj index, final String text) {
            return index.isInt() ?
                    this.item(level, index.intValue().intValue(), text) :
                    this.item(level, index.toString(), text);
        }

        public Instiffy text(final String text) {
            this.sb.append(text);
            return this;
        }

        public Instiffy text(final int level, final String text) {
            this.sb.append(" ".repeat(level)).append(text);
            return this;
        }

        public String toString() {
            return this.sb.toString();
        }


    }

    public static class Doc extends MRec {

        public Doc(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public static Doc empty(final Inst inst) {
            return new Doc(Map.of(uri("inst"), inst), DOC_TID, fURI.NULL);
        }

        public String toString() {
            final Inst inst = this.at("inst").as();
            final Instiffy insty = new Instiffy();
            insty.header(0, uri(inst.tid()).toString()).text("\n");
            //  mark.header(2, this.at("inst").dom().toString()).text(this.at("dom").<Str>as().orElse(str("no dom desc")).toString());
            insty.header(1, "{{_&b}}rng{{g}}<={{b}}dom{{X}}\n");
            final int descColumn =
                    Math.max(Math.max(
                                    Graphitty.strip(inst.dom().toString()).length(),
                                    Graphitty.strip(inst.rng().toString()).length()),
                            this.at("args").orElse(rec()).jvm().entrySet().stream()
                                    .map(kv -> Graphitty.strip(kv.getKey().toString()).length() + 2 +
                                            Graphitty.strip(kv.getValue().toString()).length())
                                    .min(Integer::compare)
                                    .orElse(0)) + 5;
            insty.item(2, "{{_&b}}dom{{X}}", inst.dom().toString()).text("{{|" + descColumn + "&c}}").text(this.at("dom").orElse(str("<n/a>")).strValue()).text("{{X}}\n");
            insty.item(2, "{{_&b}}rng{{X}}", inst.rng().toString()).text("{{|" + descColumn + "&c}}").text(this.at("rng").orElse(str("<n/a>")).strValue()).text("{{X}}\n");
            if (!this.at("args").orElse(rec()).isEmpty()) {
                insty.header(1, "{{_&g}}({{b}}ar{{g}},{{b}}gs{{g}}){{X}}\n");
                this.at("args").orElse(rec()).jvm().forEach((key, value) ->
                        insty.item(2, key, inst.arg(0).toString())
                                .text("{{|" + descColumn + "&c}}")
                                .text(value.strValue())
                                .text("{{X}}\n"));
            }
            insty.header(1, "{{_&g}}{{{b}}fun{{g}}.{{b}}cti{{g}}.{{b}}on{{g}}}{{X}}\n");
            if (inst.isResolved()) {
                if (inst.f().isLambda())
                    insty.text(2, "{{y}}<j>").text("{{|" + descColumn + "&c}}").text("jvm implementation{{X}}");
                else
                    insty.text(2, inst.f().toString());
            } else {
                insty.text(2, "{{r}}no function specified{{/r}}");
            }
            insty.text("\n");
            insty.header(1, "{{b}}description{{X}}\n");
            insty.text(2, "{{c}}").text(this.at("desc").orElse(str("<no description>")).strValue()).text("{{X}}");
            return insty.toString();
        }

        public static Doc doc(final Inst inst, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description) {
            return new Doc(rec(
                    uri("inst"), inst,
                    uri("dom"), str(domDesc),
                    uri("rng"), str(rngDesc),
                    uri("args"), rec(argDescription.entrySet().stream().map(kv -> rel(kv.getKey(), str(kv.getValue())))),
                    uri("desc"), str(description)).jvm(), DOC_TID, fURI.NULL);
        }

        public static Inst docWrap(final Inst inst, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description) {
            final Doc doc = doc(inst, domDesc, rngDesc, argDescription, description);
            final Space instSpace = Router.global().getSpace(inst.tid());
            final Optional<DocQ> docq = instSpace.qs().jvm().stream().filter(q -> q instanceof DocQ).map(Obj::<DocQ>as).findAny();
            if (docq.isEmpty())
                instSpace.logger().warn("no doc query attachment mounted on %s for %s", instSpace, inst.tid());
            else
                docq.get().docSpace.put(inst.tid(), doc);
            return inst;
        }

    }

    public class OnRead extends BaseOnRead {

        public OnRead() {
            super(noobj(), noobj());
        }

        @Override
        public Optional<Obj> preRead(final fURI source, final fURI vid) {
            LOG.trace("evaluating {{y}}preread{{/y}}: %s", vid);
            return Optional.of(objs(docSpace.entrySet().stream()
                    .filter(kv -> kv.getKey().matches(vid))
                    .map(Map.Entry::getValue))
                    .orElse(objs(Router.readFromSpace(vid.removeQ("doc")).stream().map(Obj::<Inst>as).map(Doc::empty))));
        }
    }

    public class OnWrite extends BaseOnWrite {

        public OnWrite() {
            super(noobj(), noobj(), noobj());
        }

        @Override
        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.trace("evaluating {{y}}pre write{{/y}}: %s => %s", obj, vid);
            docSpace.put(vid, obj);
            return Optional.of(obj);
        }
    }
}
