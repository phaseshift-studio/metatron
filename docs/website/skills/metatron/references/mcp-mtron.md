---
name: metatron-mcp
description: Reference for agents requiring tools
---

# MCP Clients and Servers

## Adding an MCP Server

To connect to an MCP server, a `mcp_client::T` must be created. `mcp_client::T` requires the MCP server support either websocket, http, or stdio transport. For most situations, the following pattern suffices -- simply convert the MCP server's published `mcpServer` JSON snippet into an `mcp_client::T` via the transformation path `str::T => json::T => mcp_client::T`.

```mtron
mtron> """
       {
        "type": "streamable-http",
        "url": "http://127.0.0.1:64342/stream",
        "headers": {
         "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"
        }
       }
       """.as(json::T).as(mcp_client::T).to(/usr/marko/mcp/intellij)
```
If the snippet provided has an `mcpServer` outer wrapping, then do:

```mtron
mtron> """
       {"mcpServers": {
        "intellij" : {
          "type": "streamable-http",
          "url": "http://127.0.0.1:64342/stream",
          "headers": {
           "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"
          }
        }}}
       """.as(json::T).as(rec::T)>>mcpServers/intellij.as(json::T).as(mcp_client::T)
==>mcp_client::[
    type=>streamable-http,
    url=><http://127.0.0.1:64342/stream>,
    headers=>[IJ_MCP_SERVER_PROJECT_PATH=>~/software/metatron],
    host=><http://127.0.0.1:64342/stream>,
    status=>!inst?bool<=#{?}(),
    tool=>[  execute_run_configuration=>tool::[
      inst=>inst?#{?}<=#{?}(configurationName=>'Name of the existing run configuration to execute',filePath=>"File path relative to the project root. Provide together with `line` to create and execute a temporary run configuration from code context.",line=>"1-based line number for `filePath`. Provide together with `filePath` and do not combine with `configurationName`.",timeout=>'Timeout in milliseconds',waitForExit=>"Whether to wait for process termination. If false, the tool returns immediately after the process starts and ignores `timeout`.",programArguments=>'Optional program arguments override for this launch only. Missing/null or empty string keeps the existing value; whitespace-only string clears it.',workingDirectory=>'Optional working directory override for this launch only. Missing/null or empty string keeps the existing value; whitespace-only string clears it.',envs=>'Optional environment variable overrides for this launch only. Missing/null keeps existing env unchanged; when provided, values are merged over existing env.'),
      name=>execute_run_configuration,
      desc=>'Run either an existing run configuration by name or a tempo...',
      arg=>[
       configurationName=>'Name of the existing run configuration to execute',
       filePath=>'File path relative to the project root. Provide together wi...',
       line=>"1-based line number for `filePath`. Provide together with `...",
       timeout=>'Timeout in milliseconds',
       waitForExit=>'Whether to wait for process termination. If false, the tool...',
       programArguments=>'Optional program arguments override for this launch only. M...',
       workingDirectory=>'Optional working directory override for this launch only. M...',
       envs=>'Optional environment variable overrides for this launch onl...']],
     get_run_configurations=>tool::[
      inst=>inst?#{?}<=#{?}(filePath=>'Optional file path relative to the project root. When provided, returns run points (executable entry points) in the file instead of project-wide run configurations.'),
      name=>get_run_configurations,
      desc=>'Returns either project run configurations or executable cod...',
      arg=>[filePath=>'Optional file path relative to the project root. When provi...']],
     analyze_calls=>tool::[
      inst=>inst?#{?}<=#{?}(symbolFqn=>"Plain fully qualified symbol name, or an exact signature returned by an ambiguity error or copied from a rendered child node. If you only know a short name or fragment, use `search_symbol` first and pass the best fully qualified callable name here. Examples: `com.example.Service.run`, `com.example.Service.run(String)`, or `org.assertj.core.api.Assertions.assertThat(String)`. Do not pass file path, line, column, or a separate target signature.",analysisKind=>"Call analysis direction. Use `INCOMING_CALLS` to show callers of `symbolFqn`, or `OUTGOING_CALLS` to show symbols called from `symbolFqn`.",depth=>'Maximum number of call levels to render below the requested subtree root. Default: 5. Use 0 to render only the subtree root.',maxChildren=>'Maximum number of direct children rendered for each node. Default: 50.',maxNodes=>'Maximum total number of rendered call nodes. Default: 1000.',treePath=>"Optional path to a subtree root, copied exactly from a previous `analyze_calls` result. Null or omitted means the root path `[]`. Each component is an exact signature, not a display name.",childOffset=>"Offset for paging direct children of the node addressed by `treePath`. Default: 0.",timeout=>'Timeout in milliseconds'),
      name=>analyze_calls,
      desc=>'Builds the IDE Call Hierarchy tree for a method, function, ...',
      arg=>[
       symbolFqn=>'Plain fully qualified symbol name, or an exact signature re...',
       analysisKind=>"Call analysis direction. Use `INCOMING_CALLS` to show calle...",
       depth=>'Maximum number of call levels to render below the requested...',
       maxChildren=>'Maximum number of direct children rendered for each node. D...',
       maxNodes=>'Maximum total number of rendered call nodes. Default: 1000.',
       treePath=>'Optional path to a subtree root, copied exactly from a prev...',
       childOffset=>'Offset for paging direct children of the node addressed by ...',
       timeout=>'Timeout in milliseconds']],
     build_project=>tool::[
      inst=>inst?#{?}<=#{?}(rebuild=>"Whether to perform full rebuild the project. Defaults to false. Effective only when `filesToRebuild` is not specified.",filesToRebuild=>'If specified, only compile files with the specified paths. Paths are relative to the project root.',timeout=>'Timeout in milliseconds'),
      name=>build_project,
      desc=>'Triggers building of the project or specified files, waits ...',
      arg=>[
       rebuild=>'Whether to perform full rebuild the project. Defaults to fa...',
       filesToRebuild=>'If specified, only compile files with the specified paths. ...',
       timeout=>'Timeout in milliseconds']],
     get_file_problems=>tool::[
      inst=>inst?#{?}<=#{?}(filePath=>'Path relative to the project root',errorsOnly=>'Whether to include only errors or include both errors and warnings',timeout=>'Timeout in milliseconds'),
      name=>get_file_problems,
      desc=>'Analyzes the specified file for errors and warnings using I...',
      arg=>[
       filePath=>'Path relative to the project root',
       errorsOnly=>'Whether to include only errors or include both errors and w...',
       timeout=>'Timeout in milliseconds']],
     get_project_dependencies=>tool::[
      inst=>inst?#{?}<=#{?}(),
      name=>get_project_dependencies,
      desc=>"""Get a list of all dependencies defined in the project.
   Incl...""",
      arg=>[=>]],
     get_project_modules=>tool::[
      inst=>inst?#{?}<=#{?}(),
      name=>get_project_modules,
      desc=>"""Get a list of all modules in the project with their types.
   ...""",
      arg=>[=>]],
     lint_files=>tool::[
      inst=>inst?#{?}<=#{?}(files=>'List of project-relative files to analyze. Duplicate paths are ignored after normalization.',min_severity=>"Minimum severity to include: `warning` or `error`. Defaults to `warning`.",timeout=>'Timeout in milliseconds'),
      name=>lint_files,
      desc=>'Analyzes the specified files for errors and warnings using ...',
      arg=>[
       files=>'List of project-relative files to analyze. Duplicate paths ...',
       min_severity=>"Minimum severity to include: `warning` or `error`. Defaults...",
       timeout=>'Timeout in milliseconds']],
     create_new_file=>tool::[
      inst=>inst?#{?}<=#{?}(pathInProject=>'Path where the file should be created relative to the project root',text=>'Content to write into the new file',overwrite=>'Whether to overwrite an existing file if exists. If false, an exception is thrown in case of a conflict.'),
      name=>create_new_file,
      desc=>'Creates a new file at the specified path within the project...',
      arg=>[
       pathInProject=>'Path where the file should be created relative to the proje...',
       text=>'Content to write into the new file',
       overwrite=>'Whether to overwrite an existing file if exists. If false, ...']],
     get_all_open_file_paths=>tool::[
      inst=>inst?#{?}<=#{?}(),
      name=>get_all_open_file_paths,
      desc=>"Returns active editor's and other open editors' file paths ...",
      arg=>[=>]],
     ...(44 more)]]
