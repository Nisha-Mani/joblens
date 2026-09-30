package com.joblens.analysis.ai;

/** The only door to an LLM. Implementations throw {@link AiException} subclasses on failure. */
public interface AiClient {

    AiResponse complete(AiRequest request);
}
