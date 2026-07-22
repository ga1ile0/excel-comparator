package com.example.testing;

import com.example.excelcompare.WorkbookComparator;
import com.example.excelcompare.WorkbookComparisonResult;
import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;
import io.qameta.allure.Description;
import io.qameta.allure.testng.AllureTestNg;
import io.restassured.builder.RequestSpecBuilder;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Listeners(AllureTestNg.class)
public class ExcelExportIT {

    private static ExportFetcher fetcher;
    private static SqlScriptRunner sqlRunner;
    private static final WorkbookComparator COMPARATOR = new WorkbookComparator();

    @BeforeSuite
    public static void setUp() throws IOException {
        Properties props = new Properties();
        try (InputStream is = ExcelExportIT.class.getResourceAsStream("/test.properties")) {
            if (is != null) props.load(is);
        }

        String baseUri = props.getProperty("test.base-uri", "http://localhost:8080");
        String mode    = props.getProperty("export.fetcher", "direct");

        RequestSpecProvider specProvider = () -> new RequestSpecBuilder()
                .setBaseUri(baseUri)
                // TODO: replace with your real auth (e.g. cookies from browser session)
                // .addCookies(AuthHelper.getBrowserCookies())
                .build();

        fetcher = "async".equals(mode)
                ? new AsyncExportFetcher(specProvider)
                : new DirectExportFetcher(specProvider);

        String dbUrl  = props.getProperty("test.db.url");
        String dbUser = props.getProperty("test.db.username");
        String dbPass = props.getProperty("test.db.password");

        if (dbUrl != null) {
            sqlRunner = new SqlScriptRunner(dbUrl, dbUser, dbPass);
        }
    }

    @DataProvider(name = "exports", parallel = true)
    public Object[][] exports() throws Exception {
        List<ExportDefinition> defs = ExportDefinitionLoader.loadAll();
        return defs.stream()
                .map(def -> new Object[]{def})
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "exports")
    @Description("Verifies the generated Excel export matches the expected workbook")
    public void verifyExport(ExportDefinition def) throws Exception {
        Allure.label("export", def.getName());

        Allure.step("Run SQL setup: " + def.getName(), () -> {
            if (sqlRunner == null) {
                log.warn("No DB configured — skipping SQL setup for {}", def.getName());
                return;
            }
            Path sqlFile = def.getDirectory().resolve("setup.sql");
            if (Files.exists(sqlFile)) {
                sqlRunner.run(sqlFile);
            } else {
                log.warn("No setup.sql found for {}", def.getName());
            }
        });

        byte[] actual = Allure.step("Fetch export: " + def.getName(), () -> fetcher.fetch(def));

        Path expectedPath = def.getDirectory().resolve("expected.xlsx");
        assertThat(expectedPath).as("expected.xlsx not found for: " + def.getName()).exists();

        WorkbookComparisonResult result;
        try (InputStream actualStream   = new ByteArrayInputStream(actual);
             InputStream expectedStream = Files.newInputStream(expectedPath)) {

            result = Allure.step("Compare workbooks: " + def.getName(), () ->
                    COMPARATOR.compare(actualStream, expectedStream,
                            def.getConfig().toExclusionPredicate()));
        }

        if (!result.isIdentical()) {
            byte[] diffBytes = result.toBytes();
            attachDiffWorkbook(def.getName(), diffBytes);
        }
        result.getResultWorkbook().close();

        assertThat(result.isIdentical())
                .as(buildDiffMessage(def.getName(), result))
                .isTrue();
    }

    @Attachment(value = "Excel Diff: {0}",
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fileExtension = ".xlsx")
    private byte[] attachDiffWorkbook(String exportName, byte[] diffBytes) {
        return diffBytes;
    }

    private String buildDiffMessage(String name, WorkbookComparisonResult result) {
        StringBuilder sb = new StringBuilder("Export '").append(name).append("' differs:\n");
        result.getStructuralDifferences()
              .forEach(d -> sb.append("  [structural] ").append(d).append('\n'));
        result.getCellDifferences()
              .forEach(d -> sb.append("  [cell] ").append(d).append('\n'));
        return sb.toString();
    }
}
