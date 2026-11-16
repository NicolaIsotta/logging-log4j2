/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.ContextAnchor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the request- and session-related keys of {@link WebLookup}.
 */
class WebLookupRequestTest {

    private final WebLookup lookup = new WebLookup();
    private final ServletContext servletContext = mock(ServletContext.class);
    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        loggerContext = new LoggerContext("WebLookupRequestTest");
        WebLoggerContextUtils.setServletContext(loggerContext, servletContext);
        ContextAnchor.THREAD_CONTEXT.set(loggerContext);
    }

    @AfterEach
    void tearDown() {
        Log4jServletFilter.restoreCurrentRequest(null);
        ContextAnchor.THREAD_CONTEXT.remove();
        loggerContext.stop();
    }

    @Test
    void requestKeysResolveOnlyWhileRequestIsBound() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpSession session = mock(HttpSession.class);
        final Principal principal = () -> "alice";
        when(request.getAttribute("foo")).thenReturn(42);
        when(request.getHeader("X-Foo")).thenReturn("header-value");
        when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("other", "x"), new Cookie("foo", "cookie")});
        when(request.getParameter("foo")).thenReturn("param");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/app/foo");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/app/foo"));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRemoteHost()).thenReturn("localhost");
        when(request.getRemotePort()).thenReturn(12345);
        when(request.getUserPrincipal()).thenReturn(principal);
        when(request.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("session-id");
        when(session.getAttribute("foo")).thenReturn("session-value");

        Log4jServletFilter.setCurrentRequest(request);
        assertEquals("42", lookup.lookup(null, "request.attr.foo"));
        assertNull(lookup.lookup(null, "request.attr.missing"));
        assertEquals("header-value", lookup.lookup(null, "header.X-Foo"));
        assertNull(lookup.lookup(null, "header.missing"));
        assertEquals("cookie", lookup.lookup(null, "cookie.foo"));
        assertNull(lookup.lookup(null, "cookie.missing"));
        assertEquals("param", lookup.lookup(null, "request.parameter.foo"));
        assertEquals("POST", lookup.lookup(null, "request.method"));
        assertEquals("/app/foo", lookup.lookup(null, "request.uri"));
        assertEquals("http://localhost/app/foo", lookup.lookup(null, "request.url"));
        assertEquals("127.0.0.1", lookup.lookup(null, "request.remoteAddress"));
        assertEquals("localhost", lookup.lookup(null, "request.remoteHost"));
        assertEquals("12345", lookup.lookup(null, "request.remotePort"));
        assertEquals("alice", lookup.lookup(null, "request.principal"));
        assertEquals("session-id", lookup.lookup(null, "session.id"));
        assertEquals("session-value", lookup.lookup(null, "session.attr.foo"));
        assertNull(lookup.lookup(null, "session.attr.missing"));

        Log4jServletFilter.restoreCurrentRequest(null);
        assertNull(lookup.lookup(null, "request.attr.foo"));
        assertNull(lookup.lookup(null, "header.X-Foo"));
        assertNull(lookup.lookup(null, "cookie.foo"));
        assertNull(lookup.lookup(null, "request.uri"));
        assertNull(lookup.lookup(null, "session.id"));
        assertNull(lookup.lookup(null, "session.attr.foo"));
    }

    @Test
    void missingCookiesAndSessionAreHandled() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        // `getCookies()` returns `null` if the request has no cookies
        when(request.getCookies()).thenReturn(null);
        when(request.getSession(false)).thenReturn(null);

        Log4jServletFilter.setCurrentRequest(request);
        assertNull(lookup.lookup(null, "cookie.foo"));
        assertNull(lookup.lookup(null, "session.id"));
        assertNull(lookup.lookup(null, "session.attr.foo"));
        // Logging must never create a session
        verify(request, never()).getSession();
        verify(request, never()).getSession(true);
    }

    @Test
    void httpKeysIgnoreNonHttpRequests() {
        final ServletRequest request = mock(ServletRequest.class);
        when(request.getAttribute("foo")).thenReturn("bar");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Log4jServletFilter.setCurrentRequest(request);
        assertEquals("bar", lookup.lookup(null, "request.attr.foo"));
        assertEquals("127.0.0.1", lookup.lookup(null, "request.remoteAddress"));
        assertNull(lookup.lookup(null, "header.foo"));
        assertNull(lookup.lookup(null, "request.uri"));
        assertNull(lookup.lookup(null, "session.id"));
    }

    @Test
    void filterBindsRequestDuringChain() throws Exception {
        final Log4jWebLifeCycle lifeCycle = mock(Log4jWebLifeCycle.class);
        when(servletContext.getAttribute(Log4jWebSupport.SUPPORT_ATTRIBUTE)).thenReturn(lifeCycle);
        final FilterConfig filterConfig = mock(FilterConfig.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        final Log4jServletFilter filter = new Log4jServletFilter();
        filter.init(filterConfig);

        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/app/foo");
        final AtomicReference<String> uriInChain = new AtomicReference<>();
        final FilterChain chain = (req, res) -> uriInChain.set(lookup.lookup(null, "request.uri"));

        filter.doFilter(request, mock(ServletResponse.class), chain);

        assertEquals("/app/foo", uriInChain.get());
        assertNull(Log4jServletFilter.getCurrentRequest());
        assertNull(lookup.lookup(null, "request.uri"));
    }

    @Test
    void wrapExecutionContextBindsRequest() {
        final Log4jWebLifeCycle lifeCycle = mock(Log4jWebLifeCycle.class);
        when(servletContext.getAttribute(Log4jWebSupport.SUPPORT_ATTRIBUTE)).thenReturn(lifeCycle);
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletContext()).thenReturn(servletContext);
        when(request.getRequestURI()).thenReturn("/app/async");
        when(request.getSession(anyBoolean())).thenReturn(null);
        final AtomicReference<String> uriInTask = new AtomicReference<>();

        WebLoggerContextUtils.wrapExecutionContext(request, () -> uriInTask.set(lookup.lookup(null, "request.uri")))
                .run();

        assertEquals("/app/async", uriInTask.get());
        assertNull(Log4jServletFilter.getCurrentRequest());
        verify(lifeCycle).setLoggerContext();
        verify(lifeCycle).clearLoggerContext();
    }
}
