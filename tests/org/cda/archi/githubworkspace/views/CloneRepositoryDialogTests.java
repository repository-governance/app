package org.cda.archi.githubworkspace.views;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.services.WorkspaceService;

/** Regression coverage for placeholder and user-entered destination previews. */
public final class CloneRepositoryDialogTests {
    private CloneRepositoryDialogTests() { }

    public static int run() throws Exception {
        Path root = Files.createTempDirectory("github-workspace-clone-preview-");
        try {
            WorkspaceService workspace = new WorkspaceService(root);
            GitHubRepository repository = new GitHubRepository(9001, "SBB-009999",
                    "repository-governance/SBB-009999", "Solution Building Block Demo", "private", true,
                    List.of("archimate"), "https://github.com/repository-governance/SBB-009999",
                    "https://github.com/repository-governance/SBB-009999.git",
                    "git@github.com:repository-governance/SBB-009999.git", "main", null);

            String empty = CloneRepositoryDialog.destinationPreview(workspace, repository, "");
            require(empty.equals(root.resolve("github.com/repository-governance/SBB-009999")
                    + java.io.File.separator + "(enter Plan ID)"),
                    "Empty Plan ID is shown as a presentation hint, not resolved as a filesystem segment");

            String invalid = CloneRepositoryDialog.destinationPreview(workspace, repository, "D-<");
            require(invalid.endsWith(java.io.File.separator + "(invalid Plan ID)"),
                    "Invalid Plan ID does not get resolved as a path segment");

            String valid = CloneRepositoryDialog.destinationPreview(workspace, repository, "D-09999");
            require(valid.equals(root.resolve("github.com/repository-governance/SBB-009999/D-09999").toString()),
                    "A valid human-entered Plan ID previews the normal workspace path");
            return 3;
        } finally {
            deleteTree(root);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        List<Path> paths;
        try (var walk = Files.walk(root)) {
            paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) Files.deleteIfExists(path);
    }
}
