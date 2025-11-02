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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.mtronParser;
import studio.phaseshift.metatron.lang.mtron.type.*;
import studio.phaseshift.metatron.lang.msys.Space;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.BASE_TYPES;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

public class ObjStringSerializer implements ObjSerializer<String> {

    public static Set<fURI> HIDE_TIDS = new HashSet<>(BASE_TYPES);
    private final Builder b;

    private ObjStringSerializer(final Builder builder) {
        this.b = builder;
    }

    public static Builder build() {
        return new Builder();
    }

    public static String prettyPrintCode(final Call code) {
        StringBuilder sb = new StringBuilder();
        return prettyPrintCode(sb,code,0,8).toString();
        
    }
    
    public static StringBuilder prettyPrintCode(final StringBuilder sb, final Obj call, final int depth, final int leftMargin) {
        if (call.isCode()) {
            for (final Inst inst : call.<Code>as().codeValue()) {
                prettyPrintCode(sb, inst, depth + 1, leftMargin);
            }
        } else if (!call.isNoObj() && call.isInst()) {
            final Inst inst = call.as();
            sb.append(" ".repeat(leftMargin)).append("  ".repeat(depth)).append(inst).append("\n");
            if (null != inst.jvm()) {
                inst.args().elements().forEach(arg -> {
                    if (arg.isCall() || arg.isObjs()) {
                        prettyPrintCode(sb, arg.as(), depth + 1, leftMargin);
                    }
                });
            }
        } else if (!call.isNoObj() && call.isObjs()) {
            call.stream().forEach(o -> prettyPrintCode(sb, o, depth + 1, leftMargin));

        }
        return sb;
    }

    @Override
    public String write(final Obj obj) throws IllegalStateException {
        try {
            final StringBuilder sb = new StringBuilder();
            if (obj.isNoObj())
                return sb.append(this.b.palette.errorC())
                        .append("noobj")
                        .append(this.b.ignoreRewrites ? "" : "{{X}}")
                        .toString();
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof final Inst inst) {
                generateTID(sb, obj.tid(), false, false).append("{{g}}({{X}}");
                if (!inst.args().isEmpty()) {
                    boolean isLst = inst.args().isLst();
                    inst.args().elements().forEach(kv -> {
                        sb.append(isLst ? kv : kv.<Rel>as().first());
                        if (!isLst)
                            sb.append("{{g}}=>").append(kv.<Rel>as().second());
                        sb.append("{{g}},");
                    });
                    sb.deleteCharAt(sb.length() - 1);
                }
                return sb.append("{{g}}){")
                        .append(this.b.palette.valueC())
                        .append(inst.resolution() == Inst.Resolution.A ? "{{r}}?{{X}}" : ("{{y}}" + inst.f().toString()))
                        .append("{{g}}}{{X}}")
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
                generateTID(sb, obj.tid(), true).append("{{g}}[").append(this.b.palette.valueC());
                for (final Obj o : lst.jvm()) {
                    sb.append(o).append(this.b.palette.formC()).append(',');
                }
                if (!lst.jvm().isEmpty()) sb.deleteCharAt(sb.length() - 1);
                else sb.append("{{g}},");
                return generateVID(sb.append("{{g}}]"), lst).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof final Objs objs) {
                generateTID(sb, obj.tid(), true).append("{{g}}{").append(this.b.palette.valueC());
                boolean found = false;
                for (final Obj o : objs.jvm()) {
                    found = true;
                    sb.append(o).append("{{g}},");
                }
                if (found) sb.deleteCharAt(sb.length() - 1);
                sb.append("{{g}}}");
                return generateVID(sb, objs).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof final Rec rec) {
                if (rec.isEmpty()) {
                    sb.append("{{g}}[=>]{{X}}");
                } else {
                    if (this.b.prettyPrint && rec.count() > 1) {
                        this.generateRec(sb, rec, 0);
                    } else {
                        generateTID(sb, obj.tid(), true).append("{{g}}[");
                        for (final Map.Entry<Obj, Obj> o : rec.jvm().entrySet()) {
                            sb.append(o.getKey()).append("{{g}}=>").append(o.getValue()).append("{{g}},");
                        }
                    }
                    if (rec.count() == 1) sb.deleteCharAt(sb.length() - 1).append("{{g}}]");
                }
                return generateVID(sb, rec).append(this.b.ignoreRewrites ? "" : "{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof Type) {
                return generateTID(sb, obj.tid(), false)
                        .append("{{r}}T{{g}}[{{y}}")
                        .append(null == obj.jvm() ? "" : obj.jvm().toString())
                        .append("{{g}}]{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof Fail) {
                generateTID(sb, obj.tid(), false);
                Throwable t = obj.<Fail>as().jvm();
                while (t != null) {
                    sb.append("{{r}}[{{X}}").append(t.getMessage()).append("{{r}}]{{X}}").append("\n\t ");
                    t = t.getCause();
                }
                sb.delete(sb.length() - 3, sb.length() - 1);
                return sb.toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (BASE_TYPES.contains(obj.type().tid().basePath())) {
                return generateVID(generateTID(sb, obj.tid(), true)
                        .append(this.b.palette.valueC())
                        .append(null == obj.jvm() ? "" : (obj.isStr() ? "'" + obj.jvm().toString() + "'" : obj.jvm().toString()))
                        .append(this.b.palette.form2C()), obj)
                        .append(this.b.ignoreRewrites ? "" : "{{X}}")
                        .toString();
            } else
                return generateVID(generateTID(sb, obj.tid(), true)
                        .append(this.b.palette.valueC())
                        .append(obj instanceof Space ? rel(uri("pattern"), null == ((Space) obj).pattern() ? uri("pattern") : ((Space) obj).pattern().toUri()) : obj.jvm())
                        .append(this.b.palette.form2C()), obj)
                        .append(this.b.ignoreRewrites ? "" : "{{X}}")
                        .toString();
        } catch (final Exception e) {
            throw MTronException.of(e,"unable to parse %s",obj.tid());
        }
    }

    private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth) {
        if (rec.isEmpty()) {
            sb.append("{{g}}[=>]{{X}}");
        } else {
            boolean nested = rec.recValue().values().stream().anyMatch(Obj::isPoly);
            sb.append("{{g}}[");
            if (nested)
                sb.append("\n");
            rec.recValue().forEach((k, v) -> {
                if (nested)
                    sb.append(" ".repeat(depth * 2));
                sb.append(write(k)).append("{{g}}=>");
                if (v.isRec()) {
                    this.generateRec(sb, v.as(), depth + 1);
                } else
                    sb.append(write(v));
                sb.append("{{g}},");
                if (nested)
                    sb.append("\n");
            });
            if (nested)
                sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            sb.append("{{g}}]");
        }
        return sb;
    }

    private StringBuilder generateVID(final StringBuilder sb, final Obj obj) {
        return null == obj.vid() ? sb : sb.append(this.b.palette.typeC())
                .append("{{g}}")
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
                    .append('{')
                    .append(this.b.palette.typeC())
                    .append(tid.c())
                    .append(this.b.palette.form3C())
                    .append('}');
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
        Result result = mtronParser.m_obj().end().parse(data);
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
