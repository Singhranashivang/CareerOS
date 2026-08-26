package com.careeros.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GithubTokenEncryptor githubTokenEncryptor;

    public User createOrUpdateGitHubUser(
            Long githubId,
            String username,
            String name,
            String email,
            String avatarUrl,
            String githubAccessToken
    ) {

        User user = userRepository.findByGithubId(githubId)
                .orElse(User.builder()
                        .githubId(githubId)
                        .build());

        user.setUsername(username);
        user.setName(name);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        user.setGithubAccessToken(githubTokenEncryptor.encrypt(githubAccessToken));

        return userRepository.save(user);
    }

    public User findByGithubId(Long githubId) {

        return userRepository.findByGithubId(githubId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    /** Changeable any time from settings (PATCH /api/me/goal) — always overwrites. */
    public User updateGoal(User user, UserGoal goal) {
        user.setGoal(goal);
        return userRepository.save(user);
    }

    /**
     * The "asked once during onboarding" path — OnboardingController.start
     * calls this on every start, but it only actually sets anything the
     * first time: a goal already on file (set here on a prior run, or since
     * via PATCH /api/me/goal) is never silently overwritten by starting
     * onboarding again. A null goal (the question wasn't answered on this
     * request) is likewise a no-op.
     */
    public User setGoalIfUnset(User user, UserGoal goal) {
        if (goal == null || user.getGoal() != null) {
            return user;
        }
        return updateGoal(user, goal);
    }
}
