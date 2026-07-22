package com.example.testing;

public interface ExportFetcher {
    byte[] fetch(ExportDefinition def) throws Exception;
}
