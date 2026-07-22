package com.example.excelcompare;

import io.qameta.allure.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ExcelCompareIT {

    @Autowired
    private WorkbookComparator workbookComparator;

    @Test
    public void responseWorkbookMatchesExpected() throws IOException {
        byte[] responseBytes = given()
                .when()
                .get("/api/your-endpoint")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();

        try (InputStream actual   = new ByteArrayInputStream(responseBytes);
             InputStream expected = getClass().getResourceAsStream("/expected/your-file.xlsx")) {

            assertThat(expected)
                    .as("Expected file not found on classpath")
                    .isNotNull();

            WorkbookComparisonResult result = workbookComparator.compare(actual, expected,
                    WorkbookComparator.EXCLUDE_NONE);

            if (!result.isIdentical()) {
                attachDiffWorkbook(result.toBytes());
                result.getResultWorkbook().close();
            }

            assertThat(result.isIdentical())
                    .as(buildDiffMessage(result))
                    .isTrue();
        }
    }

    @Attachment(value = "Excel Diff Workbook", type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileExtension = ".xlsx")
    private byte[] attachDiffWorkbook(byte[] diffBytes) {
        return diffBytes;
    }

    private String buildDiffMessage(WorkbookComparisonResult result) {
        StringBuilder sb = new StringBuilder("Workbooks differ:\n");
        result.getStructuralDifferences().forEach(d -> sb.append("  [structural] ").append(d).append("\n"));
        result.getCellDifferences().forEach(d -> sb.append("  [cell] ").append(d).append("\n"));
        return sb.toString();
    }
}
