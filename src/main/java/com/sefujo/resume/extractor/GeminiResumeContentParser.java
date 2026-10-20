package com.sefujo.resume.extractor;

import com.sefujo.common.exception.FileReadingException;
import com.sefujo.resume.content.ResumeContent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


@RequiredArgsConstructor
@Component
public class GeminiResumeContentParser implements ResumeContentParser {

    private final JsonMapper mapper;
    private final Client client;


    private GenerateContentResponse sendToLLM(String rawText) {
        String finalPrompt;
        try{
             String prompt = new ClassPathResource("./prompt.txt")
                    .getContentAsString(StandardCharsets.UTF_8);

             finalPrompt = prompt.replace("{{RESUME_TEXT}}", rawText);
        } catch (IOException e){
            throw new FileReadingException("Cannot read the file", e);
        }

        try{
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-3.5-flash-lite",
                    finalPrompt,
                    null
            );
            return response;

        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ResumeContent parse(String rawText) {
        GenerateContentResponse response = sendToLLM(rawText);
        String json = response.text();
        ResumeContent content = mapper.readValue(json, ResumeContent.class);
        return content;
    }
}
