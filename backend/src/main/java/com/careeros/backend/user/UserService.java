package com.careeros.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User createOrUpdateGitHubUser(
            Long githubId,
            String username,
            String name,
            String email,
            String avatarUrl,
            String encryptedGithubAccessToken
    ) {

        User user = userRepository.findByGithubId(githubId)
                .orElse(User.builder()
                        .githubId(githubId)
                        .build());

        user.setUsername(username);
        user.setName(name);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        user.setEncryptedGithubAccessToken(encryptedGithubAccessToken);

        return userRepository.save(user);
    }

    public User findByGithubId(Long githubId) {

        return userRepository.findByGithubId(githubId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }
}