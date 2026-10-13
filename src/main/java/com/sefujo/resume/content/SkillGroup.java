package com.sefujo.resume.content;

import java.util.List;

public record SkillGroup(
        String category, // e.g Languages, Frameworks, etc.
        List<String> items // Python, java, etc.
) {}
