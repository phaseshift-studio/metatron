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

package studio.phaseshift.metatron.isa.tble;

/**
 * Table-level utility checks for the {@code tbleSpace} typed-table model.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class TableStoreUtil {

    private TableStoreUtil() {}

    /**
     * Returns {@code true} when the given collection name corresponds to
     * a real SQL table discovered by the space at initialization time.
     */
    public static boolean isTableCollection(final tbleSpace space, final String collectionName) {
        return space.existingTableSchema != null
                && space.existingTableSchema.getTableNames().contains(collectionName.toLowerCase());
    }
}
