package com.example.excelcompare;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility component for color and attribute comparison helpers shared
 * across the comparison pipeline.
 */
@Slf4j
@Component
public class CellFormatComparator {

    /**
     * Returns the ARGB hex string for an {@link XSSFColor}, or {@code "none"} if null.
     */
    public static String colorHex(XSSFColor color) {
        if (color == null) return "none";
        String argb = color.getARGBHex();
        return argb != null ? argb : "none";
    }

    /**
     * Appends a difference entry to {@code out} if {@code a} and {@code b} differ.
     */
    public static <T> void diffAttr(List<String> out, String label, T a, T b) {
        if (a == null && b == null) return;
        if (a == null || !a.equals(b)) {
            out.add(String.format("%s: '%s' -> '%s'", label, a, b));
        }
    }
}
