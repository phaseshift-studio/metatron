# Dependabot triage — the 42 alerts vs. what metatron actually ships

> Date: 2026-11-13. Triaged at main `2166b739a`. **Status:** Marko approved steps 1–3 (Jackson option B) on
> 2026-11-13. They reached main inside `18cc23ed6` ("site build.", already pushed), and §7 has the verification.
> Step 4, refreshing the snapshot, is still his call. Agents never run git. The only builds were two `mvn dependency:tree` runs on ginger (no compile,
> no tests). The alert data was read with `gh` (account `okram`) after Marko logged it in, and it matches the
> reconstruction exactly (§1).

## Read first — the short version

1. **Dependabot's 42 alerts describe an old build, not today's.** All 42 sit on one manifest,
   `home/runner/work/metatron/metatron/pom.xml`. That is the dependency snapshot
   `advanced-security/maven-dependency-submission-action` uploaded from a CI runner on **2025-10-06**, when the pom
   still had langchain4j 1.3.0, hivemq-mqtt-client 1.3.10, TinkerPop 3.8.0, jlama-core 0.8.4 and gremlin-test. That CI
   step is now commented out (`.github/workflows/maven.yml:45-46`), so the snapshot has not changed since. None of its
   46 alerts (42 open, 4 dismissed) has ever closed. Of the 277 package versions the build resolves today, only 64 are
   in GitHub's dependency graph.
2. **Of the 17 packages behind the alerts, 16 are already fixed at today's resolved version or gone from the tree.**
   Guava is the exception. Its version has moved on (32.0.1-jre → 33.7.1-jre), but 33.7.1-jre is still affected by
   the same advisory.
3. **Today's build has 31 findings that Dependabot does not show: 0 critical, 12 high, 18 medium, 1 low.** They sit
   in three libraries:
   - **Netty 4.1.116 (21).** The test-only MQTT broker `moquette-broker` wins Maven's nearest-first mediation. It
     pulls `netty-codec-http`, `netty-codec-mqtt` and the epoll/kqueue transports down to 4.1.116, *at compile
     scope*, and so into the jar-with-dependencies.
   - **Jackson 3.1.4 (`tools.jackson`, 9).** It comes through the official MCP SDK, which no file in `src/`
     references.
   - **Guava 33.7.1-jre (1).** It comes through `janusgraph-driver`.
4. **All 31 are patch-level fixes, and no major upgrade is needed.** I dry-ran three `dependencyManagement` changes on
   ginger. The result resolves and scans to **0 findings** (OSV.dev and the GitHub Advisory Database). The blast
   radius is 38 artifact versions, all within their current lines, with nothing added or removed (§6).
5. **Order matters.** Land the pom pins (§5, steps 1–3) and their focused tests first, then refresh the snapshot
   (step 4). Refreshing first would close the 42 stale alerts and open ~31 real ones in their place.
6. **The recent grouped Dependabot update (merge `01c934d9f`) cannot close these alerts.** It bumped direct versions
   in pom.xml, but the alerts come from the uploaded snapshot, and only CI re-uploads that.

## 1. Method, and the `gh` caveat

- **First pass: the alert list was reconstructed.** `gh` lives at `/home/linuxbrew/.linuxbrew/bin/gh`, which is not on
  the agent shell's PATH, and it was not authenticated at first. The in-app browser isn't signed in to GitHub either.
  The reconstruction worked like this:
  - **The package set.** The repo is public, so its dependency-graph SBOM is readable anonymously at
    `GET /repos/phaseshift-studio/metatron/dependency-graph/sbom`. It lists 216 packages: 207 Maven, 6 GitHub
    Actions, and the repo itself. This is the package set Dependabot evaluates.
  - **The matching.** Every package@version was matched against the GitHub Advisory Database
    (`GET /advisories?ecosystem=maven&affects=…`, anonymous, 4 calls). Version ranges were compared with Maven's
    ordering, using a Python port of `ComparableVersion`.
  - **The cross-check.** OSV.dev (`POST /v1/querybatch`) independently returns the same 46 matches for the SBOM,
    one for one.
  - The result was **46 alerts (5 C / 21 H / 17 M / 3 L)**, against GitHub's **42 open (5 C / 20 H / 14 M / 3 L)**.
- **Second pass: the real list confirms the reconstruction exactly.** After Marko logged `gh` in (account `okram`),
  the API returned 82 alerts across all states: 42 open, 4 dismissed and 36 fixed.
  - All 42 open alerts are in the reconstruction, and the reconstruction predicts no alert that isn't there.
  - The other 4 are alerts #1–#4, which `okram` dismissed as *tolerable risk* on 2025-10-06:
    - `commons-beanutils` GHSA-wxr5-93ph-8wr9 (high)
    - `commons-configuration2` GHSA-xjp4-hw94-mvp5 and GHSA-9w38-p64v-xpmv (medium)
    - `commons-lang3` GHSA-j288-q9x7-2f5v (medium)

    All three packages are fixed in today's build.
