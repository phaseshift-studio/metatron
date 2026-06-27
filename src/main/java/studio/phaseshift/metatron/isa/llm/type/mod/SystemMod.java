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

package studio.phaseshift.metatron.isa.llm.type.mod;

import dev.langchain4j.service.AiServices;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.mAgent;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.TEXT;
import static studio.phaseshift.metatron.Tokens.TYPE;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SystemMod implements Mod {

    @Override
    public void apply(final mModel model, final AiServices<mAgent> services) {
        try {
            // Prepend user-added system messages before capability-generated ones
            final String finalSystemMessage = String.join("\n", model.getSystemMessages());
            if (!finalSystemMessage.isBlank()) {
                services.systemMessage(finalSystemMessage);
                // Mirror to typed table (system messages bypass ChatMemory)
                if (!model.session().isNoObj() && null != model.session().vid()) {
                    final Map<Obj, Obj> systemMap = new LinkedHashMap<>();
                    systemMap.put(uri(TEXT), str(finalSystemMessage));
                    systemMap.put(uri(TYPE), uri("SYSTEM"));
                    final Rec systemRec = rec(systemMap, SYSTEM_MESSAGE_TID, null);
                    final Space space = Router.global().getSpaceFor(model.session().vid());
                    SpaceChatSessionStore.mirrorSystemMessage(space, model.session().vid(), systemRec);
                }
            }
        } catch (Exception e) {
            throw MTronException.of("unable to setup system message: %s", e);
        }
    }
}
