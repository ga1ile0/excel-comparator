package com.example.excelcompare;

/**
 * Immutable snapshot of a cell pair, passed to exclusion predicates before comparison.
 *
 * <p>Use this with {@link WorkbookComparator#compare(java.nio.file.Path, java.nio.file.Path, java.util.function.Predicate)}
 * to skip cells based on their position, content, or any combination of the two:
 *
 * <pre>{@code
 * // Exclude cells whose value in either workbook matches a regex
 * Pattern ignore = Pattern.compile("N/A|TBD");
 * result = comparator.compare(file1, file2,
 *     ctx -> ignore.matcher(ctx.value1()).matches()
 *         || ignore.matcher(ctx.value2()).matches());
 *
 * // Exclude the first header row
 * result = comparator.compare(file1, file2, ctx -> ctx.rowIndex() == 0);
 * }</pre>
 *
 * @param sheetIndex zero-based index of the sheet containing this cell pair
 * @param sheetName  name of the sheet
 * @param rowIndex   zero-based row index
 * @param colIndex   zero-based column index
 * @param value1     string representation of the cell value from workbook 1 (never {@code null})
 * @param value2     string representation of the cell value from workbook 2 (never {@code null})
 */
public record CellContext(
        int sheetIndex,
        String sheetName,
        int rowIndex,
        int colIndex,
        String value1,
        String value2
) {}
