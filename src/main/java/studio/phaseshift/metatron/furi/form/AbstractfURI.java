/*
 * metatron: a distributed virtual machine and language
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
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.ARGS;
import static studio.phaseshift.metatron.Tokens.DOM;
import static studio.phaseshift.metatron.Tokens.RNG;
import static studio.phaseshift.metatron.furi.fURI.Component.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractfURI implements fURI {

    /**
     * The keys that lead the canonical query, in the order the instruction signature names them:
     * {@code inst?rng<=dom(args)} reads rng before dom, and {@code args} is the parenthesized tail.
     */
    private static final List<String> Q_LEAD = List.of(RNG, DOM, ARGS);

    /**
     * THE query-order comparator, held as a constant so a map can be recognized as already being in normal form (a
     * {@code TreeMap} answers the very comparator it was built with) — which is what keeps a write path that already
     * produced a canonical map from sorting it a second time.
     */
    static final Comparator<String> Q_ORDER = AbstractfURI::compareQKeys;

    /**
     * An EMPTY query map in normal form — the map every query-bearing uri extends, and the answer for a uri that
     * carries no query at all.
     */
    public static SortedMap<String, String> sortedQ() {
        // TreeMap, not an immutable sorted copy: a TreeMap's iteration order is a function of the COMPARATOR, so no
        // put/remove/putAll — by us or by anyone holding qMap() — can produce a differently ordered query. The order
        // is enforced by the data structure, not by a convention every future construction path must remember.
        return new TreeMap<>(Q_ORDER);
    }

    /**
     * THE canonical query map: {@code query} in the normal form {@link #compareQKeys} defines. This is the one and
     * only way a q map enters a uri (every form with a query is a {@code SAPXCQfURI}, whose constructor calls here),
     * so the order is established ONCE, at write time, and every later read — {@link #qString()} above all — is a
     * plain iteration with no comparator, sort or temporary list. {@code null} and empty are the shared empty sorted
     * map; a map already ordered by {@link #Q_ORDER} is adopted as is.
     */
    public static SortedMap<String, String> canonicalQ(final Map<String, String> query) {
        if (null == query || query.isEmpty())
            return Collections.emptySortedMap();
        if (query instanceof SortedMap<String, String> sorted && sorted.comparator() == Q_ORDER)
            return sorted;
        final SortedMap<String, String> canonical = sortedQ();
        canonical.putAll(query);
        return canonical;
    }

    /**
     * A MUTABLE canonical map seeded with {@code query} — the accumulator for the write paths that add, remove or
     * merge query components ({@code q}, {@code plus}, {@code mult}, {@code removeQ}, {@code extend}, {@code addQ},
     * {@code copyQ}, {@code args}, {@link #qMap()}). The order is the map's own, so the mutation order cannot matter.
     */
    public static SortedMap<String, String> mutableQ(final Map<String, String> query) {
        final SortedMap<String, String> canonical = sortedQ();
        if (null != query)
            canonical.putAll(query);
        return canonical;
    }

    @Override
    public List<String> segments() {
        if (this.isEmpty())
            return List.of();
        final List<String> path = this.path();
        if (path.isEmpty() || (!path.getFirst().isEmpty() && !path.getLast().isEmpty()))
            return path;
        if (path.getFirst().isEmpty() && path.getLast().isEmpty() && path.size() > 1)
            return path.subList(1, path.size() - 1);
        if (path.getFirst().isEmpty())
            return path.subList(1, path.size());
        if (path.getLast().isEmpty())
            return path.subList(0, path.size() - 1);
        throw MTronException.of("invalid path: %s", path);
    }

    /**
     * {@link #segments()}'s size without building the sublist: the branches mirror segments() one for one, guard
     * included. The root {@code /} is the one-element path {@code [""]}, whose single blank is both the absolute and
     * the branch marker; without {@code size() > 1} it is dropped twice and the root answers -1.
     */
    @Override
    public int segmentLength() {
        if (this.isEmpty())
            return 0;
        final List<String> path = this.path();
        if (path.isEmpty() || (!path.getFirst().isEmpty() && !path.getLast().isEmpty()))
            return path.size();
        if (path.getFirst().isEmpty() && path.getLast().isEmpty() && path.size() > 1)
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
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
    }

    @Override
    public fURI asRelative() {
        if (this.path().isEmpty() || !this.path().getFirst().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        if (!newPath.isEmpty() && newPath.getFirst().isEmpty())
            newPath.removeFirst();
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
    }

    @Override
    public fURI asNode() {
        if (this.path().isEmpty() || !this.path().getLast().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        if (!newPath.isEmpty() && newPath.getLast().isEmpty())
            newPath.removeLast();
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
    }

    @Override
    public fURI asBranch() {
        if (!this.path().isEmpty() && this.path().getLast().isEmpty())
            return this;
        final List<String> newPath = new ArrayList<>(this.path());
        if (!newPath.isEmpty() && !newPath.getLast().isEmpty())
            newPath.add("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
    }

    /*
     * The canonical (dot-free) form, computed AT MOST ONCE per uri.
     *
     * A uri is compared and hashed far more often than it is created, and the canonical form is what equality
     * MEANS. resolve() used to be called from equals() (both sides, every comparison) and built a whole new fURI
     * whenever a dot was present — inside the resolver's inner loop that is not merely slow, it hangs: see
     * target/hang-stack.txt, where ScoringResolver.checkArgs -> Type.isRootType -> Objects.equals -> fURI.equals
     * -> resolve -> hasSentinel spun for 472s of CPU with a SHALLOW stack (a loop, not recursion).
     *
     * Both fields are volatile and the computation is pure, so a race between threads recomputes the same value
     * instead of corrupting one. `canonical` is written LAST: a reader that sees it is guaranteed to see
     * `dotFound` too, and resolve() can then return the cached instance without touching the path at all.
     */
    private transient volatile fURI canonical;
    private transient volatile boolean dotFound;

    /*
     * The other cached-derivation slots, all pure functions of this instance's (final) fields, so a race can only
     * recompute the same value. `hash` and `string` are set last-after-compute so a reader either sees the finished
     * value or recomputes it. `hash` uses 0 as "not computed" and is never stored as 0 (a computed 0 becomes -1,
     * which satisfies the hashCode contract just as well and keeps the sentinel unambiguous).
     */
    private transient volatile int hash;
    private transient volatile String string;
    private transient volatile fURI base;
    private transient volatile Boolean generic;

    @Override
    public fURI resolve() {
        final fURI cached = this.canonical;
        if (null != cached)
            return cached;
        if (this.isEmpty()) {
            this.dotFound = false;
            this.canonical = this;
            return this;
        }
        final List<String> folded = foldDotSegments(this.path());
        // Return `this` not merely when no dot was SEEN but when the fold CHANGES NOTHING. A leading `..` is
        // kept by the fold, so it survives with an identical path — and rebuilding an equal-but-fresh instance
        // there made resolve() non-idempotent (resolve(resolve(x)) != resolve(x) by identity) and cost an
        // allocation per call. Anything that reasons about whether a resolve changed the uri depends on this.
        final fURI canonical = (null == folded || folded.equals(this.path())) ? this : this.path(folded);
        this.dotFound = (null != folded); // `..` present but kept still IS a dot
        this.canonical = canonical;
        return canonical;
    }

    @Override
    public boolean hasDotSegments() {
        this.resolve(); // answers both questions from the same pass, and caches it
        return this.dotFound;
    }

    @Override
    public boolean isGeneric() {
        // Consulted first thing on every BasicMemory.redirect (the big()/small() hot path) and pure in the path, so
        // the character scan is done once per uri rather than once per clone.
        final Boolean cached = this.generic;
        if (null != cached)
            return cached;
        final boolean computed = fURI.super.isGeneric();
        this.generic = computed;
        return computed;
    }

    /**
     * The folded segments, or null when nothing folds — i.e. when there is no `.` or `..` segment at all, which is
     * the common case and must stay allocation-free.
     */
    private static List<String> foldDotSegments(final List<String> path) {
        List<String> folded = null;
        for (int i = 0; i < path.size(); i++) {
            final String seg = path.get(i);
            if (seg.equals(".")) {
                if (null == folded) folded = new ArrayList<>(path.subList(0, i)); // the `.` is dropped
                continue;
            }
            if (seg.equals("..")) {
                // materialize the prefix BEFORE deciding: a `..` must be able to pop a segment that precedes it,
                // whether or not an earlier `.`/`..` had already started the fold. Getting this wrong is silent —
                // the `..` is dropped and the path is returned unfolded, which reads as "the dot was ignored".
                if (null == folded) folded = new ArrayList<>(path.subList(0, i));
                if (!folded.isEmpty() && !folded.getLast().equals(".."))
                    folded.removeLast();
                else
                    folded.add(seg); // a leading `..`, or one after another `..`, is KEPT (a displacement)
                continue;
            }
            if (null != folded)
                folded.add(seg);
        }
        return (null == folded) ? null : List.copyOf(folded); // null <=> no `.` or `..` segment at all
    }

    @Override
    public fURI scheme(final String scheme) {
        return fURI.of(scheme, this.host(), this.port(), this.path(), this.c(), this.poly(), this.qMap(), this.templates());
    }

    @Override
    public String scheme() {
        return null;
    }

    @Override
    public fURI host(final String host) {
        return fURI.of(this.scheme(), host, this.port(), this.path(), this.c(), this.poly(), this.qMap(), this.templates());
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
        return fURI.of(this.scheme(), this.host(), port, this.path(), this.c(), this.poly(), this.qMap(), this.templates());
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
        if (this.host() != null && (!path.isEmpty() && !path.getFirst().isEmpty() && !path.getFirst().startsWith("/"))) {
            final List<String> newPath = new ArrayList<>(path);
            newPath.addFirst("");
            return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
        }
        return fURI.of(this.scheme(), this.host(), this.port(), path, this.c(), this.poly(), this.qMap(), this.templates());
    }

    @Override
    public fURI path(final String path) {
        return this.path(Arrays.stream(path.split("/")).toList());
    }

    @Override
    public fURI c(final cInt coefficient) {
        // A uri is immutable, so re-applying the coefficient it already carries is the identity — returning `this`
        // instead of a fresh equal copy is what keeps BasicMemory.redirect allocation-free on an unrouted address.
        if (Objects.equals(this.c(), coefficient))
            return this;
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), coefficient, this.poly(), this.qMap(), this.templates());
    }

    @Override
    public fURI q(final Map<String, String> query) {
        // normal form on the way in: every caller (parser, setters, copyQ, algebra on the query) lands here
        final SortedMap<String, String> newQ = canonicalQ(query);
        if (Objects.equals(this.qMap(), newQ))
            return this;
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), newQ, this.templates());
    }

    @Override
    public fURI dom(final fURI dom) {
        return this.q(DOM, dom);

    }

    protected static List<String> cleanPath(final List<String> path) {
        if (null == path || path.size() < 2)
            return path;
        if (path.size() == 2 && path.getFirst().isEmpty() && path.getLast().isEmpty())
            return path.subList(0, path.size() - 1);
        /*List<String> newPath = path;
        if (newPath.getFirst().isEmpty() && newPath.get(1).isEmpty())
            newPath = newPath.subList(1, newPath.size());
        if (newPath.getLast().isEmpty() && newPath.get(newPath.size() - 2).isEmpty())
            newPath = newPath.subList(0, newPath.size() - 1);*/
        return path;
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
        // plain loop over the query (a stream allocates a pipeline per call)
        for (final Map.Entry<String, String> kv : this.qMap().entrySet()) {
            if (kv.getValue().equals("#") || kv.getValue().equals("+") || kv.getKey().equals("#") || kv.getKey().equals("+"))
                return true;
        }
        return false;
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
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), poly, this.qMap(), this.templates());
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
        // THE THREE LESSERS, composed: basePath is exactly cLess + qLess + polyLess -- the coefficient, the query
        // and the bracket erased, and nothing else (scheme, host, port, path and templates survive). Spelling it as
        // the composition is what ENFORCES that relationship instead of leaving it to a hand-built fURI.of(...) that
        // happens to agree today: add a fourth lesser and basePath must take it, or a lesser's contract changes and
        // basePath follows.
        // Pure in this instance's own fields, and hit twice per BasicMemory.redirect (once per routing table), so it
        // is derived once. The derived uri is a different object, so no recursion back into this slot.
        final fURI cached = this.base;
        if (null != cached)
            return cached;
        final fURI computed = this.cLess().qLess().polyLess();
        this.base = computed;
        return computed;
    }

    @Override
    public boolean test(final fURI rhs) {
        if (null == rhs)
            return false;
        final cInt c = this.c();
        final cInt d = rhs.c();
        if (c.isZero() && d.isZero())
            return true;
        if (c.within(d)) { // no need to check path as its noobj
            if (c.isZero())
                return true;
        } else
            return false;
        // rhs.one() would allocate a fresh fURI per call; the comparison against ALL
        // (path ["#"], no scheme/host/poly/query, coefficient normalized away by one()) can be inlined
        if (rhs.path().size() == 1
                && Objects.equals("#", rhs.path().getFirst())
                && null == rhs.scheme()
                && null == rhs.host()
                && rhs.poly().isEmpty()
                && rhs.qMap().isEmpty()
                && (null == rhs.templates() || rhs.templates().isEmpty()))
            return true;
        if (!rhs.hasPattern() && !this.hasPattern()) {
            if (!this.name().equals(rhs.name()))
                return false;
        }
        /* THE BRACKET RULE. A uri's bracket is its TYPE ARGUMENT list: `person[role]::T` declares a type
         * parameterized by `role`, and `person[ninja]` instantiates it, where `ninja` refines `role`. So the LHS's
         * entries are CLAIMS that the RHS entry at the same position must ACCEPT, and `test` walks the LHS. The
         * three cases:
         *
         *   1. a bare LHS claims nothing, so it passes a bracketed RHS       person.test(person[ninja])    -> true
         *   2. a bracketed LHS tested against a bare RHS has no acceptor     person[ninja].test(person)    -> false
         *   3. a generic RHS entry accepts any claim within its coefficient  person[ninja].test(person[A]) -> true
         *
         * The direction is ASYMMETRIC because an instantiation is a SPECIALIZATION: it passes where the parameter
         * is expected, never the reverse. Erasure ("person[ninja] is a person::T") is therefore explicit at the type
         * level -- vid().polyLess() -- while covariance (ninja refines role) stays Obj.test's: this method compares
         * names, never types. # (ALL) accepts this and every remaining claim, + (WILD_ONE) accepts exactly one.
         */
        final List<String> lhsPoly = this.poly();
        final List<String> rhsPoly = rhs.poly();
        if (!lhsPoly.isEmpty()) { // case 1: a bare lhs claims nothing, so there is nothing to check
            if (rhsPoly.isEmpty()) // case 2: a specialization tested where the erased parameter is expected
                return false;
            for (int i = 0, n = Math.max(lhsPoly.size(), rhsPoly.size()); i < n; i++) { // walk the CLAIMS
                if (i >= rhsPoly.size()) // a claim with no acceptor at its position
                    return false;
                final fURI rp = f(rhsPoly.get(i));
                if (rp.equals(ALL)) // # accepts this and every remaining claim
                    break;
                if (i >= lhsPoly.size()) // an argument the lhs supplies no claim for
                    return false;
                if (rp.equals(WILD_ONE)) // + accepts exactly one
                    continue;
                final fURI lp = f(lhsPoly.get(i));
                // case 3: a generic rhs entry (A, B, ...) accepts any NAME within its coefficient; two generics
                // still compare names, as a parameter claim is a specific variable, not any variable
                if (!(rp.isGeneric() && !lp.isGeneric() ? lp.c().within(rp.c()) : lp.test(rp)))
                    return false;
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
        if (rhs.path().size() == 1 && rhs.path().getFirst().equals("#"))
            return true;
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
        if (null == segment)
            return this;
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
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
    }

    @Override
    public fURI extend(final String segment) {
        if (null == segment)
            return this;
        if (segment.isEmpty() && !this.path().isEmpty() && this.path().getLast().isEmpty())
            return this.asBranch();
        final List<String> newPath = new ArrayList<>(this.path());
        final List<String> prefix = new ArrayList<>(List.of(segment.split("/")));
        if (!newPath.isEmpty() && newPath.getLast().isEmpty())
            newPath.removeLast();
        if (!newPath.isEmpty() && (!prefix.isEmpty() && prefix.getFirst().isEmpty()))
            prefix.removeFirst();
        newPath.addAll(prefix);
        if (segment.endsWith("/"))
            newPath.add("");
        if (this.path().size() == 1 && this.path().getFirst().isEmpty())
            newPath.addFirst("");
        final fURI f;
        if (segment.contains("?")) {
            final SortedMap<String, String> qs = mutableQ(this.qMap());
            qs.putAll(f(segment).qMap());
            f = fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), qs, this.templates());
        } else
            f = fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
        return f.hasHost() ? f.asAbsolute() : f;
    }

    @Override
    public boolean hasPrefix(final fURI prefix) {
        if (null == prefix)
            return false;
        if (prefix.hasPattern()) {
            fURI running = this;
            while (!running.isEmpty() && running.segmentLength() > 0) {
                if (running.bimatches(prefix))
                    return this.hasPrefix(running);
                running = running.isBranch() ? running.asNode() : running.retract(1).asBranch();
            }
            return false;
        } else {
            final fURI prefixURI = prefix;
            if (prefixURI.hasScheme() && (!this.hasScheme() || !this.scheme().equals(prefixURI.scheme())))
                return false;
            if (prefixURI.hasAuthority() && (!this.hasAuthority() || !this.authority().equals(prefixURI.authority())))
                return false;
            int prefixLen = prefixURI.pathLength();
            // A trailing empty segment (artifact of a trailing slash like
            // "/usr/dr/" -> ["","usr","dr",""]) should not block matching
            // the next real segment in the target URI.
            if (prefixLen > 0 && prefixURI.path().get(prefixLen - 1).isEmpty())
                prefixLen--;
            for (int i = 0; i < prefixLen; i++) {
                if (this.pathLength() <= i)
                    return false;
                if (!this.path().get(i).equals(prefixURI.path().get(i)))
                    return false;
            }
            return true;
        }
    }

    @Override
    public fURI neg() {
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c().neg(), this.poly(), this.qMap(), this.templates());
    }

    @Override
    public fURI mult(final fURI other) {
        if (other.isZero())
            return Singleton.NOOBJ;
        final List<String> newPath = new ArrayList<>(this.resolve().path());
        final fURI otherResolved = other.resolve();
        if (!otherResolved.path().isEmpty()) {
            if (!newPath.isEmpty() && newPath.getLast().isEmpty())
                newPath.removeLast();
            newPath.addAll(otherResolved.path().getFirst().isEmpty() ? otherResolved.path().subList(1, otherResolved.path().size()) : otherResolved.path());
        }
        final SortedMap<String, String> newQ = mutableQ(this.qMap());
        newQ.putAll(otherResolved.qMap());
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c().mult(otherResolved.c()), this.poly(), newQ, this.templates()).resolve();
    }

    @Override
    public fURI removeQ(final String key) {
        if (this.qMap().isEmpty() || !this.qMap().containsKey(key))
            return this;
        final SortedMap<String, String> newQ = mutableQ(this.qMap());
        newQ.remove(key);
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), newQ, this.templates());
    }

    @Override
    public fURI plus(final fURI other) {
        if (other.isEmpty())
            return this;
        if (this.isEmpty())
            return other;
        if (this.isZero())
            return other;
        if (other.isZero())
            return this;
        if (Objects.equals(this.scheme(), other.scheme()) &&
                Objects.equals(this.host(), other.host()) &&
                Objects.equals(this.port(), other.port())) {
            final SortedMap<String, String> newQ = mutableQ(this.qMap());
            newQ.putAll(other.qMap());
            final boolean samePath = Objects.equals(this.path(), other.path());
            final List<String> path = samePath ? this.path() : mergePaths(this.path(), other.path(), this.c(), other.c());
            // same path → two copies, so the multiplicity is the coefficient sum; a factored branch already encodes
            // the union in the `{…}` segment (per-element operand coefficients moved into it), so its coefficient stays one.
            final cInt coefficient = samePath ? this.c().plus(other.c()) : cInt.ONE();
            return fURI.of(this.scheme(), this.host(), this.port(), path, coefficient, this.poly(), newQ, this.templates());
        } else {
            final SortedMap<String, String> newQ = mutableQ(this.qMap());
            newQ.putAll(other.qMap());
            return fURI.of(null, null, -1, List.of("#"), this.c().plus(other.c()), this.poly(), newQ, this.templates());
            // throw MTronException.of("unable to add %s to %s", other, this);
        }
    }

    /**
     * Factored path union. The longest common prefix and suffix are factored out, and the divergent middles are
     * collapsed into one flat branch — a middle that is itself a {@code {…}} branch is flattened into its elements, so
     * {@code a/{b,d}/c + a/c/c} → {@code a/{b,c,d}/c}. Two coefficient moves happen here: an operand's coefficient is
     * carried onto its branch element (so {@code a/b/c{2,3} + a/d/c{-3,-2}} → {@code a/{{2,3}b,{-3,-2}d}/c}), and when
     * a middle is a branch of cardinality {@code n} the shared suffix it carried is marked {@code {n}} per segment (the
     * 2-fold vs 1-fold encoding). The result is always exactly the same set of paths.
     */
    public static List<String> mergePaths(final List<String> lhs, final List<String> rhs, final cInt lhsCoefficient, final cInt rhsCoefficient) {
        int i = 0;
        while (i < lhs.size() && i < rhs.size() && lhs.get(i).equals(rhs.get(i)))
            i++;
        int j = 0;
        while (j < lhs.size() - i && j < rhs.size() - i && lhs.get(lhs.size() - 1 - j).equals(rhs.get(rhs.size() - 1 - j)))
            j++;

        final List<String> lhsMiddle = new ArrayList<>(lhs.subList(i, lhs.size() - j));
        final List<String> rhsMiddle = new ArrayList<>(rhs.subList(i, rhs.size() - j));
        final List<String> elements = new ArrayList<>();
        for (final String element : flatten(lhsMiddle))
            elements.add(coefficientMark(lhsCoefficient, element));
        for (final String element : flatten(rhsMiddle))
            elements.add(coefficientMark(rhsCoefficient, element));
        elements.sort(Comparator.comparing((String e) -> atom(e).isEmpty()).thenComparing(AbstractfURI::atom));

        final int coefficient = Math.max(branchCardinality(lhsMiddle), branchCardinality(rhsMiddle));

        final List<String> merged = new ArrayList<>(lhs.subList(0, i));
        merged.add("{" + String.join(",", elements) + "}");
        for (final String segment : lhs.subList(lhs.size() - j, lhs.size()))
            merged.add(coefficient > 1 ? "{" + coefficient + "}" + segment : segment);
        return merged;
    }

    /**
     * Prefixes an element with its operand coefficient when it is not the multiplicative identity — {@code {2,3}b};
     * the identity leaves the atom bare.
     */
    private static String coefficientMark(final cInt coefficient, final String element) {
        return coefficient.equals(cInt.ONE()) ? element : "{" + coefficient + "}" + element;
    }

    /**
     * The atom of a branch element, stripping a leading {@code {cInt}} so sorting compares atoms, not the coefficient.
     */
    private static String atom(final String element) {
        if (element.length() > 1 && element.charAt(0) == '{') {
            final int close = element.indexOf('}');
            return close > 0 ? element.substring(close + 1) : element;
        }
        return element;
    }

    private static List<String> flatten(final List<String> middle) {
        if (1 == middle.size() && isBranch(middle.get(0)))
            return Arrays.asList(middle.get(0).substring(1, middle.get(0).length() - 1).split(","));
        return List.of(String.join("/", middle));
    }

    private static boolean isBranch(final String segment) {
        return segment.length() > 1 && segment.charAt(0) == '{' && segment.charAt(segment.length() - 1) == '}';
    }

    private static int branchCardinality(final List<String> middle) {
        if (1 == middle.size() && isBranch(middle.get(0)))
            return middle.get(0).substring(1, middle.get(0).length() - 1).split(",").length;
        return 1;
    }

    @Override
    public boolean hasPostfix(final String postfix) {
        if (null == postfix)
            return false;
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
        if (null == segment)
            return this;
        if (segment.isEmpty() && !this.path().isEmpty() && this.path().getFirst().isEmpty())
            return fURI.of(this.scheme(), this.host(), this.port(), this.path().subList(1, this.path().size()), this.c(), this.poly(), this.qMap(), this.templates());
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
            return fURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), this.poly(), this.qMap(), this.templates());
        boolean hasBlank = this.hasBlankCap(true);
        List<String> newPath = new ArrayList<>(this.path());
        if (hasBlank) newPath.removeFirst();
        for (int i = 0; i < steps; i++) {
            newPath.removeFirst();
        }
        if (hasBlank && !newPath.isEmpty() && !newPath.getFirst().isEmpty()) newPath.addFirst("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
    }


    @Override
    public fURI retract(int steps) {
        if (steps == 0)
            return this;
        if (steps >= this.pathLength())
            return fURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), this.poly(), this.qMap(), this.templates());
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
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
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
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
    }


    @Override
    public fURI retract(final String segment) {
        if (this.hasPrefix(segment))
            return fURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, this.path().size() - segment.split("/").length), this.c(), this.poly(), this.qMap(), this.templates());
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


    /**
     * The query components in CANONICAL order, so that a uri has one spelling and not as many as there are ways to
     * fill its map.
     * <p>
     * THE STORAGE IS IN NORMAL FORM. {@link #qMap()} is a {@link SortedMap} ordered by {@link #compareQKeys}, built
     * once by {@link #canonicalQ(Map)} when the uri is constructed — never re-derived here. This method is therefore
     * a plain join over the map's iteration order: no comparator, no sort, no temporary list. Formatting is an O(n)
     * read of a structure whose order was established at write time.
     * <p>
     * THE ORDER IS THE SIGNATURE'S. A query is read as an instruction's signature — {@code inst?rng<=dom(args)}, the
     * shorthand for {@code inst?dom=…&rng=…&args=[…]} written as an arrow — and it is stored, hence rendered, in
     * that same order: {@code rng}, then {@code dom}, then {@code args}, then every remaining key in REVERSE
     * ALPHABETICAL (descending natural string) order. {@code rng} precedes {@code dom} because the arrow reads
     * rng-FROM-dom: the result type is named first, the input type after, exactly as {@code a<=b} names its output on
     * the left. Everything else sorts descending rather than ascending so the fixed trio leads and the tail is a
     * stable, obviously-not-insertion order — the point is one authority, not a pretty tail.
     * <p>
     * WHY CANONICAL AT ALL: {@code equals} compares the query MAP, so two uris that differ only in the order their
     * keys were inserted are the same uri — but they used to render as different text, and {@link
     * studio.phaseshift.metatron.furi.fURICache} interns by TEXT. Two spellings of one fURI were therefore two cache
     * entries (and two instances, two {@code toString} results, two hash maps on the way to one answer). Storing the
     * map in normal form is what makes equal uris render identically, and identical text is what lets them intern as
     * one key.
     */
    @Override
    public String qString() {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> kv : this.qMap().entrySet()) {
            if (!sb.isEmpty())
                sb.append('&');
            sb.append(kv.getKey());
            if (!kv.getValue().isEmpty())
                sb.append('=').append(kv.getValue());
        }
        return sb.toString();
    }

    /**
     * THE ordering authority for query components — the comparator of the stored map, so the order is a property of
     * the STORAGE rather than of the render. A key in {@link #Q_LEAD} sorts by its position there, before every other
     * key; every remaining key sorts in descending natural string order. The order is total, and it is consistent
     * with {@code equals} (it answers 0 only for equal keys), which a {@code Map} comparator must be.
     */
    private static int compareQKeys(final String lhs, final String rhs) {
        final int lhsRank = Q_LEAD.indexOf(lhs);
        final int rhsRank = Q_LEAD.indexOf(rhs);
        if (lhsRank >= 0 || rhsRank >= 0) {
            if (lhsRank < 0)
                return 1;                       // a lead key precedes everything else
            if (rhsRank < 0)
                return -1;
            return Integer.compare(lhsRank, rhsRank);
        }
        return rhs.compareTo(lhs);              // descending: reverse alphabetical
    }

    @Override
    public SortedMap<String, String> qMap() {
        return Collections.emptySortedMap();
    }

    @Override
    public fURI q(final String key, final Object value) {
        // the canonical map is the accumulator too: the key lands in normal-form position whatever its name
        final SortedMap<String, String> newQ = mutableQ(this.qMap());
        newQ.put(key, null == value ? "" : value.toString());
        return fURI.of(this.scheme(), this.host(), this.port(), this.path(), this.c(), this.poly(), newQ, this.templates());
    }

    @Override
    public <T> T qValue(final String key, final Class<T> valueClass) {
        if (!this.hasQ(key))
            return null;
        // MTRON OBJECTS /////////////////////////////////
        if (Str.class.isAssignableFrom(valueClass))
            return (T) str(this.qMap().get(key));
        else if (Uri.class.isAssignableFrom(valueClass))
            return (T) uri(this.qMap().get(key));
        else if (Int.class.isAssignableFrom(valueClass))
            return (T) jnt(Long.valueOf(this.qMap().get(key)));
        else if (Real.class.isAssignableFrom(valueClass))
            return (T) real(Double.valueOf(this.qMap().get(key)));
        else if (Bool.class.isAssignableFrom(valueClass))
            return (T) bool(Boolean.valueOf(this.qMap().get(key)));
        else if (Lst.class.isAssignableFrom(valueClass)) {
            final String listValue = this.qMap().get(key);
            return (T) (null == listValue ? null : ObjmtronSerializer.parse(listValue));
        } else if (Rec.class.isAssignableFrom(valueClass)) {
            final String recValue = this.qMap().get(key);
            return (T) (null == recValue ? null : ObjmtronSerializer.parse(recValue));
        }
        // NATIVE JAVA OBJECTS ////////////////////////////
        else if (String.class.isAssignableFrom(valueClass))
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
        else if (List.class.isAssignableFrom(valueClass)) {
            final String listValue = this.qMap().get(key);
            return (T) ObjmtronSerializer.parse(listValue).lstValue();
        } else
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
        return !this.path().isEmpty() && this.path().getLast().isEmpty();
    }

    @Override
    public String pathString() {
        return String.join("/", this.path());
    }


    @Override
    public fURI head(final int steps) {
        if (steps == 0)
            return fURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), this.poly(), this.qMap(), this.templates());
        if (steps >= this.pathLength())
            return this;
        boolean hasBlankRight = this.hasBlankCap(false);
        boolean hasBlankLeft = this.hasBlankCap(true);
        final List<String> newPath = new ArrayList<>(this.path().subList(0, steps + (hasBlankLeft ? 1 : 0)));
        if (hasBlankRight && !newPath.isEmpty() && !newPath.getLast().isEmpty())
            newPath.addLast("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
        // return fURI.of(this.scheme(), this.host(), this.port(), this.path().subList(0, steps + (hasBlank ? 1 : 0)), this.c(), List.of(), this.qMap());
    }

    @Override
    public fURI tail(final int steps) {
        if (steps == 0)
            return fURI.of(this.scheme(), this.host(), this.port(), List.of(), this.c(), this.poly(), this.qMap(), this.templates());
        if (steps >= this.pathLength())
            return this;
        boolean hasBlankRight = this.hasBlankCap(false);
        boolean hasBlankLeft = this.hasBlankCap(true);
        final List<String> newPath = new ArrayList<>(this.path().subList(((this.path().size() - steps) - (hasBlankRight ? 1 : 0)), this.path().size()));
        if (hasBlankLeft && !newPath.isEmpty() && !newPath.getFirst().isEmpty())
            newPath.addFirst("");
        return fURI.of(this.scheme(), this.host(), this.port(), newPath, this.c(), this.poly(), this.qMap(), this.templates());
    }

    private Optional<String> getTemplate(final Component component) {
        return this.templates().stream().filter(t -> t.get0() == component).map(Tuple.Pair::get1).findFirst();
    }

    @Override
    public String toString() {
        // Rendering is pure and this is on every string-keyed path (logging, serialization, f(uri.toString()) in
        // BasicMemory.redirect's poly branch), so it is built once per uri.
        final String cached = this.string;
        if (null != cached)
            return cached;
        final StringBuilder sb = new StringBuilder();
        if (this.hasTemplates()) {
            this.getTemplate(SCHEME).map(s -> "${" + s + "}").or(() -> Optional.ofNullable(this.scheme())).ifPresent(s -> sb.append(s).append(":"));
            this.getTemplate(HOST).map(s -> "${" + s + "}").or(() -> Optional.ofNullable(this.host())).ifPresent(s -> sb.append("//").append(s));
            this.getTemplate(PORT).map(s -> "${" + s + "}").or(() -> Optional.ofNullable(-1 == this.port() ? null : "" + this.port())).ifPresent(s -> sb.append(":").append(s));
            // Output path for template URIs (same logic as non-template)
            if (this.path().size() == 1 && this.path().getFirst().isEmpty())
                sb.append("/");
            else
                sb.append(this.path().stream().collect(Collectors.joining("/")));
            this.getTemplate(QUERY).map(s -> "${" + s + "}").or(() -> Optional.ofNullable(this.qString().isEmpty() ? null : this.qString())).ifPresent(s -> sb.append("?").append(s));
        } else {
            if (null != scheme())
                sb.append(scheme()).append(":");
            if (null != host()) {
                sb.append("//");
                sb.append(host());
                if (-1 != port())
                    sb.append(":").append(port());
            }
            // NOTE: the leading separator for an authority-bearing uri is NOT added here. "An authority implies an
            // absolute path" is enforced once, in fURI.of, for every construction — so by the time anything renders,
            // an authority's path already begins with the empty marker and the join below supplies the `/`. Adding
            // it here as well is the natural mistake (this is where it was first noticed) and it DOUBLES the slash.
            if (this.path().size() == 1 && this.path().getFirst().isEmpty())
                sb.append("/");
            else
                sb.append(this.path().stream().collect(Collectors.joining("/")));
            if (!this.poly().isEmpty())
                sb.append("[").append(String.join(",", this.poly())).append("]");
            if (!this.c().isOne())
                sb.append("{").append(this.c().toString()).append("}");
            if (!this.qMap().isEmpty())
                sb.append("?").append(this.qString());
        }
        final String rendered = sb.toString();
        this.string = rendered;
        return rendered;
    }

    @Override
    public int hashCode() {
        // Memoized: a uri is compared and hashed far more often than it is built, and this is the HashMap probe cost.
        int h = this.hash;
        if (0 != h)
            return h;
        // identical to Objects.hash(scheme, path, c, templates) without the Object[] allocation
        final fURI thisResolved = this.resolve();
        h = 1;
        h = 31 * h + (null == thisResolved.scheme() ? 0 : thisResolved.scheme().hashCode());
        h = 31 * h + (null == thisResolved.path() ? 0 : thisResolved.path().hashCode());
        h = 31 * h + (null == thisResolved.c() ? 0 : thisResolved.c().hashCode());
        h = 31 * h + (null == thisResolved.templates() ? 0 : thisResolved.templates().hashCode());
        if (0 == h)
            h = -1; // keep 0 as the not-yet-computed sentinel; any stable value satisfies the contract
        this.hash = h;
        return h;
    }


    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof fURI that))
            return false;
        if (this == that)
            return true;
        final fURI thisResolved = this.resolve();
        final fURI thatResolved = that.resolve();

        return Objects.equals(thisResolved.scheme(), thatResolved.scheme())
                && Objects.equals(thisResolved.host(), thatResolved.host())
                && thisResolved.port() == thatResolved.port()
                && Objects.equals(thisResolved.path(), thatResolved.path())
                && ((!thisResolved.hasPoly() && !thatResolved.hasPoly()) || Objects.equals(thisResolved.poly(), thatResolved.poly()))
                && Objects.equals(thisResolved.c(), thatResolved.c())
                && ((!thisResolved.hasTemplates() && !thatResolved.hasTemplates()) || Objects.equals(thisResolved.templates(), thatResolved.templates()))
                // Map.equals is defined by CONTENT across implementations, and TreeMap inherits it from AbstractMap
                // (unlike compareTo, it does not consult the comparator), so a sorted q map compares equal to any
                // Map with the same entries — the SAME semantics as before the order became part of the storage.
                // The defensive copies that used to sit here were pure allocation: two maps per comparison, on the
                // equality that runs in the resolver's inner loop.
                && ((!thisResolved.hasQ() && !thatResolved.hasQ()) || thisResolved.qMap().equals(thatResolved.qMap()));
    }

}
