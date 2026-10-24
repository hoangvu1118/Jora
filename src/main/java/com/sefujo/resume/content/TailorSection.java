package com.sefujo.resume.content;

import lombok.Data;

import java.util.List;

public record TailorSection(
        List<Experience> experiences,
        List<Project> projects,
        List<SkillGroup> skillGroups
) {
    public TailorSection{
        if (projects == null) {
            projects = List.of();
        }
        if (experiences == null) {
            experiences = List.of();
        }
        if (skillGroups == null) {
            skillGroups = List.of();
        }
    }
}
