package com.sefujo.resume.extractor.gemini;

import com.sefujo.resume.content.ResumeContent;
import com.sefujo.resume.extractor.ResumeContentParser;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


@Component
public class GeminiResumeContentParser implements ResumeContentParser {

    private final GeminiClient geminiClient;
    private final JsonMapper mapper;
    private final String promptTemplate;

    public GeminiResumeContentParser(JsonMapper mapper, GeminiClient client) throws IOException {
        this.mapper = mapper;
        this.geminiClient = client;
        promptTemplate = new ClassPathResource("prompts/prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }


    @Override
    public ResumeContent parse(String rawText) {
        String finalPrompt = promptTemplate.replace("{{RESUME_TEXT}}", rawText);
        String json = geminiClient.generate(finalPrompt);
        return mapper.readValue(json, ResumeContent.class);
    }
}
