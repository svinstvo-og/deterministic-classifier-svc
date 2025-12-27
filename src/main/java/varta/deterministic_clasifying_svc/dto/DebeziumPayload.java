package varta.deterministic_clasifying_svc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DebeziumPayload<T> (
        T before,
        T after,
        String op,
        Long ts_ms) { }
