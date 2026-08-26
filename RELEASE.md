# Release Process

metatron ships through a single GitHub-backed channel:
`curl -fsSL https://metatron.phaseshift.studio/install.sh | bash`.

There are no native installers, no npm package, no `.deb` — the installer clones the
repo, builds the uber-jar, and bundles it for the runtime (`lib/metatron.jar`). The
container image is published to GHCR. Both ship from the same uber-jar, so a release
is simply getting code to `main` (rolling) or tagging it (versioned).

## Channels

| What                              | Trigger                                                          | Where                                                             |
|-----------------------------------|------------------------------------------------------------------|-------------------------------------------------------------------|
| Source install                    | any push to `main`                                               | `https://metatron.phaseshift.studio/install.sh` (served from `docs/website/install.sh`, synced from `dist/curl/install.sh` by the Maven build) |
| Container image                   | push to `main` / `v*` tags / `workflow_dispatch`                | `ghcr.io/phaseshift-studio/metatron` (`main` tag; `v*` also tags `latest`) |

## Rolling (`main`)

`main` is the rolling release. Pushes rebuild the container (`:main` tag) and the served
`install.sh` always reflects the latest commit. For most users:

```bash
curl -fsSL https://metatron.phaseshift.studio/install.sh | bash
```

## Versioned release

1. Ensure `main` is green (`.github/workflows/maven.yml`).
2. Tag and push:

   ```bash
   git tag -a v1.0.0 -m "Release v1.0.0"
   git push origin v1.0.0
   ```

   The `v*` tag triggers `.github/workflows/docker.yml`, which builds the image and pushes
   `ghcr.io/phaseshift-studio/metatron:v1.0.0` and `:latest`.

3. Verify the image:

   ```bash
   docker run --rm -it -p 8555:8555 -p 8777:8777 \
     -v /var/run/docker.sock:/var/run/docker.sock \
     ghcr.io/phaseshift-studio/metatron:v1.0.0
   ```

## Custom deployments

The container is boot-agnostic. Deployments (Home Assistant, web consoles, servers, agents)
are defined by a boot file that pulls in what it needs via `dckrspace::T` (sqlite, postgres,
janusgraph, ...). Mount a boot and pass its boot args:

```bash
docker run -d --name metatron \
  -p 8555:8555 -p 8777:8777 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$(pwd)/my.boot.mtron:/app/boot/deploy.mtron:ro" \
  ghcr.io/phaseshift-studio/metatron:main \
  "[boot=><boot/deploy.mtron>,log=>info]"
```

See `dist/docker/README.md` for the image contract.
