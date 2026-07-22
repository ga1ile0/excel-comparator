package com.example.testing;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

@Value
@Builder
public class ExportDefinition {
    String name;
    Path directory;
    EndpointConfig config;
    String bodyJson;  // null when no body.json present

    @Override
    public String toString() {
        return name;
    }
}
