package com.example.excelcompare;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Spring service facade for workbook comparison.
 * Inject this bean wherever you need to compare Excel files.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelCompareService {

    private final WorkbookComparator workbookComparator;

    /**
     * Compare two .xlsx files on disk, skipping any cell pair for which {@code exclusionFilter}
     * returns {@code true}, and write the annotated diff workbook to {@code resultPath}.
     * Pass {@link WorkbookComparator#EXCLUDE_NONE} to compare all cells.
     *
     * @param exclusionFilter predicate receiving a {@link CellContext}; return {@code true} to
     *                        skip the cell pair
     * @return the comparison result
     */
    public WorkbookComparisonResult compareAndWrite(
            Path file1, Path file2, Path resultPath,
            Predicate<CellContext> exclusionFilter) throws IOException {

        WorkbookComparisonResult result = workbookComparator.compare(file1, file2, exclusionFilter);
        result.saveToFile(resultPath);

        log.info("Comparison complete. Identical: {}. Cell differences: {}. Written to: {}",
                result.isIdentical(),
                result.getCellDifferences().size(),
                resultPath.toAbsolutePath());

        return result;
    }

    /**
     * Compare two .xlsx files and return the result. The caller is responsible for closing the
     * result workbook.
     * Pass {@link WorkbookComparator#EXCLUDE_NONE} to compare all cells.
     *
     * @param exclusionFilter predicate receiving a {@link CellContext}; return {@code true} to
     *                        skip the cell pair
     */
    public WorkbookComparisonResult compare(Path file1, Path file2,
            Predicate<CellContext> exclusionFilter) throws IOException {
        return workbookComparator.compare(file1, file2, exclusionFilter);
    }

    /**
     * Compare a workbook on disk ({@code file1}) with one supplied as raw bytes ({@code file2Bytes},
     * e.g. downloaded from an endpoint). The caller is responsible for closing the result workbook.
     * Pass {@link WorkbookComparator#EXCLUDE_NONE} to compare all cells.
     *
     * @param exclusionFilter predicate receiving a {@link CellContext}; return {@code true} to
     *                        skip the cell pair
     */
    public WorkbookComparisonResult compare(Path file1, byte[] file2Bytes,
            Predicate<CellContext> exclusionFilter) throws IOException {
        return workbookComparator.compare(file1, file2Bytes, exclusionFilter);
    }

    /**
     * Compare two workbooks from streams. The caller is responsible for closing the result
     * workbook. Pass {@link WorkbookComparator#EXCLUDE_NONE} to compare all cells.
     *
     * @param exclusionFilter predicate receiving a {@link CellContext}; return {@code true} to
     *                        skip the cell pair
     */
    public WorkbookComparisonResult compare(InputStream is1, InputStream is2,
            Predicate<CellContext> exclusionFilter) throws IOException {
        return workbookComparator.compare(is1, is2, exclusionFilter);
    }

    /**
     * Log a human-readable summary of the result.
     */
    public void logSummary(WorkbookComparisonResult result) {
        if (result.isIdentical()) {
            log.info("Workbooks are IDENTICAL.");
            return;
        }

        log.info("Workbooks differ.");

        if (!result.getStructuralDifferences().isEmpty()) {
            log.info("Structural differences ({}):", result.getStructuralDifferences().size());
            result.getStructuralDifferences().forEach(d -> log.info("  - {}", d));
        }

        if (!result.getCellDifferences().isEmpty()) {
            log.info("Cell differences ({}):", result.getCellDifferences().size());
            result.getCellDifferences().forEach(d -> log.info("  - {}", d));
        }
    }
}
