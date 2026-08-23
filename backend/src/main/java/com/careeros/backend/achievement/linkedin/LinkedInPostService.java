package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.llm.BannedVocabulary;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedInPostService {

    private final AchievementRepository achievementRepository;
    private final LinkedInPromptBuilder linkedInPromptBuilder;
    private final LinkedInPostShapeValidator shapeValidator;
    private final LinkedInPostSoloAuthorValidator soloAuthorValidator;
    private final LLMService llmService;
    private final LinkedInPostPersistenceService linkedInPostPersistenceService;

    private final ObjectMapper objectMapper;

    public LinkedInPost generate(User user, Long achievementId, boolean regenerate) {

        AchievementEntity achievement = achievementRepository
                .findByIdAndUser(achievementId, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Achievement not found"));

        var existing = linkedInPostPersistenceService.findByAchievement(achievement);

        if (!regenerate && existing.isPresent()) {
            return toPost(existing.get());
        }

        LinkedInPost post = generateGuarded(
                linkedInPromptBuilder.build(achievement),
                violations -> linkedInPromptBuilder.buildRetry(achievement, violations),
                "achievement " + achievement.getId());

        LinkedInPostEntity entity = existing.orElseGet(LinkedInPostEntity::new);
        entity.setUser(user);
        entity.setAchievement(achievement);
        entity.setHeadline(post.getHeadline());
        entity.setPost(post.getPost());
        entity.setConfidence(post.getConfidence());
        entity.setGeneratedAt(LocalDateTime.now());

        linkedInPostPersistenceService.save(entity);

        return post;
    }

    /**
     * One post from every non-dismissed achievement in [from, to], across all
     * repositories — not tied to a single achievement, so unlike generate()
     * above there's nothing to cache: LinkedInPostEntity's achievement_id is
     * NOT NULL by design (see V18), and a period summary doesn't have one
     * achievement to hang a row off. Generated fresh on every call.
     */
    public LinkedInPeriodPost generatePeriodSummary(User user, LocalDate from, LocalDate to) {

        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }

        List<AchievementEntity> achievements = achievementRepository
                .findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
                        user, from.atStartOfDay(), to.atTime(23, 59, 59));

        if (achievements.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No achievements found between " + from + " and " + to);
        }

        LinkedInPost post = generateGuarded(
                linkedInPromptBuilder.buildPeriod(achievements, from, to),
                violations -> linkedInPromptBuilder.buildPeriodRetry(achievements, from, to, violations),
                "period " + from + " to " + to);

        // PERIOD_OUTPUT_JSON no longer asks the model for a headline, so
        // post.getHeadline() is simply never populated here — this is the
        // boundary where that gets reflected in the actual response shape.
        return new LinkedInPeriodPost(post.getPost(), post.getConfidence());
    }

    /**
     * Generates a post and checks it against the banned-word list, the
     * solo-author rule (see LinkedInPostSoloAuthorValidator — added after a
     * real post invented "a team of five developers" for a solo user), and
     * the paragraph-break SHAPE rule. On a hit, retries once with the
     * specific problems named. If the retry still has problems, keeps
     * whichever of the two attempts has fewer and logs the remainder rather
     * than failing the request.
     */
    private LinkedInPost generateGuarded(
            String prompt, Function<List<String>, String> retryPrompt, String logLabel) {

        LinkedInPost first = callModel(prompt);
        List<String> firstProblems = problemsIn(first);

        if (firstProblems.isEmpty()) {
            return first;
        }

        LinkedInPost retry = callModel(retryPrompt.apply(firstProblems));
        List<String> retryProblems = problemsIn(retry);

        if (retryProblems.isEmpty()) {
            return retry;
        }

        boolean retryIsBetter = retryProblems.size() <= firstProblems.size();

        log.warn("LinkedIn post for {} still has problems after retry: {}",
                logLabel, retryIsBetter ? retryProblems : firstProblems);

        return retryIsBetter ? retry : first;
    }

    private LinkedInPost callModel(String prompt) {

        String response = llmService.generate(prompt);

        try {
            return objectMapper.readValue(response, LinkedInPost.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LinkedIn post", e);
        }
    }

    /**
     * Human-readable problem descriptions — banned words as one line, team/
     * solo-author violations as their own line, each shape violation as its
     * own. post.getHeadline() is null for a period post (see
     * generatePeriodSummary), and every check below already treats a
     * null/blank string as "no violations", so this needs no branching per
     * response shape.
     */
    private List<String> problemsIn(LinkedInPost post) {

        List<String> problems = new ArrayList<>();

        List<String> bannedWords = new ArrayList<>(BannedVocabulary.violationsIn(post.getHeadline()));
        bannedWords.addAll(BannedVocabulary.violationsIn(post.getPost()));
        if (!bannedWords.isEmpty()) {
            problems.add("used these banned words/phrases: " + String.join(", ", bannedWords));
        }

        List<String> teamReferences = new ArrayList<>(soloAuthorValidator.violationsIn(post.getHeadline()));
        teamReferences.addAll(soloAuthorValidator.violationsIn(post.getPost()));
        if (!teamReferences.isEmpty()) {
            problems.add("invented a team or used first-person plural (this is one person working "
                    + "alone): " + String.join(", ", teamReferences));
        }

        problems.addAll(shapeValidator.violationsIn(post.getPost()));

        return problems;
    }

    private static LinkedInPost toPost(LinkedInPostEntity entity) {
        return LinkedInPost.builder()
                .headline(entity.getHeadline())
                .post(entity.getPost())
                .confidence(entity.getConfidence())
                .build();
    }

}