- **The live tree comes from ginger:**
  `bin/metatron-remote --host ginger.local mvn -B -Dstyle.color=never -Dmaven.repo.local=/work/.build/m2/repository dependency:tree`
  (BUILD SUCCESS, 14.6 s). Keep the `-Dmaven.repo.local`. A raw command goes through `bin/metatron-docker build shell`,
  which does not add the cached repository the way the build verbs do. A bare `mvn` there re-downloads the whole
  dependency set into `/work/.m2`.
- **The two scanners agree on the live tree.** OSV and the GitHub-DB matcher agree on all 30 range matches. OSV
  adds Guava 33.7.1-jre; §5 step 3 explains why a strict range check misses it.

## 2. Why Dependabot is wrong about this repo: the frozen snapshot

The evidence:

- The SBOM's creators include `maven-dependency-tree-action`, the detector name of
  `advanced-security/maven-dependency-submission-action`.
- In `.github/workflows/maven.yml:45-46`, the "Update Dependency Graph" step that runs that action is commented out.
  The repo has no "Automatic Dependency Submission" workflow either; its only dynamic workflows are CodeQL and
  Dependabot Updates.
- The alerts record their manifest path, and it pins down the snapshot:
  - All 46 snapshot alerts (42 open, 4 dismissed) are on `home/runner/work/metatron/metatron/pom.xml`, which is where
    `pom.xml` sits on a GitHub Actions runner.
  - The first of them, #1–#4, were created on 2025-10-06, so that is when the snapshot was uploaded.
  - None of the 46 has ever been fixed.
- The other manifests behave normally:
  - GitHub's own reading of `pom.xml` produced 8 alerts, and all 8 were fixed as direct versions were bumped: rhino
    (2026-04-14), postgresql (2026-05-18), mysql-connector-j (2026-05-24) and mariadb-java-client (2026-10-31).
  - Two npm lockfiles, `package-lock.json` and `jdeploy-bundle/package-lock.json`, produced 28 alerts. All 28 were
    fixed, and neither file exists anymore.
- The SBOM's transitive edges describe an older pom:
  - `langchain4j 1.3.0`, `hivemq-mqtt-client 1.3.10`, `gremlin-core 3.8.0`, `gremlin-test 3.8.0`
  - `jlama-core 0.8.4`, `cucumber-* 7.21.1`, `logback 1.5.21`, `jsoup 1.21.2`

  The static parse of today's pom.xml sits next to this old data, so both logback 1.5.21 and 1.6.6 are listed. A
  static parse sees direct dependencies only.
- The net effect: today's tree has 277 package versions, GitHub's graph has 199 versioned Maven entries, and only
  **64 are in both**. Dependabot can't see 213 of today's package versions, including every vulnerable one in §4.

## 3. The 42 open alerts (and 4 dismissed), package by package

