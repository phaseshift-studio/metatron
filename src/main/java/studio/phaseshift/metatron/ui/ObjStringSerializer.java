/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.ui;

import org.petitparser.context.Result;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.parse.ObjParser;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObjStringSerializer implements ObjSerializer<String> {

    private final Builder b;

    private ObjStringSerializer(final Builder builder) {
        this.b = builder;
    }

    public static Builder build() {
        return new Builder();
    }

    @Override
    public String write(final Obj obj) throws IllegalStateException {
        final StringBuilder sb = new StringBuilder();
        if (obj.isNoObj())
            return sb.append(this.b.palette.errorC())
                    .append("noobj")
                    .append(this.b.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        else if (obj instanceof final Inst inst) {
            sb.append(this.b.palette.typeC())
                    .append(this.b.hideTypes.contains(inst.tid()) ? "" : inst.tid())
                    .append(this.b.palette.formC())
                    .append(this.b.hideTypes.contains(inst.tid()) ? "" : "::")
                    .append("(");
            if(!inst.args().isEmpty()) {
                boolean isLst = inst.args().isLst();
                for (final Obj kv : inst.args().elements()) {
                    sb.append(isLst ? kv : kv.<Rel>as().first());
                    if (!isLst)
                        sb.append(this.b.palette.formC()).append("=>").append(kv.<Rel>as().second());
                    sb.append(this.b.palette.formC()).append(',');
                }
               sb.setLength(sb.length()-1);
            }
            return sb.append(this.b.palette.formC())
                    .append("){")
                    .append(this.b.palette.valueC())
                    .append(inst.resolution() == Inst.Resolve.A ? "?" : inst.f().toString())
                    .append(this.b.palette.formC())
                    .append("}")
                    .append(this.b.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        } else if (obj instanceof final Rel rel) {
            return generateTID(sb, rel).append(rel.dom()).append(this.b.palette.formC())
                    .append("=>")
                    .append(rel.rng())
                    .append(this.b.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        } else if (obj instanceof final Lst lst) {
            generateTID(sb, obj).append(this.b.palette.formC()).append('[').append(this.b.palette.valueC());
            for (final Obj o : lst.value()) {
                sb.append(o).append(this.b.palette.formC()).append(',');
            }
            if (!lst.value().isEmpty()) sb.append("{{<1}}").append(this.b.palette.formC());
            else sb.append(this.b.palette.formC()).append(",");
            return generateVID(sb.append(']'), lst).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
        } else if (obj instanceof final Objs objs) {
            generateTID(sb, obj).append(this.b.palette.formC()).append("{").append(this.b.palette.valueC());
            boolean found = false;
            for (final Obj o : objs.value()) {
                found = true;
                sb.append(o).append(this.b.palette.formC()).append(',');
            }
            if (found) sb.append("{{<1}}");
            else sb.append(this.b.palette.formC()).append('}');
            return generateVID(sb, objs).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
        } else if (obj instanceof final Rec rec) {
            if(this.b.prettyPrint && rec.count() > 1) {
                this.generateRec(sb,obj.<Rec>as(),0);
            } else {
                generateTID(sb, obj).append(this.b.palette.formC()).append('[').append(this.b.palette.valueC());
                for (final Map.Entry<Obj, Obj> o : rec.value().entrySet()) {
                    sb.append(o.getKey())
                            .append(this.b.palette.formC())
                            .append("=>").append(o.getValue())
                            .append(this.b.palette.formC())
                            .append(',');
                }
            }
            if (!rec.value().isEmpty()) sb.append("{{<1}}").append(this.b.palette.formC());
            else sb.append(this.b.palette.formC()).append("=>");
            return generateVID(sb.append(']'), rec).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
        } else
            return generateVID(generateTID(sb, obj)
                    .append(this.b.palette.valueC())
                    .append(null == obj.value() ? "" : obj.value().toString())
                    .append(this.b.palette.form2C()), obj)
                    .append(this.b.ignoreRewrites ? "" : "{{X}}")
                    .toString();
    }

    private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth) {
        sb.append("{{FORM1}}[{{/FORM1}}").append("\n");
        rec.recValue().forEach((k,v) -> {
            if(depth > 0)
                sb.append(" ".repeat(depth * 2));
            sb.append(write(k)).append("{{FORM1}}=>{{/FORM1}}");
            if(v.isRec()) {
                this.generateRec(sb,v.as(),depth+1);
            } else
                sb.append(write(v));
            sb.append("\n");
        }); // {{FORM{sdfsdf}}}
        sb.append(" ".repeat(depth)).append("{{FORM1}}]{{FORM1}}");
        return sb;
    }

    private StringBuilder generateVID(final StringBuilder sb, final Obj obj) {
        return null == obj.vid() ? sb : sb.append(this.b.palette.typeC())
                .append(this.b.palette.formC())
                .append('@')
                .append(this.b.palette.typeC())
                .append(obj.vid());
    }

    private StringBuilder generateTID(final StringBuilder sb, final Obj obj) {
        return this.b.hideTypes.contains(obj.tid()) ? sb : sb.append(this.b.palette.typeC())
                .append(obj.tid())
                .append(this.b.palette.formC())
                .append("::");
    }

    @Override
    public Obj read(final String data) throws IllegalStateException {
        Result result = ObjParser.m_obj().end().parse(data);
        if (result.isFailure())
            throw new IllegalStateException(result.getMessage());
        return result.get();
    }

    public static final class Builder {

        private Palette palette = Palette.STANDARD;
        private boolean withColonSugar;
        private boolean ignoreRewrites;
        private boolean prettyPrint = true;
        private Set<fURI> hideTypes = new HashSet<>();

        private Builder() {
        }

        public Builder palette(final Palette palette) {
            this.palette = palette;
            return this;
        }

        public Builder hideTypeMatching(final fURI pattern) {
            this.hideTypes.add(pattern);
            return this;
        }

        public Builder prettyPrint(final boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public Builder hideTypesMatching(final Set<fURI> patterns) {
            this.hideTypes.addAll(patterns);
            return this;
        }

        public Builder ignoreRewrites(final boolean ignore) {
            this.ignoreRewrites = ignore;
            return this;
        }

        public Builder simpleColon(final boolean simpleColon) {
            this.withColonSugar = simpleColon;
            return this;
        }

        public ObjStringSerializer create() {
            return new ObjStringSerializer(this);
        }
    }
}
