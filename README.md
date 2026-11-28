# GitHub Workspace for Archi

GitHub Workspace is a native SWT/JFace plug-in for Archi **5.11.0**. It reuses Git Credential Manager authentication and GitHub REST, MVC, asynchronous process, and native model-opening services. Version **0.1.0** includes repository browsing and management, template-based repository creation, thumbnails, configurable preferences, independent local branches, and Data Manager.

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

- **Organization URL**: defaults to `https://github.com/repository-governance`; the app stores and sends the organization login.
- **Topic by default**: fixed to `archimate` and read-only. It filters Browse and Management repositories and is assigned to newly created repositories.
- **Template Repository URL**: defaults to `https://github.com/repository-governance/templates`. Each root folder is offered as a repository template.

The catalog searches GitHub using `org:{organization} topic:{topic}` and does not expose these settings as editable catalog fields.

### Data Management

**Data Repository URL** defaults to `https://github.com/repository-governance/workspace-data`. It must be an HTTPS GitHub repository URL. Data Manager obtains the configured repository's default branch from GitHub REST; it does not assume `main` or `master`. Authentication reuses the configured Git Credential Manager flow, and no PAT or credential is stored by the plugin.

Choose **File → GitHub Workspace → Data Manager...** to open a modal, resizable table designer and JSON-record manager. Tables are discovered from valid schema files; there is no separate catalog file:

```text
tables/{table-name}/schema.json
tables/{table-name}/records/{safe-primary-key}.json
```

Each record is a separate UTF-8 JSON document. Numeric and boolean fields are stored as JSON numbers and booleans; empty optional values are JSON `null`. Primary keys are encoded using URL-safe Base64 in filenames so they cannot introduce paths or traversal. Table creation and record CRUD commit through GitHub's Contents API with the repository branch and fetched SHA. A 409/422 conflict is reported for refresh/retry rather than overwritten. If GitHub identifies a repository as empty, the first explicit **Create Table** action tries Contents API first and can initialize its first branch using create-only Git Database tree/commit/ref endpoints; opening the window never writes data.

Table names may contain uppercase and lowercase letters, digits, underscores, and hyphens; they must start with a letter or underscore and are limited to 48 characters. Names are unique case-insensitively.

Limitations: schemas cannot be edited or deleted after creation; relationships, bulk operations, and migrations are not implemented. Data Manager supports up to 200 table entries, caps each record file at 256 KiB and each table at 999 records, and rejects directory listings at the Contents API 1,000-entry boundary instead of presenting a potentially partial result. Public data repositories produce a warning; do not store sensitive or personal data there. GitHub's API can return 404 for inaccessible private resources as well as absent paths, so repository metadata and the default branch are checked first; for initialized repositories, root contents must also be readable before missing table/records paths are treated as absent.

### Local Workspace

The default root is resolved from the user's profile:

```text
${user.home}/.github-workspace/workspace/
```