| # | Package (version on the snapshot) | Alerts | Alert # · GHSA (CVE) · severity | Patched in | Relationship / scope | Came in via (old snapshot) | In today's build | Status |
|---|---|---|---|---|---|---|---|---|
| 1 | `com.hubspot.jinjava:jinjava` 2.7.2 | 2 C | #14 GHSA-gjx9-j8f8-7j74 (CVE-2026-25526) C<br>#67 GHSA-m49c-g9wr-hv6v (CVE-2025-59340) C | 2.7.6 | transitive runtime | jlama-core@0.8.4 | — | removed from pom |
| 2 | `io.netty:netty-handler` 4.1.127.Final | 1 C / 3 H / 1 M | #73 GHSA-c4c3-7fpv-j4q5 (CVE-2026-75595) C<br>#46 GHSA-3qp7-7mw8-wx86 (CVE-2026-44249) H<br>#47 GHSA-x4gw-5cx5-pgmh (CVE-2026-45416) H<br>#48 GHSA-c653-97m9-rcg9 (CVE-2026-50010) H<br>#72 GHSA-fccg-mwvh-qqg4 (CVE-2026-75596) M | 4.1.137.Final | transitive runtime | hivemq-mqtt-client@1.3.10 | 4.1.140.Final | fixed in build |
| 3 | `org.apache.opennlp:opennlp-tools` 2.5.4 | 2 C / 1 H | #71 GHSA-cx4m-2p55-rw7j (CVE-2026-42027) C<br>#82 GHSA-4v8g-86x5-3vrc (CVE-2026-40682) C<br>#92 GHSA-659w-93r5-9j6m (CVE-2026-42440) H | 2.5.9 | transitive runtime | langchain4j@1.3.0 | 2.5.12 | fixed in build |
| 4 | `com.fasterxml.jackson.core:jackson-core` 2.19.2 | 3 H / 1 M | #63 GHSA-r7wm-3cxj-wff9 (CVE-2026-68494) H<br>#80 GHSA-p6pp-m3f8-5c89 (CVE-2026-89407) H<br>#81 GHSA-7hhh-6rmp-j9qf (CVE-2026-89425) H<br>#15 GHSA-72hv-8253-57qq (CVE-2026-18401) M | 2.21.7 | transitive runtime | langchain4j@1.3.0 | 2.22.4 | fixed in build |
| 5 | `com.fasterxml.jackson.core:jackson-databind` 2.19.2 | 5 H / 7 M | #50 GHSA-j3rv-43j4-c7qm (CVE-2026-54512) H<br>#51 GHSA-rmj7-2vxq-3g9f (CVE-2026-54513) H<br>#76 GHSA-q4xh-88c3-wmh7 (CVE-2026-68497) H<br>#78 GHSA-wv8q-qhhj-9h54 (CVE-2026-91776) H<br>#79 GHSA-cxp5-3px4-pw24 (CVE-2026-91777) H<br>#52 GHSA-hgj6-7826-r7m5 (CVE-2026-54514) M<br>#53 GHSA-5jmj-h7xm-6q6v (CVE-2026-54515) M<br>#62 GHSA-3pjw-73gf-8qr5 (CVE-2026-59888) M<br>#74 GHSA-vvgp-rfg2-7rr6 (CVE-2026-77310) M<br>#75 GHSA-wjgm-6hv5-3cvf (CVE-2026-19032) M<br>#77 GHSA-gx83-3vf8-gh7j (CVE-2026-83557) M<br>#84 GHSA-5gvw-p9qm-jgwh (CVE-2026-59889) M | 2.21.7 | transitive runtime | langchain4j@1.3.0 | 2.22.4 | fixed in build |
| 6 | `com.google.guava:guava` 32.0.1-jre | 1 H | #85 GHSA-xxph-c9ww-hj94 (CVE-2026-102554) H | 33.7.2 | transitive runtime | jlama-core@0.8.4 | 33.7.1-jre | **still vulnerable** (33.7.1-jre) |
| 7 | `com.nimbusds:nimbus-jose-jwt` 8.2.1 | 1 H / 1 M | #9 GHSA-gvpg-vgmx-xg6w (CVE-2023-52428) H<br>#11 GHSA-xwmg-2g98-w7v9 (CVE-2025-53864) M | 9.37.4 | transitive development | gremlin-test@3.8.0 → kerb-simplekdc@2.0.1 → kerb-client@2.0.1 → token-provider@2.0.1 | — | removed from pom |
| 8 | `commons-beanutils:commons-beanutils` 1.9.4 | none open, 1 dismissed | ~~#3 GHSA-wxr5-93ph-8wr9 (CVE-2025-48734) H~~ dismissed | 1.11.0 | transitive runtime | gremlin-core@3.8.0 | 1.11.0 | fixed in build |
| 9 | `commons-io:commons-io` 2.8.0 | 1 H | #10 GHSA-78wr-2p64-hpwj (CVE-2024-47554) H | 2.14.0 | transitive development | gremlin-test@3.8.0 | 2.15.1 | fixed in build |
| 10 | `io.netty:netty-codec` 4.1.127.Final | 2 H | #40 GHSA-mj4r-2hfc-f8p6 (CVE-2026-42583) H<br>#64 GHSA-558v-64gr-wgg4 (CVE-2026-59901) H | 4.1.136.Final | transitive runtime | hivemq-mqtt-client@1.3.10 | 4.1.140.Final | fixed in build |
| 11 | `net.minidev:json-smart` 2.3 | 1 H / 1 M | #8 GHSA-493p-pfq6-5258 (CVE-2023-1370) H<br>#7 GHSA-v528-7hrm-frqp (CVE-2021-27568) M | 2.4.9 | transitive development | gremlin-test@3.8.0 → kerb-simplekdc@2.0.1 → kerb-client@2.0.1 → token-provider@2.0.1 → nimbus-jose-jwt@8.2.1 | — | removed from pom |
| 12 | `org.apache.kerby:kerb-server` 2.0.1 | 1 H | #91 GHSA-262p-fj3f-xh7v (CVE-2026-57915) H | 2.1.2 | transitive development | gremlin-test@3.8.0 → kerb-simplekdc@2.0.1 → kerb-admin@2.0.1 | — | removed from pom |
| 13 | `org.jsoup:jsoup` 1.21.2 | 1 H / 1 M | #83 GHSA-65r4-943x-97jj (CVE-2026-75140) H<br>#69 GHSA-pmhh-3w7g-xqp8 (CVE-2026-71497) M | 1.23.2 | direct runtime | direct | 1.24.1 | fixed in build |
| 14 | `org.apache.commons:commons-configuration2` 2.9.0 | 1 M + 2 dismissed | ~~#1 GHSA-xjp4-hw94-mvp5 (CVE-2024-29131) M~~ dismissed<br>~~#2 GHSA-9w38-p64v-xpmv (CVE-2024-29133) M~~ dismissed<br>#45 GHSA-337m-mw94-2v6g (CVE-2026-45205) M | 2.15.0 | transitive runtime | gremlin-core@3.8.0 | 2.15.1 | fixed in build |
| 15 | `org.apache.commons:commons-lang3` 3.12.0 | none open, 1 dismissed | ~~#4 GHSA-j288-q9x7-2f5v (CVE-2025-48924) M~~ dismissed | 3.18.0 | transitive runtime | gremlin-core@3.8.0 | 3.22.0 | fixed in build |
| 16 | `org.apache.kerby:kerby-asn1` 2.0.1 | 1 M | #90 GHSA-x6vc-x4v4-hjhr (CVE-2026-57914) M | 2.1.2 | transitive development | gremlin-test@3.8.0 → kerb-simplekdc@2.0.1 → kerb-client@2.0.1 → kerb-core@2.0.1 → kerby-pkix@2.0.1 | — | removed from pom |
| 17 | `ch.qos.logback:logback-core` 1.5.21 | 3 L | #12 GHSA-qqpg-mvqg-649v (CVE-2026-1225) L<br>#54 GHSA-p47f-322f-whfh (CVE-2026-9828) L<br>#56 GHSA-jhq6-gfmj-v8fx (CVE-2026-10532) L | 1.5.34 | transitive runtime | logback-classic@1.5.21 | 1.6.6 | fixed in build |

