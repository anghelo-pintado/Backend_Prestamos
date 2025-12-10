package com.a.prestamos.model.dto.apiclient;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReniecResponseDto(
        @JsonProperty("first_name")
        String firstName,

        @JsonProperty("first_last_name")
        String firstLastName,

        @JsonProperty("second_last_name")
        String secondLastName,

        @JsonProperty("full_name")
        String fullName,

        @JsonProperty("document_number")
        String documentNumber
) {}
