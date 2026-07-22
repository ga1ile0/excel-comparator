package com.example.excelcompare;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
public class ExcelCompareController {

    private final ExcelCompareService excelCompareService;

    /**
     * POST /api/excel/compare
     *
     * Accepts two .xlsx files as multipart form fields {@code file1} and {@code file2}.
     * Returns the annotated diff workbook as a file download, and sets the header
     * {@code X-Workbooks-Identical} to {@code true} or {@code false}.
     */
    @PostMapping(value = "/compare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> compare(
            @RequestParam("file1") MultipartFile file1,
            @RequestParam("file2") MultipartFile file2) throws IOException {

        WorkbookComparisonResult result;

        try (var is1 = file1.getInputStream();
             var is2 = file2.getInputStream()) {
            result = excelCompareService.compare(is1, is2, WorkbookComparator.EXCLUDE_NONE);
        }

        excelCompareService.logSummary(result);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            result.getResultWorkbook().write(bos);
        } finally {
            result.getResultWorkbook().close();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("diff.xlsx").build());
        headers.add("X-Workbooks-Identical", String.valueOf(result.isIdentical()));

        return ResponseEntity.ok().headers(headers).body(bos.toByteArray());
    }
}
