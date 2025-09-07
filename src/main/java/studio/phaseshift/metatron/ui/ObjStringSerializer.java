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
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.Palette;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.util.ObjUtil;

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
    public String write(final BObj.Obj obj) throws IllegalStateException {
        final StringBuilder sb = new StringBuilder();
        if (obj.isNoObj())
            return sb.append(this.builder.palette.errorC()).append("noobj")/*.reset()*/.toString();
        else if (obj instanceof final BObj.Inst inst) {
            sb.append(this.builder.palette.typeC())
                    .append(this.builder.hideTypes.contains(inst.tid()) ? "" : inst.tid())
                    .append(this.builder.palette.formC())
                    .append(this.builder.hideTypes.contains(inst.tid()) ? "" : ':')
                    .append("(");
            for (int i = 0; i < inst.args().length(); i++) {
                sb.append(inst.args().lstValue().get(i));
                if (i != inst.args().length() - 1)
                    sb.append(this.builder.palette.formC()).append(',');
            }
            return sb.append(this.builder.palette.formC())
                    .append(")[")
                    .append(this.builder.palette.valueC())
                    .append(inst.resolved() ? ObjUtil.isLambda(inst.f()) ? "λ" : inst.f() : "?")
                    .append(this.builder.palette.formC())
                    .append(']')
                    /*.reset()*/
                    .toString();
        } else if (obj instanceof final BObj.Rel rel) {
            return generateTID(sb, rel).append(rel.domain()).append(this.builder.palette.formC())
                    .append("=>")
                    .append(rel.range())/*.reset()*/.toString();
        } else if (obj instanceof final BObj.Lst lst) {
            generateTID(sb, obj).append(this.builder.palette.formC()).append('[').append(this.builder.palette.valueC());
            for (final BObj.Obj o : lst.value()) {
                sb.append(o).append(this.builder.palette.formC()).append(',');
            }
            if (!lst.value().isEmpty())
                sb.append("{{<1}}").append(this.builder.palette.formC()).append(']');
            return generateVID(sb, lst)/*.reset()*/.toString();
        } else if (obj instanceof final BObj.Objs objs) {
            generateTID(sb, obj).append(this.builder.palette.formC()).append("{").append(this.builder.palette.valueC());
            boolean found = false;
            for (final BObj.Obj o : objs.value()) {
                found = true;
                sb.append(o).append(this.builder.palette.formC()).append(',');
            }
            if (found)
                sb.append("{{<1}}").append(this.builder.palette.formC()).append('}');
            return generateVID(sb, objs)/*.reset()*/.toString();
        } else if (obj instanceof final BObj.Rec rec) {
            generateTID(sb, obj).append(this.builder.palette.formC()).append('[').append(this.builder.palette.valueC());
            for (final Map.Entry<BObj.Obj, BObj.Obj> o : rec.value().entrySet()) {
                sb.append(o.getKey()).append(this.builder.palette.formC()).append("=>").append(o.getValue()).append(this.builder.palette.formC()).append(',');
            }
           if(!rec.value().isEmpty())
               sb.append("{{<1}}").append(this.builder.palette.formC()).append(']');
            return generateVID(sb, rec)/*.reset()*/.toString();
        } else
            return generateVID(generateTID(sb, obj)
                    .append(this.builder.palette.valueC())
                    .append(obj.value())
                    .append(this.builder.palette.form2C()), obj)
                    /*.reset()*/
                    .toString();
    }

    private StringBuilder generateVID(final StringBuilder sb, final BObj.Obj obj) {
        return null == obj.vid() ? sb : sb.append(this.builder.palette.typeC())
                .append(this.builder.palette.formC())
                .append('@')
                .append(this.builder.palette.typeC())
                .append(obj.vid());
    }

    private StringBuilder generateTID(final StringBuilder sb, final BObj.Obj obj) {
        return this.builder.hideTypes.contains(obj.tid()) ? sb : sb.append(this.builder.palette.typeC())
                .append(obj.tid())
                .append(this.builder.palette.formC())
                .append(':');
    }

    @Override
    public BObj.Obj read(final String data) throws IllegalStateException {
        Result result = ObjParser.m_obj().end().parse(data);
        if (result.isFailure())
            throw new IllegalStateException(result.getMessage());
        return result.get();
    }

    public static final class Builder {

        private Palette palette;
        private boolean withColonSugar;
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

        public Builder simpleColon(final boolean simpleColon) {
            this.withColonSugar = simpleColon;
            return this;
        }

        public ObjStringSerializer create() {
            return new ObjStringSerializer(this);
        }
    }
}
