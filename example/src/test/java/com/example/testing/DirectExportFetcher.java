package com.example.testing;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;

import static io.restassured.RestAssured.given;

@RequiredArgsConstructor
public class DirectExportFetcher implements ExportFetcher {

    private final RequestSpecProvider specProvider;

    @Override
    public byte[] fetch(ExportDefinition def) {
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
