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

package studio.phaseshift.metatron.isa.m.space;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.PERSIST;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.FILE_TYPE;
import static studio.phaseshift.metatron.isa.mach.machInstSet.SPACE_CONFIG;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;


public class memSpace extends AbstractSpace<TopicTrie> {

    public static final fURI MEM_SPACE_TID = M_ISA_TID.extend("space").extend("memspace");

    protected static final Rec MEM_SPACE_CONFIG = SPACE_CONFIG.plus(rec(uri(PERSIST).maybe().asUri(), FILE_TYPE));

    public static final Type MEM_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(MEM_SPACE_TID)
            .constructor(
                    instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(MEM_SPACE_TID),
                            lst(isa_(MEM_SPACE_CONFIG).tryToInst()),
                            (lhs, inst) -> memSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    protected memSpace(final Map<Obj, Obj> config, final fURI vid) {
        super(new TopicTrie(), config, MEM_SPACE_TID, vid);
        load();
    }

    protected memSpace(final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(new TopicTrie(), config, tid, vid);
        load();
    }


    public static memSpace of(final fURI pattern, final fURI vid) {
        return new memSpace(mutableMap(uri(Tokens.PATTERN), uri(pattern)), vid);
    }

    public static memSpace of(final Rec config, final fURI vid) {
        return new memSpace(new HashMap<>(config.jvm()), vid);
    }

    @Override
    public void close() {
        this.save();
        super.close();
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            if (pattern.equals(ALL))
                return this.sjvm().entrySet().stream().map(kv -> IdObj.of(kv.getKey(), kv.getValue())).iterator();
            else {
                if (pattern.hasPattern()) {
                    final fURI nodePattern = pattern.asNode();
                    // stream 1: direct matches from trie
                    final List<Map.Entry<fURI, Obj>> directMatches = this.sjvm().match(nodePattern);
                    Stream<Map.Entry<fURI, Obj>> polyParents = Stream.empty();
                    // stream 2: check parent paths for polys that can expand to match (no matches, requires deeper inspection)
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
                        /*  // also check root MIGHT NOT REQUIRED (WAITING FOR A FAILURE TO SHOW ITSELF)
                        final Obj rootValue = this.sjvm().get(parent);
                        if (rootValue != null && rootValue.isPoly()) {
                            polyParents = Stream.concat(polyParents, Stream.of(new AbstractMap.SimpleEntry<>(parent, rootValue)));
                        }*/
                    }
                    return Stream.concat(directMatches.stream(), polyParents)
                            .flatMap(kv -> Stream.concat(
                                    kv.getKey().test(nodePattern) ?
                                            Stream.of(IdObj.of(kv.getKey(), kv.getValue())) :
                                            Stream.empty(),
                                    kv.getValue().isPoly() ?
                                            Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), nodePattern).stream() :
                                            Stream.empty())).iterator();
                } else {
                    final Obj value = this.sjvm().get(pattern);
                    if (value != null)
                        return IteratorUtil.of(IdObj.of(pattern, value));
                    return readContainer(pattern);
                }
            }
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
            } else {
                final Obj current = this.sjvm().get(pattern);
                if (obj.isNoObj()) {
                    LOG.trace("removing %s", pattern);
                    this.sjvm().remove(pattern);
                    CommonUtil.close(current);
                } else {
                    if (obj.isRec())
                        Rec.Helper.stripNone(obj.asRec());
                    final Obj newValue = obj;
                    this.sjvm().put(pattern, newValue);
                }
            }
            return obj;
        };
    }

    private Iterator<IdObj> readContainer(final fURI pattern) {
        for (int depth = 1; depth <= 1; depth++) {
            fURI wildcard = pattern;
            for (int d = 0; d < depth; d++)
                wildcard = wildcard.extend("+");
            final List<Map.Entry<fURI, Obj>> matches = this.sjvm().match(wildcard.asNode());
            if (!matches.isEmpty()) {
                final Rec container = rec();
                for (final Map.Entry<fURI, Obj> kv : matches) {
                    final fURI childPath = kv.getKey().pretract(pattern.segmentLength()).asRelative();
                    final List<String> segs = childPath.segments();
                    if (segs.isEmpty()) continue;
                    Map<Obj, Obj> level = container.jvm();
                    for (int i = 0; i < segs.size() - 1; i++) {
                        final Uri key = uri(segs.get(i));
                        Obj child = level.get(key);
                        if (child == null || !child.isRec()) {
                            child = rec();
                            level.put(key, child);
                        }
                        level = child.asRec().jvm();
                    }
                    level.put(uri(segs.getLast()), kv.getValue());
                }
                if (!container.isEmpty())
                    return IteratorUtil.of(IdObj.of(pattern, container));
            }
        }
        return IteratorUtil.of();
    }

    @Override
    public Stream<IdObj> readStream(final fURI pattern) {
        if (pattern.equals(ALL))
            return this.sjvm().entrySet().stream().map(kv -> IdObj.of(kv.getKey(), kv.getValue()));
        if (pattern.hasPattern()) {
            final fURI nodePattern = pattern.asNode();
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
                    .flatMap(kv -> kv.getValue().stream().map(kk -> new AbstractMap.SimpleEntry<>(kv.getKey(), kk)))
                    .flatMap(kv -> Stream.concat(
                            kv.getKey().test(nodePattern) ?
                                    Stream.of(IdObj.of(kv.getKey(), kv.getValue())) :
                                    Stream.empty(),
                            kv.getValue().isPoly() ?
                                    Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), nodePattern).stream() :
                                    Stream.empty()));
        }
        final Obj value = this.sjvm().get(pattern);
        return null == value ?
                Stream.empty() :
                value.isObjs() ?
                        value.stream().flatMap(o -> Stream.of(IdObj.of(pattern, o))) :
                        Stream.of(IdObj.of(pattern, value));
    }

    @Override
    public Stream<IdObj> writeStream(final fURI pattern, final Obj obj) {
        if (pattern.hasPattern()) {
            final List<IdObj> results = new ArrayList<>();
            this.directReader().apply(pattern).forEachRemaining(kv -> {
                this.write(kv.furi(), obj);
                results.add(IdObj.of(kv.furi(), obj));
            });
            return results.stream();
        }
        final Obj result = this.write(pattern, obj);
        if (result.isNoObj())
            return Stream.empty();
        return Stream.of(IdObj.of(pattern, result));
    }

    public Obj load() {
        final Uri path = (Uri) this.jvm().getOrDefault(uri(PERSIST), null);
        if (null == path)
            return this;
        //final ObjByteBufferSerializer serializer = new ObjByteBufferSerializer();
        final File file = new File(path.uriValue().toString());
        if (!file.exists()) {
            LOG.warn("no persisted data at {{y}}%s", file.getAbsolutePath());
        } else {
            try {
                LOG.info("loading persisted data at {{y}}%s", file.getAbsolutePath());
                mParser.eval(file, ex -> {
                    throw MTronException.of(ex);
                }).reduce(noobj(), (x, y) -> noobj());
                LOG.info("total data loaded from {{y}}%s{{X}}: {{y}}%d{{/y}} bytes", file.getAbsolutePath(), Files.size(file.toPath()));
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
        return this;
    }

    public Obj save() {
        final Uri path = (Uri) this.jvm().getOrDefault(uri(PERSIST), null);
        if (null == path)
            return this;
        final ObjByteBufferSerializer serializer = new ObjByteBufferSerializer();
        final File file = new File(path.uriValue().toString());
        if (file.exists()) assert file.delete();
        if (!this.sjvm().isEmpty()) {
            try {
                assert file.createNewFile();
            } catch (IOException e) {
                throw MTronException.of(e);
            }
            try (final FileOutputStream out = new FileOutputStream(path.uriValue().toString())) {
                out.write("print('loading persisted data');\n".getBytes());
                // TopicTrie.forEach() iterates all entries across all nodes
                this.sjvm().forEach((key, value) -> {
                    try {
                        out.write((key + " -> " + new String(serializer.outputBytes(value).array(), StandardCharsets.UTF_8) + ";\n").getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }
                });
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        } else {
            LOG.warn("no data to persist at %s", this.at(PERSIST));
        }
        return this;
    }
}
