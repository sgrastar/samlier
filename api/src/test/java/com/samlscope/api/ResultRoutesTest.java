package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ResultRoutesTest {
    @Test
    void returnsThePersistedBytesWithoutReserializingThem() throws Exception {
        var expected = "{\n  \"schema_version\": \"1\"\n}\n".getBytes(StandardCharsets.UTF_8);
        var report = "<!doctype html><title>report</title>".getBytes(StandardCharsets.UTF_8);
        var app = Javalin.create(config -> ResultRoutes.register(
                config, ignored -> expected, ignored -> report)).start(0);
        try {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + app.port()
                                    + "/api/runs/run_0123456789ABCDEFGHJKMNPQRS/result.json"))
                            .GET().build(), HttpResponse.BodyHandlers.ofByteArray());

            assertEquals(200, response.statusCode());
            org.junit.jupiter.api.Assertions.assertArrayEquals(expected, response.body());
            assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
            assertEquals("nosniff", response.headers().firstValue("x-content-type-options").orElseThrow());
            assertEquals("application/json;charset=utf-8",
                    response.headers().firstValue("content-type").orElseThrow());
            var html = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + app.port()
                                    + "/api/runs/run_0123456789ABCDEFGHJKMNPQRS/report.html"))
                            .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, html.statusCode());
            org.junit.jupiter.api.Assertions.assertArrayEquals(report, html.body());
            assertEquals("attachment; filename=\"samlscope-report.html\"",
                    html.headers().firstValue("content-disposition").orElseThrow());
        } finally {
            app.stop();
        }
    }
}
