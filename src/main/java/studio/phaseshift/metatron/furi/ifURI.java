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
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.form.*;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.RNG;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface ifURI {

    ifURI resolve();

    String scheme();

    ifURI scheme(final String scheme);

    default boolean hasScheme() {
        return null != this.scheme();
    }

    default String authority() {
        if (this.hasHost())
            return this.hasPort() ? this.host() + ":" + this.port() : this.host();
        return null;
    }

    String host();

    ifURI host(final String host);

    default boolean hasHost() {
        return this.host() != null;
    }

    int port();

    default boolean hasPort() {
        return this.port() != -1;
    }

    ifURI port(final int port);

    List<String> path();

    ifURI path(final List<String> path);

    ifURI path(final String path);

    String pathString();

    default String name() {
        return this.path().isEmpty() ? Tokens.EMPTY : this.path().getLast();
    }

    boolean test(final ifURI lhs);

    ifURI extend(final String segment);

    ifURI head(final int steps);

    ifURI tail(final int steps);

    ifURI retract(final int steps);

    ifURI retractPattern();

    ifURI retract(final String segment);

    ifURI prepend(final String segment);

    ifURI pretract(final String segment);

    ifURI pretract(final int steps);

    boolean hasPrefix(final String prefix);

    boolean hasPostfix(final String postfix);

    boolean hasPattern();

    ifURI basePath();

    List<String> poly();

    int pathLength();

    C<?, ?> c();

    ifURI c(final C<?, ?> coefficient);

    ifURI dom();

    ifURI dom(final ifURI dom);

    ifURI rng();

    ifURI rng(final ifURI rng);

    String qString();

    Map<String, String> qMap();

    ifURI q(final Map<String, String> query);

    <T> T qValue(final String key, final Class<T> valueClass);

    String q(final String key);

    ifURI q(final String key, final Object value);

    boolean hasQ(final String key);

    boolean isRelative();

    default boolean isAbsolute() {
        return !this.isRelative();
    }

    boolean isBranch();

    default boolean isNode() {
        return !this.isBranch();
    }

    /// /////////////////////////////////////////////

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

        public static final ifURI WILD_ALL = new XXPXXfURI(List.of("#"));
        public static final ifURI WILD_ONE = new XXPXXfURI(List.of("+"));

        private static final ifURI INSTANCE = new XXXXXfURI();

        public static ifURI empty() {
            return INSTANCE;
        }
        
       /* public static ifURI f(final String furi) {
            return of(furi);
        }*/

        public static ifURI of(final String furi) {
            return ifURI.of(furi);
        }

        public static final Pattern FURI_PATTERN = Pattern.compile("(" +
                "(?<scheme>[^:/.]+):)?" +
                "(//((?<host>[^:/]+)(:(?<port>\\d+))?))?" +
                "(?<path>[^?{]+)?(\\{(?<coefficient>[^}]+)})?" +
                "(\\?" +
                "((?<rng>[^<&]+)<=(?<dom>[^&?]+))?" +
                "&?" +
                "(?<query>(([^&=]+(=[^&=]+)?&?)+))?)?");
    }

    static ifURI of(final String furi) {
        if (null == furi || furi.isEmpty())
            return Singleton.empty();
        final Matcher matcher = Singleton.FURI_PATTERN.matcher(furi);
        if (!matcher.matches())
            throw MTronException.of("unable to parse %s to a furi", furi);
        final String scheme = matcher.group("scheme");
        final String host = matcher.group("host");
        final int port = matcher.group("port") == null ? -1 : Integer.parseInt(matcher.group("port"));
        final String pathStr = matcher.group("path");
        final cInt coefficient = matcher.group("coefficient") == null ? cInt.ONE() : cInt.of(matcher.group("coefficient").replace("{", "").replace("}", ""));
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
            query = new LinkedHashMap<>();
            if (dom != null)
                query.put(DOM, dom);
            if (rng != null)
                query.put(RNG, rng);
            if (queryStr != null)
                query.putAll(parseQuery(queryStr));
        } else {
            query = Map.of();
        }
        return ifURI.of(scheme, host, port, path, coefficient, List.of(), query);
    }

    static Map<String, String> parseQuery(final String query) {
        return query == null ? Map.of() : Arrays.stream(query.split("&")).map(s -> s.split("=")).collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : ""));
    }


    static ifURI of(final String scheme, final String host, final int port, final List<String> path, final C<?, ?> coefficient, final List<String> poly, final Map<String, String> query) {
        if (!coefficient.isOne()) {
            if (!query.isEmpty()) {
                return new SAPCQfURI(scheme, host, port, path, coefficient, query);
            } else {
                if (null == scheme && null == host)
                    return new XXPCXfURI(path, coefficient);
                else if (null == host)
                    return new SXPCXfURI(scheme, path, coefficient);
                else
                    return new SAPCXfURI(scheme, host, port, path, coefficient);
            }
        } else {
            if (query.isEmpty()) {
                if (null != scheme) {
                    if (null == host)
                        return new SXPXXfURI(scheme, path);
                    else
                        return new SAPXXfURI(scheme, host, port, path);
                } else {
                    return host == null ? new XXPXXfURI(path) : new SAPXXfURI(null, host, port, path);
                }
            } else {
                return new SAPCQfURI(scheme, host, port, path, coefficient, query);
            }
        }
    }
}
