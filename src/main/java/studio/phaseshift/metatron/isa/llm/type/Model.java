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

package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatMemoryStore;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MODEL_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class Model extends MRec {
    public record Provider(String name, fURI host, String apiKey) {
    }

    public Model(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        //  new JsonSchema.Builder().
        //  final ResponseFormat responseFormat = ResponseFormat.builder().jsonSchema()

    }

    public static Model model(final Rec model) {
        return new Model(model.jvm(), MODEL_TID, model.vid());
    }

    public String model() {
        return this.at(NAME).uriValue().toString();
    }

    public Provider provider() {
        return new Provider(
                this.at(PROVIDER).asRec().at(NAME).strValue(),
                this.at(PROVIDER).asRec().at(HOST).uriValue(),
                this.at(API_KEY).orElse(str("")).strValue());
    }

    public Optional<Map<ToolSpecification, ToolExecutor>> tools() {
        return Optional.<Lst>ofNullable(this.at(TOOL).orElse(null)).map(t -> t.elements()
                .filter(Obj::isInst)
                .map(i -> OLLM.mtronInstToolSpecification(i.asInst()))
                .collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1)));
    }

    public Optional<fURI> thinking() {
        return Optional.<Obj>ofNullable(this.at(THINK).orElse(null)).map(Obj::uriValue);
    }

    public Optional<fURI> memory() {
        return Optional.<Obj>ofNullable(this.at(MEMORY).orElse(null)).map(Obj::uriValue);
    }

    public Agent agent() {
        final StreamingChatModel streamingChatModel = LLMFactory.createModel(this, this.model());
        final AiServices<Agent> service = AiServices.builder(Agent.class)
                .streamingChatModel(streamingChatModel);
        if (this.memory().isPresent())
            service.chatMemory(MessageWindowChatMemory.builder()
                    .maxMessages(Router.readFromSpace(this.memory().get().extend("max")).orElse(jnt(15)).intValue().intValue())
                    .id(this.memory().get())
                    .chatMemoryStore(SpaceChatMemoryStore.single())
                    .build());
        tools().ifPresent(t -> service.tools(t).executeToolsConcurrently(BootLoader.getExecutor()));

        /// ////////////////////////////////////////////////////////////////////////////////////////
        return service
                //.retrievalAugmentor(new SpaceRetrievalAugmentor(null, null, null))
                //.contentRetriever(new SpaceContentRetriever())
                .build();
    }
    
  /*  public Rec query(final Rec query) {
        this.agent()
    }*/

    public Str chat(final String message) {
        final StringBuilder response = new StringBuilder();
        Router.global().stats().ioStats().incrBytesSent(message.getBytes().length);
        final AtomicBoolean isThinking = new AtomicBoolean(false);
        final AtomicBoolean isResponding = new AtomicBoolean(false);
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean isTooling = new AtomicBoolean(false);
        final AtomicReference<MTronException> isError = new AtomicReference<>();
        try {
            final Agent agent = this.agent();
            agent.chat(message)
                    .onToolExecuted(tool -> {
                        this.logger().info("tool executed: %s(%s) => %s", tool.request().name(), tool.request().arguments(), tool.result());
                        isTooling.set(false);
                    })
                    .onCompleteResponse(c -> {
                        isComplete.set(true);
                        isResponding.set(false);
                        this.logger().none("\n");
                    })
                    .onPartialToolCall(partialToolCall -> {
                        if (!isTooling.getAndSet(true)) {
                            this.logger().none(Graphitty.sillyPrint("tooling:\n", true, true));
                            this.logger().none("\t{{y}}partial{{X}}: {{b}}%s{{g}}({{b}}%s{{g}}){{X}}\n", partialToolCall.name(), partialToolCall.partialArguments());
                        }
                    })
                    .onPartialResponse(s -> {
                        if (!isResponding.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("responding", true, true));
                        Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                        response.append(s);
                    })
                    .onPartialThinking(t -> {
                        if (!isThinking.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("thinking...\n", true, true));
                        this.thinking().ifPresent(post -> {
                            Router.global().write(post, str(t.text()));
                        });
                        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                        this.logger().none("{{b}}%s{{X}}", t.text());
                    })
                    .onError(e -> isError.set(MTronException.of(e))).start();
            while (!isComplete.get()) {
                CommonUtil.sleepThread(250);
                if (isResponding.get())
                    this.logger().none(Graphitty.sillyPrint(".", true, false));
            }
            if (null != isError.get())
                throw isError.get();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        return str(response.toString());
    }
}



