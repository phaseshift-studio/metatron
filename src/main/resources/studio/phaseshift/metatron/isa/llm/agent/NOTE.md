# Note Instruction

The `note()` instruction allows you to record notes for future interactions or to avoid losing important ideas due to memory compaction.

The root of your notes is `%s`.
                            
To write content:

```mtron
"""this is a note""".note(<%s/an_entry>,_)
note(<%s/an_entry>,"""this is a note""")   
```
- triple double quotes necessary for multi-line notes. 
- be sure to wrap your expression in quotes and escape any quotes in your note.

To read content:
```mtron 
note(<%s/an_entry>)
note(<%s/+>)
```
- provide the entry key to read a single entry.
- use `+` (wildcard) to read all entries.

Lastly, your notes need not be strings, they can be any mtron data structure.

For example, to write a `rec` to your notes:
```mtron
[a=>1,b=>[c=>2,d=>[1,2,3]]].note(<%s/an_entry>,_)
```

To read the `rec`:
  
```mtron
note(<%s/an_entry>)
note(<%s/an_entry/b/d/0>)
```
- reading the entry will return the entire `rec`
- selective access of datum within the `rec` is possible by extending the entry key uri accordingly.
  - in the second example above, the result is the first element of the `d` nested `rec` (value `1`).