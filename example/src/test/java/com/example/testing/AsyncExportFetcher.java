package com.example.testing;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import org.awaitility.Awaitility;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

@RequiredArgsConstructor
public class AsyncExportFetcher implements ExportFetcher {

    private final RequestSpecProvider specProvider;

    @Override
    public byte[] fetch(ExportDefinition def) {
        EndpointConfig config = def.getConfig();
        EndpointConfig.AsyncConfig async = config.getAsync();

        String exportId = requestGeneration(def);

        Awaitility.await()
                .atMost(async.getTimeoutSeconds(), TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> isReady(exportId, async));

        return download(exportId, async);
    }

    private String requestGeneration(ExportDefinition def) {
        EndpointConfig config = def.getConfig();
        RequestSpecification req = given().spec(specProvider.get());

        if (def.getBodyJson() != null) {
            req = req.body(def.getBodyJson()).contentType(ContentType.JSON);
        }
        if (!config.getQueryParams().isEmpty()) {
            req = req.queryParams(config.getQueryParams());
        }
        if (!config.getHeaders().isEmpty()) {
            req = req.headers(config.getHeaders());
        }

        return req
                .when()
                .request(config.getMethod(), resolveUrl(config))
                .then()
                .statusCode(202)
                .extract()
                .path(config.getAsync().getIdField());
    }

    private boolean isReady(String exportId, EndpointConfig.AsyncConfig async) {
        List<String> ids = given().spec(specProvider.get())
                .when()
                .get(async.getListUrl())
                .then()
                .statusCode(200)
                .extract()
                .path(async.getIdField());
        return ids != null && ids.contains(exportId);
    }

    private byte[] download(String exportId, EndpointConfig.AsyncConfig async) {
        String url = async.getDownloadUrl().replace("{exportId}", exportId);
        return given().spec(specProvider.get())
                .when()
                .get(url)
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }

    private String resolveUrl(EndpointConfig config) {
        String url = config.getUrl();
        for (var entry : config.getPathVariables().entrySet()) {
            url = url.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return url;
    }
}