```
Moreover, if the `mcpServer` snippet has multiple inner servers endpoints defined, to load all of them, do:

```mtron
mtron> {"mcpServers": {
         "intellij": {
         }
==>fail::[parse error at line 1, col 1:
     {"mcpServers": {
            "intellij": {
   ...
     ^
     unclosed '{' — missing '}'?]@/sys/fail/50
mtron> }}
==>fail::[parse error at line 1, col 1:
     }}
     ^
     unexpected '}' — missing opening '{' or extra '}'?]@/sys/fail/52
```
For `STDIO` transport MCP servers, the same process works:

```
mcp_client::[command=>[</home/killswitch/.local/bin/codegraph>,'serve', '--mcp']]@codegraph;
``` 

do

```mtron
mtron> *mcp_server
```### Using tools from the client

After connecting, `mcp_client::T` populates its `tool` field with `tool::T` entries keyed by `mTool.toolName(tid)` — the flattened instruction tid (e.g. `m_inst_eval_mtron`). Each entry carries `inst`, `name`, `desc`, and `arg`:

```mtron
mtron> mcp_client::[host=>http://localhost:8777/mcp]@a
==>mcp_client::[
    host=>http://localhost:8777/mcp,
    status=>!inst?bool<=#{?}(),
    tool=>[
     m_inst_write_memory=>tool::[
      inst=>inst?#{?}<=#{?}(current_memory=>'the memory to remember -- a str::T, a markdown::T, etc.',previous_memory=>'a previous memory vid to chain current memory to'),
      name=>m_inst_write_memory,
      desc=>'(experimental) returns a memory relation of the form(curren...',
      arg=>[
       current_memory=>'the memory to remember -- a str::T, a markdown::T, etc.',
       previous_memory=>'a previous memory vid to chain current memory to']],
     m_inst_read_memory=>tool::[
      inst=>inst?#{?}<=#{?}(memory_vid=>'the vid of the memory to read'),
      name=>m_inst_read_memory,
      desc=>'(experimental) returns the result of reading the provided m...',
      arg=>[memory_vid=>'the vid of the memory to read']],
     m_inst_eval_mtron=>tool::[
      inst=>inst?#{?}<=#{?}(code=>'mtron code to evaluate'),
      name=>m_inst_eval_mtron,
      desc=>'returns the result of evaluating the provided mtron express...',
      arg=>[code=>'mtron code to evaluate']],
     m_inst_list_space=>tool::[
      inst=>inst?#{?}<=#{?}(),
      name=>m_inst_list_space,
      desc=>'returns a rec identifying all active metatron spaces',
      arg=>[=>]],
     m_inst_router_info=>tool::[
      inst=>inst?#{?}<=#{?}(),
      name=>m_inst_router_info,
      desc=>'returns router vid, tid, and space count',
      arg=>[=>]],
     m_inst_find_inst=>tool::[
      inst=>inst?#{?}<=#{?}(pattern=>'the inst tid to match',dom=>'the dom of inst to match (can be added to pattern arg)',rng=>'the rng of inst to match (can be added to pattern arg'),
      name=>m_inst_find_inst,
      desc=>'returns a lst of all instruction pattern matches w/ documen...',
      arg=>[
       pattern=>'the inst tid to match',
       dom=>'the dom of inst to match (can be added to pattern arg)',
       rng=>'the rng of inst to match (can be added to pattern arg']],
     m_inst_spawn_wsclient=>tool::[
      inst=>inst?#{?}<=#{?}(host=>'the full ws:// uri of the websocket server to connect to',on_message=>'the function to evaluate on every received message'),
      name=>m_inst_spawn_wsclient,
      desc=>'create a websocket client with provided on_message behavior',
      arg=>[
       host=>'the full ws:// uri of the websocket server to connect to',
       on_message=>'the function to evaluate on every received message']],
     m_inst_spawn_wshandler=>tool::[
      inst=>inst?#{?}<=#{?}(host=>'the full ws:// uri of the websocket handler to expose',on_message=>'the function to evaluate on every received message'),
      name=>m_inst_spawn_wshandler,
      desc=>'create a websocket handler with provided on_message behavior',
      arg=>[
       host=>'the full ws:// uri of the websocket handler to expose',
       on_message=>'the function to evaluate on every received message']]]]@a
mtron> *a>>tool
==>[
    m_inst_write_memory=>tool::[
     inst=>inst?#{?}<=#{?}(current_memory=>'the memory to remember -- a str::T, a markdown::T, etc.',previous_memory=>'a previous memory vid to chain current memory to'),
     name=>m_inst_write_memory,
     desc=>'(experimental) returns a memory relation of the form(curren...',
     arg=>[
      current_memory=>'the memory to remember -- a str::T, a markdown::T, etc.',
      previous_memory=>'a previous memory vid to chain current memory to']],
    m_inst_read_memory=>tool::[
     inst=>inst?#{?}<=#{?}(memory_vid=>'the vid of the memory to read'),
     name=>m_inst_read_memory,
     desc=>'(experimental) returns the result of reading the provided m...',
     arg=>[memory_vid=>'the vid of the memory to read']],
    m_inst_eval_mtron=>tool::[
     inst=>inst?#{?}<=#{?}(code=>'mtron code to evaluate'),
     name=>m_inst_eval_mtron,
     desc=>'returns the result of evaluating the provided mtron express...',
     arg=>[code=>'mtron code to evaluate']],
    m_inst_list_space=>tool::[
     inst=>inst?#{?}<=#{?}(),
     name=>m_inst_list_space,
     desc=>'returns a rec identifying all active metatron spaces',
     arg=>[=>]],
    m_inst_router_info=>tool::[
     inst=>inst?#{?}<=#{?}(),
     name=>m_inst_router_info,
     desc=>'returns router vid, tid, and space count',
     arg=>[=>]],
    m_inst_find_inst=>tool::[
     inst=>inst?#{?}<=#{?}(pattern=>'the inst tid to match',dom=>'the dom of inst to match (can be added to pattern arg)',rng=>'the rng of inst to match (can be added to pattern arg'),
     name=>m_inst_find_inst,
     desc=>'returns a lst of all instruction pattern matches w/ documen...',
     arg=>[
      pattern=>'the inst tid to match',
      dom=>'the dom of inst to match (can be added to pattern arg)',
      rng=>'the rng of inst to match (can be added to pattern arg']],
    m_inst_spawn_wsclient=>tool::[
     inst=>inst?#{?}<=#{?}(host=>'the full ws:// uri of the websocket server to connect to',on_message=>'the function to evaluate on every received message'),
     name=>m_inst_spawn_wsclient,
     desc=>'create a websocket client with provided on_message behavior',
     arg=>[
      host=>'the full ws:// uri of the websocket server to connect to',
      on_message=>'the function to evaluate on every received message']],
    m_inst_spawn_wshandler=>tool::[
     inst=>inst?#{?}<=#{?}(host=>'the full ws:// uri of the websocket handler to expose',on_message=>'the function to evaluate on every received message'),
     name=>m_inst_spawn_wshandler,
     desc=>'create a websocket handler with provided on_message behavior',
     arg=>[
      host=>'the full ws:// uri of the websocket handler to expose',
      on_message=>'the function to evaluate on every received message']]]
mtron> [-- => [m_inst_eval_mtron=>tool::[inst=>..., name=>m_inst_eval_mtron, desc=>..., arg=>...], ...] --]
mtron> [-- invoke a tool by applying its inst field --]
mtron> a/tool/m_inst_eval_mtron/inst("1+2")
==>3
mtron> [-- => 3 --]
```
### WebSocket

```
mcp_client::[host => <http://127.0.0.1:29170/index-mcp/streamable-http>]@/usr/ai/mcp/index-mcp;
```


### HTTP

#### HTTP Stream

Given the `mcpServers` JSON snippet below:

```json
{
 "type": "streamable-http",
 "url": "http://127.0.0.1:64342/stream",
 "headers": {
  "IJ_MCP_SERVER_PROJECT_PATH": "/home/killswitch/software/metatron"
 }
}
```

```
mcp_client::[host => <http://127.0.0.1:64342/stream>]@/usr/ai/mcp/intellij; 
```