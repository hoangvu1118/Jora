package com.sefujo.resume.extractor;

import com.sefujo.resume.entity.Resume;

import java.io.InputStream;

public interface ResumeExtractor {
    String extractResume(InputStream inputStream);
}
