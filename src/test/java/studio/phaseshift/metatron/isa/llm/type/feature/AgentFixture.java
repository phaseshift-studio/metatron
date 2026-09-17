package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.DESC;
import static studio.phaseshift.metatron.Tokens.FEATURE;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.ROOT;
import static studio.phaseshift.metatron.Tokens.SESSION;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_SERVICE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SYSTEM_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SYSTEM_SERVICE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_SERVICE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_WINDOW_MESSAGE_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Test fixture that assembles an {@link Agent} from features, auto-resolving the
 * {@code requires()} closure for the config-less gateways (skill -> tool -> ...).
 *
 * <p>Features that need configuration (chat/message/concept/...) must be supplied
 * explicitly; a dangling require on one of them is a construction error with a clear
 * message rather than a hand-built rec that happens to pass.
 */
public final class AgentFixture {

    private AgentFixture() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "test-agent";
        private fURI root = f("/usr/test/agent");
        private final List<AbstractFeature> features = new ArrayList<>();

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder desc(final String desc) {
            this.desc = desc;
            return this;
        }

        public Builder root(final fURI root) {
            this.root = root;
            return this;
        }

        public Builder feature(final AbstractFeature... fs) {
            for (final AbstractFeature f : fs)
                if (!f.isNoObj())
                    this.features.add(f);
            return this;
        }

        /**
         * Attach a window message feature rooted at the given session — the config-less default
         * for session/frame-dependent features under test.
         */
        public Builder session(final fURI session) {
            return this.feature(new WindowMessageFeature(
                    mutableMap(uri(SESSION), uri(session)), LLM_WINDOW_MESSAGE_FEATURE_TID, null));
        }

        public Agent build() {
            final List<Obj> attached = new ArrayList<>(this.features);
            final Deque<AbstractFeature> queue = new ArrayDeque<>(this.features);
            while (!queue.isEmpty()) {
                final AbstractFeature f = queue.poll();
                for (final fURI required : f.requires()) {
                    if (hasService(required, attached))
                        continue;
                    final AbstractFeature gateway = gatewayFor(required);
                    attached.add(gateway);
                    queue.add(gateway); // resolve the gateway's own requires() transitively
                }
            }
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            map.put(uri(NAME), str(this.name));
            if (null != this.desc)
                map.put(uri(DESC), str(this.desc));
            map.put(uri(ROOT), uri(this.root.toString()));
            map.put(uri(FEATURE), lst(attached));
            return Agent.agent(rec(map, LLM_AGENT_TID, null));
        }

        private String desc;

        private static boolean hasService(final fURI serviceTid, final List<Obj> features) {
            for (final Obj f : features)
                if (f instanceof Feature feature && feature.offers().contains(serviceTid))
                    return true;
            return false;
        }

        /**
         * The gateways that can be default-constructed; anything requiring config must be
         * supplied by the caller.  Maps a service tid to the feature that provides it.
         */
        private static AbstractFeature gatewayFor(final fURI serviceTid) {
            if (serviceTid.equals(LLM_SKILL_SERVICE_TID))
                return new SkillFeature(new LinkedHashMap<>(), LLM_SKILL_FEATURE_TID, null);
            if (serviceTid.equals(LLM_TOOL_SERVICE_TID))
                return new ToolFeature(new LinkedHashMap<>(), LLM_TOOL_FEATURE_TID, null);
            if (serviceTid.equals(LLM_SYSTEM_SERVICE_TID))
                return new SystemFeature(new LinkedHashMap<>(), LLM_SYSTEM_FEATURE_TID, null);
            throw MTronException.of("no config-less gateway for required service (supply it explicitly): %s", serviceTid);
        }
    }
}
