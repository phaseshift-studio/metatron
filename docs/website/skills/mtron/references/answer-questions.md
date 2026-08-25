---
name: answer-questions
description: How to answer questions about mtron/metatron — syntax, conceptual, and troubleshooting approaches, plus retrieving documentation via ?docq.
---

# Answering Questions

## Question Types

| Type            | Approach                            |
|-----------------|-------------------------------------|
| Syntax          | Provide examples with explanations  |
| Conceptual      | High-level first, then drill down   |
| Troubleshooting | Review history, suggest diagnostics |

## Getting Documentation for Any Obj

Any obj in metatron can have associated documentation. Use `?docq` suffix:

```mtron
*/path/to/obj           [-- the obj itself --]
*/path/to/obj?docq      [-- the obj's documentation --]
```

Documentation structure:

```mtron
mtron> */m/space/qproc/docq/docs
```
Use this to understand unfamiliar instructions or explain them to users.

## Universal Examples (all environments)

```mtron
mtron> */sys/space/+                                  [-- list spaces                --]
==>stack::[
    pattern=><+/#>,
    q=>[
     refq::[pattern=>refq,post_read=>inst?#{*}<=#{?}(uri::T,#::T)],
     mintq::[pattern=>mintq,pre_write=>inst?#{*}<=#{?}(uri::T,#::T)],
     docq::[
      pattern=>docq,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      obj=>memspace::[pattern=><#>],
      inst=>instset::[pattern=><#>]]]]
==>memspace::[
    pattern=>/usr/#,
    q=>[
     typeq::[
      pattern=><T>,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      qless_write=>inst?#{*}<=#{?}(uri::T,#::T)],
     mintq::[pattern=>mintq,pre_write=>inst?#{*}<=#{?}(uri::T,#::T)],
     docq::[
      pattern=>docq,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      obj=>memspace::[pattern=><#>],
      inst=>instset::[pattern=><#>]],
     subq::[
      pattern=>subq,
      pre_read=>inst?#{*}<=#{?}(uri::T),
      pre_write=>inst?#{*}<=#{?}(uri::T,#::T),
      qless_write=>inst?#{*}<=#{?}(uri::T,#::T),
      obj=>[,]],
     incrq::[pattern=>incrq,pre_write=>inst?#{*}<=#{?}(uri::T,#::T)]]]@/sys/space/usr
==>fsspace::[pattern=>mtronfs:#,route=>[mtronfs:=><.metatron>]]@/sys/space/mtronfs
==>fsspace::[pattern=>local:#,route=>[local:=>/home/killswitch/],script=>!*boot/script]@/sys/space/fs
mtron> */sys/space/sys                                [-- sys space details          --]
mtron> */sys/env/HOME                                 [-- environmental variable     --]
==>'/home/killswitch'
mtron> */sys/env/+/.filter(not(<<.has(API))).take(5)  [-- first 5 environmental vars --]
==>/sys/env/GTK3_MODULES=>'xapp-gtk3-module'
==>/sys/env/BUN_INSTALL=>'/home/killswitch/.bun'
==>/sys/env/JAVA_HOME=>'/home/killswitch/.sdkman/candidates/java/current'
==>/sys/env/QT_IM_MODULE=>'ibus'
==>/sys/env/GNOME_DESKTOP_SESSION_ID=>'this-is-deprecated'
```
## Deployment-Specific (adapt to user's environment)

**First:** Check `*/sys/space/${space}.pattern` to discover the URI prefix.

```mtron
mtron> [-- SQL table (if pattern is "acme:#") --]
mtron> *acme:${table}.*(_).limit(10)
==>fail::[unable to locate inst-f of limit(10)@<2>]@/sys/fail/46
mtron> [-- Document collection (if pattern is "mongo:#") --]
mtron> *mongo:${collection}.*(_).limit(10)
==>fail::[unable to locate inst-f of limit(10)@<2>]@/sys/fail/50
mtron> [-- Graph vertices/edges (if pattern is "g:#") --]
mtron> *g:V.*(_).limit(10)
==>fail::[unable to locate inst-f of limit(10)@<2>]@/sys/fail/54
mtron> *g:E.*(_).limit(10)
==>fail::[unable to locate inst-f of limit(10)@<2>]@/sys/fail/58
mtron> [-- File system (if pattern is "local:#") --]
mtron> *<local:path/to/file>                    [-- Read file --]
mtron> *<local:path/#>                          [-- List recursively --]
```
**Don't hardcode prefixes!** Discover from `pattern` field, then use that prefix.