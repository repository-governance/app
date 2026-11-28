package org.cda.archi.githubworkspace.repositories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cda.archi.githubworkspace.clients.GitHubContentsClient;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.JsonParser;
import org.cda.archi.githubworkspace.infrastructure.JsonWriter;
import org.cda.archi.githubworkspace.models.DataRecord;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.services.GitHubSession;

/** One JSON file per record, with path-safe reversible primary-key addressing. */
public final class DataRecordRepository {
    public static final int MAX_RECORDS = 999;
    private final GitHubContentsClient client;

    public DataRecordRepository(GitHubContentsClient client) { this.client = client; }

    public List<DataRecord> list(String owner, String repo, String branch, TableSchema schema,
            GitHubSession session, CancellationToken cancellation) throws IOException, InterruptedException {
        String directory = "tables/" + schema.name() + "/records";
        List<GitHubContentsClient.Entry> entries = client.list(owner, repo, directory, branch,
                session.tokenForRequest(), cancellation);
        if (entries.size() >= MAX_RECORDS) {
            throw new IOException("The records directory reached GitHub's 1,000-entry Contents API limit; "
                    + "results are not presented as complete.");
        }
        List<DataRecord> records = new ArrayList<>();
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (GitHubContentsClient.Entry entry : entries) {
            if (!"file".equals(entry.type()) || !entry.name().matches("key-[A-Za-z0-9_-]{1,240}\\.json")
                    || !entry.path().equals(directory + "/" + entry.name())) continue;
            cancellation.throwIfCancelled();
            if (records.size() >= MAX_RECORDS) {
                throw new IOException("The table has more than 1,000 records; results are not complete.");
            }
            String recordPath = directory + "/" + entry.name();
            var file = client.read(owner, repo, recordPath, branch, session.tokenForRequest(), cancellation);
            if (file == null) continue;
            try {
                Object parsed = JsonParser.parse(file.content());
                if (!(parsed instanceof Map<?, ?> values)) throw new IllegalArgumentException();
                Map<String, Object> typed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> item : values.entrySet()) {
                    if (!(item.getKey() instanceof String key)) throw new IllegalArgumentException();
                    typed.put(key, item.getValue());
                }
                DataRecord record = new DataRecord(typed, file.sha());
                Object key = record.values().get(schema.primaryKey());
                if (key == null || !entry.name().equals(fileName(schema, key)) || !keys.add(String.valueOf(key))) {
                    throw new IllegalArgumentException();
                }
                records.add(record);
            } catch (RuntimeException ex) {
                throw new IOException("Invalid JSON record at " + recordPath + ".");
            }
        }
        return List.copyOf(records);
    }

    public boolean exists(String owner, String repo, String branch, TableSchema schema, Object primaryKey,
            GitHubSession session, CancellationToken cancellation) throws IOException, InterruptedException {
        return client.read(owner, repo, recordPath(schema, primaryKey), branch,
                session.tokenForRequest(), cancellation) != null;
    }

    public void ensureCapacity(String owner, String repo, String branch, TableSchema schema,
            GitHubSession session, CancellationToken cancellation) throws IOException, InterruptedException {
        String directory = "tables/" + schema.name() + "/records";
        List<GitHubContentsClient.Entry> entries = client.list(owner, repo, directory, branch,
                session.tokenForRequest(), cancellation);
        long count = entries.stream().filter(entry -> "file".equals(entry.type())
                && entry.name().matches("key-[A-Za-z0-9_-]{1,240}\\.json")
                && entry.path().equals(directory + "/" + entry.name())).count();
        if (count >= MAX_RECORDS) {
            throw new IOException("This table is limited to 999 records to stay below GitHub's directory listing limit.");
        }
    }

    public String create(String owner, String repo, String branch, TableSchema schema, DataRecord record,
            GitHubSession session, CancellationToken cancellation) throws IOException, InterruptedException {
        return client.put(owner, repo, recordPath(schema, record.values().get(schema.primaryKey())), branch,
                JsonWriter.write(record.values()) + "\n", "Create record " + schema.name() + "/"
                        + record.primaryKey(schema), null, session.tokenForRequest(), cancellation);
    }

    public String update(String owner, String repo, String branch, TableSchema schema, DataRecord record,
            GitHubSession session, CancellationToken cancellation) throws IOException, InterruptedException {
        return client.put(owner, repo, recordPath(schema, record.values().get(schema.primaryKey())), branch,
                JsonWriter.write(record.values()) + "\n", "Update record " + schema.name() + "/"
                        + record.primaryKey(schema), record.sha(), session.tokenForRequest(), cancellation);
    }

    public void delete(String owner, String repo, String branch, TableSchema schema, DataRecord record,
            GitHubSession session, CancellationToken cancellation) throws IOException, InterruptedException {
        client.delete(owner, repo, recordPath(schema, record.values().get(schema.primaryKey())), branch,
                record.sha(), "Delete record " + schema.name() + "/" + record.primaryKey(schema),
                session.tokenForRequest(), cancellation);
    }

    public static String recordPath(TableSchema schema, Object key) {
        if (key == null) throw new IllegalArgumentException("Primary key is required.");
        byte[] bytes = String.valueOf(key).getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > 180) {
            throw new IllegalArgumentException("Primary keys must contain 1–180 UTF-8 bytes.");
        }
        return "tables/" + schema.name() + "/records/" + fileName(schema, key);
    }

    private static String fileName(TableSchema schema, Object key) {
        byte[] bytes = String.valueOf(key).getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > 180) {
            throw new IllegalArgumentException("Primary keys must contain 1–180 UTF-8 bytes.");
        }
        return "key-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + ".json";
    }
}
