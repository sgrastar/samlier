package org.samlier.runner.outbox;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboundKind;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;

/** The only production boundary that performs HTTP for persisted outbox intents. */
public final class HttpOutboundSender implements OutboundSender {
    private final HttpClient client;
    private final TranscriptRecorder transcript;
    private final Clock clock;

    public HttpOutboundSender(HttpClient client, TranscriptRecorder transcript, Clock clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static HttpOutboundSender create(TranscriptRecorder transcript, Clock clock) {
        return new HttpOutboundSender(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(20))
                .build(), transcript, clock);
    }

    @Override
    public SendResult send(String runId, OutboundAction action, byte[] ephemeralCredential) throws Exception {
        if (action.kind() != OutboundKind.ECP_SOAP) {
            throw new IllegalArgumentException("HTTP sender does not implement " + action.kind());
        }
        if (ephemeralCredential == null || ephemeralCredential.length == 0) {
            throw new IllegalArgumentException("ECP SOAP requires an ephemeral credential");
        }
        var contentType = "text/xml; charset=utf-8";
        var authorization = "Basic " + Base64.getEncoder().encodeToString(ephemeralCredential);
        var requestHeaders = Map.of(
                "Content-Type", List.of(contentType),
                "Accept", List.of("text/xml, application/soap+xml"),
                "Authorization", List.of(authorization));
        var outbound = transcript.record(new TranscriptInput(
                runId, Direction.OUTBOUND, clock.instant(), action.actionId(), "POST",
                action.target().toString(), null, requestHeaders, action.payload(), contentType,
                null, action.payload(), Map.of("type", "EcpSoapRequest", "kind", action.kind().name())));

        var request = HttpRequest.newBuilder(action.target())
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", contentType)
                .header("Accept", "text/xml, application/soap+xml")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofByteArray(action.payload()))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        var responseHeaders = new LinkedHashMap<String, List<String>>();
        response.headers().map().forEach((name, values) -> responseHeaders.put(name, List.copyOf(values)));
        var responseContentType = response.headers().firstValue("content-type").orElse(null);
        var inbound = transcript.record(new TranscriptInput(
                runId, Direction.INBOUND, clock.instant(), action.actionId(), "POST",
                action.target().toString(), response.statusCode(), responseHeaders, response.body(),
                responseContentType, null, response.body(),
                Map.of("type", "EcpSoapResponse", "request_transcript", outbound.id())));
        return new SendResult(false,
                Map.of("http_status", response.statusCode(), "response_bytes", response.body().length), inbound.id());
    }
}
