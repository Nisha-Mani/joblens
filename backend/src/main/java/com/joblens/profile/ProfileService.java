package com.joblens.profile;

import com.joblens.common.error.NotFoundException;
import com.joblens.profile.dto.ProfileRequest;
import com.joblens.profile.dto.ProfileResponse;
import com.joblens.profile.dto.SkillRequest;
import com.joblens.profile.dto.SkillSuggestion;
import com.joblens.profile.dto.UserSkillResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All operations are scoped to the authenticated user's id; callers never pass another user's id. */
@Service
public class ProfileService {

    private static final int MAX_SUGGESTIONS = 10;

    private final UserProfileRepository profiles;
    private final SkillRepository skills;
    private final UserSkillRepository userSkills;

    public ProfileService(UserProfileRepository profiles, SkillRepository skills,
                          UserSkillRepository userSkills) {
        this.profiles = profiles;
        this.skills = skills;
        this.userSkills = userSkills;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return profiles.findByUserId(userId).map(ProfileResponse::from).orElseGet(ProfileResponse::empty);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, ProfileRequest request) {
        UserProfile profile = profiles.findByUserId(userId).orElseGet(() -> new UserProfile(userId));
        profile.update(blankToNull(request.name()), blankToNull(request.headline()),
            blankToNull(request.location()), request.yearsOfExperience(),
            blankToNull(request.summary()));
        return ProfileResponse.from(profiles.save(profile));
    }

    @Transactional(readOnly = true)
    public List<UserSkillResponse> listSkills(UUID userId) {
        return userSkills.findByIdUserIdOrderBySkillNameAsc(userId).stream()
            .map(UserSkillResponse::from).toList();
    }

    /** Adds the skill, or updates its proficiency if the user already has it. Safe under concurrent calls. */
    @Transactional
    public UserSkillResponse addOrUpdateSkill(UUID userId, SkillRequest request) {
        String name = request.name().trim();
        SkillCategory category = request.category() != null ? request.category() : SkillCategory.OTHER;
        skills.insertIfAbsent(name, category.name());
        Skill skill = skills.findByNameIgnoreCase(name).orElseThrow();
        userSkills.upsert(userId, skill.getId(), request.proficiency().name());
        return new UserSkillResponse(skill.getId(), skill.getName(), skill.getCategory(), request.proficiency());
    }

    @Transactional
    public void removeSkill(UUID userId, UUID skillId) {
        UserSkillId id = new UserSkillId(userId, skillId);
        if (!userSkills.existsById(id)) {
            throw new NotFoundException("Skill not found");
        }
        userSkills.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<SkillSuggestion> suggest(String prefix) {
        String trimmed = prefix == null ? "" : prefix.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return skills.searchByPrefix(escapeLike(trimmed), PageRequest.of(0, MAX_SUGGESTIONS)).stream()
            .map(SkillSuggestion::from).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
