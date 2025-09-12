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

import studio.phaseshift.metatron.lang.obj.base.Uri;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;


public class fURI implements Cloneable {

    public static final fURI ALL = fURI.of("#");
    public static final fURI ONE = fURI.of("+");
    public static final fURI NONE = null;
    public static final fURI DOM = fURI.of("dom");
    public static final fURI RNG = fURI.of("rng");


    private final String host;
    private final String scheme;
    private final int port;
    private final List<String> path;
    private final String query;
    private final boolean sstart;
    private boolean send;
    // private final boolean wildcard;

    private fURI(final String scheme, final String host, final int port, final boolean sstart, final List<String> path, final boolean send, final String query) {
        this.host = host;
        this.scheme = scheme;
        this.port = port;
        this.path = path;
        this.query = query;
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
        int queryPosition = uri.indexOf('?');
        final String tempQuery = queryPosition == -1 ? null : uri.substring(queryPosition + 1);
        this.query = null == tempQuery ? null : (tempQuery.isBlank() ? null : tempQuery);
        if (null != this.query)
            uri = uri.substring(0, queryPosition);
        this.send = uri.charAt(uri.length() - 1) == '/';

        int position = 0;
        int i = uri.indexOf(":");
        int temp = uri.indexOf("//");
        if (i != -1 && i < temp) {
            this.scheme = uri.substring(0, i);
            position = i + 3;
        } else {
            i = 0;
            this.scheme = null;
        }
        if (temp != -1) {
            temp = uri.indexOf("/", position + 1);
            final String[] authority = uri.substring(position, -1 == temp ? uri.length() : temp).split(":");
            if (temp == -1)
                temp = uri.length() - 1;
            this.host = authority[0];
            this.port = authority.length == 2 ? Integer.parseInt(authority[1]) : -1;
        } else {
            temp = uri.charAt(0) == '/' ? i + 1 : i;
            this.host = null;
            this.port = -1;
        }
        this.sstart = (this.host != null && null != this.scheme && uri.charAt(temp) == '/') || uri.charAt(0) == '/';
        position = null != this.scheme ? temp + 1 : temp;
        if (position == uri.length()) {
            this.path = Collections.emptyList();
            this.send = false;
            return;
        }
        this.path = Arrays.asList(uri.substring(position).split("/"));
    }

    public static fURI of(final String uri) {
        return new fURI(uri);
    }

    public Uri toUri(final boolean schemaType) {
        final String scheme = this.scheme();
        return schemaType && null != scheme ?
                new MUri(this.scheme(null), fURI.of(scheme), null) :
                MUri.of(this);
    }

    public String name() {
        return this.segments().get(this.segments().size() - 1);
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
        return new fURI(scheme, this.host, this.port, this.sstart, this.path, this.send, this.query);
    }

    public fURI path(final String path) {
        return new fURI(this.scheme, this.host, this.port, path.charAt(0) == '/', Arrays.asList(path.split("/")), path.charAt(path.length() - 1) == '/', this.query);
    }

    public List<String> segments() {
        return Collections.unmodifiableList(this.path);
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
            if (end.length() == 1 && (end.equals("#") || end.equals("+")))
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
        return null == this.host ? null : this.host + (this.port == -1 ? "" : ":" + this.port);
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
        newPath.addAll(Arrays.asList(segment.split("/")));
        newPath.addAll(this.path);
        return new fURI(this.scheme, this.host, this.port, !newPath.isEmpty() && null != this.host || segment.charAt(0) == '/', newPath, this.path.isEmpty() ? false : this.send, this.query);
    }

    public fURI extend(final fURI extension) {
        return this.extend(extension.toString());
    }

    public fURI extend(final String segment) {
        final List<String> newPath = new ArrayList<>(this.path.size() + 1);
        newPath.addAll(this.path);
        newPath.addAll(Arrays.asList(segment.split("/")));
        return new fURI(this.scheme, this.host, this.port, this.sstart, newPath, segment.charAt(segment.length() - 1) == '/', this.query);
    }

    private fURI rePreTract(boolean retract, final int steps) {
        if (this.path.size() < steps)
            return new fURI(this.scheme, this.host, this.port, false, Collections.emptyList(), false, this.query);
        final List<String> newPath = retract ? this.path.subList(0, this.path.size() - steps) : this.path.subList(steps, this.path.size());
        return new fURI(this.scheme, this.host, this.port, this.sstart && !newPath.isEmpty(), newPath, this.send && !newPath.isEmpty(), this.query);

    }

