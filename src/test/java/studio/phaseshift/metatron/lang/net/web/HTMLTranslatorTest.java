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

package studio.phaseshift.metatron.lang.net.web;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.translator.HTMLTranslator;

import java.io.File;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class HTMLTranslatorTest extends MetatronTest {

    @Test
    public void testWebPageParsing() {
        final HTMLTranslator t = new HTMLTranslator();
        final Rec page = (Rec) t.translatePage(new File("./docs/images/metatron-character.html"));
        //LOG.info("%s", Jsoup.parse("/home/killswitch/Desktop/funny.html"));
        LOG.info("%s", page);
        LOG.info("%s", t.translate(page));
    }
}
