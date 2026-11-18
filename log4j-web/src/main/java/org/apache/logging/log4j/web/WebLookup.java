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
// Please note that if you move this class, make sure to update the Interpolator class (if still applicable) or remove
// this comment if no longer relevant

import java.security.Principal;
import java.util.Objects;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.AbstractLookup;
import org.apache.logging.log4j.util.Strings;

@Plugin(name = "web", category = "Lookup")
public class WebLookup extends AbstractLookup {
    private static final String ATTR_PREFIX = "attr.";
    private static final String REQUEST_ATTR_PREFIX = "request.attr.";
    private static final String REQUEST_HEADER_PREFIX = "header.";
    private static final String REQUEST_COOKIE_PREFIX = "cookie.";
    private static final String REQUEST_PARAMETER_PREFIX = "request.parameter.";
    private static final String SESSION_ATTR_PREFIX = "session.attr.";
    private static final String INIT_PARAM_PREFIX = "initParam.";

    @Override
    public String lookup(final LogEvent event, final String key) {
        final ServletContext ctx = WebLoggerContextUtils.getServletContext();
        if (ctx == null) {
            return null;
        }

        if (key.startsWith(ATTR_PREFIX)) {
            final String attrName = key.substring(ATTR_PREFIX.length());
            final Object attrValue = ctx.getAttribute(attrName);
            return attrValue == null ? null : attrValue.toString();
        }

        if (key.startsWith(INIT_PARAM_PREFIX)) {
            final String paramName = key.substring(INIT_PARAM_PREFIX.length());
            return ctx.getInitParameter(paramName);
        }

        if (key.startsWith(REQUEST_ATTR_PREFIX)) {
            final ServletRequest request = Log4jServletFilter.getCurrentRequest();
            return request == null
                    ? null
                    : Objects.toString(request.getAttribute(key.substring(REQUEST_ATTR_PREFIX.length())), null);
        }

        if (key.startsWith(REQUEST_HEADER_PREFIX)) {
            final HttpServletRequest request = getHttpRequest();
            return request == null ? null : request.getHeader(key.substring(REQUEST_HEADER_PREFIX.length()));
        }

        if (key.startsWith(REQUEST_COOKIE_PREFIX)) {
            final HttpServletRequest request = getHttpRequest();
            return request == null ? null : getCookieValue(request, key.substring(REQUEST_COOKIE_PREFIX.length()));
        }

        if (key.startsWith(REQUEST_PARAMETER_PREFIX)) {
            final HttpServletRequest request = getHttpRequest();
            return request == null ? null : request.getParameter(key.substring(REQUEST_PARAMETER_PREFIX.length()));
        }

        if (key.startsWith(SESSION_ATTR_PREFIX)) {
            final HttpSession session = getHttpSession();
            return session == null
                    ? null
                    : Objects.toString(session.getAttribute(key.substring(SESSION_ATTR_PREFIX.length())), null);
        }

        if ("request.method".equals(key)) {
            final HttpServletRequest request = getHttpRequest();
            return request == null ? null : request.getMethod();
        }

        if ("request.uri".equals(key)) {
            final HttpServletRequest request = getHttpRequest();
            return request == null ? null : request.getRequestURI();
        }

        if ("request.url".equals(key)) {
            final HttpServletRequest request = getHttpRequest();
            return request == null ? null : Objects.toString(request.getRequestURL(), null);
        }

        if ("request.remoteAddress".equals(key)) {
            final ServletRequest request = Log4jServletFilter.getCurrentRequest();
            return request == null ? null : request.getRemoteAddr();
        }

        if ("request.remoteHost".equals(key)) {
            final ServletRequest request = Log4jServletFilter.getCurrentRequest();
            return request == null ? null : request.getRemoteHost();
        }

        if ("request.remotePort".equals(key)) {
            final ServletRequest request = Log4jServletFilter.getCurrentRequest();
            return request == null ? null : Integer.toString(request.getRemotePort());
        }

        if ("request.principal".equals(key)) {
            final HttpServletRequest request = getHttpRequest();
            final Principal principal = request == null ? null : request.getUserPrincipal();
            return principal == null ? null : principal.getName();
        }

        if ("session.id".equals(key)) {
            final HttpSession session = getHttpSession();
            return session == null ? null : session.getId();
        }

        if ("rootDir".equals(key)) {
            final String root = ctx.getRealPath("/");
            if (root == null) {
                final String msg =
                        "Failed to resolve web:rootDir -- " + "servlet container unable to translate virtual path "
                                + " to real path (probably not deployed as exploded";
                throw new IllegalStateException(msg);
            }
            return root;
        }

        if ("contextPathName".equals(key)) {
            final String path = ctx.getContextPath();
            if (path.trim().contains("/")) {
                final String[] fields = path.split("/");
                for (String field : fields) {
                    if (field.length() > 0) {
                        return field;
                    }
                }
                return null;
            }
            return ctx.getContextPath();
        }

        if ("contextPath".equals(key)) {
            return ctx.getContextPath();
        }

        if ("servletContextName".equals(key)) {
            return ctx.getServletContextName();
        }

        if ("serverInfo".equals(key)) {
            return ctx.getServerInfo();
        }

        if ("effectiveMajorVersion".equals(key)) {
            return String.valueOf(ctx.getEffectiveMajorVersion());
        }

        if ("effectiveMinorVersion".equals(key)) {
            return String.valueOf(ctx.getEffectiveMinorVersion());
        }

        if ("majorVersion".equals(key)) {
            return String.valueOf(ctx.getMajorVersion());
        }

        if ("minorVersion".equals(key)) {
            return String.valueOf(ctx.getMinorVersion());
        }

        if (ctx.getAttribute(key) != null) {
            return ctx.getAttribute(key).toString();
        }

        if (ctx.getInitParameter(key) != null) {
            return ctx.getInitParameter(key);
        }

        ctx.log(getClass().getName() + " unable to resolve key " + Strings.quote(key));
        return null;
    }

    private static HttpServletRequest getHttpRequest() {
        final ServletRequest request = Log4jServletFilter.getCurrentRequest();
        return request instanceof HttpServletRequest ? (HttpServletRequest) request : null;
    }

    private static HttpSession getHttpSession() {
        final HttpServletRequest request = getHttpRequest();
        // Never create a session as a side effect of logging
        return request == null ? null : request.getSession(false);
    }

    private static String getCookieValue(final HttpServletRequest request, final String name) {
        final Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (final Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
