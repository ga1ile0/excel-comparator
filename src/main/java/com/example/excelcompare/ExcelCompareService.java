package com.example.excelcompare;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
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
     * Compare two .xlsx files on disk and write the annotated diff workbook
     * to {@code resultPath}.
     *
     * @return the comparison result; call {@link WorkbookComparisonResult#isIdentical()}
     *         for a quick check
     */
    public WorkbookComparisonResult compareAndWrite(
            Path file1, Path file2, Path resultPath) throws IOException {
        return compareAndWrite(file1, file2, resultPath, WorkbookComparator.EXCLUDE_NONE);
    }

    /**
     * Compare two .xlsx files on disk, skipping any cell pair for which {@code exclusionFilter}
     * returns {@code true}, and write the annotated diff workbook to {@code resultPath}.
     *
     * @param exclusionFilter predicate receiving a {@link CellContext}; return {@code true} to
     *                        skip the cell pair
     * @return the comparison result
     */
    public WorkbookComparisonResult compareAndWrite(
            Path file1, Path file2, Path resultPath,
            Predicate<CellContext> exclusionFilter) throws IOException {

        WorkbookComparisonResult result = workbookComparator.compare(file1, file2, exclusionFilter);

        try (OutputStream os = Files.newOutputStream(resultPath)) {
            result.getResultWorkbook().write(os);
        } finally {
            result.getResultWorkbook().close();
        }

        log.info("Comparison complete. Identical: {}. Cell differences: {}. Written to: {}",
                result.isIdentical(),
                result.getCellDifferences().size(),
                resultPath.toAbsolutePath());

        return result;
    }

    /**
     * Compare two files and return the result. The caller is responsible for
     * writing and closing the result workbook.
     */
    public WorkbookComparisonResult compare(Path file1, Path file2) throws IOException {
        return workbookComparator.compare(file1, file2);
    }

    /**
     * Compare two files, skipping any cell pair for which {@code exclusionFilter} returns
     * {@code true}. The caller is responsible for writing and closing the result workbook.
     *
     * @param exclusionFilter predicate receiving a {@link CellContext}; return {@code true} to
     *                        skip the cell pair
     */
    public WorkbookComparisonResult compare(Path file1, Path file2,
            Predicate<CellContext> exclusionFilter) throws IOException {
        return workbookComparator.compare(file1, file2, exclusionFilter);
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
