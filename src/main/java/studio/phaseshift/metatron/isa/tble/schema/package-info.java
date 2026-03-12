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

/**
 * Pluggable database schemas for tbleSpace.
 * <p>
 * This package provides a schema abstraction layer that allows different
 * database table structures and indexing strategies to be used with tbleSpace.
 * <p>
 * Available schemas:
 * <ul>
 *   <li>{@link studio.phaseshift.metatron.isa.tble.schema.fURIAwareIndexedSchema} -
 *       MQTT-indexed schema with virtual generated columns for efficient pattern matching</li>
 *   <li>{@link studio.phaseshift.metatron.isa.tble.schema.SimpleKeyValueSchema} -
 *       Basic schema with furi/obj table and no special indexing</li>
 * </ul>
 * <p>
 * To implement a custom schema, implement the {@link studio.phaseshift.metatron.isa.tble.schema.TableSchema}
 * interface and configure it in tbleSpace.
 *
 * @see studio.phaseshift.metatron.isa.tble.schema.TableSchema
 * @see studio.phaseshift.metatron.isa.tble.tbleSpace
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
package studio.phaseshift.metatron.isa.tble.schema;