Alert numbers are GitHub's. Struck-through alerts are dismissed.

What the Status column means:

- **Fixed in build:** today's resolved version is outside every vulnerable range in that row.
- **Removed from pom:** the package is not in today's tree. jlama-core is gone, and so is gremlin-test with its
  `kerb-simplekdc` chain.

Rows 1–3 hold all 5 criticals, and none of them ships today.

## 4. What today's build actually ships with: 31 findings Dependabot doesn't show

| # | Package @ resolved version | Scope | Findings | GHSA (CVE) severity | Patched in | Pulled in by (direct dep in pom.xml) |
|---|---|---|---|---|---|---|
| 1 | `com.google.guava:guava` 33.7.1-jre | compile | 1 H | GHSA-xxph-c9ww-hj94 (CVE-2026-102554) H | 33.7.2 | janusgraph-driver → guava |
| 2 | `io.netty:netty-codec-http` 4.1.116.Final | compile | 6 H / 11 M / 1 L | GHSA-57rv-r2g8-2cj3 (CVE-2026-42584) H<br>GHSA-6jqx-86gh-f27w (CVE-2026-55831) H<br>GHSA-f6hv-jmp6-3vwv (CVE-2026-42587) H<br>GHSA-jppx-w49h-x2qq (CVE-2026-56745) H<br>GHSA-mvh2-crg5-v77c (CVE-2026-55833) H<br>GHSA-pwqr-wmgm-9rr8 (CVE-2026-33870) H<br>GHSA-38f8-5428-x5cv (CVE-2026-42585) M<br>GHSA-4mp9-239f-g9hg (CVE-2026-59898) M<br>GHSA-6cqp-g7gg-8hr5 (CVE-2026-56746) M<br>GHSA-84h7-rjj3-6jx4 (CVE-2025-67735) M<br>GHSA-8c42-7qj2-3j46 (CVE-2026-59903) M<br>GHSA-gcjf-9mgh-3p7g (CVE-2026-59921) M<br>GHSA-hvcg-qmg6-jm4c (CVE-2026-50020) M<br>GHSA-m4cv-j2px-7723 (CVE-2026-42580) M<br>GHSA-q4f6-jm68-57ww (CVE-2026-59899) M<br>GHSA-v8h7-rr48-vmmv (CVE-2026-41417) M<br>GHSA-xxqh-mfjm-7mv9 (CVE-2026-42581) M<br>GHSA-fghv-69vj-qj49 (CVE-2025-58056) L | 4.1.137.Final | moquette-broker → netty-codec-http |
| 3 | `tools.jackson.core:jackson-core` 3.1.4 | compile | 2 H | GHSA-7hhh-6rmp-j9qf (CVE-2026-89425) H<br>GHSA-p6pp-m3f8-5c89 (CVE-2026-89407) H | 3.1.7 | mcp → mcp-json-jackson3 → jackson-databind → jackson-core |
| 4 | `tools.jackson.core:jackson-databind` 3.1.4 | compile | 3 H / 4 M | GHSA-cxp5-3px4-pw24 (CVE-2026-91777) H<br>GHSA-q4xh-88c3-wmh7 (CVE-2026-68497) H<br>GHSA-wv8q-qhhj-9h54 (CVE-2026-91776) H<br>GHSA-5gvw-p9qm-jgwh (CVE-2026-59889) M<br>GHSA-gx83-3vf8-gh7j (CVE-2026-83557) M<br>GHSA-vvgp-rfg2-7rr6 (CVE-2026-77310) M<br>GHSA-wjgm-6hv5-3cvf (CVE-2026-19032) M | 3.1.7 | mcp → mcp-json-jackson3 → jackson-databind |
| 5 | `io.netty:netty-codec-mqtt` 4.1.116.Final | compile | 1 M | GHSA-jfg9-48mv-9qgx (CVE-2026-44248) M | 4.1.133.Final | moquette-broker → netty-codec-mqtt |
| 6 | `io.netty:netty-transport-native-epoll` 4.1.116.Final | runtime | 1 M | GHSA-w573-9ffj-6ff9 (CVE-2026-45536) M | 4.1.135.Final | moquette-broker → netty-transport-native-epoll |
| 7 | `io.netty:netty-transport-native-kqueue` 4.1.116.Final | runtime | 1 M | GHSA-w573-9ffj-6ff9 (CVE-2026-45536) M | 4.1.135.Final | moquette-broker → netty-transport-native-kqueue |

