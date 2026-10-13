package com.sefujo.resume.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
public class UploadResumeResponse {
    String fileName;
    Long fileSize;
    String fileType;
}
