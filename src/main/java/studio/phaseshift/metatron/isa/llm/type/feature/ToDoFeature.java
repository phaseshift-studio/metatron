package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.feature.service.*;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_at_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TODO_FEATURE_TID;


    public ToDoFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_SERVICE_TID);
    }

    @Override
    public Set<fURI> uses() {
        // concept/chat/message enrich the todo (concept links, message back-refs, the skill's
        // access paths) but the todo list itself works without them — degraded, not broken
        return Set.of(LLM_CONCEPT_SERVICE_TID, LLM_CHAT_SERVICE_TID, LLM_MESSAGE_SERVICE_TID);
    }

    public Lst todos(final Agent agent) {
        return this.getRootObj(agent).orElse(lst());
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final ConceptService conceptFeature = agent.service(ConceptService.class).orElse(null);
        final ChatService chatFeature = agent.service(ChatService.class).orElse(null);
        final MessageService messageFeature = agent.service(MessageService.class).orElse(null);
        agent.requireService(SkillService.class).addSkill(mSkill.of(rec(
                uri(NAME), uri(LLM_TODO_FEATURE_TID.name()),
                uri(DESC), str("persistent agent-owned todo list for cross-turn task tracking"),
                uri(CONTENT), str("""
                                  ---[todo_feature]---
                                  the following tools allow you to create and maintain a todo list: get_todo, add_todo, remove_todo.
                                  todo::T is defined:
                                  %s
                                  todo::T concepts accessed using mtron eval tool:
                                  %s
                                  todo::T messages accessed using mtron eval tool:
                                  %s
                                  """.formatted(
                        CommonUtil.indent(LLM_TODO_TYPE.toString(), 2),
                        CommonUtil.indent(null == conceptFeature ? "<no concepts>" : "*<" + conceptFeature.root(agent).extend("concept_name") + ">", 2),
                        CommonUtil.indent(null == messageFeature ? "<no messages>" : "*<" + messageFeature.root(agent).extend("message_id").toString() + ">", 2))),
                uri(TOOL), lst(
                        docWrap(instC(f("get_todo").dom(NOOBJ_TID.zero()).rng(LST_TID.poly(LLM_TODO_TID.maybeSome())), lst(), (lhs, inst) -> Router.readFromSpace(this.getRoot(agent)).orElse(lst())),
                                "noobj",
                                "a todo lst",
                                Map.of(),
                                "retrieve an ordered lst of todos"),
                        docWrap(instC(f("add_todo").dom(NOOBJ_TID.zero()).rng(LST_TID.poly(LLM_TODO_TID.maybeSome())), rec(uri(TODO), LLM_TODO_TYPE, uri(INDEX).maybe(), INT_TYPE), (lhs, inst) -> {
                                    Lst todoLst = Router.readFromSpace(this.getRoot(agent)).orElse(lst());
                                    final Rec todo = inst.arg(TODO, 0).asRec().tid(LLM_TODO_TID);
                                    if (todo.at(STATUS).isNoObj())
                                        todo.at(STATUS, uri("open"), MUTABLE);
                                    if (todo.at(TIME).isNoObj())
                                        todo.at(TIME, mathInstSet.nowDatetime(), MUTABLE);
                                    final int index = inst.arg(INDEX, 1).orElse(jnt(-1)).intValue().intValue();
                                    if (index >= 0)
                                        todoLst.lstValue().add(index, inst.arg(TODO, 0));
                                    else todoLst.lstValue().add(inst.arg(TODO, 0));
                                    if (null != conceptFeature) {
                                        final Set<Obj> uris = new LinkedHashSet<>(todo.at(CONCEPT).orElse(lst()).lstValue());
                                        uris.addAll(conceptFeature.processConcepts(agent, todo.at(TEXT).strValue(), true).stream().map(u -> auto_at_(u).tryToInst()).toList());
                                        todo.at(CONCEPT, lst(uris.stream()), MUTABLE);
                                    }
                                    if (null != chatFeature) {
                                        final Rec lastMessage = chatFeature.lastMessage();
                                        if (lastMessage.hasVID())
                                            todo.at(MESSAGE, todo.at(MESSAGE).orElse(lst()).add(auto_from_(lastMessage.vid())), MUTABLE);
                                    }
                                    Router.writeToSpace(this.getRoot(agent), todoLst);
                                    return todoLst;
                                }), "noobj", "an updated todo lst",
                                Map.of(uri(TODO), "the todo item to add to the todo lst",
                                        uri(INDEX).maybe(), "the index in the lst to add the todo (default: end of lst)"), "add a new item to the todo lst"),
                        docWrap(instC(f("remove_todo").dom(NOOBJ_TID.zero()).rng(LST_TID.poly(LLM_TODO_TID.maybeSome())), rec(uri(INDEX), INT_TYPE), (lhs, inst) -> {
                                    Lst todoLst = Router.readFromSpace(this.getRoot(agent)).orElse(lst());
                                    final int index = inst.arg(INDEX, 0).intValue().intValue();
                                    todoLst.lstValue().remove(index);
                                    Router.writeToSpace(this.getRoot(agent), todoLst);
                                    return todoLst;
                                }), "noobj", "an updated todo lst",
                                Map.of(uri(INDEX), "the index of the todo item to remove"), "remove an item from the todo lst")))));
        final Obj todos = Router.readFromSpace(this.getRoot(agent).extend("+"));
        if (!todos.isNoObj()) {
            agent.requireService(SystemService.class).addSystemMessage(
                    """
                    ---[todo_feature]---
                    the following items are still on your todo list:
                    %s
                    """.formatted(String.join("\n", todos.stream()
                            .filter(x -> !x.asRec().at(STATUS).orElse(uri("complete")).equals(uri("complete")))
                            .map(Object::toString).toList())));
        }
        return noobj();
    }
}
