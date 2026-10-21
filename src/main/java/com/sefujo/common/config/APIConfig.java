package com.sefujo.common.config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class APIConfig {

    @Bean
    public Client client(@Value("${gemini.api.key}") String apiKey) {
        return Client.builder()
                .apiKey(apiKey).build();
    }
}
