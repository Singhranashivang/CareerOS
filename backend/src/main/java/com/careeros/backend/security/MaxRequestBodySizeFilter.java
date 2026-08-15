package com.careeros.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * No endpoint in this app takes anything larger than a short JSON body
 * (posts, achievement text, form fields — no file uploads anywhere), but
 * nothing was capping it either: server.tomcat.max-http-form-post-size only
 * governs application/x-www-form-urlencoded bodies parsed via
 * getParameter(), not a raw @RequestBody JSON stream read by Jackson, so a
 * client could stream an arbitrarily large body at e.g. POST
 * /api/scheduled-posts before validation ever gets a chance to reject it.
 *
 * Two layers: reject immediately on a declared Content-Length over the cap
 * (the common case, no bytes read), and wrap the stream so a chunked body
 * with no Content-Length — or one that lies — still gets cut off while
 * reading rather than fully buffered into memory first.
 */
@Component
public class MaxRequestBodySizeFilter extends OncePerRequestFilter {

    private static final long MAX_BODY_BYTES = 64 * 1024; // 64 KB — comfortably fits any post/achievement text field

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {

        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Request body exceeds the maximum allowed size");
            return;
        }

        filterChain.doFilter(new SizeLimitingRequestWrapper(request, MAX_BODY_BYTES), response);
    }

    private static final class SizeLimitingRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;

        SizeLimitingRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream original = super.getInputStream();
            return new ServletInputStream() {

                private long readCount = 0;

                @Override
                public int read() throws IOException {
                    int b = original.read();
                    if (b != -1 && ++readCount > maxBytes) {
                        throw new IOException("Request body exceeds the maximum allowed size of "
                                + maxBytes + " bytes");
                    }
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int n = original.read(b, off, len);
                    if (n > 0 && (readCount += n) > maxBytes) {
                        throw new IOException("Request body exceeds the maximum allowed size of "
                                + maxBytes + " bytes");
                    }
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return original.isFinished();
                }

                @Override
                public boolean isReady() {
                    return original.isReady();
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    original.setReadListener(readListener);
                }
            };
        }
    }
}
