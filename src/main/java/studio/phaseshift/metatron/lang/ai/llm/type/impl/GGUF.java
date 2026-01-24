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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MInt;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.LLM_INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableOrderedMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class GGUF extends MRec {

    public static final String SHAPE = "shape";
    public static final String OFFSET = "offset";
    public static final String GGML = "ggml";
    public static final String TENSOR = "tensor";

    public static final fURI GGUF_TID = LLM_INSTSET_TID.extend("gguf");
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
            //this.jvm().put(uri(TENSOR),  instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(REC_TID.maybeSome()), lst(), (lhs, inst) -> this.tensors()));
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
                .collect(new CommonUtil.RecCollector());
    }

    public static GGUF of(final fURI location, final Tuple.Pair<Long, Long> size, final fURI quantization, final fURI family, final fURI vid) {
        final Path modelPath = Path.of(location.toString());
        final Map<Obj, Obj> map = mutableOrderedMap(
                uri(FILE), uri(modelPath.getFileName().toString()),
                uri(PATH), uri(modelPath.getParent().toAbsolutePath().toString()));
        if (null != size)
            map.put(uri(SIZE), lst(jnt(size.get0()), jnt(size.get1())));
        if (null != quantization)
            map.put(uri(QUANT), uri(quantization));
        if (null != family)
            map.put(uri(FAMILY), uri(family));
        return new GGUF(map, GGUF_TID, vid);
    }
}
