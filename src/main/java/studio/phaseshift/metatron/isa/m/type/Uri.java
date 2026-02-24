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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.Tokens.C;
import static studio.phaseshift.metatron.furi.fURI.*;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Rec.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Uri extends Mono, Ring.O<Uri> {

    Type URI_TYPE = Type.Builder.build().tid(URI_TID).vid(URI_TID).create();

    @Override
    Uri clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    fURI jvm();

    default Obj at(final Obj key) {
        final fURI k = key.uriValue();
        if (k.equals(f(SCHEME)))
            return uri(this.uriValue().scheme());
        else if (k.equals(f(HOST)))
            return uri(this.uriValue().host());
        else if (k.equals(f(PORT)))
            return jnt(this.uriValue().port());
        else if (k.equals(f(PATH)))
            return lst(this.uriValue().segments().stream().map(MUri::uri).map(Obj::<Obj>as).toList());
        else if (k.equals(f(C)))
            return rec(
                    MIN, null == this.uriValue().cV().min() ? noobj() : jnt(this.uriValue().cV().min()),
                    MAX, null == this.uriValue().cV().max() ? noobj() : jnt(this.uriValue().cV().max()));
        else if (k.equals(f(Q)))
            return rec(this.uriValue().queryMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue()))));
        else
            throw MTronException.of("unknown uri component: %s", k);
    }

    default Uri jvm(final fURI jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    @Override
    default Uri tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    @Override
    default Uri vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    @Override
    default Uri one() {
        return this.jvm().one().toUri();
    }

    @Override
    default Uri mult(final Uri rhs) {
        return this.jvm(this.uriValue().mult(rhs.uriValue()));
    }


    @Override
    default Uri zero() {
        return this.jvm().zero().toUri();
    }

    @Override
    default Uri plus(final Uri rhs) {
        return this.jvm(this.uriValue().plus(rhs.uriValue()));
    }

    @Override
    default Uri neg() {
        return this.jvm(this.uriValue().neg());
    }

    @Override
    default boolean test(final Obj obj) {
        if (obj.isUri())
            return this.uriValue().test(obj.uriValue());
        return Mono.super.test(obj);
    }


    final class UriType {
        private final static Map<String, Pattern> REGEX_CACHE = new HashMap<>();

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    //instC(SPLIT_INST_TID.dom(URI_TID).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(Arrays.stream(lhs.uriValue().toString().split(inst.arg(0).uriValue().toString())).map(MUri::uri))),
                    instC(AS_INST_TID.dom(URI_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> jnt(Integer.parseInt(lhs.uriValue().toString()), inst.arg(0).tid(), fnull)),
                    instC(AS_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue(), inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(URI_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.uriValue().toString(), inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(URI_TID).rng(REC_TID), lst(REC_TYPE), (lhs, inst) -> {
                        final fURI lhsUri = lhs.asUri().uriValue();
                        return rec(
                                SCHEME, lhsUri.scheme() == null ? noobj() : uri(lhsUri.scheme()),
                                HOST, lhsUri.host() == null ? noobj() : uri(lhsUri.host()),
                                PORT, lhsUri.port() == -1 ? noobj() : jnt(lhsUri.port()),
                                PATH, lhsUri.path().isEmpty() ? noobj() : lst(Stream.concat(Stream.concat((lhsUri.isAbsolute() && !lhsUri.hasAuthority()) ? Stream.of(uri()) : Stream.empty(), lhsUri.segments().stream().map(MUri::uri)), lhsUri.isBranch() ? Stream.of(uri()) : Stream.empty())),
                                C, rec(MIN, null == lhsUri.cV().min() ? noobj() : jnt(lhsUri.cV().min()), MAX, null == lhsUri.cV().max() ? noobj() : jnt(lhsUri.cV().max())),
                                Q, lhsUri.queryMap().isEmpty() ? noobj() : rec(lhsUri.queryMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue())))));
                    }),
                    docWrap(instC(HAS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(STR_TID)), (lhs, inst) -> REGEX_CACHE.compute(inst.arg(0).strValue(), (k, v) -> null == v ? Pattern.compile(k) : v).matcher(lhs.uriValue().toString()).find() ? lhs : noobj()),
                            "an str to check", "whether the domain matches arg", Map.of(jnt(0), "the regex for matching"), "check whether the lhs str matches the regex arg"),
                    instC(SPLIT_INST_TID.dom(URI_TID).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(Arrays.stream(lhs.uriValue().toString().split(inst.arg(0).uriValue().toString())).map(MUri::uri))),
                    instC(MERGE_INST_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.stream().map(Obj::uriValue).reduce((a, b) -> a.extend(inst.arg(0).uriValue()).extend(b)).orElse(f("noobj")))),
                  //  instC(RSHIFT_INST_TID.dom(URI_TID).rng(URI_TID), lst(isa_(T(INT_TID)).else_(jnt(1))), (lhs, inst) -> lhs.jvm(lhs.uriValue().retract(inst.arg(0).intValue().intValue()))),
                  //  instC(LSHIFT_INST_TID.dom(URI_TID).rng(URI_TID), lst(isa_(T(INT_TID)).else_(jnt(1))), (lhs, inst) -> lhs.jvm(lhs.uriValue().pretract(inst.arg(0).intValue().intValue()))),
                    instC(PLUS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID.maybe())), (lhs, inst) -> lhs.jvm(lhs.uriValue().plus(inst.arg(0).uriValue()))),
                    instC(MULT_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID.maybe())), (lhs, inst) -> lhs.jvm(lhs.uriValue().mult(inst.arg(0).uriValue()))),
                    instC(SUM_INST_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Uri) a).plus((Uri) b)).uriValue()), uri(fURI.NOOBJ)),
                    instC(PROD_INST_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(), (lhs, inst) -> lhs.stream().reduce(inst.seed(), (a, b) -> uri(a.uriValue().mult(b.uriValue()))), uri(".")),
                  /*  instC(URI_SCHEME_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().scheme())),
                    instC(URI_HOST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().host())),*/
                    instC(PATH_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().path())),
                    /*   instC(URI_PORT_TID.dom(URI_TID).rng(INT_TID), lst(T(URI_TID)), (lhs, inst) -> jnt(lhs.uriValue().port())),*/
                    instC(Q_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().queryValue(inst.arg(0).uriValue(), fURI.class))),
                    instC(Q_INST_TID.dom(URI_TID).rng(REC_TID), lst(), (lhs, inst) -> rec(lhs.uriValue().queryMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue()))))),
                    instC(Q_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(REC_TID)), (lhs, inst) -> lhs.jvm(lhs.uriValue().queryMap(inst.arg(0).recValue().entrySet().stream().collect(Collectors.toMap(kv -> kv.getKey().uriValue().toString(), kv -> kv.getValue().uriValue().toString(), (a, b) -> b, LinkedHashMap::new))))),
                    instC(URI_C_TID.dom(URI_TID).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(jnt(lhs.uriValue().cV().min()), jnt(lhs.uriValue().cV().max()))),
                    instC(POW_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(INT_TID)), (lhs, inst) -> {
                        final int pow = inst.arg(0).intValue().intValue();
                        if (0 == pow) return lhs.jvm(f(""));
                        fURI u = f(".");
                        for (int i = 0; i < pow; i++) {
                            u = u.mult(lhs.uriValue());
                        }
                        return lhs.jvm(u);
                    }),
                    instC(SCHEME_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().scheme() == null ? noobj() : uri(lhs.uriValue().scheme())),
                    instC(SCHEME_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().scheme(inst.arg(0).uriValue().toString().isEmpty() ? null : inst.arg(0).uriValue().toString()))),
                    instC(HOST_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().host() == null ? noobj() : uri(lhs.uriValue().host())),
                    instC(HOST_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().host(inst.arg(0).uriValue().toString().isEmpty() ? null : inst.arg(0).uriValue().toString()))),
                    instC(PORT_INST_TID.dom(URI_TID).rng(INT_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().port() == -1 ? noobj() : jnt(lhs.uriValue().port())),
                    instC(PORT_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(INT_TID)), (lhs, inst) -> uri(lhs.uriValue().port(inst.arg(0).intValue().intValue()))),
                    instC(SELECT_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> Helper.selectUri(lhs.asUri(), inst.arg(0).uriValue())),
                    instC(WHERE_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> Helper.whereUri(lhs.asUri(), inst.arg(0).uriValue()) ? lhs : noobj())
                    // instC(UPDATE_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().update(inst.arg(0).uriValue())))
                    // GROUP
                    // UPDATE
            ));
        }

    }

    class Helper {
        public static boolean whereUri(final Uri lhs, final fURI filter) {
            if (filter.segments().size() < lhs.uriValue().segments().size() && !filter.hasPattern('#'))
                return false;
            for (int i = 0; i < filter.segments().size(); i++) {
                final String segment = filter.segments().get(i);
                if (segment.equals("#"))
                    return true;
                if (lhs.uriValue().segments().size() <= i)
                    return false;
                if (!lhs.uriValue().segments().get(i).equals(segment) && !segment.equals("+"))
                    return false;
            }
            return true;
        }

        public static Uri selectUri(final Uri lhs, final fURI selection) {
            String path = "";
            boolean all_found = false;
            for (int i = 0; i < selection.segments().size(); i++) {
                final String segment = selection.segments().get(i);
                if (segment.equals("#"))
                    all_found = true;
                if (!all_found && lhs.uriValue().segments().size() <= i)
                    return null;
                if (all_found || lhs.uriValue().segments().get(i).equals(segment) || segment.equals("+"))
                    path += "/" + lhs.uriValue().segments().get(i);
                else
                    return null;
            }
            return uri(lhs.uriValue().path(path));
        }
    }


}