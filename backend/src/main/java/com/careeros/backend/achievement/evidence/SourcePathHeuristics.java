package com.careeros.backend.achievement.evidence;

import java.util.Optional;
import java.util.Set;

/**
 * File-path heuristics shared by anything that needs to characterise a
 * changed file without a language-aware parser: "what area of the codebase
 * is this" and "is this a test". Extracted out of {@link CodeStatsFetcher}
 * so {@code CommitClusterer}'s path-overlap signal uses the identical
 * "area" definition rather than a second, silently-drifting copy.
 */
public final class SourcePathHeuristics {

    private static final Set<String> SKIP_SEGMENTS = Set.of("src", "main", "java", "com", "org",
            "app", "lib", "test", "resources", "node_modules", "target", "build");

    private SourcePathHeuristics() {
    }

    /** "src/main/java/com/x/security/Foo.java" → "security" */
    public static Optional<String> area(String path) {
        String[] segments = path.split("/");
        if (segments.length < 2) return Optional.empty();

        for (int i = segments.length - 2; i >= 0; i--) {
            String s = segments[i];
            if (!SKIP_SEGMENTS.contains(s) && s.length() > 2 && !s.contains(".")) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public static boolean isTest(String path) {
        String p = path.toLowerCase();
        return p.contains("/test/") || p.contains("__tests__")
                || p.endsWith(".test.ts") || p.endsWith(".test.tsx")
                || p.endsWith(".spec.ts") || p.endsWith("test.java")
                || p.endsWith("_test.py") || p.endsWith("test_.py");
    }
}
