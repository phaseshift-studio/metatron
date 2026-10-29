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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.form.AbstractfURI;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
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
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Uri extends Mono, Comparable<Uri> {

    Uri ONE = uri("");

    // Uri with coefficient {0}: noobj typed as a Uri, for a method that answers a Uri or nothing.
    // its payload is the zero address itself (the MUri constructor reads its payload)
    Uri URI_NOOBJ = new MUri(NOOBJ, URI_TID.zero(), null);

    static Uri uri_noobj() {
        return URI_NOOBJ;
    }

    static Uri uri0() {
        return (Uri) uri("").c(cInt.ZERO());
    }

    @Override
    Uri clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    fURI jvm();

    @Override
    default int compareTo(final Uri other) {
        return this.uriValue().compareTo(other.uriValue());
    }

    /*default Obj at(final Obj key) {
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
            return rec(this.uriValue().qMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), Helper.parseQ(kv.getValue()))));
        else
            throw MTronException.of("unknown uri component: %s", k);
    }*/

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

    default Uri one() {
        return ONE;
    }

    default Uri mult(final Uri rhs) {
        return this.equals(ONE) ? rhs : rhs.equals(ONE) ? this : this.jvm(this.uriValue().extend(rhs.uriValue())).c(c -> c.mult(rhs.c())).asUri();
    }

    default Uri plus(final Uri rhs) {
        if (this.uriValue().equals(rhs.uriValue()))
            return this.c(c -> c.plus(rhs.c())).asUri();
        return this.jvm(this.uriValue().path(AbstractfURI.mergePaths(this.uriValue().isEmpty() ? List.of("") : this.uriValue().path(), rhs.uriValue().isEmpty() ? List.of("") : rhs.uriValue().path(), cInt.C_ONE, cInt.C_ONE)));
    }

    /**
     * Carries a rhs uri's tid coefficient onto its first path segment as a {@code {n}atom} marker, so the mult/plus
     * concatenation keeps the multiplicity in the path ({@code a/b * {4}e} → {@code a/b/{4}e}). A one coefficient is
     * left alone.
     */
    private static fURI coefficientPrefix(final Uri rhs) {
        final fURI f = rhs.uriValue();
        final cInt c = rhs.tid().c();
        if (c.isOne() || f.path().isEmpty())
            return f;
        final java.util.List<String> path = new java.util.ArrayList<>(f.path());
        path.set(0, "{" + c + "}" + path.get(0));
        return fURI.of(f.scheme(), f.host(), f.port(), path, cInt.ONE(), f.poly(), f.qMap(), f.templates());
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
                throw MTronException.of("failed to expand template ${%s}: %s", exprStr, MTronException.translateMessage(e));
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
                // A segment may itself carry separators: a template parses `/users/${id}` as ONE segment. Splitting is
                // needed, but a segment that BEGINS with `/` carries the path's OWN leading separator, so its empty
                // first part must be dropped once the path is already absolute — otherwise the split contributes a
                // SECOND empty segment and every authority-bearing expansion renders as `api.com//users`.
                for (final String part : segment.split("/", -1)) {
                    normalizedPath.add(part);
                }
                // KNOWN CAUSE OF UriTest.testSelect/testWhere (6 rows): a template parses `/users/${id}` as ONE
                // segment, so splitting it here contributes a SECOND empty leading segment and every
                // authority-bearing expansion renders as `api.com//users`. Dropping that leading empty part fixes all
                // 6 — but it must be conditional on the template form, because the fURITest template rows
                // (`<http://api.com//${plus([d])}>`) DEPEND on the doubled form surviving the string round trip.
                // Two naive conditions were tried and each regressed those 10 rows; the rule needs both cases in it.
                // Trace evidence is in this task's history (bash-229): TMPL templatePath=[, /users/${>>id}].
            } else {
                normalizedPath.add(segment);
            }
        }
        // An authority implies an ABSOLUTE path: `scheme://host:port/path` separates authority from path with `/`,
        // and a template can spell one WITHOUT that slash — `http://api.com:${>>port}${>>version}` — which leaves the
        // path relative, so the rendering glues the port to the path (`:9000v1`). The marker goes back in here.
        // This is the THIRD site of one rule (also buildDatetimeUri and the rec round trip in UriTest.testSelect):
        // the general enforcement was tried in fURI.of and is DISABLED there — see the note in that file, it
        // double-prepends the template path — so until that is resolved the rule lives at its call sites.
        if (null != host && !normalizedPath.isEmpty() && !normalizedPath.getFirst().isEmpty())
            normalizedPath.add(0, "");
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
        private final static Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

        /*
         * WHAT REMAINS HERE, and why: two ROW-LESS `as` conversions (uri->int, uri->uri) -- no law-table row, hence
         * no claim and no inverse -- the has/split bodies, which read this type's PRIVATE REGEX_CACHE, and
         * uri->rec, whose body calls Uri.Helper.parseQ (package-private here), so the builder cannot import it yet.
         * THE SOUND uri->str DIRECTION IS GONE FROM HERE: a uri prints and the same uri parses back, so it is
         * declared WITH its inverse (str->uri) in mInsts.uris(); the LOSSY str->uri direction declares none. The
         * uri->rec ROW STAYS as the pair's one remaining claim, and it is the SOUND direction (a component rec
         * reads back as its uri) -- no row declares rec->uri any more.
         */
        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(URI_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> jnt(Integer.parseInt(lhs.uriValue().toString()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), null)),
                    instC(AS_INST_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
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
                                QPROC, lhsUri.qMap().isEmpty() ? noobj() : rec(lhsUri.qMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), Helper.parseQ(kv.getValue())))).c(c -> c.mult(lhs.c())));
                    }),
                    docWrap(instC(HAS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(STR_TID)), (lhs, inst) -> CommonUtil.findSafely(REGEX_CACHE.computeIfAbsent(inst.arg(0).strValue(), CommonUtil::guardPattern), lhs.uriValue().toString()) ? lhs : noobj()),
                            "a uri to check", "whether the domain matches arg regex", Map.of(jnt(0), "the regex for matching"), "check whether the lhs str matches the regex arg"),
                    docWrap(instC(HAS_INST_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> (lhs.uriValue().test(inst.arg(0).uriValue()) || CommonUtil.findSafely(REGEX_CACHE.computeIfAbsent(inst.arg(0).uriValue().toString(), CommonUtil::guardPattern), lhs.uriValue().toString())) ? lhs : noobj()),
                            "a uri to check", "whether the domain matches arg regex", Map.of(jnt(0), "the regex for matching"), "check whether the lhs str matches the regex arg"),
                    instC(SPLIT_INST_TID.dom(URI_TID).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(Arrays.stream(CommonUtil.splitSafely(REGEX_CACHE.computeIfAbsent(inst.arg(0).uriValue().toString(), CommonUtil::guardPattern), lhs.uriValue().toString())).map(MUri::uri)))
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
            if (!Machine.current().memory().hasSpaceFor(base))
                return noobj();
            // The real reference spine: stored uris sitting exactly `depth` below base
            // (e.g. fsSpace directories).  This is the common, non-synthesizing path.
            final List<fURI> uris = new ArrayList<>(IteratorUtil.stream(
                            Machine.current().memory().getSpaceFor(base).directReader().apply(pattern))
                    .map(IdObj::furi)
                    .filter(u -> relativeDepth(u, base) == depth)
                    .toList());
            // Synthesize the directory spine: when nothing sits exactly at `depth`, project
            // every uri deeper than `depth` onto its depth-`depth` prefix — the segment a
            // filesystem would materialize as a directory on the way to a file.  Read-time
            // only (nothing is written), so * on a synthesized uri still resolves to noobj.
            if (uris.isEmpty()) {
                IteratorUtil.stream(Machine.current().memory().getSpaceFor(base).directReader().apply(base.extend(fURI.Singleton.ALL)))
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
            if (value.isEmpty())
                return Obj.none();
            final Obj parsed = ObjmtronSerializer.parse(value);
            // the parser reports an unparseable literal as a fail::T VALUE, not a throw, so the
            // free-text fallback tests the result rather than catching
            return parsed.stream().anyMatch(Obj::isFail) ? str(value) : parsed;
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
                return Machine.read(target).isNoObj() ? noobj() : uri(target);
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
                            final Pattern pattern = REGEX_CACHE.computeIfAbsent(entryKeyString, CommonUtil::guardPattern);
                            if (CommonUtil.findSafely(pattern, k)) {
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