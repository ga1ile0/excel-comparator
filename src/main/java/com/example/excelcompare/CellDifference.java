package com.example.excelcompare;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes all differences found between two corresponding cells.
 */
@Getter
@RequiredArgsConstructor
@ToString
public class CellDifference {

    private final int sheetIndex;
    private final String sheetName;
    private final int rowIndex;
    private final int colIndex;

    private final List<String> differences = new ArrayList<>();

    public void addDifference(String description) {
        differences.add(description);
    }

    public boolean hasDifferences() {
        return !differences.isEmpty();
    }

    public List<String> getDifferences() {
        return List.copyOf(differences);
    }
}
