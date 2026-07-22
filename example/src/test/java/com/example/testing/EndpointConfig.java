package com.example.testing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.example.excelcompare.CellContext;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EndpointConfig {

    private String method = "GET";
    private String url;
    private Map<String, String> pathVariables = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
    private Map<String, String> headers = new HashMap<>();
    private List<CellExclusion> excludeCells = new ArrayList<>();
    private List<Integer> excludeRows = new ArrayList<>();
    private AsyncConfig async;

    public Predicate<CellContext> toExclusionPredicate() {
        return ctx -> {
            if (excludeRows.contains(ctx.rowIndex())) return true;
            return excludeCells.stream().anyMatch(e ->
                    e.getSheet() == ctx.sheetIndex() &&
                    e.getRow() == ctx.rowIndex() &&
                    e.getCol() == ctx.colIndex());
        };
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AsyncConfig {
        private String listUrl;
        private String downloadUrl;
        private String idField = "id";
        private int timeoutSeconds = 30;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CellExclusion {
        private int sheet;
        private int row;
        private int col;
    }
}
