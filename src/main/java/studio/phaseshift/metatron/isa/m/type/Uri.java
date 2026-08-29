/*
 * metatron: a distributed virtual machine and language
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
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
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

    public static Uri uri0() {
        return uri("").zero();
    }

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
        else if (k.equals(f(SUB)))
            return uri(this.uriValue().subdomain());
        else if (k.equals(f(AUTHORITY)))
            return uri(f(this.uriValue().host()).port(this.uriValue().port()));
        else if (k.equals(f(HOST)))
            return uri(this.uriValue().host());
        else if (k.equals(f(PORT)))
            return jnt(this.uriValue().port());
        else if (k.equals(f(PATH)))
            return lst(this.uriValue().path().stream().map(MUri::uri).map(Obj::<Obj>as).toList());
        else if (k.equals(f(COEFF)))
            return rec(
                    MIN, null == this.uriValue().c().min() ? noobj() : jnt((Long) this.uriValue().c().min()),
                    MAX, null == this.uriValue().c().max() ? noobj() : jnt((Long) this.uriValue().c().max()));
        else if (k.equals(f(QPROC)))
            return rec(this.uriValue().qMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), ObjmtronSerializer.single().read(kv.getValue()))));
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
            return this.uriValue().test(obj.uriValue()) || this.uriValue().big().test(obj.uriValue().big());
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
                    yield result.lstValue().stream().map(Uri::objToString).collect(Collectors.joining("/"));
                } else
                    yield objToString(result);
            }
            case SCHEME, SUB, HOST, AUTHORITY -> {
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
                    instC(AS_INST_TID.dom(URI_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> jnt(Integer.parseInt(lhs.uriValue().toString()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), null)),
                    instC(AS_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(URI_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.uriValue().toString(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(URI_TID).rng(REC_TID), lst(REC_TYPE), (lhs, inst) -> {
                        final fURI lhsUri = lhs.asUri().uriValue();
                        return rec(
                                SCHEME, lhsUri.scheme() == null ? noobj() : uri(lhsUri.scheme()),
                                SUB, lhsUri.subdomain() == null ? noobj() : uri(lhsUri.subdomain()),
                                HOST, lhsUri.host() == null ? noobj() : uri(lhsUri.host()),
                                PORT, lhsUri.port() == -1 ? noobj() : jnt(lhsUri.port()),
                                AUTHORITY, lhsUri.authority() == null ? noobj() : uri(lhsUri.authority()),
                                PATH, lhsUri.path().isEmpty() ? noobj() : lst(lhsUri.path().stream().map(MUri::uri)),
                                COEFF, rec(MIN, null == lhsUri.c().min() ? noobj() : jnt(lhsUri.c().min()), MAX, null == lhsUri.c().max() ? noobj() : jnt(lhsUri.c().max())),
                                QPROC, lhsUri.qMap().isEmpty() ? noobj() : rec(lhsUri.qMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), Helper.parseQ(kv.getValue()))))).c(c -> c.mult(lhs.c()));
                    }),
                    instC(REVERSE_INST_TID.dom(URI_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.jvm(lhs.uriValue().path(lhs.asUri().uriValue().path().reversed()))),
                    docWrap(instC(HAS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(STR_TID)), (lhs, inst) -> REGEX_CACHE.compute(inst.arg(0).strValue(), (k, v) -> null == v ? Pattern.compile(k) : v).matcher(lhs.uriValue().toString()).find() ? lhs : noobj()),
                            "a uri to check", "whether the domain matches arg regex", Map.of(jnt(0), "the regex for matching"), "check whether the lhs str matches the regex arg"),
                    docWrap(instC(HAS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> (lhs.uriValue().test(inst.arg(0).uriValue()) || REGEX_CACHE.compute(inst.arg(0).uriValue().toString(), (k, v) -> null == v ? Pattern.compile(k) : v).matcher(lhs.uriValue().toString()).find()) ? lhs : noobj()),
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
                    // instC(AUTHORITY_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().hasAuthority() ? uri(lhs.uriValue().authority()) : noobj()),
                    instC(HOST_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().hasHost() ? uri(lhs.uriValue().host()) : noobj()),
                    instC(HOST_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().host(inst.arg(0).uriValue().toString().isEmpty() ? null : inst.arg(0).uriValue().toString()))),
                    instC(PORT_INST_TID.dom(URI_TID).rng(INT_TID.maybe()), lst(), (lhs, inst) -> lhs.uriValue().hasPort() ? jnt(lhs.uriValue().port()) : noobj()),
                    instC(PORT_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(INT_TID).predicate(is_(gte_(jnt(-1))).tryToInst())), (lhs, inst) -> uri(lhs.uriValue().port(inst.arg(0).intValue().intValue()))),
                    instC(SELECT_INST_TID.dom(URI_TID).rng(URI_TID), lst(REC_TYPE), (lhs, inst) -> {
                        final Uri.Helper.UriProjected projected = Uri.Helper.project(lhs.uriValue(), inst.arg(0).orElse(rec0()));
                        return uri(fURI.of(
                                projected.scheme(),
                                projected.host(),
                                projected.port(),
                                projected.path(),
                                lhs.uriValue().c(),
                                lhs.uriValue().poly(),
                                projected.query(),
                                List.of()
                        ), lhs.tid(), lhs.vid());
                    }),
                    instC(WHERE_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(REC_TYPE), (lhs, inst) -> {
                        try {
                            Uri.Helper.UriProjected projected = Uri.Helper.project(lhs.uriValue(), inst.arg(0).orElse(rec0()));
                            final fURI original = lhs.uriValue();
                            // STRICT CHECK: For where(), if the projection result differs from origin
                            // (e.g., something was replaced by 'none' and removed), it means the
                            // target does not match this specific projected profile exactly.
                            if (!Objects.equals(original.scheme(), projected.scheme()) ||
                                    !Objects.equals(original.host(), projected.host()) ||
                                    original.port() != projected.port() ||
                                    !Objects.equals(original.path(), projected.path()) ||
                                    !Objects.equals(original.qMap(), projected.query())) {
                                return noobj();
                            }
                            return lhs;
                        } catch (ProjectionFailureException e) {
                            return noobj();
                        }
                    })
            ));
        }

    }

    final class Helper {

        private Helper() {
            // do nothing
        }

        /**
         * Enumerate the uris matching {@code pattern}, keeping only those at exactly
         * {@code depth} below {@code base} — the walk returns depth-N leaves, matching
         * the {@code >>.>>.>>} broadcast, so {@code >> N} is referentially the chain.
         */
        private static Obj readChildUris(final fURI pattern, final fURI base, final int depth) {
            // Read the space's RAW reader (real stored children only).  The resolving read
            // (resolveRead) fabricates child uris via locateBasePoly/unrollPoly when the direct
            // read is empty.  The walk never invents an address from nothing, but when no uri
            // sits exactly at `depth` it falls back to projecting deeper uris onto their
            // depth-`depth` prefix — the implicit "directory spine" a flat space lacks.  When
            // no space supports the base uri, the walk is simply empty — not an error.
            if (!Router.global().hasSpaceFor(base))
                return noobj();
            // The real reference spine: stored uris sitting exactly `depth` below base
            // (e.g. fsSpace directories).  This is the common, non-synthesizing path.
            final List<fURI> uris = new ArrayList<>(IteratorUtil.stream(
                            Router.global().getSpaceFor(base).directReader().apply(pattern))
                    .map(IdObj::furi)
                    .filter(u -> relativeDepth(u, base) == depth)
                    .toList());
            // Synthesize the directory spine: when nothing sits exactly at `depth`, project
            // every uri deeper than `depth` onto its depth-`depth` prefix — the segment a
            // filesystem would materialize as a directory on the way to a file.  Read-time
            // only (nothing is written), so * on a synthesized uri still resolves to noobj.
            if (uris.isEmpty()) {
                IteratorUtil.stream(Router.global().getSpaceFor(base).directReader().apply(base.extend(fURI.Singleton.ALL)))
                        .map(IdObj::furi)
                        .filter(u -> relativeDepth(u, base) > depth)
                        .map(u -> u.retract(relativeDepth(u, base) - depth))
                        .forEach(uris::add);
            }
            // Preserve the base uri's absoluteness and branchness on every result — the
            // walk must not drift between relative/absolute or node/branch forms as it
            // reads real children or synthesizes the directory spine.
            return objs(uris.stream()
                    .map(u -> base.isRelative() ? u.asRelative() : u.asAbsolute())
                    .map(u -> base.isBranch() ? u.asBranch() : u.asNode())
                    .distinct()
                    .map(MUri::uri));
        }

        /**
         * A query value is stored as a raw string but is an mtron literal — parse it back to
         * its native type (1 → int::1, abc → uri:abc, !*test → the auto-from ref).  Fall back to
         * a plain str for anything that isn't a valid literal (free-text query values).
         */
        static Obj parseQ(final String value) {
            try {
                return value.isEmpty() ? Obj.none() : ObjmtronSerializer.parse(value);
            } catch (final Exception e) {
                return str(value);
            }
        }

        private static int relativeDepth(final fURI child, final fURI base) {
            // The path lst carries the leading slash as a leading "" (["","a","b"] is /a/b) —
            // drop it from both before comparing, so relative depth is plain segment counting.
            final List<String> cp = child.path().stream().filter(s -> !s.isEmpty()).toList();
            final List<String> bp = base.path().stream().filter(s -> !s.isEmpty()).toList();
            int i = 0;
            while (i < bp.size() && i < cp.size() && cp.get(i).equals(bp.get(i))) i++;
            return cp.size() - i;
        }

        public static Obj rshiftUri(final Uri lhs, final Obj arg) {
            if (arg.isNoObj()) {
                // uri >> — the edge: the child uris emanating from this uri, obj-less.
                // Reference-side navigation: the additive enumeration of the uri's children,
                // no referents resolved.  e.g. a/b/c >> = {a/b/c/e, a/b/c/g}.
                return readChildUris(lhs.uriValue().extend("+/"), lhs.uriValue(), 1);
            }
            if (arg.isInt()) {
                // uri >> <int> — the walk: descend exactly <int> levels.  >> 0 is the
                // identity (zero compositions of the edge) — the uri itself.
                final int depth = arg.intValue().intValue();
                if (depth == 0)
                    return uri(lhs.uriValue());
                final StringBuilder pattern = new StringBuilder();
                for (int i = 0; i < depth; i++) {
                    if (i > 0) pattern.append("/");
                    pattern.append("+");
                }
                return readChildUris(lhs.uriValue().extend(pattern.toString()), lhs.uriValue(), depth);
            }
            if (arg.isUri()) {
                final fURI argUri = arg.uriValue();
                if (argUri.hasPattern()) {
                    // uri >> <pattern> — the walk: enumerate every uri matching lhs + pattern.
                    final int depth = (int) argUri.asNode().path().stream().filter("+"::equals).count();
                    return readChildUris(lhs.uriValue().extend(argUri.toString()), lhs.uriValue(), depth);
                }
                // uri >> <uri> — navigate to a child: a/b/c >><2> => a/b/c/2 — but only a
                // real child, never a fabricated address.  Uri components (scheme, host, …)
                // live in rec space, not uri space — use .as(rec::T)>>component for those.
                final fURI target = lhs.uriValue().extend(argUri.toString());
                return Router.readFromSpace(target).isNoObj() ? noobj() : uri(target);
            }
            return noobj();
        }

        // Use the same singleton exception from StrProjectionHelper or a shared one
        private static final Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

        public record UriProjected(String scheme, String host, int port, List<String> path, Map<String, String> query) {
        }

        public static UriProjected project(final fURI lhsURI, final Obj rulesObj) {
            if (rulesObj == null || rulesObj.isNoObj()) {
                return new UriProjected(lhsURI.scheme(), lhsURI.host(), lhsURI.port(), lhsURI.path(), lhsURI.qMap());
            }

            // 1. Basic Components: Scheme & Host
            String scheme = lhsURI.scheme();
            String host = lhsURI.host();
            final Rec rulesRec = rulesObj.asRec();
            if (rulesRec.has(SCHEME)) {
                Obj res = rulesRec.at(SCHEME).apply(str(scheme));
                // Use a helper to check for actual failures vs purposeful 'none'
                if (res == null || (res.isNothing() && !rulesRec.at(SCHEME).isNone()))
                    throw ProjectionFailureException.instance();
                scheme = Str.Helper.cleanString(res);
            }
            if (rulesRec.has(HOST)) {
                Obj res = rulesRec.at(HOST).apply(str(host));
                if (res == null || (res.isNothing() && !rulesRec.at(HOST).isNone()))
                    throw ProjectionFailureException.instance();
                host = Str.Helper.cleanString(res);
            }

            // 2. Port
            int port = lhsURI.port();
            if (rulesRec.has(PORT)) {
                Obj res = rulesRec.at(PORT).apply(jnt(port));
                if (res == null || (res.isNothing() && !rulesRec.at(PORT).isNone()))
                    throw ProjectionFailureException.instance();
                String newPortStr = Str.Helper.cleanString(res);
                if (CommonUtil.isInt(newPortStr)) {
                    port = Integer.parseInt(newPortStr);
                } else if (!res.isNone()) { // Only throw error if it wasn't intentionally 'none'
                    throw MTronException.of("unable to convert to a port value: %s", newPortStr);
                }
            }

            // 3. Path
            List<String> newPath = null;
            if (rulesRec.has(PATH)) {
                final Lst pathSegments = lst((List) lhsURI.path().stream().map(MStr::str).toList());
                final Obj pathResult = rulesRec.at(PATH).apply(pathSegments);
                if (pathResult == null || (pathResult.isNothing() && !rulesRec.at(PATH).isNone()))
                    throw ProjectionFailureException.instance();
                newPath = pathResult.isLst() ?
                        pathResult.elements().map(Str.Helper::cleanString).toList() :
                        Arrays.asList(Str.Helper.cleanString(pathResult).split("/"));
            } else {
                newPath = lhsURI.path();
            }
            if (rulesRec.has(NAME)) {
                if (null == newPath)
                    newPath = new ArrayList<>(lhsURI.path());
                final Str nameSegment = str(lhsURI.name());
                final Obj nameResult = rulesRec.at(NAME).apply(nameSegment);
                if (nameResult == null || (nameResult.isNothing() && !rulesRec.at(NAME).isNone()))
                    throw ProjectionFailureException.instance();
                newPath.removeLast();
                newPath.add(nameResult.strValue());
            }
            // 4. Query: Predicate-based matching
            Map<String, String> queryMap = new HashMap<>(lhsURI.qMap());
            if (rulesRec.has(QPROC)) {
                final Obj qRules = rulesRec.at(QPROC);
                if (qRules.isRec()) {
                    final Map<String, String> nextQMap = new HashMap<>();
                    queryMap.forEach((k, v) -> {
                        Obj matchingRule = null;
                        for (Map.Entry<Obj, Obj> entry : qRules.asRec().recValue().entrySet()) {
                            final String entryKeyString = Str.Helper.cleanString(entry.getKey());
                            final Pattern pattern = REGEX_CACHE.computeIfAbsent(entryKeyString, Pattern::compile);
                            if (pattern.asPredicate().test(k)) {
                                matchingRule = entry.getValue();
                                break;
                            }
                        }

                        if (null != matchingRule) {
                            if (!matchingRule.isNone()) {
                                Obj res = matchingRule.apply(uri(v));
                                // If an instruction is called and returns noobj, that's a failure.
                                if (res == null || res.isNothing())
                                    throw ProjectionFailureException.instance();
                                nextQMap.put(k, Str.Helper.cleanString(res));
                            }
                            // Note: matchingRule.isNone() is handled by simply NOT adding it to nextQMap (removal)
                        } else {
                            nextQMap.put(k, v);
                        }
                    });
                    queryMap = nextQMap;
                } else {
                    Obj res = qRules.apply(str(lhsURI.qString()));
                    if (res == null || (res.isNothing() && !qRules.isNone()))
                        throw ProjectionFailureException.instance();
                    queryMap = f("?" + Str.Helper.cleanString(res)).qMap();
                }
            }
            return new UriProjected(scheme, host, port, newPath, queryMap);
        }
    }
}