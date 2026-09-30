package com.joblens.resume;

/** Turns raw resume text into structured data. Implementations must not call external services. */
public interface ResumeParser {

    ParsedResume parse(String text);
}
