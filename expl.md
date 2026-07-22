Good news: the comparator already has everything you need (`WorkbookComparator`, `toBytes()`, exclusion filter). The main design question is how to wire hundreds of scenarios without writing a test per export.

---

## Recommended approach: filesystem-driven `@DataProvider`

### 1. One folder per export under `src/test/resources/exports/`

```
exports/
  invoices/
    setup.sql          ← DELETE + INSERT for this export's dataset
    body.json          ← request body (empty file for GET)
    endpoint.json      ← { "method": "POST", "url": "/api/exports/invoices",
                            "excludeCells": [{"sheet":0,"row":0,"col":5}] }
    expected.xlsx      ← golden workbook generated for the same dataset
  purchase-orders/
    setup.sql
    body.json
    endpoint.json
    expected.xlsx
```

Everything a test needs is in one folder. Adding a new export = drop a new folder. No code changes.

---

### 2. A single test class driven by `@DataProvider`

```java
@DataProvider(name = "exports")
public static Object[][] exports() throws Exception {
    URL root = ExcelExportIT.class.getResource("/exports");
    return Files.list(Paths.get(root.toURI()))
                .filter(Files::isDirectory)
                .sorted()
                .map(dir -> new Object[]{dir.getFileName().toString(), dir})
                .toArray(Object[][]::new);
}

@Test(dataProvider = "exports")
@Description("{0}")   // Allure display name = folder name
public void verifyExport(String exportName, Path dir) throws Exception {
    runSql(dir.resolve("setup.sql"));
    byte[] actual = callEndpoint(dir.resolve("endpoint.json"), dir.resolve("body.json"));

    ExclusionConfig exc = loadExclusions(dir.resolve("endpoint.json"));
    try (InputStream a = new ByteArrayInputStream(actual);
         InputStream e = Files.newInputStream(dir.resolve("expected.xlsx"))) {

        WorkbookComparisonResult result = comparator.compare(a, e, exc.toPredicate());
        if (!result.isIdentical()) attachDiff(result.toBytes());
        assertThat(result.isIdentical()).as(buildDiffMessage(result)).isTrue();
    }
}
```

TestNG names each case `verifyExport[invoices]`, `verifyExport[purchase-orders]` — visible in Allure automatically.

---

### 3. Other key design decisions

| Concern | Recommendation |
|---|---|
| **Endpoint config** | `endpoint.json` per folder (self-contained), not an enum — no code change needed for new exports |
| **Timestamp exclusion** | `"excludeCells"` array in `endpoint.json` maps directly to the `Predicate<CellContext>` your comparator already supports |
| **DB isolation** | Run SQL scripts sequentially (default TestNG) or use `@BeforeMethod` cleanup. Parallel DB writes on the same table = race conditions |
| **Auth/headers** | Pull from a shared `RestAssured` `RequestSpecification` configured once in `@BeforeSuite` |
| **Allure steps** | Wrap `runSql`, `callEndpoint`, `compare` in `@Step` — gives a clean step timeline per test case |
| **New export onboarding** | Script that generates the folder skeleton + fires the real endpoint against a seeded DB to capture `expected.xlsx` |

---

### 4. The `@Factory` alternative

If you want **one class instance per export** (better Allure grouping, easier `@BeforeMethod`/`@AfterMethod` scoping):

```java
@Factory
public static Object[] factory() {
    return // stream of new ExcelExportIT(dir) per folder
}
```

More boilerplate but each test shows as a fully independent class in the report tree.

---

**Bottom line**: the `@DataProvider` approach is the least code, easiest to maintain, and pairs naturally with your existing `WorkbookComparator`. You'd write the test infrastructure once (~100 lines), then every new export is just a new folder.

Want me to scaffold the full implementation in your project?









Here's a design that covers the realistic cases you'll hit:

**Simple GET export:**
```json
{
  "method": "GET",
  "url": "/api/exports/invoices",
  "excludeCells": [
    { "sheet": 0, "row": 0, "col": 5 }
  ]
}
```

**POST with body (body.json is the separate file):**
```json
{
  "method": "POST",
  "url": "/api/exports/purchase-orders",
  "excludeCells": [
    { "sheet": 0, "row": 0, "col": 5 }
  ]
}
```

**With query params and path variables:**
```json
{
  "method": "GET",
  "url": "/api/exports/departments/{departmentId}/report",
  "pathVariables": {
    "departmentId": "42"
  },
  "queryParams": {
    "year": "2024",
    "format": "xlsx"
  },
  "excludeCells": [
    { "sheet": 0, "row": 0, "col": 5 },
    { "sheet": 0, "row": 1, "col": 5 }
  ]
}
```

**With extra headers (e.g. tenant ID, locale):**
```json
{
  "method": "POST",
  "url": "/api/exports/summary",
  "headers": {
    "X-Tenant-Id": "tenant-abc",
    "Accept-Language": "pl-PL"
  },
  "excludeCells": []
}
```

**With exclude by pattern instead of exact cell** (more flexible for "skip entire row 0" type of cases):
```json
{
  "method": "GET",
  "url": "/api/exports/timesheets",
  "excludeRows": [0],
  "excludeCells": [
    { "sheet": 1, "row": 3, "col": 2 }
  ]
}
```

---

A few notes:

- **`excludeCells` vs `excludeRows`** — exact cell is precise but fragile if the export structure shifts; row-level exclusion is better for headers that always contain a timestamp
- **`body.json` stays separate** — keeps `endpoint.json` readable and lets you version the body independently
- **Path variables as template syntax** `{name}` — standard convention, easy to parse with `String.replace`
- **No auth in the file** — auth token/cookie should come from your global RestAssured `RequestSpecification`, not hardcoded per export

Want me to also design the Java class that deserializes this and builds the RestAssured call?
