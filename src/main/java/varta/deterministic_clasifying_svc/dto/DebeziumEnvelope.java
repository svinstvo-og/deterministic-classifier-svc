package varta.deterministic_clasifying_svc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DebeziumEnvelope<T> (
        DebeziumPayload<T> payload
) { }
