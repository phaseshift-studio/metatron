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

import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.ifURI;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.furi.fURI.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractfURI implements ifURI {

    @Override
    public ifURI resolve() {
        final List<String> newSegments = new ArrayList<>();
        for (final String seg : this.path()) {
            if (seg.equals("."))
                continue;
            if (seg.equals("..") && !newSegments.isEmpty() && !newSegments.getLast().equals(".."))
                newSegments.removeLast();
            else
                newSegments.add(seg);
        }
        return this.path(newSegments);
    }

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
    public ifURI dom(final ifURI dom) {
        return this;
    }

    @Override
    public ifURI rng(final ifURI rng) {
        return this;
    }

    @Override
    public boolean hasPattern() {
        if (Objects.equals(this.scheme(), "#") || Objects.equals(this.scheme(), "+"))
            return true;
        if (Objects.equals(this.host(), "#") || Objects.equals(this.host(), "+"))
            return true;
        for (String segment : this.path()) {
            if (segment.equals("#") || segment.equals("+"))
                return true;
        }
        return this.qMap().entrySet().stream().anyMatch(kv -> {
            if (kv.getValue().equals("#") || kv.getValue().equals("+"))
                return true;
            if (kv.getKey().equals("#") || kv.getKey().equals("+"))
                return true;
            return false;
        });
    }

    @Override
    public List<String> poly() {
        return List.of();
    }


    @Override
    public ifURI basePath() {
        return ifURI.of(this.scheme(), this.host(), this.port(), this.path(), cInt.ONE(), List.of(), Map.of());
    }

    @Override
    public boolean test(final ifURI lhs) {
        final C c = this.c();
        final C d = lhs.c();
        //if (c.isZero() && d.isZero())
        //    return true;
        if (c.within(d)) { // no need to check path as its noobj
            if (c.isZero())
                return true;
        } else
            return false;
        if (!lhs.hasPattern() && !this.hasPattern()) {
            if (!this.name().equals(lhs.name()))
                return false;
        }
        /*if (!Objects.equals(this.poly(), lhs.poly())) {
            if (null != this.poly() && null != lhs.poly()) {
                for (int i = 0; i < lhs.poly().size(); i++) {
                    final ifURI rp = ifURI.of(lhs.poly().get(i));
                    if (rp.toString().equals("#"))
                        break;
                    if (i >= this.poly().size())
                        return false;
                    final ifURI lp = ifURI.of(this.poly().get(i));
                    if (!lp.test(rp))
                        return false;
                }
            }
        }*/
        if (lhs.toString().equals("#"))
            return true;
        if (Objects.equals(lhs.scheme(), "#"))
            return true;
        if (!Objects.equals(this.scheme(), lhs.scheme()) && !Objects.equals(lhs.scheme(), "+"))
            return false;
        if (Objects.equals(lhs.host(), "#"))
            return true;
        if (!Objects.equals(this.host(), lhs.host()) && !Objects.equals(lhs.host(), "+"))
            return false;
        if (!(lhs.port() == -1) || !Objects.equals(lhs.host(), "+"))
            if (this.port() != -1 && (lhs.port() == -1 || (lhs.port() != 0 && this.port() != lhs.port())))
                return false;
        if (!lhs.hasPattern())
            return this.path().equals(lhs.path());
        if (this.isAbsolute() != lhs.isAbsolute())
            return false;
        // if (this.path().getFirst().isEmpty() != lhs.path().getFirst().isEmpty())
        //    return false;
        //if (!Objects.equals(this.host, other.host) && !Objects.equals(other.host, ONE_WILD_STRING))
        //    return false;
        //if (this.path.isEmpty() && other.toString().contains("#"))
        //   return true;
        for (int i = 0; i < lhs.path().size(); i++) {
            if (lhs.path().get(i).equals("#")) // #
                return true;
            if (!lhs.path().get(i).equals("+")) {
                if (this.pathLength() <= i) // a/b a/b/c
                    return false;
                else if (!this.path().get(i).equals(lhs.path().get(i))) // a a
                    return false;
            }  // +
        }
        if (this.path().size() != lhs.path().size()) // && this.path().getLast().isEmpty() == lhs.path().getLast().isEmpty();
            return false;
        // TODO: this is a later addition to the matching semantics of furi. 
        // currently this behavior is handled specially for inst sets (dom/rng-selection).
        // by having it here, this allows any space to leverage query pattern matching.
        for (final Map.Entry<String, String> kv : lhs.qMap().entrySet()) {
            if (this.qMap().entrySet().stream().noneMatch(xy -> f(xy.getKey()).test(f(kv.getKey())) &&
                    (kv.getValue().isEmpty() || kv.getValue().equals("+") || Objects.equals(xy.getValue(), kv.getValue()))))
                return false;
        }
        return true;
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
    public ifURI neg() {
        return ifURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c().neg(), List.of(), this.qMap());
    }

    @Override
    public ifURI mult(final ifURI other) {
        final List<String> newPath = new ArrayList<>(this.path());
        if (!other.path().isEmpty()) {
            if (!newPath.isEmpty() && newPath.getLast().isEmpty())
                newPath.removeLast();
            newPath.addAll(other.path().getFirst().isEmpty() ? other.path().subList(1, other.path().size()) : other.path());
        }
        final Map<String, String> newQ = new LinkedHashMap<>(this.qMap());
        newQ.putAll(other.qMap());
        return ifURI.of(this.scheme(), this.host(), this.port(), newPath, ((C) this.c()).mult(other.c()), List.of(), newQ);
    }

    @Override
    public ifURI plus(final ifURI other) {
        if (Objects.equals(this.scheme(), other.scheme()) &&
                Objects.equals(this.host(), other.host()) &&
                Objects.equals(this.port(), other.port()) &&
                Objects.equals(this.path(), other.path())) {
            final Map<String, String> newQ = new LinkedHashMap<>(this.qMap());
            newQ.putAll(other.qMap());
            return ifURI.of(this.scheme(), this.host(), this.port(), this.path(), ((C) this.c()).plus(other.c()), List.of(), newQ);
        } else {
            throw MTronException.of("unable to add %s to %s", other, this);
        }
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

    private boolean hasBlankCap(final boolean prefix) {
        return !this.path().isEmpty() && (prefix ? this.path().getFirst().isEmpty() : this.path().getLast().isEmpty());
    }

    @Override
    public ifURI pretract(final int steps) {
        if (steps == 0)
            return this;
        if (steps >= this.pathLength())
            return ifURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), List.of(), this.qMap());
        boolean hasBlank = this.hasBlankCap(true);
        List<String> newPath = new ArrayList<>(this.path());
        if (hasBlank) newPath.removeFirst();
        for (int i = steps; i < newPath.size(); i++) {
            newPath.removeFirst();
        }
        if (hasBlank) newPath.addFirst("");
        if (newPath.stream().allMatch(String::isEmpty)) {
            newPath.clear();
        }
        return ifURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), List.of(), this.qMap());
    }


    @Override
    public ifURI retract(int steps) {
        if (steps == 0)
            return this;
        if (steps >= this.pathLength())
            return ifURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), List.of(), this.qMap());
        boolean hasBlank = this.hasBlankCap(false);
        List<String> newPath = new ArrayList<>(this.path());
        if (hasBlank) newPath.removeLast();
        for (int i = 0; i < steps; i++) {
            newPath.removeLast();
        }
        if (hasBlank) newPath.addLast("");
        if (newPath.stream().allMatch(String::isEmpty)) {
            newPath.clear();
        }
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
        if (this.hasPrefix(segment))
            return ifURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, this.path().size() - segment.split("/").length), this.c(), List.of(), this.qMap());
        return this;
    }


    @Override
    public C<?, ?> c() {
        return cInt.ONE();
    }

    @Override
    public boolean hasQ(final String key) {
        return this.qMap().containsKey(key);
    }

    @Override
    public ifURI dom() {
        if (this.hasQ(DOM))
            return ifURI.of(this.q(DOM));
        return Singleton.WILD_ALL;
    }


    @Override
    public ifURI rng() {
        if (this.hasQ(RNG))
            return ifURI.of(this.q(RNG));
        return Singleton.WILD_ALL;
    }


    @Override
    public String qString() {
        return String.join("&", this.qMap().entrySet().stream().map(e -> e.getKey() + (e.getValue().isEmpty() ? "" : ("=" + e.getValue()))).toList());
    }

    @Override
    public Map<String, String> qMap() {
        return Map.of();
    }

    @Override
    public ifURI q(final String key, final Object value) {
        final Map<String, String> newQ = new HashMap<>(this.qMap());
        newQ.put(key, null == value ? "" : value.toString());
        return ifURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), List.of(), newQ);
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
        if (steps == 0)
            return ifURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), List.of(), this.qMap());
        if (steps >= this.pathLength())
            return this;
        boolean hasBlankRight = this.hasBlankCap(false);
        boolean hasBlankLeft = this.hasBlankCap(true);
        final List<String> newPath = new ArrayList<>(this.path().subList(0, steps + (hasBlankLeft ? 1 : 0)));
        if (hasBlankRight && !newPath.isEmpty() && !newPath.getLast().isEmpty())
            newPath.addLast("");
        return ifURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), List.of(), this.qMap());
        // return ifURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, steps + (hasBlank ? 1 : 0)), this.c(), List.of(), this.qMap());
    }

    @Override
    public ifURI tail(final int steps) {
        if (steps == 0)
            return ifURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), List.of(), this.qMap());
        if (steps >= this.pathLength())
            return this;
        boolean hasBlankRight = this.hasBlankCap(false);
        boolean hasBlankLeft = this.hasBlankCap(true);
        final List<String> newPath = new ArrayList<>(this.path().subList(((this.path().size() - steps) - (hasBlankRight ? 1 : 0)), this.path().size()));
        if (hasBlankLeft && !newPath.isEmpty() && !newPath.getFirst().isEmpty())
            newPath.addFirst("");
        return ifURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), List.of(), this.qMap());
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
