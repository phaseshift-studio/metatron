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
import studio.phaseshift.metatron.lang.obj.base.*;
import studio.phaseshift.metatron.lang.parse.ObjParser;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObjStringSerializer implements ObjSerializer<String> {

    private final Builder builder;

    private ObjStringSerializer(final Builder builder) {
        this.builder = builder;
    }

    public static Builder build() {
        return new Builder();
    }

    @Override
    public String write(final Obj obj) throws IllegalStateException {
        final StringBuilder sb = new StringBuilder();
        if (obj.isNoObj())
            return sb.append(this.builder.palette.errorC())
                    .append("noobj")
                    .append(this.builder.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        else if (obj instanceof final Inst inst) {
            sb.append(this.builder.palette.typeC())
                    .append(this.builder.hideTypes.contains(inst.tid()) ? "" : inst.tid())
                    .append(this.builder.palette.formC())
                    .append(this.builder.hideTypes.contains(inst.tid()) ? "" : ':')
                    .append("(");
            for (int i = 0; i < inst.args().count(); i++) {
                sb.append(inst.args().lstValue().get(i));
                if (i != inst.args().count() - 1)
                    sb.append(this.builder.palette.formC()).append(',');
            }
            return sb.append(this.builder.palette.formC())
                    .append("){")
                    .append(this.builder.palette.valueC())
                    .append(inst.resolution() == Inst.Resolve.A ? "" : inst.f().toString())
                    .append(this.builder.palette.formC())
                    .append("}")
                    .append(this.builder.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        } else if (obj instanceof final Rel rel) {
            return generateTID(sb, rel).append(rel.dom()).append(this.builder.palette.formC())
                    .append("=>")
                    .append(rel.rng())
                    .append(this.builder.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        } else if (obj instanceof final Lst lst) {
            generateTID(sb, obj).append(this.builder.palette.formC()).append('[').append(this.builder.palette.valueC());
            for (final Obj o : lst.value()) {
                sb.append(o).append(this.builder.palette.formC()).append(',');
            }
            if (!lst.value().isEmpty()) sb.append("{{<1}}").append(this.builder.palette.formC());
            else sb.append(this.builder.palette.formC()).append(",");
            return generateVID(sb.append(']'), lst).append(this.builder.ignoreRewrites ? "" : "{{X}}").toString();
        } else if (obj instanceof final Objs objs) {
            generateTID(sb, obj).append(this.builder.palette.formC()).append("{").append(this.builder.palette.valueC());
            boolean found = false;
            for (final Obj o : objs.value()) {
                found = true;
                sb.append(o).append(this.builder.palette.formC()).append(',');
            }
            if (found) sb.append("{{<1}}");
            else sb.append(this.builder.palette.formC()).append('}');
            return generateVID(sb, objs).append(this.builder.ignoreRewrites ? "" : "{{X}}").toString();
        } else if (obj instanceof final Rec rec) {
            generateTID(sb, obj).append(this.builder.palette.formC()).append('[').append(this.builder.palette.valueC());
            for (final Map.Entry<Obj, Obj> o : rec.value().entrySet()) {
                sb.append(o.getKey())
                        .append(this.builder.palette.formC())
                        .append("=>").append(o.getValue())
                        .append(this.builder.palette.formC())
                        .append(',');
            }
            if (!rec.value().isEmpty()) sb.append("{{<1}}").append(this.builder.palette.formC());
            else sb.append(this.builder.palette.formC()).append("=>");
            return generateVID(sb.append(']'), rec).append(this.builder.ignoreRewrites ? "" : "{{X}}").toString();
        } else
            return generateVID(generateTID(sb, obj)
                    .append(this.builder.palette.valueC())
                    .append(null == obj.value() ? "" : obj.value().toString())
                    .append(this.builder.palette.form2C()), obj)
                    .append(this.builder.ignoreRewrites ? "" : "{{X}}")
                    .toString();
    }

    private StringBuilder generateVID(final StringBuilder sb, final Obj obj) {
        return null == obj.vid() ? sb : sb.append(this.builder.palette.typeC())
                .append(this.builder.palette.formC())
                .append('@')
                .append(this.builder.palette.typeC())
                .append(obj.vid());
    }

    private StringBuilder generateTID(final StringBuilder sb, final Obj obj) {
        return this.builder.hideTypes.contains(obj.tid()) ? sb : sb.append(this.builder.palette.typeC())
                .append(obj.tid())
                .append(this.builder.palette.formC())
                .append(':');
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
