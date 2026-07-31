package studio.phaseshift.metatron.docs;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pre-processes adoc files to extract skill metadata based on lean semantic tags.
 */
public final class SkillPreProcessor {

    private static final Pattern TRIGGER = Pattern.compile("\\[\\.skill-trigger(?:,\\s*|\\s+)\\.capability=\"([^\"]+)\"\\]");
    private static final Pattern TRIAGE = Pattern.compile("\\[\\.skill\\s+\\.triage\\]");
    private static final Pattern SKILL_TAG = Pattern.compile("^<([0-9]+)>\\s*\\[\\.skill(\\s+[^]]+)\\]\\s*(.*)$", Pattern.MULTILINE);
    private static final Pattern ATTR = Pattern.compile("\\.([a-z]+)=([^\\s\\]]+)");
    private static final Pattern CODE_CALLOUT = Pattern.compile("\\[--\\s*<([0-9]+)>\\s*--]");

    public static class SkillCapability {
        public String id;
        public String intent;
        public List<String> executions = new ArrayList<>();
        public List<String> verifications = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public List<String> triageSteps = new ArrayList<>();
    }

    public static class SkillRegistry {
        public String capabilityName;
        public Map<String, SkillCapability> entries = new HashMap<>();
        public Map<String, String> currentBlockCallouts = new HashMap<>();
    }

    public SkillRegistry scan(final String adocText) {
        final SkillRegistry registry = new SkillRegistry();
        
        Matcher tm = TRIGGER.matcher(adocText);
        if (tm.find()) {
            registry.capabilityName = tm.group(1);
        }

        String[] lines = adocText.split("\\R");
        boolean inBlock = false;
        StringBuilder blockBody = new StringBuilder();

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();

            if (!inBlock && trimmed.startsWith("[mtron]")) {
                inBlock = true;
                blockBody = new StringBuilder();
                continue;
            }
            
            if (inBlock) {
                if (trimmed.equals("----")) {
                    mapCallouts(blockBody.toString(), registry);
                    inBlock = false;
                } else {
                    blockBody.append(rawLine).append("\n");
                }
                continue;
            }

            if (trimmed.contains("[.skill .triage]")) {
                String step = trimmed.replace("[.skill .triage]", "").trim();
                String capId = registry.capabilityName != null ? registry.capabilityName : "global";
                registry.entries.computeIfAbsent(capId, k -> new SkillCapability()).triageSteps.add(step);
            }

            Matcher sm = SKILL_TAG.matcher(trimmed);
            if (sm.find()) {
                String calloutId = sm.group(1);
                String attrsRaw = sm.group(2);
                String text = sm.group(3).trim();

                Map<String, String> attributes = new HashMap<>();
                Matcher am = ATTR.matcher(attrsRaw); // Error here in previous version, should be attrsRaw
                while (am.find()) {
                    attributes.put(am.group(1), am.group(2));
                }

                String to = attributes.get("to");
                String ref = attributes.get("ref");
                String id = (to != null) ? to : ref;
                if (id == null) continue;

                SkillCapability cap = registry.entries.computeIfAbsent(id, k -> {
                    SkillCapability s = new SkillCapability();
                    s.id = id;
                    return s;
                });

                if (attrsRaw.contains(".pattern")) {
                    cap.intent = text;
                    String code = registry.currentBlockCallouts.get(calloutId);
                    if (code != null) cap.executions.add(code);
                } else if (attrsRaw.contains(".verify")) {
                    cap.verifications.add(text);
                }
                if (attrsRaw.contains(".warn")) {
                    cap.warnings.add(text);
                }
            }
        }
        return registry;
    }

    private void mapCallouts(String body, SkillRegistry registry) {
        registry.currentBlockCallouts.clear();
        for (String line : body.split("\\R")) {
            Matcher cm = CODE_CALLOUT.matcher(line);
            if (cm.find()) {
                String calloutId = cm.group(1);
                String cleanCode = line.replaceAll("\\[--\\s*<[0-9]+>\\s*--]", "").trim();
                registry.currentBlockCallouts.put(calloutId, cleanCode);
            }
        }
    }

    public String project(final String adocText, final SkillRegistry registry) {
        if (registry == null || registry.capabilityName == null) return "# No skill defined";
        StringBuilder sb = new StringBuilder();
        sb.append("---\ntitle: ").append(registry.capabilityName).append("\ncategory: agent-skill\ndomain: relational-db\n---\n\n");
        sb.append("# Skill: ").append(registry.capabilityName).append("\n\n");

        SkillCapability global = registry.entries.get(registry.capabilityName);
        if (global != null && !global.triageSteps.isEmpty()) {
            sb.append("## Triage & Pre-flight\n");
            for (String step : global.triageSteps) sb.append("- ").append(step).append("\n");
            sb.append("\n");
        }

        sb.append("## Capabilities Matrix\n\n");
        List<String> sortedIds = new ArrayList<>(registry.entries.keySet());
        Collections.sort(sortedIds);

        for (String id : sortedIds) {
            SkillCapability cap = registry.entries.get(id);
            if (cap.id == null || cap.id.equals(registry.capabilityName)) continue;
            sb.append("### ").append(cap.id).append("\n");
            sb.append("**Intent**: ").append(cap.intent != null ? cap.intent : "N/A").append("\n\n");

            if (!cap.executions.isEmpty()) {
                sb.append("#### Execution Patterns\n");
                for (String code : cap.executions) sb.append("```mtron\n").append(code).append("\n```\n");
                sb.append("\n");
            }

            if (!cap.verifications.isEmpty()) {
                sb.append("#### Verification\n");
                for (String v : cap.verifications) sb.append("- ").append(v).append("\n");
                sb.append("\n");
            }

            if (!cap.warnings.isEmpty()) {
                sb.append("> [!CAUTION]\n");
                for (String w : cap.warnings) sb.append(w).append("\n");
                sb.append("\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }
}
