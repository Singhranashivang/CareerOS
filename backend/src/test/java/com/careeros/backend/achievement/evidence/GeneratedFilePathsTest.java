package com.careeros.backend.achievement.evidence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedFilePathsTest {

    @Test
    void excludesTheThingsThatInflatedTheLineCounts() {
        // Every one of these was measured in a real repository.
        assertThat(GeneratedFilePaths.isGenerated("backend/package-lock.json")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("node_modules/react/index.js")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("notebooks/Olympic.ipynb")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("data/athlete_events.csv")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("dist/main.bundle.js")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("static/js/app.min.js")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("public/logo.png")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("yarn.lock")).isTrue();
    }

    @Test
    void keepsHandWrittenFlywayMigrations() {
        // db/migration is Flyway and hand-written; migrations/ is Django or Rails
        // and generated. The whole distinction rests on this pair.
        assertThat(GeneratedFilePaths.isGenerated(
                "src/main/resources/db/migration/V16__add_repository_analysis_outcome.sql"))
                .isFalse();
        assertThat(GeneratedFilePaths.isGenerated("app/migrations/0003_auto.py")).isTrue();
    }

    @Test
    void excludesGeneratorOutputLivingInOrdinarySourcePaths() {
        assertThat(GeneratedFilePaths.isGenerated("src/reportWebVitals.js")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("src/setupTests.js")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("src/App.test.js")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("src/serviceWorker.ts")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("next-env.d.ts")).isTrue();
        assertThat(GeneratedFilePaths.isGenerated("src/vite-env.d.ts")).isTrue();
    }

    @Test
    void keepsHandEditedConfigAndRealTests() {
        // These get edited by hand, unlike the scaffolds above.
        assertThat(GeneratedFilePaths.isGenerated("next.config.ts")).isFalse();
        assertThat(GeneratedFilePaths.isGenerated("vite.config.ts")).isFalse();
        assertThat(GeneratedFilePaths.isGenerated("tailwind.config.js")).isFalse();
        assertThat(GeneratedFilePaths.isGenerated("src/components/Form.test.tsx")).isFalse();
    }

    @Test
    void keepsOrdinarySource() {
        assertThat(GeneratedFilePaths.isGenerated("backend/index.js")).isFalse();
        assertThat(GeneratedFilePaths.isGenerated("src/main/java/com/x/Foo.java")).isFalse();
        assertThat(GeneratedFilePaths.isGenerated("package.json")).isFalse();
        assertThat(GeneratedFilePaths.isGenerated("app/page.tsx")).isFalse();
        assertThat(GeneratedFilePaths.isGenerated("README.md")).isFalse();
    }

    @Test
    void handlesNull() {
        assertThat(GeneratedFilePaths.isGenerated(null)).isFalse();
    }
}
