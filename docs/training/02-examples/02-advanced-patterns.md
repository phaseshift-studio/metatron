# Advanced Patterns - Real-World Metatron Usage

This document shows advanced patterns and real-world usage from `boot/boot.mtron` and production code.

## Space Configuration Patterns

### SQL Database Space
```metatron
tble::[pattern    => netflix:#,
       host       => <mariadb://localhost:3306/netflix?user=mtron&password=mtron>,
       route      => [<netflix:>=><>],
       serializer => !*</m/mach/io/serializer/json/simple>,
       table      => [,],
       driver     => <org.mariadb.jdbc.Driver>]@/sys/space/netflix;
```

**Key elements:**
- `pattern => netflix:#` - Matches all URIs starting with `netflix:`
- `host => <mariadb://...>` - Database connection URI
- `route => [<netflix:>=><>]` - Strips `netflix:` prefix before passing to space
- `serializer => !*<...>` - Execute instruction to get serializer object
- `table => [,]` - Empty list means auto-discover tables
- `driver => <...>` - JDBC driver class name

### Graph Database Space
```metatron
tp3::[pattern => grateful:#,
      host    => <conf/grateful-dead.properties>,
      route   => [<grateful:>=><>]]@/sys/space/grateful;
```

**Simpler configuration:**
- Uses properties file for TinkerPop configuration
- Same pattern/route structure as SQL

### HTTP Server Space with Multiple Routes
```metatron
http::[host     => <http://localhost:8777>,
       pattern  => http://#,
       route    => [/ => examples/www,
                    /docs => target/docs]]@/sys/space/www;
```

**Multiple route mappings:**
- `/` → `examples/www` - Root maps to www directory
- `/docs` → `target/docs` - Docs maps to target directory
- Allows serving from multiple filesystem locations

### Distributed/Cluster Space
```metatron
meta::[pattern => meta://#,
       peers   => !*boot/peers,
       route   => [<meta://>=><>]]@/sys/space/meta;
```

**Distributed computing:**
- `peers => !*boot/peers` - Execute to get list of peer nodes
- Enables distributed data access across multiple Metatron instances

## Conditional Configuration with Defaults

### MQTT Broker Configuration
```metatron
<boot/args/mqtt>.else(boot/args/mqtt -> [broker => <mqtt://chibi.local:1883>])
```

**Pattern:**
1. Try to read `boot/args/mqtt`
2. If it doesn't exist or is empty, execute the else clause
3. Else clause sets default broker configuration

### Peer List Configuration
```metatron
*<boot/peers>.else(boot/peers -> [ws://localhost:6666, <ws://127.0.0.1:7777>])
```

**Default peer list:**
- If no peers configured, use localhost on two ports
- Useful for development/testing

### Script Interpreter Configuration
```metatron
*<boot/script>.else(boot/script ->
 [sh     => /bin/sh,
  bash   => /bin/bash,
  zsh    => /bin/zsh,
  python => /usr/bin/python3,
  perl   => /usr/bin/perl,
  mtron  => /bin/mtron])
```

**Multi-line default:**
- Maps script types to interpreter paths
- Enables script execution from Metatron

## Dynamic Configuration

### Filesystem Prefix Construction
```metatron
fs_prefix -> *boot/args/local.as(str::T).plus(':#').as(uri::T);
```

**Chain of operations:**
1. `*boot/args/local` - Get local path from boot args
2. `.as(str::T)` - Convert to string
3. `.plus(':#')` - Append `:#` pattern
4. `.as(uri::T)` - Convert to URI
5. `fs_prefix ->` - Assign to variable

**Then use it:**
```metatron
fs::[pattern => *fs_prefix,
     route   => [*fs_prefix=><file://>]]@/sys/space/fs;
```

## IoT Integration Patterns

### MQTT Space with Dynamic Broker
```metatron
mqtt::[pattern => mqtt://#,
       broker  => !*boot/args/mqtt/broker,
       route   => [<mqtt://>=><>]]@/sys/space/mqtt;
```

**Dynamic broker:**
- `broker => !*boot/args/mqtt/broker` - Execute to get broker URI
- Allows runtime configuration

### Zigbee2MQTT Integration
```metatron
miot::[pattern => miot://#,
       route   => [<miot://>=><>],
       mqtt    => [broker        => !*boot/args/mqtt/broker,
                   state_topic   => z2m/+,
                   command_topic => inst?uri<=rec(){
                       to(a).map(z2m:).mult(*a../friendly_name).mult(set/state)
                   }]]@/sys/space/miot;
```

**Complex MQTT configuration:**
- `state_topic => z2m/+` - Subscribe to all Zigbee2MQTT state topics
- `command_topic => inst?uri<=rec(){ ... }` - Inline instruction to build command topics
  - Takes a record (device info)
  - Extracts friendly name
  - Builds topic: `z2m/{friendly_name}/set/state`

### Home Assistant Integration
```metatron
haos::[pattern => haos://#,
       host    => <http://homeassistant.local:8123>,
       token   => *boot/args/haos/token,
       route   => [<haos://>=><>]]@/sys/space/haos;
```

**Authentication:**
- `token => *boot/args/haos/token` - Dereference token from boot args
- Keeps sensitive data separate from code

