package com.signallens.ingestion.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter =
            new RequestSizeLimitFilter();

    @Test
    void oversizedDeclaredContentLengthShouldReturn413()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest() {
                    @Override
                    public long getContentLengthLong() {
                        return 65_537L;
                    }

                    @Override
                    public int getContentLength() {
                        return 65_537;
                    }
                };

        request.setMethod("POST");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void requestAtExactSizeLimitShouldPass() throws Exception {

        byte[] body = new byte[65_536];

        MockHttpServletRequest request =
                new MockHttpServletRequest() {
                    @Override
                    public long getContentLengthLong() {
                        return 65_536L;
                    }

                    @Override
                    public int getContentLength() {
                        return 65_536;
                    }
                };

        request.setMethod("POST");
        request.setContent(body);

        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            chainInvoked.set(true);

            jakarta.servlet.http.HttpServletRequest httpRequest =
                    (jakarta.servlet.http.HttpServletRequest) wrappedRequest;

            httpRequest.getInputStream().readAllBytes();
        };

        filter.doFilter(request, response, chain);

        assertNotEquals(413, response.getStatus());
        assertTrue(chainInvoked.get());
    }

    @Test
    void bodyExceedingLimitShouldReturn413WhenRead()
            throws Exception {

        byte[] body = new byte[65_537];

        MockHttpServletRequest request =
                new MockHttpServletRequest() {
                    @Override
                    public long getContentLengthLong() {
                        return -1L;
                    }

                    @Override
                    public int getContentLength() {
                        return -1;
                    }
                };

        request.setMethod("POST");
        request.setContent(body);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            jakarta.servlet.http.HttpServletRequest httpRequest =
                    (jakarta.servlet.http.HttpServletRequest) wrappedRequest;

            httpRequest.getInputStream().readAllBytes();
        };

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
    }
    @Test
    void requestAtExactLimitWithoutContentLengthShouldPass()
            throws Exception {

        byte[] body = new byte[65_536];

        MockHttpServletRequest request =
                new MockHttpServletRequest() {
                    @Override
                    public long getContentLengthLong() {
                        return -1L;
                    }

                    @Override
                    public int getContentLength() {
                        return -1;
                    }
                };

        request.setMethod("POST");
        request.setContent(body);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            chainInvoked.set(true);

            jakarta.servlet.http.HttpServletRequest httpRequest =
                    (jakarta.servlet.http.HttpServletRequest) wrappedRequest;

            httpRequest.getInputStream().readAllBytes();
        };

        filter.doFilter(request, response, chain);

        assertNotEquals(413, response.getStatus());
        assertTrue(chainInvoked.get());
    }
}