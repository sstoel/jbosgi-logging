/*
 * JBoss, Home of Professional Open Source
 * Copyright 2026, JBoss Inc., and individual contributors as indicated
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
import org.osgi.service.log.FormatterLogger;

/**
 * A {@code FormatterLogger} of {@code SystemLogService} logs a message whose printf format fails as if it were a normal
 * message ({@code "Hello %d [foo]"}); the entry does not say that formatting failed.
 *
 * <h3>Severity</h3>
 * cosmetic. Only the System.out fallback of jboss-osgi-logging is affected; a reader of the output cannot tell a
 * formatting failure from a message that literally contains "%d". The baseline test
 * {@code SystemLoggerTestCase.testFormatterLogger} asserts this output ("A format that does not fit its arguments is
 * logged as it is"), i.e. it codifies the deviation.
 *
 * <h3>Component</h3>
 * jbosgi-logging, {@code org.jboss.osgi.logging.SystemLogger#formatMessage} (new on WF41-osgi-r8, commit bdcaf98).
 *
 * <h3>Found by</h3>
 * subrepos-a workstream, javadoc conformance check of the Log 1.4 Logger support in jbosgi-logging.
 *
 * <h3>Spec reference</h3>
 * OSGi Compendium R7 Log Service 1.4, {@code org.osgi.service.log.FormatterLogger} class javadoc: "If an exception
 * occurs formatting the message, the logged message will indicate the formatting failure including the format string
 * and the arguments." (org.osgi.service.log 1.5.0 sources, unchanged since 1.4)
 *
 * <h3>Code location</h3>
 * {@code src/main/java/org/jboss/osgi/logging/SystemLogger.java:252-257} on WF41-osgi-r8:
 * {@code try { return String.format(format, args); } catch (RuntimeException ex) { return format + " " + Arrays.deepToString(args); }}
 * Introduced on WF41-osgi-r8 (bdcaf98).
 *
 * <h3>Root cause</h3>
 * The fallback text contains the format and the arguments but nothing that marks it as a failure.
 *
 * <h3>Reproduction</h3>
 * <pre>
 * cd jbosgi-logging
 * export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
 * mvn -Dmaven.compiler.proc=full test -Dtest=FormatterLoggerFailureNotIndicatedIssueTestCase
 * </pre>
 *
 * <h3>Observed</h3>
 * <pre>
 * testFormattingFailureIndicated: The logged message must indicate the formatting failure (FormatterLogger javadoc),
 *   but it is [... INFO,bnd=test.bundle,logger=test.formatter,msg=Hello %d [foo]]
 * </pre>
 *
 * <h3>Expected</h3>
 * A message that names the failure, e.g. {@code "Formatting failed: java.util.IllegalFormatConversionException ...
 * format=Hello %d, arguments=[foo]"}.
 *
 * <h3>Reference behaviour</h3>
 * Not checked against Equinox/Felix.
 *
 * <h3>Suggested fix</h3>
 * {@code return "Formatting failed (" + ex + "): format=" + format + ", arguments=" + Arrays.deepToString(args);} and adapt
 * the assertion in SystemLoggerTestCase.testFormatterLogger.
 *
 * <h3>Regression vs WF41</h3>
 * no in the sense that WF41 had no FormatterLogger in this class; the code is new on WF41-osgi-r8.
 */
public class FormatterLoggerFailureNotIndicatedIssueTestCase {

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
    public void testFormattingFailureIndicated() {
        FormatterLogger logger = logService.getLogger("test.formatter", FormatterLogger.class);
        logger.info("Hello %d", "foo");
        String line = output.toString().trim();
        String message = line.substring(line.indexOf(",msg=") + 5);
        Assert.assertTrue("Precondition: format string and argument are logged: " + line, message.contains("Hello %d") && message.contains("foo"));
        String lower = message.toLowerCase();
        Assert.assertTrue("The logged message must indicate the formatting failure (FormatterLogger javadoc), but it is " + line,
                lower.contains("fail") || lower.contains("error") || lower.contains("exception"));
    }
}
