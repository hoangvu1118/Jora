package com.sefujo.resume;

import com.sefujo.common.exception.*;
import com.sefujo.resume.content.ResumeContent;
import com.sefujo.resume.dto.ResumeVersionBriefResponse;
import com.sefujo.resume.dto.UploadResumeResponse;
import com.sefujo.resume.entity.Resume;
import com.sefujo.resume.entity.ResumeVersion;
import com.sefujo.resume.extractor.ResumeContentParser;
import com.sefujo.resume.extractor.ResumeExtractor;
import com.sefujo.storage.FileStorage;
import com.sefujo.user.User;
import com.sefujo.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
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

    private void checkResumeArchive(Resume resume) {
        if(resume.isArchived()) {
            throw new ResumeArchiveException("Resume is archived");
        }
    }

    public String extractResume(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFound("Cannot found resume for this Id"));
        checkResumeArchive(resume);
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

    public String retrieveRawResume(Resume resume) {
        if(resume.getRawText() == null || resume.getRawText().isEmpty()) {
            throw new InvalidFileException("Please extract the raw text first");
        }
        return resume.getRawText();
    }

    private ResumeVersionBriefResponse toResumeVersionBriefResponse(ResumeVersion version) {
        return new ResumeVersionBriefResponse(
                version.getId(),
                version.getType(),
                version.getEditStatus(),
                version.getContent()
        );
    }

    public ResumeVersionBriefResponse createResumeVersion(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFound("Cannot found resume for this Id"));
        checkResumeArchive(resume);
        String rawText = retrieveRawResume(resume);
        // Call Gemini for the reformat resume
        ResumeContent resumeContent = parseResumeContent(rawText);

        ResumeVersion newVersion;
        try{
            newVersion = resumeVersionRepository.saveAndFlush(
                    new ResumeVersion(resume, resumeContent)
            );
        } catch (DataIntegrityViolationException e){
            // another request created the BASE first: return that one
            newVersion = resumeVersionRepository
                                .findByResumeIdAndType(resumeId, ResumeVersionType.BASE)
                                .orElseThrow(() -> e);   // shouldn't happen; if it does, it's a real error
        }
        return toResumeVersionBriefResponse(newVersion);
    }

    public Optional<ResumeVersionBriefResponse> findBaseResume(Long userId, Long resumeId) {
        resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFound("Cannot found resume for this Ids"));

        Optional<ResumeVersion> version = resumeVersionRepository.findByResumeIdAndType(resumeId, ResumeVersionType.BASE);
        if(version.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(toResumeVersionBriefResponse(version.get()));
    }

    public ResumeVersionBriefResponse getResumeVersion(Long userId, Long resumeId, Long versionId) {
        ResumeVersion version = resumeVersionRepository.findByIdAndResumeIdAndResumeUserId(versionId, resumeId, userId)
                .orElseThrow(() -> new ResourceNotFound("Cannot found resume version for this Ids"));
        return toResumeVersionBriefResponse(version);
    }

    public ResumeContent parseResumeContent(String rawText) {
        return resumeContentParser.parse(rawText);
    }

    public void archiveResume(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFound("Cannot found resume for this Id"));
        checkResumeArchive(resume);

        resume.archive();
        resumeRepository.saveAndFlush(resume);
    }
}
