package com.a.prestamos.client;

import com.a.prestamos.model.dto.ReniecResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ReniecConsumer {
    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${app.reniec.api-url}")
    private String reniecApiUrl;

    @Value("${app.reniec.api-key}")
    private String reniecApiKey;

    public ReniecResponseDto verifyByDni(String dni) {
        return webClientBuilder.build()
                .get()
                .uri(reniecApiUrl, uriBuilder -> uriBuilder.queryParam("numero", dni).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + reniecApiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Error al llamar a la API de RENIEC: " + errorBody)))
                )
                .bodyToMono(ReniecResponseDto.class)
                .block();
    }
}
