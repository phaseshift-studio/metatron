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

package studio.phaseshift.metatron.isa.tble.schema.storage;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;

/**
 * Interface for pluggable database schemas used by tbleSpace.
 * Implementations define how fURIs and objects are stored and queried.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface TableSchema {

    /**
     * Initialize the schema (create tables, indexes, etc.)
     *
     * @param conn the database connection
     * @throws SQLException if schema initialization fails
     */
    void initialize(final Connection conn) throws SQLException;

    /**
     * Write an object to the database at the given fURI.
     * If obj is null or empty, the entry should be deleted.
     *
     * @param conn    the database connection
     * @param furi    the fURI key
     * @param objJson the serialized object as JSON string (null to delete)
     * @return number of rows affected
     * @throws SQLException if write fails
     */
    int write(final Connection conn, final fURI furi, final String objJson) throws SQLException;

    /**
     * Read objects matching the given fURI pattern.
     * Supports MQTT-style wildcards: + (single level), # (multi-level)
     *
     * @param conn    the database connection
     * @param pattern the fURI pattern to match
     * @return iterator of matching fURIs and their JSON objects
     * @throws SQLException if read fails
     */
    Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException;

    /**
     * Read up to {@code limit} objects whose {@code furi} satisfies the given SQL
     * {@code WHERE} clause (as produced by
     * {@link studio.phaseshift.metatron.isa.tble.KVStoreUtil}).
     * <p>
     * Only the schema knows the {@code kv_store} column layout it created — one
     * {@code obj} text column for the JSON schemas, discrete typed columns
     * ({@code int_val}, {@code complex_val}, …) for the typed schema — so the
     * projection and the row decoding are delegated here instead of being assumed
     * by callers.
     *
     * @param conn        the database connection
     * @param whereClause a SQL condition over {@code furi} (no leading WHERE)
     * @param limit       maximum number of rows to return
     * @return iterator of matching fURIs and their objects
     * @throws SQLException if the read fails
     */
    default Iterator<Space.IdObj> readWhere(final Connection conn, final String whereClause,
                                            final long limit) throws SQLException {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + " does not support pattern-limited reads");
    }

    /**
     * Delete an object at the given fURI.
     *
     * @param conn the database connection
     * @param furi the fURI key
     * @return number of rows deleted
     * @throws SQLException if delete fails
     */
    int delete(final Connection conn, final fURI furi) throws SQLException;

    /**
     * Get the schema version for migration tracking.
     *
     * @return schema version string
     */
    default String version() {
        return "1.0";
    }
    
}
