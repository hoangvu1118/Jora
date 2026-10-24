package com.sefujo.resume.extractor.gemini;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.sefujo.common.exception.LlmUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiClient {

    private final Client client;
    private final String model;

    public GeminiClient(Client client,
                        @Value("${gemini.model:gemini-3.5-flash-lite}") String model) {
        this.client = client;
        this.model = model;
    }

    public String generate(String prompt) {
        try {
            GenerateContentResponse response = client.models.generateContent(model, prompt, null);
            return response.text();
        } catch (Exception e) {
            throw new LlmUnavailableException("Cannot communicate with LLMs", e);
        }
    }
}