On Windows this is `%USERPROFILE%\.github-workspace\workspace\`. Clear **Use default location** to enter or browse to a custom writable directory. Applying preferences validates that it exists or can be created and is writable. Changing the root affects future operations only: no repositories are moved or overwritten, and open models remain associated with their original physical files.

### Authentication

Git installation/version, credential-helper configuration, and the last authenticated GitHub account appear in a read-only Setting/Value table. Authentication uses the existing `git credential fill` flow. No token is requested by the UI or stored in preferences.

The preference page uses Archi's native **Apply**, **Apply and Close**, **Cancel**, and **Restore Defaults** actions.

## Manage organization repositories

Choose **File → GitHub Workspace → Management Repositories** to view repositories tagged with the fixed `archimate` topic. The grid includes a Topic column alongside each repository's description, visibility, and default branch.

**Create Repository** presents a **Building Block** form for code (`SBB-` plus seven digits), type (`AM` or `SD`), alphabetic title, multiline description, and template. The GitHub repository name combines code, type, and a title slug; descriptions are saved to GitHub. Repositories are always private, receive the `archimate` topic, and contain the selected template folder at the root.

Repository creation and template copying require a GitHub credential with permission to read the template repository, list the organization's repositories, create repositories in that organization, write Git objects, and manage repository topics. If the account lacks those permissions, GitHub Workspace reports the API failure; it does not store credentials in Preferences.

## Diagnostic log

The help icon in the lower-left corner of Browse Repositories, Management Repositories, and Data Manager opens a modal, selectable multiline view of the complete trace, including operation messages and exception stack traces. The log is appended under the user's profile at `${user.home}/.github-workspace/logs/github-workspace.log` (on Windows: `%USERPROFILE%\.github-workspace\logs\github-workspace.log`). Authorization headers, tokens, credential output, and API request bodies are not written to the log.

## Data Manager

The Data Manager table designer currently supports String, Text, Integer, Decimal, Boolean, Date, DateTime, Email, URL, and UUID columns. It requires exactly one String, Integer, or UUID primary key. Forms are generated from `schema.json` at runtime. The automated build uses simulated HTTP responses only; it does not perform real GitHub reads or writes. Test a configured repository and token manually before production use.

## Browse repositories

The File-menu contribution was checked against Archi 5.11.0's [`ArchiActionBarAdvisor.java`](https://github.com/archimatetool/archi/blob/release_5.11.0/com.archimatetool.editor/src/com/archimatetool/editor/actions/ArchiActionBarAdvisor.java): Archi creates the File menu using `IWorkbenchActionConstants.M_FILE`, places `FILE_START` before New, and terminates it with `FILE_END`. GitHub Workspace contributes immediately after `FILE_START`, so it appears first, with the supplied GitHub icon on the submenu and the supplied catalogs icon on Browse Repositories. The preference page uses Eclipse's `org.eclipse.ui.preferencePages` extension point, which Archi 5.11.0 itself declares in its `plugin.xml`.

Connect to GitHub in the catalog. Search uses the saved organization and topic, GitHub REST API pagination, and existing completeness/rate-limit/error handling. The catalog includes a local text filter, Refresh, Preferences, connection status, an empty state, and scrollable repository cards.

Cards show repository name, description, visibility, default branch, local branch workspaces, and a **Clone** action. A thumbnail is loaded asynchronously only as its card enters the viewport. GitHub's authenticated REST Contents API is used; the first PNG in `thumbnails/` by alphabetical filename is selected. Non-PNG files are ignored, absent thumbnails retain a placeholder, PNG dimensions are bounded, aspect ratio is preserved, and a bounded in-memory cache avoids repeat requests. No repository is cloned just to display an image. `manifest.json` and thumbnails are not generated or changed.

## Clone and open an architecture plan

Choose **Clone** and enter a **branch name** matching `D-[0-9]+` (for example `D-09999`). This value is the name of the local working branch and its workspace folder; it is not a plan fetched from GitHub. The destination is:

```text
{workspace-root}/github.com/{organization}/{repository}/{branch-name}/
```

For example:

```text
%USERPROFILE%\.github-workspace\workspace\github.com\
  repository-governance\SBB-009999\D-09999\
