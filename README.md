# Governance for Archi

Governance 0.2.0 is an Archi 5.11.0 plug-in for authenticating with the user's local Git credentials, searching authorized GitHub repositories, cloning a selected repository into the user's persistent workspace, and opening its `source/design.archimate` file in Archi's native model tree and diagram editor. GitHub operations are read-only; editing and saving the model are performed by Archi on the local Git working copy.

## Compatibility

- Target Archi: **5.11.0**.
- Runtime Java: **21** (`JavaSE-21`).
- Local Git must be on `PATH`; its configured Git Credential Manager (or another existing credential helper) handles GitHub authentication.
- Docker Engine and Docker Compose are required to build. The pinned Eclipse Temurin 21 build image installs Git for temporary-repository integration tests and downloads the official, checksum-verified Archi 5.11.0 bundles for compilation. Neither Java, Maven nor Eclipse PDE is needed on the build host.

The build container is not the runtime. At runtime Archi invokes Git on the user's operating system and uses that operating system's Git and credential-manager configuration.

## Behavior and workspace

In Archi, open **Governance → GitHub Repositories…**, authenticate with **Connect**, and search an organization and topic (default `archimate`). Select a repository and choose **Open Model**. The context menu item **View on GitHub** remains available for the repository page.

Governance creates the persistent workspace beneath:

```text
${user.home}/.governance/workspace/github.com/{organization}/{repository}/
```

On Windows this is typically:

```text
%USERPROFILE%\.governance\workspace\github.com\repository-governance\SBB-009999\
```

The checkout is a real Git repository. Governance opens only `source/design.archimate`, using Archi's native `IEditorModelManager.INSTANCE.openModel(File)` API. The model retains its checkout path; Archi Save/Ctrl+S writes directly to that file, and Git can report the local change with `git status`. `manifest.json`, thumbnails, and other repository files are not managed or shown by Governance.

If the checkout already exists, Governance validates its Git root and `origin`, accepting equivalent GitHub HTTPS and SSH URLs, and reuses it without pull/fetch/reset/checkout/clean. Local edits are preserved. A directory that is not the expected working copy is not overwritten. A selected repository's clone URL must be HTTPS on `github.com` and match its organization/name.

Clones run off the SWT UI thread and report progress. A unique staging directory within the workspace is published as the permanent checkout only after Git reports success and its root and origin validate. Failed/cancelled staging data is deliberately not removed automatically; this avoids deleting user data. Such hidden partial staging folders do not become the final checkout, and a later attempt can use a new staging folder. The final workspace path is never a temporary directory.

If the same model file is already open, Governance compares normalized physical paths, selects/activates the existing model and does not reload it. Closing the Governance dialog cancels active work; after a successful native model open, the dialog closes automatically.

## Archi native API reference

The implementation follows the official Archi 5.11.0 source:

