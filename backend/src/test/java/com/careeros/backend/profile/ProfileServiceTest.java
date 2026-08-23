package com.careeros.backend.profile;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final ProfileService service = new ProfileService(achievementRepository, new ObjectMapper());

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    private static AchievementEntity achievement(
            String repo, String title, String bullet, double confidence,
            LocalDateTime generatedAt, String citedShasJson, String technologiesJson
    ) {
        return AchievementEntity.builder()
                .id(1L)
                .repositoryName(repo)
                .title(title)
                .resumeBullet(bullet)
                .confidence(confidence)
                .generatedAt(generatedAt)
                .citedCommitShasJson(citedShasJson)
                .technologiesJson(technologiesJson)
                .build();
    }

    @Test
    void computesTotalsAcrossRepositoriesAndDedupesTechnologies() {
        var a = achievement("RepoA", "A", "Did A", 0.8,
                LocalDateTime.of(2026, 7, 1, 9, 0), "[\"sha1\"]", "[\"Java\",\"React\"]");
        var b = achievement("RepoB", "B", "Did B", 0.55,
                LocalDateTime.of(2026, 8, 1, 9, 0), "[\"sha2\",\"sha3\"]", "[\"Java\",\"PostgreSQL\"]");
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(b, a)); // newest-first, as the real query returns

        ProfileResponse response = service.getProfile(USER);

        assertThat(response.achievements()).hasSize(2);
        assertThat(response.totals().achievementCount()).isEqualTo(2);
        assertThat(response.totals().repositoriesContributedTo()).isEqualTo(2);
        assertThat(response.totals().dateRangeStart()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
        assertThat(response.totals().dateRangeEnd()).isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 0));
        assertThat(response.totals().technologies()).containsExactly("Java", "PostgreSQL", "React");
    }

    @Test
    void mapsConfidenceToTheMatchingImpactLevelAndParsesCitedShas() {
        var high = achievement("RepoA", "A", "bullet", 0.8,
                LocalDateTime.now(), "[\"sha1\",\"sha2\"]", null);
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(high));

        var item = service.getProfile(USER).achievements().get(0);

        assertThat(item.impactLevel().name()).isEqualTo("HIGH_IMPACT");
        assertThat(item.citedCommitShas()).containsExactly("sha1", "sha2");
    }

    @Test
    void anEmptyProfileHasNullDateRangeAndZeroTotals() {
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of());

        ProfileResponse response = service.getProfile(USER);

        assertThat(response.totals().achievementCount()).isZero();
        assertThat(response.totals().dateRangeStart()).isNull();
        assertThat(response.totals().dateRangeEnd()).isNull();
        assertThat(response.totals().technologies()).isEmpty();
    }

    @Test
    void markdownExportGroupsByRepositoryWithOnlyResumeBulletsNoStarFields() {
        var a1 = achievement("RepoA", "Title A1", "Bullet A1", 0.9,
                LocalDateTime.of(2026, 8, 1, 9, 0), null, null);
        var a2 = achievement("RepoA", "Title A2", "Bullet A2", 0.9,
                LocalDateTime.of(2026, 7, 15, 9, 0), null, null);
        var b1 = achievement("RepoB", "Title B1", "Bullet B1", 0.9,
                LocalDateTime.of(2026, 7, 1, 9, 0), null, null);
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(a1, a2, b1)); // newest-first

        String markdown = service.exportMarkdown(USER);

        assertThat(markdown).contains("# Career Profile");
        assertThat(markdown).contains("Jul 1, 2026 – Aug 1, 2026");
        assertThat(markdown).contains("## RepoA").contains("## RepoB");
        assertThat(markdown).contains("- Bullet A1").contains("- Bullet A2").contains("- Bullet B1");
        // No STAR-field prose, no titles as headings — just the bullets.
        assertThat(markdown).doesNotContain("Title A1").doesNotContain("starSituation");
        // RepoA's section (first repo encountered, newest-first) comes before RepoB's.
        assertThat(markdown.indexOf("## RepoA")).isLessThan(markdown.indexOf("## RepoB"));
    }

    @Test
    void anEmptyProfileExportsAPlaceholderMessage() {
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of());

        String markdown = service.exportMarkdown(USER);

        assertThat(markdown).contains("No achievements yet");
    }
}