```

Git also validates the branch name with `git check-ref-format --branch`. Cloning uses Git's HTTPS `clone_url` into a unique staging folder within the repository workspace. On a new checkout, GitHub Workspace reads the repository's actual default branch (for this repository, `master`) and checks whether a remote branch with the requested name already exists:

- If it does not exist, it creates a local branch from the default branch.
- If it exists, it fetches and checks out that branch with its `origin/{branch-name}` tracking branch.
- It does not push the new branch, commit changes, or alter the remote default branch.

The staging checkout is validated before being published at the final branch-name path. A failed or cancelled operation never publishes a valid-looking final checkout; incomplete staging data is retained rather than automatically deleted.

When the same branch workspace already exists locally, GitHub Workspace validates its Git root, `origin`, and active branch, then reuses it without changing branch or running `pull`, `fetch`, `reset`, `clean`, `stash`, `checkout`, or `switch`. Local edits are preserved. A mismatched repository or active branch is reported and left untouched.

Repositories with local branches show a separate **Open** action for each validated branch workspace. **Clone** remains available to enter another branch name. Each branch is a separate Git working copy; Git worktrees are not used.

## Native Archi model handling

Only `source/design.archimate` is opened, through Archi's `IEditorModelManager.INSTANCE.openModel(File)` API. It remains at its physical path inside the plan checkout, so Archi Save/Ctrl+S writes directly to the versioned model. Git can report those changes with `git status`.

Already-open models are deduplicated by physical file path, not by the model's internal identifier. Archi 5.11.0's official `EditorModelManager` source confirms that it locates an existing model by comparing the requested `File` with each open model's `getFile()`. Therefore, identical internal IDs in different plan directories are not treated as the same file by this manager; each distinct checkout path may be open independently. This source inspection is not a substitute for checking the behavior in a running Archi installation.

## Authentication and security

Git Credential Manager remains responsible for sign-in. Credentials are passed to GitHub API requests only in the HTTPS Authorization header and are not put in command arguments, URLs, logs, or files. New clones use validated HTTPS `github.com` URLs; process calls use argument vectors, never a shell.

Organization, repository, branch name, workspace, and model paths are validated. Symbolic links and redirected workspace paths are rejected, and destinations are constrained to the configured workspace. GitHub Workspace does not change Git configuration, execute repository scripts, alter `manifest.json` or thumbnails, or delete local checkouts.

## Automated tests and generated files

`docker compose run --rm builder` compiles, runs the existing authentication/search/workspace/security/plan/thumbnail/local-Git tests and mocked Data Manager schema/Contents CRUD tests, validates the OSGi bundle and installer archive, and checks SHA-256 sums. Tests use temporary repositories and simulated GitHub API responses; they require no real credentials.

Generated files:

- `dist/github-workspace-0.1.0.jar`
- `dist/github-workspace-0.1.0.archiplugin`
- `dist/SHA256SUMS`

Historical Governance artifacts in `dist/` are retained and are not overwritten by this packaging step.

## Manual Archi validation checklist

Automated tests cannot prove native UI behavior. In Archi 5.11.0, verify:

1. Uninstall Governance, install the GitHub Workspace `.archiplugin`, restart, and confirm **File → GitHub Workspace** contains **Browse Repositories...**, **Management Repositories**, and **Data Manager...**.
2. Open Preferences, verify the default organization/topic/template/data repository/workspace, apply a custom writable location and Data Repository URL, restart Archi, and confirm the settings persist.
3. Connect through Git Credential Manager, confirm the account, and browse repositories with and without thumbnails.
4. Clone a repository into a new branch name. Verify the final path, active branch, and that no new branch was pushed. Repeat with a branch of that name already present remotely and verify upstream tracking.
5. Edit and save `source/design.archimate`; confirm `git status` sees the change.
6. Clone a second branch name for the same repository. Open both models simultaneously and verify distinct paths do not cause duplicate-model or internal-ID conflicts in Archi.
7. Open an already-open plan again; verify it activates the current model without reload or loss of unsaved changes.
8. Change the workspace root and verify no checkout moved, disappeared, or changed, including any legacy `.governance/workspace/` directory.
9. Cancel a clone and verify no final plan checkout was left published.
10. With an organization where the authenticated account has repository-creation permission, open **Management Repositories**, confirm it lists repositories tagged `archimate`, and create a test repository from a selected template folder; verify its files are at the repository root, it is private, and it has the `archimate` topic.
11. Configure a private test Data Repository and open **Data Manager**. Verify the reported table catalog/default branch and, for an empty repository, that opening the window alone creates no remote content.
12. Create `architects` with `id` (String primary key), `fullName` (String), `email` (Email), and `active` (Boolean); create, read, edit, and delete a test record, then close/reopen the dialog and verify the schema/records reload from GitHub. Also check a public repository warning if using a disposable public repository.

**Unverified here:** native UI behavior in Archi 5.11.0 and all real GitHub reads/writes—including empty-repository initialization—must be manually exercised before claiming functional acceptance. Automated Data Manager tests use simulated HTTP responses and do not contact GitHub.
