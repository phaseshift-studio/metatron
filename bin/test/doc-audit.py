#!/usr/bin/env python3
"""Audit a maven site-build log for the MtronPreprocessor "docs are buggy"
(fail::-without-[ERROR] modifier) blocks and tabulate them per skill doc,
categorising each so the alignment pass can focus on the real mtron problems
rather than build-environment artifacts (missing docker CLI / no live MCP server)."""
import re, sys, collections

log = sys.argv[1]
txt = open(log, encoding="utf-8", errors="replace").read().splitlines()

DOC_MARK  = re.compile(r"\bprocess(?:ing)?[^\w]*(\S+\.(?:adoc|md|MD|AdOc))", re.I)
DONE_MARK = re.compile(r"processed\s+(\S+\.md)", re.I)
BUG_MARK  = re.compile(r"no \[ERROR\] modifier in code block \(docs are buggy\): fail::\[(.*)")
FAILREF   = re.compile(r"@/sys/fail/(\d+)")

def clean(doc: str) -> str:
    return doc.rstrip(".").lower()

def classify(msg: str) -> str:
    m = msg
    # environmental — the isolated build container has no backend running; these
    # examples are FINE in a real env (a server running / docker present), so they
    # are not "align the snippet" work.
    if "docker cli not found" in m:                       return "ENV        (no docker CLI in build)"
    if "Mongo" in m or "mongodb" in m or "MongoSocketOpen" in m: return "ENV        (no MongoDB at localhost:27017)"
    if "McpClient" in m or "mcp_client" in m or "protocol detection" in m: return "ENV        (no live MCP server)"
    if "no active space supports" in m:                   return "ENV        (space not booted in docs run)"
    # genuinely actionable — these are what "align the mtron" targets
    if "NullPointerException" in m or "is null" in m and "get1" in m or "isPoly" in m:
                                                              return "BUG*       (example hits a code NPE — real bug the doc exposes)"
    if "parse error" in m:                                return "SYNTAX     (malformed mtron — fix the expression)"
    if "unable to convert" in m or ("is not a" in m and "::T" in m) or "requires " in m and "got " in m:
                                                              return "TYPE-FIT   (fails a refinement — mark [ERROR] or fix)"
    if "cannot be cast" in m:                             return "RUNTIME    (class-cast failure in the example)"
    if "unable to locate" in m:                           return "RESOLVE    (instruction not found — unknown inst or bug)"
    if "unable to construct" in m:                        return "RESOLVE    (cannot construct — check constructor args)"
    return "REVIEW     (unclassified — look at it)"

cur = None
rows = []   # (doc, category, failref)
for i, ln in enumerate(txt):
    dm = DOC_MARK.search(ln)
    if dm:
        cur = clean(dm.group(1))
        continue
    done = DONE_MARK.search(ln)
    if done:
        cur = clean(done.group(1))
        continue
    bm = BUG_MARK.search(ln)
    if bm:
        # find the fail:: closing ref within the next ~8 lines
        ref = ""
        for j in range(i, min(i + 10, len(txt))):
            fr = FAILREF.search(txt[j])
            if fr:
                ref = "/sys/fail/" + fr.group(1)
                break
        rows.append((cur or "(unknown)", classify(bm.group(1)), ref))

if not rows:
    print("no 'docs are buggy' blocks found in", log); sys.exit(0)

by_doc = collections.defaultdict(lambda: collections.Counter())
for doc, cat, ref in rows:
    by_doc[doc][cat] += 1

print(f"TOTAL failing/unmarked blocks: {len(rows)}   across {len(by_doc)} docs\n" + "=" * 80)
for doc in sorted(by_doc):
    print(f"\n### {doc}")
    for cat, n in sorted(by_doc[doc].items(), key=lambda kv: (-kv[1], kv[0])):
        print(f"    {n:>3}  {cat}")

# actionable = non-environmental = the alignment work
def actable(cat): return "ENV" not in cat
print("\n" + "=" * 80)
print("ACTIONABLE (non-environmental) — the ones to actually align:")
act = [(d, c, r) for (d, c, r) in rows if actable(c)]
env = [(d, c, r) for (d, c, r) in rows if not actable(c)]
print(f"  {len(act)} actionable   vs   {len(env)} environmental (docker/MCP — docs are fine in a real env)")
per = collections.defaultdict(list)
for d, c, r in act:
    per[d].append((c, r))
for d in sorted(per):
    print(f"\n### {d}  ({len(per[d])} to align)")
    for c, r in per[d]:
        print(f"    {r:>16}   {c.split('   ')[0]}")
