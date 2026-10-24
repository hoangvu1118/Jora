package com.sefujo.resume.extractor;

import com.sefujo.resume.content.ResumeContent;
import com.sefujo.resume.content.TailorSection;

public interface ResumeContentParser {
    ResumeContent parse(String rawText);
}