## LLM Integration

### Ollama Catalog Space
```metatron
catalog::[pattern => catalog://#,
          host    => <http://localhost:11434>,
          route   => [<catalog://>=><>]]@/sys/space/ollama;
```

**Usage:**
```metatron
*catalog:models              % List available models
*catalog:models/llama2       % Get llama2 model info
catalog:pull/llama2 -> [,]   % Pull llama2 model
```

## Serial Communication

### Serial Port Space
```metatron
serial::[pattern => serial://#,
         route   => [<serial://>=><>]]@/sys/space/serial;
```

**Usage:**
```metatron
*serial:/dev/ttyUSB0         % Read from serial port
serial:/dev/ttyUSB0 -> "AT\r\n"  % Write AT command
```

## Import and Initialization Patterns

### Importing Instruction Sets
```metatron
[== mount instruction sets ==];
print("{{y}}mounting instruction sets\n{{X}}");
import(/m/mach/io);
import(/m/math);
import(/m/web);
import(/m/iot);
import(/m/grph/tp3);
import(/m/llm);
import(/m/tble);
```

**Pattern:**
1. Print status message with color
2. Import each instruction set
3. Makes operations available globally

### Colored Output
```metatron
print("{{y}}mounting instruction sets\n{{X}}");
print("{{r}}------------\n{{g}}*{{y}}boot/args{{X}}: ", *boot/args, "\n{{r}}------------{{X}}\n");
```

**Color codes:**
- `{{y}}` - Yellow for headings
- `{{r}}` - Red for separators
- `{{g}}` - Green for labels
- `{{X}}` - Reset to default

## Advanced Navigation Patterns

### Multi-level Navigation
```metatron
*a.>>b>>c>>d                 % Navigate 4 levels deep
*user.>>profile>>address>>city  % Get nested field
```

### Navigation with Wildcards
```metatron
*users/+.>>name              % Get names of all users
*posts/+.>>author.>>name     % Get author names of all posts
```

### Navigate and Return
```metatron
*a.>>b>>c.<<.<<              % Navigate in 2 levels, back out 2 levels
```

## Query Optimization Patterns

### Count Optimization
```metatron
*netflix:movie.count()
% Automatically rewritten to: SELECT COUNT(*) FROM movie
% Instead of fetching all rows and counting in memory
```

### Field Selection
```metatron
*netflix:movie/+.>>title
% Could be optimized to: SELECT title FROM movie
% Instead of fetching all fields
```

## Error Handling Patterns

### Graceful Defaults
```metatron
*config/timeout.else(config/timeout -> 30)
% If timeout not configured, use 30 seconds
```

### Conditional Execution
```metatron
value-<|[
  is(gt(0)) => process_positive,
  is(lt(0)) => process_negative,
  _ => process_zero
].rng()
```

## Data Transformation Patterns

### Split-Transform-Merge
```metatron
"hello world"
  .-<' '                     % Split by space
  .map(capitalize)           % Capitalize each word
  .>-' '                     % Join back with space
% Result: "Hello World"
```

### Filter-Map-Reduce
```metatron
*users/+
  .where([age=>is(gte(18))])  % Filter adults
  .select([name])             % Select just names
  .count()                    % Count results
```

## Cross-Space References

### Reference Across Spaces
```metatron
[movie  => !*netflix:movie/1,
 graph  => !*grateful:vertex/1,
 file   => !*local:data/config.json,
 device => !*haos:light.living_room]
```

**Universal references:**
- Each reference points to different data system
- All accessible through same `!*` syntax
- Enables cross-system data integration

### Navigation Across Spaces
```metatron
*mydata.>>movie.>>director_id.>>name
% Could traverse: mydata → netflix:movie → netflix:director → name
% Automatic cross-table/cross-space navigation
```

## Inline Instruction Patterns

### Command Topic Builder
```metatron
inst?uri<=rec(){
    to(a).map(z2m:).mult(*a../friendly_name).mult(set/state)
}
```

**Breakdown:**
- `inst?uri<=rec()` - Instruction that takes record, returns URI
- `to(a)` - Bind input to variable `a`
- `.map(z2m:)` - Start with `z2m:` prefix
- `.mult(*a../friendly_name)` - Append friendly name from record
- `.mult(set/state)` - Append `/set/state`
- Result: `z2m/{friendly_name}/set/state`

## Memory Space Patterns

### In-Memory Cache
```metatron
mem::[pattern => /cache/#,
      q       => [subq::[=>]]]@/sys/space/cache;
```

**Usage:**
```metatron
/cache/user/1 -> [name=>"Alice", age=>30]  % Store in cache
*cache/user/1                               % Retrieve from cache
```

### User Space
```metatron
mem::[pattern => /usr/#,
      q       => [subq::[=>]]]@/sys/space/usr;
```

**User-specific data:**
- Each user gets their own namespace
- Fast in-memory access
- Volatile (lost on restart)

## Next Steps

- See [SQL Integration](03-sql-integration.md) for database-specific patterns
- See [IoT Integration](04-iot-integration.md) for IoT/MQTT details
- See [Mtron Syntax](../02-language/01-mtron-syntax.md) for syntax reference
- See [Metatron Environment](../02-language/02-metatron-environment.md) for architecture
