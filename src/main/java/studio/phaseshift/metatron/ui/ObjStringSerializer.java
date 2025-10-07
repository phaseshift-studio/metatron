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

package studio.phaseshift.metatron.ui;

import org.petitparser.context.Result;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.parse.ObjParser;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.*;

public class ObjStringSerializer implements ObjSerializer<String> {

    private final Builder b;

    private ObjStringSerializer(final Builder builder) {
        this.b = builder;
    }

    public static Builder build() {
        return new Builder();
    }

    public static Set<fURI> BASE_TIDS = Set.of(BOOL_TID, INT_TID, REL_TID, REAL_TID, STR_TID, URI_TID, REC_TID, LST_TID, OBJS_TID);
    public static Set<fURI> HIDE_TIDS = new HashSet<>(BASE_TIDS);

    @Override
    public String write(final Obj obj) throws IllegalStateException {
        final StringBuilder sb = new StringBuilder();
        if (obj.isNoObj())
            return sb.append(this.b.palette.errorC())
                    .append("noobj")
                    .append(this.b.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        /// ///////////////////////////////////////////////////////////////
        /// ///////////////////////////////////////////////////////////////
        else if (obj instanceof final Inst inst) {
            generateTID(sb, obj.tid(), false).append("(");
            if (!inst.args().isEmpty()) {
                boolean isLst = inst.args().isLst();
                for (final Obj kv : inst.args().elements()) {
                    sb.append(isLst ? kv : kv.<Rel>as().first());
                    if (!isLst)
                        sb.append(this.b.palette.formC()).append("=>").append(kv.<Rel>as().second());
                    sb.append(this.b.palette.formC()).append(',');
                }
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb.append(this.b.palette.formC())
                    .append("){")
                    .append(this.b.palette.valueC())
                    .append(inst.resolution() == Inst.Resolution.A ? "{{r}}?{{/r}}" : ("{{y}}" + inst.f().toString()))
                    .append(this.b.palette.formC())
                    .append("}{{X}}")
                    //.append(this.b.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        }
        /// ///////////////////////////////////////////////////////////////
        /// ///////////////////////////////////////////////////////////////
        else if (obj instanceof final Rel rel) {
            return generateTID(sb, obj.tid(), true).append(rel.first()).append(this.b.palette.formC())
                    .append("=>")
                    .append(rel.second())
                    .append(this.b.ignoreRewrites ? "" : "{{X}}")
                    .toString();
        }
        /// ///////////////////////////////////////////////////////////////
        /// ///////////////////////////////////////////////////////////////
        else if (obj instanceof final Lst lst) {
            generateTID(sb, obj.tid(), true).append(this.b.palette.formC()).append('[').append(this.b.palette.valueC());
            for (final Obj o : lst.value()) {
                sb.append(o).append(this.b.palette.formC()).append(',');
            }
            if (!lst.value().isEmpty()) sb.deleteCharAt(sb.length() - 1).append(this.b.palette.formC());
            else sb.append(this.b.palette.formC()).append(",");
            return generateVID(sb.append(']'), lst).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
        }
        /// ///////////////////////////////////////////////////////////////
        /// ///////////////////////////////////////////////////////////////
        else if (obj instanceof final Objs objs) {
            generateTID(sb, obj.tid(), true).append(this.b.palette.formC()).append("{").append(this.b.palette.valueC());
            boolean found = false;
            for (final Obj o : objs.value()) {
                found = true;
                sb.append(o).append(this.b.palette.formC()).append(',');
            }
            if (found) sb.deleteCharAt(sb.length() - 1);
            sb.append(this.b.palette.formC()).append('}');
            return generateVID(sb, objs).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
        }
        /// ///////////////////////////////////////////////////////////////
        /// ///////////////////////////////////////////////////////////////
        else if (obj instanceof final Rec rec) {
            if (rec.isEmpty()) {
                sb.append(this.b.palette.formC()).append("[=>]{{X}}");
            } else {
                if (this.b.prettyPrint && rec.count() > 1) {
                    this.generateRec(sb, rec, 0);
                } else {
                    generateTID(sb, obj.tid(), true).append(this.b.palette.formC()).append('[').append(this.b.palette.valueC());
                    for (final Map.Entry<Obj, Obj> o : rec.value().entrySet()) {
                        sb.append(o.getKey())
                                .append(this.b.palette.formC())
                                .append("=>").append(o.getValue())
                                .append(this.b.palette.formC())
                                .append(',');
                    }
                }
                if (rec.count() == 1) sb.deleteCharAt(sb.length() - 1).append(this.b.palette.formC()).append(']');
            }
            return generateVID(sb, rec).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
        }
        /// ///////////////////////////////////////////////////////////////
        /// ///////////////////////////////////////////////////////////////
        else if (obj instanceof Type) {
            return generateTID(sb, obj.tid(), false)
                    .append("{{r}}T")
                    .append(this.b.palette.formC())
                    .append("[")
                    .append(this.b.palette.valueC())
                    .append(null == obj.value() ? "" : obj.value().toString())
                    .append(this.b.palette.formC())
                    .append("]{{X}}").toString();
        }
        /// ///////////////////////////////////////////////////////////////
        /// ///////////////////////////////////////////////////////////////
        else
            return generateVID(generateTID(sb, obj.tid(), true)
                    .append(this.b.palette.valueC())
                    .append(null == obj.value() ? "" : obj.value().toString())
                    .append(this.b.palette.form2C()), obj)
                    .append(this.b.ignoreRewrites ? "" : "{{X}}")
                    .toString();
    }

    /*private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth) {
        boolean nested = false; //rec.recValue().values().stream().anyMatch(Obj::isRec);
        sb.append("{{FORM1}}[{{/FORM1}}");
        if (nested)
            sb.append("\n");
        rec.recValue().forEach((k, v) -> {
            if (depth > 0)
                sb.append(" ".repeat(depth * 2));
            sb.append(write(k)).append("{{FORM1}}=>{{/FORM1}}");
            if (v.isRec()) {
                this.generateRec(sb, v.as(), depth + 1);
            } else
                sb.append(write(v));
            sb.append("{{FORM1}},");
           // if (nested) sb.append("\n");
        });
        sb.deleteCharAt(sb.length() - 1);
        sb.deleteCharAt(sb.length() - 1);
        sb./*append(" ".repeat(depth)).append("{{FORM1}}]{{FORM1}}");
        return sb;
    }*/

    private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth) {
        boolean nested = rec.recValue().values().stream().anyMatch(Obj::isRec);
        sb.append("{{FORM1}}[{{/FORM1}}");
        if (depth > 0 || nested)
            sb.append("\n");
        rec.recValue().forEach((k, v) -> {
            if (depth > 0)
                sb.append(" ".repeat(depth * 2));
            sb.append(write(k)).append("{{FORM1}}=>{{/FORM1}}");
            if (v.isRec()) {
                this.generateRec(sb, v.as(), depth + 1);
            } else
                sb.append(write(v));
            sb.append("{{FORM1}},");
            if (nested)
                sb.append("\n");
        });
        if (nested)
            sb.deleteCharAt(sb.length() - 1);
        sb.deleteCharAt(sb.length() - 1);
        sb.append("{{FORM1}}]{{FORM1}}");
        return sb;
    }

    public static StringBuilder prettyPrintCode(final StringBuilder sb, final Obj call, final int depth, final int leftMargin) {
        if (call.isCode()) {
            for (final Inst inst : call.<Code>as().codeValue()) {
                prettyPrintCode(sb, inst, depth + 1, leftMargin);
            }
        } else if (!call.isNoObj() && call.isInst()) {
            final Inst inst = call.as();
            sb.append(" ".repeat(leftMargin)).append("  ".repeat(depth)).append(inst).append("\n");
            if (null != inst.value()) {
                for (final Obj arg : inst.args().elements()) {
                    if (arg.isCall() || arg.isObjs()) {
                        prettyPrintCode(sb, arg.as(), depth + 1, leftMargin);
                    }
                }
            }
        } else if (!call.isNoObj() && call.isObjs()) {
            call.stream().forEach(o -> prettyPrintCode(sb, o, depth + 1, leftMargin));

        }
        return sb;
    }

    private StringBuilder generateVID(final StringBuilder sb, final Obj obj) {
        return null == obj.vid() ? sb : sb.append(this.b.palette.typeC())
                .append(this.b.palette.formC())
                .append('@')
                .append(this.b.palette.typeC())
                .append(obj.vid());
    }

    private StringBuilder generateTID(final StringBuilder sb, final fURI tid, final boolean hide) {
        return generateTID(sb, tid, true, hide);
    }

    private StringBuilder generateTID(final StringBuilder sb, final fURI tid, final boolean doubleColon, final boolean hide) {
        if (hide && HIDE_TIDS.contains(tid))
            return sb;
        if (!this.b.hideTypes.contains(tid))
            sb.append(this.b.palette.typeC()).append(tid.small().basePath());
        if (!tid.cV().isOne())
            sb.append(this.b.palette.form3C())
                    .append('[')
                    .append(this.b.palette.typeC())
                    .append(tid.c())
                    .append(this.b.palette.form3C())
                    .append(']');
        if (tid.hasQuery()) {
            sb.append(this.b.palette.valueC()).append('?');
            for (Map.Entry<String, String> kv : tid.queryMap().entrySet()) {
                sb.append(this.b.palette.form3C()).append(kv.getKey()).append(this.b.palette.form2C()).append("=");
                if (kv.getKey().equals("dom") || kv.getKey().equals("rng"))
                    generateTID(sb, fURI.of(kv.getValue()), false, false);
                else
                    sb.append(this.b.palette.typeC()).append(kv.getValue());
                sb.append(this.b.palette.valueC()).append("&");
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return doubleColon ? sb.append(this.b.palette.formC()).append("::") : sb;
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
