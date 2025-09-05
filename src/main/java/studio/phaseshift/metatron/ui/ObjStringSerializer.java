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

import org.jline.jansi.Ansi;
import org.petitparser.context.Result;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.Palette;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.HashSet;
import java.util.Set;

import static org.jline.jansi.Ansi.ansi;

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
        if (obj.isNoObj())
            return ansi().fg(this.builder.palette.errorC()).a("noobj").reset().toString();
        else if (obj instanceof final BObj.Inst inst) {
            Ansi s = ansi()
                    .fg(this.builder.palette.typeC()).
                    a(this.builder.hideTypes.contains(inst.tid()) ? "" : inst.tid())
                    .fg(this.builder.palette.formC())
                    .a(this.builder.hideTypes.contains(inst.tid()) ? "" : ':')
                    .a("(");
            for (int i = 0; i < inst.args().length(); i++) {
                s = s.a(inst.args().lstValue().get(i));
                if (i != inst.args().length() - 1)
                    s = s.fg(this.builder.palette.formC()).a(',');
            }
            return s.fg(this.builder.palette.formC())
                    .a(")[")
                    .fg(this.builder.palette.valueC())
                    .a(inst.resolved() ? ObjUtil.isLambda(inst.f()) ? "λ" : inst.f() : "?")
                    .fg(this.builder.palette.formC())
                    .a(']')
                    .reset()
                    .toString();
        } else if (obj instanceof final BObj.Rel rel) {
            return ansi()
                    .fg(this.builder.palette.typeC())
                    .a(this.builder.hideTypes.contains(rel.tid()) ? "" : rel.tid())
                    .fg(this.builder.palette.formC())
                    .a(this.builder.hideTypes.contains(rel.tid()) ? "" : ':')
                    .a("[")
                    .a(rel.domain())
                    .fg(this.builder.palette.formC())
                    .a("=>")
                    .a(rel.range())
                    .fg(this.builder.palette.formC())
                    .a(']').reset().toString();
        } else if (obj instanceof final BObj.Lst lst) {
            Ansi s = generateTID(ansi(), obj)
                    .a('[').fg(this.builder.palette.valueC());
            for (final BObj.Obj o : lst.value()) {
                s = s.a(o).fg(this.builder.palette.formC()).a(',');
            }
            s = s.cursorLeft(1).fg(this.builder.palette.formC()).a(']');
            return generateVID(s, lst).reset().toString();
        } else if (obj instanceof final BObj.Objs objs) {
            Ansi s = generateTID(ansi(), obj)
                    .a('{').fg(this.builder.palette.valueC());
            for (final BObj.Obj o : objs.value()) {
                s = s.a(o).fg(this.builder.palette.formC()).a(',');
            }
            s = s.cursorLeft(1).fg(this.builder.palette.formC()).a('}');
            return generateVID(s, objs).reset().toString();
        }/*else if (obj instanceof final BObj.Rec rec) {
            Ansi s = ansi()
                    .fg(this.builder.palette.typeC())
                    .a(this.builder.hideTypes.contains(rec.tid()) ? "" : rec.tid())
                    .fg(this.builder.palette.formC())
                    .a('[');
            final List<Map.Entry<BObj.Obj, BObj.Obj>> kv = new ArrayList<>(rec.value().entrySet());
            for (int i = 0; i < kv.size(); i++) {
                s = s.a(kv.get(i).getKey()).fg(this.builder.palette.formC()).a("=>").a(kv.get(i).getValue());
                if (i != kv.size() - 1)
                    s = s.fg(this.builder.palette.formC()).a(',');
            }
            return s.fg(this.builder.palette.formC()).a(']').reset().toString();
        }*/ else
            return generateVID(generateTID(ansi(), obj)
                    .fg(this.builder.palette.valueC())
                    .a(obj.value())
                    .fg(this.builder.palette.form2C()), obj)
                    .reset()
                    .toString();
    }

    private Ansi generateVID(final Ansi ansi, final BObj.Obj obj) {
        return null == obj.vid() ? ansi : ansi.fg(this.builder.palette.typeC())
                .fg(this.builder.palette.formC())
                .a('@')
                .fg(this.builder.palette.typeC())
                .a(obj.vid());
    }

    private Ansi generateTID(final Ansi ansi, final BObj.Obj obj) {
        return this.builder.hideTypes.contains(obj.tid()) ? ansi : ansi.fg(this.builder.palette.typeC())
                .a(obj.tid())
                .fg(this.builder.palette.formC())
                .a(':');
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
