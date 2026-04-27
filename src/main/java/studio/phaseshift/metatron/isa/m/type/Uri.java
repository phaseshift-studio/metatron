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
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.Tokens.C;
import static studio.phaseshift.metatron.furi.fURI.Singleton.NOOBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.gte_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.is_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Rec.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Uri extends Mono, Ring.O<Uri>, Comparable<Uri> {

    Type URI_TYPE = Type.Builder.build().tid(URI_TID).vid(URI_TID).create();

    @Override
    Uri clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    fURI jvm();

    @Override
    default int compareTo(final Uri other) {
        return this.uriValue().compareTo(other.uriValue());
    }

    default Obj at(final Obj key) {
        final fURI k = key.uriValue();
        if (k.equals(f(SCHEME)))
            return uri(this.uriValue().scheme());
        else if (k.equals(f(HOST)))
            return uri(this.uriValue().host());
        else if (k.equals(f(PORT)))
            return jnt(this.uriValue().port());
        else if (k.equals(f(PATH)))
            return lst(this.uriValue().path().stream().map(MUri::uri).map(Obj::<Obj>as).toList());
        else if (k.equals(f(C)))
            return rec(
                    MIN, null == this.uriValue().c().min() ? noobj() : jnt((Long) this.uriValue().c().min()),
                    MAX, null == this.uriValue().c().max() ? noobj() : jnt((Long) this.uriValue().c().max()));
        else if (k.equals(f(QPROC)))
            return rec(this.uriValue().qMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue()))));
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

    default boolean hasTemplates() {
        return this.uriValue().hasTemplates();
    }

    default List<studio.phaseshift.metatron.util.Tuple.Pair<fURI.Component, Obj>> parsedTemplates() {
        return List.of();
    }

    @Override
    default Obj apply(final Obj lhs) {
        if (!this.hasTemplates())
            return Mono.super.apply(lhs);
        return uri(expandTemplate(this, lhs));
    }

    /**
     * Expand all template expressions in this URI by evaluating them against lhs.
     *
     * @param templateUri The Uri containing templates
     * @param lhs         The left-hand side object to apply templates against
     * @return A new fURI with all templates expanded
     */
    static fURI expandTemplate(final Uri templateUri, final Obj lhs) {
        final fURI template = templateUri.uriValue();
        final List<Tuple.Pair<fURI.Component, Obj>> parsedTemplates = templateUri.parsedTemplates();

        if (parsedTemplates.isEmpty())
            return template;

        final List<Tuple.Pair<fURI.Component, String>> rawTemplates = template.templates();
        String scheme = template.scheme();
        String host = template.host();
        // For port: if there's a PORT template, the raw port was ${expr}, not -1
        String portStr = null;
        for (var t : rawTemplates) {
            if (t.get0() == fURI.Component.PORT) {
                portStr = "${" + t.get1() + "}";
                break;
            }
        }
        if (portStr == null && template.port() != -1) {
            portStr = String.valueOf(template.port());
        }
        List<String> path = new ArrayList<>(templateUri.uriValue().path());
        Map<String, String> query = new LinkedHashMap<>(template.qMap());
        for (int i = 0; i < parsedTemplates.size(); i++) {
            final Tuple.Pair<fURI.Component, Obj> parsed = parsedTemplates.get(i);
            final fURI.Component component = parsed.get0();
            final Obj expr = parsed.get1();
            final String exprStr = rawTemplates.get(i).get1();
            try {
                final Obj result = expr.apply(lhs);
                final String replacement = coerceByComponent(result, component);
                final String templatePlaceholder = "${" + exprStr + "}";
                switch (component) {
                    case SCHEME ->
                            scheme = scheme != null ? scheme.replace(templatePlaceholder, replacement) : replacement;
                    case HOST -> host = host != null ? host.replace(templatePlaceholder, replacement) : replacement;
                    case PORT ->
                            portStr = portStr != null ? portStr.replace(templatePlaceholder, replacement) : replacement;
                    case PATH -> {
                        path.replaceAll(s -> s.replace(templatePlaceholder, replacement));
                    }
                    case QUERY -> {
                        Map<String, String> newQuery = new LinkedHashMap<>();
                        for (Map.Entry<String, String> entry : query.entrySet()) {
                            String key = entry.getKey().replace(templatePlaceholder, replacement);
                            String val = entry.getValue().replace(templatePlaceholder, replacement);
                            newQuery.put(key, val);
                        }
                        query = newQuery;
                    }
                    default -> {
                        // COEFFICIENT, POLY, AUTHORITY - handle gracefully
                    }
                }
            } catch (Exception e) {
                throw MTronException.of("Failed to expand template ${%s}: %s", exprStr, e.getMessage());
            }
        }

        // Construct new fURI with expanded values
        int finalPort = -1;
        if (portStr != null && !portStr.isEmpty()) {
            try {
                finalPort = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw MTronException.of("PORT template did not evaluate to integer: %s", portStr);
            }
        }
        // Normalize path: split any elements containing "/" so the path representation
        // matches what you'd get from parsing the URI string directly
        final List<String> normalizedPath = new ArrayList<>();
        for (final String segment : path) {
            if (segment.contains("/")) {
                for (final String part : segment.split("/", -1)) {
                    normalizedPath.add(part);
                }
            } else {
                normalizedPath.add(segment);
            }
        }
        return fURI.of(scheme, host, finalPort, normalizedPath, template.c(), template.poly(), query, null);
    }

    /**
     * Coerce evaluation result based on URI component type.
     *
     * @param result    The result of lhs.apply(expr)
     * @param component The URI component where this template appears
     * @return String representation appropriate for the component
     */
    static String coerceByComponent(final Obj result, final fURI.Component component) {
        return switch (component) {
            case PORT -> {
                // Port must be an integer
                if (result.isInt()) {
                    yield String.valueOf(result.intValue());
                }
                // Try to coerce from string
                if (result.isStr()) {
                    try {
                        Integer.parseInt(result.strValue());
                        yield result.strValue();
                    } catch (NumberFormatException e) {
                        // fall through to error
                    }
                }
                throw MTronException.of("PORT template must evaluate to Int, got %s: %s", result.tid(), result);
            }
            case QUERY -> {
                // Query parameters: Rec → k1=v1&k2=v2, otherwise toString
                if (result.isRec()) {
                    yield result.asRec().elements()
                            .map(kv -> objToString(kv.first()) + "=" + objToString(kv.second()))
                            .collect(Collectors.joining("&"));
                }
                yield objToString(result);
            }
            case PATH -> {
                if (result.isLst()) {
                    result.logger().warn("coercing Lst to PATH: {}", result);
                    yield result.lstValue().stream().map(Uri::objToString).collect(Collectors.joining("/"));
                } else
                    yield objToString(result);
            }
            case SCHEME, HOST, AUTHORITY -> {
                // Simple toString for these components
                if (result.isLst()) {
                    final String x = result.lstValue().stream().map(Uri::objToString).collect(Collectors.joining("/"));
                    yield x;
                } else
                    yield objToString(result);
            }
            case COEFFICIENT, POLY -> {
                // These are unlikely but handle gracefully
                yield objToString(result);
            }
        };
    }

    /**
     * Convert an Obj to its string representation for URI components.
     */
    static String objToString(final Obj obj) {
        if (obj.isStr()) return obj.strValue();
        if (obj.isInt()) return String.valueOf(obj.intValue());
        if (obj.isReal()) return String.valueOf(obj.realValue());
        if (obj.isBool()) return String.valueOf(obj.boolValue());
        if (obj.isUri()) return obj.uriValue().toString();
        return obj.toString();
    }


    final class UriType {
        private final static Map<String, Pattern> REGEX_CACHE = new HashMap<>();

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    //instC(SPLIT_INST_TID.dom(URI_TID).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(Arrays.stream(lhs.uriValue().toString().split(inst.arg(0).uriValue().toString())).map(MUri::uri))),
                    instC(AS_INST_TID.dom(URI_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> jnt(Integer.parseInt(lhs.uriValue().toString()), inst.arg(0).tid(), null)),
                    instC(AS_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue(), inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(URI_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.uriValue().toString(), inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(URI_TID).rng(REC_TID), lst(REC_TYPE), (lhs, inst) -> {
                        final fURI lhsUri = lhs.asUri().uriValue();
                        return rec(
                                SCHEME, lhsUri.scheme() == null ? noobj() : uri(lhsUri.scheme()),
                                HOST, lhsUri.host() == null ? noobj() : uri(lhsUri.host()),
                                PORT, lhsUri.port() == -1 ? noobj() : jnt(lhsUri.port()),
                                PATH, lhsUri.path().isEmpty() ? noobj() : lst(lhsUri.path().stream().map(MUri::uri)),
                                C, rec(MIN, null == lhsUri.c().min() ? noobj() : jnt(lhsUri.c().min()), MAX, null == lhsUri.c().max() ? noobj() : jnt(lhsUri.c().max())),
                                QPROC, lhsUri.qMap().isEmpty() ? noobj() : rec(lhsUri.qMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue())))));
                    }),
                    instC(REVERSE_INST_TID.dom(URI_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.jvm(lhs.uriValue().path(lhs.asUri().uriValue().path().reversed()))),
                    docWrap(instC(HAS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(STR_TID)), (lhs, inst) -> REGEX_CACHE.compute(inst.arg(0).strValue(), (k, v) -> null == v ? Pattern.compile(k) : v).matcher(lhs.uriValue().toString()).find() ? lhs : noobj()),
                            "a uri to check", "whether the domain matches arg regex", Map.of(jnt(0), "the regex for matching"), "check whether the lhs str matches the regex arg"),
                    docWrap(instC(HAS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> REGEX_CACHE.compute(inst.arg(0).uriValue().toString(), (k, v) -> null == v ? Pattern.compile(k) : v).matcher(lhs.uriValue().toString()).find() ? lhs : noobj()),
                            "a uri to check", "whether the domain matches arg regex", Map.of(jnt(0), "the regex for matching"), "check whether the lhs str matches the regex arg"),
                    instC(SPLIT_INST_TID.dom(URI_TID).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(Arrays.stream(lhs.uriValue().toString().split(inst.arg(0).uriValue().toString())).map(MUri::uri))),
                    instC(MERGE_INST_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.stream().map(Obj::uriValue).reduce((a, b) -> a.extend(inst.arg(0).uriValue()).extend(b)).orElse(f("noobj")))),
                   /* instC(RSHIFT_INST_TID.dom(URI_TID).rng(ALL.maybeSome()), lst(T(ALL.maybeSome())), (lhs, inst) ->
                            objs(inst.arg(0).stream().map(u -> {
                                if (u.isInt()) {
                                    return lhs.uriValue().segmentLength() > u.intValue().intValue() ? uri(lhs.uriValue().asRelativeNode().segments().get(u.intValue().intValue())) : noobj();
                                } else {
                                    final String component = u.uriValue().toString();
                                    final Object result = switch (component) {
                                        case SCHEME -> lhs.uriValue().scheme();
                                        case HOST -> lhs.uriValue().host();
                                        case PORT -> lhs.uriValue().port();
                                        case AUTHORITY -> lhs.uriValue().authority();
                                        case PATH -> lhs.uriValue().pathString();
                                        case C -> lst(jnt(lhs.uriValue().c().min()), jnt(lhs.uriValue().c().max()));
                                        case QSTRING -> lhs.uriValue().qMap().entrySet().stream()
                                                .map(kv -> rel(MObjFactory.single().toObjFromString(kv.getKey()), MObjFactory.single().toObjFromString(kv.getValue())))
                                                .collect(new CommonUtil.RecCollector());
                                        default -> noobj();
                                    };
                                    return result instanceof Obj ? (Obj) result :
                                            (null == result || Integer.valueOf(-1) == result ? noobj() :
                                                    (result instanceof Integer ? jnt((Integer) result) :
                                                            uri(result.toString())));
                                }
                            }))),*/
                    //  instC(LSHIFT_INST_TID.dom(URI_TID).rng(URI_TID), lst(isa_(T(INT_TID)).else_(jnt(1))), (lhs, inst) -> lhs.jvm(lhs.uriValue().pretract(inst.arg(0).intValue().intValue()))),
                    instC(MINUS_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().toString().replace(inst.arg(0).uriValue().toString(), ""))),
                    instC(PLUS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID.maybe())), (lhs, inst) -> lhs.jvm(lhs.uriValue().plus(inst.arg(0).uriValue()))),
                    instC(MULT_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID.maybe())), (lhs, inst) -> lhs.jvm(lhs.uriValue().mult(inst.arg(0).uriValue()))),
                    instC(SUM_INST_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Uri) a).plus((Uri) b)).uriValue()), uri(NOOBJ)),
                    instC(PROD_INST_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(), (lhs, inst) -> lhs.stream().reduce(inst.seed(), (a, b) -> uri(a.uriValue().mult(b.uriValue()))), uri(".")),
                  /*  instC(URI_SCHEME_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().scheme())),
                    instC(URI_HOST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().host())),*/
                    instC(PATH_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().pathString())),
                    /*   instC(URI_PORT_TID.dom(URI_TID).rng(INT_TID), lst(T(URI_TID)), (lhs, inst) -> jnt(lhs.uriValue().port())),*/
                    // instC(Q_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().qValue(inst.arg(0).uriValue().toString(), fURI.class))),
                    instC(Q_INST_TID.dom(URI_TID).rng(URI_TID), lst(REC_TYPE), (lhs, inst) -> lhs.jvm(lhs.uriValue().q((Map<String, String>) inst.arg(0).asRec().elements().collect(Collectors.toMap(kv -> kv.first().uriValue().toString(), kv -> "" + kv.second().jvm(), (a, b) -> b, LinkedHashMap::new))))),
                    //instC(LSHIFT_INST_TID.dom(URI_TID).rng(URI_TID), lst(REC_TYPE), (lhs, inst) -> uri(lhs.uriValue().q((Map<String, String>) inst.arg(0).asRec().elements().collect(Collectors.toMap(kv -> kv.first().uriValue().toString(), kv -> "" + kv.second().jvm(), (a, b) -> b, LinkedHashMap::new))))),
                    // instC(Q_INST_TID.dom(URI_TID).rng(REC_TID), lst(), (lhs, inst) -> rec(lhs.uriValue().qMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue()))))),
                    // TODO  instC(Q_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(REC_TID)), (lhs, inst) -> lhs.jvm(lhs.uriValue().qMap(inst.arg(0).recValue().entrySet().stream().collect(Collectors.toMap(kv -> kv.getKey().uriValue().toString(), kv -> kv.getValue().uriValue().toString(), (a, b) -> b, LinkedHashMap::new))))),
                    instC(URI_C_TID.dom(URI_TID).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(jnt((Long) lhs.uriValue().c().min()), jnt((Long) lhs.uriValue().c().max()))),
                    instC(POW_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(INT_TID)), (lhs, inst) -> {
                        final int pow = inst.arg(0).intValue().intValue();
                        if (0 == pow) return lhs.jvm(f(""));
                        fURI u = null;
                        for (int i = 0; i < pow; i++) {
                            u = null == u ? lhs.uriValue() : u.mult(lhs.uriValue());
                        }
                        return lhs.jvm(u);
                    }),
                    instC(SCHEME_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().hasScheme() ? uri(lhs.uriValue().scheme()) : noobj()),
                    instC(SCHEME_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().scheme(inst.arg(0).uriValue().toString().isEmpty() ? null : inst.arg(0).uriValue().toString()))),
                    instC(AUTHORITY_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().hasAuthority() ? uri(lhs.uriValue().authority()) : noobj()),
                    instC(HOST_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().hasHost() ? uri(lhs.uriValue().host()) : noobj()),
                    instC(HOST_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().host(inst.arg(0).uriValue().toString().isEmpty() ? null : inst.arg(0).uriValue().toString()))),
                    instC(PORT_INST_TID.dom(URI_TID).rng(INT_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().hasPort() ? jnt(lhs.uriValue().port()) : noobj()),
                    instC(PORT_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(INT_TID).predicate(is_(gte_(jnt(-1))).tryToInst())), (lhs, inst) -> uri(lhs.uriValue().port(inst.arg(0).intValue().intValue()))),
                    instC(SELECT_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> Helper.selectUri(lhs.asUri(), inst.arg(0).uriValue())),
                    instC(WHERE_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> Helper.whereUri(lhs.asUri(), inst.arg(0).uriValue()) ? lhs : noobj())
                    // instC(UPDATE_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().update(inst.arg(0).uriValue())))
                    // GROUP
                    // UPDATE
            ));
        }

    }

    final class Helper {

        private Helper() {
            // do nothing
        }

        public static Obj rshiftUri(final Uri lhs, final Inst inst) {
            return objs(inst.arg(0).stream().map(u -> {
                if (u.isInt()) {
                    return lhs.uriValue().segmentLength() > u.intValue().intValue() ? uri(lhs.uriValue().asRelativeNode().segments().get(u.intValue().intValue())) : noobj();
                } else {
                    final String component = u.uriValue().toString();
                    final Object result = switch (component) {
                        case SCHEME -> lhs.uriValue().scheme();
                        case HOST -> lhs.uriValue().host();
                        case PORT -> lhs.uriValue().port();
                        case AUTHORITY -> lhs.uriValue().authority();
                        case PATH -> lhs.uriValue().pathString();
                        case C -> lst(jnt(lhs.uriValue().c().min()), jnt(lhs.uriValue().c().max()));
                        case QPROC -> lhs.uriValue().qMap().entrySet().stream()
                                .map(kv -> rel(MObjFactory.single().toObjFromString(kv.getKey()), MObjFactory.single().toObjFromString(kv.getValue())))
                                .collect(new CommonUtil.RecCollector());
                        default -> noobj();
                    };
                    return result instanceof Obj ? (Obj) result :
                            (null == result || Integer.valueOf(-1) == result ? noobj() :
                                    (result instanceof Integer ? jnt((Integer) result) :
                                            uri(result.toString())));
                }
            }));
        }


        public static boolean whereUri(final Uri lhs, final fURI filter) {
            if (filter.path().size() < lhs.uriValue().path().size() && !filter.hasPattern("#"))
                return false;
            for (int i = 0; i < filter.path().size(); i++) {
                final String segment = filter.path().get(i);
                if (segment.equals("#"))
                    return true;
                if (lhs.uriValue().path().size() <= i)
                    return false;
                if (!lhs.uriValue().path().get(i).equals(segment) && !segment.equals("+"))
                    return false;
            }
            return true;
        }

        public static Uri selectUri(final Uri lhs, final fURI selection) {
            String path = "";
            boolean all_found = false;
            for (int i = 0; i < selection.path().size(); i++) {
                final String segment = selection.path().get(i);
                if (segment.equals("#"))
                    all_found = true;
                if (!all_found && lhs.uriValue().path().size() <= i)
                    return null;
                if (all_found || lhs.uriValue().path().get(i).equals(segment) || segment.equals("+"))
                    path += "/" + lhs.uriValue().path().get(i);
                else
                    return null;
            }
            return uri(lhs.uriValue().path(path));
        }
    }


}