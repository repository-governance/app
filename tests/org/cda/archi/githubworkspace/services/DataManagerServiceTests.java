package org.cda.archi.githubworkspace.services;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cda.archi.githubworkspace.clients.GitHubApiClient;
import org.cda.archi.githubworkspace.clients.GitHubContentsClient;
import org.cda.archi.githubworkspace.clients.RestResponse;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.JsonParser;
import org.cda.archi.githubworkspace.infrastructure.JsonWriter;
import org.cda.archi.githubworkspace.models.ColumnDefinition;
import org.cda.archi.githubworkspace.models.DataRecord;
import org.cda.archi.githubworkspace.models.DataType;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.repositories.DataRecordRepository;
import org.cda.archi.githubworkspace.repositories.TableSchemaRepository;

/** Simulated GitHub Contents/Git Database API coverage; never writes to GitHub. */
public final class DataManagerServiceTests {
    private DataManagerServiceTests() { }

    public static int run() throws Exception {
        int assertions = 0;
        TableSchemaService schemaCodec = new TableSchemaService();
        TableSchema schema = new TableSchema("1.0", "architects", "Architecture team", "id", List.of(
                new ColumnDefinition("id", DataType.STRING, true, true),
                new ColumnDefinition("fullName", DataType.TEXT, false, true),
                new ColumnDefinition("email", DataType.EMAIL, false, true),
                new ColumnDefinition("active", DataType.BOOLEAN, false, false)));
        String serialized = schemaCodec.serialize(schema);
        TableSchema decoded = schemaCodec.parse(serialized);
        check(decoded.equals(schema), "Schema JSON round-trips ordered columns and primary key");
        check(serialized.contains("\"primaryKey\":\"id\"") && !serialized.contains("\"primaryKey\":true"),
                "Schema has one top-level source-of-truth primaryKey field");
        assertions += 2;
        TableSchema patterned = new TableSchema("1.0", "codes", "Codes", "id", List.of(
                new ColumnDefinition("id", DataType.STRING, true, true),
                new ColumnDefinition("code", DataType.STRING, false, true,
                        "^[A-Z]{2}-[0-9]{4}$", "Use two uppercase letters, a hyphen, and four digits.")));
        TableSchema decodedPatterned = schemaCodec.parse(schemaCodec.serialize(patterned));
        check(decodedPatterned.equals(patterned)
                && schemaCodec.serialize(patterned).contains("\"validationMessage\""),
                "String regex and its custom message persist in schema.json");
        assertions++;

        expect(IllegalArgumentException.class, () -> new TableSchema("1.0", "../escape", "", null,
                List.of(new ColumnDefinition("id", DataType.STRING, true, true))),
                "Table names reject traversal characters");
        expect(IllegalArgumentException.class, () -> new TableSchema("1.0", "architects", "", null,
                List.of(new ColumnDefinition("id", DataType.STRING, true, true),
                        new ColumnDefinition("id", DataType.STRING, false, false))),
                "Duplicate column names are rejected");
        expect(IllegalArgumentException.class, () -> new ColumnDefinition("id", DataType.EMAIL, true, true),
                "Email cannot be a primary key");
        check(!DataRecordRepository.recordPath(schema, "x/../../secrets").contains("../"),
                "Primary key path is Base64 URL-safe and traversal-proof");
        assertions += 4;
        check(TableSchema.isSafeTableName("Architect_Reviews-2026")
                && TableSchema.isSafeTableName("_Internal")
                && !TableSchema.isSafeTableName("../escape"),
                "Table names accept uppercase and underscores while rejecting path traversal");
        assertions++;

        DataValidationService validation = new DataValidationService();
        Map<String, Object> validCode = validation.validate(patterned,
                Map.of("id", "row-1", "code", "AB-1234"), true);
        check(validCode.get("code").equals("AB-1234"), "Matching String regex accepts the value");
        try {
            validation.validate(patterned, Map.of("id", "row-2", "code", "ab-1234"), true);
            throw new AssertionError("Non-matching String regex should fail.");
        } catch (IllegalArgumentException ex) {
            check(ex.getMessage().contains("Use two uppercase letters, a hyphen, and four digits."),
                    "Non-matching String regex returns the configured custom message");
        }
        try {
            validation.validateStored(patterned, Map.of("id", "row-3", "code", "ab-1234"));
            throw new AssertionError("Stored value not matching String regex should fail.");
        } catch (IllegalArgumentException ex) {
            check(ex.getMessage().contains("Use two uppercase letters, a hyphen, and four digits."),
                    "Stored String values return the configured pattern failure message");
        }
        expect(IllegalArgumentException.class, () -> new ColumnDefinition("bad", DataType.STRING,
                false, false, "[", "custom"), "Invalid Java regular expressions are rejected");
        expect(IllegalArgumentException.class, () -> new ColumnDefinition("bad", DataType.TEXT,
                false, false, "^.*$", "custom"), "Regex validation is limited to String columns");
        assertions += 5;
        Map<String, Object> typed = validation.validate(schema,
                Map.of("id", "AS117", "fullName", "Ana García", "email", "ana@example.com", "active", "true"),
                true);
        check(Boolean.TRUE.equals(typed.get("active")), "Boolean values remain JSON booleans");
        expect(IllegalArgumentException.class, () -> validation.validate(schema,
                Map.of("id", "", "fullName", "A", "email", "bad"), true),
                "Required fields and email format are validated");
        TableSchema numeric = new TableSchema("1.0", "numbers", "", "id", List.of(
                new ColumnDefinition("id", DataType.INTEGER, true, true),
                new ColumnDefinition("amount", DataType.DECIMAL, false, false)));
        Map<String, Object> numberValues = validation.validate(numeric,
                Map.of("id", "7", "amount", "1234.50"), true);
        check(numberValues.get("id") instanceof Integer && numberValues.get("amount") instanceof java.math.BigDecimal,
                "Integer and Decimal convert to JSON-native numeric types");
        check(validation.validate(numeric, Map.of("id", "7", "amount", ""), true)
                        .containsKey("amount")
                        && validation.validate(numeric, Map.of("id", "7", "amount", ""), true).get("amount") == null,
                "Optional empty values are represented as null, not zero");
        assertions += 4;
        TableSchema formatted = new TableSchema("1.0", "contacts", "", "id", List.of(
                new ColumnDefinition("id", DataType.UUID, true, true),
                new ColumnDefinition("email", DataType.EMAIL, false, true),
                new ColumnDefinition("website", DataType.URL, false, false),
                new ColumnDefinition("createdOn", DataType.DATE, false, true),
                new ColumnDefinition("updatedAt", DataType.DATETIME, false, false)));
        Map<String, Object> formattedValues = validation.validate(formatted, Map.of(
                "id", "", "email", "valid@example.org", "website", "https://example.org",
                "createdOn", "2026-11-28", "updatedAt", ""), true);
        check(java.util.UUID.fromString(String.valueOf(formattedValues.get("id"))) != null
                && formattedValues.get("createdOn").equals("2026-11-28")
                && formattedValues.get("updatedAt") == null,
                "UUID, URL, Date and absent optional DateTime convert as specified");
        expect(IllegalArgumentException.class, () -> validation.validateStored(schema,
                Map.of("id", "AS117", "fullName", "Ana", "email", "ana@example.com", "active", "true")),
                "Stored JSON types are validated instead of coercing strings to booleans");
        assertions += 2;

        FakeContentsApi api = new FakeContentsApi();
        GitHubContentsClient client = new GitHubContentsClient(api);
        TableSchemaRepository schemaRepository = new TableSchemaRepository(client, schemaCodec);
        DataRecordRepository recordRepository = new DataRecordRepository(client);
        DataRecordService records = new DataRecordService(recordRepository, validation);
        GitHubSession session = new GitHubSession(new org.cda.archi.githubworkspace.models.GitHubUser(
                1L, "tester", "Tester", "https://github.com/tester"), "fake-token".toCharArray());
        CancellationToken token = new CancellationToken();
        try {
            TableSchemaRepository.Catalog empty = schemaRepository.discover("owner", "data", session, token);
            check(empty.emptyRepository() && empty.branch().equals("main"),
                    "Repository metadata distinguishes empty repository and reads its configured branch");
            assertions++;
            schemaRepository.create("owner", "data", empty.branch(), empty.emptyRepository(), schema,
                    session, token);
            check(api.initialized && api.files.containsKey("tables/architects/schema.json"),
                    "First explicit table creation safely initializes an empty repository");
            assertions++;
            TableSchemaRepository.Catalog loaded = schemaRepository.discover("owner", "data", session, token);
            check(loaded.schemas().size() == 1 && loaded.schemas().get(0).name().equals("architects"),
                    "Tables are discovered from valid schema.json files");
            assertions++;
            check(records.list("owner", "data", loaded.branch(), schema, session, token).isEmpty(),
                    "A missing records directory is treated as an empty table, not an error");
            assertions++;
            TableSchema withPattern = new TableSchema("1.0", "architects", "Architecture team with validated IDs",
                    "id", List.of(
                            new ColumnDefinition("id", DataType.STRING, true, true,
                                    "^AS[0-9]{3}$", "Use AS followed by three digits."),
                            new ColumnDefinition("fullName", DataType.TEXT, false, true),
                            new ColumnDefinition("email", DataType.EMAIL, false, true),
                            new ColumnDefinition("active", DataType.BOOLEAN, false, false)));
            TableSchemaRepository.StoredSchema storedSchema =
                    schemaRepository.read("owner", "data", loaded.branch(), schema.name(), session, token);
            TableSchemaManager tableManager = new TableSchemaManager(schemaRepository, validation);
            tableManager.update("owner", "data", loaded.branch(), schema, withPattern, storedSchema.sha(),
                    List.of(), session, token);
            TableSchemaRepository.Catalog updatedCatalog = schemaRepository.discover("owner", "data", session, token);
            check(updatedCatalog.schemas().get(0).equals(withPattern)
                    && !updatedCatalog.schemaShas().get("architects").equals(storedSchema.sha()),
                    "Existing schema edits persist via its original Contents SHA");
            assertions++;
            expect(java.io.IOException.class, () -> schemaRepository.update("owner", "data", loaded.branch(),
                    schema, storedSchema.sha(), session, token),
                    "Concurrent schema changes are rejected using stale SHA");
            assertions++;
            TableSchema activeSchema = withPattern;
            Map<String, String> input = Map.of("id", "AS117", "fullName", "Ana García",
                    "email", "ana@example.com", "active", "true");
            DataRecord created = records.create("owner", "data", loaded.branch(), activeSchema, input, session, token);
            check(api.files.containsKey(DataRecordRepository.recordPath(activeSchema, "AS117")),
                    "Create persists one UTF-8 JSON document at a safe deterministic path");
            assertions++;
            List<DataRecord> read = records.list("owner", "data", loaded.branch(), activeSchema, session, token);
            check(read.size() == 1 && read.get(0).value("active").equals(Boolean.TRUE)
                    && read.get(0).sha().equals(created.sha()), "Read restores typed values and Contents SHA");
            assertions++;
            List<ColumnDefinition> requiredAddition = new ArrayList<>(activeSchema.columns());
            requiredAddition.add(new ColumnDefinition("department", DataType.STRING, false, true));
            TableSchema incompatible = new TableSchema("1.0", activeSchema.name(), activeSchema.description(),
                    activeSchema.primaryKey(), requiredAddition);
            expect(java.io.IOException.class, () -> tableManager.update("owner", "data", loaded.branch(),
                    activeSchema, incompatible, updatedCatalog.schemaShas().get(activeSchema.name()),
                    read, session, token),
                    "Schema updates cannot make existing records fail required-field validation");
            List<ColumnDefinition> newPrimaryColumns = new ArrayList<>(activeSchema.columns());
            newPrimaryColumns.set(0, new ColumnDefinition("id", DataType.STRING, false, true,
                    "^AS[0-9]{3}$", "Use AS followed by three digits."));
            newPrimaryColumns.add(new ColumnDefinition("newId", DataType.STRING, true, true));
            TableSchema changedPrimary = new TableSchema("1.0", activeSchema.name(),
                    activeSchema.description(), "newId", newPrimaryColumns);
            expect(java.io.IOException.class, () -> tableManager.update("owner", "data", loaded.branch(),
                    activeSchema, changedPrimary, updatedCatalog.schemaShas().get(activeSchema.name()),
                    read, session, token),
                    "Primary-key identity cannot change while records exist");
            assertions += 2;
            expect(java.io.IOException.class, () -> records.create("owner", "data", loaded.branch(), activeSchema,
                    input, session, token), "Duplicate primary keys are rejected before a write");
            assertions++;
            Map<String, String> changed = Map.of("id", "AS117", "fullName", "Ana García",
                    "email", "ana@example.com", "active", "false");
            DataRecord updated = records.update("owner", "data", loaded.branch(), activeSchema, created,
                    changed, session, token);
            check(Boolean.FALSE.equals(updated.value("active")) && !updated.sha().equals(created.sha()),
                    "Update uses original SHA then returns the new SHA");
            assertions++;
            expect(java.io.IOException.class, () -> records.update("owner", "data", loaded.branch(), activeSchema,
                    created, changed, session, token), "Stale SHA update reports a concurrency conflict");
            assertions++;
            records.delete("owner", "data", loaded.branch(), activeSchema, updated, session, token);
            check(records.list("owner", "data", loaded.branch(), activeSchema, session, token).isEmpty(),
                    "Delete uses fetched SHA and removes only after remote success");
            assertions++;
            check(api.lastContentWasUtf8 && api.lastDeleteBody.contains("\"sha\"")
                    && api.lastMessage.equals("Delete record architects/AS117") && api.observedBranch.equals("main"),
                    "Contents writes use Base64 UTF-8, branch and SHA with descriptive commit messages");
            assertions++;
        } finally {
            session.close();
        }
        return assertions;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void expect(Class<? extends Throwable> type, Throwing action, String message) throws Exception {
        try { action.run(); }
        catch (Throwable ex) {
            if (type.isInstance(ex)) return;
            throw new AssertionError(message + ": unexpected " + ex, ex);
        }
        throw new AssertionError(message + ": no exception");
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }

    private static RestResponse response(int status, String body) {
        return new RestResponse(status, body, HttpHeaders.of(Map.of(), (name, value) -> true));
    }

    private static final class FakeContentsApi implements GitHubApiClient {
        private record Stored(String content, String sha) { }
        private final Map<String, Stored> files = new LinkedHashMap<>();
        private boolean initialized;
        private int revision;
        private String pendingInitialPath;
        private String pendingInitialContent;
        private boolean lastContentWasUtf8;
        private String lastDeleteBody = "";
        private String lastMessage = "";
        private String observedBranch = "";

        @Override public RestResponse get(URI uri, String bearerToken, CancellationToken cancellation) {
            String path = uri.getPath();
            if (path.equals("/repos/owner/data")) {
                return response(200, JsonWriter.write(Map.of("default_branch", "main", "private", true,
                        "size", initialized ? 1 : 0)));
            }
            if (path.equals("/repos/owner/data/git/ref/heads/main")) {
                return initialized ? response(200, "{\"ref\":\"refs/heads/main\",\"object\":{\"sha\":\"commit\"}}")
                        : response(409, "{\"message\":\"Git Repository is empty.\"}");
            }
            if (path.endsWith("/contents")) return response(200, JsonWriter.write(directoryEntries("")));
            if (path.endsWith("/contents/tables")) return response(200, JsonWriter.write(directoryEntries("tables/")));
            if (path.endsWith("/contents/tables/architects/records")) {
                List<Map<String, Object>> entries = directoryEntries("tables/architects/records/");
                return entries.isEmpty() ? response(404, "{\"message\":\"Not Found\"}")
                        : response(200, JsonWriter.write(entries));
            }
            String contentPath = path.substring(path.indexOf("/contents/") + "/contents/".length());
            Stored stored = files.get(contentPath);
            if (stored == null) return response(404, "{\"message\":\"Not Found\"}");
            byte[] bytes = stored.content().getBytes(StandardCharsets.UTF_8);
            return response(200, JsonWriter.write(Map.of("type", "file", "encoding", "base64",
                    "size", bytes.length, "content", Base64.getEncoder().encodeToString(bytes),
                    "sha", stored.sha(), "path", contentPath)));
        }

        @Override public RestResponse put(URI uri, String bearerToken, String jsonBody, CancellationToken cancellation) {
            String path = uri.getPath().substring(uri.getPath().indexOf("/contents/") + "/contents/".length());
            Map<?, ?> body = (Map<?, ?>) JsonParser.parse(jsonBody);
            observedBranch = String.valueOf(body.get("branch"));
            if (!initialized) return response(409, "{\"message\":\"Git Repository is empty.\"}");
            Stored previous = files.get(path);
            Object sha = body.get("sha");
            if (previous != null && !previous.sha().equals(sha)) return response(409, "{\"message\":\"sha does not match\"}");
            if (previous == null && sha != null) return response(409, "{\"message\":\"file does not exist\"}");
            byte[] bytes = Base64.getDecoder().decode(String.valueOf(body.get("content")));
            String text = new String(bytes, StandardCharsets.UTF_8);
            lastContentWasUtf8 = text.contains("García");
            lastMessage = String.valueOf(body.get("message"));
            String newSha = "sha-" + (++revision);
            files.put(path, new Stored(text, newSha));
            String bodyResponse = JsonWriter.write(Map.of("content", Map.of("sha", newSha)));
            return response(previous == null ? 201 : 200, bodyResponse);
        }

        @Override public RestResponse delete(URI uri, String bearerToken, String jsonBody,
                CancellationToken cancellation) {
            lastDeleteBody = jsonBody;
            String path = uri.getPath().substring(uri.getPath().indexOf("/contents/") + "/contents/".length());
            Map<?, ?> body = (Map<?, ?>) JsonParser.parse(jsonBody);
            lastMessage = String.valueOf(body.get("message"));
            Stored previous = files.get(path);
            if (previous == null || !previous.sha().equals(body.get("sha"))) {
                return response(409, "{\"message\":\"sha does not match\"}");
            }
            files.remove(path);
            return response(200, "{}");
        }

        @Override public RestResponse post(URI uri, String bearerToken, String jsonBody,
                CancellationToken cancellation) {
            String path = uri.getPath();
            Map<?, ?> body = (Map<?, ?>) JsonParser.parse(jsonBody);
            if (path.endsWith("/git/trees")) {
                List<?> tree = (List<?>) body.get("tree");
                Map<?, ?> first = (Map<?, ?>) tree.get(0);
                pendingInitialPath = String.valueOf(first.get("path"));
                pendingInitialContent = String.valueOf(first.get("content"));
                return response(201, "{\"sha\":\"tree-sha\"}");
            }
            if (path.endsWith("/git/commits")) return response(201, "{\"sha\":\"commit-sha\"}");
            if (path.endsWith("/git/refs")) {
                if (initialized) return response(422, "{\"message\":\"Reference already exists\"}");
                initialized = true;
                String sha = "sha-" + (++revision);
                files.put(pendingInitialPath, new Stored(pendingInitialContent, sha));
                return response(201, "{\"ref\":\"refs/heads/main\"}");
            }
            return response(404, "{}");
        }

        private List<Map<String, Object>> directoryEntries(String prefix) {
            Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
            for (String path : files.keySet()) {
                if (!path.startsWith(prefix)) continue;
                String remainder = path.substring(prefix.length());
                int slash = remainder.indexOf('/');
                String name = slash < 0 ? remainder : remainder.substring(0, slash);
                String full = prefix + name;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("path", full);
                entry.put("name", name);
                entry.put("type", slash < 0 ? "file" : "dir");
                entry.put("sha", "entry-sha");
                entry.put("size", 1);
                entries.put(name, entry);
            }
            return new ArrayList<>(entries.values());
        }
    }
}
