---
name: llm instruction set
description: >
  The `/m/llm` instruction set — models, agents, features, skills and tools, and the Model Context Protocol on
  both sides of the wire: `mcp_client::T` over streamable-http, websockets or stdio, and `mcp_server::T` carried
  by http, ws or the process's own stdio. TRIGGER: When connecting an agent to an MCP server, exposing mtron
  instructions to an MCP client, choosing a transport (streamable-http + sse, ws, stdio), wiring tools into a
  `tool_feature`, or asking how a tool gets its name.
---

# llm instruction set (`/m/llm`)

Three ideas carry the package.

**An agent is a rec, not a class.** `agent::T` holds a `model` and a bag of `feature`s; a model is a provider
(ollama, openai, …) plus a model name; a feature is a stage spliced into the chat loop (`tool_feature`,
`system_feature`, `cost_feature`, `compaction_feature`, …). `.chat()` runs the loop and `>>message` is the ledger
it wrote.

**A tool is an instruction.** There is no tool registry to populate: anything callable *is* a tool, and `mTool`
derives both the JSON schema and the name from the instruction's tid. That one rule is why a `tool_feature` can
be handed an instruction (`!*bash`), a skill, an MCP client or an `mcp_server` — one vocabulary throughout — and
why the same objs can be turned around and exposed outward as a server.

**MCP is a projection, not a port.** `mcp_server::T` is transport-agnostic: it speaks JSON-RPC 2.0 over its own
`tool` / `resource` / `prompt` recs and knows nothing about sockets. A *carrier* wraps it — `mcp_http`, `mcp_ws`,
`mcp_stdio` — and owns exactly the one thing the server does not: how bytes arrive. The other end is
`mcp_client::T`, one connection to any server over any of those transports.

## the space these examples use

The examples that *compile* (a `mtron_pre` block is executed by the docs pipeline and inlined with its results;
a plain `mtron` block is only shown) need no server: they define a server, select its tools, and read what came
out. The examples that *talk to a carrier* — a client reaching a mounted server — are shown, and they address the
live carrier the way a boot file does, at `http://localhost:8777` and `ws://localhost:8555`.