### 4.1 Why Netty 4.1.116 is on the compile classpath

Maven picks the nearest version first, then widens the scope to the broadest path that reaches the artifact:

- **Version.** `moquette-broker` (test, direct) → `netty-codec-http 4.1.116` sits at depth 2. The other path,
  `janusgraph-driver → gremlin-driver → netty-all → netty-codec-http 4.1.137`, sits at depth 4. The nearest wins, so
  the version is **4.1.116**.
- **Scope.** The `netty-all` path is compile scope, so the winning version becomes **compile**.
- **Packaging.** `src/main/assembly/assembly.xml` packs `<scope>runtime</scope>`, which means compile plus runtime.
  So 4.1.116 classes ship in the jar-with-dependencies.

The same happens to `netty-codec-mqtt` and `netty-transport-classes-epoll/kqueue` (compile scope) and to the native
epoll/kqueue jars (runtime scope).

There is a correctness problem as well as a security one: the classpath carries **three Netty lines at once**.

- 4.1.116, from moquette.
- 4.1.137, from `netty-all` via gremlin-driver.
- 4.1.140, from `hivemq-mqtt-client 1.4.2`.

As a result, `netty-codec-http2 4.1.137` and gremlin-driver run against `netty-codec-http 4.1.116`. That is a latent
`NoSuchMethodError`. Aligning Netty fixes both problems.

### 4.2 Reachability in production

