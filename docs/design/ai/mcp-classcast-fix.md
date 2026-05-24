# MCP ClassCastException Fix

## Issue Summary

The MCP tool tests were failing with `ClassCastException` errors when trying to cast `SXPXXXfURI` objects to `String`:

```
java.lang.ClassCastException: class studio.phaseshift.metatron.furi.form.SXPXXXfURI cannot be cast to class java.lang.String
```

## Root Cause

### Understanding the fURI Form System

In metatron, fURIs (functional URIs) serve as the ID system for objects. The `furi.form` package contains optimized implementations that only allocate memory for the URI components that are actually present, avoiding the overhead of a single monolithic fURI class with all possible fields.

- **`SXPXXXfURI`**: An optimized fURI form with only a **S**cheme and **P**ath (no authority, no coefficient, no query)
- Extends `XXPXXXfURI` → extends `AbstractfURI` → implements `fURI`
- The naming convention indicates which components are present:
  - `S` = Scheme present
  - `A` = Authority (host/port) present
  - `P` = Path present
  - `C` = Coefficient present
  - `Q` = Query present
  - `X` = Component absent

This optimization is critical because URI processing is expensive relative to simpler ID systems like Long-based spaces, so metatron minimizes memory allocation by using the smallest possible representation for each fURI.

### The Problem

When Jackson's `ObjectMapper` deserialized JSON-RPC arguments in the MCP tool handlers, metatron's type system was intercepting the deserialization and creating `SXPXXXfURI` form objects instead of leaving them as plain Java `String` objects.

In `MetatronMcpServer.java`, the code was attempting explicit casts:

```java
// Line 94 - evaluate_code tool
String code = (String) args.get("code");  // ❌ ClassCastException!

// Line 173 - list_instructions tool
String filter = args.containsKey("filter") ? (String) args.get("filter") : null;  // ❌ ClassCastException!
```

## Solution

Instead of casting to `String`, call `.toString()` on the monad to unwrap the value:

```java
// Line 94 - evaluate_code tool
String code = args.get("code").toString();  // ✅ Works with both String and fURI monads

// Line 173 - list_instructions tool
String filter = args.containsKey("filter") ? args.get("filter").toString() : null;  // ✅ Works with both
```

### Why This Works

The `fURI` interface and its implementations (`SXPXXXfURI`, etc.) all implement `toString()` which returns the string representation of the URI. When the value is wrapped in a monad, calling `.toString()` extracts the underlying string value.

From `AbstractfURI.java` line 669:
```java
@Override
public String toString() {
    final StringBuilder sb = new StringBuilder();
    if (null != scheme())
        sb.append(scheme()).append(":");
    if (null != host()) {
        sb.append("//");
        sb.append(host());
        if (-1 != port())
            sb.append(":").append(port());
    }
    // ... builds the URI string representation
    return sb.toString();
}
```

## Files Modified

- `/home/killswitch/software/metatron/src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/MetatronMcpServer.java`
  - Line 95: Changed `(String) args.get("code")` to `args.get("code").toString()`
  - Line 175: Changed `(String) args.get("filter")` to `args.get("filter").toString()`

- `/home/killswitch/software/metatron/src/main/java/studio/phaseshift/metatron/isa/mach/type/net/MServer.java`
  - Line 161: Changed `final String sessionId = conn.getAttachment();` to `final String sessionId = conn.getAttachment().toString();`

## Impact

This fix allows the MCP tool handlers to work correctly regardless of whether Jackson deserializes the arguments as plain `String` objects or wraps them in metatron's fURI monad system. The `.toString()` method works for both cases:

- If it's already a `String`, `.toString()` returns itself
- If it's a `SXPXXXfURI` monad, `.toString()` unwraps the value

## Testing

After this fix, the MCP tool tests should no longer throw `ClassCastException` errors. The tests that were timing out (`testMcpGetSystemInfoTool` and `testMcpListInstructionsTool`) should now be able to process the tool arguments correctly.

## Related Documentation

- See `docs/ai/mcp-implementation-complete.md` for MCP server implementation details
- See `docs/ai/mcp-server-usage.md` for MCP server usage guide
- See `src/main/java/studio/phaseshift/metatron/furi/` package for fURI monad implementation