```mtron
mtron> import(/m/llm)
==>instset::[pattern=>/m/llm/#,q=>[docq::[pattern=>docq,pre_read=>inst?rng=#{*}&dom=#{?}(uri::T){<j>},pre_write=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>},obj=>memspace::[pattern=><#>],inst=>instset::[pattern=><#>]]],space=>[super=>!*/m],type=>[rec::T[?[{?}provider=>uri::T,host=>uri::T,protocol=>uri::T,llm=>uri::T,{?}api_key=>str::T,{?}timeout=>!*/m/math/time,{?}size=>!*/m/math/datasize,{?}quant=>int::T,{?}context=>int::T,{?}cost=>{?}[in=>!*/m/math/currency,out=>!*/m/math/currency]]][ctor?rng=model&dom=#{?}(<#{*}>::T){<j>}]@model,rec::T[?[inst=><#>::T,name=>uri::T,desc=>str::T,{?}arg=><#>::T]]@tool,rec::T[?[agent=>uri{+}::T,user=>uri{+}::T,algorithm=>rec::T]]@session,rec::T[?[session=>uri::T,index=>int::T,{?}prev=>uri::T,{?}next=>uri::T,{?}message=>lst::T,time=>!*/m/math/time]]@iteration,rec{*}::T[?[name=>str::T,{?}concept=>{?}[concept{*}::T],{?}message=>{?}[message{*}::T]]]@concept,rec::T[?[text=>str::T,{?}status=>isa(union(open,run,block,close)).else(open),{?}time=>isa(uri::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@datetime).else(datetime_now()),{?}concept=>[rec{*}::T[?[name=>str::T,{?}concept=>{?}[concept{*}::T],{?}message=>{?}[message{*}::T]]]@concept],{?}message=>[message{*}::T],{?}reference=>lst::T]]@todo,rec::T[?[{?}in=>isa(int::T).else(0),{?}out=>isa(int::T).else(0),{?}max=>int::T,{?}est=>{?}[{?}user=>int::T,{?}ai=>int::T,{?}tool=>int::T,{?}system=>int::T]]][ctor?rng=token_counter_tool&dom=#{?}(<#{*}>::T){<j>}]@token_counter_tool,rec::T[?[text=>str::T,kind=>union(decision,problem,solution,observation),{?}source=>[<#{?}>::T],{?}concept=>[<#{?}>::T],{?}tier=>isa(int::T[is(gt(0))]@nat).else(1)]]@claim,rec::T[?[title=>str::T,desc=>str::T,status=>union(open,in_progress,resolved,abandoned),{?}source=>[<#{?}>::T],{?}claim=>[<#{?}>::T],{?}time=>uri::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@datetime]]@loose_end,message::T[?[text=>str::T]]@system,rec::T[?[tag=>str::T,key=>str::T,{?}body=>str::T,{?}obj=><#>::T,{?}error=>fail::T,{?}index=>int::T,{?}stage=>uri::T]]@watermark,rec::T[?[{?}chat=><#>::T,time=>uri::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@datetime,runtime=>!*/m/math/time,{?}watermark=>[rec::T[?[tag=>str::T,key=>str::T,{?}body=>str::T,{?}obj=><#>::T,{?}error=>fail::T,{?}index=>int::T,{?}stage=>uri::T]]@watermark],{?}error=>fail::T]]@chat_result,message::T[?[{?}name=>str::T,{?}text=>str::T,{?}contents=><#{*}>::T]]@user,message::T[?[name=>uri::T,{?}args=>str::T,text=>str::T,{?}contents=>str::T]]@tool_request,message::T[?[{?}text=>str::T,{?}tool_requests=>[message::T[?[name=>uri::T,{?}args=>str::T,text=>str::T,{?}contents=>str::T]]@tool_request]]]@ai,message::T[?[name=>uri::T,text=>str::T,{?}chat=><#>::T,{?}id=>str::T]]@tool_result,message::T[?[text=>str::T]]@thinking,message::T[?[text=>str::T,{?}in=>int::T,{?}out=>int::T,{?}compression=>real::T]]@compaction,rec::T[?[{?}session=>uri::T]]@message,rec::T[?[name=>uri::T,desc=>str::T,{?}content=>str::T,{?}resource=>[[uri=>uri::T,{?}name=>str::T,{?}desc=>str::T,text=>str::T]],{?}tool=>[<#>::T]]]@skill,rec::T[?[{?}root=>uri::T,{?}to=><#>::T,{?}on_agent_ctor=><#>::T,{?}on_before_chat=><#>::T,{?}on_partial_response=><#>::T,{?}on_partial_thinking=><#>::T,{?}on_partial_tool_call=><#>::T,{?}before_tool_execution=><#>::T,{?}on_tool_executed=><#>::T,{?}on_tool_result=><#>::T,{?}on_complete_response=><#>::T,{?}on_error=><#>::T]]@feature,rec::T[?[name=>str::T,{?}desc=>str::T,{?}feature=>lst::T]][ctor?rng=agent&dom=#{?}(<#{*}>::T){<j>}]@agent,feature::T[?[model=>rec::T[?[{?}provider=>uri::T,host=>uri::T,protocol=>uri::T,llm=>uri::T,{?}api_key=>str::T,{?}timeout=>!*/m/math/time,{?}size=>!*/m/math/datasize,{?}quant=>int::T,{?}context=>int::T,{?}cost=>{?}[in=>!*/m/math/currency,out=>!*/m/math/currency]]][ctor?rng=model&dom=#{?}(<#{*}>::T){<j>}]@model,{?}response=>{?}[{?}to=><#>::T,{?}complete=><#>::T],{?}format=><#>::T]][ctor?rng=chat_feature&dom=#{?}(<#{*}>::T){<j>}]@chat_feature,feature::T[][ctor?rng=summarize_feature&dom=#{?}(<#{*}>::T){<j>}]@summarize_feature,feature::T[?[session=>uri::T]][ctor?rng=token_message_feature&dom=#{?}(<#{*}>::T){<j>}]@token_message_feature,feature::T[?[session=>uri::T]][ctor?rng=window_message_feature&dom=#{?}(<#{*}>::T){<j>}]@window_message_feature,feature::T[][ctor?rng=persisted_frame_feature&dom=#{?}(<#{*}>::T){<j>}]@persisted_frame_feature,feature::T[][ctor?rng=transient_frame_feature&dom=#{?}(<#{*}>::T){<j>}]@transient_frame_feature,feature::T[?[{?}tool=>lst{?}::T,{?}max=>isa(int::T).else(-1)]][ctor?rng=tool_feature&dom=#{?}(<#{*}>::T){<j>}]@tool_feature,feature::T[?[{?}model=>rec::T[?[{?}provider=>uri::T,host=>uri::T,protocol=>uri::T,llm=>uri::T,{?}api_key=>str::T,{?}timeout=>!*/m/math/time,{?}size=>!*/m/math/datasize,{?}quant=>int::T,{?}context=>int::T,{?}cost=>{?}[in=>!*/m/math/currency,out=>!*/m/math/currency]]][ctor?rng=model&dom=#{?}(<#{*}>::T){<j>}]@model]][ctor?rng=embed_feature&dom=#{?}(<#{*}>::T){<j>}]@embed_feature,feature::T[][ctor?rng=skill_feature&dom=#{?}(<#{*}>::T){<j>}]@skill_feature,feature::T[][ctor?rng=system_feature&dom=#{?}(<#{*}>::T){<j>}]@system_feature,feature::T[][ctor?rng=think_feature&dom=#{?}(<#{*}>::T){<j>}]@think_feature,feature::T[][ctor?rng=midchat_feature&dom=#{?}(<#{*}>::T){<j>}]@midchat_feature,feature::T[][ctor?rng=tagging_concept_feature&dom=#{?}(<#{*}>::T){<j>}]@tagging_concept_feature,feature::T[][ctor?rng=agent_concept_feature&dom=#{?}(<#{*}>::T){<j>}]@agent_concept_feature,feature::T[][ctor?rng=lucene_concept_feature&dom=#{?}(<#{*}>::T){<j>}]@lucene_concept_feature,feature::T[?[rate=>[in=>!*/m/math/currency,out=>!*/m/math/currency]]][ctor?rng=cost_feature&dom=#{?}(<#{*}>::T){<j>}]@cost_feature,feature::T[?[{?}stage=>{?}[union(on_agent_ctor,on_before_chat,on_partial_response,on_partial_thinking,on_partial_tool_call,on_tool_executed,on_tool_result,on_complete_response,on_error)=>lst::T]]][ctor?rng=lambda_feature&dom=#{?}(<#{*}>::T){<j>}]@lambda_feature,feature::T[][ctor?rng=todo_feature&dom=#{?}(<#{*}>::T){<j>}]@todo_feature,feature::T[?[{?}model=>rec::T[?[{?}provider=>uri::T,host=>uri::T,protocol=>uri::T,llm=>uri::T,{?}api_key=>str::T,{?}timeout=>!*/m/math/time,{?}size=>!*/m/math/datasize,{?}quant=>int::T,{?}context=>int::T,{?}cost=>{?}[in=>!*/m/math/currency,out=>!*/m/math/currency]]][ctor?rng=model&dom=#{?}(<#{*}>::T){<j>}]@model,{?}threshold=>real::T,{?}context=>int::T]][ctor?rng=compaction_feature&dom=#{?}(<#{*}>::T){<j>}]@compaction_feature,feature::T[][ctor?rng=audit_feature&dom=#{?}(<#{*}>::T){<j>}]@audit_feature,feature::T[?[{?}max_loop=>int::T,{?}max_time=>real::T@time,{?}delay=>real::T@time,{?}preserve=>lst::T]][ctor?rng=loop_feature&dom=#{?}(<#{*}>::T){<j>}]@loop_feature,session_or_agent::T[?union(rec::T[?[name=>str::T,{?}desc=>str::T,{?}feature=>lst::T]][ctor?rng=agent&dom=#{?}(<#{*}>::T){<j>}]@agent,rec::T[?[agent=>uri{+}::T,user=>uri{+}::T,algorithm=>rec::T]]@session)],mcp_server::T[?[{?}tool=>{?}[uri::T=>inst::T],{?}resource=><#>::T,{?}prompt=><#>::T]][ctor?rng=mcp_message&dom=#{?}(rec::T){<j>}]@mcp_message],inst=>[as?rng=model&dom=rec(rec::T[?[{?}provider=>uri::T,host=>uri::T,protocol=>uri::T,llm=>uri::T,{?}api_key=>str::T,{?}timeout=>!*/m/math/time,{?}size=>!*/m/math/datasize,{?}quant=>int::T,{?}context=>int::T,{?}cost=>{?}[in=>!*/m/math/currency,out=>!*/m/math/currency]]][ctor?rng=model&dom=#{?}(<#{*}>::T){<j>}]@model){<j>},as?rng=tool&dom=docs(rec::T[?[inst=><#>::T,name=>uri::T,desc=>str::T,{?}arg=><#>::T]]@tool){<j>},as?rng=tool&dom=inst(rec::T[?[inst=><#>::T,name=>uri::T,desc=>str::T,{?}arg=><#>::T]]@tool){<j>},as?rng=skill&dom=uri(rec::T[?[name=>uri::T,desc=>str::T,{?}content=>str::T,{?}resource=>[[uri=>uri::T,{?}name=>str::T,{?}desc=>str::T,text=>str::T]],{?}tool=>[<#>::T]]]@skill){<j>},as?rng=skill&dom=agent(rec::T[?[name=>uri::T,desc=>str::T,{?}content=>str::T,{?}resource=>[[uri=>uri::T,{?}name=>str::T,{?}desc=>str::T,text=>str::T]],{?}tool=>[<#>::T]]]@skill){<j>},chat?rng=chat_result&dom=agent(str::T){<j>},embed?rng=/m/vec/vec&dom=model(<#>::T){<j>},interrupt?rng=noobj{0}&dom=agent(){<j>},sweep?rng=rec&dom=session_or_agent{?}({?}session_or_agent=>session_or_agent{?}::T,{?}repair=>bool::T,{?}prune=>bool::T){<j>},chat?rng=chat_result&dom=agent(str::T,rec::T){<j>},summary?rng=rec&dom=session_or_agent{?}({?}session_or_agent=>session_or_agent{?}::T,{?}model=>choose([isa(rec::T[?[{?}provider=>uri::T,host=>uri::T,protocol=>uri::T,llm=>uri::T,{?}api_key=>str::T,{?}timeout=>!*/m/math/time,{?}size=>!*/m/math/datasize,{?}quant=>int::T,{?}context=>int::T,{?}cost=>{?}[in=>!*/m/math/currency,out=>!*/m/math/currency]]][ctor?rng=model&dom=#{?}(<#{*}>::T){<j>}]@model)=>id(),isa(rec::T[?[agent=>uri{+}::T,user=>uri{+}::T,algorithm=>rec::T]]@session)=>*rshift(agent).mult(model)]).rshift(),{?}scope=>union(time::T,datetime::T),{?}kind=>lst::T,{?}concept=>lst::T){<j>},compact?rng=rec&dom=session_or_agent{?}({?}session_or_agent=>session_or_agent{?}::T,{?}model=>model::T,{?}prompt=>str::T){<j>}]]@/m/llm
mtron> import(/m/web,web)
==>instset::[pattern=>/m/web/#,q=>[docq::[pattern=>docq,pre_read=>inst?rng=#{*}&dom=#{?}(uri::T){<j>},pre_write=>inst?rng=#{*}&dom=#{?}(uri::T,<#>::T){<j>},obj=>memspace::[pattern=><#>],inst=>instset::[pattern=><#>]]],space=>[super=>!*/m],const=>[[remote_console=>inst(){<j>}]@helper,obj_xml::[=>]@obj_xml,obj_html::[=>]@obj_html,obj_json::[density=>OPAQUE,wrap_uri=>true,bias_towards_uri=>true,bias_towards_objs=>false]@obj_json,obj_markdown::[=>]@obj_markdown,obj_text::[=>]@obj_text,/m/mach/io/serializer/mtron::[clip=>[str=>35,rec=>7,lst=>7]]@clean,bytebuffer::[=>]@bytebuffer,obj_json::[density=><TRANSPARENT>,wrap_uri=>true,bias_towards_uri=>true,bias_towards_objs=>false]@obj_json,obj_bson::[=>]@obj_bson,obj_xsv::[delimiter=>',',header=>false]@obj_xsv],type=>[uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime,str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@xml,str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@html,str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@json,str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@yaml,str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@xsv,xsv::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@csv,str::T@css,str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@markdown,str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@java,serializer::T,serializer::T[?[clip=>[{?}/m/rec=>isa(int::T),{?}/m/lst=>isa(int::T),{?}/m/str=>isa(int::T),{?}/m/uri=>isa(int::T),{?}/m/real=>isa(int::T),{?}/m/bytes=>isa(int::T),{?}/m/fail=>isa(int::T)],{?}pointer=>isa(str::T),{?}pager=>isa(bool::T)]][ctor?rng=obj_mtron&dom=#{?}(obj_mtron::T){<j>}]@obj_mtron,serializer::T[?[{?}wrap_uri=>isa(bool::T),{?}bias_towards_uri=>isa(bool::T),{?}bias_towards_objs=>isa(bool::T)]][ctor?rng=obj_simple_json&dom=#{?}(obj_simple_json::T){<j>}]@obj_simple_json,serializer::T@obj_bson,serializer::T[][ctor?rng=obj_yaml&dom=#{?}(obj_yaml::T){<j>}]@obj_yaml,serializer::T[?[{?}pretty=>bool::T,{?}indent=>2]][ctor?rng=obj_bytebuffer&dom=#{?}(obj_bytebuffer::T){<j>}]@obj_bytebuffer,serializer::T[?[{?}delimiter=>isa(str::T),{?}header=>isa(bool::T)]][ctor?rng=obj_xsv&dom=#{?}(obj_xsv::T){<j>}]@obj_xsv,space::T[][ctor?rng=httpspace&dom=#{?}(<#{*}>::T){<j>}]@httpspace,space::T[?[host=>uri::T]][ctor?rng=wsspace&dom=#{?}(wsspace::T){<j>}]@wsspace,rec::T[?[{?}in=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime),{?}out=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime),{?}send=>inst::T,{?}send_recv=>inst::T,{?}on_open=><#>::T,{?}on_error=><#>::T,{?}on_message=><#>::T,{?}on_close=><#>::T]]@web_socket,web_socket::T[][ctor?rng=web_socket&dom=#{?}(rec::T){<j>}]@ws_handler,web_socket::T[][ctor?rng=web_socket&dom=#{?}(rec::T){<j>}]@ws_client,mcp::T[?[{?}tool=>{?}[uri::T=>inst::T],{?}resource=><#>::T,{?}prompt=><#>::T]][ctor?rng=mcp_ws&dom=#{?}(rec::T){<j>}]@mcp_ws,mcp_ws::T[?[{?}tool=>[uri::T=>inst::T],{?}resource=><#>::T,{?}prompt=><#>::T]][ctor?rng=mcp_emulator_ws&dom=#{?}(rec::T){<j>}]@mcp_emulator_ws,mtron::T[?[{?}in=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime).else(application/x-mtron),{?}out=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime).else(application/x-mtron)]][ctor?rng=mtron_ws&dom=#{?}(<#{*}>::T){<j>}]@mtron_ws,rec::T[?[{?}in=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime),{?}out=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime),{?}send=>inst::T,{?}on_get=><#>::T,{?}on_post=><#>::T,{?}on_put=><#>::T,{?}on_delete=><#>::T,{?}on_patch=><#>::T,{?}on_head=><#>::T,{?}on_options=><#>::T,{?}on_error=><#>::T,{?}on_close=><#>::T]]@http_socket,http_socket::T[][ctor?rng=http_handler&dom=#{?}(<#{*}>::T){<j>}]@http_handler,http_socket::T[][ctor?rng=http_client&dom=#{?}(<#{*}>::T){<j>}]@http_client,mcp_http::T[?[{?}tool=>[uri::T=>inst::T],{?}resource=><#>::T,{?}prompt=><#>::T]][ctor?rng=mcp_emulator_http&dom=#{?}(rec::T){<j>}]@mcp_emulator_http,mtron::T[?[{?}in=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime).else(application/x-mtron),{?}out=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime).else(application/x-mtron)]][ctor?rng=mtron_http&dom=#{?}(rec::T){<j>}]@mtron_http,mcp::T[?[{?}tool=>{?}[uri::T=>inst::T],{?}resource=><#>::T,{?}prompt=><#>::T]][ctor?rng=mcp_http&dom=#{?}(rec::T){<j>}]@mcp_http,mcp::T[?[server=>mcp_server::T,{?}in=>uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime,{?}out=>uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime,{?}status=>bool::T]][ctor?rng=mcp_stdio&dom=#{?}(rec::T){<j>}]@mcp_stdio,rest::T[?[{?}in=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime).else(application/x-mtron),{?}out=>isa(uri::T[?union(application/bson,application/json,application/ld+json,media/,media/mpeg,application/octet-stream,application/atom+xml,application/xml,application/x-mtron,application/yaml,application/javascript,text/html,text/plain,text/css,text/markdown,text/javascript,text/x-shellscript,text/x-java,text/x-python,text/event-stream,image/png,image/jpeg,image/gif,image/svg+xml,image/x-icon,application/xhtml+xml)]@mime).else(application/x-mtron),{?}web_root=><#>::T,{?}default_page=><#>::T,{?}read_only=><#>::T]][ctor?rng=web_http&dom=#{?}(rec::T){<j>}]@web_http,mcp_server::T[?[{?}tool=>{?}[uri::T=>inst::T],{?}resource=><#>::T,{?}prompt=><#>::T]][ctor?rng=mcp_mtron&dom=#{?}(rec::T){<j>}]@mcp_mtron,mcp::T[?[{?}tool=>{?}[uri::T=>inst::T],{?}resource=><#>::T,{?}prompt=><#>::T]][ctor?rng=mcp_server&dom=#{?}(rec::T){<j>}]@mcp_server,rec::T@route,protocol::T@http,protocol::T@ws,protocol::T@mcp,http::T@rest,protocol::T@mtron,protocol::T@stream,stream::T@sse,rec::T[?union(protocol::T@http,protocol::T@ws,protocol::T@mcp,http::T@rest,protocol::T@mtron,protocol::T@stream)]@protocol,rec::T[?[{?}host=>uri::T,{?}transport=>uri::T,{?}command=>lst::T,{?}env=>[uri::T=><#>::T],{?}tool=>[uri::T=>rec::T[?[inst=><#>::T,name=>uri::T,desc=>str::T,{?}arg=><#>::T]]@tool],{?}status=>bool::T]][ctor?rng=mcp_client&dom=#{?}(rec::T){<j>}]@mcp_client],inst=>[ping?rng=time&dom=#{?}(uri::T){<j>},format?rng=str&dom=markdown(){<j>},as?rng=skill&dom=markdown(str::T){<j>},as?rng=rec&dom=json(rec::T){<j>},as?rng=rec&dom=yaml(rec::T){<j>},as?rng=json&dom=rec(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@json){<j>},as?rng=rec&dom=xml(rec::T){<j>},as?rng=xml&dom=rec(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@xml){<j>},as?rng=rec&dom=html(rec::T){<j>},as?rng=html&dom=rec(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@html){<j>},as?rng=rec&dom=markdown(rec::T){<j>},as?rng=html&dom=markdown(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@html){<j>},as?rng=markdown&dom=html(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@markdown){<j>},as?rng=markdown&dom=rec(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@markdown){<j>},as?rng=rec&dom=java(rec::T){<j>},as?rng=java&dom=rec(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@java){<j>},as?rng=java&dom=str(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@java){<j>},as?rng=xsv&dom=str(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@xsv){<j>},as?rng=csv&dom=str(xsv::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@csv){<j>},as?rng=json&dom=mcp_client(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@json){<j>},as?rng=mcp_client{+}&dom=json(rec::T[?[{?}host=>uri::T,{?}transport=>uri::T,{?}command=>lst::T,{?}env=>[uri::T=><#>::T],{?}tool=>[uri::T=>rec::T[?[inst=><#>::T,name=>uri::T,desc=>str::T,{?}arg=><#>::T]]@tool],{?}status=>bool::T]][ctor?rng=mcp_client&dom=#{?}(rec::T){<j>}]@mcp_client){<j>},as?rng=mcp_server&dom=skill(mcp::T[?[{?}tool=>{?}[uri::T=>inst::T],{?}resource=><#>::T,{?}prompt=><#>::T]][ctor?rng=mcp_server&dom=#{?}(rec::T){<j>}]@mcp_server){<j>}]]@/m/web
mtron> [-- a server is a selection over instructions; `!*eval` is dereferenced as this is constructed --]
mtron> mcp_server::[tool => [!*eval]]@/sys/space/mcp/basic_server
==>mcp_server::[tool=>[m_inst_eval=>eval?rng=#{*}&dom=#{?}(<#>::T){<j>}]]@/sys/space/mcp/basic_server
```
What the shown blocks assume is a route table like this one — the same mounts the live profile uses, one route
per server, `mcp_mtron` for the metatron-native tools, `mcp_message` for the ledger, and `/basic` for the server
just defined:

```mtron
[-- a carrier is a space; a route binds a server to it. `else` makes a mount idempotent --]
*/sys/space/web/http.else(httpspace::[pattern=> http://#, host => http://localhost:8777, route => [/mcp => mcp_mtron, /message => mcp_message, /basic => !*</sys/space/mcp/basic_server>, / => /usr]]@/sys/space/web/http)
*/sys/space/web/ws.else(wsspace::[pattern=> ws:#, host => ws://localhost:8555, route => [/mcp => mcp_mtron, /message => mcp_message]]@/sys/space/web/ws)
```

## types

| type             | vid                     | meaning                                                                |
|------------------|-------------------------|------------------------------------------------------------------------|
| `agent::T`       | `/m/llm/agent`          | a model + features; `.chat()` runs the loop                            |
| `model::T`       | `/m/llm/model`          | a provider, a protocol, a host, an llm name                            |
| `feature::T`     | `/m/llm/feature`        | a stage in the chat loop (`tool_feature`, `chat_feature`, …)           |
| `tool::T`        | `/m/llm/tool`           | an instruction with a name, a description and an argument schema       |
| `skill::T`       | `/m/llm/skill`          | a directory of markdown: front matter, instructions, tools, resources  |
| `message::T`     | `/m/llm/message`        | one ledger record — `user`, `ai`, `system`, `thinking`, `tool_result`  |
| `mcp_client::T`  | `/m/web/mcp/mcp_client` | a connection to an MCP server                                          |
| `mcp_message::T` | `/m/llm/mcp/mcp_message`| the message-ledger server                                              |
| `mcp_server::T`  | `/m/web/mcp/mcp_server` | a transport-agnostic MCP server                                        |

