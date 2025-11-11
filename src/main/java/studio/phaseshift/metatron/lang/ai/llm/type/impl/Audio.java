package studio.phaseshift.metatron.lang.ai.llm.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Bytes;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.MLLM_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Audio extends MRec {

    public static final fURI AUDIO_TID = MLLM_TID.extend("audio");
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
