package com.sefujo.resume.content;

import java.time.YearMonth;
import java.util.List;

public record Experience(
        String jobTitle,
        String companyName,
        List<String> bullets,
        YearMonth startDate,
        YearMonth endDate

) {

}