`mcp_client::T` is *declared* by `/m/web` — transport surfaces are its business — and *implemented* by
`studio.phaseshift.metatron.isa.llm.type.mcp.mcpClient`, which is why this document, the llm one, is where a
client is documented.

## Model Context Protocol

MCP is JSON-RPC 2.0 over `tool` / `resource` / `prompt`, plus a handshake. metatron splits it in three:
`mcp_server::T` is the protocol, a carrier is the bytes, `mcp_client::T` is the other end. One server obj rides
all three carriers unchanged — mount it on http and a browser-driven client reaches it, mount it on ws and a
websocket client does, hand it to stdio and a client that spawns a process does.

### mcp client

An `mcp_client::T` is one connection — and it connects **eagerly**: the constructor calls `listTools()`, so by
the time you hold the obj, `tool` is populated and `status` can answer a health check.

| key         | type   | meaning                                                                                        |
|-------------|--------|------------------------------------------------------------------------------------------------|
| `host`      | `uri`  | the endpoint; the **scheme picks the transport** (`http`/`https` → streamable-http, `ws`/`wss` → ws) |
| `transport` | `uri`  | force a transport when the scheme is ambiguous (`streamable-http`)                             |
| `command`   | `lst`  | **stdio**: the argv that spawns the server as a subprocess                                     |
| `env`       | `rec`  | environment for a spawned stdio server (it becomes headers on http/ws)                         |
| `headers`   | `rec`  | http/ws request headers                                                                        |
| `tool`      | `rec`  | *populated*: tool name → `tool::T`, each carrying the `inst` to call                           |
| `status`    | `bool` | *populated*: applying it runs a health check and answers true/false                            |

