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
import studio.phaseshift.metatron.isa.llm.type.mAgent;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.util.MTronException;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MailMod implements Mod {
    @Override
    public void apply(final mModel model, final AiServices<mAgent> services) {
        if (model.notes().isPresent())
            if (null == model.vid())
                throw MTronException.of("mail mod requires an anchored model");
            else {
                try {
                    model.addSystemMessage("""
                                           ### IMPORTANT ###
                                           Before starting your prompted task, be sure to check your mail.
                                           If the mail requires little effort to accomplish, then handle the mail
                                           immediately. Otherwise, push the mail back on your mail stack and address it
                                           at non-prompt point.
                                         
                                           To check your mail, use your provided mtron `eval` tool with the following argument:
                                             `@<%s/feature/mail>.remove(0).to(m)`
                                           A result of `noobj` means "no mail" at this time. To push an unhandled mail back
                                           on the mail stack, use the `eval` tool with the following argument:
                                             `@<%s/feature/mail> >>= +*m`
                                           """.formatted(model.vid(),model.vid()));
                } catch (Exception e) {
                    throw MTronException.of("unable to setup mail: %s", e);
                }
            }
    }
}
