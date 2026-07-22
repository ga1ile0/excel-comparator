package com.example.testing;

import io.restassured.specification.RequestSpecification;

@FunctionalInterface
public interface RequestSpecProvider {
    RequestSpecification get();
}
