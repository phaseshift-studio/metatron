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
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.c.cInt.C_ONE;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class fURI implements Cloneable, Ring<fURI>, Comparable<fURI> {

    public static final String SEGMENT_SPLIT = "/";
    public static final char SEGMENT_SPLIT_CHAR = SEGMENT_SPLIT.charAt(0);
    public static final String KVS_SPLIT = "&";
    public static final String KV_SPLIT = "=";
    public static final String QUERY_START = "?";
    public static final String SCHEMA_END = ":";
    public static final String HOST_START = "//";
    public static final char ALL_WILD_CHAR = '#';
    public static final char ONE_WILD_CHAR = '+';
    public static final String ALL_WILD_STRING = String.valueOf(ALL_WILD_CHAR);
    public static final String ONE_WILD_STRING = String.valueOf(ONE_WILD_CHAR);
    public static final fURI ALL = fURI.of(ALL_WILD_CHAR);
    public static final fURI SINGLE = fURI.of(ONE_WILD_CHAR);
    public static final fURI fnull = null;
    public static final fURI DOM = fURI.of("dom");
    public static final fURI RNG = fURI.of("rng");
    public static final fURI NOOBJ = fURI.of("").c("0");
    private static final fURI ONE = f(".").c("1");
    private static final fURI ZERO = f("").c("0");
    private final String host;
    private final String scheme;
    private final int port;
    private final List<String> path;
    private final List<String> poly;
    private final Query query;
    private boolean sstart;
    private boolean send;

   /* private fURI(final String scheme, final String host, final int port, final boolean sstart, final List<String> path, final boolean send, final String query) {
        this.host = host;
        this.scheme = scheme;
        this.port = port;
        this.path = path;
        this.query = Query.from(query);
        this.sstart = sstart;
        this.send = send;
        this.poly = this.name().contains("[") ? Arrays.asList(this.name().substring(this.name().indexOf('[') + 1, this.name().indexOf(']')).split(",")) : null;
    }*/

    public fURI() {
        this(null, null, -1, false, null, false, null, null);
    }

    private fURI(final String scheme, final String host, final int port, final boolean sstart, final List<String> path, final boolean send, final List<String> poly, final String query) {
        this.host = host;
        this.scheme = scheme;
        this.port = port;
        this.path = path;
        this.query = Query.from(query);
        this.sstart = sstart;
        this.send = send;
        this.poly = poly;
    }
    // private final boolean wildcard;


    public fURI(String uri) {
        if (null == uri || uri.isEmpty()) {
            this.scheme = null;
            this.host = null;
            this.port = -1;
            this.path = Collections.emptyList();
            this.sstart = false;
            this.send = false;
            this.query = null;
            this.poly = null;
            return;
        }
        int queryPosition = uri.lastIndexOf(QUERY_START);
        if (queryPosition == -1 || uri.charAt(queryPosition - 1) == '{')
            queryPosition = -1;
        if (queryPosition == uri.length() - 1) {
            uri = uri.substring(0, uri.length() - 1);
            queryPosition = -1;
        }
        final String tempQuery = queryPosition == -1 ? null : uri.substring(queryPosition + 1);
        this.query = Query.from(tempQuery);
        if (null != this.query)
            uri = uri.substring(0, queryPosition);
        this.send = uri.charAt(uri.length() - 1) == SEGMENT_SPLIT.charAt(0);

        int position = 0;
        int i = uri.indexOf(SCHEMA_END);
        int j = uri.indexOf(SEGMENT_SPLIT);
        if (j != -1 && j < i) i = -1;
        int temp = uri.indexOf(HOST_START);
        if (i != -1 && (temp == -1 || i < temp)) {
            this.scheme = 0 == i ? null : uri.substring(0, i);
            position = i + 1;
        } else {
            i = 0;
            this.scheme = null;
        }
        if (temp != -1) {
            position = position + 2;
            temp = uri.indexOf(SEGMENT_SPLIT_CHAR, position + 1);
            final String[] authority = uri.substring(position, -1 == temp ? uri.length() : temp).split(SCHEMA_END);
            if (temp == -1)
                temp = uri.length() - 1;
            this.host = authority[0];
            this.port = authority.length == 2 ? Integer.parseInt(authority[1]) : -1;
        } else {
            temp = uri.charAt(0) == SEGMENT_SPLIT_CHAR ? i + 1 : i;
            this.host = null;
            this.port = -1;
        }
        this.sstart = (this.host != null && null != this.scheme && uri.charAt(temp) == SEGMENT_SPLIT_CHAR) || uri.charAt(0) == SEGMENT_SPLIT_CHAR;
        position = null != this.scheme ? temp + 1 : temp;
        if (position == uri.length()) {
            this.path = Collections.emptyList();
            this.send = false;
            this.poly = null;
            return;
        }

        final int polyStart = uri.indexOf('[');
        final int polyEnd = polyStart == -1 ? -1 : uri.indexOf(']');
        this.path = Arrays.asList((polyStart == -1 ? uri.substring(position) : uri.substring(position, polyStart)).split(SEGMENT_SPLIT));
        if (polyStart != -1 && polyEnd != -1) {
            final String remaining = uri.substring(polyStart + 1, polyEnd);
            final String[] splits = remaining.split("(?<!\\d),(?!\\d)");
            this.poly = Arrays.asList(splits);
            this.path.set(this.path.size() - 1, this.path.getLast() + uri.substring(polyEnd + 1));
        } else {
            this.poly = null;
        }
        //   this.poly = this.name().contains("[") ? Arrays.asList(this.name().substring(this.name().indexOf('[') + 1, this.name().indexOf(']')).split(",")) : null;
    }

    public static fURI f(final String s) {
        return fURI.of(s);
    }

    public static fURI dotPath(final String uri) {
        return fURI.of(uri.replace('.', SEGMENT_SPLIT_CHAR));
    }

    public static fURI of(final String uri) {
        return new fURI(uri);
    }

    public static fURI of(final Object uri) {
        return uri instanceof fURI ? (fURI) uri : fURI.of(uri.toString());
    }

    @Override
    public int compareTo(final fURI furi) {
        if (null == furi) return -1;
        if (this.equals(furi)) return 0;
        if (Objects.equals(this.host, "#"))
            return 1;
        if (!Objects.equals(this.host, furi.host) && !Objects.equals(this.host, "+"))
            return -1;
        for (int i = 0; i < this.path.size(); i++) {
            final String segment = this.path.get(i);
            if (segment.equals("#"))
                return 1;
            if (furi.pathLength() <= i)
                return -1;
            if (!segment.equals("+") && !segment.equals(furi.path.get(i)))
                return -1;
        }
        return (this.path.size() > furi.pathLength() || furi.pathLength() == this.path.size() && this.hasPattern()) ? 1 : -1;
    }

    public fURI authority(final fURI authority) {
        return new fURI(this.scheme, null == authority ? null : authority.host, null == authority ? -1 : authority.port, this.sstart, this.path, this.send, this.poly, this.query == null ? null : this.query.toString());
    }

    public boolean hasAuthority() {
        return null != this.host && -1 != this.port;
    }

    public fURI big() {
        return Router.loaded() ? Router.global().rewrite(this, true) : this;
    }

    public fURI small() {
        return Router.loaded() ? Router.global().rewrite(this, false) : this;
    }

    public List<String> poly() {
        return null == this.poly ? List.of() : this.poly;
    }

    public fURI poly(final List<String> poly) {
        if (null == poly || poly.isEmpty() || Objects.equals(this.poly, poly))
            return this;
        return new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, poly, Query.to(this.query));

    }

    public boolean isEmpty() {
        return this.toString().isEmpty();
    }

    public fURI commonRoot(final fURI other) {
        if (this.sstart != other.sstart)
            return fURI.of("");
        final List<String> common = new ArrayList<>();
        final int maxLength = Math.min(this.path.size(), other.path.size());
        for (int i = 0; i < Math.min(this.path.size(), other.path.size()); i++) {
            if (this.path.get(i).equals(other.path.get(i)))
                common.add(this.path.get(i));
            else
                break;
        }
        fURI base = fURI.of(common.stream().reduce(this.sstart ? SEGMENT_SPLIT : Tokens.EMPTY, (a, b) -> a + b + SEGMENT_SPLIT));
        if (other.path.size() != this.path.size())
            return base.extend(ALL);
        final int extensionCount = maxLength - common.size();
        for (int i = 0; i < extensionCount; i++) {
            base = base.extend(SINGLE);
        }
        return base;
    }

    public Uri toUri(final boolean schemeType) {
        final String scheme = this.scheme();
        return schemeType && null != scheme ?
                uri(this.scheme(null), fURI.of(scheme)) :
                uri(this);
    }

    public int pathLength() {
        return this.segments().size();
    }

    public fURI removePrefix(final fURI prefix) {
        final String newPath = this.toString();
        final String pre = prefix.toString();
        //return new fURI(newPath.startsWith(prefix.toString()) ? newPath.substring(prefix.send ? prefix.toString().length() +1 : prefix.toString().length()) : newPath);
        if (!newPath.startsWith(pre))
            return this;
        final fURI newURI = new fURI(newPath.substring(pre.length() + (newPath.charAt(pre.length()) == '/' ? 1 : 0)));
        newURI.sstart = !prefix.send;
        return newURI;
    }


    public String name() {
        return this.segments().isEmpty() ? Tokens.EMPTY : this.basePath().segments().get(this.segments().size() - 1);
    }

    public fURI resolve() {
        final List<String> newSegments = new ArrayList<>();
        for (final String seg : this.segments()) {
            if (seg.equals("."))
                continue;
            if (seg.equals("..") && !newSegments.isEmpty() && !newSegments.getLast().equals(".."))
                newSegments.removeLast();
            else
                newSegments.add(seg);
        }
        return this.path(newSegments);
    }

    public Uri toUri() {
        return this.toUri(false);
    }

    public boolean isBranch() {
        return this.send;
    }

    public boolean isNode() {
        return !this.send;
    }

    public fURI scheme(final String scheme) {
        if (Objects.equals(this.scheme, scheme))
            return this;
        if (null != scheme && scheme.contains(":"))
            throw MTronException.of("scheme cannot contain delimiter: %s", scheme);
        return new fURI(scheme, this.host, this.port, this.sstart, this.path, this.send, this.poly, Query.to(this.query));
    }

    public fURI path(final String path) {
        return new fURI(this.scheme, this.host, this.port, path.charAt(0) == SEGMENT_SPLIT_CHAR || this.hasAuthority(), Arrays.asList(path.split("/")), path.charAt(path.length() - 1) == SEGMENT_SPLIT_CHAR, this.poly, Query.to(this.query));
    }

    public String path() {
        final String p = this.path.stream().reduce(this.sstart ? SEGMENT_SPLIT : Tokens.EMPTY, (a, b) -> a + b + SEGMENT_SPLIT);
        return this.send || p.isEmpty() ? p : p.substring(0, p.length() - 1);
    }

    public List<String> segments() {
        return Collections.unmodifiableList(this.path);
    }

    public fURI segments(final List<String> segs) {
        return new fURI(this.scheme, this.host, this.port, this.sstart, segs, this.send, this.poly, Query.to(this.query));
    }

    public String scheme() {
        return this.scheme;
    }

    public fURI retractPattern() {
        if (!this.hasPattern())
            return this;
        fURI r = this;
        while (!r.segments().isEmpty()) {
            final String end = r.segments().getLast();
            if (end.length() == 1 && (end.equals(ALL.toString()) || end.equals(SINGLE.toString())))
                r = r.retract();
            else
                break;
        }
        return r;
    }

    public String hostOrSegment() {
        return null != this.authority() ? this.host : this.path.getFirst();
    }

    public fURI authority() {
        return null == this.host ? null : f(this.host + (this.port == -1 ? "" : SCHEMA_END + this.port));
        //return null == this.host ? null : this.host +;
    }

    public String host() {
        return this.host;
    }

    public fURI host(final String host) {
        if (Objects.equals(this.host, host))
            return this;
        if (null != host && host.contains(":"))
            throw MTronException.of("host cannot contain port: %s", host);
        return new fURI(this.scheme, host, this.port, this.sstart, this.path, this.send, this.poly, Query.to(this.query));
    }

    public fURI port(final int port) {
        if (port == this.port)
            return this;
        return new fURI(this.scheme, this.host, port, this.sstart, this.path, this.send, this.poly, Query.to(this.query));
    }

    public int port() {
        return this.port;
    }

    public boolean isAbsolute() {
        return this.host != null || this.sstart;
    }

    public fURI prepend(final String segment) {
        final List<String> newPath = new ArrayList<>();
        newPath.addAll(Arrays.asList(segment.split(SEGMENT_SPLIT)));
        newPath.addAll(this.path);
        return new fURI(this.scheme, this.host, this.port, !newPath.isEmpty() && null != this.host || segment.charAt(0) == SEGMENT_SPLIT_CHAR, newPath, !this.path.isEmpty() && this.send, this.poly, Query.to(this.query));
    }

    public fURI extend(final fURI extension) {
        return this.extend(extension.toString());
    }

    public fURI extend(final String segment) {
        if (!segment.contains("..")) {
            final List<String> newPath = new ArrayList<>(this.path.size() + 1);
            newPath.addAll(this.path);
            newPath.addAll(Arrays.asList(segment.split(SEGMENT_SPLIT)));
            newPath.removeIf(String::isEmpty);
            newPath.removeIf(s -> s.equals("."));
            return new fURI(this.scheme, this.host, this.port, (this.hasAuthority() && this.path.isEmpty()) || this.sstart, newPath, !segment.isEmpty() && (segment.charAt(segment.length() - 1) == SEGMENT_SPLIT_CHAR), this.poly, Query.to(this.query));
        } else {
            final List<String> segments = new ArrayList<>(this.segments());
            for (final String s : segment.split(SEGMENT_SPLIT)) {
                if (!segments.isEmpty() && s.equals("..") && !segments.getLast().endsWith(".."))
                    segments.removeLast();
                else if (!s.equals("."))
                    segments.add(s);
            }
            return new fURI(this.scheme, this.host, this.port, (this.hasAuthority() && this.path.isEmpty()) || this.sstart, segments, segment.charAt(segment.length() - 1) == SEGMENT_SPLIT_CHAR, this.poly, Query.to(this.query));
        }
    }

    private fURI rePreTract(boolean retract, final int steps) {
        if (this.path.size() < steps)
            return new fURI(this.scheme, this.host, this.port, false, Collections.emptyList(), false, this.poly, Query.to(this.query));
        final String coefficient = this.c();
        final fURI noc = this.cLess();
        final List<String> newPath = retract ? noc.path.subList(0, noc.path.size() - steps) : noc.path.subList(steps, noc.path.size());
        if (!newPath.isEmpty() && null != coefficient) {
            newPath.set(newPath.size() - 1, newPath.getLast() + "{" + coefficient + "}");
        }
        return new fURI(this.scheme, this.host, this.port, this.sstart && !newPath.isEmpty(), newPath, this.send && !newPath.isEmpty(), this.poly, Query.to(this.query));

    }

    public boolean isZero() {
        return this.cV().isZero(); //|| this.path().equals("noobj");
    }

    public fURI removeSubpath(final fURI subpath) {
        String newPath = this.toString();
        return new fURI(newPath.replace(subpath.asBranch().toString(), Tokens.EMPTY));
    }

    public boolean hasScheme() {
        return null != this.scheme;
    }

    public boolean hasPrefix(final fURI prefix) {
        if (prefix.hasScheme() && (!this.hasScheme() || !this.scheme.equals(prefix.scheme)))
            return false;
        if (prefix.hasAuthority() && (!this.hasAuthority() || !this.authority().matches(prefix.authority())))
            return false;
        for (int i = 0; i < prefix.path.size(); i++) {
            if (this.pathLength() <= i)
                return false;
            if (!this.segment(i).matches(prefix.segment(i)))
                return false;
        }
        return true;
    }

    public fURI asAbsolute() {
        fURI clone = this.clone();
        clone.sstart = true;
        return clone;
    }

    public fURI asRelative() {
        fURI clone = this.clone();
        clone.sstart = false;
        return clone;
    }

    public fURI asNode() {
        if (!this.send)
            return this;
        fURI clone = this.clone();
        clone.send = false;
        return clone;
    }

    public fURI asBranch() {
        if (this.send)
            return this;
        fURI clone = this.clone();
        clone.send = true;
        return clone;
    }

    public fURI retract() {
        return this.retract(1);
    }

    public fURI retract(final int steps) {
        return this.rePreTract(true, steps);
    }

    public fURI head(final int steps) {
        final List<String> head = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            head.add(this.segments().get(i));
        }
        return this.segments(head);
    }

    public fURI tail(final int steps) {
        final List<String> tail = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            tail.addFirst(this.segments().get(this.segments().size() - (i + 1)));
        }
        return this.segments(tail);
    }

    public fURI segment(final int step) {
        return fURI.of(this.path.get(step));
    }

    public fURI pretract() {
        return this.pretract(1);
    }

    public fURI pretract(final int steps) {
        return this.rePreTract(false, steps);
    }

    public boolean hasPattern(char pattern) {
        return this.toString().chars().anyMatch(c -> c == pattern);
    }

    public boolean hasPattern() {
        return (null != this.scheme && this.scheme.length() == 1 && (this.scheme.charAt(0) == ALL_WILD_CHAR || this.scheme.charAt(0) == ONE_WILD_CHAR)) ||
                (null != this.host && this.host.length() == 1 && (this.host.charAt(0) == ALL_WILD_CHAR || this.host.charAt(0) == ONE_WILD_CHAR)) ||
                this.path.stream().filter(s -> !s.isEmpty()).map(s -> s.charAt(0)).anyMatch(c -> c == ONE_WILD_CHAR || c == ALL_WILD_CHAR);
    }

    public boolean hasQuery() {
        return this.query != null;
    }

    public boolean hasQuery(final String key) {
        return this.query != null && this.query.hasKey(key);
    }

    public boolean hasQuery(final fURI key) {
        return this.hasQuery(key.toString());
    }

    public boolean hasDom() {
        return this.hasQuery(DOM.toString());
    }

    public boolean hasRng() {
        return this.hasQuery(RNG.toString());
    }

    public fURI query(final String query) {
        return new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, this.poly, null == query || query.isEmpty() ? null : query);
    }

    public fURI query(final String key, final String value) {
        Map<String, String> appended = null == this.query ? new LinkedHashMap<>() : new LinkedHashMap<>(this.query.query);
        appended.put(key, value);
        return new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, this.poly, Query.to(Query.from(appended)));
    }

    public fURI removeQ(final String key) {
        Map<String, String> appended = null == this.query ? new LinkedHashMap<>() : new LinkedHashMap<>(this.query.query);
        appended.remove(key);
        return new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, this.poly, Query.to(Query.from(appended)));
    }

    public fURI query(final Object key, final Object value) {
        return this.query(key.toString(), value.toString());
    }

    public fURI queryMap(final Map<String, String> kv) {
        return new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, this.poly, Query.to(Query.from(kv)));
    }

    public fURI zero() {
        return ZERO;
    }

    public fURI one() {
        return ONE;
    }

    public fURI maybe() {
        return this.c("?");
    }

    public fURI any() {
        return this.c("**");
    }

    public fURI some() {
        return this.c("+");
    }

    public fURI maybeSome() {
        return this.c("*");
    }

    public fURI c(final String coefficient) {
        if (null == coefficient || coefficient.isEmpty() || cInt.of(coefficient).isOne()) {
            if (this.path.isEmpty())
                return this;
            if (!this.path.getLast().contains("{"))
                return this;
            final List<String> segments = new ArrayList<>(this.path);
            String last = segments.removeLast();
            segments.add(last.substring(0, last.indexOf("{")));
            return new fURI(this.scheme, this.host, this.port, this.sstart, segments, this.send, this.poly, Query.to(this.query));
        } else {
            final List<String> segments = new ArrayList<>(this.c(null).path);
            String last = segments.isEmpty() ? "" : segments.removeLast();
            segments.add(last + "{" + cInt.of(coefficient) + "}");
            return new fURI(this.scheme, this.host, this.port, this.sstart, segments, this.send, this.poly, Query.to(this.query));
        }
    }

    public cInt cV() {
        if (this.c() == null)
            return C_ONE;
        return cInt.of(this.c());
    }

    public String c() {
        if (this.path.isEmpty())
            return null;
        final String last = this.path.getLast();
        final int left = last.indexOf('{');
        if (left == -1)
            return null;
        final int right = last.indexOf('}');
        if (right == -1)
            return null;
        else if (left > right)
            throw MTronException.of("malformed coefficient: %s", last);
        return last.substring(left + 1, right);
    }

    public fURI plus(final fURI furi) {
        if (this.isZero())
            return furi;
        if (furi.isZero())
            return this;
        if (this.basePath().matches(furi.basePath())) {
            cInt c1 = this.cV();
            cInt c2 = furi.cV();
            cInt c3 = c1.plus(c2);
            final Map<String, String> query = new LinkedHashMap<>();
            query.putAll(this.queryMap());
            query.putAll(furi.queryMap());
            return this.c(c3.toString()).queryMap(query);
        } else {
            return this.commonRoot(furi).c(this.cV().plus(furi.cV()).toString());
            //throw MTronException.of("only furis with the same path can be added: %s !+ %s", this, furi);
        }
    }

    public fURI mult(final fURI furi) {
        if (this.isOne())
            return furi;
        if (furi.isOne())
            return this;
        if (this.isZero() || furi.isZero())
            return this.zero();
        cInt c1 = this.cV();
        cInt c2 = furi.cV();
        cInt c3 = c1.mult(c2);
        Map<String, String> query = new LinkedHashMap<>();
        query.putAll(this.queryMap());
        query.putAll(furi.queryMap());
        return this.cLess().extend(furi).c(c3.toString()).queryMap(query);
    }

    public fURI neg() {
        return this.c(this.cV().neg().toString());
    }

    public Query query() {
        return this.query;
    }

    public Map<String, String> queryMap() {
        return null == this.query ? Map.of() : this.query.query;
    }

    public fURI basePath() {
        return this.cLess().qLess();
    }

    public fURI cLess() {
        return this.c(null);
    }

    public fURI dom() {
        return this.queryValue(DOM, fURI.class, fURI.ALL);
    }

    public fURI dom(final fURI domain) {
        return null == domain ? this.removeQ(DOM.toString()) : this.query(DOM, domain);
    }

    public fURI rng() {
        return this.queryValue(RNG, fURI.class, fURI.ALL);
    }

    public fURI rng(final fURI range) {
        return null == range ? this.removeQ(RNG.toString()) : this.query(RNG, range);
    }

    public fURI qLess() {
        return null == this.query ? this : new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, this.poly, null);
    }

    public <T> T queryValue(final fURI key, final Class<T> conversion, final T defaultValue) {
        return Optional.ofNullable(queryValue(key, conversion)).orElse(defaultValue);
    }

    public <T> T queryValue(final fURI key, final Class<T> conversion) {
        return Query.get(this.query, key.toString(), conversion);

    }

    public fURI path(final List<String> segments) {
        return new fURI(this.scheme, this.host, this.port, this.sstart, segments, this.send, this.poly, null == this.query ? null : this.query.toString());
    }

    public boolean onlyMatches(final fURI other) {
        return !this.equals(other) && this.matches(other);
    }

    public boolean bimatches(final fURI other) {
        return this.matches(other) || other.matches(this);
        /*boolean thisPattern = this.hasPattern();
        boolean otherPattern = other.hasPattern();
        if (thisPattern && otherPattern)
            return this.matches(other) || other.matches(this);
        else if (thisPattern)
            return other.matches(this);
        else if (otherPattern)
            return this.matches(other);
        else return this.equals(other);*/
    }

   /* public boolean matches(final fURI rhs, final Map<fURI, fURI> generics) {
        final fURI lhsBaseNorm = this.basePath().isGeneric() ? generics.getOrDefault(this.basePath(), this.basePath()) : this.basePath();
        final fURI lhsDomNorm = this.hasDom() && this.dom().isGeneric() ? generics.getOrDefault(this.dom(), this.dom()) : this.dom();
        final fURI lhsRngNorm = this.hasRng() && this.rng().isGeneric() ? generics.getOrDefault(this.rng(), this.rng()) : this.rng();
        final fURI lhsNorm = lhsBaseNorm.dom(lhsDomNorm).rng(lhsRngNorm);
        /// /////////////
        final fURI rhsBaseNorm = rhs.basePath().isGeneric() ? generics.getOrDefault(rhs.basePath(), rhs.basePath()) : rhs.basePath();
        final fURI rhsDomNorm = rhs.hasDom() && rhs.dom().isGeneric() ? generics.getOrDefault(rhs.dom(), rhs.dom()) : rhs.dom();
        final fURI rhsRngNorm = rhs.hasRng() && rhs.rng().isGeneric() ? generics.getOrDefault(rhs.rng(), rhs.rng()) : rhs.rng();
        final fURI rhsNorm = rhsBaseNorm.dom(rhsDomNorm).rng(rhsRngNorm);
        return lhsNorm.matches(rhsNorm);
    }*/

    public List<String> select(final fURI pattern) {
        final List<String> selection = new ArrayList<>();
        boolean fullMatch = false;
        for (int i = 0; i < this.path.size(); i++) {
            final String seg = this.path.get(i);
            if (fullMatch) {
                selection.add(seg);
            } else {
                if (pattern.path.size() <= i)
                    return List.of();
                final String pat = pattern.path.get(i);
                if (pat.equals("+")) {
                    selection.add(seg);
                } else if (pat.equals("#")) {
                    selection.add(seg);
                    fullMatch = true;
                } else if (!seg.equals(pat)) {
                    return List.of();
                }
            }
        }
        return selection;
    }

    public fURI resolve(final Map<fURI, fURI> generics) {
        final fURI cless = this.cLess();
        Graphitty.log(this).trace("resolving generics: %s", generics);
        if (cless.isGeneric()) {
            fURI lhs = cless.basePath().isGeneric() ?
                    cless.basePath().path(cless.basePath().segments().stream().map(s -> generics.computeIfAbsent(f(s), k -> f(s)).toString()).reduce("", (a, b) -> a + "/" + b).substring(1)) :
                    cless.basePath();
            if (cless.hasDom())
                lhs = cless.dom().isGeneric() ?
                        lhs.dom(cless.dom().path(cless.dom().segments().stream().map(s -> generics.computeIfAbsent(f(s), k -> f(s)).toString()).reduce("", (a, b) -> a + "/" + b).substring(1))) :
                        lhs.dom(cless.dom());
            if (cless.hasRng())
                lhs = cless.rng().isGeneric() ?
                        lhs.rng(cless.rng().path(cless.rng().segments().stream().map(s -> generics.computeIfAbsent(f(s), k -> f(s)).toString()).reduce("", (a, b) -> a + "/" + b).substring(1))) :
                        lhs.rng(cless.rng());
            Graphitty.log(this).trace("generics after resolution: %s", generics);
            return lhs.query(this.hasQuery() ? this.query.toString() : null).c(this.c());
        } else {
            return this;
        }
    }

    public boolean matches(final fURI rhs) {
        final C c = this.cV();
        final C d = rhs.cV();
        //if (c.isZero() && d.isZero())
        //    return true;
        if (c.within(d)) { // no need to check path as its noobj
            if (c.isZero())
                return true;
        } else
            return false;
        if (!rhs.hasPattern() && !this.hasPattern()) {
            if (!this.name().equals(rhs.name()))
                return false;
        }
        if (!Objects.equals(this.poly, rhs.poly)) {
            if (null != this.poly && null != rhs.poly) {
                for (int i = 0; i < rhs.poly.size(); i++) {
                    final fURI rp = f(rhs.poly.get(i));
                    if (rp.equals(ALL))
                        break;
                    if (i >= this.poly.size())
                        return false;
                    final fURI lp = f(this.poly.get(i));
                    if (!lp.matches(rp))
                        return false;
                }
            }
        }
        final fURI lhs = this.basePath();
        final fURI other = rhs.basePath();
        if (other.toString().equals(ALL_WILD_STRING))
            return true;
        if (!other.hasPattern())
            return lhs.equals(other);
        if (Objects.equals(other.scheme, ALL_WILD_STRING))
            return true;
        if (!Objects.equals(this.scheme, other.scheme) && (other.scheme == null || (!other.scheme.equals(ONE_WILD_STRING))))
            return false;
        if (Objects.equals(other.host, ALL_WILD_STRING))
            return true;
        if (!Objects.equals(this.host, other.host) && (other.host == null || (!other.host.equals(ONE_WILD_STRING))))
            return false;
        if (!(other.port <= -1) || !Objects.equals(other.host, ONE_WILD_STRING))
            if (this.port != -1 && (other.port == -1 || (other.port != 0 && this.port != other.port)))
                return false;
        if (this.isAbsolute() != other.isAbsolute())
            return false;
        if (this.sstart != other.sstart)
            return false;
        //if (!Objects.equals(this.host, other.host) && !Objects.equals(other.host, ONE_WILD_STRING))
        //    return false;
        //if (this.path.isEmpty() && other.toString().contains("#"))
        //   return true;
        for (int i = 0; i < other.path.size(); i++) {
            if (other.path.get(i).equals(ALL_WILD_STRING)) // #
                return true;
            if (!other.path.get(i).equals(ONE_WILD_STRING)) {
                if (this.path.size() <= i) // a/b a/b/c
                    return false;
                else if (!this.path.get(i).equals(other.path.get(i))) // a a
                    return false;
            }  // +
        }
        return this.path.size() == other.path.size() && this.send == other.send;
    }

    public boolean isCLessGeneric() {
        return this.cLess().isGeneric();
    }

    public boolean isGeneric() {
        if (this.path.isEmpty())
            return false;
        if (this.pathLength() == 1 && (this.path.getFirst().equals(ALL_WILD_STRING) || this.path.getFirst().equals(ONE_WILD_STRING)))
            return false;
        boolean hasCapitalGeneric = false;
        for (final String seg : this.c("1").path) { // TODO: this is necessary because {} is appended to final segment (needs to be fixed ASAP!).
            if (!seg.isEmpty() && seg.chars().allMatch(Character::isUpperCase))
                hasCapitalGeneric = true;
            if (seg.chars().anyMatch(c -> c != ALL_WILD_CHAR && c != ONE_WILD_CHAR && !Character.isAlphabetic(c) || Character.isLowerCase(c)))
                return false;
        }
        return hasCapitalGeneric;
    }

    public boolean equals(final Object other) {
        return other instanceof fURI &&
                //    this.toString().equals(other.toString());
                ((this.isZero() && ((fURI) other).isZero())
                        ||
                        (this.sstart == ((fURI) other).sstart &&
                                Objects.equals(this.cLess().path, ((fURI) other).cLess().path) &&
                                this.send == ((fURI) other).send &&
                                Objects.equals(this.query, ((fURI) other).query) &&
                                Objects.equals(this.cV(), ((fURI) other).cV()) &&
                                Objects.equals(this.scheme, ((fURI) other).scheme) &&
                                Objects.equals(this.host, ((fURI) other).host) &&
                                Objects.equals(this.port, ((fURI) other).port)));
    }

    public int hashCode() {
        return Objects.hash(this.scheme, this.host, this.port, this.sstart, this.path, this.send, this.query);
        //return this.toString().hashCode();
    }

    public String toString() {
        final StringBuilder b = new StringBuilder(null == this.scheme ? Tokens.EMPTY : this.scheme + SCHEMA_END);
        if (null != this.host)
            b.append(HOST_START).append(this.host).append(this.port == -1 ? "" : (":" + this.port));
        if (this.sstart)
            b.append(SEGMENT_SPLIT);
        for (final String path : this.path) {
            b.append(path.contains("{") ? path.substring(0, path.indexOf("{")) : path).append(SEGMENT_SPLIT);
        }
        if (!this.send && !this.path.isEmpty())
            b.delete(b.length() - 1, b.length());
        if (this.poly != null)
            b.append('[').append(this.poly.stream().reduce(",", (x, y) -> (x + ',' + y)).substring(2)).append(']');
        if (this.c() != null)
            b.append('{').append(this.c()).append('}');

        if (null != this.query && !this.query.query.isEmpty())
            b.append("?").append(Query.to(this.query));
        return b.toString();
    }

    public fURI clone() {
        try {
            fURI clone = (fURI) super.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }

    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Query {

        protected final static Query EMPTY_QUERY = new Query(Map.of());
        protected final Map<String, String> query;

        public Query(final Map<String, String> query) {
            this.query = query;
        }

        public static Query from(final String queryString) {
            return null == queryString || queryString.trim().isEmpty() ? null :
                    new Query(Stream.of(queryString.split(KVS_SPLIT))
                            .map(kv -> kv.split(KV_SPLIT))
                            .collect(Collectors.toMap(kv -> kv[0], kv -> kv.length == 1 ? Tokens.EMPTY : kv[1], (a, b) -> b, LinkedHashMap::new)));
        }

        public static String to(final Query query) {
            return (null == query || query.query.isEmpty()) ? null : query.toString();
        }

        public static Query from(final Map<String, String> query) {
            return null == query || query.isEmpty() ? null : new Query(query);
        }

        public static <T> T get(final Query query, final String key, final Class<T> conversion) {
            return null == query ? null : query.get(key, conversion);
        }

        public <T> T get(final String key, final Class<T> type) {
            final String value = this.query.get(key);
            if (null == value)
                return null;
            if (String.class.isAssignableFrom(type))
                return (T) value;
            else if (fURI.class.isAssignableFrom(type))
                return (T) fURI.of(value);
            else if (Long.class.isAssignableFrom(type))
                return (T) Long.valueOf(value);
            else throw MTronException.of("no known conversion of %s to %s", value, type);
        }

        public boolean hasKey(final String key) {
            return this.query.containsKey(key);
        }

        public int hashCode() {
            return this.query.hashCode();
        }

        public boolean equals(final Object other) {
            if (other instanceof Query otherQuery) {
                return this.query.equals(otherQuery.query);
            } else
                return false;
           /* if (!(other instanceof Query otherQuery))
                return false;
            if (this.query.size() != otherQuery.query.size())
                return false;
            if (this.query.keySet().stream().anyMatch(key -> !otherQuery.query.containsKey(key)))
                return false;
            return this.query.entrySet().stream().allMatch(entry -> {
                final String otherValue = otherQuery.query.get(entry.getKey());
                return entry.getValue().equals(otherValue) || f(entry.getValue()).bimatches(f(otherValue)); // TODO: should be directional (not bi-matches)
            });*/
        }

        public String toString() {
            if (query.isEmpty())
                return "";
            final StringBuilder sb = new StringBuilder();
            this.query.forEach((key, value) -> sb.append(key).append(null == value || value.isEmpty() ? Tokens.EMPTY : KV_SPLIT + value).append(KVS_SPLIT));
            sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }

    }

}
