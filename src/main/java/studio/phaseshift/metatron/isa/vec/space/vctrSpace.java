package studio.phaseshift.metatron.isa.vec.space;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.*;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.vec.vecInstSet.VEC_ISA_TID;

/**
 * vctrSpace: A space for vector similarity searches using ChromaDB.
 * <p>
 * URI Pattern: vctr:/<collection>/[ADD|QUERY|DELETE]
 *
 * @author Marko A. Rodriguez
 */
public class vctrSpace extends AbstractSpace<Client> {

    private static final Logger LOG = LoggerFactory.getLogger(vctrSpace.class);

    // Namespace/Type identification
    public static final fURI VCTR_SPACE_TID = VEC_ISA_TID.extend("space").extend("vctrspace");

    public static final Type VCTR_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID) // Generic space type
            .vid(VCTR_SPACE_TID)
            .isaPredicate(rec(
                    uri(HOST), URI_TYPE,
                    uri(PATTERN), URI_TYPE,
                    uri(ROUTE), REC_TYPE)
            ).create();

    private final Client client;
    private final EmbeddingFunction embeddingFunction;

    public static vctrSpace of(final Client client, final EmbeddingFunction ef,
                               final Map<Obj, Obj> jvm, final fURI vid) {
        return new vctrSpace(client, ef, jvm, vid);
    }

    private vctrSpace(final Client client, final EmbeddingFunction ef,
                      final Map<Obj, Obj> jvm, final fURI vid) {
        super(null, jvm, VCTR_SPACE_TID, vid); // Client is managed via JVM/Config injection typically
        this.client = client;
        this.embeddingFunction = ef;
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (key) -> {
            final fURI keyQless = key.qLess();
            // Extract collection name from URI: vctr:/my-collection/query
            String collectionName = keyQless.name().replace("vctr:", "").split("/")[0];

            try {
                Collection collection = client.getCollection(collectionName, embeddingFunction);

                // Implementation of query logic based on URI pattern
                if (keyQless.name().contains("query")) {
                    // For simplicity in this first pass, we assume the input is provided via a parameter or 
                    // we use the text extracted from the path if it's formatted as such.
                    // In a real implementation, this would integrate with the Mtron expression engine.
                    String queryText = keyQless.toString().substring(keyQless.toString().lastIndexOf("/") + 1);

                    Collection.QueryResponse result = collection.query(Collections.singletonList(queryText), -1, Map.of(), Map.of(), List.of());

                    return result.getDocuments().stream()
                            .map(doc -> IdObj.of(key, str(String.join("\n", doc)))) // Simplified for demo
                            .iterator();
                }

                return IteratorUtil.of();
            } catch (Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    @Override
    public Stream<IdObj> readStream(final fURI pattern) {
        // Implement stream-based reading for large vector sets
        return Stream.empty();
    }

    @Override
    public Stream<IdObj> writeStream(final fURI pattern, final Obj obj) {
        // Implementation of 'add' via directWriter
        return Stream.empty();
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            // Logic for collection.add(...)
            return obj;
        };
    }

    @Override
    public void close() {
        super.close();
    }
}
