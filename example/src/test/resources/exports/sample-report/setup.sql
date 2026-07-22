-- Clear and seed the sample report dataset.
-- This dataset is small but complete: covers all columns and edge-case values
-- that the export template exercises. It matches the content of expected.xlsx.

DELETE FROM sample_report_items;

INSERT INTO sample_report_items (id, name, amount, report_date) VALUES
    (1, 'Item A', 100.00, '2024-01-15'),
    (2, 'Item B', 250.50, '2024-01-16'),
    (3, 'Item C',  75.25, '2024-01-17');
