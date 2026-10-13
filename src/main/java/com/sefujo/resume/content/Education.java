package com.sefujo.resume.content;

import java.time.YearMonth;

public record Education(
        String university,
        String degree,
        YearMonth startDate,
        YearMonth endDate
){}
