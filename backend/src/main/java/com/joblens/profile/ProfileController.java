package com.joblens.profile;

import com.joblens.profile.dto.ProfileRequest;
import com.joblens.profile.dto.ProfileResponse;
import com.joblens.profile.dto.SkillRequest;
import com.joblens.profile.dto.SkillSuggestion;
import com.joblens.profile.dto.UserSkillResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/users/me/profile")
    public ProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getProfile(userId(jwt));
    }

    @PutMapping("/users/me/profile")
    public ProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody ProfileRequest request) {
        return profileService.updateProfile(userId(jwt), request);
    }

    @GetMapping("/users/me/skills")
    public List<UserSkillResponse> listSkills(@AuthenticationPrincipal Jwt jwt) {
        return profileService.listSkills(userId(jwt));
    }

    @PostMapping("/users/me/skills")
    public UserSkillResponse addSkill(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody SkillRequest request) {
        return profileService.addOrUpdateSkill(userId(jwt), request);
    }

    @DeleteMapping("/users/me/skills/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSkill(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID skillId) {
        profileService.removeSkill(userId(jwt), skillId);
    }

    @GetMapping("/skills/suggestions")
    public List<SkillSuggestion> suggest(@RequestParam(defaultValue = "") String q) {
        return profileService.suggest(q);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