- [`OpenModelAction.java`](https://github.com/archimatetool/archi/blob/release_5.11.0/com.archimatetool.editor/src/com/archimatetool/editor/actions/OpenModelAction.java) invokes `IEditorModelManager.INSTANCE.openModel(file)` on the SWT thread.
- [`IEditorModelManager.java`](https://github.com/archimatetool/archi/blob/release_5.11.0/com.archimatetool.editor/src/com/archimatetool/editor/model/IEditorModelManager.java) exposes `openModel(File)`, `getModels()` and `isModelLoaded(File)`.
- [`EditorModelManager.java`](https://github.com/archimatetool/archi/blob/release_5.11.0/com.archimatetool.editor/src/com/archimatetool/editor/model/impl/EditorModelManager.java) loads the original file, assigns it to the model, registers it, and publishes the native Models-tree event.
- [`TreeModelView.java`](https://github.com/archimatetool/archi/blob/release_5.11.0/com.archimatetool.editor/src/com/archimatetool/editor/views/tree/TreeModelView.java) handles that event by refreshing and selecting the model. `EditorManager.openDiagramEditor(...)` is used to activate a native diagram editor when the model contains a diagram.

These APIs are exported by `com.archimatetool.editor` (with model APIs in `com.archimatetool.model`); both are declared in Governance's OSGi `Require-Bundle`.

## Authentication and security

Governance sends `protocol=https` and `host=github.com` to `git credential fill` through process stdin. Git Credential Manager remains responsible for any sign-in interaction. The credential output is captured and cleared from transient buffers; no credential is put in process arguments, URLs, preferences, logs, or the UI. The candidate is validated with `GET https://api.github.com/user` and used only for HTTPS requests to `api.github.com`.

The standalone Windows diagnostic `scripts/poc-github-auth.ps1` remains independent and is not used by the plug-in. Git credentials rejected by GitHub's REST API are reported as incompatible; no PAT prompt, OAuth app, GitHub App, or other fallback is attempted.

The workspace root and repository identity are validated against path traversal and redirected symbolic/junction paths. Repository names and clone URLs are checked before Git is invoked. Commands use `ProcessBuilder` argument vectors, not a shell. Governance does not execute scripts from cloned repositories or change Git configuration.

## Build, tests and package

From PowerShell at the repository root:

```powershell
docker compose build
docker compose run --rm builder
```

The builder compiles against the official Archi platform, runs the existing authentication/search tests plus workspace, path-safety, model-open de-duplication, and temporary local Git integration tests, then packages and validates the bundle. Tests use no real GitHub credentials or API responses; the Git integration tests create temporary local repositories and perform actual local Git commands.

Direct Docker use:

```powershell
docker build -f docker/Dockerfile -t archi-governance-builder .
docker run --rm -v "${PWD}:/workspace" archi-governance-builder
```

`scripts/test.sh` compiles and runs tests. `scripts/package.sh` also generates the JAR and `.archiplugin`, checks the OSGi manifest and `plugin.xml`, validates ZIP integrity, and verifies SHA-256 checksums for the generated artifacts in `dist/`. Packaging 0.2.0 does not delete or replace 0.1.0 artifacts.

## Install or update in Archi on Windows

1. Use **Archi 5.11.0** from the [official archive](https://www.archimatetool.com/archive/).
2. Build the plug-in and locate `dist/governance-0.2.0.archiplugin`.
3. In Archi, choose **Help → Manage Plug-ins… → Install New…** and select that file. For an existing Governance 0.1.0 installation, install the 0.2.0 package as the update.
4. Approve the requested restart.
5. Open **Governance → GitHub Repositories…**.

## Functional validation in Archi

1. Confirm local Git is detected. The displayed `user.name` and `user.email` are local Git identity, not proof of the GitHub account.
2. Enter `repository-governance` and keep topic `archimate`.
3. Choose **Connect** and complete any normal Git Credential Manager interaction. Confirm the authenticated GitHub login.
4. Confirm `SBB-009999` appears when the repository remains accessible to that account and has the topic.
5. Select it and choose **Open Model**. Confirm Governance reports progress, then closes; in Archi, confirm the model appears in the Models tree and a native diagram editor opens when diagrams exist.
6. Edit a harmless model property, save with Ctrl+S, and confirm that the checkout's `source/design.archimate` changes and `git -C "<checkout>" status --short` reports it. Do not commit or push as part of this check.
7. Press **Open Model** again from Governance after reconnecting/searching. Confirm the existing working copy is reused; if the model is already open, confirm it is selected/activated without a duplicate or reload.
8. Use the repository context menu **View on GitHub** to open its GitHub page.

An automated build does not prove these native UI steps. Report them as unverified until executed in a running Archi 5.11.0 installation.

## Errors and limitations

- Missing Git or configured helper: install/configure Git and its helper for the environment that launches Archi. Governance does not request or store a PAT.
- `/user` rejection: the helper's credential may be Git-only, expired or revoked. No fallback is attempted.
- GitHub 401/403/404/422/429, incomplete search, or more than 1,000 matches: Governance reports the failure and does not show partial search results.
- Existing incorrect working copy: it is left untouched and must be handled by the user.
- Failed/cancelled clone: unique staging contents are retained rather than automatically deleted; retry creates a new staging directory. Governance does not clean these up.
- Missing or invalid `source/design.archimate`: the dialog stays open and reports the failure.
- Model loading uses Archi's synchronous native model manager on the SWT UI thread, as does Archi's own File > Open operation. Git, repository validation, and workspace preparation run asynchronously.

## Artifacts

Generated in `dist/`:

- `governance-0.2.0.jar` — OSGi bundle.
- `governance-0.2.0.archiplugin` — installable Archi plug-in ZIP.
- `SHA256SUMS` — verified checksums (including any retained 0.1.0 deliverables).

The plugin version is defined by `VERSION`.
