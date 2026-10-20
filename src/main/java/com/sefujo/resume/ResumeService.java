package com.sefujo.resume;

import com.sefujo.common.exception.FileReadingException;
import com.sefujo.common.exception.InvalidFileException;
import com.sefujo.common.exception.ResourceNotFound;
import com.sefujo.resume.content.ResumeContent;
import com.sefujo.resume.dto.UploadResumeResponse;
import com.sefujo.resume.entity.Resume;
import com.sefujo.resume.extractor.ResumeContentParser;
import com.sefujo.resume.extractor.ResumeExtractor;
import com.sefujo.storage.FileStorage;
import com.sefujo.user.User;
import com.sefujo.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final FileStorage fileStorage;
    private final UserService userService;
    private final ResumeExtractor resumeExtractor;
    private final ResumeContentParser resumeContentParser;

    private UploadResumeResponse toResponse(Resume resume) {
        long resumeId = resume.getId();
        String fileName = resume.getFileName();
        Long fileSize = resume.getFileSize();
        String fileType = resume.getContentType();
        UploadResumeResponse response = new UploadResumeResponse(
                resumeId, fileName, fileSize, fileType
        );
        return response;
    }

    @Transactional
    public UploadResumeResponse uploadResume(Long userId, MultipartFile file) {
        if(!isValidPdf(file)) {
            throw new InvalidFileException("Invalid file");
        }
        String key = fileStorage.save(userId, file);
        User user = userService.getUserById(userId);
        try{
            Resume resume = new Resume(user, key, file.getOriginalFilename(), file.getSize(), file.getContentType());
            return toResponse(resumeRepository.saveAndFlush(resume));
        } catch (RuntimeException e) {
            fileStorage.delete(key);
            throw e; // error so the user get the message
        }
    }

    private boolean isValidPdf(MultipartFile file) {
        // 1. Check for null and empty
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 2. Check MIME type
        String contentType = file.getContentType();
        if (contentType != null && contentType.equals("application/pdf")) {
            return true;
        }

        // Optional fallback: check file name extension if content type is missing/unreliable
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            return true;
        }

        return false;
    }

    public String extractResume(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFound("Cannot found resume for this Id"));
        try(
            InputStream pdfStream = fileStorage.load(resume.getFilePath());
            ){
            String rawText = resumeExtractor.extractResume(pdfStream);
            resume.setRawText(rawText);
            resumeRepository.saveAndFlush(resume);
            return rawText;
        } catch (IOException e){
            throw new FileReadingException("Cannot read this file", e);
        }
    }

    public ResumeContent parseResumeContent(String rawText) {
        return resumeContentParser.parse(rawText);
    }
}