```mtron
mcp_client::[host=>http://localhost:8777/mcp]@/usr/ai/mcp/a
*/usr/ai/mcp/a>>status                        [-- the endpoint answers --]
*/usr/ai/mcp/a>>tool                          [-- one tool::T per tool --]
```

Invoking a tool is applying the `inst` it carries. The client is only a transport, so there is no call
vocabulary to learn:

```mtron
[-- the tool is applied through the inst it carries; the client is only a transport --]
/usr/ai/mcp/a/tool/m_web_mcp_mcp_mtron_eval_mtron/inst("1+2")     [-- => 3 --]
/usr/ai/mcp/a/tool/m_web_mcp_mcp_mtron_find_inst/inst("plus")
```

And the custom server defined above — one instruction — is reachable the same way through its own mount:

```mtron
[-- the same for a server you defined: its vid is the route target, and a client reaches it like any other --]
/basic => !*</sys/space/mcp/basic_server>
mcp_client::[host=>http://localhost:8777/basic]@/usr/ai/mcp/b
/usr/ai/mcp/b>>tool                                          [-- => [m_inst_eval => tool::[…]] --]
/usr/ai/mcp/b/tool/m_inst_eval/inst("[1,2,3,4]>-.sum()")     [-- => 10 --]
```

#### transports, and where sse lives

