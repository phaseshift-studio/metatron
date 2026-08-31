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

package studio.phaseshift.metatron.isa.m.space.memspace;

import studio.phaseshift.metatron.SkipWhenPortUnavailable;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.space.AbstractLLMSessionIntegrationTest;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * memSpace-backed implementation of {@link AbstractLLMSessionIntegrationTest}.
 * <p>
 * Uses a raw path-based URI with no route — verifying that
 * {@link studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore}
 * works with any backing space, not just tbleSpace.
 * <p>
 * Registers the default AtomicLong-based {@code incrQ} QProc so typed-collection
 * mirroring via {@code +?incrq} works — the QProc generates sequential entry IDs
 * and stores the data itself, same pattern as tbleIncrQ.
 * <p>
 * Session VID:  {@code /mem/example/llm_session/1}
 * Message path: {@code /mem/example/msg/1/0}
 */
@SkipWhenPortUnavailable(value = 11434)
public class MemSpaceLLMSessionIntegrationTest extends AbstractLLMSessionIntegrationTest {

    private static final fURI SPACE_VID = f("/sys/space/test_llm_mem_int");
    private static final fURI SESSION_VID = f("/mem/example/llm_session/1");

    private memSpace space;

    @Override
    protected Space createSessionSpace() {
        this.space = memSpace.of(
                rec(
                        uri(PATTERN), uri("/mem/example/#"),
                        uri(QPROC), lst(incrQ())
                ),
                SPACE_VID
        );
        return this.space;
    }

    @Override
    protected fURI sessionVID() {
        return SESSION_VID;
    }

    @Override
    protected void cleanupSession() throws Exception {
        if (this.space != null) {
            try {
                Router.global().removeSpace(this.space.vid());
            } catch (final Exception ignored) {
            }
            this.space.close();
            this.space = null;
        }
    }
}