| Finding | Production path | Assessment |
|---|---|---|
| Netty HTTP, server side (request smuggling, CORS, SPDY, WebSocket V07/V08 handshaker, multipart, `HttpObjectDecoder`) | None. metatron serves HTTP with `com.sun.net.httpserver` (`isa/m/web/space/http/httpServer.java`) and WebSockets with Java-WebSocket (`isa/m/web/space/ws/wsServer.java`). `src/main` has no `io.netty` import. | Not reachable in production. Reachable in tests (moquette's broker). |
| Netty HTTP, client side (GHSA-57rv-r2g8-2cj3 response desync, GHSA-f6hv-jmp6-3vwv decompression bomb, GHSA-v8h7-rr48-vmmv start-line injection) | gremlin-driver's WebSocket client in `isa/m/db/grph/space/grphSpace.java` (handshake only) | Needs a hostile Gremlin Server. Low. |
| `netty-codec-mqtt` `MqttDecoder` | metatron's MQTT client is HiveMQ, which has its own codec | Tests only (moquette). |
| epoll/kqueue fd passing (GHSA-w573-9ffj-6ff9) | Needs file-descriptor passing over Unix domain sockets | Not reachable. |
| Jackson 3.1.4 | Only the MCP SDK uses it. No file in `src/` references `io.modelcontextprotocol`; the MCP client is langchain4j-mcp (`isa/m/ai/llm/type/mcp/mcpClient.java`, `isa/m/ai/llm/type/feature/ToolFeature.java`) | Not reachable today, but it ships in the jar. |
| Guava GHSA-xxph-c9ww-hj94 | Triggered only by Java native deserialization (`readObject`) of `CompactHashMap`/`CompactHashSet`/`MapMakerInternalMap`. `src/main` has no `ObjectInputStream` or `readObject`. | Not reachable. |

Bottom line: nothing is critical, and nothing is clearly exploitable in production. Fix them anyway, because every
item is a patch-level change and all of them ship in the jar.

## 5. Upgrade plan (critical-first)

All 5 criticals are in Dependabot's stale view (rows 1–3 of §3), and none ships today. They close at step 4 with no
pom change. For the live build the order is high-first.

Each step adds a `dependencyManagement` entry. Put the new entries at the top of that section, ahead of the existing
BOM imports, so they win any conflict.

### Step 1: Netty → 4.1.140.Final via `netty-bom` (patch; safe). Fixes 21: 6 H / 14 M / 1 L

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-bom</artifactId>
    <version>4.1.140.Final</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- It touches two instruction sets:
  - **grph.** gremlin-driver's JanusGraph WebSocket client moves `netty-all` from 4.1.137 to 4.1.140.
  - **mqtt.** The HiveMQ client is already on 4.1.140. The moquette test broker moves from 4.1.116 to 4.1.140, and
    `netty-tcnative` moves from 2.0.69 to 2.0.85 (test only).
- Upstream can't fix this. `moquette-broker 0.18.0` (from JitPack) is the newest release and still declares 4.1.116.
- A BOM beats `<exclusions>` on moquette, because it also stops the next library from skewing Netty.
- 4.1.140.Final is the newest 4.1.x. Netty 4.2.20.Final exists, but it starts a new line, gremlin-driver 3.8.x is
  built on 4.1, and nothing here needs it.

### Step 2: Jackson 3 (`tools.jackson`) 3.1.4 → 3.1.8 (patch; safe). Fixes 9: 5 H / 4 M

There are two options, and the choice is Marko's.

- **A. Drop `io.modelcontextprotocol.sdk:mcp`** (`pom.xml:437-442`).
  - Nothing imports it. An earlier session kept it as a protocol reference for Streamable HTTP; metatron implements
    MCP itself over `com.sun.net.httpserver`.
  - Removing it also takes these out of the jar: `mcp-core`, `mcp-json-jackson3`, `json-schema-validator`,
    `reactor-core`, `snakeyaml-engine` and `itu`.
- **B. Keep the SDK and pin the three artifacts explicitly.**
  - Do *not* import `tools.jackson:jackson-bom` instead. That BOM also manages
    `com.fasterxml.jackson.core:jackson-annotations` at **2.21**, which would downgrade the 2.22 that langchain4j's
    `jackson-databind 2.22.4` needs.
  - The pins:

```xml
<dependency><groupId>tools.jackson.core</groupId><artifactId>jackson-core</artifactId><version>3.1.8</version></dependency>
<dependency><groupId>tools.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>3.1.8</version></dependency>
<dependency><groupId>tools.jackson.dataformat</groupId><artifactId>jackson-dataformat-yaml</artifactId><version>3.1.8</version></dependency>
```

- An SDK upgrade can't bring a fixed Jackson. MCP SDK 2.1.0 is the latest stable release, and 2.2.0 is at M2.
- Instruction sets touched: **llm/mcp**, and only nominally, since no code uses the SDK.

### Step 3: Guava 33.7.1-jre → 33.7.2-jre (patch; safe). Fixes 1 H

```xml
<dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.7.2-jre</version></dependency>
```

- It touches **grph**, since `janusgraph-driver` brings in Guava.
- It also touches the rewriter. `algebra/rewrite/RewriterBuilder.java:21` imports `com.google.common.base.Objects`
  without declaring Guava; it rides on janusgraph-driver. An optional follow-up is to switch that import to
  `java.util.Objects`, so metatron's own code stops depending on Guava.
- Treat 33.7.1-jre as affected. The advisory's range is `<= 33.7.1` (fixed in 33.7.2), written without the `-jre`
  qualifier. Maven orders `33.7.1-jre` *above* `33.7.1`, so a strict range check lets it through. OSV's explicit
  version list flags it.

### Step 4: refresh GitHub's dependency snapshot (no pom change; CI or settings, Marko's call)

Do this after steps 1–3 land.

- **A (recommended): restore the CI step.** Uncomment the "Update Dependency Graph" step in
  `.github/workflows/maven.yml` (lines 45–46) and place it after the build step. The job needs
  `permissions: contents: write`. Running it in the same workflow and job most likely reuses the old snapshot's
  correlator, in which case the new snapshot replaces the old one.
- **B: turn on automatic submission.** Use Settings → Code security → Dependency graph → *Automatic dependency
  submission*. Its correlator differs, so it may sit alongside the old snapshot instead of replacing it.
- **Check the result either way.** Afterwards, look on the dependency graph page for leftovers such as
  `langchain4j 1.3.0` or `jlama-core 0.8.4`.
- **Expected outcome:** all 42 open alerts close as fixed, and no new ones open, because the proposed tree scans
  clean.

### Major upgrades

None are needed; everything above stays within its current line. These can wait:

- Netty 4.2.x.
- TinkerPop 4 (`gremlin-driver 4.0.0-beta.3`).
- Newer `janusgraph-driver` versions. Central only has timestamped builds, such as
  `1.3.0-20261028-235300.a59ae8d`.
- MCP SDK 2.2.0-M2.

These areas have no findings at today's versions:

| Area | Versions in today's build |
|---|---|
| **LangChain4j / llm** | 1.23.0; its `opennlp-tools 2.5.12` is past the fix |
| **Database drivers / tble, dcmnt** | mariadb 4.0.0, mysql-connector-j 26.10.0, sqlite-jdbc 3.54.1.0, mongodb 5.14.0, postgresql 42.7.14 (test) |
| **HiveMQ / mqtt** | 1.4.2, the latest release |
| **Docker / dckr** | docker-java 3.7.1, test scope via testcontainers 2.0.5 |
| **TinkerPop** | 3.8.2 |

### Not reachable, or cannot be fixed yet

- **Not in the build at all; these close at step 4:**
  - jinjava (2 C)
  - nimbus-jose-jwt (1 H, 1 M)
  - json-smart (1 H, 1 M)
  - kerb-server (1 H)
  - kerby-asn1 (1 M)
- **Already fixed in the build; these also close at step 4:**
  - netty-handler and netty-codec 4.1.127 (1 C, 5 H, 1 M)
  - opennlp-tools 2.5.4 (2 C, 1 H)
  - jackson 2.19.2 (8 H, 8 M)
  - commons-beanutils, commons-io, commons-configuration2, commons-lang3 (dismissed alerts #1–#4 are among these)
  - jsoup 1.21.2
  - logback-core 1.5.21
- **Real, but not reachable by metatron's code (fix them anyway in steps 1–3):**
  - the Guava `readObject` bug
  - netty-codec-mqtt
  - epoll/kqueue fd passing
  - Jackson 3
  - Netty's server-side HTTP advisories
- **Cannot be fixed yet:** nothing. There are two upstream gaps, and the pins cover both. moquette 0.18.0 still
  declares Netty 4.1.116, and the MCP SDK has no newer stable release.

## 6. Dry run: the proposed pins, resolved on ginger

I tested the plan without touching pom.xml.

1. I copied pom.xml to a gitignored `.pom-dependabot-proposed.xml` and added the entries from all three steps,
   using option B for step 2.
2. I resolved it on ginger with `mvn -f .pom-dependabot-proposed.xml dependency:tree` (BUILD SUCCESS, 4.2 s).
3. I deleted the file.

The results:

- Every `io.netty` module is at 4.1.140.Final, a single line.
- `jackson-annotations` stays at 2.22, so nothing is downgraded.
- The tree has 285 nodes before and after; nothing is added or removed.
- **The scan finds 0 issues**, both with the OSV.dev querybatch and with the GitHub-DB matcher.

The blast radius is these 38 artifacts:

| From | To | Scope | Artifacts |
|---|---|---|---|
| 2.0.69.Final | 2.0.85.Final | test | netty-tcnative-boringssl-static:linux-aarch_64, netty-tcnative-boringssl-static:linux-x86_64, netty-tcnative-boringssl-static:osx-aarch_64, netty-tcnative-boringssl-static:osx-x86_64, netty-tcnative-boringssl-static:windows-x86_64, netty-tcnative-classes |
| 3.1.4 | 3.1.8 | compile | jackson-core, jackson-databind, jackson-dataformat-yaml |
| 33.7.1-jre | 33.7.2-jre | compile | guava |
| 4.1.116.Final | 4.1.140.Final | compile | netty-codec-http, netty-codec-mqtt, netty-transport-classes-epoll, netty-transport-classes-kqueue |
| 4.1.116.Final | 4.1.140.Final | runtime | netty-transport-native-epoll:linux-aarch_64, netty-transport-native-epoll:linux-x86_64, netty-transport-native-kqueue:osx-aarch_64, netty-transport-native-kqueue:osx-x86_64 |
| 4.1.137.Final | 4.1.140.Final | compile | netty-all, netty-codec-dns, netty-codec-haproxy, netty-codec-http2, netty-codec-memcache, netty-codec-redis, netty-codec-smtp, netty-codec-socks, netty-codec-stomp, netty-codec-xml, netty-handler-proxy, netty-handler-ssl-ocsp, netty-resolver-dns, netty-resolver-dns-classes-macos, netty-transport-rxtx, netty-transport-sctp, netty-transport-udt |
| 4.1.137.Final | 4.1.140.Final | runtime | netty-resolver-dns-native-macos:osx-aarch_64, netty-resolver-dns-native-macos:osx-x86_64, netty-transport-native-epoll:linux-riscv64 |

## 7. Verification after approval (on ginger, one run at a time)

1. **Tree.** Run
   `bin/metatron-remote --host ginger.local mvn -B -Dstyle.color=never -Dmaven.repo.local=/work/.build/m2/repository dependency:tree`
   and expect the versions from §6.
2. **Focused tests.** Read the `Tests run:` line, not the exit code.
   - **mqtt** (moquette's broker on the new Netty): `mqttSpaceTest,mqttSpaceReconnectTest,mqttServerTest,MqttQueueTest,MqttCodecTest`
   - **grph** (gremlin-driver on 4.1.140, plus Guava): `TinkerGrphSpaceTest,JanusGrphSpaceTest`. JanusGraph runs in
     testcontainers, and TinkerGraph failures are on the accepted-failure list.
   - **mcp**, only if step 2 uses option A: `mcp_mtronTest,mcpHttpHandlerSseTest,mcpMessageServerTest,mcp_wsHandlerTest,mcp_stdioHandlerTest,mcpEmulatorTest`.
     The compile itself proves that nothing references the SDK.
   - For example:
     `bin/metatron-remote --host ginger.local build test -Dtest=mqttSpaceTest,mqttSpaceReconnectTest,mqttServerTest,MqttQueueTest,MqttCodecTest,TinkerGrphSpaceTest,JanusGrphSpaceTest`
3. **Package.** Run `bin/metatron-remote --host ginger.local build`, then check that the jar-with-dependencies
   carries Netty 4.1.140. For example, read `META-INF/maven/io.netty/netty-codec-http/pom.properties` inside the jar,
   if the assembly keeps it.
4. **Rescan.** Run the scanners in `.docker-target/dependabot-triage-2026-11-13/` against the new tree and expect 0.

### Result (2026-11-13, steps 1–3 applied to pom.xml)

- **Tree.** The real pom resolves to exactly the dry-run tree: 285 nodes, every version and scope identical. OSV.dev
  reports **0 findings** on it.
- **Tests.** One `build test` run on ginger covered 7 classes, 855 tests: **0 failures**, 15 errors, 73 skipped.

| Class | Tests run | Result |
|---|---|---|
| `JanusGrphSpaceTest` (gremlin-driver's WebSocket client against a JanusGraph container) | 159 | pass (25 skipped) |
| `TinkerGrphSpaceTest` | 203 | pass (29 skipped) |
| `mqttSpaceTest` (moquette broker and HiveMQ client on Netty 4.1.140) | 452 | pass (19 skipped) |
| `mqttSpaceReconnectTest` | 5 | pass |
| `MqttCodecTest` | 18 | pass |
| `MqttQueueTest` | 3 | pass |
| `mqttServerTest` | 15 | **15 errors, not caused by this change** |

All 15 `mqttServerTest` errors are the same metatron type check: "obj does not match `/m/iot/mqtt/mqtt_server::T` …
predicate apply failed". It is thrown in `Listener.<init>`, reached from `mqttServer.java:154` through
`Obj$Helper.objTypeCheck`, before any network code runs.

To rule out the dependency change, I re-ran `mqttServerTest` against the **previous** pom: a gitignored copy with
these additions removed, since deleted. It fails identically, 15 of 15. So the failure is already on main at
`2166b739a`.

The cause turned out to be the 2026-11-12 `Listener` refactor, not this morning's merges. The refactor made the
constructor type-check the server's whole construction map, and a server with no routes put an empty `route` into
it, which `listener::T` forbids. The same fault broke `NetworkIdentityTest`, `ListenerBindTest` and
`httpsServerTest`. It is fixed in a separate commit, and with that fix `mqttServerTest` passes 15/15.

The package step (item 3) was not run. The jar-with-dependencies uses the same resolution as the tree above.

## 8. Appendix

### Re-pull the alerts

`gh` is logged in as `okram`, so this lists the open alerts:

```bash
/home/linuxbrew/.linuxbrew/bin/gh api 'repos/phaseshift-studio/metatron/dependabot/alerts?state=open&per_page=100' --paginate --jq '.[] | [.number, .security_advisory.severity, .dependency.package.name, .security_advisory.ghsa_id, .dependency.manifest_path, .dependency.relationship] | @tsv'
```

After step 4, it should show no open alerts on `home/runner/work/metatron/metatron/pom.xml`. Use
`state=auto_dismissed,dismissed,fixed,open` to see the full history.

### Reproduce

The scripts and inputs are kept in `.docker-target/dependabot-triage-2026-11-13/`, which is gitignored:

- `mvnver.py` implements Maven version ordering and runs its self-test with `python3 mvnver.py`.
- `scan.py` parses the tree, diffs it against the SBOM, and fetches the candidate advisories.
- `match.py` matches advisories against both views.
- `verify.py` diffs and scans the proposed tree.
- `tables.py` builds the tables in this report.
- The inputs are `metatron-sbom.json`, `metatron-deptree.txt` and `metatron-deptree-proposed.txt`.
- `alerts-all.json` holds all 82 Dependabot alerts as pulled on 2026-11-13.

The scripts read their inputs from `/tmp`, so copy them back there before re-running.
