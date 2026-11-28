package org.cda.archi.githubworkspace.repositories;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.cda.archi.githubworkspace.clients.GitHubContentsClient;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.RemoteFile;
import org.cda.archi.githubworkspace.models.DataRecord;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.services.GitHubSession;
import org.cda.archi.githubworkspace.services.TableSchemaService;

/** Discovers tables from valid schema.json files; no duplicate remote catalog is maintained. */
public final class TableSchemaRepository {
    public static final int MAX_TABLES = 200;
    public record Catalog(List<TableSchema> schemas, Map<String, String> schemaShas,
            String branch, boolean isPrivate, boolean emptyRepository) {
        public Catalog {
            schemas = List.copyOf(schemas);
            schemaShas = Map.copyOf(schemaShas);
        }
    }
    public record StoredSchema(TableSchema schema, String sha) { }
    private final GitHubContentsClient client;
    private final TableSchemaService schemas;

    public TableSchemaRepository(GitHubContentsClient client, TableSchemaService schemas) {
        this.client = client;
        this.schemas = schemas;
    }

    public Catalog discover(String owner, String repo, GitHubSession session, CancellationToken cancellation)
            throws IOException, InterruptedException {
        GitHubContentsClient.RepositoryInfo info =
                client.repository(owner, repo, session.tokenForRequest(), cancellation);
        if (info.defaultBranch().isBlank()) {
            return new Catalog(List.of(), Map.of(), "", info.isPrivate(), info.empty());
        }
        if (info.empty()) {
            return new Catalog(List.of(), Map.of(), info.defaultBranch(), info.isPrivate(), true);
        }
        List<GitHubContentsClient.Entry> root = client.list(owner, repo, "", info.defaultBranch(),
                session.tokenForRequest(), cancellation);
        if (root.isEmpty()) {
            throw new IOException("GitHub returned no repository root contents. Verify Contents permission "
                    + "and the repository's default branch; no tables were assumed.");
        }
        List<GitHubContentsClient.Entry> entries = client.list(owner, repo, "tables", info.defaultBranch(),
                session.tokenForRequest(), cancellation);
        if (entries.size() > MAX_TABLES) {
            throw new IOException("Data Manager supports up to 200 table entries in the tables directory.");
        }
        List<TableSchema> result = new ArrayList<>();
        Map<String, String> shas = new LinkedHashMap<>();
        for (GitHubContentsClient.Entry entry : entries) {
            cancellation.throwIfCancelled();
            if (!"dir".equals(entry.type()) || !TableSchema.isSafeTableName(entry.name())) continue;
            RemoteFile file = client.read(owner, repo, "tables/" + entry.name() + "/schema.json", info.defaultBranch(),
                    session.tokenForRequest(), cancellation);
            if (file == null) continue;
            try {
                TableSchema schema = schemas.parse(file.content());
                if (schema.name().equals(entry.name())) {
                    result.add(schema);
                    shas.put(schema.name(), file.sha());
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid remote schemas are deliberately not exposed as tables.
            }
        }
        result.sort(Comparator.comparing(TableSchema::name));
        return new Catalog(result, shas, info.defaultBranch(), info.isPrivate(), info.empty());
    }

    public StoredSchema read(String owner, String repo, String branch, String tableName,
            GitHubSession session, CancellationToken cancellation) throws IOException, InterruptedException {
        if (!TableSchema.isSafeTableName(tableName)) throw new IOException("Invalid table name.");
        String path = "tables/" + tableName + "/schema.json";
        RemoteFile file = client.read(owner, repo, path, branch, session.tokenForRequest(), cancellation);
        if (file == null) throw new IOException("The table schema no longer exists. Refresh the table catalog.");
        try {
            TableSchema schema = schemas.parse(file.content());
            if (!schema.name().equals(tableName)) throw new IllegalArgumentException("Schema name does not match its folder.");
            return new StoredSchema(schema, file.sha());
        } catch (IllegalArgumentException ex) {
            throw new IOException("The current table schema is invalid and cannot be edited.");
        }
    }

    public String create(String owner, String repo, String branch, boolean knownEmpty, TableSchema schema,
            GitHubSession session, CancellationToken cancellation) throws IOException, InterruptedException {
        String path = "tables/" + schema.name() + "/schema.json";
        String content = schemas.serialize(schema);
        String message = "Create table " + schema.name();
        try {
            return client.put(owner, repo, path, branch, content, message, null,
                    session.tokenForRequest(), cancellation);
        } catch (IOException ex) {
            if (!knownEmpty || !(ex instanceof GitHubContentsClient.EmptyRepositoryConflict)) throw ex;
            if (branch == null || branch.isBlank()) {
                throw new IOException("The repository is empty and GitHub did not report a default branch. "
                        + "Set a default branch in GitHub, then refresh.");
            }
            client.initializeEmptyRepository(owner, repo, branch, path, content, message,
                    session.tokenForRequest(), cancellation);
            return "initialized";
        }
    }

    public String update(String owner, String repo, String branch, TableSchema original, TableSchema schema,
            String originalSha, List<DataRecord> records, GitHubSession session, CancellationToken cancellation)
            throws IOException, InterruptedException {
        if (originalSha == null || originalSha.isBlank()) {
            throw new IOException("The schema SHA is unavailable. Refresh the table catalog before editing.");
        }
        if (!original.name().equals(schema.name())) {
            List<GitHubContentsClient.Entry> targetEntries = client.list(owner, repo,
                    "tables/" + schema.name(), branch,
                    session.tokenForRequest(), cancellation);
            if (!targetEntries.isEmpty()) {
                throw new IOException("A GitHub folder already exists for table " + schema.name()
                        + "; choose a different name or inspect that folder first.");
            }
            List<String[]> moves = new ArrayList<>();
            for (DataRecord record : records) {
                Object key = record.values().get(original.primaryKey());
                if (key == null || record.sha() == null || record.sha().isBlank()) {
                    throw new IOException("A record is missing its primary key or GitHub SHA; refresh and retry.");
                }
                moves.add(new String[] {
                        DataRecordRepository.recordPath(original, key),
                        record.sha(),
                        DataRecordRepository.recordPath(schema, key)
                });
            }
            client.moveTable(owner, repo, branch, original.name(), schema.name(), originalSha,
                    schemas.serialize(schema), moves, session.tokenForRequest(), cancellation);
            return "renamed";
        }
        String path = "tables/" + schema.name() + "/schema.json";
        return client.put(owner, repo, path, branch, schemas.serialize(schema),
                "Update table " + schema.name(), originalSha, session.tokenForRequest(), cancellation);
    }
}
