package com.a.prestamos.client;

import com.a.prestamos.model.dto.apiclient.ReniecResponseDto;
import com.a.prestamos.model.dto.apiclient.SunatResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class DocumentConsumer {
    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${app.reniec.api-url}")
    private String reniecApiUrl;

    @Value("${app.reniec.api-key}")
    private String reniecApiKey;

    @Value("${app.sunat.api-url}")
    private String sunatApiUrl;

    @Value("${app.sunat.api-key}")
    private String sunatApiKey;

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

    public SunatResponseDto verifyByRuc(String ruc) {
        return webClientBuilder.build()
                .get()
                .uri(sunatApiUrl, uriBuilder -> uriBuilder.queryParam("numero", ruc).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sunatApiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Error al llamar a la API de SUNAT: " + errorBody)))
                )
                .bodyToMono(SunatResponseDto.class)
                .block();
    }
}
