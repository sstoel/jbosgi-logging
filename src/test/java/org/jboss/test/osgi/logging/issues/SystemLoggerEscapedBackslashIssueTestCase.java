/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.osgi.logging.issues;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.jboss.osgi.logging.SystemLogService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.Logger;

/**
 * The Logger of the SystemLogService treats an escaped backslash before a place holder ("\\{}") as an escaped
 * place holder: it prints "\{}" literally instead of a backslash followed by the argument.
 *
 * <h3>Severity</h3>
 * minor. Messages that need a backslash in front of a formatted value (Windows paths, regular expressions) are
 * logged with a literal "\{}" and the argument is lost (it shifts to the next place holder) when the fallback
 * SystemLogService of LogServiceTracker is used.
 *
 * <h3>Component</h3>
 * jbosgi-logging, org.jboss.osgi.logging.SystemLogger#formatMessage
 *
 * <h3>Found by</h3>
 * services workstream, review of the Log Service 1.4 Logger implementations against the Logger javadoc; the
 * framework's own LoggerImpl (internal/log) handles this case correctly.
 *
 * <h3>Spec reference</h3>
 * OSGi Core R8, 101.2 The Logger Interface (https://docs.osgi.org/specification/osgi.core/8.0.0/service.log.html)
 * and org.osgi.service.log.Logger javadoc (Log Service 1.5): "If you need to use the literal "{}" in the formatted
 * message, precede the place holder with a reverse solidus ('\'): "\{}". If you need to place a backslash before
 * the place holder, precede the reverse solidus with a reverse solidus: "\\{}"."
 *
 * <h3>Code location</h3>
 * src/main/java/org/jboss/osgi/logging/SystemLogger.java:270-281 (formatMessage):
 * {@code if (index > 0 && format.charAt(index - 1) == '\\') { result.append(format, start, index - 1).append("{}"); }}
 * Added by commit bdcaf98 ("Implement the Log Service 1.4 of OSGi Compendium R7").
 *
 * <h3>Root cause</h3>
 * formatMessage only looks at the one character before "{}". A backslash that is itself escaped by a preceding
 * backslash is not recognised, so "\\{}" is handled like "\{}".
 *
 * <h3>Reproduction</h3>
 * JDK 25: {@code JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home mvn -Dmaven.compiler.proc=full
 * test -Dtest=SystemLoggerEscapedBackslashIssueTestCase} in jbosgi-logging
 *
 * <h3>Observed</h3>
 * <pre>
 * java.lang.AssertionError: The format C:\\{} and {} with the arguments temp, x must be logged as 'C:\temp and x',
 * but the output was: [2026-10-13 20:10.40.348 INFO,bnd=test.bundle,logger=test.logger,msg=C:\{} and temp]
 * </pre>
 *
 * <h3>Expected</h3>
 * {@code logger.info("C:\\\\{} and {}", "temp", "x")} (Java source; the format is C:\\{} and {}) logs
 * {@code C:\temp and x}.
 *
 * <h3>Reference behaviour</h3>
 * The framework's own Logger (org.jboss.osgi.framework.internal.log.LoggerImpl) and Equinox both produce
 * {@code C:\temp and x} for this format.
 *
 * <h3>Suggested fix</h3>
 * Count the backslashes directly before "{}": one escapes the place holder, two produce one backslash followed by
 * the argument (as LoggerImpl.format in the framework does).
 *
 * <h3>Regression vs WF41</h3>
 * no (the Logger API is new in this branch, the bug is in new code).
 */
public class SystemLoggerEscapedBackslashIssueTestCase {

    private PrintStream systemOut;
    private ByteArrayOutputStream output;
    private SystemLogService logService;

    @Before
    public void setUp() {
        systemOut = System.out;
        output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true));

        final Bundle bundle = (Bundle) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Bundle.class }, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                return "getSymbolicName".equals(method.getName()) ? "test.bundle" : null;
            }
        });
        BundleContext context = (BundleContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { BundleContext.class }, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                return "getBundle".equals(method.getName()) ? bundle : null;
            }
        });
        logService = new SystemLogService(context);
    }

    @After
    public void tearDown() {
        System.setOut(systemOut);
    }

    @Test
    public void testEscapedBackslashBeforePlaceHolder() throws Exception {
        Logger logger = logService.getLogger("test.logger");
        logger.info("C:\\\\{} and {}", "temp", "x");
        String line = output.toString();
        Assert.assertTrue("The format C:\\\\{} and {} with the arguments temp, x must be logged as 'C:\\temp and x', but the output was: " + line,
                line.contains("msg=C:\\temp and x]"));
    }
}
