# GitHub Workspace for Archi

GitHub Workspace is a native SWT/JFace plug-in for Archi **5.11.0**. It reuses the existing Governance authentication, GitHub REST, MVC, asynchronous process, and native model-opening services. Its first release resets the product version to **0.1.0** and adds repository browsing, thumbnails, configurable preferences, and independent local plan branches.

## Compatibility and build

- Archi 5.11.0; Java 21 at runtime.
- Git and its configured Git Credential Manager/credential helper must be available to the operating-system process that launches Archi.
- Docker Engine and Docker Compose build against the official, checksum-verified Archi 5.11.0 bundles. No local Java, Maven, or Eclipse PDE installation is required.

```powershell
docker compose build
docker compose run --rm builder
```

The Docker image installs Git for temporary local-repository integration tests. It is not used at runtime; Archi invokes the user's local Git installation.

## Install and replace Governance

Governance and GitHub Workspace use different OSGi bundle identities. To avoid keeping both products installed:

1. In Archi, choose **Help → Manage Plug-ins…**.
2. Select **Governance** and choose **Uninstall**; approve the restart.
3. Build GitHub Workspace, then choose **Help → Manage Plug-ins… → Install New…** and select `dist/github-workspace-0.1.0.archiplugin`.
4. Restart Archi and open **File → GitHub Workspace → Browse Repositories...**.

The old on-disk directory `~/.governance/workspace/` is only detected and reported. It is never moved, migrated, deleted, or modified.

## Preferences

Open **Preferences → GitHub Workspace**. Settings use the native Archi/Eclipse preference store and survive restarts.

### Repository Discovery

- **Organization**: defaults to `repository-governance`.
- **Repository Topic**: defaults to `archimate`.

The catalog searches GitHub using `org:{organization} topic:{topic}` and does not expose these settings as editable catalog fields.

### Local Workspace

The default root is resolved from the user's profile:

```text
${user.home}/.github-workspace/workspace/
```

On Windows this is `%USERPROFILE%\.github-workspace\workspace\`. Clear **Use default location** to enter or browse to a custom writable directory. Applying preferences validates that it exists or can be created and is writable. Changing the root affects future operations only: no repositories are moved or overwritten, and open models remain associated with their original physical files.

### Authentication

Git installation/version, credential-helper configuration, and the last authenticated GitHub account are read-only status. Authentication uses the existing `git credential fill` flow. No token is requested by the UI or stored in preferences.

The preference page uses Archi's native **Apply**, **Apply and Close**, **Cancel**, and **Restore Defaults** actions.

## Browse repositories

The File-menu contribution was checked against Archi 5.11.0's [`ArchiActionBarAdvisor.java`](https://github.com/archimatetool/archi/blob/release_5.11.0/com.archimatetool.editor/src/com/archimatetool/editor/actions/ArchiActionBarAdvisor.java): Archi creates the File menu using `IWorkbenchActionConstants.M_FILE`, places `FILE_START` before New, and terminates it with `FILE_END`. GitHub Workspace contributes immediately after `FILE_START`, so it appears first, with the supplied GitHub icon on the submenu and the supplied catalogs icon on Browse Repositories. The preference page uses Eclipse's `org.eclipse.ui.preferencePages` extension point, which Archi 5.11.0 itself declares in its `plugin.xml`.

Connect to GitHub in the catalog. Search uses the saved organization and topic, GitHub REST API pagination, and existing completeness/rate-limit/error handling. The catalog includes a local text filter, Refresh, Preferences, connection status, an empty state, and scrollable repository cards.

Cards show repository name, description, visibility, default branch, local plan checkouts, and a **Clone** action. A thumbnail is loaded asynchronously only as its card enters the viewport. GitHub's authenticated REST Contents API is used; the first PNG in `thumbnails/` by alphabetical filename is selected. Non-PNG files are ignored, absent thumbnails retain a placeholder, PNG dimensions are bounded, aspect ratio is preserved, and a bounded in-memory cache avoids repeat requests. No repository is cloned just to display an image. `manifest.json` and thumbnails are not generated or changed.

## Clone and open an architecture plan

Choose **Clone** and enter a Plan ID matching `D-[0-9]+` (for example `D-09999`). The destination is:

```text
{workspace-root}/github.com/{organization}/{repository}/{planId}/
```

For example:

```text
%USERPROFILE%\.github-workspace\workspace\github.com\
  repository-governance\SBB-009999\D-09999\
