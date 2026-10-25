package com.signallens.ingestion.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final long MAX_REQUEST_SIZE = 64 * 1024;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (request.getContentLengthLong() > MAX_REQUEST_SIZE) {
            writePayloadTooLarge(response);
            return;
        }

        HttpServletRequestWrapper wrappedRequest =
                new HttpServletRequestWrapper(request) {

                    private ServletInputStream limitedInputStream;

                    @Override
                    public ServletInputStream getInputStream() throws IOException {
                        if (limitedInputStream == null) {
                            limitedInputStream = new LimitedServletInputStream(
                                    super.getInputStream(),
                                    MAX_REQUEST_SIZE
                            );
                        }
                        return limitedInputStream;
                    }

                    @Override
                    public BufferedReader getReader() throws IOException {
                        return new BufferedReader(
                                new InputStreamReader(
                                        getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );
                    }
                };

        try {
            filterChain.doFilter(wrappedRequest, response);
        } catch (PayloadTooLargeException ex) {
            if (!response.isCommitted()) {
                writePayloadTooLarge(response);
            }
        }
    }

    private void writePayloadTooLarge(HttpServletResponse response)
            throws IOException {

        response.resetBuffer();
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.getWriter().write("""
                {
                  "status": 413,
                  "error": "Content Too Large",
                  "message": "Request body exceeds the 64 KiB limit"
                }
                """);
    }

    private static class PayloadTooLargeException extends IOException {
        PayloadTooLargeException() {
            super("Request body exceeds the configured limit");
        }
    }

    private static class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long bytesRead;

        LimitedServletInputStream(
                ServletInputStream delegate,
                long limit
        ) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();

            if (value != -1) {
                countBytes(1);
            }

            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length)
                throws IOException {

            if (length == 0) {
                return 0;
            }

            int count = delegate.read(buffer, offset, length);

            if (count > 0) {
                countBytes(count);
            }

            return count;
        }

        private void countBytes(int count) throws PayloadTooLargeException {
            bytesRead += count;

            if (bytesRead > limit) {
                throw new PayloadTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}