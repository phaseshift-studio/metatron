-- llm_memory: one row per conversation / agent memory session.
-- Uses pointer-Lst model: { mem: Lst<Uri>, max?: Int }
-- The mem field is a JSON array of message URIs (pointers to individually-addressable message rows).
-- Messages are stored independently in their own tables — ordering is by Lst index, not per-message position.

CREATE TABLE IF NOT EXISTS llm_memory (
    id            INT PRIMARY KEY,
    agent_id      VARCHAR(255)  NOT NULL,
    name          VARCHAR(255)  DEFAULT NULL,
    mem           JSON          DEFAULT JSON_ARRAY(),
    max           INT           DEFAULT 15,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (agent_id, name)
);

-- Messages are typed at the metatron level via TID (system::T, user::T, ai::T, tool_result::T).
-- Each message type maps to its own table.  Messages are independent records — they do not carry
-- a memory_id FK; the pointer Lst in llm_memory is the exclusive owner of message ordering.
-- Orphaned messages (evicted from the window but not deleted) are harmless and lazily GC'd.

-- llm_message_system: SYSTEM messages — behavioral / instruction context for the LLM.
-- TID: /m/llm/system     (metatron: system::T)

CREATE TABLE IF NOT EXISTS llm_message_system (
    id          INT  PRIMARY KEY,
    text        TEXT NOT NULL
);

-- llm_message_user: USER messages — supports both single-text and multi-modal content.
-- TID: /m/llm/user       (metatron: user::T)
-- Single-text:   contents => [text => "hello"]
-- Multi-modal:   contents => [[text => "..."], [image => [mime_type => "image/png", url => "..."]]]

CREATE TABLE IF NOT EXISTS llm_message_user (
    id              INT PRIMARY KEY,
    name            VARCHAR(255)  DEFAULT NULL,
    content_json    JSON          NOT NULL   
);

-- llm_message_ai: AI / ASSISTANT messages — model responses with optional tool requests.
-- TID: /m/llm/ai          (metatron: ai::T)

CREATE TABLE IF NOT EXISTS llm_message_ai (
    id              INT PRIMARY KEY,
    name            VARCHAR(255)  DEFAULT NULL,
    text            TEXT          DEFAULT NULL,
    thinking        INT           DEFAULT NULL,
    tool_requests   JSON          DEFAULT JSON_ARRAY(),
    attrs           JSON          DEFAULT JSON_OBJECT()
);

-- llm_message_tool_result: TOOL_EXECUTION_RESULT messages — results of tool invocations.
-- TID: /m/llm/tool_result  (metatron: tool_result::T)
-- boundary note: metatron NAME token maps to LC4j ToolExecutionResultMessage.toolName()

CREATE TABLE IF NOT EXISTS llm_message_tool_result (
    id      INT PRIMARY KEY,
    name    VARCHAR(255)  NOT NULL,  
    text    TEXT          NOT NULL   
);


-- llm_model: LLM model catalog (name -> metadata).

CREATE TABLE IF NOT EXISTS llm_model (
    id             INT PRIMARY KEY,
    name           VARCHAR(255)    NOT NULL UNIQUE,
    label          VARCHAR(255)    DEFAULT 'null',
    info           JSON            DEFAULT JSON_OBJECT(),
    features_json  JSON            DEFAULT JSON_ARRAY(),
    metadata_json  JSON            DEFAULT JSON_OBJECT()
);


-- llm_skill: registered skills / tools available to models.

CREATE TABLE IF NOT EXISTS llm_skill (
    id             INT PRIMARY KEY,
    name           VARCHAR(255)    NOT NULL UNIQUE,
    description    TEXT            DEFAULT 'null',
    instructions   JSON            DEFAULT JSON_OBJECT(),
    metadata       JSON            DEFAULT JSON_OBJECT()
);


-- llm_tool: tool registration — mtron instruction mapped to a LangChain4j ToolSpecification.

CREATE TABLE IF NOT EXISTS llm_tool (
    id             INT PRIMARY KEY,
    memory_id      INT REFERENCES llm_memory(id) ON DELETE SET NULL,
    mtron_inst     VARCHAR(1024)  NOT NULL UNIQUE,
    name           VARCHAR(255)    NOT NULL,
    description    TEXT            NOT NULL,
    args_schema    JSON            DEFAULT JSON_ARRAY(),
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
