/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.parser.mtronParser;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class webInstSet extends MInstSet {

    public static final fURI MWEB_TID = f("/web");
    public static final fURI INST_TID = MWEB_TID.extend("inst");
    public static final fURI PAGE_TID = MWEB_TID.extend("page");
    public static final fURI CSS_TID = MWEB_TID.extend("css");
    private static final WebTranslator WEB_TRANSLATOR = new WebTranslator();
    private static final JSONTranslator JSON_TRANSLATOR = new JSONTranslator();

    public webInstSet(final fURI vid) {
        super(MWEB_TID, vid);
    }

    public static webInstSet create() {
        return new webInstSet(fURI.NULL);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(
                webSpace.WEB_TYPE,
                T(PAGE_TID, mtronParser.parse("?[html=>?[head=>_,body=>_]]")),
                T(CSS_TID)).collect(Collectors.toSet());
    }
}