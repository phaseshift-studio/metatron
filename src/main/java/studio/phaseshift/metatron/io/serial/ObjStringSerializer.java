/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.io.serial;

import org.petitparser.context.Result;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.sys.type.console.Highlighter;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

@ServiceMetadata(tid = "/sys/io/string")
public class ObjStringSerializer implements ObjSerializer<String> {

    public static Set<fURI> HIDE_TIDS = new HashSet<>(BASE_TYPES);
    private final Builder b;

    public ObjStringSerializer() {
        this.b = new Builder();
    }

    public ObjStringSerializer(final Builder b) {
        this.b = b;
    }

    public static Builder build() {
        return new Builder();
    }

    public static String prettyPrintCode(final Call code) {
        return prettyPrintCode(code, 8);
    }

    public static String prettyPrintCode(final Call code, final int leftMargin) {
        return prettyPrintCode(new StringBuilder(), code, 0, leftMargin).toString().trim();
    }

    @Override
    public fURI tid() {
        return OBJ_SERIAL_TID.extend("string");
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) {
        return ByteBuffer.wrap(this.write(obj).getBytes());
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        return this.read(new String(bytes.array()));
    }

    public static StringBuilder prettyPrintCode(final StringBuilder sb, final Obj call, final int depth, final int leftMargin) {
        if (call.isCode()) {
            for (final Inst inst : call.<Code>as().codeValue()) {
                prettyPrintCode(sb, inst, depth, leftMargin);
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
        return Highlighter.unformat(this.temp(obj));
    }

    public String temp(final Obj obj) {
        try {
            final StringBuilder sb = new StringBuilder();
            if (obj.isNoObj())
                return sb.append("{{m}}noobj{{X}}").toString();
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof final Code code) {
                generateTID(sb, obj.tid(), true, true);
                return generateVID(sb
                        .append(code.insts().stream()
                                .map(Inst::toString).reduce("", (a, b) -> a + "." + b).substring(1)), code)
                        .toString();
            } else if (obj instanceof final Inst inst) {
                if (inst.tid().basePath().equals(AUTO_INST_TID) && inst.arg(0).tid().equals(FROM_INST_TID)) {
                    return sb.append("{{c}}*{{X}}").append(inst.arg(0).<Inst>as().arg(0)).toString();
                } else {
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
                    return sb.append("{{g}}){{{y}}")
                            .append(inst.isResolved(false) ? ("{{y}}" + inst.f().toString()) : "{{r}}?{{X}}")
                            .append("{{g}}}{{X}}")
                            //.append(this.b.ignoreRewrites ? "" : "{{X}}")
                            .toString();
                }
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof final Rel rel) {
                return generateTID(sb, obj.tid(), true)
                        .append(rel.jvm().get0())
                        .append("{{g}}=>")
                        .append(rel.jvm().get1())
                        .append("{{X}}")
                        .toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof final Lst lst) {
                if (this.b.prettyPrint && lst.count() > 1) {
                    this.generateLst(sb, lst, 2);
                    return sb.toString();
                } else {
                    generateTID(sb, obj.tid(), true).append("{{g}}[{{y}}");
                    for (final Obj o : lst.jvm()) {
                        sb.append(o).append("{{g}},");
                    }
                    if (!lst.jvm().isEmpty()) sb.deleteCharAt(sb.length() - 1);
                }
                return generateVID(sb.append("{{g}}]"), lst).append("{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof final Objs objs) {
                generateTID(sb, obj.tid(), true).append("{{g}}{");
                boolean found = false;
                for (final Obj o : objs.jvm()) {
                    found = true;
                    sb.append(o).append("{{g}},");
                }
                if (found) sb.deleteCharAt(sb.length() - 1);
                sb.append("{{g}}}");
                return generateVID(sb, objs).append("{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof final Rec rec) {
                if (rec.isEmpty()) {
                    sb.append("{{g}}[=>]{{X}}");
                } else {
                    if (this.b.prettyPrint && rec.count() > 1) {
                        this.generateRec(sb, rec, 2);
                        return sb.toString();
                    } else {
                        generateTID(sb, obj.tid(), true).append("{{g}}[");
                        for (final Map.Entry<Obj, Obj> o : rec.jvm().entrySet()) {
                            sb.append(o.getKey().isUri() ? ("{{b}}" + o.getKey().uriValue()) : o.getKey()).append("{{g}}=>").append(o.getValue()).append("{{g}},");
                        }
                    }
                    if (rec.count() == 1) sb.deleteCharAt(sb.length() - 1).append("{{g}}]");
                }
                return generateVID(sb, rec).append("{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof Type) {
                final Obj predicate = ((Type) obj).predicate();
                final Obj constructor = ((Type) obj).constructor();
                final String pred = predicate == null ? "{{y}}<X>{{X}}" : write(predicate);
                final String con = constructor == null ? "{{y}}<X>{{X}}" : write(constructor);
                generateTID(sb, obj.tid(), false).append("{{m}}T");
                if (null != predicate)
                    sb.append("{{g}}[{{X}}").append(pred).append("{{g}}]{{X}}");
                if (null != constructor)
                    sb.append("{{g}}[{{X}}").append(con).append("{{g}}]{{X}}");
                return sb.append("{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj instanceof Fail) {
                generateTID(sb, obj.tid(), false);
                Throwable t = obj.<Fail>as().jvm().get0();
                while (t != null) {
                    sb.append("{{r}}[{{X}}").append(t.getMessage()).append("{{r}}]{{X}}").append("\n\t ");
                    t = t.getCause();
                }
                sb.delete(sb.length() - 3, sb.length() - 1);
                return sb.toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj.isBytes()) {
                generateTID(sb, obj.tid(), true);
                if (obj.bytesValue().capacity() > b.strClip) {
                    this.writeClip(sb, obj);
                } else {
                    sb.append("{{c}}0x{{y}}").append(HexFormat.of().formatHex(obj.<Bytes>as().jvm().array()));
                }

                return generateVID(sb, obj).append("{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (obj.isStr()) {
                final String objStr = obj.strValue();
                final String quotes = objStr.contains("\"") || objStr.contains("\n") || objStr.contains("'") ? "\"\"\"" : "'";
                return generateVID(generateTID(sb, obj.tid(), true)
                        .append("{{c}}")
                        .append(quotes)
                        .append("{{y}}")
                        .append(objStr)
                        .append("{{c}}")
                        .append(quotes), obj).append("{{X}}").toString();
            }
            /// ///////////////////////////////////////////////////////////////
            /// ///////////////////////////////////////////////////////////////
            else if (BASE_TYPES.contains(obj.type().tid().basePath())) {
                return generateVID(generateTID(sb, obj.tid(), true)
                        .append("{{y}}")
                        .append(null == obj.jvm() ? "" : obj.jvm().toString())
                        .append("{{m}}"), obj)
                        .append("{{X}}")
                        .toString();
            } else
                return generateVID(generateTID(sb, obj.tid(), true)
                        .append("{{y}}")
                        .append(obj.jvm().toString())
                        .append("{{m}}"), obj)
                        .append("{{X}}")
                        .toString();
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to parse %s", obj.tid());
        }
    }

    private StringBuilder writeClip(final StringBuilder sb, final Obj obj) {
        if (obj.isStr() && obj.strValue().length() > b.strClip) {
            sb.append(write(str(obj.strValue().substring(0, b.strClip - 1))));
            sb.append(Graphitty.sillyPrint("...", true, false));
        } else if (obj.isBytes() && obj.bytesValue().capacity() > b.strClip) {
            byte[] bb = Arrays.copyOf(obj.bytesValue().array(), b.strClip - 1);
            sb.append(write(bytes(ByteBuffer.wrap(bb))));
            sb.append(Graphitty.sillyPrint("...", true, false));
        } else
            sb.append(write(obj));
        return sb;
    }

    private StringBuilder generateLst(final StringBuilder sb, final Lst lst, final int depth) {
        generateTID(sb, lst.tid(), true);
        if (lst.isEmpty()) {
            sb.append("{{g}}[,]{{X}}");
        } else {
            boolean nested =
                    lst.elements().anyMatch(Obj::isPoly) ||
                            lst.elements().filter(o -> !o.isPoly()).map(Obj::toString).map(Highlighter::visualLength).reduce(0, Integer::sum) > (30 - depth);
            sb.append("{{g}}[");
            AtomicBoolean first = new AtomicBoolean(true);
            lst.elements().forEach(v -> {
                if (nested && !first.getAndSet(false))
                    sb.append(" ".repeat(depth + 2));
                this.processNestedPoly(sb, depth, nested, v);
            });
            if (nested)
                sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            sb.append("{{g}}]");
        }
        return generateVID(sb, lst);
    }

    private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth) {
        generateTID(sb, rec.tid(), true);
        if (rec.isEmpty()) {
            sb.append("{{g}}[=>]{{X}}");
        } else {
            boolean nested =
                    rec.recValue().values().stream().anyMatch(Obj::isPoly) ||
                            rec.recValue().values().stream().filter(o -> !o.isPoly()).map(Obj::toString).map(String::length).reduce(0, Integer::sum) > (75 - depth);
            sb.append("{{g}}[");
            if (nested)
                sb.append("\n");
            AtomicBoolean first = new AtomicBoolean(true);
            rec.recValue().forEach((k, v) -> {
                if (nested)
                    sb.append(" ".repeat(false && first.getAndSet(false) ? 0 : (depth * 2) + 1));
                sb.append(k.isUri() ? ("{{b}}" + k.uriValue()) : write(k)).append("{{g}}=>");
                this.processNestedPoly(sb, depth, nested, v);
            });
            if (nested)
                sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            sb.append("{{g}}]");
        }
        return generateVID(sb, rec);
    }

    private void processNestedPoly(final StringBuilder sb, final int depth, final boolean nested, final Obj v) {
        if (v.isRec()) {
            this.generateRec(sb, v.as(), depth + 1);
        } else if (v.isLst()) {
            this.generateLst(sb, v.as(), depth + 1);
        } else {
            this.writeClip(sb, v);
        }
        sb.append("{{g}},");
        if (nested)
            sb.append("\n");
    }

    private StringBuilder generateVID(final StringBuilder sb, final Obj obj) {
        return null == obj.vid() ? sb : sb.append("{{g}}@{{b}}").append(obj.vid());
    }

    private StringBuilder generateTID(final StringBuilder sb, final fURI tid, final boolean hide) {
        return generateTID(sb, tid, true, hide);
    }

    private StringBuilder generateTID(final StringBuilder sb, final fURI tid, final boolean doubleColon, final boolean hide) {
        if (hide && HIDE_TIDS.contains(tid))
            return sb;
        if (!this.b.hideTypes.contains(tid))
            sb.append("{{b}}").append(tid.small().basePath());
        if (!tid.cV().isOne())
            sb.append("{{g}}{{{y}}")
                    .append(tid.c())
                    .append("{{g}}}");
        if (tid.hasQuery()) {
            sb.append("{{y}}?");
            for (Map.Entry<String, String> kv : tid.queryMap().entrySet()) {
                sb.append("{{c}}").append(kv.getKey()).append("{{m}}=");
                if (kv.getKey().equals("dom") || kv.getKey().equals("rng"))
                    generateTID(sb, fURI.of(kv.getValue()), false, false);
                else
                    sb.append("{{b}}").append(kv.getValue());
                sb.append("{{y}}&");
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return doubleColon ? sb.append("{{g}}::") : sb;
    }

    @Override
    public Obj read(final String data) throws IllegalStateException {
        Result result = mParser.m_obj().end().parse(data);
        if (result.isFailure())
            throw new IllegalStateException(result.getMessage());
        return result.get();
    }

    public static final class Builder {
        private boolean withColonSugar;
        private boolean ignoreRewrites;
        private int strClip = 40;
        private boolean prettyPrint = true;
        private Set<fURI> hideTypes = new HashSet<>();

        private Builder() {
        }

        public Builder strClip(final int clipSize) {
            this.strClip = clipSize;
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
