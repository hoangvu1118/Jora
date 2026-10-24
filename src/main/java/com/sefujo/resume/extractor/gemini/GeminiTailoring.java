package com.sefujo.resume.extractor.gemini;

import com.sefujo.resume.content.TailorSection;
import com.sefujo.resume.extractor.ResumeTailor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class GeminiTailoring implements ResumeTailor {

    private final JsonMapper mapper;
    private final GeminiClient geminiClient;
    private final String tailorPrompt;

    public GeminiTailoring(JsonMapper mapper, GeminiClient geminiClient) throws IOException {
        this.mapper = mapper;
        this.geminiClient = geminiClient;
        this.tailorPrompt = new ClassPathResource("prompts/resumeTailor.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public TailorSection tailorSections(String description, TailorSection sections) {
        String stringSection = mapper.writeValueAsString(sections);
        String prompt = tailorPrompt
                .replace("{{JOB_DESCRIPTION}}", description)
                .replace("{{RESUME_JSON}}", stringSection);
        String json = geminiClient.generate(prompt);
        return mapper.readValue(json, TailorSection.class);
    }

}
