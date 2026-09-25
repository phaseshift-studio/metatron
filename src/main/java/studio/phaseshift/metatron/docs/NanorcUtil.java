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

package studio.phaseshift.metatron.docs;

import studio.phaseshift.metatron.util.MTronException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NanorcUtil {

    private NanorcUtil() {
        //do nothing
    }

    private static Set<String> SUPPORTED_LANGUAGES = null;

    public static Set<String> supportedLanguages() {
        if (null == SUPPORTED_LANGUAGES) {
            try (final Stream<String> temp = Files.list(Path.of("conf/nanorc"))
                    .filter(f -> f.getFileName().toString().endsWith(".nanorc"))
                    //.peek(f -> LOG.info("loading syntax highlighting language: %s", f))
                    .map(f -> f.getFileName().toString().split("\\.")[0])) {
                SUPPORTED_LANGUAGES = new HashSet<>(temp.toList());
            } catch (final Exception e) {
                throw MTronException.of("unable to access conf/nanorc directory: %s", e);
            }
        }
        return Collections.unmodifiableSet(SUPPORTED_LANGUAGES);
    }
}
