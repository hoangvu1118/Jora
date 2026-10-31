package com.sefujo.resume;

import com.sefujo.common.exception.ConflictException;
import com.sefujo.common.exception.RateLimitException;
import com.sefujo.common.exception.ResourceNotFound;
import com.sefujo.job.entity.Job;
import com.sefujo.job.repository.JobRepository;
import com.sefujo.resume.content.ResumeContent;
import com.sefujo.resume.content.TailorSection;
import com.sefujo.resume.dto.ResumeVersionBriefResponse;
import com.sefujo.resume.dto.TailoredResult;
import com.sefujo.resume.entity.Resume;
import com.sefujo.resume.entity.ResumeVersion;
import com.sefujo.resume.extractor.LLMLock;
import com.sefujo.resume.extractor.ResumeContentParser;
import com.sefujo.resume.extractor.ResumeTailor;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ResumeVersionService {
    private final ResumeService resumeService;
    private final ResumeVersionRepository resumeVersionRepository;
    private final JobRepository jobRepository;
    private final ResumeContentParser resumeContentParser;
    private final ResumeTailor resumeTailor;
    private final LLMLock llmlock;

    private ResumeVersionBriefResponse toResumeVersionBriefResponse(ResumeVersion version) {
        return new ResumeVersionBriefResponse(
                version.getId(),
                version.getType(),
                version.getEditStatus(),
                version.getContent()
        );
    }

    public ResumeVersionBriefResponse createResumeVersion(Long userId, Long resumeId) {

        if(!llmlock.allowLLMRequest(resumeId)) {
            throw new RateLimitException("Too many Requests");
        }
        Resume resume = resumeService.getActiveResume(userId, resumeId);

        String rawText = resumeService.retrieveRawResume(resume);
        // Call Gemini for the reformat resume
        ResumeContent resumeContent = resumeContentParser.parse(rawText);

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
        resumeService.getOwnedResume(userId, resumeId);

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

    private ResumeContent toResumeContent(ResumeContent resumeContent, TailorSection tailorSection) {
        return new ResumeContent(
                resumeContent.header(),
                resumeContent.education(),
                tailorSection.experiences(),
                tailorSection.projects(),
                tailorSection.skillGroups(),
                resumeContent.extraSections()
        );
    }

    public TailoredResult tailorResume(Long userId, Long resumeId, Long jobId) {
        Resume resume = resumeService.getActiveResume(userId, resumeId);

        ResumeVersion parentVersion = resumeVersionRepository.findByResumeIdAndType(resumeId, ResumeVersionType.BASE)
                .orElseThrow(() -> new ConflictException("Please create BASE Resume for this Id first"));

        ResumeVersion existingTailoredVersion = resumeVersionRepository.findByResumeIdAndJobIdAndType(resumeId, jobId, ResumeVersionType.TAILORED)
                .orElse(null);
        if(existingTailoredVersion != null) {
            return new TailoredResult(
                    toResumeVersionBriefResponse(existingTailoredVersion),
                    false
            );
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFound("Cannot found job for this Id"));

        ResumeContent content = parentVersion.getContent();
        TailorSection tailorSection = new TailorSection(
                content.experiences(),
                content.projects(),
                content.skillGroups()
        );
        String description = job.getDescription();

        if(!llmlock.allowLLMRequest(resumeId)) {
            throw new RateLimitException("Too many Requests");
        }

        TailorSection processedSection = resumeTailor.tailorSections(description, tailorSection);
        ResumeContent processedContent = toResumeContent(content, processedSection);

        ResumeVersion tailoredVersion = createTailorVersion(parentVersion, resume, processedContent, job);
        return new TailoredResult(
                toResumeVersionBriefResponse(tailoredVersion),
                true
        );
    }

    private ResumeVersion createTailorVersion(ResumeVersion version, Resume resume, ResumeContent content, Job job) {
        try{
            ResumeVersion newResumeVersion = new ResumeVersion();
            newResumeVersion.setResume(resume);
            newResumeVersion.setParentVersion(version);
            newResumeVersion.setJob(job);
            newResumeVersion.setType(ResumeVersionType.TAILORED);
            newResumeVersion.updateContent(content);
            return resumeVersionRepository.saveAndFlush(newResumeVersion);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Can't save resume version, check if it has existed before");
        }
    }

}
