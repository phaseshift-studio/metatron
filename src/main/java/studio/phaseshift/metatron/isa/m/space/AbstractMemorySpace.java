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

package studio.phaseshift.metatron.isa.m.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractMemorySpace extends AbstractSpace<TopicTrie> {


    public AbstractMemorySpace(final TopicTrie sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        this(sjvm, config, tid, vid, true);
    }

    protected AbstractMemorySpace(final TopicTrie sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid, final boolean autoRegister) {
        super(sjvm, config, tid, vid, autoRegister);
    }

    /**
     * ONLY WHAT IS STORED (Marko, 2026-11-19: a directReader "only return[s] what is explicitly in their store"): an
     * exact address answers its stored value, a pattern the stored entries it matches. What a read means -- a poly's
     * insides, an interior address's subtree, + and # -- is derived once, for every space, by Space.Helper.
     */
    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (qpattern) -> {
            final fURI pattern = qpattern.qLessExceptDomRng();
            if (pattern.equals(ALL))
                return this.sjvm().entrySet().stream().map(kv -> IdObj.of(kv.getKey(), kv.getValue())).iterator();
            if (pattern.hasPattern()) {
                final fURI nodePattern = pattern.asNode();
                final Stream<IdObj> stored = this.sjvm().match(nodePattern).stream()
                        .filter(kv -> kv.getKey().test(nodePattern))
                        .map(kv -> IdObj.of(kv.getKey(), kv.getValue()));
                // a level listed with + also answers its interior nodes, as noobj: the trie keeps a node for each level
                // of a flat key (a/b/c stored lays out a and a/b), so the uri walk lists a level from + alone
                // the level the + lists: the root of /+ keeps its leading / (a full retract empties the path)
                final fURI level = 1 == nodePattern.segmentLength() ? Space.Helper.root(nodePattern) : nodePattern.retract(1);
                if (!"+".equals(nodePattern.name()) || level.hasPattern())
                    return stored.iterator();
                return Stream.concat(stored, this.sjvm().interior(level).stream()
                        .map(segment -> IdObj.of(level.extend(segment), noobj()))).iterator();
            }
            // resolve(): the writer (resolveWrite → directWriter) stores under vid.resolve(), so the reader
            // must resolve too — otherwise /. (the root's node vid) writes to / but reads back /. → noobj.
            final Obj value = this.sjvm().get(pattern.resolve());
            return null == value ? IteratorUtil.of() : IteratorUtil.of(IdObj.of(pattern, value));
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            // DELIBERATELY NOT SYNCHRONIZED. Serializing this looks right -- it is a read-modify-write on the trie --
            // but it DEADLOCKS: a write holds the machine's lock and reaches into the space, while this writer can
            // reach back for the machine (closing a replaced value, writing the space back at its own vid), so the
            // two lock orders cross. Measured: it hung setup with no timeout firing anywhere, which is what a
            // deadlock looks like from outside. Serializing the shared state has to happen at a level that does not
            // span both -- or the trie's own map has to be concurrent -- but not here.
            if (pattern.hasPattern()) {
                this.targets(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
            } else {
                final Obj current = this.sjvm().get(pattern);
                if (obj.isNoObj()) {
                    LOG.trace("removing %s", pattern);
                    this.sjvm().remove(pattern);
                    CommonUtil.close(current);
                } else {
                    if (obj.isRec())
                        Rec.Helper.stripNone(obj.asRec());
                    this.sjvm().put(pattern, obj);
                }
            }
            return obj;
        };
    }

    /**
     * the addresses a PATTERN WRITE lands on: the stored entries it matches, else the slots of the nearest stored poly
     * above them (a row or a column of a stored lst) -- entries that are stored, never an interior address a read derives,
     * and never only the leaves a # read answers (a delete of x/# removes the containers too). A space that keeps a
     * memSpace as its cache (mqttSpace) expands its own pattern writes with this.
     */
    public Iterator<IdObj> targets(final fURI qpattern) {
        final fURI nodePattern = qpattern.qLessExceptDomRng().asNode();
        final List<Map.Entry<fURI, Obj>> directMatches = this.sjvm().match(nodePattern);
        Stream<Map.Entry<fURI, Obj>> polyParents = Stream.empty();
        if (directMatches.isEmpty() && nodePattern.hasPattern()) {
            fURI parent = nodePattern.retract(1);
            while (parent.segmentLength() > 0) {
                final Obj parentValue = this.sjvm().get(parent);
                if (parentValue != null && parentValue.isPoly()) {
                    polyParents = Stream.of(new AbstractMap.SimpleEntry<>(parent, parentValue));
                    break;
                }
                parent = parent.retract(1);
            }
        }
        return Stream.concat(directMatches.stream(), polyParents)
                .flatMap(kv -> Stream.concat(
                        kv.getKey().test(nodePattern) ?
                                Stream.of(IdObj.of(kv.getKey(), kv.getValue())) :
                                Stream.empty(),
                        kv.getValue().isPoly() ?
                                Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), nodePattern).stream() :
                                Stream.empty())).iterator();
    }
}
