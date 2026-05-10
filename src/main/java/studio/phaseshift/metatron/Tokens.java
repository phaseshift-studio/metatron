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

package studio.phaseshift.metatron;

import studio.phaseshift.metatron.furi.fURI;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Tokens {


    private Tokens() {
        // do nothing
    }

    public static final String MTRON = "mtron";
    public static final String METATRON = "metatron";
    public static final String METATRON_VERSION = "0.1-SNAPSHOT";
    public static final fURI HASH_FURI = f("#");
    public static final fURI PLUS_FURI = f("+");
    public static final fURI STACK_PATTERN = f("+/#");
    public static final String OPENAI = "openai";
    public static final String LOCALAI = "localai";
    public static final String ANTHROPIC = "anthropic";
    public static final String OLLAMA = "ollama";
    public static final String RESULT = "result";
    public static final String DATA = "data";
    public static final String TO = "to";
    public static final String JSONRPC = "jsonrpc";
    public static final String REQUIRED = "required";
    public static final String SRC = "src";
    public static final String TARGET = "target";
    public static final String INCR = "incr";
    public static final String FORMAT = "format";
    public static final String PROVIDER = "provider";
    public static final String API_KEY = "api_key";
    public static final String ORG = "org";
    public static final String BLOCK = "block";
    public static final String AI = "AI";
    public static final String TIME = "time";
    public static final String POSTGRESQL = "postgresql";
    public static final String ENTRY = "entry";
    public static final String ID = "id";
    public static final String IN = "in";
    public static final String OUT = "out";
    public static final String COEFFICIENT = "coefficient";
    public static final String QUERY = "query";
    public static final String DOM = "dom";
    public static final String RNG = "rng";
    public static final String SIZE = "size";
    public static final String SKILL = "skill";
    public static final String MIME_TYPE = "mimeType";
    public static final String LLM = "llm";
    public static final String MCP = "mcp";
    public static final String PANE = "pane";
    public static final String LICENSE = "license";
    public static final String THINK = "think";
    public static final String COST = "cost";
    public static final String THOUGHT = "thought";
    public static final String MEMORY = "memory";
    public static final String NOTE = "note";
    public static final String HISTORY = "history";
    public static final String HREF = "href";
    public static final String TOOL = "tool";
    public static final String PROMPT = "prompt";
    public static final String RESOURCE = "resource";
    public static final String RESPONSE = "response";
    public static final String OBJECT = "object";
    public static final String TYPE = "type";
    public static final String THINKING = "thinking";
    public static final String TEXT = "text";
    public static final String MONAD = "monad";
    public static final String REPEAT = "repeat";
    public static final String SHORT = "short";
    public static final String LONG = "long";
    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";
    public static final String HTML = "html";
    public static final String RDF = "rdf";
    public static final String HEAD = "head";
    public static final String P = "p";
    public static final String BODY = "body";
    public static final String B_LIST = "b_list";
    public static final String O_LIST = "o_list";
    public static final String TAG = "tag";
    public static final String DIV = "div";
    public static final String QUOTE = "quote";
    public static final String CODE = "code";
    public static final String LANG = "lang";
    public static final String START = "start";
    public static final String METHOD = "method";
    public static final String MESSAGE = "message";
    public static final String RUNNING = "running";
    public static final String HALTED = "halted";
    public static final String STOPPED = "stopped";
    public static final String PAUSED = "paused";
    public static final String BARRIER = "barrier";
    public static final String REASON = "reason";
    public static final String SUPER = "super";
    public static final String ROUTE = "route";
    public static final String PEERS = "peers";
    public static final String CACHE = "cache";
    public static final String CONST = "const";
    public static final String CONSTQ = "constq";
    public static final String CLIENT = "client";
    public static final String SCRIPT = "script";
    public static final String REWRITE = "rewrite";
    public static final String SUGAR = "sugar";
    public static final String QPROC = "q";
    public static final String ICON = "icon";
    public static final String T = "T";
    public static final String C = "c";
    public static final String MQL = "mql";
    public static final String MIN = "min";
    public static final String MAX = "max";
    public static final String PATH = "path";
    public static final String POLY = "poly";
    public static final String PERSIST = "persist";
    public static final String SCHEME = "scheme";
    public static final String AUTHORITY = "authority";
    public static final String SUB = "sub";
    public static final String SUBQ = "subq";
    public static final String PATTERN = "pattern";
    public static final String SERIALIZER = "serializer";
    public static final String LOGG = "log";
    public static final String DRIVER = "driver";
    public static final String SEND = "send";
    public static final String CLOSE = "close";
    public static final String TABLE = "table";
    public static final String CTOR = "ctor";
    public static final String SPARQL = "sparql";
    public static final String COLLECTION = "collection";
    public static final String REFERENCE = "reference";
    public static final String VALUE = "value";
    public static final String FURI = "furi";
    public static final String OBJ = "obj";
    public static final String JDBC = "jdbc:";
    public static final String STATUS = "status";
    public static final String ON_OPEN = "on_open";
    public static final String ON_ERROR = "on_error";
    public static final String ON_MESSAGE = "on_message";
    public static final String ON_CLOSE = "on_close";
    public static final String ON_GET = "on_get";
    public static final String ON_POST = "on_post";
    public static final String ON_PUT = "on_put";
    public static final String ON_DELETE = "on_delete";
    public static final String ON_PATCH = "on_patch";
    public static final String ON_HEAD = "on_head";
    public static final String ON_OPTIONS = "on_options";
    public static final String HOST = "host";
    public static final String HEADERS = "headers";
    public static final String TRANSPORT = "transport";
    public static final String SERVER = "server";
    public static final String LOCAL = "local";
    public static final String PORT = "port";
    public static final String USER = "user";
    public static final String PASS = "pass";
    public static final String NAME = "name";
    public static final String CREATOR = "creator";
    public static final String LEVEL = "level";
    public static final String SPACE = "space";
    public static final String INST = "inst";
    public static final String SQL = "sql";
    public static final String STORE = "store";
    public static final String LOOP = "loop";
    public static final String GRAPH = "graph";
    public static final String PREFIX = "prefix";
    public static final String USER_HOME = "user.home";
    public static final String PREPEND = "prepend";
    public static final String LOAD = "load";
    public static final String NATIVE = "native";
    public static final String HTTP = "http";
    public static final String HTTPS = "https";
    public static final String STREAMABLE_HTTP = "streamable-http";
    public static final String WS = "ws";
    public static final String WSS = "wss";
    public static final String MQTT = "mqtt";
    public static final String EMPTY = "";
    public static final String ARG = "arg";
    public static final String DESC = "desc";
    public static final String DIR = "dir";
    public static final String CONTENT = "content";
    public static final String EXAMPLE = "example";
    public static final String MODEL = "model";
    public static final String DOC = "doc";
    public static final String LHS = "lhs";
    public static final String AGENT = "agent";
    public static final String GGUF_KEY = "gguf";
    public static final String QUANT = "quant";
    public static final String FAMILY = "family";
    public static final String FROM = "from";
    public static final String PROBABILITY = "probability";
    public static final String FIELD = "field";
    public static final String CLUSTER = "cluster";
    public static final String BOOT = "boot";
    public static final String TITLE = "title";
    public static final String HR = "hr";
    public static final String ON_RECV = "on_recv";
    public static final String HOSTNAME = "HOSTNAME";
    public static final String SCHEMA = "schema";
    public static final String INSTSET = "instset";
    public static final String TABLES = "tables";
    public static final String ALT = "alt";
    public static final String REFERENCES = "references";
    public static final String FROM_TABLE = "from_table";
    public static final String FROM_COLUMN = "from_column";
    public static final String TO_TABLE = "to_table";
    public static final String TO_COLUMN = "to_column";
    public static final String URI = "uri";
    public static final String LABEL = "label";
    public static final String VERTEX = "vertex";
    public static final String EDGE = "edge";
    public static final String CONFIG = "config";
    public static final String STATE = "state";
    public static final String ROOT = "root";
    public static final String SESSION = "session";
    public static final String WEB_ROOT = "web_root";
    public static final String DEFAULT_PAGE = "default_page";
    public static final String READ_ONLY = "read_only";


}
