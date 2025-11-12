package studio.phaseshift.metatron.lang.net.web;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.apache.tinkerpop.shaded.kryo.io.ByteBufferInputStream;
import org.petitparser.context.Result;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.ui.*;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Translator;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

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