`createTransport` picks the carrier in this order: an explicit `transport => streamable-http`, else a non-empty
`command` (stdio), else the `host` scheme.

* **streamable-http** — `host => http://…`. The client POSTs a JSON-RPC request and reads the response; the
  server-to-client channel is the GET half of the same endpoint, an **sse** stream (`text/event-stream`) that the
  server holds open and pushes `event: message` frames down. A client never opens it explicitly — that is the
  streamable transport's business, and it is why one endpoint is enough.
* **websocket** — `host => ws://…`. One frame is one message, and there is no second channel because the socket
  is already bidirectional. It is the simplest carrier to reason about:

```mtron
mcp_client::[host=>ws://localhost:8555/mcp]@/usr/ai/mcp/c
*/usr/ai/mcp/c>>tool
```

* **stdio** — `command => […]`. The client spawns the server process and speaks newline-delimited JSON on its
  pipes: no port, no listener, and the server *is* a subprocess.

```mtron
[-- a stdio server: argv only — the client owns the process --]
mcp_client::[command=>['bin/metatron','--mcp']]@/usr/ai/mcp/metatron
mcp_client::[command=>['codegraph','serve','--mcp']]@/usr/ai/mcp/codegraph
```

A stdio client's `env` rec goes to the child process; on http/ws the same rec is sent as headers, so a server
configured by its environment and one configured by headers are configured in the same place.

