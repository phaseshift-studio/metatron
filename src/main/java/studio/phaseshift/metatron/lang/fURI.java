/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang;

import studio.phaseshift.metatron.lang.obj.Coeff;
import studio.phaseshift.metatron.lang.obj.Uri;
import studio.phaseshift.metatron.lang.obj.mtron.MCoeff;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class fURI implements Cloneable {

    public static final String EMPTY = "";
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
    public static final fURI NONE = fURI.of("");
    public static final fURI NULL = null;
    public static final fURI DOM = fURI.of("dom");
    public static final fURI RNG = fURI.of("rng");

    public static fURI f(final String s) {
        return fURI.of(s);
    }

    private final String host;
    private final String scheme;
    private final int port;
    private final List<String> path;
    private final Query query;
    private boolean sstart;
    private boolean send;
    // private final boolean wildcard;


    public fURI big() {
        return null == Router.global() ? this : Router.global().rewrite(this, true);
    }

    public fURI small() {
        return null == Router.global() ? this : Router.global().rewrite(this, false);
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
        fURI base = fURI.of(common.stream().reduce(this.sstart ? SEGMENT_SPLIT : EMPTY, (a, b) -> a + b + SEGMENT_SPLIT));
        if (other.path.size() != this.path.size())
            return base.extend(ALL);
        final int extensionCount = maxLength - common.size();
        for (int i = 0; i < extensionCount; i++) {
            base = base.extend(SINGLE);
        }
        return base;
    }


    private fURI(final String scheme, final String host, final int port, final boolean sstart, final List<String> path, final boolean send, final String query) {
        this.host = host;
        this.scheme = scheme;
        this.port = port;
        this.path = path;
        this.query = Query.from(query);
        this.sstart = sstart;
        this.send = send;
    }

    public fURI(String uri) {
        if (null == uri || uri.isEmpty()) {
            this.scheme = null;
            this.host = null;
            this.port = -1;
            this.path = Collections.emptyList();
            this.sstart = false;
            this.send = false;
            this.query = null;
            return;
        }
        int queryPosition = uri.lastIndexOf(QUERY_START);
        if (queryPosition == -1 || uri.charAt(queryPosition - 1) == '[')
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
        int temp = uri.indexOf(HOST_START);
        if (i != -1 && (temp == -1 || i < temp)) {
            this.scheme = uri.substring(0, i);
            position = i + 3;
        } else {
            i = 0;
            this.scheme = null;
        }
        if (temp != -1) {
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
            return;
        }
        this.path = Arrays.asList(uri.substring(position).split(SEGMENT_SPLIT));
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

    public Uri toUri(final boolean schemaType) {
        final String scheme = this.scheme();
        return schemaType && null != scheme ?
                new MUri(this.scheme(null), fURI.of(scheme), null) :
                MUri.of(this);
    }

    public boolean hasPrefix(final fURI subfuri) {
        if (this.segments().size() < subfuri.segments().size())
            return false;
        for (int i = 0; i < subfuri.segments().size(); i++) {
            if (!f(this.segments().get(i)).matches(f(subfuri.segments().get(i))))
                return false;
        }
        return false;
    }

    public String name() {
        return this.segments().isEmpty() ? EMPTY : this.segments().get(this.segments().size() - 1);
    }

    public Uri toUri() {
        return this.toUri(true);
    }

    public boolean isBranch() {
        return this.send;
    }

    public boolean isNode() {
        return !this.send;
    }

    public fURI scheme(final String scheme) {
        return new fURI(scheme, this.host, this.port, this.sstart, this.path, this.send, Query.to(this.query));
    }

    public fURI path(final String path) {
        return new fURI(this.scheme, this.host, this.port, path.charAt(0) == SEGMENT_SPLIT_CHAR, Arrays.asList(path.split("/")), path.charAt(path.length() - 1) == SEGMENT_SPLIT_CHAR, Query.to(this.query));
    }

    public String path() {
        final String p = this.path.stream().reduce(this.sstart ? SEGMENT_SPLIT : EMPTY, (a, b) -> a + b + SEGMENT_SPLIT);
        return this.send ? p : p.substring(0, p.length() - 1);
    }


    public List<String> segments() {
        return Collections.unmodifiableList(this.path);
    }

    public fURI segments(final List<String> segs) {
        return new fURI(this.scheme, this.host, this.port, this.sstart, segs, this.send, Query.to(this.query));
    }

    public String scheme() {
        return this.scheme;
    }

    public fURI retractPattern() {
        if (!this.hasPattern())
            return this;
        fURI r = this;
        while (!r.segments().isEmpty()) {
            final String end = r.segments().get(r.segments().size() - 1);
            if (end.length() == 1 && (end.equals(ALL.toString()) || end.equals(SINGLE.toString())))
                r = r.retract();
            else
                break;
        }
        return r;
    }

    public String hostOrSegment() {
        return null != this.authority() ? this.host : this.path.get(0);
    }

    public String authority() {
        return null == this.host ? null : this.host + (this.port == -1 ? "" : SCHEMA_END + this.port);
    }

    public String host() {
        return this.host;
    }

    public int port(final int orElse) {
        return -1 == this.port ? orElse : this.port;
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
        return new fURI(this.scheme, this.host, this.port, !newPath.isEmpty() && null != this.host || segment.charAt(0) == SEGMENT_SPLIT_CHAR, newPath, !this.path.isEmpty() && this.send, Query.to(this.query));
    }

    public fURI extend(final fURI extension) {
        return this.extend(extension.toString());
    }

    public fURI extend(final String segment) {
        final List<String> newPath = new ArrayList<>(this.path.size() + 1);
        newPath.addAll(this.path);
        newPath.addAll(Arrays.asList(segment.split(SEGMENT_SPLIT)));
        return new fURI(this.scheme, this.host, this.port, this.sstart, newPath, !segment.isEmpty() && (segment.charAt(segment.length() - 1) == SEGMENT_SPLIT_CHAR), Query.to(this.query));
    }

    private fURI rePreTract(boolean retract, final int steps) {
        if (this.path.size() < steps)
            return new fURI(this.scheme, this.host, this.port, false, Collections.emptyList(), false, Query.to(this.query));
        final String coefficient = this.coefficient();
        final fURI noc = this.coefficientless();
        final List<String> newPath = retract ? noc.path.subList(0, noc.path.size() - steps) : noc.path.subList(steps, noc.path.size());
        if (!newPath.isEmpty() && null != coefficient) {
            newPath.set(newPath.size() - 1, newPath.get(newPath.size() - 1) + "[" + coefficient + "]");
        }
        return new fURI(this.scheme, this.host, this.port, this.sstart && !newPath.isEmpty(), newPath, this.send && !newPath.isEmpty(), Query.to(this.query));

    }

    public boolean isZero() {
        return this.equals(fURI.NONE) || this.coefficientValue().isZero();
    }


    public fURI removeSubpath(final fURI subpath) {
        String newPath = this.toString();
        return new fURI(newPath.replace(subpath.asBranch().toString(), EMPTY));
    }


    public fURI absolute() {
        fURI clone = this.clone();
        clone.sstart = true;
        return clone;
    }

    public fURI relative() {
        fURI clone = this.clone();
        clone.sstart = false;
        return clone;
    }

    public fURI asNode() {
        fURI clone = this.clone();
        clone.send = false;
        return clone;
    }

    public fURI asBranch() {
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
            tail.add(0, this.segments().get(this.segments().size() - (i + 1)));
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

    public boolean hasPattern() {
        return (null != this.host && this.host.indexOf(ALL_WILD_CHAR) != -1) ||
                this.path.toString().indexOf(ALL_WILD_CHAR) != -1 ||
                (null != this.host && this.host.indexOf(ONE_WILD_CHAR) != -1) ||
                this.path.toString().indexOf(ONE_WILD_CHAR) != -1;
    }

    private static boolean matchString(final String a, final String b) {
        final String[] as = a.split(SEGMENT_SPLIT);
        final String[] bs = b.split(SEGMENT_SPLIT);
        for (int i = 0; i < bs.length; i++) {
            if (bs[i].equals("#") || (bs[i].equals("+") && i == bs.length - 1))
                return true;
            if (bs[i].equals("+"))
                continue;
            if (as.length > i) {
                if (!as[i].equals(bs[i]))
                    return false;
            }
        }
        return true;
    }

    public boolean hasQuery() {
        return this.query != null;
    }

    public boolean hasQuery(final String key) {
        return this.query != null && this.query.hasKey(key);
    }

    public boolean hasDom() {
        return this.hasQuery(DOM.toString());
    }

    public boolean hasRng() {
        return this.hasQuery(RNG.toString());
    }

    public fURI query(final String query) {
        return new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, null == query || query.isEmpty() ? null : query);
    }

    public fURI query(final String key, final String value) {
        Map<String, String> appended = null == this.query ? new LinkedHashMap<>() : new LinkedHashMap<>(this.query.query);
        appended.put(key, value);
        return new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, Query.to(Query.from(appended)));
    }

    public fURI query(final Object key, final Object value) {
        return this.query(key.toString(), value.toString());
    }

    public fURI queryMap(final Map<String, String> kv) {
        return new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, Query.to(Query.from(kv)));
    }

    public fURI one() {
        return this.coefficient("1");
    }

    public fURI zero() {
        return this.coefficient("0");
    }

    public fURI any() {
        return this.coefficient("*");
    }

    public fURI maybe() {
        return this.coefficient("?");
    }

    public fURI some() {
        return this.coefficient("+");
    }

    public fURI coefficient(final String coefficient) {
        if (null == coefficient) {
            if (this.path.isEmpty())
                return this;
            if (this.path.get(this.path.size() - 1).indexOf('[') == -1)
                return this;
            final List<String> segments = new ArrayList<>(this.path);
            String last = segments.remove(segments.size() - 1);
            segments.add(last.substring(0, last.indexOf("[")));
            return new fURI(this.scheme, this.host, this.port, this.sstart, segments, this.send, Query.to(this.query));
        } else {
            final List<String> segments = new ArrayList<String>(this.coefficient(null).path);
            String last = segments.isEmpty() ? "" : segments.remove(segments.size() - 1);
            segments.add(last + "[" + MCoeff.Int.of(coefficient) + "]");
            return new fURI(this.scheme, this.host, this.port, this.sstart, segments, this.send, Query.to(this.query));
        }
    }

    public MCoeff.Int coefficientValue() {
        if (this.coefficient() == null)
            return MCoeff.Int.one();
        return MCoeff.Int.of(this.coefficient());
    }

    public String coefficient() {
        if (this.path.isEmpty())
            return null;
        final String last = this.path.get(this.path.size() - 1);
        final int left = last.indexOf('[');
        final int right = last.indexOf(']');
        if (left == -1 && right == -1)
            return null;
        else if (left > right)
            throw MTronException.of("malformed coefficient: %s", last);
        return last.substring(left + 1, right);
    }

    public fURI plus(final fURI furi) {
        if (this.basePath().matches(furi.basePath())) {
            MCoeff.Int c1 = this.coefficientValue();
            MCoeff.Int c2 = furi.coefficientValue();
            MCoeff.Int c3 = c1.plus(c2);
            Map<String, String> query = new LinkedHashMap<>();
            query.putAll(this.queryMap());
            query.putAll(furi.queryMap());
            return this.coefficient(c3.toString()).queryMap(query);
        } else {
            throw MTronException.of("only furis with the same path can be added: %s !+ %s", this, furi);
        }
    }

    public fURI mult(final fURI furi) {
        MCoeff.Int c1 = this.coefficientValue();
        MCoeff.Int c2 = furi.coefficientValue();
        MCoeff.Int c3 = c1.mult(c2);
        Map<String, String> query = new LinkedHashMap<>();
        query.putAll(this.queryMap());
        query.putAll(furi.queryMap());
        return this.coefficientless().extend(furi).coefficient(c3.toString()).queryMap(query);
    }

    public fURI neg() {
        return this.coefficient(this.coefficientValue().neg().toString());
    }

    public Query query() {
        return this.query;
    }

    public Map<String, String> queryMap() {
        return null == this.query ? Map.of() : this.query.query;
    }

    public fURI basePath() {
        return this.coefficientless().queryless();
    }

    public fURI coefficientless() {
        return this.coefficient(null);
    }

    public fURI dom() {
        return this.queryValue(DOM, fURI.class, fURI.ALL);
    }

    public fURI dom(final fURI domain) {
        return this.query(DOM, domain);
    }

    public fURI rng() {
        return this.queryValue(RNG, fURI.class, fURI.ALL);
    }

    public fURI rng(final fURI range) {
        return this.query(RNG, range);
    }

    public fURI queryless() {
        return null == this.query ? this : new fURI(this.scheme, this.host, this.port, this.sstart, this.path, this.send, null);
    }

    public <T> T queryValue(final fURI key, final Class<T> conversion, final T defaultValue) {
        return Optional.ofNullable(queryValue(key, conversion)).orElse(defaultValue);
    }

    public <T> T queryValue(final fURI key, final Class<T> conversion) {
        return Query.get(this.query, key.toString(), conversion);

    }

    public boolean onlyMatches(final fURI other) {
        return !this.equals(other) && this.matches(other);
    }

    public boolean bimatches(final fURI other) {
        return this.matches(other) || other.matches(this);
    }

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

    public boolean matches(final fURI rhs) {
        final Coeff coeff = this.coefficientValue();
        if (coeff.isZero() && rhs.coefficientValue().isZero())
            return true;
        if (coeff.isZero() && coeff.within(rhs.coefficientValue()))
            return true;
        if (!coeff.within(rhs.coefficientValue()))
            return false;
        final fURI lhs = this.basePath();
        final fURI other = rhs.basePath();
        if (!other.hasPattern())
            return lhs.equals(other);
        if (other.toString().equals("#"))
            return true;
        if (this.isAbsolute() != other.isAbsolute())
            return false;
        if (this.sstart != other.sstart)
            return false;
        if (Objects.equals(other.host, ALL_WILD_STRING))
            return true;
        if (!Objects.equals(this.host, other.host) && !Objects.equals(other.host, ONE_WILD_STRING))
            return false;
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

    public boolean equals(final Object other) {
        return other instanceof fURI &&
                //    this.toString().equals(other.toString());
                this.sstart == ((fURI) other).sstart &&
                Objects.equals(this.coefficientless().path, ((fURI) other).coefficientless().path) &&
                this.send == ((fURI) other).send &&
                Objects.equals(this.query, ((fURI) other).query) &&
                Objects.equals(this.coefficientValue(), ((fURI) other).coefficientValue()) &&
                Objects.equals(this.scheme, ((fURI) other).scheme) &&
                Objects.equals(this.host, ((fURI) other).host) &&
                Objects.equals(this.port, ((fURI) other).port);
    }

    public int hashCode() {
        return Objects.hash(this.scheme, this.host, this.port, this.sstart, this.path, this.send, this.query);
        //return this.toString().hashCode();
    }

    public String toString() {
        final StringBuilder b = new StringBuilder(null == this.scheme ? EMPTY : this.scheme + SCHEMA_END);
        if (null != this.host)
            b.append(HOST_START).append(this.authority());
        if (this.sstart)
            b.append(SEGMENT_SPLIT);
        for (final String path : this.path) {
            b.append(path).append(SEGMENT_SPLIT);
        }
        if (!this.send && !this.path.isEmpty())
            b.delete(b.length() - 1, b.length());
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
            return other instanceof Query && ((Query) other).query.equals(this.query);
        }

        public String toString() {
            if (query.isEmpty())
                return "";
            final StringBuilder sb = new StringBuilder();
            this.query.forEach((key, value) -> sb.append(key).append(null == value || value.isEmpty() ? EMPTY : KV_SPLIT + value).append(KVS_SPLIT));
            sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }

        public static Query from(final String queryString) {
            return null == queryString || queryString.trim().isEmpty() ? null :
                    new Query(Stream.of(queryString.split(KVS_SPLIT))
                            .map(kv -> kv.split(KV_SPLIT))
                            .collect(Collectors.toMap(kv -> kv[0], kv -> kv.length == 1 ? EMPTY : kv[1], (a, b) -> b, LinkedHashMap::new)));
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

    }

}