```

Git also validates the plan with `git check-ref-format --branch`. Cloning uses Git's HTTPS `clone_url` into a unique staging folder within the repository workspace. On a new checkout, GitHub Workspace reads the repository's actual default branch and checks whether the requested Plan ID exists remotely:

- If it does not exist, it creates a local plan branch from the default branch.
- If it exists, it fetches and checks out that branch with its `origin/{PlanID}` tracking branch.
- It does not push the new branch, commit changes, or alter the remote default branch.

The staging checkout is validated before being published at the final Plan ID path. A failed or cancelled operation never publishes a valid-looking final checkout; incomplete staging data is retained rather than automatically deleted.

When the same plan already exists locally, GitHub Workspace validates its Git root, `origin`, and active branch, then reuses it without changing branch or running `pull`, `fetch`, `reset`, `clean`, `stash`, `checkout`, or `switch`. Local edits are preserved. A mismatched repository or active branch is reported and left untouched.

Repositories with local plans show a separate **Open Model** action for each validated Plan ID. **Clone** remains available to create another plan. Plans are separate Git working copies; Git worktrees are not used.

## Native Archi model handling

Only `source/design.archimate` is opened, through Archi's `IEditorModelManager.INSTANCE.openModel(File)` API. It remains at its physical path inside the plan checkout, so Archi Save/Ctrl+S writes directly to the versioned model. Git can report those changes with `git status`.

Already-open models are deduplicated by physical file path, not by the model's internal identifier. Archi 5.11.0's official `EditorModelManager` source confirms that it locates an existing model by comparing the requested `File` with each open model's `getFile()`. Therefore, identical internal IDs in different plan directories are not treated as the same file by this manager; each distinct checkout path may be open independently. This source inspection is not a substitute for checking the behavior in a running Archi installation.

## Authentication and security

Git Credential Manager remains responsible for sign-in. Credentials are passed to GitHub API requests only in the HTTPS Authorization header and are not put in command arguments, URLs, logs, or files. New clones use validated HTTPS `github.com` URLs; process calls use argument vectors, never a shell.

Organization, repository, Plan ID, workspace, and model paths are validated. Symbolic links and redirected workspace paths are rejected, and destinations are constrained to the configured workspace. GitHub Workspace does not change Git configuration, execute repository scripts, alter `manifest.json` or thumbnails, or delete local checkouts.

## Automated tests and generated files

`docker compose run --rm builder` compiles, runs all preserved authentication/search tests plus workspace, security, plan branch, reuse, thumbnail, and local Git integration tests, validates the OSGi bundle and installer archive, and checks SHA-256 sums. Tests use temporary repositories and fake GitHub API responses; they require no real credentials.

Generated files:

- `dist/github-workspace-0.1.0.jar`
- `dist/github-workspace-0.1.0.archiplugin`
- `dist/SHA256SUMS`

Historical Governance artifacts in `dist/` are retained and are not overwritten by this packaging step.

## Manual Archi validation checklist

Automated tests cannot prove native UI behavior. In Archi 5.11.0, verify:

1. Uninstall Governance, install the GitHub Workspace `.archiplugin`, restart, and confirm the menu is **File → GitHub Workspace → Browse Repositories...** with no separate Governance menu.
2. Open Preferences, verify the default organization/topic/workspace, apply a custom writable location, restart Archi, and confirm the settings persist.
3. Connect through Git Credential Manager, confirm the account, and browse repositories with and without thumbnails.
4. Clone a repository into a new Plan ID. Verify the final path, active branch, and that no new branch was pushed. Repeat with a Plan ID already present remotely and verify upstream tracking.
5. Edit and save `source/design.archimate`; confirm `git status` sees the change.
6. Clone a second Plan ID for the same repository. Open both models simultaneously and verify distinct paths do not cause duplicate-model or internal-ID conflicts in Archi.
7. Open an already-open plan again; verify it activates the current model without reload or loss of unsaved changes.
8. Change the workspace root and verify no checkout moved, disappeared, or changed, including any legacy `.governance/workspace/` directory.
9. Cancel a clone and verify no final plan checkout was left published.

**Unverified here:** the new preference page, File menu rendering, thumbnail image presentation, and simultaneous native opening of two copied models must be exercised in Archi 5.11.0 before claiming functional acceptance. The pre-refactor 103-assertion suite is preserved and has a passing baseline; new automated checks supplement it but do not replace this manual checklist.
