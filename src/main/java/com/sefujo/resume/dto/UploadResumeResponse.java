package com.sefujo.resume.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;


public record UploadResumeResponse(
        Long id,
        String fileName,
        Long fileSize,
        String fileType
) {}
