package com.usergems.morningprep.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuration for RestClient instances used by API clients.
 */
@Configuration
public class RestClientConfig {

    @Value("${usergems.calendar-api.base-url}")
    private String calendarApiBaseUrl;

    @Value("${usergems.person-api.base-url}")
    private String personApiBaseUrl;

    @Value("${usergems.calendar-api.timeout-seconds:30}")
    private int calendarApiTimeoutSeconds;

    @Value("${usergems.person-api.timeout-seconds:30}")
    private int personApiTimeoutSeconds;

    /**
     * RestClient for Calendar API calls.
     */
    @Bean
    public RestClient calendarRestClient() {
        return RestClient.builder()
            .baseUrl(calendarApiBaseUrl)
            .requestFactory(createRequestFactory(calendarApiTimeoutSeconds))
            .build();
    }

    /**
     * RestClient for Person API calls.
     */
    @Bean
    public RestClient personRestClient() {
        return RestClient.builder()
            .baseUrl(personApiBaseUrl)
            .requestFactory(createRequestFactory(personApiTimeoutSeconds))
            .build();
    }

    private SimpleClientHttpRequestFactory createRequestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
    }
}
