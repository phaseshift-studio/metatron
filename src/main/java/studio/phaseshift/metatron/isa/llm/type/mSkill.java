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

package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.skills.DefaultSkill;
import dev.langchain4j.skills.DefaultSkillResource;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skill;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.io.File;
import java.nio.file.Path;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mSkill extends MRec {

    public mSkill(final Skill skill, final fURI tid, final fURI vid) {
        super(mutableMap(
                uri(NAME), uri(skill.name()),
                uri(DESC), str(skill.description()),
                uri(CONTENT), str(skill.content()),
                uri(ENTRY), skill.resources().isEmpty() ? noobj() : lst(skill.resources().stream().map(r -> rec(uri(DIR), uri(r.relativePath()), uri(CONTENT), str(r.content())).<Obj>as()).toList())), tid, vid);
    }

    public mSkill(final Rec skillRec, final fURI tid, final fURI vid) {
        super(skillRec.jvm(), tid, vid);
    }

    public Skill toSkill() {
        DefaultSkill.Builder skill = new DefaultSkill.Builder();
        if (this.has(NAME))
            skill = skill.name(this.at(NAME).uriValue().toString());
        if (this.has(DESC))
            skill = skill.description(this.at(DESC).strValue());
        if (this.has(CONTENT))
            skill = skill.content(this.at(CONTENT).strValue());
        if (this.has(ENTRY))
            skill = skill.resources(this.at(ENTRY).asLst().elements()
                    .map(Obj::asRec)
                    .map(e -> new DefaultSkillResource.Builder()
                            .relativePath(e.at(DIR).uriValue().toString())
                            .content(e.at(CONTENT).strValue())
                            .build())
                    .toList());
        return skill.build();
    }

    public static mSkill of(final File skillDir) {
        return new mSkill(FileSystemSkillLoader.loadSkill(Path.of(skillDir.getAbsolutePath())), LLM_SKILL_TID, null);
    }
    
    public static mSkill of(final Rec skillRec) {
        return new mSkill(skillRec, LLM_SKILL_TID, skillRec.vid());
    }
}
