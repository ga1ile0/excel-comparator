package com.example.excelcompare;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Compares two Excel workbooks (.xlsx) sheet-by-sheet, cell-by-cell,
 * covering both cell values and all formatting attributes.
 *
 * <p>Usage:
 * <pre>{@code
 * WorkbookComparisonResult result = workbookComparator.compare(path1, path2);
 * result.getResultWorkbook().write(new FileOutputStream("diff.xlsx"));
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkbookComparator {

    private static final byte[] DIFF_COLOR_RGB = new byte[]{(byte) 0xFF, 0x00, 0x00};

    /** Predicate that excludes no cells — used as the default when no filter is supplied. */
    static final Predicate<CellContext> EXCLUDE_NONE = ctx -> false;

    // Public entry points

    public WorkbookComparisonResult compare(Path file1, Path file2) throws IOException {
        return compare(file1, file2, EXCLUDE_NONE);
    }

    /**
     * Compare two workbooks, skipping any cell pair for which {@code exclusionFilter} returns
     * {@code true}. Excluded cells produce no {@link CellDifference} and are not highlighted in
     * the result workbook.
     *
     * @param exclusionFilter predicate receiving a {@link CellContext}; return {@code true} to
     *                        skip the cell pair
     */
    public WorkbookComparisonResult compare(Path file1, Path file2,
            Predicate<CellContext> exclusionFilter) throws IOException {
        try (InputStream is1 = Files.newInputStream(file1);
             InputStream is2 = Files.newInputStream(file2);
             Workbook wb1 = WorkbookFactory.create(is1);
             Workbook wb2 = WorkbookFactory.create(is2)) {
            return compareWorkbooks(wb1, wb2, exclusionFilter);
        }
    }

    public WorkbookComparisonResult compare(InputStream is1, InputStream is2) throws IOException {
        return compare(is1, is2, EXCLUDE_NONE);
    }

    /**
     * Compare two workbooks from streams, skipping any cell pair for which
     * {@code exclusionFilter} returns {@code true}.
     *
     * @param exclusionFilter predicate receiving a {@link CellContext}; return {@code true} to
     *                        skip the cell pair
     */
    public WorkbookComparisonResult compare(InputStream is1, InputStream is2,
            Predicate<CellContext> exclusionFilter) throws IOException {
        try (Workbook wb1 = WorkbookFactory.create(is1);
             Workbook wb2 = WorkbookFactory.create(is2)) {
            return compareWorkbooks(wb1, wb2, exclusionFilter);
        }
    }

    // Core comparison

    private WorkbookComparisonResult compareWorkbooks(Workbook wb1, Workbook wb2,
            Predicate<CellContext> exclusionFilter) {
        List<CellDifference> cellDiffs       = new ArrayList<>();
        List<String>         structuralDiffs = new ArrayList<>();

        int sheets1 = wb1.getNumberOfSheets();
        int sheets2 = wb2.getNumberOfSheets();

        if (sheets1 != sheets2) {
            structuralDiffs.add(String.format("Sheet count differs: %d vs %d", sheets1, sheets2));
        }

        XSSFWorkbook resultWb = cloneWorkbook((XSSFWorkbook) wb1);

        int sheetsToCompare = Math.min(sheets1, sheets2);
        for (int si = 0; si < sheetsToCompare; si++) {
            Sheet sheet1      = wb1.getSheetAt(si);
            Sheet sheet2      = wb2.getSheetAt(si);
            Sheet resultSheet = resultWb.getSheetAt(si);

            if (!sheet1.getSheetName().equals(sheet2.getSheetName())) {
                structuralDiffs.add(String.format(
                        "Sheet %d name differs: '%s' vs '%s'",
                        si, sheet1.getSheetName(), sheet2.getSheetName()));
            }

            compareSheet(wb1, wb2, sheet1, sheet2, resultSheet, si, cellDiffs, structuralDiffs,
                    exclusionFilter);
        }

        for (int si = sheetsToCompare; si < sheets1; si++) {
            structuralDiffs.add("Sheet only in workbook1: " + wb1.getSheetAt(si).getSheetName());
        }
        for (int si = sheetsToCompare; si < sheets2; si++) {
            structuralDiffs.add("Sheet only in workbook2: " + wb2.getSheetAt(si).getSheetName());
        }

        boolean identical = cellDiffs.isEmpty() && structuralDiffs.isEmpty();

        return WorkbookComparisonResult.builder()
                .identical(identical)
                .cellDifferences(List.copyOf(cellDiffs))
                .structuralDifferences(List.copyOf(structuralDiffs))
                .resultWorkbook(resultWb)
                .build();
    }

    // Sheet-level comparison

    private void compareSheet(
            Workbook wb1, Workbook wb2,
            Sheet sheet1, Sheet sheet2, Sheet resultSheet,
            int sheetIndex,
            List<CellDifference> cellDiffs,
            List<String> structuralDiffs,
            Predicate<CellContext> exclusionFilter) {

        int lastRow = Math.max(lastRowNum(sheet1), lastRowNum(sheet2));

        for (int ri = 0; ri <= lastRow; ri++) {
            Row row1   = sheet1.getRow(ri);
            Row row2   = sheet2.getRow(ri);
            Row rowRes = getOrCreateRow(resultSheet, ri);

            int lastCol = Math.max(lastColNum(row1), lastColNum(row2));
            for (int ci = 0; ci <= lastCol; ci++) {
                Cell cell1 = getCell(row1, ci);
                Cell cell2 = getCell(row2, ci);

                CellContext ctx = new CellContext(
                        sheetIndex, sheet1.getSheetName(), ri, ci,
                        extractValue(cell1), extractValue(cell2));
                if (exclusionFilter.test(ctx)) {
                    continue;
                }

                CellDifference diff = compareCell(
                        wb1, wb2, cell1, cell2, sheetIndex, sheet1.getSheetName(), ri, ci);

                if (diff.hasDifferences()) {
                    cellDiffs.add(diff);
                    highlightCell(rowRes, ci, (XSSFWorkbook) resultSheet.getWorkbook());
                }
            }
        }
    }

    // Cell-level comparison

    private CellDifference compareCell(
            Workbook wb1, Workbook wb2,
            Cell cell1, Cell cell2,
            int sheetIndex, String sheetName, int ri, int ci) {

        CellDifference diff = new CellDifference(sheetIndex, sheetName, ri, ci);

        String val1 = extractValue(cell1);
        String val2 = extractValue(cell2);
        if (!val1.equals(val2)) {
            diff.addDifference(String.format("value: '%s' -> '%s'", val1, val2));
        }

        String formula1 = extractFormula(cell1);
        String formula2 = extractFormula(cell2);
        if (!formula1.equals(formula2)) {
            diff.addDifference(String.format("formula: '%s' -> '%s'", formula1, formula2));
        }

        CellType type1 = cell1 == null ? CellType.BLANK : cell1.getCellType();
        CellType type2 = cell2 == null ? CellType.BLANK : cell2.getCellType();
        if (type1 != type2 && type1 != CellType.FORMULA && type2 != CellType.FORMULA) {
            diff.addDifference(String.format("cellType: %s -> %s", type1, type2));
        }

        if (cell1 != null && cell2 != null) {
            compareFormatting(wb1, wb2, cell1, cell2).forEach(diff::addDifference);
        } else if (cell1 == null && cell2 != null && cell2.getCellStyle() != null) {
            diff.addDifference("formatting: cell1 absent, cell2 has style");
        } else if (cell1 != null && cell2 == null && cell1.getCellStyle() != null) {
            diff.addDifference("formatting: cell1 has style, cell2 absent");
        }

        return diff;
    }

    // Formatting comparison

    private List<String> compareFormatting(Workbook wb1, Workbook wb2, Cell cell1, Cell cell2) {
        List<String> diffs = new ArrayList<>();
        CellStyle s1 = cell1.getCellStyle();
        CellStyle s2 = cell2.getCellStyle();
        if (s1 == null && s2 == null) return diffs;

        Font f1 = wb1.getFontAt(s1.getFontIndex());
        Font f2 = wb2.getFontAt(s2.getFontIndex());
        compareFonts(f1, f2, diffs);
        compareFill(s1, s2, diffs);
        compareBorders(s1, s2, diffs);
        compareAlignment(s1, s2, diffs);
        CellFormatComparator.diffAttr(diffs, "numberFormat", s1.getDataFormatString(), s2.getDataFormatString());
        CellFormatComparator.diffAttr(diffs, "locked", s1.getLocked(), s2.getLocked());
        CellFormatComparator.diffAttr(diffs, "hidden", s1.getHidden(), s2.getHidden());

        return diffs;
    }

    private void compareFonts(Font f1, Font f2, List<String> diffs) {
        if (f1 == null && f2 == null) return;
        if (f1 == null) { diffs.add("font: absent in cell1"); return; }
        if (f2 == null) { diffs.add("font: absent in cell2"); return; }

        CellFormatComparator.diffAttr(diffs, "font.name",           f1.getFontName(),           f2.getFontName());
        CellFormatComparator.diffAttr(diffs, "font.heightInPoints", f1.getFontHeightInPoints(),  f2.getFontHeightInPoints());
        CellFormatComparator.diffAttr(diffs, "font.bold",           f1.getBold(),               f2.getBold());
        CellFormatComparator.diffAttr(diffs, "font.italic",         f1.getItalic(),             f2.getItalic());
        CellFormatComparator.diffAttr(diffs, "font.underline",      f1.getUnderline(),          f2.getUnderline());
        CellFormatComparator.diffAttr(diffs, "font.strikeout",      f1.getStrikeout(),          f2.getStrikeout());
        CellFormatComparator.diffAttr(diffs, "font.typeOffset",     f1.getTypeOffset(),         f2.getTypeOffset());

        if (f1 instanceof XSSFFont xf1 && f2 instanceof XSSFFont xf2) {
            CellFormatComparator.diffAttr(diffs, "font.color",
                    CellFormatComparator.colorHex(xf1.getXSSFColor()),
                    CellFormatComparator.colorHex(xf2.getXSSFColor()));
        } else {
            CellFormatComparator.diffAttr(diffs, "font.colorIndex", f1.getColor(), f2.getColor());
        }
    }

    private void compareFill(CellStyle s1, CellStyle s2, List<String> diffs) {
        CellFormatComparator.diffAttr(diffs, "fill.pattern", s1.getFillPattern(), s2.getFillPattern());
        if (s1 instanceof XSSFCellStyle xs1 && s2 instanceof XSSFCellStyle xs2) {
            CellFormatComparator.diffAttr(diffs, "fill.fgColor",
                    CellFormatComparator.colorHex(xs1.getFillForegroundXSSFColor()),
                    CellFormatComparator.colorHex(xs2.getFillForegroundXSSFColor()));
            CellFormatComparator.diffAttr(diffs, "fill.bgColor",
                    CellFormatComparator.colorHex(xs1.getFillBackgroundXSSFColor()),
                    CellFormatComparator.colorHex(xs2.getFillBackgroundXSSFColor()));
        } else {
            CellFormatComparator.diffAttr(diffs, "fill.fgColorIndex", s1.getFillForegroundColor(), s2.getFillForegroundColor());
            CellFormatComparator.diffAttr(diffs, "fill.bgColorIndex", s1.getFillBackgroundColor(), s2.getFillBackgroundColor());
        }
    }

    private void compareBorders(CellStyle s1, CellStyle s2, List<String> diffs) {
        CellFormatComparator.diffAttr(diffs, "border.top",    s1.getBorderTop(),    s2.getBorderTop());
        CellFormatComparator.diffAttr(diffs, "border.bottom", s1.getBorderBottom(), s2.getBorderBottom());
        CellFormatComparator.diffAttr(diffs, "border.left",   s1.getBorderLeft(),   s2.getBorderLeft());
        CellFormatComparator.diffAttr(diffs, "border.right",  s1.getBorderRight(),  s2.getBorderRight());

        if (s1 instanceof XSSFCellStyle xs1 && s2 instanceof XSSFCellStyle xs2) {
            CellFormatComparator.diffAttr(diffs, "border.topColor",
                    CellFormatComparator.colorHex(xs1.getTopBorderXSSFColor()),
                    CellFormatComparator.colorHex(xs2.getTopBorderXSSFColor()));
            CellFormatComparator.diffAttr(diffs, "border.bottomColor",
                    CellFormatComparator.colorHex(xs1.getBottomBorderXSSFColor()),
                    CellFormatComparator.colorHex(xs2.getBottomBorderXSSFColor()));
            CellFormatComparator.diffAttr(diffs, "border.leftColor",
                    CellFormatComparator.colorHex(xs1.getLeftBorderXSSFColor()),
                    CellFormatComparator.colorHex(xs2.getLeftBorderXSSFColor()));
            CellFormatComparator.diffAttr(diffs, "border.rightColor",
                    CellFormatComparator.colorHex(xs1.getRightBorderXSSFColor()),
                    CellFormatComparator.colorHex(xs2.getRightBorderXSSFColor()));
        } else {
            CellFormatComparator.diffAttr(diffs, "border.topColorIndex",    s1.getTopBorderColor(),    s2.getTopBorderColor());
            CellFormatComparator.diffAttr(diffs, "border.bottomColorIndex", s1.getBottomBorderColor(), s2.getBottomBorderColor());
            CellFormatComparator.diffAttr(diffs, "border.leftColorIndex",   s1.getLeftBorderColor(),   s2.getLeftBorderColor());
            CellFormatComparator.diffAttr(diffs, "border.rightColorIndex",  s1.getRightBorderColor(),  s2.getRightBorderColor());
        }
    }

    private void compareAlignment(CellStyle s1, CellStyle s2, List<String> diffs) {
        CellFormatComparator.diffAttr(diffs, "align.horizontal",  s1.getAlignment(),         s2.getAlignment());
        CellFormatComparator.diffAttr(diffs, "align.vertical",    s1.getVerticalAlignment(), s2.getVerticalAlignment());
        CellFormatComparator.diffAttr(diffs, "align.wrapText",    s1.getWrapText(),          s2.getWrapText());
        CellFormatComparator.diffAttr(diffs, "align.indention",   s1.getIndention(),         s2.getIndention());
        CellFormatComparator.diffAttr(diffs, "align.rotation",    s1.getRotation(),          s2.getRotation());
        CellFormatComparator.diffAttr(diffs, "align.shrinkToFit", s1.getShrinkToFit(),       s2.getShrinkToFit());
    }

    // Result workbook: highlight differing cells in red

    private void highlightCell(Row row, int colIndex, XSSFWorkbook wb) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);

        XSSFCellStyle newStyle = wb.createCellStyle();
        CellStyle existing = cell.getCellStyle();
        if (existing != null) newStyle.cloneStyleFrom(existing);

        newStyle.setFillForegroundColor(new XSSFColor(DIFF_COLOR_RGB, null));
        newStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cell.setCellStyle(newStyle);
    }

    // Workbook cloning

    private XSSFWorkbook cloneWorkbook(XSSFWorkbook source) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            source.write(bos);
            return new XSSFWorkbook(new ByteArrayInputStream(bos.toByteArray()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to clone workbook", e);
        }
    }

    // Value / formula extraction

    private String extractValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case NUMERIC -> Double.toString(cell.getNumericCellValue());
            case STRING  -> cell.getStringCellValue();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> extractCachedFormulaValue(cell);
            case BLANK   -> "";
            case ERROR   -> "ERROR:" + cell.getErrorCellValue();
            default      -> "";
        };
    }

    private String extractCachedFormulaValue(Cell cell) {
        return switch (cell.getCachedFormulaResultType()) {
            case NUMERIC -> Double.toString(cell.getNumericCellValue());
            case STRING  -> cell.getStringCellValue();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case ERROR   -> "ERROR:" + cell.getErrorCellValue();
            default      -> "";
        };
    }

    private String extractFormula(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.FORMULA) return "";
        return cell.getCellFormula();
    }

    // Small utilities

    private int lastRowNum(Sheet sheet)  { return sheet == null ? 0 : sheet.getLastRowNum(); }
    private int lastColNum(Row row)      { return (row == null || row.getLastCellNum() < 0) ? 0 : row.getLastCellNum(); }

    private Cell getCell(Row row, int col) {
        if (row == null) return null;
        return row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }
}
