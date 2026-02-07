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

package studio.phaseshift.metatron.lang.db.tabl;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tableSpace extends AbstractSpace<Connection> {

    private final GraphittyLogger LOG = Graphitty.log(this);
    public static fURI TABL_TID = tablInstSet.TABL_INSTSET_TID.extend("space").extend("tabl");
    protected final fURI prefix;
    public static final Type TABL_TYPE = T(TABL_TID, null,
            instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(TABL_TID),
                    lst(T(REC_TID, isa_(rec(
                            uri(PATTERN), T(URI_TID),
                            uri(Tokens.HOST), T(URI_TID),
                            uri(Tokens.PREFIX), T(URI_TID),
                            uri(Tokens.Q).c(cInt::maybe), isa_(T(LST_TID)))))), (lhs, inst) -> {
                        final fURI pattern = inst.arg(0).<Rec>as().at(PATTERN).uriValue();
                        final fURI host = inst.arg(0).<Rec>as().at(Tokens.HOST).uriValue();
                        final fURI user = inst.arg(0).asRec().at(Tokens.USER).uriValue();
                        final Str pass = inst.arg(0).asRec().at(Tokens.PASS);
                        final fURI prefix = inst.arg(0).<Rec>as().at(Tokens.PREFIX).uriValue();
                        final tableSpace space = tableSpace.of(mutableMap(uri(PATTERN), uri(pattern), uri(Tokens.HOST), uri(host), uri(USER), uri(user), uri(PASS), pass, uri(Tokens.PREFIX), uri(prefix)), inst.arg(0).vid());
                        Router.global().addSpace(space);
                        return space;
                    }));

    public static tableSpace of(final Map<Obj, Obj> config, final fURI vid) {
        MTronException.wrap(() -> Class.forName("org.sqlite.JDBC"));
        try (Connection conn = DriverManager.getConnection("jdbc:" + config.get(uri(HOST)).toCleanString(), config.getOrDefault(uri(USER), uri("")).toCleanString(), config.getOrDefault(uri(PASS), str("")).toCleanString())) {
            Graphitty.log(tableSpace.class).info("connected to %s: %s", config.get(uri(HOST)).toCleanString(), conn.getMetaData());
            return new tableSpace(conn, config, TABL_TID, vid);
        } catch (final SQLException ex) {
            throw MTronException.of(ex);
        }
    }

    protected String toTableVId(final fURI vid) {
        return null == this.prefix ? vid.toString() : vid.removePrefix(this.prefix).toString();
    }

    protected fURI toMtronVid(final String tableVID) {
        return null == this.prefix ? f(tableVID) : this.prefix.extend(tableVID);
    }


    public tableSpace(final Connection sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
        this.prefix = config.getOrDefault(uri(PREFIX), uri("")).uriValue();
    }


    public Function<fURI, Iterator<Tuple.Pair<fURI,Obj>>> directReader() {
        return (pattern) -> {
            try {
                final Map<fURI, Obj> results = new LinkedHashMap<>();
                if (pattern.equals(fURI.ALL)) {
                    // Read all tables/rows - implement based on your schema
                    // Example: SELECT * FROM all_tables
                } else if (pattern.hasPattern()) {
                    if (pattern.hasPattern()) {
                        // Pattern matching - e.g., /db/table/*
                        // Parse pattern to extract database, table, columns, conditions
                        // Execute SELECT with WHERE clause matching the pattern
                    } else {

                        // Specific fURI - e.g., /db/table/id
                        // Execute SELECT with exact match
                        // Convert ResultSet to MRec and add to results
                    }
                } else {
                    // Specific fURI - e.g., /db/table/id
                    // Execute SELECT with exact match
                    // Convert ResultSet to MRec and add to results
                }

                return IteratorUtil.of();
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    public Obj read(final fURI vid) {
        return studio.phaseshift.metatron.furi.Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            return studio.phaseshift.metatron.furi.Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
    }

    public Obj write(final fURI vid, final Obj obj) {
        return studio.phaseshift.metatron.furi.Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            Space.Helper.resolveWrite(LOG, this, vid.basePath(), obj, this.directWriter(), this.directReader());
            return studio.phaseshift.metatron.furi.Q.Helper.processPostWrite(this.qs(), vid, vid, obj).orElse(obj);
        });
    }
}
