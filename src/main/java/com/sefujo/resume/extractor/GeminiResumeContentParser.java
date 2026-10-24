package com.sefujo.resume.extractor;

import com.sefujo.common.exception.LlmUnavailableException;
import com.sefujo.resume.content.ResumeContent;
import com.sefujo.resume.content.TailorSection;
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
    private final String tailorPrompt;

    public GeminiResumeContentParser(JsonMapper mapper, Client client) throws IOException {
        this.mapper = mapper;
        this.client = client;
        promptTemplate = new ClassPathResource("prompts/prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        tailorPrompt = new ClassPathResource("prompts/resumeTailor.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private GenerateContentResponse sendToLLM(String rawText) {
        String finalPrompt = promptTemplate.replace("{{RESUME_TEXT}}", rawText);

        try{
            return client.models.generateContent(
                    "gemini-3.5-flash-lite",
                    finalPrompt,
                    null
            );

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

    private GenerateContentResponse tailorSectionLLM(String description, String sections){
        String finalPrompt = tailorPrompt
                .replace("{{JOB_DESCRIPTION}}", description)
                .replace("{{RESUME_JSON}}", sections);
        try{
            return client.models.generateContent(
                    "gemini-3.5-flash-lite",
                    finalPrompt,
                    null
            );

        } catch (Exception e){
            throw new LlmUnavailableException("Cannot communicate with LLMs", e);
        }
    }

    @Override
    public TailorSection tailorSections(String description, String sections) {
        GenerateContentResponse response = tailorSectionLLM(description, sections);
        String json = response.text();
        return mapper.readValue(json, TailorSection.class);
    }
}
