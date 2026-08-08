package com.careeros.backend.github;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks GitHub's paginated list endpoints.
 *
 * GitHub returns 30 items per page by default and silently truncates —
 * there's no error, you just get less data than exists. Every list
 * endpoint must be paged or the numbers downstream are wrong.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GithubPaginator {

    private static final int PAGE_SIZE = 100;   // GitHub's maximum

    private final RestClient restClient;

    public <T> List<T> fetchAll(
            String url,
            String accessToken,
            ParameterizedTypeReference<List<T>> type,
            int maxPages
    ) {
        List<T> all = new ArrayList<>();
        String separator = url.contains("?") ? "&" : "?";

        for (int page = 1; page <= maxPages; page++) {

            List<T> batch = restClient.get()
                    .uri(url + separator + "per_page=" + PAGE_SIZE + "&page=" + page)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(type);

            if (batch == null || batch.isEmpty()) {
                break;
            }

            all.addAll(batch);

            if (batch.size() < PAGE_SIZE) {
                break;   // last page
            }

            if (page == maxPages) {
                log.warn("Hit page cap {} for {} — results truncated", maxPages, url);
            }
        }

        return all;
    }
}