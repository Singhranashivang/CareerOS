package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.llm.BannedVocabulary;
import com.careeros.backend.achievement.llm.BannedVocabularyScrubber;
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
    private final PreferredVoiceExamples preferredVoiceExamples;

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

        // checkShape=false: a single achievement rarely has 3+ paragraphs of
        // honest material, and the reliability report showed the model either
        // ignoring the requirement or padding with invented detail to satisfy
        // it — see LinkedInPromptBuilder.HEADER. Period posts still enforce it.
        List<String> voiceSamples = preferredVoiceExamples.forUser(user);

        LinkedInPost post = generateGuarded(
                linkedInPromptBuilder.build(achievement, voiceSamples),
                violations -> linkedInPromptBuilder.buildRetry(achievement, violations, voiceSamples),
                "achievement " + achievement.getId(), false, this::parseSinglePost);

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

        return generatePeriodPost(user, achievements, from, to, "period " + from + " to " + to);
    }

    /**
     * One post from exactly the given achievements, not a date range — see
     * AchievementTimelineController's /linkedin/combined. Rejects if any id
     * isn't the user's own or is dismissed (the repository query already
     * scopes to both, so a short result means one of those). The 12-item cap
     * is the same MAX_PERIOD_ACHIEVEMENTS truncation buildPeriod already
     * applies for the date-range path — reused as-is here, not reimplemented.
     */
    public LinkedInPeriodPost generateCombined(User user, List<Long> achievementIds) {

        if (achievementIds == null || achievementIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one achievement id is required");
        }

        List<Long> distinctIds = achievementIds.stream().distinct().toList();
        List<AchievementEntity> achievements = achievementRepository
                .findByIdInAndUserAndDismissedFalseOrderByGeneratedAtDesc(distinctIds, user);

        if (achievements.size() != distinctIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "One or more achievement ids were not found, not yours, or dismissed");
        }

        // Already ordered newest-first by the query above.
        LocalDate to = achievements.get(0).getGeneratedAt().toLocalDate();
        LocalDate from = achievements.get(achievements.size() - 1).getGeneratedAt().toLocalDate();

        return generatePeriodPost(user, achievements, from, to, "combined selection of " + distinctIds.size());
    }

    private LinkedInPeriodPost generatePeriodPost(
            User user, List<AchievementEntity> achievements, LocalDate from, LocalDate to, String logLabel) {

        List<String> voiceSamples = preferredVoiceExamples.forUser(user);

        // checkShape=true: unlike a single achievement, a period genuinely has
        // several separate things to say, so the multi-paragraph shape rule
        // stays enforced here.
        LinkedInPost post = generateGuarded(
                linkedInPromptBuilder.buildPeriod(achievements, from, to, voiceSamples),
                violations -> linkedInPromptBuilder.buildPeriodRetry(
                        achievements, from, to, violations, voiceSamples),
                logLabel, true, this::parsePeriodPost);

        // PERIOD_OUTPUT_JSON no longer asks the model for a headline, so
        // post.getHeadline() is simply never populated here — this is the
        // boundary where that gets reflected in the actual response shape.
        return new LinkedInPeriodPost(post.getPost(), post.getConfidence());
    }

    /**
     * Generates a post and checks it against the solo-author rule (see
     * LinkedInPostSoloAuthorValidator — added after a real post invented
     * "a team of five developers" for a solo user) and, when checkShape is
     * true, the paragraph-break SHAPE rule. On a hit, retries once with the
     * specific problems named. If the retry still has problems, keeps
     * whichever of the two attempts has fewer and logs the remainder rather
     * than failing the request. Banned vocabulary is deliberately NOT part of
     * this retry loop — see scrubBannedVocabulary below. parser turns the raw
     * model response into a LinkedInPost — single-achievement and period
     * responses are different JSON shapes (see parseSinglePost vs
     * parsePeriodPost), but everything past parsing (validation, scrubbing)
     * only ever deals with the common LinkedInPost shape.
     */
    private LinkedInPost generateGuarded(
            String prompt, Function<List<String>, String> retryPrompt, String logLabel, boolean checkShape,
            Function<String, LinkedInPost> parser) {

        LinkedInPost first = callModel(prompt, parser);
        List<String> firstProblems = problemsIn(first, checkShape);

        LinkedInPost chosen;
        if (firstProblems.isEmpty()) {
            chosen = first;
        } else {
            LinkedInPost retry = callModel(retryPrompt.apply(firstProblems), parser);
            List<String> retryProblems = problemsIn(retry, checkShape);

            if (retryProblems.isEmpty()) {
                chosen = retry;
            } else {
                boolean retryIsBetter = retryProblems.size() <= firstProblems.size();
                log.warn("LinkedIn post for {} still has problems after retry: {}",
                        logLabel, retryIsBetter ? retryProblems : firstProblems);
                chosen = retryIsBetter ? retry : first;
            }
        }

        return scrubBannedVocabulary(chosen, logLabel);
    }

    /**
     * Deterministic cleanup instead of a retry — a reliability check found
     * the model still uses a banned word in roughly 60% of first attempts
     * regardless of the prompt's instruction not to, and a retry didn't
     * reliably fix it either. Text substitution always wins that fight.
     * See BannedVocabularyScrubber.
     */
    private LinkedInPost scrubBannedVocabulary(LinkedInPost post, String logLabel) {
        List<String> found = new ArrayList<>(BannedVocabulary.violationsIn(post.getHeadline()));
        found.addAll(BannedVocabulary.violationsIn(post.getPost()));
        if (!found.isEmpty()) {
            log.info("Scrubbed banned vocabulary from LinkedIn post for {}: {}", logLabel, found);
            post.setHeadline(BannedVocabularyScrubber.scrub(post.getHeadline()));
            post.setPost(BannedVocabularyScrubber.scrub(post.getPost()));
        }
        return post;
    }

    private LinkedInPost callModel(String prompt, Function<String, LinkedInPost> parser) {
        return parser.apply(llmService.generate(prompt));
    }

    private LinkedInPost parseSinglePost(String response) {
        try {
            return objectMapper.readValue(response, LinkedInPost.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LinkedIn post", e);
        }
    }

    /**
     * Joins the model's paragraph array into the same post.post text field
     * everything downstream expects — see LinkedInPeriodModelResponse. A
     * missing/short paragraphs array still ends up here as fewer-than-wanted
     * "\n\n" breaks, which shapeValidator already catches and retries on
     * exactly as it did the old single-string shape.
     */
    private LinkedInPost parsePeriodPost(String response) {
        try {
            LinkedInPeriodModelResponse parsed = objectMapper.readValue(response, LinkedInPeriodModelResponse.class);
            List<String> paragraphs = parsed.paragraphs() == null ? List.of() : parsed.paragraphs();
            return LinkedInPost.builder()
                    .post(String.join("\n\n", paragraphs))
                    .confidence(parsed.confidence())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LinkedIn post", e);
        }
    }

    /**
     * Human-readable problem descriptions that are worth a retry — team/
     * solo-author violations as their own line, each shape violation as its
     * own, shape only when checkShape is true (see generate() vs
     * generatePeriodPost()). Banned vocabulary is NOT here — see
     * scrubBannedVocabulary; it's fixed deterministically after the fact
     * instead of being something worth spending a retry on.
     * post.getHeadline() is null for a period post (see generatePeriodPost),
     * and every check below already treats a null/blank string as "no
     * violations", so this needs no branching per response shape.
     */
    private List<String> problemsIn(LinkedInPost post, boolean checkShape) {

        List<String> problems = new ArrayList<>();

        List<String> teamReferences = new ArrayList<>(soloAuthorValidator.violationsIn(post.getHeadline()));
        teamReferences.addAll(soloAuthorValidator.violationsIn(post.getPost()));
        if (!teamReferences.isEmpty()) {
            problems.add("invented a team or used first-person plural (this is one person working "
                    + "alone): " + String.join(", ", teamReferences));
        }

        if (checkShape) {
            problems.addAll(shapeValidator.violationsIn(post.getPost()));
        }

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
