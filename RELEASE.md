# Release Process

## Snapshot Release

Snapshot releases publish to npm under the `snapshot` tag and create a
GitHub pre-release. Users install with `npm install -g metatron-vm@snapshot`.

**Trigger:** push to the `jdeploy-snapshot` branch.

### Steps

1.  Ensure your changes are committed on `main` (or merged to `main`).

2.  Merge `main` into `jdeploy-snapshot`:

    ```bash
    git checkout jdeploy-snapshot
    git merge main
    ```

    Resolve conflicts if any. The `jdeploy-snapshot` branch tracks the
    releasable tip — it should be a fast-forward or clean merge from `main`.

3.  Push to trigger the workflow:

    ```bash
    git push origin refs/heads/jdeploy-snapshot:refs/heads/jdeploy-snapshot
    ```

    The fully-qualified ref is needed because there is also a
    `refs/tags/jdeploy-snapshot` tag that makes the short name ambiguous.

4.  Monitor the action:
    `https://github.com/phaseshift-studio/metatron/actions/workflows/jdeploy.yml`

    The workflow (`.github/workflows/jdeploy.yml`) does three things:

    | Step | What happens |
    |------|-------------|
    | Maven build | `mvn package -DskipTests` with JDK 25 Temurin |
    | jDeploy bundle | `shannah/jdeploy@master` creates native installers and a GitHub release |
    | npm publish | Extracts the `.tgz`, versions it as `0.0.0-jdeploy-snapshot.<run>`, publishes with `--tag snapshot` |

5.  Once green, verify the install works from npm:

    ```bash
    npm install -g metatron-vm@snapshot
    metatron-vm
    ```

6.  Switch back to `main` when done:

    ```bash
    git checkout main
    ```

## Versioned Release

Versioned releases publish to npm under the `latest` tag and create a
full GitHub release. Users install with `npm install -g metatron-vm`.

**Trigger:** push a tag matching `v*` (e.g. `v1.0.0`).

### Steps

1.  Ensure `main` is green and all snapshot testing is complete.

2.  Tag the release commit:

    ```bash
    git checkout main
    git tag -a v1.0.0 -m "Release v1.0.0"
    ```

    Use [semantic versioning](https://semver.org). The leading `v` is
    required by the workflow trigger (`v*`).

3.  Push the tag:

    ```bash
    git push origin v1.0.0
    ```

4.  Monitor the action — the same `jdeploy.yml` workflow runs. The
    npm publish step detects the tag trigger and uses the tag version
    (with the `v` prefix stripped) and the `latest` tag.

5.  After the workflow completes, verify:

    ```bash
    npm install -g metatron-vm
    metatron-vm
    ```

    And check the GitHub release at
    `https://github.com/phaseshift-studio/metatron/releases`.

## Quick Reference

| What | Command |
|------|---------|
| Snapshot release | `git checkout jdeploy-snapshot && git merge main && git push origin refs/heads/jdeploy-snapshot:refs/heads/jdeploy-snapshot` |
| Version tag | `git tag -a vX.Y.Z -m "Release vX.Y.Z" && git push origin vX.Y.Z` |
| Check action | `https://github.com/phaseshift-studio/metatron/actions/workflows/jdeploy.yml` |
| Install snapshot | `npm install -g metatron-vm@snapshot` |
| Install release | `npm install -g metatron-vm` (after versioned publish is wired up) |
| Local install test | `mvn clean package -DskipTests && npx jdeploy install` |
