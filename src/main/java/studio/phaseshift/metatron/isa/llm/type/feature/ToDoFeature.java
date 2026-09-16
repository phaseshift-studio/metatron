package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TODO_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A persistent scratchpad the agent owns across the entire session.
 * Never cleared between chat calls.  The agent reads it via system
 * message injection (no tool needed) and writes updates via
 * {@code <<mtron:ledger>>} blocks at end of response.
 */
public class ToDoFeature extends AbstractFeature {

    public ToDoFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_FEATURE_TID);
    }

    public Lst todos(final Agent agent) {
        return this.getRootObj(agent).orElse(lst());
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        agent.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().addSkill(mSkill.of(rec(
                uri(NAME), uri(LLM_TODO_FEATURE_TID.name()),
                uri(DESC), str("persistent agent-owned todo list for cross-turn task tracking"),
                uri(CONTENT), str("the following tools allow you to create and maintain a todo list: get_todo, add_todo, remove_todo"),
                uri(TOOL), lst(
                        docWrap(instC(f("get_todo").dom(A.maybe()).rng(LST_TID), lst(), (lhs, inst) -> Router.readFromSpace(this.getRoot(agent)).orElse(lst())),
                                "maybe an obj (optional)",
                                "a todo lst",
                                Map.of(),
                                "retrieve an ordered lst of todos"),
                        docWrap(instC(f("add_todo").dom(A.maybe()).rng(LST_TID), rec(uri(TEXT), STR_TYPE, uri(INDEX).maybe(), INT_TYPE), (lhs, inst) -> {
                                    Lst todo = Router.readFromSpace(this.getRoot(agent)).orElse(lst());
                                    final int index = inst.arg(INDEX, 1).orElse(jnt(-1)).intValue().intValue();
                                    if (index >= 0)
                                        todo.lstValue().add(index, inst.arg(TEXT, 0));
                                    else todo.lstValue().add(inst.arg(TEXT, 0));
                                    Router.writeToSpace(this.getRoot(agent), todo);
                                    return todo;
                                }), "maybe an obj (optional)", "an updated todo lst",
                                Map.of(uri(TEXT), "the todo item to add to the todo lst",
                                        uri(INDEX).maybe(), "the index in the lst to add the todo (default: end of lst)"), "add a new item to the todo lst"),
                        docWrap(instC(f("remove_todo").dom(A.maybe()).rng(LST_TID), rec(uri(INDEX), INT_TYPE), (lhs, inst) -> {
                                    Lst todo = Router.readFromSpace(this.getRoot(agent)).orElse(lst());
                                    final int index = inst.arg(INDEX, 0).intValue().intValue();
                                    todo.lstValue().remove(index);
                                    Router.writeToSpace(this.getRoot(agent), todo);
                                    return todo;
                                }), "maybe an obj (optional)", "an updated todo lst",
                                Map.of(uri(TEXT), "the index of the todo item to remove"), "remove an item from the todo lst")))));
        return noobj();
    }
}
