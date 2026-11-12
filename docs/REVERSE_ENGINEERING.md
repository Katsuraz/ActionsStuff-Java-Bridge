# Reusable Minecraft reverse-engineering workflow

**Workflow:** [.github/workflows/reverse-engineer.yml](../.github/workflows/reverse-engineer.yml)

This workflow performs **static** analysis of a Java/Fabric mod JAR. It uses
[CreeperHost ModLens](https://github.com/CreeperHost/modlens-mcp),
[Vineflower](https://github.com/Vineflower/vineflower) 1.12.0, and
the repository's existing [Fabric Loom](https://github.com/FabricMC/fabric-loom) Gradle build.

## Trigger directly from ChatGPT (no manual GitHub Actions button)

When you give ChatGPT a **public direct JAR URL** and ask for analysis, the
connected GitHub integration can update [`.github/rea-request.json`](../.github/rea-request.json)
on the repository's `main` branch. A push modifying that file starts this
workflow automatically. ChatGPT can then retrieve the resulting GitHub Actions
logs and analysis artifacts through the connected GitHub integration.

Request format (change `request_id` each time, even when analyzing the same JAR):

~~~json
{
  "request_id": "example-001",
  "jar_url": "https://github.com/OWNER/REPO/releases/download/TAG/mod.jar",
  "gradle_mode": "validate"
}
~~~

Use `"jar_url": ""` for a self-generated smoke-test fixture. Valid Gradle modes
are `validate`, `build`, and `skip`. The request is accepted **only** for
the exact request-file change in a push to the main branch of this repository;
unrelated pushes will not automatically replay an old request. URL downloads
still have the host and size restrictions in the inspection tool. This is an
automated GitHub Actions workflow, **not a locally installed MCP plugin**.
Because this repository is public, don't submit private JAR URLs or secrets.

## How to run

1. Open the repository **Actions** tab.
2. Select **Minecraft Reverse Engineering**, then **Run workflow**.
3. Leave **jar_url** empty for a small synthetic Fabric JAR smoke test.
4. To analyze a real mod, enter a *public direct HTTPS JAR download URL* in **jar_url**.
   Supported download hosts: GitHub Releases, Modrinth CDN, Maven Central,
   Fabric Maven, and GitHub release-asset hosts. Max download: 60 MiB.
   Do not submit confidential or unlicensed redistributable material to public logs/artifacts.
5. Choose **gradle_mode**:
   - **validate**: resolve this repository's existing Fabric Loom plugin via Gradle help (default).
   - **build**: execute the repository's Gradle build (does *not* prove in-game functionality).
   - **skip**: avoid Gradle.
6. Open the finished workflow run and download **minecraft-reverse-engineering-report**.

The artifact contains a JAR inventory, parsed mod metadata, discovered Mixin
configuration names, classes list, decompiled Java, Vineflower output log,
ModLens metadata/index results, and a Fabric Loom Gradle log.

For reuse from another repository once this workflow is on the default branch:

~~~yaml
jobs:
  reverse-engineer:
    uses: Katsuraz/ActionsStuff-Java-Bridge/.github/workflows/reverse-engineer.yml@main
    with:
      jar_url: https://github.com/OWNER/REPO/releases/download/TAG/mod.jar
      gradle_mode: skip
~~~

For another project's **own** Loom build, copy/adapt the workflow into that
project instead; a reusable workflow checks out the *calling* repository and
will run its Gradle scripts if validation/build mode is enabled.

## Important verification boundaries

- Passing tests means the pipeline ran correctly. It does **not** prove that
  mod Mixins apply at runtime or that Minecraft starts successfully.
- Decompiled Java is **approximate reconstructed source**, not necessarily the
  original source code.
- Do not commit original third-party mod JARs or extracted assets without
  redistribution permission. Analysis runs should use artifacts/URLs you may
  lawfully inspect, with only transient workflow artifacts retained for 7 days.
- The workflow executes repository Gradle build scripts when enabled. Only
  run it on trusted repository code.
- ModLens is a **CLI invoked in GitHub Actions**, not a new MCP connection
  inside ChatGPT.
- Node.js 24, JDK 21, Vineflower 1.12.0, Python 3.12, and Gradle 8.7
  are supplied by the runner. ModLens is fetched from pinned commit
  `ba2020c226b51858d157bc943ed13f1c6b578514` and built on each run.
  The upstream dependency audit reported known vulnerabilities during CI;
  review upstream fixes before processing untrusted archives.
- Default runs generate a small self-contained test JAR, so no outside mod
  JAR or secret is required to check the pipeline.

## Troubleshooting

If the Actions workflow doesn't appear on the default branch, merge the
reviewed PR first. If the run fails, inspect the failed step and its logs;
the artifact is uploaded with `if: always()` when possible.
For private JARs, avoid publicly hosted downloads and copy this workflow
into a private repository with an appropriate private artifact mechanism.