#### the JSON snippet path

A published MCP server config is JSON, so it can be typecast instead of transcribed:

```mtron
mtron> """
       {"mcpServers": {
         "local" : {
           "type": "streamable-http",
           "url": "http://localhost:8777/mcp",
           "headers": {
            "IJ_MCP_SERVER_PROJECT_PATH": "."
           }
         }}}
       """.as(json::T).as(rec::T)>>mcpServers/local.as(json::T)@/usr/ai/mcp/local_cfg
==>json::'{"type":"streamable-http","url":"http://localhost:8777/mcp","headers":{}}'
```
That value is a `json::T` str — exactly what the client cast takes — so a published snippet becomes a client in
one line:

```mtron
[-- the same snippet, straight to a client: the cast reads the json::T str it produced --]
""".as(json::T).as(mcp_client::T)@/usr/ai/mcp/local
```

`as?mcp_client<=json` unwraps an `mcpServers` wrapper (every entry, as a lst), merges `args` into `command` and
`env` into `headers` — so the snippet a harness reads is the snippet mtron reads.

One wrinkle worth knowing before it bites: `simple()` hands every untyped JSON string to the **mtron parser**, so
a bare `"--mcp"` in a snippet parses as `minus(minus(mcp))`. Published stdio snippets spell the argument as a uri
(`"<--mcp>"`), which is what the drstynx boot does; `ObjJSONSerializer.literal()` keeps strings exactly as
written and is the reader to use for a document whose fields are all strings.

#### tools from a client, for an agent

A client is a tool *source*, which is how an agent gets someone else's tools with no wrapper:

```mtron
[-- the drstynx tool bag: instructions, skills, and two mcp servers — one of them a spawned subprocess --]
tool_feature::[tool => [!*gremlin,/
                       !*sql,/
                       !(map(json::"""{"mcpServers":{"codegraph":{"command":"codegraph","args":["<--mcp>"]}}}""").as(mcp_client::T)),/
                       mcp_client::[host=><http://localhost:8777/mcp>]]]
```

`tool_feature`'s intake ladder is `mTool | mcp_client | mTool.tool(t)`: an `mcp_client` in that list contributes
**all of its tools**, each keyed by the name the server advertised.

A client holds a live connection — and on http a server-side session — so a client that is never closed leaks
one. `close()` releases it; `clone()` returns the same client rather than opening a second connection.

### mcp server

An `mcp_server::T` is a rec with three optional collections — `tool`, `resource`, `prompt` — and it is
transport-agnostic: mount it, and the carrier supplies the bytes.

Because a tool is an instruction, a server is a **selection over instructions**, and that selection is boot data:

```mtron
mtron> mcp_server::[tool => [!*eval, !*eval]]@/sys/space/mcp/two_say
==>mcp_server::[tool=>[m_inst_eval=>eval?rng=#{*}&dom=#{?}(<#>::T){<j>}]]@/sys/space/mcp/two_say
mtron> */sys/space/mcp/two_say>>tool     [-- one entry: a collection is keyed by the instruction --]
==>[m_inst_eval=>eval?rng=#{*}&dom=#{?}(<#>::T){<j>}]
```
Three rules follow, and the third is the one that surprises people:

* **a tool's name is derived, never typed.** `mTool.toolName` flattens the instruction's path, so `/m/inst/eval`
  is `m_inst_eval` and `/m/llm/mcp/mcp_message/add_message` is `m_llm_mcp_mcp_message_add_message`. The name is
  stable across carriers *and across servers* — the same instruction is the same tool wherever it is mounted.
