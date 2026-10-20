package com.sefujo.common.config;

import com.google.genai.Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class APIConfig {

    @Bean
    public Client client() {
        return new Client();
    }
}
