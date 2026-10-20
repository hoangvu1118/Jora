package com.sefujo.resume.dto;

import com.sefujo.resume.ResumeVersionStatus;
import com.sefujo.resume.ResumeVersionType;
import com.sefujo.resume.content.ResumeContent;

public record ResumeVersionBriefResponse(
        Long id,
        ResumeVersionType type,
        ResumeVersionStatus status,
        ResumeContent content
) {}
