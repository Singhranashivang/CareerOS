package com.careeros.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MaxRequestBodySizeFilterTest {

    private final MaxRequestBodySizeFilter filter = new MaxRequestBodySizeFilter();

    @Test
    void aSmallBodyPassesThroughUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/scheduled-posts");
        byte[] body = "{\"platform\":\"LINKEDIN\",\"body\":\"a short post\"}".getBytes(StandardCharsets.UTF_8);
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        byte[] readBack = ((jakarta.servlet.http.HttpServletRequest) chain.getRequest())
                .getInputStream().readAllBytes();
        assertThat(readBack).isEqualTo(body);
    }

    @Test
    void aDeclaredContentLengthOverTheCapIsRejectedBeforeAnyByteIsRead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/scheduled-posts");
        request.setContentType("application/json");
        request.setContent(new byte[200_000]); // getContentLengthLong() reflects this directly, as a real declared Content-Length would

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).as("the controller must never see a rejected request").isNull();
    }

    /**
     * MockHttpServletRequest.setContent() always derives getContentLengthLong()
     * from the real byte array, so it can't represent "declared length doesn't
     * match actual body" (chunked transfer-encoding has no declared length at
     * all) — overriding just that one accessor is the only way to decouple them
     * for a test, same as a real chunked request looks to this filter.
     */
    private static final class UndeclaredLengthRequest extends MockHttpServletRequest {
        UndeclaredLengthRequest(String method, String uri) {
            super(method, uri);
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }

    @Test
    void aBodyThatLiesAboutItsLengthIsCutOffWhileReadingNotFullyBuffered() throws Exception {
        // No declared Content-Length (as with chunked transfer-encoding) — the
        // pre-check alone can't catch this; the stream wrapper has to.
        UndeclaredLengthRequest request = new UndeclaredLengthRequest("POST", "/api/scheduled-posts");
        byte[] oversized = new byte[100_000];
        request.setContent(oversized);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Passed the pre-check (no declared Content-Length on this mock), so it
        // reaches the controller layer — but reading the full body must fail.
        assertThat(chain.getRequest()).isNotNull();
        var wrapped = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> wrapped.getInputStream().readAllBytes())
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exceeds the maximum allowed size");
    }
}
