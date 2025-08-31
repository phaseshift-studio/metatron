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

import net.sourceforge.urin.*;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class fURI {
    protected final UrinReference<String, Query<String>, Fragment<String>> urin;

    private fURI(final UrinReference<String, Query<String>, Fragment<String>> urin) {
        this.urin = urin;
    }

    public static fURI of(final String uri) {
        return new fURI(uri);
    }

    public BObj.Uri toUri(final boolean schemaType) {
        final String scheme = this.scheme();
        return schemaType && null != scheme ? new SObj.Uri(this.scheme(null), fURI.of(scheme)) : new SObj.Uri(this);
    }

    public fURI(final String uri) throws IllegalArgumentException {
        try {
            int colon = uri.indexOf(':');
            int slash = uri.indexOf('/');
            if (colon != -1 && colon < slash) {
                this.urin = Scheme.scheme(uri.substring(0, colon)).parseUrin(uri);
            } else {
                this.urin = Scheme.scheme("m").parseUrinReference(uri);
            }
        } catch (final ParseException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public List<String> segments() {
        final List<String> segs = new ArrayList<>(this.urin.path().segments().size());
        for (var s : this.urin.path().segments()) {
            segs.add(s.value());
        }
        return segs;
    }

    public fURI scheme(final String scheme) {
        if (null == this.urin.asUri().getScheme())
            return this;
        else {
            URI u = this.urin.asUri();
            String authority = u.getAuthority();
            String path = u.getPath();
            String query = u.getQuery();
            String newURI = "";
            if (null != scheme) {
                newURI += scheme;
                newURI += ":";
            }
            if (authority != null) {
                if (scheme != null)
                    newURI += "/";
                newURI += "/" + authority;
            }
            if (path != null) {
                if (authority != null)
                    newURI += "/";
                newURI += path;
            }
            if (query != null) {
                newURI += "?" + query;
            }
            return new fURI(newURI);
        }
    }

    public fURI path(final String path) {
        return new fURI(this.urin.withPath(Path.path(path)));
    }

    public String scheme() {
        return this.urin.asUri().getScheme();
    }

    public String hostOrSegment() {
        return this.urin.hasAuthority() ? this.urin.asUri().getAuthority() : this.urin.path().segments().get(0).value();
    }

    public boolean isAbsolute() {
        return this.urin.hasAuthority() || this.urin.asUri().toString().startsWith("/");
    }

    public fURI prepend(final String segment) {
        if (segment.isEmpty() || segment.equals("."))
            return this;

        final List<Segment<String>> path;
        if (segment.contains("/")) {
            path = new ArrayList<>(this.urin.path().segments());
            final List<Segment<String>> segments = Arrays.stream(segment.split("/"))
                    .filter(s -> !s.equals("."))
                    .map(Segment::segment)
                    .collect(Collectors.toList());
            Collections.reverse(segments);
            segments.forEach(s -> path.add(0, s));
        } else {
            path = new ArrayList<>(this.urin.path().segments());
            path.add(0, Segment.segment(segment));
        }
        return new fURI(this.urin.withPath(AbsolutePath.path(path)));
    }

    public fURI extend(final fURI extension) {
        return this.extend(extension.toString());
    }

    public fURI extend(final String segment) {
        if (segment.isEmpty() || segment.equals("."))
            return this;

        final List<Segment<String>> path;
        if (segment.contains("/")) {
            path = new ArrayList<>(this.urin.path().segments());
            path.addAll(Arrays.stream(segment.split("/"))
                    .filter(s -> !s.equals("."))
                    .map(Segment::segment)
                    .toList());
        } else {
            path = new ArrayList<>(this.urin.path().segments());
            path.add(Segment.segment(segment));
        }
        return new fURI(this.urin.withPath(AbsolutePath.path(path)));
    }

    private fURI rePreTract(boolean retract, final int steps) {
        final List<Segment<String>> path = this.urin.path().segments();
        if (path.size() < steps)
            return new fURI(this.urin.withPath(Path.path()));
        for (int i = 0; i < steps; i++) {
            if (retract)
                path.remove(path.size() - 1);
            else
                path.remove(0);
        }

        return new fURI(this.isAbsolute() ?
                this.urin.withPath(AbsolutePath.path(path)) :
                Scheme.scheme("m").relativeReference(Path.rootlessPath(path)));

    }

    public fURI retract(final int steps) {
        return this.rePreTract(true, steps);
    }

    public fURI pretract(final int steps) {
        return this.rePreTract(false, steps);
    }

    public boolean hasPattern() {
        return this.urin.toString().contains("#") || this.urin.toString().contains("+");
    }

    private static boolean matchString(final String a, final String b) {
        String[] as = a.split("/");
        String[] bs = b.split("/");
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
        return this.urin.hasQuery();
    }

    public Map<String, String> query() {
        if (!this.hasQuery())
            return Map.of();
        final Map<String, String> q = new HashMap<>();
        Arrays.stream(this.urin.query().value().split("&")).forEach(kv -> {
            String[] pairs = kv.split("=");
            q.put(pairs[0], pairs.length > 1 ? pairs[1] : "");
        });
        return q;
    }

    public fURI query(final Map<String, String> map) {
        final String queryString = map.entrySet().stream().map(kv -> kv.getValue().isEmpty() ? kv.getKey().toString() : kv.getKey() + "=" + kv.getValue()).reduce("", (a, b) -> a + "&" + b);
        return new fURI(Scheme.GenericScheme.scheme(this.urin.asUri().getScheme()).relativeReference(this.urin.authority(), AbsolutePath.path(this.urin.path()), Query.query(queryString)));
    }

    public boolean matches(final fURI other) {
        if (other.urin.asString().equals("#"))
            return true;
        if (!other.hasPattern())
            return this.urin.equals(other.urin);
        if (this.isAbsolute() != other.isAbsolute())
            return false;
        if ((this.urin.hasAuthority() && other.urin.hasAuthority()) &&
                (!(this.urin.authority().equals(other.urin.authority()) ||
                        other.urin.authority().host().equals(Host.registeredName("+")) ||
                        other.urin.authority().host().equals(Host.registeredName(""))))) {
            return false;
        }
        if (other.urin.path().segments().isEmpty() && other.urin.toString().contains("#"))
            return true;
        final List<Segment<String>> as = this.urin.path().segments();
        final List<Segment<String>> bs = other.urin.path().segments();
        for (int i = 0; i < bs.size(); i++) {
            if (!bs.get(i).hasValue()) // #
                return true;
            if (bs.get(i).value().equals("+")) // +
                continue;
            if (as.size() <= i) // a/b a/b/c
                return false;
            if (!as.get(i).equals(bs.get(i))) // a a
                return false;
        }
        return as.size() == bs.size();
    }

    public boolean equals(final Object other) {
        return other instanceof fURI && this.urin.equals(((fURI) other).urin);
    }

    public int hashCode() {
        return this.urin.hashCode();
    }

    public String toString() {
        return this.urin.toString();
    }

}
