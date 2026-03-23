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
 * Storage schema for document databases.
 * <p>
 * This package contains classes that define how metatron encodes and stores its own data
 * in MongoDB/DocumentDB. This is the internal representation used by metatron for persistence.
 * </p>
 *
 * <h2>Storage Schema Responsibilities</h2>
 * <ul>
 *   <li>Key-value encoding for mtron objects</li>
 *   <li>Index management for efficient queries</li>
 *   <li>Type serialization/deserialization</li>
 *   <li>Collection structure for metatron's internal data</li>
 * </ul>
 *
 * <h2>Contrast with Domain Schema</h2>
 * <p>
 * Storage schema is about <b>how metatron stores data</b>, while domain schema
 * (in {@link studio.phaseshift.metatron.isa.doc.schema.domain}) is about
 * <b>how users' existing collections are structured</b>.
 * </p>
 *
 * @see studio.phaseshift.metatron.isa.doc.schema.domain Domain schema package
 * @see studio.phaseshift.metatron.isa.tble.schema.storage SQL storage schema reference
 */
package studio.phaseshift.metatron.isa.doc.schema.storage;
