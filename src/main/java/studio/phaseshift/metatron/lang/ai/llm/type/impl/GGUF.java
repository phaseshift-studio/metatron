package studio.phaseshift.metatron.lang.ai.llm.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.Uri;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInt;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.Common;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.Space.HOST;
import static studio.phaseshift.metatron.lang.Space.NAME;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.INST_TID;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.LLM_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Common.mutableMap;
import static studio.phaseshift.metatron.util.Common.mutableOrderedMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class GGUF extends MRec {

    public static final String SHAPE = "shape";
    public static final String OFFSET = "offset";
    public static final String GGML = "ggml";
    public static final String TENSOR = "tensor";

    public static final fURI GGUF_TID = LLM_TID.extend("gguf");
    public static final fURI TENSOR_REF_TID = GGUF_TID.extend("tensor_ref");
    private com.llama4j.gguf.GGUF rawData;
    public static final String VERSION = "version";
    public static final String PATH = "path";
    public static final String FILE = "file";
    public static final String FAMILY = "family";
    public static final String QUANT = "quant";
    public static final String SIZE = "size";
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static Type GGUF_TYPE = T(GGUF_TID, isa_(rec(uri(FILE), T(URI_TID), uri(PATH), T(URI_TID))));

    public static Type TENSOR_REF_TYPE = T(TENSOR_REF_TID,
            isa_(rec(uri(NAME), T(URI_TID),
                    uri(OFFSET), T(INT_TID),
                    uri(GGML), T(URI_TID))));

    public GGUF(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        final Path modelPath = Path.of(jvm.get(uri(PATH)).<Uri>as().uriValue().extend(jvm.get(uri(FILE)).<Uri>as().uriValue()).toString());
        try {
            LOG.debug("verifying model GGUF file: %s", modelPath);
            this.rawData = com.llama4j.gguf.GGUF.read(modelPath);
            // file and path come in constructor map
            this.jvm().put(uri(VERSION), jnt(this.rawData.getVersion()));
            this.jvm().put(uri(TENSOR), instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(REC_TID.maybeSome()), lst(), (lhs, inst) -> this.tensors()));
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw MTronException.of("unable to verify corrupted ggruf file %s: %s", modelPath, e);
        } catch (final Exception e) {
            throw MTronException.of("unable to verify %s: %s", modelPath, e);
        }
    }

    public Rec tensors() {
        return this.rawData.getTensors().stream().map(ti -> rec(
                        uri(NAME), uri(ti.name()),
                        uri(OFFSET), jnt(ti.offset()),
                        uri(GGML), uri(ti.ggmlType().name()),
                        uri(SHAPE), lst(Arrays.stream(ti.shape()).mapToObj(MInt::jnt).map(Obj::<Obj>as).toList())).tid(TENSOR_REF_TID))
                .map(r -> rel(r.at(NAME), r))
                .collect(new Common.RecCollector());
    }

    public static GGUF of(final fURI location, final fURI vid) {
        final Path modelPath = Path.of(location.toString());
        return new GGUF(mutableOrderedMap(
                uri(FILE), uri(modelPath.getFileName().toString()),
                uri(PATH), uri(modelPath.getParent().toAbsolutePath().toString())), GGUF_TID, vid);
    }
}
