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

package studio.phaseshift.metatron.furi;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.form.*;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface fURI extends Cloneable, Ring<fURI>, Comparable<fURI>, Predicate<fURI> {

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
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), Map.of());
    }

    default boolean hasAuthority() {
        return null != this.host() && -1 != this.port();
    }

    default boolean isGeneric() {
        if (this.path().isEmpty())
            return false;
        if (this.pathLength() == 1 && (this.path().getFirst().equals("#") || this.path().getFirst().equals("+")))
            return false;
        boolean hasCapitalGeneric = false;
        for (final String seg : this.one().path()) { // TODO: this is necessary because {} is appended to final segment (needs to be fixed ASAP!).
            if (!seg.isEmpty() && seg.chars().allMatch(Character::isUpperCase))
                hasCapitalGeneric = true;
            if (seg.chars().anyMatch(c -> c != '#' && c != '+' && !Character.isAlphabetic(c) || Character.isLowerCase(c)))
                return false;
        }
        return hasCapitalGeneric;
    }

    default boolean bimatches(final fURI other) {
        return this.test(other) || other.test(this);
    }

    fURI resolve();

    default fURI resolve(final Map<fURI, fURI> generics) {
        final fURI cless = this.one();
        Graphitty.log(this).trace("resolving generics: %s", generics);
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
            Graphitty.log(this).trace("generics after resolution: %s", generics);
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
        return Router.loaded() ? Router.global().rewrite(this, true) : this;
    }

    default fURI small() {
        return Router.loaded() ? Router.global().rewrite(this, false) : this;
    }

    default boolean isEmpty() {
        return this.toString().isEmpty();
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
        return this.extend(segments.path().stream().collect(Collectors.joining("/")));
    }

    fURI head(final int steps);

    fURI tail(final int steps);

    fURI retract(final int steps);

    fURI retractPattern();

    fURI retract(final String segment);

    fURI prepend(final String segment);

    fURI pretract(final String segment);

    fURI pretract(final int steps);

    default fURI removePrefix(final fURI prefix) {
        final String newPath = this.toString();
        final String pre = prefix.toString();
        //return new fURI(newPath.startsWith(prefix.toString()) ? newPath.substring(prefix.send ? prefix.toString().length() +1 : prefix.toString().length()) : newPath);
        if (!newPath.startsWith(pre))
            return this;
        final fURI newURI = Singleton.of(newPath.substring(pre.length() + (newPath.charAt(pre.length()) == '/' ? 1 : 0)));
        return newURI;
    }

    default boolean hasPoly() {
        return null != this.poly() && !this.poly().isEmpty();
    }

    boolean hasPrefix(final String prefix);

    boolean hasPostfix(final String postfix);

    boolean hasPattern();

    boolean hasPattern(final String pattern);

    fURI basePath();

    List<String> poly();

    int pathLength();

    fURI poly(final List<String> poly);

    fURI neg();

    fURI mult(final fURI other);

    fURI plus(final fURI other);

    cInt c();

    fURI c(final cInt coefficient);

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

    default fURI qString(final String query) {
        if (null == query || query.isEmpty())
            return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), Map.of());
        else
            return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), Singleton.parseQuery(query));
    }

    <T> T qValue(final String key, final Class<T> valueClass);

    String q(final String key);

    fURI q(final String key, final Object value);

    boolean hasQ(final String key);

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


    /// ////////////////////////////////////////////////

    class Singleton {
        public static final Pattern POLY_PATTERN = Pattern.compile(
                "(?<poly>[^\\[{?&*+},]+(\\{([^}\\]]+))?}?[^,])");
        public static final Pattern FURI_PATTERN = Pattern.compile(
                "((?<scheme>[^:/.]+):)?" +
                        "(//((?<host>[^:/]+)(:(?<port>\\d+))?))?" +
                        "(?<path>[^\\[{?&]+)?" +
                        "(\\[(?<poly>[^]]+)])?" +
                        "(\\{(?<coefficient>[^}\\]]+)})?" +
                        "(\\?" +
                        "((?<rng>[^<&]+)<=(?<dom>[^&?]+))?" +
                        "&?" +
                        "(?<query>(([^&=]+(=[^&=]+)?&?)+))?)?");
        public static final fURI ALL = new XXPXXXfURI(List.of("#"));
        public static final fURI WILD_ONE = new XXPXXXfURI(List.of("+"));
        public static final fURI NOOBJ = f("noobj").zero();

        private static final fURI INSTANCE = new XXXXXXfURI();

        public final static fURI empty() {
            return INSTANCE;
        }


        public final static fURI f(final String furi) {
            return null == furi ? Singleton.empty() : of(furi);
        }


        public static fURI of(final String furi) {
            if (null == furi || furi.isEmpty())
                return Singleton.empty();
            final String furiParse = furi.startsWith("<") && furi.endsWith(">") ? furi.substring(1, furi.length() - 1) : furi;
            if ("{0}".equals(furiParse))
                return Singleton.NOOBJ;
            if ("/".equals(furiParse))
                return fURI.of(null, null, -1, List.of("", ""), cInt.ONE(), List.of(), Map.of());
            final Matcher matcher = Singleton.FURI_PATTERN.matcher(furiParse);
            if (!matcher.matches())
                throw MTronException.of("unable to parse %s to a furi", furiParse);
            final String scheme = matcher.group("scheme");
            final String host = matcher.group("host");
            final int port = matcher.group("port") == null ? -1 : Integer.parseInt(matcher.group("port"));
            final String pathStr = matcher.group("path");
            final String polyStr = matcher.group("poly");
            List<String> poly = null;
            if (null != polyStr) {
                final Matcher polyMatcher = Singleton.POLY_PATTERN.matcher(polyStr);
                while (polyMatcher.find()) {
                    if (null == poly) poly = new ArrayList<>();
                    if (null != polyMatcher.group("poly")) {
                        poly.add(polyMatcher.group("poly"));
                    }
                }
            }
            final cInt coefficient = matcher.group("coefficient") == null ? cInt.ONE() : cInt.of(matcher.group("coefficient"));
            final String queryStr = matcher.group("query");
            final String dom = matcher.group("dom");
            final String rng = matcher.group("rng");
            final List<String> path = null == pathStr ? List.of() : new ArrayList<>(Arrays.asList(pathStr.split("/")));
            if (null != pathStr) {
                if (pathStr.endsWith("/"))
                    path.add("");
                if (path.stream().allMatch(String::isEmpty)) {
                    path.clear();
                }
            }
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
            return fURI.of(scheme, host, port, path, coefficient, poly, query);
        }

        static Map<String, String> parseQuery(final String query) {
            return query == null ? Map.of() : Arrays.stream(query.split("&")).map(s -> s.split("=")).collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : ""));
        }
    }


    static fURI of(final String scheme, final String host, final int port, final List<String> path, final C<?, ?> coefficient, final List<String> poly, final Map<String, String> query) {
        if (null != poly && !poly.isEmpty()) {
            return new SAPPCQfURI(scheme, host, port, path, poly, coefficient, query);
        }
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
