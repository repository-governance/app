package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.util.List;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.models.DataRecord;
import org.cda.archi.githubworkspace.repositories.TableSchemaRepository;

/** Table catalog and creation rules. The repository schema files remain the only catalog. */
public final class TableSchemaManager {
    private final TableSchemaRepository repository;
    private final DataValidationService validation;

    public TableSchemaManager(TableSchemaRepository repository) {
        this(repository, new DataValidationService());
    }

    public TableSchemaManager(TableSchemaRepository repository, DataValidationService validation) {
        this.repository = repository;
        this.validation = validation;
    }

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

    public void update(String owner, String repo, String branch, TableSchema original, TableSchema updated,
            String originalSha, List<DataRecord> existingRecords, GitHubSession session, CancellationToken token)
            throws IOException, InterruptedException {
        if (!existingRecords.isEmpty()
                && (!original.primaryKey().equals(updated.primaryKey())
                        || original.primaryKeyColumn().type() != updated.primaryKeyColumn().type())) {
            throw new IOException("The primary key name or type cannot change while records exist.");
        }
        for (DataRecord record : existingRecords) {
            try {
                validation.validateStored(updated, record.values());
            } catch (IllegalArgumentException ex) {
                throw new IOException("The schema change is incompatible with record "
                        + original.name() + "/" + record.primaryKey(original) + ": " + ex.getMessage());
            }
        }
        repository.update(owner, repo, branch, original, updated, originalSha, existingRecords, session, token);
    }
}
