package com.example.excelcompare;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Holds the outcome of comparing two workbooks:
 * <ul>
 *   <li>whether they are identical</li>
 *   <li>all cell-level differences</li>
 *   <li>the annotated result workbook (first workbook with differing cells highlighted red)</li>
 * </ul>
 */
@Getter
@Builder
@ToString(exclude = "resultWorkbook")
public class WorkbookComparisonResult {

    /** True only when every sheet, cell value, and formatting attribute is identical. */
    private final boolean identical;

    /** Per-cell differences (value + formatting). */
    private final List<CellDifference> cellDifferences;

    /**
     * Higher-level structural differences: missing sheets, differing sheet counts,
     * different row/column counts, etc.
     */
    private final List<String> structuralDifferences;

    /**
     * A copy of the first workbook where every differing cell is highlighted in red.
     * Write this to a file with {@code workbook.write(outputStream)}.
     */
    private final Workbook resultWorkbook;

    /**
     * Serializes the result workbook to a byte array suitable for Allure attachments.
     * The workbook is NOT closed after this call.
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            resultWorkbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize result workbook", e);
        }
    }

    /**
     * Writes the result workbook to {@code path} as an {@code .xlsx} file and closes the
     * workbook.
     *
     * @param path target file path (will be created or overwritten)
     */
    public void saveToFile(Path path) throws IOException {
        try (OutputStream os = Files.newOutputStream(path);
             Workbook wb = resultWorkbook) {
            wb.write(os);
        }
    }
}
