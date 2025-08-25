/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.util;

import java.io.*;
import java.util.NoSuchElementException;

public final class FastNoSuchElementException extends NoSuchElementException {
    @Serial
    private static final long serialVersionUID = 2303108654138257697L;
    private static final FastNoSuchElementException INSTANCE = new FastNoSuchElementException();

    private FastNoSuchElementException() {
    }

    public static NoSuchElementException instance() {
        return INSTANCE;
    }

    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
