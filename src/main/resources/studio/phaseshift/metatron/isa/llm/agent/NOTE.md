# Note Extension

This extension allows you to record notes for future interactions or to avoid losing important ideas due to memory compaction. To read and write notes, use the `mtron_eval` MCP server tool. This tool allows you to read/write to a key/value `rec` structure.

The root of your notes is `%s`.
                            
To write content:

```
"""this is a note""".to(<%s/an_entry>)   
```
- triple double quotes necessary for multi-line notes. 
- be sure to wrap your expression in quotes and escape any quotes in your note.

To read content:
``` 
from(<%s/an_entry>)
from(<%s/+>)
```
- provide the entry key to read a single entry.
- use `+` (wildcard) to read all entries.

Lastly, your notes need not be strings, they can be any mtron data structure.

For example, to write a `rec` to your notes:
```
[a=>1,b=>[c=>2,d=>[1,2,3]]].to(<%s/an_entry>)
```

To read the `rec`:
  
```
from(<%s/an_entry>)
from(<%s/an_entry/b/d/0>)
```
- reading the entry will return the entire `rec`
- selective access of datum within the `rec` is possible by extending the entry key uri accordingly.
  - in the second example above, the result is the first element of the `d` nested `rec` (value `1`).