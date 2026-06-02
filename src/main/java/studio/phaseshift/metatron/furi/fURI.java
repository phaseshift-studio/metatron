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

package studio.phaseshift.metatron.furi;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.form.*;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.COEFFICIENT;
import static studio.phaseshift.metatron.Tokens.CONSTQ;
import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.PATH;
import static studio.phaseshift.metatron.Tokens.POLY;
import static studio.phaseshift.metatron.Tokens.PORT;
import static studio.phaseshift.metatron.Tokens.QUERY;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.Tokens.SCHEME;
import static studio.phaseshift.metatron.Tokens.T;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface fURI extends Cloneable, Ring<fURI>, Comparable<fURI>, Predicate<fURI> {

    /**
     * URI components that can contain template expressions.
     * Used to identify which part of the URI a template belongs to for proper coercion during expansion.
     */
    enum Component {
        SCHEME,      // URI scheme (e.g., http, https, file)
        HOST,        // Host name or IP address
        PORT,        // Port number (must coerce to integer)
        AUTHORITY,   // Combined user-info, host, and port
        PATH,        // Path segments (can have multiple templates)
        QUERY,       // Query parameters (coerce to k=v pairs)
        COEFFICIENT, // fURI coefficient {min,max}
        POLY         // Polynomial type annotation [type=>type]
    }

    default boolean classAgnosticEquals(final Object other) {
        if (other instanceof fURI)
            return this.equals(other);
        if (other instanceof Uri)
            return this.equals(((Uri) other).uriValue());
        if (other instanceof String)
            return this.toString().equals(other);
        throw MTronException.of("unable to compare %s to %s", this, other);
    }

    static boolean isPattern(final String piece) {
        return piece.equals("#") || piece.equals("+");
    }

    static boolean isOnePattern(final String piece) {
        return piece.equals("+");
    }

    static boolean isAllPattern(final String piece) {
        return piece.equals("#");
    }

    static boolean manyMatches(final List<fURI> firstList, final List<fURI> secondList) {
        if (firstList.size() != secondList.size())
            return false;
        for (int i = 0; i < firstList.size(); i++) {
            if (!firstList.get(i).basePath().test(secondList.get(i).basePath()))
                return false;
        }
        return true;
    }

    ////////////////////////////////////////
    // URI TEMPLATE SUPPORT (${expr} syntax)
    ////////////////////////////////////////

    /**
     * Check if this URI contains template expressions (e.g., ${var}, ${+10}, ${[a=>b]})
     * Uses ${...} syntax to signal arbitrary Metatron code evaluation.
     * Distinguished from fURI coefficients: {?}, {*}, {+}, {0}, {1,3}, etc.
     *
     * @return true if this fURI contains at least one ${...} template expression
     */
    default boolean hasTemplates() {
        return null != templates() && !templates().isEmpty();
    }

    /**
     * Extract all template expressions from this URI with their component locations.
     * Each template is a pair of (Component, expression_string) where the expression
     * is the raw Metatron code inside ${...} (without the delimiters).
     * <p>
     * Templates are parsed at the fURI level (structural) but evaluated at the Uri level (monadic).
     * The Component enum indicates which part of the URI the template belongs to, enabling
     * context-aware coercion during expansion (e.g., PORT→int, QUERY→k=v pairs).
     *
     * @return List of template pairs, empty if no templates present
     */
    default List<Tuple.Pair<Component, String>> templates() {
        return List.of();
    }

    default fURI segments(final List<String> segments) {
        final List<String> newPath = new ArrayList<>(segments);
        if (!this.path().isEmpty()) {
            if (this.path().getFirst().isEmpty()) {
                if (!newPath.getFirst().isEmpty())
                    newPath.addFirst("");
            } else if (newPath.getFirst().isEmpty())
                newPath.removeFirst();
            if (this.path().getLast().isEmpty()) {
                if (!newPath.getLast().isEmpty())
                    newPath.addLast("");
            } else if (newPath.getLast().isEmpty())
                newPath.removeLast();
        }
        return this.path(newPath);
    }


    default fURI removeSubpath(final fURI subpath) {
        String newPath = this.toString();
        return Singleton.of(newPath.replace(subpath.asBranch().toString(), Tokens.EMPTY));
    }

    default Uri toUri(final boolean schemeType) {
        final String scheme = this.scheme();
        return schemeType && null != scheme ?
                uri(this.scheme(null), Singleton.of(scheme)) :
                uri(this);
    }

    default fURI noQ() {
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), Map.of(), this.templates());
    }

    default boolean hasAuthority() {
        return null != this.host() && -1 != this.port();
    }

    default boolean isGeneric() {
        if (this.hasScheme() || this.hasAuthority() || this.path().isEmpty())
            return false;
        if (this.segmentLength() == 1 && (this.path().getFirst().equals("#") || this.path().getFirst().equals("+")))
            return false;
        boolean hasCapitalGeneric = false;
        for (final String seg : this.path()) {
            if (!seg.isEmpty() && seg.chars().allMatch(Character::isUpperCase))
                hasCapitalGeneric = true;
            if (seg.chars().anyMatch(c -> c != '#' && c != '+' && !Character.isAlphabetic(c) || Character.isLowerCase(c)))
                return false;
        }
        return hasCapitalGeneric;
    }

    default boolean hasAnchor() {
        if (this.hasScheme())
            return this.scheme().startsWith("@");
        else if (this.hasHost())
            return this.host().startsWith("@");
        else if (!this.segments().isEmpty())
            return this.segments().getFirst().startsWith("@");
        return false;
    }

    default fURI stripAnchor() {
        return this.hasAnchor() ? f(this.toString().substring(1)) : this;
    }

    default fURI addAnchor() {
        return this.hasAnchor() ? this : f("@" + this.toString());
    }

    default boolean bimatches(final fURI other) {
        return this.test(other) || other.test(this);
    }

    fURI resolve();

    default fURI resolve(final Map<fURI, fURI> generics) {
        final fURI cless = this.one();
        //Graphitty.log(this).trace("resolving generics: %s", generics);
        if (cless.isGeneric()) {
            fURI lhs = cless.basePath().isGeneric() ?
                    cless.basePath().path(cless.basePath().path().stream().map(s -> generics.computeIfAbsent(Singleton.f(s), k -> Singleton.f(s)).toString()).reduce("", (a, b) -> a + "/" + b).substring(1)) :
                    cless.basePath();
            if (cless.hasDom())
                lhs = cless.dom().isGeneric() ?
                        lhs.dom(cless.dom().path(cless.dom().path().stream().map(s -> generics.computeIfAbsent(Singleton.f(s), k -> Singleton.f(s)).toString()).reduce("", (a, b) -> a + "/" + b).substring(1))) :
                        lhs.dom(cless.dom());
            if (cless.hasRng())
                lhs = cless.rng().isGeneric() ?
                        lhs.rng(cless.rng().path(cless.rng().path().stream().map(s -> generics.computeIfAbsent(Singleton.f(s), k -> Singleton.f(s)).toString()).reduce("", (a, b) -> a + "/" + b).substring(1))) :
                        lhs.rng(cless.rng());
            //Graphitty.log(this).trace("generics after resolution: %s", generics);
            return lhs.qString(this.hasQ() ? this.qString() : null).c(this.c());
        } else {
            return this;
        }
    }

    String scheme();

    fURI scheme(final String scheme);

    default boolean hasScheme() {
        return null != this.scheme();
    }

    default fURI big() {
        if (!Router.loaded())
            return this;
        final fURI temp = this.hasPoly() ? this.poly(this.poly().stream().map(p -> Router.global().redirect(Singleton.f(p), true)).map(fURI::toString).toList()) : this;
        return Router.global().redirect(temp, true);
    }

    default fURI small() {
        if (!Router.loaded())
            return this;
        final fURI temp = this.hasPoly() ? this.poly(this.poly().stream().map(p -> Router.global().redirect(Singleton.f(p), false)).map(fURI::toString).toList()) : this;
        return Router.global().redirect(temp, false);
    }

    default boolean isEmpty() {
        return !this.hasScheme() && !this.hasHost() && !this.hasPort() && this.path().isEmpty() && !this.hasQ();
    }

    default String authority() {
        if (this.hasHost())
            return this.hasPort() ? this.host() + ":" + this.port() : this.host();
        return null;
    }

    String host();

    fURI host(final String host);

    default boolean hasHost() {
        return this.host() != null;
    }

    int port();

    default boolean hasPort() {
        return this.port() != -1;
    }

    fURI port(final int port);

    List<String> path();

    fURI path(final List<String> path);

    fURI path(final String path);

    String pathString();

    default String name() {
        return this.path().isEmpty() ? Tokens.EMPTY : this.path().getLast();
    }

    boolean test(final fURI lhs);

    fURI extend(final String segment);

    default fURI extend(final fURI segments) {
        return this.extend(null == segments ? null : segments.toString());
    }

    fURI head(final int steps);

    fURI tail(final int steps);

    fURI retract(final int steps);

    fURI retractPattern();

    fURI retract(final String segment);

    fURI prepend(final String segment);

    fURI pretract(final String segment);

    fURI pretract(final int steps);

    /*default fURI removePrefix(final fURI prefix) {
        if (null == prefix)
            return this;
        final String newPath = this.toString();
        final String pre = prefix.toString();
        //return new fURI(newPath.startsWith(prefix.toString()) ? newPath.substring(prefix.send ? prefix.toString().length() +1 : prefix.toString().length()) : newPath);
        if (!newPath.startsWith(pre))
            return this;
        final fURI newURI = Singleton.of(newPath.substring(pre.length() + (newPath.charAt(pre.length()) == '/' ? 1 : 0)));
        return newURI;
    }*/

    default fURI removePrefix(final fURI prefix) {
        if (null == prefix)
            return this;
        if (prefix.hasPattern()) {
            fURI running = this;
            while (!running.isEmpty() && running.segmentLength() > 0) {
                if (running.bimatches(prefix))
                    return this.removePrefix(running);
                running = running.isBranch() ? running.asNode() : running.retract(1).asBranch();
            }
            return this;
        } else {
            final String newPath = this.toString();
            final String pre = prefix.toString();
            //return new fURI(newPath.startsWith(prefix.toString()) ? newPath.substring(prefix.send ? prefix.toString().length() +1 : prefix.toString().length()) : newPath);
            if (!newPath.startsWith(pre))
                return this;
            if (f(pre).equals(this))
                return f("");
            final fURI newURI = Singleton.of(newPath.substring(pre.length() + (newPath.charAt(pre.length()) == '/' ? 1 : 0)));
            // final fURI newURI = f(newPath.substring(pre.length()));
            return newURI;
        }
    }

    default boolean hasPoly() {
        return null != this.poly() && !this.poly().isEmpty();
    }

    default boolean hasPrefix(final String prefix) {
        return this.hasPrefix(f(prefix));
    }

    boolean hasPrefix(final fURI prefix);

    boolean hasPostfix(final String postfix);

    default boolean hasPostfix(final fURI postfix) {
        return this.hasPostfix(postfix.toString());
    }

    boolean hasPattern();

    boolean hasPattern(final String pattern);

    fURI basePath();

    List<String> poly();

    default Optional<Poly<?, ?>> polyParsed() {
        // if (null != this.parsedPoly)
        //    return this.parsedPoly;
        if (!this.hasPoly()) return Optional.empty();
        final List<String> poly = this.poly();
        if (poly.size() == 1) {
            if (poly.getFirst().trim().equals(","))
                return Optional.of(lst0());
            else if (poly.getFirst().trim().equals("=>"))
                return Optional.of(rec0());
        }
        if (poly.getFirst().contains("=>")) {
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            for (final String s : poly) {
                final String[] kv = s.split("=>");
                if (kv.length != 2)
                    throw MTronException.of("invalid rec type poly %s", s);
                map.put(uri(f(kv[0].trim()).big()), T(f(kv[1].trim()).big()));
            }
            return Optional.of(rec(map));
        } else {
            final List<Obj> list = new ArrayList<>();
            for (final String s : poly) {
                list.add(T(f(s.trim()).big()));
            }
            return Optional.of(lst(list));
        }
    }

    int pathLength();


    List<String> segments();

    default String segments(final int index, final String defaultSegment) {
        return this.segmentLength() > index ? this.segments().get(index) : defaultSegment;
    }

    int segmentLength();

    default fURI asRelativeNode() {
        return this.asRelative().asNode();
    }

    fURI poly(final List<String> poly);

    fURI neg();

    fURI mult(final fURI other);

    fURI plus(final fURI other);

    cInt c();

    fURI c(final cInt coefficient);

    default fURI c(final Function<cInt, cInt> func) {
        return this.c(func.apply(this.c()));
    }

    fURI dom();

    fURI dom(final fURI dom);

    default boolean hasDom() {
        return this.hasQ(DOM);
    }

    default boolean hasRng() {
        return this.hasQ(RNG);
    }

    fURI rng();

    fURI rng(final fURI rng);

    String qString();

    Map<String, String> qMap();

    fURI q(final Map<String, String> query);

    default fURI qLess() {
        return this.q(Map.of());
    }

    default fURI qLessExceptDomRng() {
        return this.qLess(DOM, RNG);
    }

    default fURI qLess(final String... except) {
        if (!this.hasQ())
            return this;
        return this.q(this.qMap()
                .entrySet()
                .stream()
                .filter(kv -> Stream.of(except).anyMatch(e -> e.equals(kv.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b)));
    }

    fURI removeQ(final String key);

    default fURI removeQ(final fURI key) {
        return this.removeQ(key.toString());
    }

    default fURI qString(final String query) {
        if (null == query || query.isEmpty())
            return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), Map.of(), this.templates());
        else {
            // Extract and merge any templates from the query string
            List<Tuple.Pair<Component, String>> mergedTemplates = null;
            if (query.contains("${")) {
                mergedTemplates = new ArrayList<>(this.templates());
                final Matcher m = Singleton.MERGE_PATTERN.matcher(query);
                while (m.find()) {
                    mergedTemplates.add(Tuple.Pair.with(Component.QUERY, m.group(1)));
                }
            }
            return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), Singleton.parseQuery(query),
                    mergedTemplates != null && !mergedTemplates.isEmpty() ? mergedTemplates : this.templates());
        }
    }

    <T> T qValue(final String key, final Class<T> valueClass);

    default <T> T qValue(final fURI key, final Class<T> valueClass) {
        return this.qValue(key.toString(), valueClass);
    }

    String q(final String key);

    fURI q(final String key, final Object value);

    default fURI addQ(final String key) {
        return this.q(key, null);
    }

    default fURI addQ(final String key, final Object value) {
        return this.q(key, value);
    }

    boolean hasQ(final String key);

    default boolean hasQ(final fURI key) {
        return this.hasQ(key.toString());
    }

    default boolean hasQ() {
        return !this.qMap().isEmpty();
    }

    boolean isRelative();

    default boolean isAbsolute() {
        return !this.isRelative();
    }

    boolean isBranch();

    default boolean isNode() {
        return !this.isBranch();
    }

    fURI asAbsolute();

    fURI asRelative();

    fURI asNode();

    fURI asBranch();

    /// /////////////////////////////////////////////

    default fURI any() {
        return this.c(cInt.ANY());
    }

    default fURI zero() {
        return this.c(cInt.ZERO());
    }

    default fURI one() {
        return this.c(cInt.ONE());
    }

    default fURI maybe() {
        return this.c(cInt.MAYBE());
    }

    default fURI maybeMaybe() {
        return this.c(cInt.of(-1, 1));
    }

    default fURI some() {
        return this.c(cInt.SOME());
    }

    default fURI maybeSome() {
        return this.c(cInt.MAYBESOME());
    }

    default Uri toUri() {
        return uri(this);
    }

    /// ///////////////////////////////////

    default boolean isZero() {
        return this.c().isZero();
    }

    default boolean isOne() {
        return this.c().isOne();
    }

    default boolean isAny() {
        return this.c().isAny();
    }

    default boolean isZeroable() {
        return this.c().isZeroable();
    }

    default boolean isSome() {
        return this.c().isSome();
    }

    default boolean isMaybe() {
        return this.c().isMaybe();
    }

    default boolean isMaybeSome() {
        return this.c().isMaybeSome();
    }

    /// ///////////////////////////////////////////////////

    default fURI constant() {
        return this.q(CONSTQ, null);
    }

    default fURI type(final fURI type) {
        return this.q(T, type);
    }


    /// ////////////////////////////////////////////////

    class Singleton {
        public static final Pattern MERGE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
        public static final Pattern POLY_PATTERN = Pattern.compile(
                "(?<poly>[^\\[{?&*+},]+(\\{([^}\\]]+))?}?[^,])");
        public static final Pattern FURI_PATTERN = Pattern.compile(
                "((?<scheme>[^:/.]+):)?" +
                        "(//((?<host>[^?\\[&<>:/]+)(:(?<port>\\d+))?))?" +
                        "(?<path>[^?\\[{&]+)?" +
                        "(\\[(?<poly>[^]]+)])?" +
                        "(\\{(?<coefficient>[^}\\]]+)})?" +
                        "(\\?" +
                        "((?<rng>[^<&]+)<=(?<dom>[^&?]+))?" +
                        "&?" +
                        "(?<query>(([^&=]+(=[^&=]+)?&?)+))?)?");
        // Template-aware pattern: allows ${...} in scheme, host, port, path, query components
        // Key differences from FURI_PATTERN:
        // - scheme: allows ${...} via alternation
        // - host: allows ${...} via alternation
        // - port: stops at / to not consume path, allows ${...}
        // - path: allows ${...} and captures them fully (including at end of path)
        // - coefficient: only matches {..} NOT preceded by $ (negative lookbehind)
        public static final Pattern FURI_TEMPLATE_PATTERN = Pattern.compile(
                "((?<scheme>\\$\\{[^}]+}|[^:/.]+):)?" +
                        "(//((?<host>\\$\\{[^}]+}|[^?\\[&<>:/]+)(:(?<port>\\$\\{[^}]+}|\\d+))?))?" +
                        "(?<path>([^?\\[&{]|\\$\\{[^}]+})+)?" +
                        "(\\[(?<poly>[^]]+)])?" +
                        "((?<!\\$)\\{(?<coefficient>[^}\\]]+)})?" +
                        "(\\?" +
                        "((?<rng>[^<&]+)<=(?<dom>[^&?]+))?" +
                        "&?" +
                        "(?<query>.+)?)?");
        public static final fURI ALL = new XXPXXXfURI(List.of("#"));
        public static final fURI WILD_ONE = new XXPXXXfURI(List.of("+"));
        public static final fURI NOOBJ = f("noobj").zero();

        public final static fURI empty() {
            return XXXXXXfURI.INSTANCE;
        }


        public final static fURI f(final String furi) {
            return null == furi ? Singleton.empty() : of(furi);
        }


        public static fURI of(final String furi) {
            if (null == furi || furi.isEmpty())
                return Singleton.empty();
            final String furiParse = furi.startsWith("<") && furi.endsWith(">") ? furi.substring(1, furi.length() - 1) : furi;
            if (furiParse.isEmpty())
                return Singleton.empty();
            if ("{0}".equals(furiParse))
                return Singleton.NOOBJ;
            if ("/".equals(furiParse))
                return fURI.of(null, null, -1, List.of("", ""), cInt.ONE(), List.of(), Map.of(), null);

            // Quick check: does this fURI contain templates?
            final boolean hasTemplates = furiParse.contains("${");
            final Matcher matcher = hasTemplates ? Singleton.FURI_TEMPLATE_PATTERN.matcher(furiParse) : Singleton.FURI_PATTERN.matcher(furiParse);

            if (!matcher.matches())
                throw MTronException.of("unable to parse %s to a furi", furiParse);
            final String scheme = matcher.group(SCHEME);
            final String host = matcher.group(HOST);
            final String portStr = matcher.group(PORT);
            final int port = portStr == null ? -1 : (hasTemplates && portStr.contains("${") ? -1 : Integer.parseInt(portStr));
            final String pathStr = matcher.group(PATH);
            final List<String> path = null == pathStr ? List.of() : (hasTemplates && pathStr.contains("${")) ? List.of(pathStr) : new ArrayList<>(Arrays.asList(pathStr.split("/")));
            if (null != pathStr) {
                if (pathStr.endsWith("/"))
                    path.add("");
                if (path.stream().allMatch(String::isEmpty)) {
                    path.clear();
                }
            }
            final String polyStr = matcher.group(POLY);
            List<String> poly = null;
            if (null != polyStr) {
                final Matcher polyMatcher = Singleton.POLY_PATTERN.matcher(polyStr);
                while (polyMatcher.find()) {
                    if (null == poly) poly = new ArrayList<>();
                    if (null != polyMatcher.group(POLY)) {
                        poly.add(polyMatcher.group(POLY));
                    }
                }
            }
            final cInt coefficient = matcher.group(COEFFICIENT) == null ? cInt.ONE() : cInt.of(matcher.group(COEFFICIENT));
            final String queryStr = matcher.group(QUERY);
            final String dom = matcher.group(DOM);
            final String rng = matcher.group(RNG);
            final Map<String, String> query;
            if (dom != null || rng != null || queryStr != null) {
                query = new HashMap<>();
                if (dom != null)
                    query.put(DOM, dom);
                if (rng != null)
                    query.put(RNG, rng);
                if (queryStr != null)
                    query.putAll(parseQuery(queryStr));
            } else {
                query = Map.of();
            }

            // Extract templates if present
            final List<Tuple.Pair<Component, String>> templates = hasTemplates ?
                    extractTemplates(scheme, host, portStr, pathStr, queryStr) :
                    null;

            return fURI.of(scheme, host, port, path, coefficient, poly, query, templates);
        }

        static Map<String, String> parseQuery(final String query) {
            return query == null ? Map.of() : Arrays.stream(query.split("&")).map(s -> s.split("=")).collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : "", (v1, v2) -> v1 + ";" + v2));
        }

        /**
         * Pattern to match template expressions: ${...}
         * Captures the content inside the braces (without the ${ and } delimiters)
         */
        private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

        /**
         * Extract all template expressions from URI components.
         * Returns a list of pairs containing the component type and the expression string.
         * Templates are extracted in order: SCHEME, HOST, PORT, PATH, QUERY
         *
         * @param schemeStr The scheme string (may contain ${...})
         * @param hostStr   The host string (may contain ${...})
         * @param portStr   The port string (may contain ${...})
         * @param pathStr   The path string (may contain ${...})
         * @param queryStr  The query string (may contain ${...})
         * @return List of (Component, expression) pairs, or null if no templates found
         */
        static List<Tuple.Pair<Component, String>> extractTemplates(final String schemeStr,
                                                                    final String hostStr,
                                                                    final String portStr,
                                                                    final String pathStr,
                                                                    final String queryStr) {
            final List<Tuple.Pair<Component, String>> templates = new ArrayList<>();

            // Extract templates from SCHEME
            if (schemeStr != null) {
                final Matcher schemeMatcher = TEMPLATE_PATTERN.matcher(schemeStr);
                while (schemeMatcher.find()) {
                    templates.add(Tuple.Pair.with(Component.SCHEME, schemeMatcher.group(1)));
                }
            }

            // Extract templates from HOST
            if (hostStr != null) {
                final Matcher hostMatcher = TEMPLATE_PATTERN.matcher(hostStr);
                while (hostMatcher.find()) {
                    templates.add(Tuple.Pair.with(Component.HOST, hostMatcher.group(1)));
                }
            }

            // Extract templates from PORT
            if (portStr != null) {
                final Matcher portMatcher = TEMPLATE_PATTERN.matcher(portStr);
                while (portMatcher.find()) {
                    templates.add(Tuple.Pair.with(Component.PORT, portMatcher.group(1)));
                }
            }

            // Extract templates from PATH
            if (pathStr != null) {
                final Matcher pathMatcher = TEMPLATE_PATTERN.matcher(pathStr);
                while (pathMatcher.find()) {
                    templates.add(Tuple.Pair.with(Component.PATH, pathMatcher.group(1)));
                }
            }

            // Extract templates from QUERY
            if (queryStr != null) {
                final Matcher queryMatcher = TEMPLATE_PATTERN.matcher(queryStr);
                while (queryMatcher.find()) {
                    templates.add(Tuple.Pair.with(Component.QUERY, queryMatcher.group(1)));
                }
            }

            return templates.isEmpty() ? null : templates;
        }
    }


    static fURI of(final String scheme,
                   final String host,
                   final int port,
                   final List<String> path,
                   final C<?, ?> coefficient,
                   final List<String> poly,
                   final Map<String, String> query,
                   final List<Tuple.Pair<Component, String>> templates) {
        if (null != templates && !templates.isEmpty())
            return new SAPPCQTfURI(scheme, host, port, path, poly, coefficient, query, templates);
        if (null != poly && !poly.isEmpty())
            return new SAPPCQfURI(scheme, host, port, path, poly, coefficient, query);
        if (null != coefficient && !coefficient.isOne()) {
            if (!query.isEmpty()) {
                if (null != poly && !poly.isEmpty())
                    return new SAPPCQfURI(scheme, host, port, path, poly, coefficient, query);
                else
                    return new SAPXCQfURI(scheme, host, port, path, coefficient, query);
            } else {
                if (null == scheme && null == host)
                    return new XXPXCXfURI(path, coefficient);
                else if (null == host)
                    return new SXPXCXfURI(scheme, path, coefficient);
                else
                    return new SAPXCXfURI(scheme, host, port, path, coefficient);
            }
        } else {
            if (query.isEmpty()) {
                if (null != scheme) {
                    if (null == host)
                        return new SXPXXXfURI(scheme, path);
                    else
                        return new SAPXXXfURI(scheme, host, port, path);
                } else {
                    return host == null ? new XXPXXXfURI(path) : new SAPXXXfURI(null, host, port, path);
                }
            } else {
                return new SAPXCQfURI(scheme, host, port, path, coefficient, query);
            }
        }
    }
}
