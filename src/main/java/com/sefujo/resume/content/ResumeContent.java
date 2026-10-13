package com.sefujo.resume.content;

import java.util.List;

public record ResumeContent(
        Header header,
        List<Education> education,
        List<Experience> experiences,
        List<Project> projects,
        List<SkillGroup> skillGroups,
        List<ExtraSection> extraSections
) {
    public ResumeContent {
        if (projects == null) {
            projects = List.of();
        }
        if (experiences == null) {
            experiences = List.of();
        }
        if (skillGroups == null) {
            skillGroups = List.of();
        }
        if (extraSections == null) {
            extraSections = List.of();
        }

    }
}

