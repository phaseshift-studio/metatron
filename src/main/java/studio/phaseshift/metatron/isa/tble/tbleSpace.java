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

package studio.phaseshift.metatron.isa.tble;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.tble.schema.MqttIndexedSchema;
import studio.phaseshift.metatron.isa.tble.schema.SimpleSchema;
import studio.phaseshift.metatron.isa.tble.schema.TableSchema;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.StringReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleSpace extends AbstractSpace<Connection> {

    private final GraphittyLogger LOG = Graphitty.log(this);
    public static fURI TABL_SPACE_TID = tbleInstSet.TBLE_ISA_TID.extend(SPACE).extend("tble");
    public static final Type TABL_SPACE_TYPE =
            Type.Builder.build()
                    .tid(SPACE_TID)
                    .vid(TABL_SPACE_TID)
                    .isaPredicate(rec(
                            uri(PATTERN), URI_TYPE,
                            uri(HOST), URI_TYPE,
                            uri(DRIVER), URI_TYPE,
                            uri(ROUTE), rec(URI_TYPE, URI_TYPE)))
                    .constructor(instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(TABL_SPACE_TID),
                            lst(REC_TYPE),
                            (_, inst) -> tbleSpace.of(inst.arg(0).asRec().jvm(), inst.arg(0).vid()))).create();

    protected ObjSerializer serializer;
    protected TableSchema schema;

    public static tbleSpace of(final Map<Obj, Obj> config, final fURI vid) {
        MTronException.wrap(() -> Class.forName(config.get(uri(DRIVER)).uriValue().toString()));
        try {
            final Connection conn = DriverManager.getConnection(JDBC + config.get(uri(HOST)).uriValue().toString());
            return new tbleSpace(conn, config, TABL_SPACE_TID, vid);
        } catch (final SQLException ex) {
            throw MTronException.of(ex);
        }
    }


    public tbleSpace(final Connection sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
        LOG.info("connected {{b}}%s{{X}}", config.get(uri(HOST)));
        this.serializer = this.at(uri(SERIALIZER)).orElse(new ObjSimpleJSONSerializer());

        // Initialize schema - auto-detect based on database type
        try {
            final String dbProductName = sjvm.getMetaData().getDatabaseProductName().toLowerCase();
            if (dbProductName.contains("mariadb") || dbProductName.contains("mysql")) {
                this.schema = new MqttIndexedSchema();
                LOG.info("detected {{b}}mariadb/mysql{{X}} - using {{g}}mqtt schema");
            } else {
                this.schema = new SimpleSchema();
                LOG.info("detected {{b}}%s{{X}} - using {{y}}simple schema", dbProductName);
            }
            this.schema.initialize(sjvm);
            LOG.info("initialized schema {{b}}%s{{X}} (version: %s)",
                     this.schema.getClass().getSimpleName(), this.schema.version());
        } catch (final SQLException ex) {
            throw MTronException.of(ex);
        }
    }


    @Override
    public void close() {
        CommonUtil.close(this.sjvm());
    }


    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            try {
                if (pattern.hasPattern()) {
                    // Pattern write - write to all matching fURIs
                    this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
                } else {
                    // Direct write using schema
                    final String objJson = obj.isNoObj() ? null : this.serializer.write(obj).toString();
                    this.schema.write(this.sjvm(), pattern, objJson);
                }
            } catch (final SQLException e) {
                throw MTronException.of(e);
            }
            return obj;
        };
    }

    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            try {
                // Use schema to read objects
                final Iterator<TableSchema.FuriObjPair> schemaResults = this.schema.read(this.sjvm(), pattern);
                final List<IdObj> objs = new ArrayList<>();

                // Convert schema results to Obj pairs and unroll polys if pattern matching
                while (schemaResults.hasNext()) {
                    final TableSchema.FuriObjPair pair = schemaResults.next();
                    final JsonElement json = JsonParser.parseReader(new StringReader(pair.objJson()));
                    final Obj obj = this.serializer.read(json);

                    // Add the direct match
                    if (pair.furi().test(pattern.asNode())) {
                        objs.add(IdObj.of(pair.furi(), obj));
                    }

                    // If pattern matching and obj is a poly, unroll it
                    if (pattern.hasPattern() && obj.isPoly()) {
                        Space.Helper.unrollPoly(pair.furi(), obj.as(), pattern.asNode())
                                .forEach(kv -> objs.add(kv));
                    }
                }

                return objs.iterator();
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    public Obj read(final fURI vid) {
        final fURI newVID = this.rewrite(vid, true);
        LOG.debug("reading %s => %s", vid, newVID);
        return studio.phaseshift.metatron.furi.Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, newVID, directReader());
            return studio.phaseshift.metatron.furi.Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
    }

    public Obj write(final fURI vid, final Obj obj) {
        final fURI newVID = this.rewrite(vid, true);
        LOG.debug("writing %s => %s", vid, newVID);
        return studio.phaseshift.metatron.furi.Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            Space.Helper.resolveWrite(LOG, this, newVID, obj, this.directWriter(), this.directReader());
            return studio.phaseshift.metatron.furi.Q.Helper.processPostWrite(this.qs(), vid, vid, obj).orElse(obj);
        });
    }
}
