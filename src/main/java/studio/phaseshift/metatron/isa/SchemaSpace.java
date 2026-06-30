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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.IteratorUtil;

import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.WILD_ONE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TYPE;
import static studio.phaseshift.metatron.isa.m.type.InstSet.instset0;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface SchemaSpace extends Space {

    default InstSet schema() {
        return this.at(uri(SCHEMA)).orElse(instset0).as();
    }

    /**
     * Resolve a collection-level URI segment against the schema {@link InstSet}.
     * <p>
     * Matches the collection name (from {@link studio.phaseshift.metatron.furi.DataPath#collection()})
     * against the last segment of each type's VID in the schema InstSet.
     * For wildcard collections ({@code #} or {@code +}), returns all types.
     * <p>
     * This is the single, shared entry point for collection-level type resolution
     * across all {@link SchemaSpace} implementations — table spaces, document
     * spaces, graph spaces, and future database-backed spaces.
     *
     * @param collectionName the collection name (table, vertex-label, etc.) or wildcard
     * @return iterator of {@link IdObj} with type VIDs and their {@link Type} values,
     *         or an empty iterator when no matching type is found
     */
    default Iterator<IdObj> resolveCollectionSchema(final String collectionName) {
        final InstSet instSet = this.schema();
        final boolean wildcard = ALL.name().equals(collectionName)
                || WILD_ONE.name().equals(collectionName);
        if (wildcard) {
            return instSet.types().stream()
                    .map(t -> IdObj.of(t.vid(), t))
                    .iterator();
        }
        return instSet.types().stream()
                .filter(t -> t.vid().name().equalsIgnoreCase(collectionName))
                .findFirst()
                .<Iterator<IdObj>>map(t -> IteratorUtil.of(IdObj.of(t.vid(), t)))
                .orElseGet(Collections::emptyIterator);
    }

    /**
     * Shared logging helper for schema-change events so the output is
     * consistent across all {@link SchemaSpace} implementations.
     *
     * @param entityTerm the entity kind (e.g. {@code "table"}, {@code "collection"}, {@code "label"})
     * @param fieldTerm  the field kind (e.g. {@code "column"}, {@code "field"}, {@code "property"})
     * @param name       the entity name
     * @param fieldNames the field/property names
     * @param isNew      {@code true} when the entity itself is newly created;
     *                   {@code false} when fields are being added to an existing entity
     */
    static void logSchemaChange(final GraphittyLogger log, final String entityTerm,
                                final String fieldTerm, final String name,
                                final Set<String> fieldNames, final boolean isNew) {
        final String fieldPlural = fieldTerm + (fieldNames.size() == 1 ? "" : "s");
        if (isNew)
            log.info("created %s {{b}}%s{{X}} with %d %s: %s",
                    entityTerm, name, fieldNames.size(), fieldPlural, fieldNames);
        else
            log.info("updated %s {{b}}%s{{X}} — added %s: %s",
                    entityTerm, name, fieldPlural, fieldNames);
    }

    @Override
    default void close() {
        this.schema().close();
    }

}
