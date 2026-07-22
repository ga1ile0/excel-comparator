# sample-report

This folder contains the test definition for the `sample-report` Excel export.

## Files

| File            | Purpose                                                    |
|-----------------|------------------------------------------------------------|
| `endpoint.json` | Describes the HTTP request used to trigger the export      |
| `body.json`     | Optional request body (empty `{}` for this GET endpoint)   |
| `setup.sql`     | Seeds the database before the export is fetched            |
| `expected.xlsx` | **Must be generated manually** — see instructions below    |

## Generating `expected.xlsx`

`expected.xlsx` is not committed to source control automatically. You must generate it once
for each new export definition, then commit it alongside the other files.

**Steps:**

1. Make sure the application is running and connected to the test database.
2. Run the seed script against your test database:
   ```sql
   -- contents of setup.sql
   DELETE FROM sample_report_items;
   INSERT INTO sample_report_items (id, name, amount, report_date) VALUES
       (1, 'Item A', 100.00, '2024-01-15'),
       (2, 'Item B', 250.50, '2024-01-16'),
       (3, 'Item C',  75.25, '2024-01-17');
   ```
3. Call the endpoint and save the response as `expected.xlsx` in this folder:
   ```bash
   curl -o expected.xlsx http://localhost:8080/api/exports/sample-report
   ```
4. Verify the file opens correctly in Excel / LibreOffice.
5. Commit `expected.xlsx` to this folder.

From this point on, the test will compare every future export against this file and
highlight any differing cells in red in an Allure attachment.
