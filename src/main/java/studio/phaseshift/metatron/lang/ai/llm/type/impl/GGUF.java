package studio.phaseshift.metatron.lang.ai.llm.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.Space.HOST;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.LLM_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Common.mutableMap;
import static studio.phaseshift.metatron.util.Common.mutableOrderedMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GGUF extends MRec {

    public static final fURI GGUF_TID = LLM_TID.extend("gguf");
    private com.llama4j.gguf.GGUF rawData;
    public static final String VERSION = "version";
    public static final String PATH = "path";
    public static final String FILE = "file";
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public GGUF(final fURI location, final fURI tid, final fURI vid) {
        super(mutableOrderedMap(), tid, vid);
        final Path modelPath = Path.of(location.toString());
        try {
            LOG.debug("verifying model GGUF file: %s", modelPath);
            this.rawData = com.llama4j.gguf.GGUF.read(modelPath);
            this.jvm().put(uri(FILE), uri(modelPath.getFileName().toString()));
            this.jvm().put(uri(VERSION), jnt(this.rawData.getVersion()));
            this.jvm().put(uri(PATH), uri(modelPath.getParent().toAbsolutePath().toString()));
        } catch (final Exception e) {
            LOG.error("unable to verify %s: %s", modelPath, e);
            this.rawData = null;
        }
    }

    public static GGUF of(final fURI location, final fURI vid) {
        try {
            return new GGUF(location, GGUF_TID, vid);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}
