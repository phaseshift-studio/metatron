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
import studio.phaseshift.metatron.furi.Q;
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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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


public class memSpace extends AbstractSpace<Map<fURI, Obj>> {

    public static final fURI MEM_SPACE_TID = M_ISA_TID.extend("space/mem");

    protected static final Rec MEM_SPACE_CONFIG = SPACE_CONFIG.plus(rec(uri(PERSIST).maybe().asUri(), FILE_TYPE));

    public static final Type MEM_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(MEM_SPACE_TID)
            .constructor(
                    instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(MEM_SPACE_TID),
                            lst(isa_(MEM_SPACE_CONFIG).tryToInst()),
                            (lhs, inst) -> memSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    protected memSpace(final Map<Obj, Obj> config, final fURI vid) {
        super(new ConcurrentHashMap<>(), config, MEM_SPACE_TID, vid);
        load();
    }


    public static memSpace of(final fURI pattern, final fURI vid) {
        return new memSpace(mutableMap(uri(Tokens.PATTERN), uri(pattern)), vid);
    }

    public static memSpace of(final Rec config, final fURI vid) {
        return new memSpace(config.jvm(), vid);
    }

    @Override
    public void close() {
        this.save();
        super.close();
    }

    @Override
    public Obj read(final fURI vid) {
        return Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            //return result;
            return Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            Space.Helper.resolveWrite(LOG, this, vid.basePath(), obj, this.directWriter(), this.directReader());
            //return obj;
            return Q.Helper.processPostWrite(this.qs(), vid, vid, obj).orElse(Q.Helper.processQlessWrite(this.qs(), vid, vid, obj).orElse(obj));
        });
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            if (pattern.equals(ALL))
                return this.sjvm().entrySet().stream().map(kv -> IdObj.of(kv.getKey(), kv.getValue())).iterator();
            else {
                if (pattern.hasPattern()) {
                    return this.sjvm().entrySet().stream().flatMap(kv -> Stream.concat(
                            kv.getKey().test(pattern.asNode()) ?
                                    Stream.of(IdObj.of(kv.getKey(), kv.getValue())) :
                                    Stream.empty(),
                            kv.getValue().isPoly() ?
                                    Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), pattern.asNode()).stream() :
                                    Stream.empty())).iterator();
                } else {
                    final Obj value = this.sjvm().get(pattern);
                    return null == value ? IteratorUtil.of() : IteratorUtil.of(IdObj.of(pattern, value));
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
                } else
                    this.sjvm().put(pattern, (null != current && (obj.isObjs() || current.isObjs())) ? current.append(obj) : obj);
            }
            return obj;
        };
    }

    protected void load() {
        final Uri path = (Uri) this.jvm().getOrDefault(uri(PERSIST), null);
        if (null == path)
            return;
        //final ObjByteBufferSerializer serializer = new ObjByteBufferSerializer();
        final File file = new File(path.uriValue().toString());
        if (!file.exists()) {
            LOG.warn("no persisted data at {{y}}%s", file.getAbsolutePath());
        } else {
            try {
                LOG.info("loading persisted data at {{y}}%s", file.getAbsolutePath());
                mParser.eval(file, ex -> {
                    throw MTronException.of(ex);
                }).reduce(noobj(), (_, _) -> noobj());
                LOG.info("total data loaded from {{y}}%s{{X}}: {{y}}%d{{/y}} bytes", file.getAbsolutePath(), Files.size(file.toPath()));
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
    }

    protected void save() {
        final Uri path = (Uri) this.jvm().getOrDefault(uri(PERSIST), null);
        if (null == path)
            return;
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
                out.write("print('loading persisted data\\n');\n".getBytes());
                this.sjvm().forEach((key, value) -> {
                    try {
                        out.write((key + " -> " + new String(serializer.outputBytes(value).array(), StandardCharsets.UTF_8) + ";\n").getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }
                });
                out.write("print('complete.\\n');\n".getBytes(StandardCharsets.UTF_8));

            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        } else {
            LOG.warn("no data to persist at %s", this.at(PERSIST));
        }
    }
}
