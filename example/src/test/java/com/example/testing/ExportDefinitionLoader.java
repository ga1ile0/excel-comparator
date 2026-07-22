package com.example.testing;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class ExportDefinitionLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<ExportDefinition> loadAll() throws Exception {
        URL resource = ExportDefinitionLoader.class.getResource("/exports");
        if (resource == null) {
            throw new IllegalStateException("No /exports directory found on classpath. "
                    + "Add export folders under src/test/resources/exports/");
        }
        Path exportsDir = Paths.get(resource.toURI());
        try (Stream<Path> dirs = Files.list(exportsDir)) {
            return dirs
                    .filter(Files::isDirectory)
                    .sorted()
                    .map(ExportDefinitionLoader::load)
                    .toList();
        }
    }

    private static ExportDefinition load(Path dir) {
        try {
            EndpointConfig config = MAPPER.readValue(
                    dir.resolve("endpoint.json").toFile(), EndpointConfig.class);

            Path bodyFile = dir.resolve("body.json");
            String bodyJson = Files.exists(bodyFile) ? Files.readString(bodyFile) : null;

            return ExportDefinition.builder()
                    .name(dir.getFileName().toString())
                    .directory(dir)
                    .config(config)
                    .bodyJson(bodyJson)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load export definition from: " + dir, e);
        }
    }
}
