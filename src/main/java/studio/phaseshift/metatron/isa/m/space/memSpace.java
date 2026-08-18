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

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.DATA;
import static studio.phaseshift.metatron.Tokens.PERSIST;
import static studio.phaseshift.metatron.isa.m.mInstSet.MEM_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.uri0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class memSpace extends AbstractMemorySpace {

    protected memSpace(final Map<Obj, Obj> config, final fURI vid) {
        this(config, MEM_SPACE_TID, vid);
    }

    protected memSpace(final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(new TopicTrie(), config, tid, vid);
        if (!this.at(DATA).isNoObj())
            Runtime.getRuntime().addShutdownHook(new Thread(() -> this.save()));
        load();
    }


    public static memSpace of(final fURI pattern, final fURI vid) {
        return new memSpace(mutableMap(uri(Tokens.PATTERN), uri(pattern)), vid);
    }

    public static memSpace of(final Rec config, final fURI vid) {
        return new memSpace(mutableMap(config.jvm()), vid);
    }

    @Override
    public void close() {
        this.save();
        this.sjvm().entrySet().forEach(kv -> {
            try {
                if (!(kv.getValue() instanceof Router) && kv.getValue() != this)
                    CommonUtil.close(kv.getValue());
            } catch (final Exception e) {
                LOG.warn(e);
            }
        });
        super.close();
    }


    public memSpace load() {
        final Uri path = this.at(DATA).orElse(uri0());
        if (path.isNoObj())
            return this;
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
        final Uri path = this.at(DATA).orElse(uri0());
        if (path.isNoObj())
            return this;
        if (!this.sjvm().isEmpty()) {
            try {
                final File file = new File(path.uriValue().toString());
                if (file.exists())
                    file.delete();
                else
                    file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw MTronException.of(e);
            }
            try (final FileOutputStream out = new FileOutputStream(path.uriValue().toString())) {
                // TopicTrie.forEach() iterates all entries across all nodes
                this.sjvm().forEach((key, value) -> {
                    try {
                        out.write((key + " ->(" + ObjmtronSerializer.singleNoClip().write(value) + ");\n").getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }
                });
                LOG.info("saved space to %s", this.at(DATA).uriValue());
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        } else {
            LOG.warn("no data to persist at %s", this.at(PERSIST));
        }
        return this;
    }
}
