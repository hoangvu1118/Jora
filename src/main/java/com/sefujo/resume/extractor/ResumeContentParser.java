package com.sefujo.resume.extractor;

import com.sefujo.resume.content.ResumeContent;

public interface ResumeContentParser {
    ResumeContent parse(String rawText);
}
