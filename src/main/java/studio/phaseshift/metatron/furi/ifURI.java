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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface ifURI {

    String scheme();

    ifURI scheme(final String scheme);

    default boolean hasScheme() {
        return null != this.scheme();
    }

   /* default String authority() {
        if (this.hasHost())
            return this.hasPort() ? this.host() + ":" + this.port() : this.host();
        return null;
    }*/

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

    //default ifURI dom() {
    //    return this.q(DOM);
    //}

    ifURI dom();

    ifURI dom(ifURI dom);

    ifURI rng();

    ifURI rng(ifURI rng);

    String qString();

    Map<String, String> qMap();

    ifURI q(final Map<String, String> query);

    <T> T qValue(final String key, final Class<T> valueClass);

    String q(final String key);

    ifURI q(final String key, final Object value);

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

    static ifURI empty() {
        return XXXXXfURI.INSTANCE;
    }

    static ifURI of(final String furi) {
        if (null == furi)
            return empty();
        String scheme = null;
        String host = null;
        int port = -1;
        String pathStr = null;
        cInt coefficient = cInt.ONE();
        Map<String, String> query = Map.of();

        Pattern pattern = Pattern.compile("((?<scheme>[^:/.]+):)?(//((?<host>[^:/]+)(:(?<port>\\d+))?))?(?<path>[^?{]+)?(\\{(?<coefficient>[^}]+)})?(\\?(?<query>[^#+]+))?");
        Matcher matcher = pattern.matcher(furi);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid URI: " + furi);
        }

        scheme = matcher.group("scheme");
        host = matcher.group("host");
        port = matcher.group("port") == null ? -1 : Integer.parseInt(matcher.group("port"));
        pathStr = matcher.group("path");
        coefficient = matcher.group("coefficient") == null ? cInt.ONE() : cInt.of(matcher.group("coefficient").replace("{", "").replace("}", ""));
        String queryStr = matcher.group("query");
        query = queryStr == null ? Map.of() : parseQuery(queryStr);
        List<String> path = null == pathStr ? List.of() : new ArrayList<>(Arrays.asList(pathStr.split("/")));
        if (pathStr != null && pathStr.endsWith("/"))
            path.add("");
        return ifURI.of(scheme, host, port, path, coefficient, List.of(), query);
        
        /*String uri = furi;
        String scheme = null;
        String host = null;
        int port = -1;
        List<String> path;
        Map<String, String> query = Map.of();
        C<?, ?> coefficient = cInt.ONE();
        boolean sstart = false;
        boolean send = false;
        List<String> poly = null;

        ///  PARSE AWAY QUERY BACKEND
        int queryPosition = uri.lastIndexOf("?");
        if (queryPosition == -1 || uri.charAt(queryPosition - 1) == '{')
            queryPosition = -1;
        if (queryPosition == uri.length() - 1) {
            uri = uri.substring(0, uri.length() - 1);
            queryPosition = -1;
        }
        final String tempQuery = queryPosition == -1 ? null : uri.substring(queryPosition + 1);
        query = null == tempQuery ? Map.of() : Arrays.stream(tempQuery.split("&")).map(s -> s.split("=")).collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : ""));
        if (!query.isEmpty())
            uri = uri.substring(0, queryPosition);
        /// PARSE SEGMENTS
        send = uri.charAt(uri.length() - 1) == '/';
        int position = 0;
        int i = uri.indexOf(":");
        int j = uri.indexOf("/");
        if (j != -1 && j < i) i = -1;
        int temp = uri.indexOf("//");
        if (temp == j && (i != -1 && (temp == -1 || i < temp))) {
            scheme = 0 == i ? null : uri.substring(0, i);
            position = i + 1;
        } else {
            if (i != -1 && (temp == -1 || j != temp)) {
                scheme = uri.substring(0, i);
                position = i;
                temp = -1;
                i = 0;
            } else {
                i = 0;
                scheme = null;
            }
        }
        //   if(temp != j)
        ///  PARSE AUTHORITY
        if (temp != -1) {
            position = position + 2;
            temp = uri.indexOf('/', position + 1);
            final String[] authority = uri.substring(position, -1 == temp ? uri.length() : temp).split(SCHEMA_END);
            if (temp == -1)
                temp = uri.length() - 1;
            host = authority[0];
            port = authority.length == 2 ? Integer.parseInt(authority[1]) : -1;
        } else {
            temp = uri.charAt(0) == '/' ? i + 1 : i;
            host = null;
            port = -1;
        }
        // PARSE EMPTY PATH
        sstart = (host == null && scheme == null) && (uri.charAt(temp) == '/' || uri.charAt(0) == '/');
        position = scheme != null ? temp + 1 : temp;
        if (position == uri.length()) {
            path = Collections.emptyList();
            send = false;
            poly = null;
            return ifURI.of(scheme, host, port, path, coefficient, poly, query);
        }
        /// POLY AND PATH
        final int polyStart = uri.indexOf('[');
        final int polyEnd = polyStart == -1 ? -1 : uri.indexOf(']');
        path = new ArrayList<>(Arrays.asList((polyStart == -1 ? uri.substring(position) : uri.substring(position, polyStart)).split("/")));
        if (polyStart != -1 && polyEnd != -1) {
            final String remaining = uri.substring(polyStart + 1, polyEnd);
            final String[] splits = remaining.split("(?<!\\d),(?!\\d)");
            poly = Arrays.asList(splits);
            path.set(path.size() - 1, path.getLast() + uri.substring(polyEnd + 1));
        } else {
            poly = null;
        }
        /// PARSE COEFFICIENT
        if (path.get(path.size() - 1).contains("{")) {
            coefficient = cInt.of(path.get(path.size() - 1).substring(path.get(path.size() - 1).indexOf("{") + 1, path.get(path.size() - 1).indexOf("}")));
            path.set(path.size() - 1, path.get(path.size() - 1).substring(0, path.get(path.size() - 1).indexOf("{")));
        }
        if (sstart)
            path.add(0, "");
        if (send)
            path.add("");
        return of(scheme, host, port, path, coefficient, poly, query);*/
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
