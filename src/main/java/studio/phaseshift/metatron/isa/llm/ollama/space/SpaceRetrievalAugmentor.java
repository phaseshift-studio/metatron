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

package studio.phaseshift.metatron.isa.llm.ollama.space;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SpaceRetrievalAugmentor implements RetrievalAugmentor {
    DefaultRetrievalAugmentor defaultRetrievalAugmentor = new DefaultRetrievalAugmentor(null, null, null, null, null);

    private final QueryTransformer queryTransformer;
    private ContentRetriever contentRetriever;
    private ContentInjector contentInjector;

    public SpaceRetrievalAugmentor(final QueryTransformer queryTransformer, final ContentRetriever contentRetriever, final ContentInjector contentInjector) {
        this.queryTransformer = queryTransformer;

    }


    @Override
    public AugmentationResult augment(final AugmentationRequest request) {
        UserMessage userMessage = (UserMessage) request.metadata().chatMessage();
        //this.queryTransformer.transform(Query.from(request.metadata().chatMessage().text()));
        return AugmentationResult.builder().chatMessage(request.metadata().chatMessage()).build();
    }


}
