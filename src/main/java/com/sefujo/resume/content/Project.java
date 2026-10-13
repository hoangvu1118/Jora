package com.sefujo.resume.content;

import java.util.List;

public record Project(
  String title,
  List<String> technologies,
  List<String> bullets

){}