    public fURI removeSubpath(final fURI subpath) {
        String newPath = this.toString();
        return new fURI(newPath.replace(subpath.asBranch().toString(), ""));
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

    public fURI pretract() {
        return this.pretract(1);
    }

    public fURI pretract(final int steps) {
        return this.rePreTract(false, steps);
    }

    public boolean hasPattern() {
        return (null != this.host && this.host.contains("#")) ||
                this.path.toString().contains("#") ||
                (null != this.host && this.host.contains("+")) ||
                this.path.toString().contains("+");
    }

    private static boolean matchString(final String a, final String b) {
        final String[] as = a.split("/");
        final String[] bs = b.split("/");
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


    public Map<String, String> query() {
        if (null == this.query)
            return Map.of();
        final Map<String, String> q = new LinkedHashMap<>();
        Arrays.stream(this.query.split("&")).forEach(kv -> {
            String[] pairs = kv.split("=");
            q.put(pairs[0], pairs.length > 1 ? pairs[1] : "");
        });
        return q;
    }

    public <T> T queryValue(final fURI key, final Class<T> conversion, final T defaultValue) {
        return Optional.ofNullable(queryValue(key, conversion)).orElse(defaultValue);
    }

    public <T> T queryValue(final fURI key, final Class<T> conversion) {
        final String value = this.query().get(key.toString());
        if (null == value)
            return (T) null;
        if (String.class.isAssignableFrom(conversion))
            return (T) value;
        else if (fURI.class.isAssignableFrom(conversion))
            return (T) fURI.of(value);
        else if (Long.class.isAssignableFrom(conversion))
            return (T) Long.valueOf(value);
        else throw MTronException.of("no known conversion of %s to %s", value, conversion);
    }

    /*public fURI query(final Map<String, String> map) {
        final String queryString = map.entrySet().stream().map(kv -> kv.getValue().isEmpty() ? kv.getKey().toString() : kv.getKey() + "=" + kv.getValue()).reduce("", (a, b) -> a + "&" + b);
        return new fURI(Scheme.GenericScheme.scheme(this.urin.asUri().getScheme()).relativeReference(this.urin.authority(), AbsolutePath.path(this.urin.path()), Query.query(queryString)));
    }*/

    public boolean matches(final fURI other) {
        if (other.toString().equals("#"))
            return true;
        if (!other.hasPattern())
            return this.equals(other);
        if (this.isAbsolute() != other.isAbsolute())
            return false;
        if (this.sstart != other.sstart)
            return false;
        if (Objects.equals(other.host, "#"))
            return true;
        if (!Objects.equals(this.host, other.host) && !Objects.equals(other.host, "+"))
            return false;
        if (this.path.isEmpty() && other.toString().contains("#"))
            return true;
        for (int i = 0; i < other.path.size(); i++) {
            if (other.path.get(i).equals("#")) // #
                return true;
            if (other.path.get(i).equals("+")) // +
                continue;
            else if (this.path.size() <= i) // a/b a/b/c
                return false;
            else if (!this.path.get(i).equals(other.path.get(i))) // a a
                return false;
        }
        return this.path.size() == other.path.size();
    }

    public boolean equals(final Object other) {
        return other instanceof fURI &&
                this.toString().equals(other.toString());/*
                Objects.equals(this.scheme, ((fURI) other).scheme) &&
                Objects.equals(this.host, ((fURI) other).host) &&
                Objects.equals(this.port, ((fURI) other).port) &&
                this.sstart == ((fURI) other).sstart &&
                Objects.equals(this.path, ((fURI) other).path) &&
                this.send == ((fURI) other).send &&
                Objects.equals(this.query, ((fURI) other).query);*/
    }

    public int hashCode() {
        //  return Objects.hash(this.scheme, this.host, this.port, this.sstart, this.path, this.send, this.query);
        return this.toString().hashCode();
    }

    public String toString() {
        final StringBuilder b = new StringBuilder(null == this.scheme ? "" : this.scheme + ":");
        if (null != this.host)
            b.append("//").append(this.authority());
        if (this.sstart)
            b.append("/");
        for (final String path : this.path) {
            b.append(path).append("/");
        }
        if (!this.send && !this.path.isEmpty())
            b.delete(b.length() - 1, b.length());
        if (null != this.query)
            b.append("?").append(this.query);
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

}
