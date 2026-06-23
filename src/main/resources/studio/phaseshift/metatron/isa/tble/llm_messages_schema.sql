-- =========================================================================
-- llm_memory: memory policy / algorithm configuration object
-- One row per agent conversation session.
-- Messages are NOT inlined here — they are stored at llm:llm_memory/{id}/msg/{position}
-- =========================================================================
CREATE TABLE IF NOT EXISTS llm_memory (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    agent_id        VARCHAR(255)  NOT NULL,
    name            VARCHAR(255)  DEFAULT NULL,
    algorithm       JSON          NOT NULL,           -- {"max": 15, ...future algorithms}
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (agent_id, name)
);

-- =========================================================================
-- llm_message_system: SYSTEM messages — behavioral / instruction context for the LLM
-- Typed columns for metatron-native scalars (Str, Int).
-- Nested Rec/Lst fields live in the per-type message tables below.
-- TID: /m/llm/system     (metatron: system::T)
-- =========================================================================
CREATE TABLE IF NOT EXISTS llm_message_system (
    id      INT PRIMARY KEY AUTO_INCREMENT,
    text    TEXT NOT NULL
);

-- =========================================================================
-- llm_message_user: USER messages — single-text or multi-modal content
-- TID: /m/llm/user       (metatron: user::T)
-- Single-text path stored in `text`; multi-modal path stored in `parts` (Lst of content-part Recs as JSON)
-- =========================================================================
CREATE TABLE IF NOT EXISTS llm_message_user (
    id      INT PRIMARY KEY AUTO_INCREMENT,
    name    VARCHAR(255)  DEFAULT NULL,
    text    TEXT,
    parts   JSON          DEFAULT NULL
);

-- =========================================================================
-- llm_message_ai: AI / ASSISTANT messages — model responses with optional tool requests
-- TID: /m/llm/ai          (metatron: ai::T)
-- =========================================================================
CREATE TABLE IF NOT EXISTS llm_message_ai (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(255)  DEFAULT NULL,
    text            TEXT          DEFAULT NULL,
    thinking        INT           DEFAULT NULL,
    tool_requests   JSON          DEFAULT NULL,       -- Lst of tool-request Recs
    attrs           JSON          DEFAULT NULL        -- Rec of provider key-value metadata
);

-- =========================================================================
-- llm_message_tool_result: TOOL_EXECUTION_RESULT messages
-- TID: /m/llm/tool_result  (metatron: tool_result::T)
-- boundary note: metatron NAME token maps to LC4j ToolExecutionResultMessage.toolName()
-- =========================================================================
CREATE TABLE IF NOT EXISTS llm_message_tool_result (
    id          INT PRIMARY KEY AUTO_INCREMENT,
    tool_name   VARCHAR(255) NOT NULL,
    text        TEXT         NOT NULL,
    result_id   VARCHAR(255) DEFAULT NULL
);

-- =========================================================================
-- llm_model: LLM model catalog (unchanged)
-- =========================================================================
CREATE TABLE IF NOT EXISTS llm_model (
    id             INT PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(255)    NOT NULL UNIQUE,
    label          VARCHAR(255)    DEFAULT 'null',
    info           JSON            DEFAULT JSON_OBJECT(),
    features_json  JSON            DEFAULT JSON_ARRAY(),
    metadata_json  JSON            DEFAULT JSON_OBJECT()
);

-- =========================================================================
-- llm_skill: registered skills (unchanged)
-- =========================================================================
CREATE TABLE IF NOT EXISTS llm_skill (
    id             INT PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(255)    NOT NULL UNIQUE,
    description    TEXT            DEFAULT 'null',
    instructions   JSON            DEFAULT JSON_OBJECT(),
    metadata       JSON            DEFAULT JSON_OBJECT()
);

-- =========================================================================
-- llm_tool: tool registration (unchanged)
-- =========================================================================
CREATE TABLE IF NOT EXISTS llm_tool (
    id             INT PRIMARY KEY AUTO_INCREMENT,
    memory_id      INT REFERENCES llm_memory(id) ON DELETE SET NULL,
    mtron_inst     VARCHAR(1024)  NOT NULL UNIQUE,
    name           VARCHAR(255)    NOT NULL,
    description    TEXT            NOT NULL,
    args_schema    JSON            DEFAULT JSON_ARRAY(),
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