* **`tool` may be a list, a single instruction, or an already-keyed rec.** The type's constructor reshapes it into
  the keyed rec its `::T` declares, and it does so before `super(...)` — a typed obj is checked against its
  predicate *at construction*, so an unreshaped list could not be constructed at all. Which name an entry lands
  under follows from how you wrote it: a **collection** entry is named by its instruction (`!*eval` →
  `m_inst_eval`), while an **authored key is the name** (`adduser => …` publishes `adduser`, which is what the
  emulator's own clients call). Either way the key is what `tools/list` advertises, so a client can always call
  what it was shown.
* **`!*` is dereferenced when the server is built** — `!*eval` means "whatever `eval` resolves to right now",
  not a pointer the server carries.

A server can be built from a skill instead: its instructions become tools and its documents become resources.

```mtron
[-- a skill as a server; `.as(mcp_server::T)` is the whole mapping --]
*<mtronfs:skills/mtron>.as(skill::T).as(mcp_server::T)@/sys/space/mcp/mtron_skill
```

#### resources and prompts

* **resources** are the server's documents. A skill's markdown becomes resources keyed by relative path, each
  with `uri` / `name` / `description` and its text inline — except a document over 256 KB, which is exposed as a
  **`reference`** (a path to read) rather than inlined, so a listing stays small.
* **prompts** are templated messages: a prompt entry resolves against `noobj()` and returns one `user` message.

#### the ledger server (`mcp_message`)

`/m/llm`'s own server is `mcp_message` — the chat ledger over MCP, mounted as `/message` above. Its tools are
`add_message`, `get_messages` and `search_messages`, and it is the bus a harness lifecycle is mirrored onto (see
the `dsh-mtron` reference). Mounting it is one route away:

```mtron
mcp_client::[host=>http://localhost:8777/message]@/usr/ai/mcp/m
/usr/ai/mcp/m>>tool                       [-- add_message, get_messages, search_messages --]
```

`add_message` returns a **receipt**: the record, its `location`, and a `status` of `published`, `parked` (its tool
group still owes results) or `unpaired` (refused). Reads are windowed, session-scoped and newest-first:

```mtron
[-- root is an absolute space that serves incrq; session is the envelope that scopes the ledger --]
m_llm_mcp_mcp_message_add_message(root='/usr/doc/message', kind='user', text='first light', session='/usr/doc/session/a')
m_llm_mcp_mcp_message_get_messages(root='/usr/doc/message', session='/usr/doc/session/a', max=10)
m_llm_mcp_mcp_message_search_messages(root='/usr/doc/message', pattern='light', session='/usr/doc/session/a')
```

#### the carriers

| carrier     | vid                    | what it owns                                                                                            |
|-------------|------------------------|---------------------------------------------------------------------------------------------------------|
| `mcp_http`  | `/m/web/mcp/mcp_http`  | POST answers a request; **GET is the sse notification channel**; HEAD probes; DELETE ends a session       |
| `mcp_ws`    | `/m/web/mcp/mcp_ws`    | one handler per connection; the socket is already bidirectional                                          |
| `mcp_stdio` | `/m/web/mcp/mcp_stdio` | the process's own stdin/stdout; **fd 1 is the wire**                                                     |

`mcp_http` is Streamable HTTP: `initialize` opens a session (`Mcp-Session-Id`), a POST carries requests, and the
GET half answers only when the client sends `Accept: text/event-stream` — anything else is a 405 rather than a
socket held open forever. That GET drains the server's subscription outbox, then holds the stream open with
heartbeats, so a `?subq`-driven `notifications/resources/updated` reaches the client without a second request.

A route is what binds a server to a carrier, and a **short name is a factory, not an instance** — dereferencing
it yields the type, and the carrier builds one:

```mtron
/mcp       => mcp_mtron                               [-- the router's redirect table: a type with a ctor --]
/message   => mcp_message
/basic     => !*</sys/space/mcp/basic_server>         [-- an instance already built --]
/drstynx   => *dr.as(skill::T).as(mcp_server::T)      [-- an evaluated expression: built at mount time --]
```

#### stdio: the carrier with no carrier

`mcp_stdio` serves a server on the process's own streams, which is what lets an agent attach to a VM by spawning
one — no port, no listener, nothing left running:

```bash
bin/metatron --mcp                       # boot/mcp.boot.mtron, then serve on stdin/stdout
bin/metatron --mcp -b some.boot.mtron    # another profile, the same stdio discipline
```

A profile mounts the carrier like any other, and the `mcp => stdio` boot arg is what tells the BootLoader to
claim fd 1 *before* the boot file runs:

```mtron
[space       => /sys/space,
 log         => warn,
 mcp         => stdio,
 typer/stage => [code_resolve => false]]
[===============]
import(/m/mach/io);
import(/m/web,web);
import(/m/llm);
fsspace::[pattern => mfs:#, q => [mimeq::[=>]], route => [mfs: => <.>]]@/sys/space/fs/metatron;
memspace::[pattern => </usr/#>, q => [docq::[=>],subq::[=>],incrq::[=>]]]@/sys/space/usr;
mcp_server::[tool => [!*eval]]@/sys/space/mcp/basic_server;
mcp_stdio::[server => !*</sys/space/mcp/basic_server>]@/sys/io/mcp/stdio;
```

Three properties are worth stating because none of them is obvious:

* **stdout belongs to the protocol.** fd 1 is claimed for the wire, and `System.out` — with every logback console
  appender, which capture their stream once at start — is re-pointed at stderr. A boot banner, a log line or a
  widget render on fd 1 is read by the client as a malformed frame.
* **the session is the process.** One client, one VM, one session: `initialize` is answered inline, there is no
  session id, and the client closing stdin is the shutdown. There is no per-call mode — the JVM boots once and
  serves many calls.
* **what an agent can see is decided by the boot's spaces, not the transport.** All-memspace state makes the
  session an ephemeral island; mounting the same sqlite or database the live server mounts makes it another door
  into the same world. The carrier takes no position — one line of boot data decides.

A harness registers it like any other stdio server, which is the point:

```json
{"mcpServers": {"metatron": {"command": "bin/metatron", "args": ["--mcp"]}}}
```

## see also

* [web instruction set](web-instset-mtron.md) — the `protocol::T` lattice, route tables, `sse::T`, and the
  `mcp::T` surfaces a carrier realizes.
* [MCP server architecture](mcp-server-architecture.md) — `mcp_server`, tool registration, the carrier split.
* [MCP server notifications](mcp-server-notifications.md) — server→client push over a `?subq` subscription, and
  the `json::` / `.inst()` split-pattern those notifications ride on.
* `docs/design/mcp-stdio.md` — the stdio carrier in full: stdout discipline, the constructor reshape, and why the
  tools are being pulled out into instructions.
* `.metatron/skills/drstynx/assets/drstynx.boot.mtron` — a real boot file with two MCP clients (one http, one a
  spawned subprocess) inside a single `tool_feature`.