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

package studio.phaseshift.metatron.isa.llm.space;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * dcmntSpace-backed implementation of {@link AbstractLLMSessionIntegrationTest}.
 *
 * <h3>BLOCKED — URI scheme incompatibility</h3>
 * {@code SpaceChatMemoryStore} stores messages at {@code {scheme}:msg/{memId}/{pos}}
 * (e.g. {@code sqlite:msg/1/0}).  This works for tbleSpace because its KV schema
 * treats {@code msg/1/0} as a flat key.  dcmntSpace interprets {@code /} as a
 * collection/document separator — {@code msg/1/0} would mean database {@code msg},
 * collection {@code 1}, entry {@code 0}, which doesn't map to MongoDB's document
 * model.
 *
 * <h3>Potential solutions</h3>
 * <ol>
 *   <li><b>Backend-specific URI factory in SpaceChatMemoryStore</b> —
 *       tbleSpace uses {@code msg/{id}/{pos}}, dcmntSpace uses
 *       {@code msg_{id}/{pos}} or {@code msg/{id}_{pos}}.</li>
 *   <li><b>dcmntSpace supports non-hierarchical keys</b> — teach dcmntSpace
 *       to treat a single-segment path like {@code msg_1_0} or to
 *       accept a flat key mode similar to tbleSpace's KV schema.</li>
 *   <li><b>Embedded documents</b> — store all messages for a memory as
 *       an array inside the memory document, with position as the
 *       array index.  Reintroduces the scaling concern but dcmntSpace
 *       is the right backend for document-shaped data.</li>
 * </ol>
 *
 * <h3>Setup (ready when unblocked)</h3>
 * Uses in-memory MongoDB (bwaldvogel) — no Docker, sub-second startup:
 * <pre>
 *   MongoServer mongoServer = new MongoServer(new MemoryBackend());
 *   mongoServer.bind();
 *   dcmntSpace.of(rec(..., uri(HOST), uri("mongodb://..."), ...), SPACE_VID);
 * </pre>
 */
public class DcmntSpaceLLMMemoryIntegrationTest { //extends AbstractLLMMemoryIntegrationTest {


}
