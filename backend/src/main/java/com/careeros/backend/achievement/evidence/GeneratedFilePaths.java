package com.careeros.backend.achievement.evidence;

import java.util.regex.Pattern;

/**
 * Files nobody wrote by hand.
 *
 * Line count was being read as a proxy for effort, which a lockfile or a
 * vendored dependency tree defeats completely: one repo measured 54,571 changed
 * lines of which 132 were authored, another 271,505 of which 387 were. Counting
 * those is the mirror of counting padded commits.
 */
final class GeneratedFilePaths {

    /**
     * db/migration/ (Flyway, hand-written) is deliberately NOT matched by the
     * migrations/ rule — that one targets Django, Rails and EF, which generate
     * their migrations. The singular/plural split separates them cleanly.
     */
    private static final Pattern GENERATED = Pattern.compile(
            "(^|/)node_modules/"
            + "|(^|/)vendor/"
            + "|(^|/)third_party/"
            + "|(^|/)(dist|build|out|coverage)/"
            + "|(^|/)\\.next/"
            + "|(^|/)(\\.venv|venv|site-packages)/"
            + "|(^|/)(package-lock\\.json|yarn\\.lock|pnpm-lock\\.yaml|composer\\.lock"
            + "|gemfile\\.lock|poetry\\.lock|cargo\\.lock|go\\.sum)$"
            + "|\\.min\\.(js|css)$"
            + "|\\.bundle\\.js$"
            + "|\\.(csv|tsv|parquet|xlsx|ipynb)$"
            + "|(^|/)migrations/"
            + "|(^|/)schema\\.rb$"
            // Generator output that lives in ordinary source directories, so no
            // directory rule can catch it: create-react-app, Next, Vite.
            + "|(^|/)(reportwebvitals|setuptests|serviceworker|service-worker"
            + "|registerserviceworker|app\\.test)\\.(js|jsx|ts|tsx)$"
            + "|(^|/)(react-app-env|next-env|vite-env)\\.d\\.ts$"
            + "|\\.(png|jpe?g|gif|ico|svg|woff2?|ttf|eot|pdf|zip|mp4|mp3)$",
            Pattern.CASE_INSENSITIVE);

    private GeneratedFilePaths() {
    }

    static boolean isGenerated(String path) {
        return path != null && GENERATED.matcher(path).find();
    }
}
