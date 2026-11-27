package org.cda.archi.githubworkspace.models;

/** A first-level directory that can seed a newly created repository. */
public record RepositoryTemplateFolder(String name, String treeSha) {
    public RepositoryTemplateFolder {
        if (name == null || name.isBlank() || treeSha == null || treeSha.isBlank()) {
            throw new IllegalArgumentException("A template folder needs a name and Git tree.");
        }
    }
}
