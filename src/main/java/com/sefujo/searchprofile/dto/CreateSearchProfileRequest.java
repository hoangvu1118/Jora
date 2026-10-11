package com.sefujo.searchprofile.dto;

import com.sefujo.searchprofile.EmploymentLevel;
import com.sefujo.searchprofile.EmploymentType;
import com.sefujo.searchprofile.WorkplaceType;
import lombok.Data;

import java.util.Set;

@Data
public class CreateSearchProfileRequest {
    private String name;
    private EmploymentLevel level;
    private Set<String> jobTitles;
    private Set<String> skills;
    private Set<String> locations;
    private Set<EmploymentType> employmentTypes;
    private Set<WorkplaceType> workplaceTypes;
}

