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

package studio.phaseshift.metatron.lang.ai.llm.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Bytes;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.LLM_INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Audio extends MRec {

    public static final fURI AUDIO_TID = LLM_INSTSET_TID.extend("audio");
    public static final Type AUDIO_TYPE = T(AUDIO_TID, null, instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(AUDIO_TID), lst(T(REC_TID, isa_(rec(uri("location"), T(URI_TID))))), (lhs, inst) -> {
        return new Audio(lhs.jvm(), AUDIO_TID, lhs.vid());
    }));

    public Audio(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        for (AudioFileFormat.Type fileFormat : AudioSystem.getAudioFileTypes())
            Graphitty.log(this).info("supported audio format: %s", fileFormat);
    }

    public void play() {
        AudioInputStream ais = null;
        try {
            final Bytes location = this.at("location");
            // URL url = URI.create(location.toString()).toURL();
            //File file = Path.of(location.toString()).toFile();
            final Clip clip = AudioSystem.getClip();
            // getAudioInputStream() also accepts a File or InputStream
            ByteArrayInputStream bai = new ByteArrayInputStream(location.bytesValue().array());
            ais = AudioSystem.getAudioInputStream(bai);
            final AudioFormat baseFormat = ais.getFormat();
            Graphitty.log(this).info("base format : " + baseFormat);
            final AudioFormat decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            Graphitty.log(this).info("decoded format : " + decodedFormat);
            final AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, ais);
            clip.open(din);
            clip.start();
        } catch (final UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            throw MTronException.of(e);
        } finally {
            try {
                if (ais != null) {
                    ais.close();
                }
            } catch (final IOException e) {
                // do nothing
            }
        }
    }

}
