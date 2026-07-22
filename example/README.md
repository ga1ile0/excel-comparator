# Excel Export Test Framework

A self-contained Maven submodule that tests Excel exports in bulk using TestNG's parallel data
provider and the `WorkbookComparator` library.

---

## How it works

Each subfolder under `src/test/resources/exports/` defines one export test case.

```
src/test/resources/exports/
└── my-report/
    ├── endpoint.json   ← HTTP request definition (required)
    ├── body.json       ← Request body, if any (optional)
    ├── setup.sql       ← DB seed script, if needed (optional)
    └── expected.xlsx   ← Golden file to compare against (required)
```

At test time `ExportDefinitionLoader` scans the `/exports` classpath directory, builds an
`ExportDefinition` for every subfolder, and feeds them to the `exports` TestNG `@DataProvider`.
The provider runs up to **20 threads in parallel** (configurable in `testng.xml`).

For each export the test:

1. Runs `setup.sql` against the configured database (if present and DB is configured).
2. Fetches the export via HTTP using the configured `ExportFetcher`.
3. Compares the response bytes against `expected.xlsx` using `WorkbookComparator`.
4. If there are differences, attaches a colour-highlighted diff workbook to the Allure report.
5. Fails the test with a human-readable diff summary.

---

## Adding a new export test

1. Create a subfolder under `src/test/resources/exports/` — the folder name becomes the test name.

2. Add `endpoint.json`:
   ```json
   {
     "method": "GET",
     "url": "/api/exports/my-report",
     "queryParams": { "year": "2024" },
     "headers": { "Accept": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" },
     "excludeCells": [
       { "sheet": 0, "row": 0, "col": 3 }
     ],
     "excludeRows": [1]
   }
   ```

3. *(Optional)* Add `body.json` if the endpoint takes a request body:
   ```json
   { "filter": "active" }
   ```

4. *(Optional)* Add `setup.sql` to seed the database before the export is fetched.

5. Generate `expected.xlsx` — see the next section.

---

## Generating `expected.xlsx`

`expected.xlsx` is **not** generated automatically. You produce it once, review it, then commit
it. After that, the test uses it as the golden file.

```bash
# 1. Apply the seed data
psql -U testuser -d testdb -f src/test/resources/exports/my-report/setup.sql

# 2. Fetch the export and save it
curl -o src/test/resources/exports/my-report/expected.xlsx \
     http://localhost:8080/api/exports/my-report

# 3. Open the file, verify its contents, then commit it
git add src/test/resources/exports/my-report/expected.xlsx
git commit -m "Add expected.xlsx for my-report export test"
```

---

## Switching to async mode

Some APIs return `202 Accepted` immediately and generate the file in the background. Set
`export.fetcher=async` in `test.properties` and add an `async` block to `endpoint.json`:

```json
{
  "method": "POST",
  "url": "/api/exports/my-report/request",
  "async": {
    "listUrl": "/api/exports/ready",
    "downloadUrl": "/api/exports/{exportId}/download",
    "idField": "exportId",
    "timeoutSeconds": 60
  }
}
```

`AsyncExportFetcher` will:
1. POST to `url` and capture the ID from the `idField` path in the `202` response body.
2. Poll `listUrl` every 2 s until the ID appears (up to `timeoutSeconds`).
3. GET `downloadUrl` (with `{exportId}` replaced) and return the bytes.

---

## Configuring authentication

Open `ExcelExportIT.setUp()` and replace the TODO comment with your real auth mechanism:

```java
// Example: browser cookies captured with Selenium / Playwright
RequestSpecProvider specProvider = () -> new RequestSpecBuilder()
        .setBaseUri(baseUri)
        .addCookies(AuthHelper.getBrowserCookies())
        .build();

// Example: Bearer token
RequestSpecProvider specProvider = () -> new RequestSpecBuilder()
        .setBaseUri(baseUri)
        .addHeader("Authorization", "Bearer " + TokenProvider.getToken())
        .build();
```

---

## Configuring `test.properties`

| Property           | Default                             | Description                              |
|--------------------|-------------------------------------|------------------------------------------|
| `test.base-uri`    | `http://localhost:8080`             | Base URI of the application under test   |
| `export.fetcher`   | `direct`                            | `direct` or `async`                      |
| `test.db.url`      | *(unset — SQL setup skipped)*       | JDBC URL for the test database           |
| `test.db.username` | —                                   | Database username                        |
| `test.db.password` | —                                   | Database password                        |

Override any property on the command line:

```bash
mvn test -Dtest.base-uri=http://staging.example.com -Dexport.fetcher=async
```

---

## Adjusting thread count

Edit `src/test/resources/testng.xml`:

```xml
<suite name="Excel Export Tests" data-provider-thread-count="20">
```

Set `data-provider-thread-count` to the desired parallelism. Each export test runs in its own
thread; keep in mind that all threads share the same `sqlRunner` and `fetcher` instances, which
are both thread-safe.

---

## Running the tests

```bash
# From the repo root (builds library + example)
mvn test -pl example

# With a custom base URI
mvn test -pl example -Dtest.base-uri=http://staging.example.com
```
