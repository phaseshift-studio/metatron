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

import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.Markdown;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DocQ extends BaseQ {

    public static final fURI DOC_TID = sysInstSet.SPACE_TID.extend("q").extend("doc");
    protected final GraphittyLogger LOG = Graphitty.log(this);
    // <source,pattern,callback>
    public final Map<fURI, Obj> docSpace;


    public DocQ() {
        super(Map.of(uri("query"), uri("doc")), f("doc"), DOC_TID);
        this.docSpace = new LinkedHashMap<>();
        this.onRead = new DocQ.OnRead();
        this.onWrite = new DocQ.OnWrite();
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
            Markdown mark = new Markdown();
            mark.header(1, inst.toString()).text("\n");
            //  mark.header(2, this.at("inst").dom().toString()).text(this.at("dom").<Str>as().orElse(str("no dom desc")).toString());
            mark.header(2, "rng<=dom\n");
            mark.item("**dom**", inst.dom().toString()).text("\t *").text(this.at("dom").orElse(str("<n/a>")).strValue()).text("*\n");
            mark.item("**rng**", inst.rng().toString()).text("\t *").text(this.at("rng").orElse(str("<n/a>")).strValue()).text("*\n");
            mark.header(2, "(ar,gs)\n");
            this.at("args").orElse(rec()).jvm().forEach((key, value) -> mark.item(key, inst.arg(0).toString() + "\t *" + value.strValue() + "*\n"));
            mark.header(2, "{fun.cti.on}\n");
            mark.text(" ").text(inst.isResolved() ? "{{y}}" + inst.f().toString() + "{{X}}" : "{{{r}}?{{/r}}}").text("\n");
            mark.header(2, "description\n");
            mark.text(" *").text(this.at("desc").orElse(str("<no description>")).strValue()).text("*");
            return mark.markdownString();
        }

        public static Doc doc(final Inst inst, final String domDesc, final String rngDesc, final Map<Obj, String> argDescription, final String description) {
            return new Doc(rec(
                    uri("inst"), inst,
                    uri("dom"), str(domDesc),
                    uri("rng"), str(rngDesc),
                    uri("args"), new MRec(argDescription.entrySet().stream()
                            .map(kv -> List.of(kv.getKey(), str(kv.getValue())))
                            .collect(Collectors.toMap(kv -> kv.get(0), kv -> kv.get(1), (a, b) -> b, LinkedHashMap::new))),
                    uri("desc"), str(description)).jvm(), DOC_TID, fURI.NULL);
        }

    }

    public class OnRead implements Q.OnRead {

        @Override
        public Optional<Obj> preRead(final fURI source, final fURI vid) {
            LOG.trace("evaluating {{y}}preread{{/y}}: %s", docSpace);
            return Optional.of(objs(docSpace.entrySet().stream()
                    .filter(kv -> kv.getKey().matches(vid))
                    .map(Map.Entry::getValue))
                    .orElse(objs(Router.readFromSpace(vid.removeQ("doc")).stream().map(Obj::<Inst>as).map(Doc::empty))));
        }
    }

    public class OnWrite implements Q.OnWrite {

        @Override
        public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            LOG.trace("evaluating {{y}}pre write{{/y}}: %s => %s", obj, vid);
            docSpace.put(vid, obj);
            return Optional.of(obj);
        }
    }
}
