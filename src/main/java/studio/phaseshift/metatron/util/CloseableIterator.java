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

package studio.phaseshift.metatron.util;

import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

public interface CloseableIterator<S> extends Iterator<S>, Closeable, AutoCloseable {

    @Override
    void close() throws IOException;

    public static void closeIterator(final Iterator<?> iterator) {
        try {
            if (iterator instanceof CloseableIterator)
                ((CloseableIterator<?>) iterator).close();
        } catch (final IOException e) {
            LoggerFactory.getLogger(CloseableIterator.class).error(e.getMessage());
        }
    }
}