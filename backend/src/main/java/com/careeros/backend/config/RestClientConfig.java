package com.careeros.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Takes Boot's auto-configured builder rather than RestClient.builder().
     *
     * A bare RestClient.builder() resolves its own request factory by classpath
     * detection and lands on JdkClientHttpRequestFactory, whose constructor calls
     * HttpClient.newHttpClient(). That builds a selector — and its wakeup pipe —
     * before any request is ever made, so a broken loopback takes the whole
     * context down at startup. It also silently ignores every spring.http.client.*
     * property, including timeouts.
     *
     * The injected builder honours those properties instead.
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
