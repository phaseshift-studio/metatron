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

package studio.phaseshift.metatron.isa.web.parser;

import studio.phaseshift.metatron.isa.Translator;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AudioTranslator implements Translator<Obj, BufferedInputStream> {

    private static final GraphittyLogger LOG = Graphitty.log(AudioTranslator.class);

    public AudioTranslator() {

    }

    @Override
    public Obj translate(final BufferedInputStream bis) {
        int counter = 0;

        try (bis) {
            while (bis.read() != -1) {
                counter++;
            }
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
        return str("audio file length: " + counter);

    }

    @Override
    public BufferedInputStream translate(final Obj obj) {
        return new BufferedInputStream(InputStream.nullInputStream());
    }
}
