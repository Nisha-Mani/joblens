package com.joblens.profile.dto;

import com.joblens.profile.UserProfile;

public record ProfileResponse(
    String name, String headline, String location, Integer yearsOfExperience, String summary) {

    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(profile.getName(), profile.getHeadline(), profile.getLocation(),
            profile.getYearsOfExperience(), profile.getSummary());
    }

    public static ProfileResponse empty() {
        return new ProfileResponse(null, null, null, null, null);
    }
}
