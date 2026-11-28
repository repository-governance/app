package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.DataRecord;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.repositories.DataRecordRepository;

/** Applies validation and CRUD rules over the remote record repository. */
public final class DataRecordService {
    private final DataRecordRepository repository;
    private final DataValidationService validation;

    public DataRecordService(DataRecordRepository repository, DataValidationService validation) {
        this.repository = repository;
        this.validation = validation;
    }

    public List<DataRecord> list(String owner, String repo, String branch, TableSchema schema,
            GitHubSession session, CancellationToken token) throws IOException, InterruptedException {
        List<DataRecord> loaded = repository.list(owner, repo, branch, schema, session, token);
        List<DataRecord> checked = new ArrayList<>();
        for (DataRecord record : loaded) {
            Map<String, Object> values = validation.validateStored(schema, record.values());
            checked.add(new DataRecord(values, record.sha()));
        }
        return List.copyOf(checked);
    }

    public DataRecord create(String owner, String repo, String branch, TableSchema schema,
            Map<String, String> raw, GitHubSession session, CancellationToken token)
            throws IOException, InterruptedException {
        Map<String, Object> values = validation.validate(schema, raw, true);
        Object key = values.get(schema.primaryKey());
        repository.ensureCapacity(owner, repo, branch, schema, session, token);
        if (repository.exists(owner, repo, branch, schema, key, session, token)) {
            throw new IOException("A record with primary key " + key + " already exists.");
        }
        DataRecord record = new DataRecord(values, null);
        String sha = repository.create(owner, repo, branch, schema, record, session, token);
        return new DataRecord(values, sha);
    }

    public DataRecord update(String owner, String repo, String branch, TableSchema schema, DataRecord original,
            Map<String, String> raw, GitHubSession session, CancellationToken token)
            throws IOException, InterruptedException {
        Map<String, Object> values = validation.validate(schema, raw, false);
        Object oldKey = original.values().get(schema.primaryKey());
        Object newKey = values.get(schema.primaryKey());
        if (!sameKey(oldKey, newKey)) throw new IOException("Primary keys cannot be changed.");
        DataRecord record = new DataRecord(values, original.sha());
        String sha = repository.update(owner, repo, branch, schema, record, session, token);
        return new DataRecord(values, sha);
    }

    public void delete(String owner, String repo, String branch, TableSchema schema, DataRecord record,
            GitHubSession session, CancellationToken token) throws IOException, InterruptedException {
        repository.delete(owner, repo, branch, schema, record, session, token);
    }

    private static boolean sameKey(Object left, Object right) {
        if (left instanceof BigDecimal a && right instanceof BigDecimal b) return a.compareTo(b) == 0;
        return java.util.Objects.equals(left, right);
    }
}
