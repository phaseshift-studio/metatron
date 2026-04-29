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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.furi.fURI;

import java.util.List;

public class Sugar {

    public enum Position {
        PREFIX,
        INFIX,
        POSTFIX,
        WRAP
    }

    private final String startToken;
    private final String endToken;
    private final List<fURI> instChain;
    private final int argCount;
    private final Position position;

    private Sugar(final String startToken, final String endToken, final List<fURI> instChain, final int argCount, final Position position) {
        this.startToken = startToken;
        this.endToken = endToken;
        this.instChain = instChain;
        this.argCount = argCount;
        this.position = position;
    }

    public static Sugar prefix(final String token, final List<fURI> instChain, final int argCount) {
        return new Sugar(token, null, instChain, argCount, Position.PREFIX);
    }

    public static Sugar wrap(final String startToken, final String endToken, final List<fURI> instChain, final int argCount) {
        return new Sugar(startToken, endToken, instChain, argCount, Position.WRAP);
    }

    public static Sugar infix(final String token, final List<fURI> instChain) {
        return new Sugar(token, null, instChain, 2, Position.INFIX);
    }

    public String getStartToken() {
        return this.startToken;
    }

    public String getEndToken() {
        return this.endToken;
    }

    public List<fURI> getInstChain() {
        return this.instChain;
    }

    public int getArgCount() {
        return this.argCount;
    }

    public Position getPosition() {
        return this.position;
    }
}
