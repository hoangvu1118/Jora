package com.sefujo.resume.extractor;

import com.sefujo.resume.content.TailorSection;

public interface ResumeTailor {

    TailorSection tailorSections(String description, TailorSection sections);
}
