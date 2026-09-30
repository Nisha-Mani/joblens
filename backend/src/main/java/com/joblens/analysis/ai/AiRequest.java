package com.joblens.analysis.ai;

/**
 * A provider-neutral request for structured output.
 *
 * @param purpose      what is being asked (lets test doubles respond appropriately)
 * @param system       instructions
 * @param user         the data to work on
 * @param schemaName   name of the JSON schema the answer must follow
 * @param schemaJson   the JSON schema (strict mode) as a string
 */
public record AiRequest(AiPurpose purpose, String system, String user, String schemaName, String schemaJson) { }
