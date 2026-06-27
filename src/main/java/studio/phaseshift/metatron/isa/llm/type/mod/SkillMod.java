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
import dev.langchain4j.skills.Skills;
import studio.phaseshift.metatron.isa.llm.type.mAgent;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SkillMod implements Mod {

    @Override
    public void apply(final mModel model, final AiServices<mAgent> services) {
        if (model.skills().isPresent()) {
            try {
                final Skills skills = new Skills.Builder().skills(
                        model.skills().get()
                                .elements()
                                .filter(s -> !s.isUri())
                                .map(s -> mSkill.of(s.apply().asRec()).toSkill())
                                .toList()).build();
                services.toolProvider(skills.toolProvider());
                model.addSystemMessage(
                        "\nYou have access to the following skills:\n" +
                                skills.formatAvailableSkills()
                                + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
            } catch (Exception e) {
                throw MTronException.of("unable to setup skills: %s", e);
            }
        }
    }
}
