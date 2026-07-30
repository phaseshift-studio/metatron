package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Enables an agent to self-direct a multi-pass reasoning loop.
 * The LLM signals continuation by appending a block to its response
 * (parsed by {@link Agent#MTRON_BLOCK}):
 * <pre>
 *   &lt;&lt;mtron:loop&gt;&gt;
 *   [prompt=&gt;"next instructions",
 *    label=&gt;"research"]
 *   &lt;&lt;/mtron:loop&gt;&gt;
 * </pre>
 * The Agent writes the parsed Rec to {@code res("loop")}.
 * LoopFeature reads it here and kicks off the next iteration.
 */
public class LoopFeature extends AbstractFeature {

    private static final fURI LOOP = res("loop");
    private static final fURI LOOP_RESULTS = res("loop_results");

    protected static final
    String LOOP_FEATURE_INSTRUCTIONS = """
                                       You can operate in a multi-pass reasoning loop.
                                       When a task requires multiple rounds of tool use, verification,
                                       or information gathering, append the `<<mtron:loop>>` markup block
                                       to your response. For example:
                                       
                                           <<mtron:loop>>
                                               [prompt=>"instructions for your next pass",
                                                label=>"research",
                                                delay=>second::10.0]
                                           <</mtron:loop>>
                                       
                                       The `prompt` becomes your next user message — be precise.
                                       The `label` and `delay` are optional.
                                       `delay` accepts any time::T (millis, second, minute, hour).
                                       The delay between iterations can be used for polling or rate-limited workflows.
                                       
                                       When the task is complete, respond normally without the <<mtron:loop>> block.
                                       
                                       **IMPORTANT**: This skill is about formatting your response, not calling a function.
                                       
                                       You are constrained to %%%1 max loops and %%%2 maximum time.
                                       """;

    final int maxLoops;
    final long maxTimeMillis;
    final long delayMillis;
    final List<String> preserve;
    final List<Rec> iterations = new ArrayList<>();
    long startTime;
    int loopCount;

    public LoopFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.maxLoops = this.at(uri("max_loop")).orElse(jnt(10)).intValue().intValue();
        final Obj maxTime = this.at(uri("max_time"));
        this.maxTimeMillis = maxTime.isNoObj() ? Long.MAX_VALUE
                : (long) (maxTime.asReal().realValue() * 1000.0d);
        final Obj delay = this.at(uri("delay"));
        this.delayMillis = delay.isNoObj() ? 0
                : (long) (delay.asReal().realValue() * 1000.0d); // seconds → millis
        final Obj preserveObj = this.at(uri("preserve"));
        this.preserve = preserveObj.isNoObj() ? List.of()
                : preserveObj.asLst().lstValue().stream().map(o -> o.strValue()).toList();
    }

    @Override
    public Lst skill() {
        final String instructions = LOOP_FEATURE_INSTRUCTIONS
                .replace("%%%1", this.maxLoops > 0 ? this.maxLoops + "" : "<no limit>")
                .replace("%%%2", this.maxTimeMillis > 0 ? this.maxTimeMillis + "" : "<no limit>");
        return lst(rec(uri(NAME), uri("loop"),
                uri(DESC), str("Multi-pass reasoning loop with iteration control and polling support"),
                uri(CONTENT), str(instructions)));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // New user prompt: no carryover loop signal → reset counters
        if (agent.at(LOOP).isNoObj()) {
            this.loopCount = 0;
            this.iterations.clear();
            this.startTime = System.currentTimeMillis();
            agent.at(LOOP_RESULTS, lst(), MUTABLE);
        }
        return noobj();
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str response) {
        // Record this iteration
        final Obj chatResult = agent.at(res(CHAT));
        final Obj timeResult = agent.at(res("time"));
        final Rec entry = rec(
                uri("iteration"), jnt(this.loopCount + 1),
                uri("result"), rec(
                        uri(CHAT), chatResult,
                        uri("time"), timeResult));
        this.iterations.add(entry);
        agent.at(LOOP_RESULTS, lst(this.iterations.stream().map(r -> (Obj) r).toList()), MUTABLE);

        // Read loop signal from blackboard (parsed by Agent from <<mtron:loop>> block)
        final Obj loopSignal = agent.at(LOOP);
        if (loopSignal.isNoObj()) return;

        final Rec signal = loopSignal.asRec();
        if (signal.isNoObj()) return;

        String nextPrompt = signal.at(uri(PROMPT)).strValue();
        if (nextPrompt.isBlank()) return;
        nextPrompt = "[previous iteration agent prompt] " + nextPrompt;

        // Safety valves
        this.loopCount++;
        if (this.loopCount >= this.maxLoops) {
            agent.logger().warn("loop_feature: max iterations reached (%d)", this.maxLoops);
            return;
        }
        final long elapsed = System.currentTimeMillis() - this.startTime;
        if (elapsed >= this.maxTimeMillis) {
            agent.logger().warn("loop_feature: max time exceeded (%dms)", this.maxTimeMillis);
            return;
        }

        // Preserve fields across iterations
        for (final String field : this.preserve) {
            final Obj val = agent.at(res(field));
            if (!val.isNoObj())
                agent.at(LOOP.extend(field), val, MUTABLE);
        }

        // Clear loop signal for next iteration
        agent.at(LOOP, noobj(), MUTABLE);

        // Delay before next pass (polling, rate-limiting)
        if (this.delayMillis > 0) {
            try {
                Thread.sleep(this.delayMillis);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Next pass
        agent.chat(nextPrompt, noobjRec());
    }

    @Override
    public void onError(final Agent agent, final Fail fail) {
        this.iterations.add(rec(
                uri("iteration"), jnt(this.loopCount + 1),
                uri(ERROR), fail.isNoObj() ? noobj() : fail));
        agent.at(LOOP_RESULTS, lst(this.iterations.stream().map(r -> (Obj) r).toList()), MUTABLE);
    }
}
