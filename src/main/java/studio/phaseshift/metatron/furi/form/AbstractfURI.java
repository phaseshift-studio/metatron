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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractfURI implements fURI {

    @Override
    public List<String> segments() {
        final List<String> path = this.path();
        if (path.isEmpty() || (!path.getFirst().isEmpty() && !path.getLast().isEmpty()))
            return path;
        if (path.getFirst().isEmpty() && path.getLast().isEmpty())
            return path.subList(1, path.size() - 1);
        if (path.getFirst().isEmpty())
            return path.subList(1, path.size());
        if (path.getLast().isEmpty())
            return path.subList(0, path.size() - 1);
        throw MTronException.of("invalid path: %s", path);
    }

    @Override
    public int segmentLength() {
        final List<String> path = this.path();
        if (path.isEmpty() || (!path.getFirst().isEmpty() && !path.getLast().isEmpty()))
            return path.size();
        if (path.getFirst().isEmpty() && path.getLast().isEmpty())
            return path.size() - 2;
        if (path.getFirst().isEmpty())
            return path.size() - 1;
        if (path.getLast().isEmpty())
            return path.size() - 1;
        throw MTronException.of("invalid path: %s", path);
    }


    @Override
    public fURI asAbsolute() {
        if (!this.path().isEmpty() && this.path().getFirst().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        if (!newPath.isEmpty() && !newPath.getFirst().isEmpty())
            newPath.addFirst("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }

    @Override
    public fURI asRelative() {
        if (this.path().isEmpty() || !this.path().getFirst().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        if (!newPath.isEmpty() && newPath.getFirst().isEmpty())
            newPath.removeFirst();
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }

    @Override
    public fURI asNode() {
        if (this.path().isEmpty() || !this.path().getLast().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        if (!newPath.isEmpty() && newPath.getLast().isEmpty())
            newPath.removeLast();
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }

    @Override
    public fURI asBranch() {
        if (!this.path().isEmpty() && this.path().getLast().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        if (!newPath.isEmpty() && !newPath.getLast().isEmpty())
            newPath.add("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }

    @Override
    public fURI resolve() {
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
    public fURI scheme(final String scheme) {
        return fURI.of(scheme, this.host(), this.port(), this.path(), this.c(), this.poly(), this.qMap());
    }

    @Override
    public String scheme() {
        return null;
    }

    @Override
    public fURI host(final String host) {
        return fURI.of(this.scheme(), host, this.port(), this.path(), this.c(), this.poly(), this.qMap());
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
    public fURI port(final int port) {
        return fURI.of(this.scheme(), this.host(), port, this.path(), this.c(), this.poly(), this.qMap());
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
    public fURI path(final List<String> path) {
        if (this.host() != null && (!path.isEmpty() && !path.getFirst().isEmpty())) {
            final List<String> newPath = new ArrayList<>(path);
            newPath.addFirst("");
            return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
        }
        return fURI.of(this.scheme(), this.host(), this.port(), path, this.c(), this.poly(), this.qMap());
    }

    @Override
    public fURI path(final String path) {
        return fURI.of(this.scheme(), this.host(), this.port(), List.of(path.split("/")), this.c(), this.poly(), this.qMap());
    }

    @Override
    public fURI c(final cInt coefficient) {
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), coefficient, this.poly(), this.qMap());
    }

    @Override
    public fURI q(final Map<String, String> query) {
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), null == query ? Map.of() : query);
    }

    @Override
    public fURI dom(final fURI dom) {
        return this.q(DOM, dom);

    }

    @Override
    public fURI rng(final fURI rng) {
        return this.q(RNG, rng);
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
    public boolean hasPattern(final String pattern) {
        if (Objects.equals(this.scheme(), pattern))
            return true;
        if (Objects.equals(this.host(), pattern))
            return true;
        for (String segment : this.path()) {
            if (Objects.equals(segment, pattern))
                return true;
        }
        return this.qMap().entrySet().stream().anyMatch(kv -> {
            if (Objects.equals(kv.getValue(), pattern))
                return true;
            if (Objects.equals(kv.getKey(), pattern))
                return true;
            return false;
        });
    }

    @Override
    public List<String> poly() {
        return List.of();
    }

    @Override
    public fURI poly(final List<String> poly) {
        if (Objects.equals(this.poly(), poly))
            return this;
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), poly, this.qMap());
    }


    @Override
    public int compareTo(final fURI furi) {
        if (null == furi) return -1;
        if (this.equals(furi)) return 0;
        if (Objects.equals(this.host(), "#"))
            return 1;
        if (!Objects.equals(this.host(), furi.host()) && !Objects.equals(this.host(), "+"))
            return -1;
        if (!Objects.equals(this.poly(), furi.poly()))
            return -1;
        for (int i = 0; i < this.path().size(); i++) {
            final String segment = this.path().get(i);
            if (segment.equals("#"))
                return 1;
            if (furi.pathLength() <= i)
                return -1;
            if (!segment.equals("+") && !segment.equals(furi.path().get(i)))
                return -1;
        }
        return (this.path().size() > furi.pathLength() || furi.pathLength() == this.path().size() && this.hasPattern()) ? 1 : -1;
    }


    @Override
    public fURI basePath() {
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), cInt.ONE(), List.of(), Map.of());
    }

    @Override
    public boolean test(final fURI rhs) {
        final cInt c = this.c();
        final cInt d = rhs.c();
        if (c.isZero() && d.isZero())
            return true;
        if (c.within(d)) { // no need to check path as its noobj
            if (c.isZero())
                return true;
        } else
            return false;
        if (rhs.one().equals(ALL))
            return true;
        if (!rhs.hasPattern() && !this.hasPattern()) {
            if (!this.name().equals(rhs.name()))
                return false;
        }
        if (!this.poly().isEmpty()) {
            if (!Objects.equals(this.poly(), rhs.poly())) {
                if (null != this.poly() && null != rhs.poly()) {
                    for (int i = 0; i < rhs.poly().size(); i++) {
                        final fURI rp = f(rhs.poly().get(i));
                        if (rp.equals(ALL))
                            break;
                        if (i >= this.poly().size())
                            return false;
                        if (rp.equals(WILD_ONE))
                            continue;
                        final fURI lp = f(this.poly().get(i));
                        if (!lp.test(rp))
                            return false;
                    }
                }
            }
        }
        if (Objects.equals(rhs.scheme(), "#"))
            return true;
        if (!Objects.equals(this.scheme(), rhs.scheme()) && !Objects.equals(rhs.scheme(), "+"))
            return false;
        if (Objects.equals(rhs.host(), "#"))
            return true;
        if (!Objects.equals(this.host(), rhs.host()) && !Objects.equals(rhs.host(), "+"))
            return false;
        if (!(rhs.port() == -1) || !Objects.equals(rhs.host(), "+"))
            if (this.port() != -1 && (rhs.port() == -1 || (rhs.port() != 0 && this.port() != rhs.port())))
                return false;
        if (!rhs.hasPattern())
            return this.path().equals(rhs.path());
        if (this.isAbsolute() != rhs.isAbsolute())
            return false;
        for (int i = 0; i < rhs.path().size(); i++) {
            if (rhs.path().get(i).equals("#")) // #
                return true;
            if (!rhs.path().get(i).equals("+")) {
                if (this.pathLength() <= i) // a/b a/b/c
                    return false;
                else if (!this.path().get(i).equals(rhs.path().get(i))) // a a
                    return false;
            }  // +
        }
        if (this.path().size() != rhs.path().size()) // && this.path().getLast().isEmpty() == rhs.path().getLast().isEmpty();
            return false;
        // TODO: this is a later addition to the matching semantics of furi. 
        // currently this behavior is handled specially for inst sets (dom/rng-selection).
        // by having it here, this allows any space to leverage query pattern matching.
        for (final Map.Entry<String, String> kv : rhs.qMap().entrySet()) {
            if (this.qMap().entrySet().stream().noneMatch(xy -> f(xy.getKey()).test(f(kv.getKey())) &&
                    (kv.getValue().isEmpty() || kv.getValue().equals("+") || Objects.equals(xy.getValue(), kv.getValue()))))
                return false;
        }
        return true;
    }

    @Override
    public fURI prepend(final String segment) {
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
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }

    @Override
    public fURI extend(final String segment) {
        if (segment.isEmpty() && !this.path().isEmpty() && this.path().getLast().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        final List<String> prefix = Arrays.asList(segment.split("/"));
        if (!this.path().isEmpty() && this.path().getLast().isEmpty())
            newPath.removeLast();
        if (this.path().isEmpty() && segment.startsWith("/"))
            newPath.add("");
        newPath.addAll(prefix);
        if (segment.endsWith("/"))
            newPath.add("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }

    @Override
    public boolean hasPrefix(String prefix) {
        final fURI prefixURI = f(prefix);
        if (prefixURI.hasScheme() && (!this.hasScheme() || !this.scheme().equals(prefixURI.scheme())))
            return false;
        if (prefixURI.hasAuthority() && (!this.hasAuthority() || !this.authority().equals(prefixURI.authority())))
            return false;
        for (int i = 0; i < prefixURI.pathLength(); i++) {
            if (this.pathLength() <= i)
                return false;
            if (!this.path().get(i).equals(prefixURI.path().get(i)))
                return false;
        }
        return true;
    }

    @Override
    public fURI neg() {
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c().neg(), this.poly(), this.qMap());
    }

    @Override
    public fURI mult(final fURI other) {
        if (other.isZero())
            return Singleton.NOOBJ;
        final List<String> newPath = new ArrayList<>(this.path());
        if (!other.path().isEmpty()) {
            if (!newPath.isEmpty() && newPath.getLast().isEmpty())
                newPath.removeLast();
            newPath.addAll(other.path().getFirst().isEmpty() ? other.path().subList(1, other.path().size()) : other.path());
        }
        final Map<String, String> newQ = new LinkedHashMap<>(this.qMap());
        newQ.putAll(other.qMap());
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c().mult(other.c()), this.poly(), newQ).resolve();
    }

    @Override
    public fURI removeQ(final String key) {
        if (this.qMap().isEmpty() || !this.qMap().containsKey(key))
            return this;
        final Map<String, String> newQ = new LinkedHashMap<>(this.qMap());
        newQ.remove(key);
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), newQ);
    }

    @Override
    public fURI plus(final fURI other) {
        if (other.isZero())
            return this;
        if (Objects.equals(this.scheme(), other.scheme()) &&
                Objects.equals(this.host(), other.host()) &&
                Objects.equals(this.port(), other.port()) &&
                Objects.equals(this.path(), other.path())) {
            final Map<String, String> newQ = new LinkedHashMap<>(this.qMap());
            newQ.putAll(other.qMap());
            return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c().plus(other.c()), this.poly(), newQ).resolve();
        } else {
            final Map<String, String> newQ = new LinkedHashMap<>(this.qMap());
            newQ.putAll(other.qMap());
            return fURI.of(null, null, -1, List.of("#"), this.c().plus(other.c()), this.poly(), newQ).resolve();
            // throw MTronException.of("unable to add %s to %s", other, this);
        }
    }

    @Override
    public boolean hasPostfix(final String postfix) {
        return this.toString().endsWith(postfix);
        /*
        final List<String> postfixSegments = new ArrayList<>();
        Collections.addAll(postfixSegments, postfix.split("/"));
        if (postfix.endsWith("/"))
            postfixSegments.add("");
        if (postfixSegments.size() > this.path().size())
            return false;
        for (int i = 0; i < postfixSegments.size(); i++) {
            final String postfixSegment = postfixSegments.get(i);
            // if (postfixSegment.equals("#") || postfixSegment.equals("+"))
            //     continue;
            final String pathSegment = this.path().get(this.path().size() - postfixSegments.size() + i);
            if (!Objects.equals(postfixSegment, pathSegment))
                return false;
        }
        return true;*/
    }

    @Override
    public fURI pretract(final String segment) {
        if (segment.isEmpty() && !this.path().isEmpty() && this.path().getFirst().isEmpty())
            return fURI.of(this.scheme(), this.host(), this.port(), this.path().subList(1, this.path().size()), this.c(), this.poly(), this.qMap());
        if (this.hasPrefix(segment))
            return this.pretract(segment.split("/").length);
        return this;
    }

    private boolean hasBlankCap(final boolean prefix) {
        return !this.path().isEmpty() && (prefix ? this.path().getFirst().isEmpty() : this.path().getLast().isEmpty());
    }

    @Override
    public fURI pretract(final int steps) {
        if (steps == 0)
            return this;
        if (steps >= this.pathLength())
            return fURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), this.poly(), this.qMap());
        boolean hasBlank = this.hasBlankCap(true);
        List<String> newPath = new ArrayList<>(this.path());
        if (hasBlank) newPath.removeFirst();
        for (int i = 0; i < steps; i++) {
            newPath.removeFirst();
        }
        if (hasBlank && !newPath.isEmpty() && !newPath.getFirst().isEmpty()) newPath.addFirst("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }


    @Override
    public fURI retract(int steps) {
        if (steps == 0)
            return this;
        if (steps >= this.pathLength())
            return fURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), this.poly(), this.qMap());
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
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }

    @Override
    public fURI retractPattern() {
        if (this.path().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        boolean hasBlank = this.hasBlankCap(false);
        while (!newPath.isEmpty() && (newPath.getLast().isEmpty() || newPath.getLast().equals("#") || newPath.getLast().equals("+"))) {
            newPath.removeLast();
        }
        if (hasBlank && !newPath.isEmpty() && !newPath.getLast().isEmpty())
            newPath.addLast("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
    }


    @Override
    public fURI retract(final String segment) {
        if (this.hasPrefix(segment))
            return fURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, this.path().size() - segment.split("/").length), this.c(), this.poly(), this.qMap());
        return this;
    }


    @Override
    public cInt c() {
        return cInt.ONE();
    }

    @Override
    public boolean hasQ(final String key) {
        return this.qMap().containsKey(key);
    }

    @Override
    public fURI dom() {
        if (this.hasQ(DOM))
            return this.qValue(DOM, fURI.class);
        return ALL;
    }


    @Override
    public fURI rng() {
        if (this.hasQ(RNG))
            return this.qValue(RNG, fURI.class);
        return ALL;
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
    public fURI q(final String key, final Object value) {
        final Map<String, String> newQ = new HashMap<>(this.qMap());
        newQ.put(key, null == value ? "" : value.toString());
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), newQ);
    }

    @Override
    public <T> T qValue(final String key, final Class<T> valueClass) {
        if (String.class.isAssignableFrom(valueClass))
            return (T) this.qMap().get(key);
        else if (fURI.class.isAssignableFrom(valueClass))
            return (T) f(this.qMap().get(key));
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
        if (null == key)
            return null;
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
    public fURI head(final int steps) {
        if (steps == 0)
            return fURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), this.poly(), this.qMap());
        if (steps >= this.pathLength())
            return this;
        boolean hasBlankRight = this.hasBlankCap(false);
        boolean hasBlankLeft = this.hasBlankCap(true);
        final List<String> newPath = new ArrayList<>(this.path().subList(0, steps + (hasBlankLeft ? 1 : 0)));
        if (hasBlankRight && !newPath.isEmpty() && !newPath.getLast().isEmpty())
            newPath.addLast("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
        // return fURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, steps + (hasBlank ? 1 : 0)), this.c(), List.of(), this.qMap());
    }

    @Override
    public fURI tail(final int steps) {
        if (steps == 0)
            return fURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), this.poly(), this.qMap());
        if (steps >= this.pathLength())
            return this;
        boolean hasBlankRight = this.hasBlankCap(false);
        boolean hasBlankLeft = this.hasBlankCap(true);
        final List<String> newPath = new ArrayList<>(this.path().subList(((this.path().size() - steps) - (hasBlankRight ? 1 : 0)), this.path().size()));
        if (hasBlankLeft && !newPath.isEmpty() && !newPath.getFirst().isEmpty())
            newPath.addFirst("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap());
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
        sb.append(this.path().stream().collect(Collectors.joining("/")));
        if (!this.poly().isEmpty())
            sb.append("[").append(String.join(",", this.poly())).append("]");
        if (!this.c().isOne())
            sb.append("{").append(this.c().toString()).append("}");
        if (!this.qMap().isEmpty())
            sb.append("?").append(this.qString());
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.scheme(), /*this.host(), this.port(),*/ this.path(), this.c() /*this.poly(), this.qMap()*/);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof fURI that))
            return false;
        return Objects.equals(this.scheme(), that.scheme())
                && Objects.equals(this.host(), that.host())
                && this.port() == that.port()
                && Objects.equals(this.path(), that.path())
                && Objects.equals(this.poly(), that.poly())
                && Objects.equals(this.c(), that.c())
                && Objects.equals(new HashMap<>(this.qMap()), new HashMap<>(that.qMap()));
    }

}
