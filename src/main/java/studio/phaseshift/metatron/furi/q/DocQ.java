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
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.Space.PATTERN;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.INST_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.Inst.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Common.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DocQ extends BaseQ {

    public static final String DESC = "desc";
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
        super.put(ON_WRITE, this.onWrite);
    }

    public static class Instiffy {
        public static final int DESC_WRAP = 35;
        public static final int PADDING = 1;

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

        public Instiffy clip(final int amount) {
            if (amount > 0)
                this.sb.delete(0, amount);
            else
                this.sb.delete(this.sb.length() + amount, this.sb.length() - 1);
            return this;
        }

        public Instiffy rlastRow() {
            int r = this.sb.lastIndexOf("\n");
            int l = this.sb.lastIndexOf("\n", r);
            if (r != -1) {
                if (l != -1) {
                    this.sb.delete(l, r);
                } else {
                    this.sb.delete(0, r);
                }
            } else {
                this.sb.delete(l, 0);
            }
            return this;
        }

        public Instiffy rtrim() {
            final String current = this.sb.toString();
            this.sb.delete(0, this.sb.length() - 1);
            this.sb.append(current.stripTrailing());
            return this;
        }

        public Instiffy until(final char token, final int column) {
            final String stripped = this.strippedString();
            int length = column - (stripped.length() - (Math.max(stripped.lastIndexOf("\n"), 0)));
            //  if (length < 0)
            //throw MTronException.of("width of frame too large: " + length);
            this.sb.append(("" + token).repeat(length < 0 ? column : length));
            return this;
        }

        public Instiffy text(final int level, final String text) {
            this.sb.append(" ".repeat(level)).append(text);
            return this;
        }

        public String toString() {
            return this.sb.toString().replace("lhs", "{{~}}lhs{{X&c}}").replace("rhs", "{{~}}rhs{{X&c}}");
        }

        public String strippedString() {
            return Graphitty.strip(this.toString());
        }
    }

    public static class Doc extends MRec {

        private static final String NONE = "<none>";

        public Doc(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public static Doc empty(final Inst inst) {
            return new Doc(Map.of(uri(INST_TID.name()), inst), DOC_TID, fURI.NULL);
        }

        public Poly args() {
            return this.at(ARGS);
        }

        public String description() {
            return this.at(DESC).isNoObj() ? null : this.at(DESC).strValue();
        }

        public String toString() {
            // ┌|├|┐|└|┘|│|┤|─|⋰|⋱|⮝|⮞|⮜|⮟
            /**
             * ┌───────────────────────────────────────────────────────────┐
             * │ rng<=dom           ┌────────────────────────────────────┐ │
             * │  dom: str::T    <──┤a str to split                      │ │
             * │  rng: str{+}::T <──┤the components of the split lhs str │ │
             * │ (ar,gs)            │                                    │ │
             * │  0. str::T      <──┤a token to split on                 │ │
             * │ {fun.cti.on}       │                                    │ │
             * │  <j>            <──┤jvm implementation                  │ │
             * │                    └────────────────────────────────────┘ │
             * ├─description───────────────────────────────────────────────┤
             * │    split the lhs string according to t                    │
             * │    he token arg and emit a stream of s                    │
             * │    plits                                                  │
             * └───────────────────────────────────────────────────────────┘
             */
            final Inst inst = this.at(INST_TID.name()).as();
            final Instiffy insty = new Instiffy();
            final int lhsBorderColumn = Math.max(Math.max(
                            Graphitty.strip(inst.dom().toString()).length(),
                            Graphitty.strip(inst.rng().toString()).length()),
                    this.at(ARGS).orElse(rec()).jvm().entrySet().stream()
                            .map(kv -> Graphitty.strip(kv.getKey().toString()).length() +
                                    Graphitty.strip(kv.getValue().toString()).length())
                            .min(Integer::compare)
                            .orElse(20)) + 3;
            final int rhsBorderColumn = Math.max(inst.tid().toString().length(), Math.max(Math.max(
                            this.at(DOM).orElse(str(NONE)).strValue().length(),
                            this.at(RNG).orElse(str(NONE)).strValue().length()),
                    this.at(ARGS).orElse(rec()).jvm().values().stream()
                            .map(obj -> Graphitty.strip(obj.toString()).length())
                            .max(Integer::compare)
                            .orElse(20)) + lhsBorderColumn + 4);
            insty.text("\n{{m}}/--").text(inst.tid().toUri().toString()).text("{{m}}").until('-', rhsBorderColumn).text("/{{X}}\n");
            insty.text("{{m}}|").text(" ").text("{{_&b}}rng{{g}}<={{b}}dom{{X}}").text("{{|" + rhsBorderColumn + "&m}}|{{X}}\n");
            insty.text("{{m}}|").text("  ").text("{{_&b}}dom{{X}}:").text(inst.dom().toString()).text("{{|" + lhsBorderColumn + "&c}}").text(this.at(DOM).orElse(str(NONE)).strValue()).text("{{|" + rhsBorderColumn + "&m}}|\n");
            insty.text("{{m}}|").text("  ").text("{{_&b}}rng{{X}}:").text(inst.rng().toString()).text("{{|" + lhsBorderColumn + "&c}}").text(this.at(RNG).orElse(str(NONE)).strValue()).text("{{|" + rhsBorderColumn + "&m}}|\n");
            if (!this.at(ARGS).orElse(rec()).isEmpty()) {
                insty.text("{{m}}| {{_&g}}({{b}}ar{{g}},{{b}}gs{{g}}){{X}}").text("{{|" + rhsBorderColumn + "&m}}|{{X}}\n");
                this.at(ARGS).orElse(rec()).jvm().forEach((key, value) ->
                        insty.text("{{m}}|").item(2, key, inst.arg(0).toString())
                                .text("{{|" + lhsBorderColumn + "&c}}")
                                .text(value.strValue())
                                .text("{{|" + rhsBorderColumn + "&m}}|\n"));
            }
            insty.text("{{m}}| {{_&g}}{{{b}}fun{{g}}.{{b}}cti{{g}}.{{b}}on{{g}}}{{X}}").text("{{|" + rhsBorderColumn + "&m}}|{{X}}\n");
            if (inst.isResolved()) {
                if (inst.f().isLambda())
                    insty.text("{{m}}|   {{y}}<j>").text("{{|" + lhsBorderColumn + "&c}}").text("jvm implementation").text("{{|" + rhsBorderColumn + "&m}}|{{X}}\n");
                else
                    insty.text("{{m}}|   ").text(inst.f().toString()).text("{{|" + rhsBorderColumn + "&m}}|{{X}}\n");
            } else {
                insty.text("{{m}}|   {{r}}").text(NONE).text("{{|" + rhsBorderColumn + "&m}}|{{X}}\n");
            }
            insty.text("{{m}}|").until(' ', rhsBorderColumn).text("|{{X}}\n");
            insty.text("{{m}}|--").text("{{b}}description{{m}}").until('-', rhsBorderColumn - 1).text("-|{{X}}\n");
            String desc = this.at(DESC).orElse(str("<no description>")).strValue();
            int lhs = 0;
            int rowLength = Math.min(rhsBorderColumn - 6, desc.length()); // 6 to compensate for lhs padding in desc box
            while (true) {
                int rhs = Math.min(desc.length(), lhs + rowLength);
                if (rhs < 1)
                    break;
                insty.text("{{m}}|  {{c}}").text(desc.substring(lhs, rhs).trim()).text("{{|" + rhsBorderColumn + "&m}}|{{X}}\n");
                if (lhs + rowLength >= desc.length())
                    break;
                lhs = rhs;
            }
            insty.text("{{m}}").text("/").until('-', rhsBorderColumn).text("/{{X}}");
            return insty.toString();
        }

        public static Doc doc(final Inst inst, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description) {
            return new Doc(rec(
                    uri(INST_TID.name()), inst,
                    uri(DOM), str(domDesc),
                    uri(RNG), str(rngDesc),
                    uri(ARGS), rec(argDescription.entrySet().stream().map(kv -> rel(kv.getKey(), str(kv.getValue())))),
                    uri(DESC), str(description)).jvm(), DOC_TID, fURI.NULL);
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
