package com.sefujo.resume.extractor;

import com.sefujo.common.exception.LlmUnavailableException;
import com.sefujo.resume.content.ResumeContent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


@Component
public class GeminiResumeContentParser implements ResumeContentParser {

    private final JsonMapper mapper;
    private final Client client;
    private final String promptTemplate;

    public GeminiResumeContentParser(JsonMapper mapper, Client client) throws IOException {
        this.mapper = mapper;
        this.client = client;
        promptTemplate = new ClassPathResource("prompts/prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private GenerateContentResponse sendToLLM(String rawText) {
        String finalPrompt = promptTemplate.replace("{{RESUME_TEXT}}", rawText);

        try{
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-3.5-flash-lite",
                    finalPrompt,
                    null
            );
            return response;

        } catch (Exception e){
            throw new LlmUnavailableException("Cannot communicate with LLMs", e);
        }
    }

    @Override
    public ResumeContent parse(String rawText) {
        GenerateContentResponse response = sendToLLM(rawText);
        String json = response.text();
        ResumeContent content = mapper.readValue(json, ResumeContent.class);
        return content;
    }
}
