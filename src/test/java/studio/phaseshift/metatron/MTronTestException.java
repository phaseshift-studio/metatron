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

package studio.phaseshift.metatron;

import studio.phaseshift.metatron.isa.mach.type.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MTronTestException extends RuntimeException {

    private MTronTestException(final String message) {
        super(Graphitty.string(message));
        //  this.printStackTrace();
    }

    private MTronTestException(final String message, final Throwable cause) {
        super(Graphitty.string(message), cause);
        // this.printStackTrace();
    }

    public static MTronTestException of(final Throwable cause) {
        if(null == cause)
            return new MTronTestException("null cause");
        if (cause instanceof MTronTestException)
            return (MTronTestException) cause;
        else {
            try {
                return new MTronTestException(cause.getClass().getSimpleName() + ": " + cause.getMessage());
            } catch (final Throwable e) {
                return new MTronTestException(cause.getClass().getSimpleName() + ": " + Highlighter.unformat(cause.getMessage()));
            }
        }
    }

    public static MTronTestException of(final Throwable cause, final String format, final Object... args) {
        return new MTronTestException(Graphitty.string(format.formatted(args)), cause);
    }
}