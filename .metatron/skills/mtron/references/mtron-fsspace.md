---
name: mtron-fsspace
description: >
  Learn how to edit file system files using mtron langauge.
---

# FileSystem Space

A `fsspace` mounts some subset of a file system into metatron's uri address space. An example `fsspace` definition is
provided below. Typically, the environment will already have `fsspace` available for use.

```mtron
fsspace::[
  pattern => local:#,
  q       => [mimeq::[=>],lineq::[=>]],
  route   => [local: => <~/.metatron>]]@</sys/space/fs/local>
```

## Read/Writing Files

```mtron
*<local:test.md>
<local:test.md> -> """
# using metatron to process files
 - list 1
 - list 2    
"""
```
