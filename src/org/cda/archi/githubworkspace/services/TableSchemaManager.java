package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.util.List;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.repositories.TableSchemaRepository;

/** Table catalog and creation rules. The repository schema files remain the only catalog. */
public final class TableSchemaManager {
    private final TableSchemaRepository repository;

    public TableSchemaManager(TableSchemaRepository repository) { this.repository = repository; }

    public TableSchemaRepository.Catalog discover(String owner, String repo, GitHubSession session,
            CancellationToken token) throws IOException, InterruptedException {
        return repository.discover(owner, repo, session, token);
    }

    public void create(String owner, String repo, String branch, boolean knownEmpty, TableSchema schema,
            List<TableSchema> existing, GitHubSession session, CancellationToken token)
            throws IOException, InterruptedException {
        if (existing.stream().anyMatch(table -> table.name().equalsIgnoreCase(schema.name()))) {
            throw new IOException("A table named " + schema.name() + " already exists.");
        }
        repository.create(owner, repo, branch, knownEmpty, schema, session, token);
    }
}
