package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            String originalSha, List<DataRecord> existingRecords, Map<String, String> renamedColumns,
            GitHubSession session, CancellationToken token)
            throws IOException, InterruptedException {
        if (!existingRecords.isEmpty()
                && (!original.primaryKey().equals(updated.primaryKey())
                        || original.primaryKeyColumn().type() != updated.primaryKeyColumn().type())) {
            throw new IOException("The primary key name or type cannot change while records exist.");
        }
        validateRenames(original, updated, renamedColumns);
        List<DataRecord> recordsToSave = new java.util.ArrayList<>();
        for (DataRecord record : existingRecords) {
            try {
                Map<String, Object> migrated = migrateValues(record.values(), renamedColumns);
                recordsToSave.add(new DataRecord(validation.validateStored(updated, migrated), record.sha()));
            } catch (IllegalArgumentException ex) {
                throw new IOException("The schema change is incompatible with record "
                        + original.name() + "/" + record.primaryKey(original) + ": " + ex.getMessage());
            }
        }
        repository.update(owner, repo, branch, original, updated, originalSha, recordsToSave,
                !renamedColumns.isEmpty(), session, token);
    }

    private static void validateRenames(TableSchema original, TableSchema updated,
            Map<String, String> renamedColumns) throws IOException {
        java.util.Set<String> sourceNames = original.columns().stream().map(column -> column.name())
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> targetNames = updated.columns().stream().map(column -> column.name())
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> uniqueTargets = new java.util.HashSet<>();
        for (Map.Entry<String, String> rename : renamedColumns.entrySet()) {
            if (!sourceNames.contains(rename.getKey()) || !targetNames.contains(rename.getValue())
                    || !uniqueTargets.add(rename.getValue())) {
                throw new IOException("The column rename mapping is invalid. Refresh the schema editor and retry.");
            }
        }
    }

    private static Map<String, Object> migrateValues(Map<String, Object> source,
            Map<String, String> renamedColumns) {
        if (renamedColumns.isEmpty()) return source;
        java.util.Set<String> renamedSources = renamedColumns.keySet();
        for (String target : renamedColumns.values()) {
            if (source.containsKey(target) && !renamedSources.contains(target)) {
                throw new IllegalArgumentException("A value already exists for " + target + ".");
            }
        }
        Map<String, Object> values = new LinkedHashMap<>(source);
        Map<String, Object> movedValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> rename : renamedColumns.entrySet()) {
            if (values.containsKey(rename.getKey())) {
                movedValues.put(rename.getValue(), values.get(rename.getKey()));
            }
        }
        renamedColumns.keySet().forEach(values::remove);
        values.putAll(movedValues);
        return values;
    }
}
