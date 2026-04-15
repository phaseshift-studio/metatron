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
 * Domain schema for document databases.
 * <p>
 * This package contains classes that discover and represent user-facing collection structures,
 * document types, and references in MongoDB/DocumentDB databases.
 * </p>
 *
 * <h2>Domain Schema Responsibilities</h2>
 * <ul>
 *   <li>Discover existing collections in the database</li>
 *   <li>Infer document structure and field types</li>
 *   <li>Detect references (DBRef and manual)</li>
 *   <li>Generate mtron type definitions for collections</li>
 *   <li>Provide schema access via fURIs (e.g., *mongo:schema/mydb)</li>
 * </ul>
 *
 * <h2>Reference Detection</h2>
 * <p>
 * The domain schema detects several types of references:
 * </p>
 * <ul>
 *   <li><b>DBRef</b> - MongoDB's standard reference format: { $ref: "collection", $id: ObjectId(...) }</li>
 *   <li><b>Manual References</b> - Foreign key style: { userId: ObjectId(...) }</li>
 *   <li><b>Embedded Documents</b> - Nested objects (not references)</li>
 * </ul>
 *
 * <h2>Lazy Resolution</h2>
 * <p>
 * References are resolved lazily using auto_from instructions to prevent infinite
 * recursion in circular reference structures.
 * </p>
 *
 * @see studio.phaseshift.metatron.isa.dcmnt.schema.storage Storage schema package
 * @see studio.phaseshift.metatron.isa.tble.schema.domain SQL domain schema reference
 */
package studio.phaseshift.metatron.isa.dcmnt.schema.domain;
