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

package studio.phaseshift.metatron.furi.form;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.ifURI;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Collectors;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractfURI implements ifURI {

    @Override
    public ifURI scheme(final String scheme) {
        return ifURI.of(scheme, this.host(), this.port(), this.path(), this.c(), List.of(), this.qMap());
    }

    @Override
    public String scheme() {
        return null;
    }

    @Override
    public ifURI host(final String host) {
        return ifURI.of(this.scheme(), host, this.port(), this.path(), this.c(), List.of(), this.qMap());
    }

    @Override
    public String host() {
        return null;
    }


    @Override
    public int port() {
        return -1;
    }

    @Override
    public ifURI port(final int port) {
        return ifURI.of(this.scheme(), this.host(), port, this.path(), this.c(), List.of(), this.qMap());
    }


    @Override
    public List<String> path() {
        return List.of();
    }

    @Override
    public int pathLength() {
        return this.path().size();
    }

    @Override
    public ifURI path(final List<String> path) {
        return ifURI.of(this.scheme(), this.host(), this.port(), path, this.c(), List.of(), this.qMap());
    }

    @Override
    public ifURI path(final String path) {
        return ifURI.of(this.scheme(), this.host(), this.port(), List.of(path.split("/")), this.c(), List.of(), this.qMap());
    }

    @Override
    public ifURI c(final C<?, ?> coefficient) {
        return ifURI.of(this.scheme(), this.host(), this.port(), this.path(), coefficient, List.of(), this.qMap());
    }

    @Override
    public ifURI q(final Map<String, String> query) {
        return ifURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), List.of(), query);
    }


    @Override
    public boolean test(ifURI lhs) {
        return false;
    }

    @Override
    public ifURI prepend(final String segment) {
        if (segment.isEmpty() && this.path().getFirst().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>();
        final List<String> prefix = Arrays.asList(segment.split("/"));
        if ((segment.startsWith("/") && !this.pathString().startsWith("/")) || this.hasHost())
            newPath.add("");
        newPath.addAll(prefix);
        //   if (segment.endsWith("/"))
        //  newPath.add("");
        newPath.addAll(this.path().getFirst().isEmpty() ? this.path().subList(1, this.path().size()) : this.path());
        return ifURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), List.of(), this.qMap());
    }

    @Override
    public ifURI extend(final String segment) {
        if (segment.isEmpty() && this.path().getLast().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        final List<String> prefix = Arrays.asList(segment.split("/"));
        if (this.path().getLast().isEmpty())
            newPath.removeLast();
        if (this.path().isEmpty() && segment.startsWith("/"))
            newPath.add("");
        newPath.addAll(prefix);
        if (segment.endsWith("/"))
            newPath.add("");
        return ifURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), List.of(), this.qMap());
    }

    @Override
    public boolean hasPrefix(String prefix) {
        final List<String> prefixSegments = Arrays.asList(prefix.split("/"));
        if (prefixSegments.size() > this.path().size())
            return false;
        for (int i = 0; i < prefixSegments.size(); i++) {
            final String prefixSegment = prefixSegments.get(i);
            if (prefixSegment.equals("#") || prefixSegment.equals("+"))
                continue;
            final String pathSegment = this.path().get(i);
            if (!Objects.equals(prefixSegment, pathSegment))
                return false;
        }
        return true;
    }

    @Override
    public boolean hasPostfix(String postfix) {
        final List<String> postfixSegments = new ArrayList<>();
        Collections.addAll(postfixSegments, postfix.split("/"));
        if (postfix.endsWith("/"))
            postfixSegments.add("");
        if (postfixSegments.size() > this.path().size())
            return false;
        for (int i = 0; i < postfixSegments.size(); i++) {
            final String postfixSegment = postfixSegments.get(i);
            if (postfixSegment.equals("#") || postfixSegment.equals("+"))
                continue;
            final String pathSegment = this.path().get(this.path().size() - postfixSegments.size() + i);
            if (!Objects.equals(postfixSegment, pathSegment))
                return false;
        }
        return true;
    }

    @Override
    public ifURI pretract(final String segment) {
        if (segment.isEmpty() && this.path().getFirst().isEmpty())
            return ifURI.of(this.scheme(), this.host(), this.port(), this.path().subList(1, this.path().size()), this.c(), List.of(), this.qMap());
        if (this.hasPrefix(segment))
            return ifURI.of(this.scheme(), this.host(), this.port(), this.path().subList(segment.split("/").length, this.path().size()), this.c(), List.of(), this.qMap());
        return this;
    }

    @Override
    public ifURI pretract(final int steps) {
        return ifURI.of(this.scheme(), this.host(), this.port(), new ArrayList<>(this.path().subList(0, Math.max(0, this.path().size() - steps))), this.c(), List.of(), this.qMap());
    }


    @Override
    public ifURI retract(int steps) {
        if (steps <= 0)
            return this;
        if (steps > this.path().size())
            return ifURI.of(this.scheme(), this.host(), this.port(), Collections.emptyList(), this.c(), List.of(), this.qMap());
        final List<String> newPath = new ArrayList<>(this.path());
        newPath.remove(newPath.size() - steps);
        return ifURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), List.of(), this.qMap());
    }

    @Override
    public ifURI retractPattern() {
        final List<String> newPath = new ArrayList<>(this.path());
        while (newPath.getLast().equals("#") || newPath.getLast().equals("+")) {
            newPath.removeLast();
        }
        return ifURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), List.of(), this.qMap());
    }

    @Override
    public ifURI retract(final String segment) {
        if (segment.isEmpty() && this.path().getLast().isEmpty())
            return ifURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, this.path().size() - 1), this.c(), List.of(), this.qMap());
        if (this.hasPrefix(segment))
            return ifURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, this.path().size() - segment.split("/").length), this.c(), List.of(), this.qMap());
        return this;
    }


    @Override
    public C<?, ?> c() {
        return cInt.ONE();
    }


    @Override
    public ifURI dom() {
        return null;
    }


    @Override
    public ifURI rng() {
        return null;
    }


    @Override
    public String qString() {
        return String.join("&", this.qMap().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
    }

    @Override
    public Map<String, String> qMap() {
        return Map.of();
    }

    @Override
    public ifURI q(final String key, final Object value) {
        return null;
    }

    @Override
    public <T> T qValue(final String key, final Class<T> valueClass) {
        if (String.class.isAssignableFrom(valueClass))
            return (T) this.qMap().get(key);
        else if (Integer.class.isAssignableFrom(valueClass))
            return (T) Integer.valueOf(this.qMap().get(key));
        else if (Long.class.isAssignableFrom(valueClass))
            return (T) Long.valueOf(this.qMap().get(key));
        else if (Double.class.isAssignableFrom(valueClass))
            return (T) Double.valueOf(this.qMap().get(key));
        else if (Boolean.class.isAssignableFrom(valueClass))
            return (T) Boolean.valueOf(this.qMap().get(key));
        else
            throw MTronException.of("no known conversion of %s to %s", this.qMap().get(key), valueClass);
    }


    @Override
    public String q(final String key) {
        return this.qMap().get(key);
    }

    @Override
    public boolean isRelative() {
        return !this.path().isEmpty() && !this.path().getFirst().isEmpty();
    }

    @Override
    public boolean isBranch() {
        return this.path().isEmpty() || this.path().getLast().isEmpty();
    }

    @Override
    public String pathString() {
        return String.join("/", this.path());
    }


    @Override
    public ifURI head(final int steps) {
        if (steps >= this.pathLength())
            return this;
        return this.path().isEmpty() ? ifURI.of(Tokens.EMPTY) : ifURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, steps), this.c(), List.of(), this.qMap());
    }

    @Override
    public ifURI tail(final int steps) {
        if (steps >= this.pathLength())
            return this;
        return this.path().isEmpty() ? ifURI.of(Tokens.EMPTY) : ifURI.of(this.scheme(), this.host(), this.port(), this.path().subList(this.pathLength() - steps, this.pathLength()), this.c(), List.of(), this.qMap());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (null != scheme())
            sb.append(scheme()).append(":");
        if (null != host()) {
            sb.append("//");
            sb.append(host());
            if (-1 != port())
                sb.append(":").append(port());
        }
        if (!path().isEmpty())
            if (host() != null)
                sb.append("/");
        sb.append(path().stream().collect(Collectors.joining("/")));
        if (!c().isOne())
            sb.append("{").append(c().toString()).append("}");
        if (!qMap().isEmpty())
            sb.append("?").append(qString());
        return sb.toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof ifURI that))
            return false;
        return Objects.equals(this.scheme(), that.scheme())
                && Objects.equals(this.host(), that.host())
                && this.port() == that.port()
                && Objects.equals(this.path(), that.path())
                && Objects.equals(this.c(), that.c())
                && Objects.equals(this.qMap(), that.qMap());
    }

}
