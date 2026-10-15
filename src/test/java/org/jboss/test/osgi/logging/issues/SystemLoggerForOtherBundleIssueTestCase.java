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
import org.osgi.service.log.Logger;

/**
 * {@code SystemLogService.getLogger(Bundle, String, Class)} ignores the bundle: the entries of a logger obtained "for
 * the specified bundle" are written as entries of the bundle that owns the log service, and an unresolved bundle is
 * accepted instead of being rejected with {@code IllegalArgumentException}.
 *
 * <h3>Severity</h3>
 * cosmetic. SystemLogService is the System.out fallback of jboss-osgi-logging ({@code LogServiceTracker} uses it when
 * no LogService is registered) and exported API of the {@code org.jboss.osgi.logging} package; the entries of code
 * that logs on behalf of another bundle (extenders such as Declarative Services or Blueprint use exactly this method)
 * are attributed to the wrong bundle ("bnd=" field) in that fallback output.
 *
 * <h3>Component</h3>
 * jbosgi-logging, {@code org.jboss.osgi.logging.SystemLogService#getLogger(Bundle, String, Class)} (added on
 * WF41-osgi-r8 for Log Service 1.4, commit bdcaf98).
 *
 * <h3>Found by</h3>
 * subrepos-a workstream, javadoc conformance check of the new Log 1.4 Logger support in jbosgi-logging.
 *
 * <h3>Spec reference</h3>
 * OSGi Compendium R7 Log Service 1.4, {@code org.osgi.service.log.LoggerFactory.getLogger(Bundle bundle, String name,
 * Class<L> loggerType)}: "Return the Logger of the specified type named with the specified name for the specified
 * bundle. ... This method is used to obtain a Logger for the specified bundle which may be useful to code which is
 * logging on behalf of another bundle. ... @throws IllegalArgumentException If the specified type is not a supported
 * Logger type or the specified Bundle is not a resolved bundle." (org.osgi.service.log 1.5.0 sources, same text in
 * 1.4).
 *
 * <h3>Code location</h3>
 * {@code src/main/java/org/jboss/osgi/logging/SystemLogService.java:80-84} on WF41-osgi-r8:
 * <pre>
 * public &lt;L extends Logger&gt; L getLogger(Bundle bundle, String name, Class&lt;L&gt; loggerType) {
 *     // Entries are always written on behalf of the bundle that owns this log service
 *     return getLogger(name, loggerType);
 * }
 * </pre>
 * and {@code SystemLogService.write} ({@code :99-127}), which always prints {@code context.getBundle()}.
 * Introduced on WF41-osgi-r8 (bdcaf98, "Implement the Log Service 1.4 of OSGi Compendium R7").
 *
 * <h3>Root cause</h3>
 * The bundle argument is dropped; the logger has no bundle of its own.
 *
 * <h3>Reproduction</h3>
 * <pre>
 * cd jbosgi-logging
 * export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
 * mvn -Dmaven.compiler.proc=full test -Dtest=SystemLoggerForOtherBundleIssueTestCase
 * </pre>
 *
 * <h3>Observed</h3>
 * <pre>
 * testEntriesOfSpecifiedBundle: A logger obtained for bundle other.bundle must write entries of that bundle, but the
 *   entry is [... INFO,bnd=owner.bundle,logger=on.behalf,msg=hello]
 * testUnresolvedBundleRejected: getLogger(Bundle, ...) must throw IllegalArgumentException for a bundle that is not
 *   resolved (state INSTALLED), but it returned a logger
 * </pre>
 *
 * <h3>Expected</h3>
 * The entry shows {@code bnd=other.bundle}; an INSTALLED bundle is rejected with IllegalArgumentException.
 *
 * <h3>Reference behaviour</h3>
 * The framework's own LoggerFactory (jbosgi-framework {@code LogServiceImpl}) associates such loggers with the given
 * bundle (tck/06 and wildfly/24 are about other aspects of that method).
 *
 * <h3>Suggested fix</h3>
 * Check {@code (bundle.getState() &amp; (Bundle.RESOLVED | Bundle.STARTING | Bundle.ACTIVE | Bundle.STOPPING)) != 0}
 * (else IllegalArgumentException) and pass the bundle to the SystemLogger, which hands it to {@code write} instead of
 * {@code context.getBundle()}.
 *
 * <h3>Regression vs WF41</h3>
 * no in the sense that WF41 had no Logger API in this class at all; the method is new on WF41-osgi-r8.
 */
public class SystemLoggerForOtherBundleIssueTestCase {

    private PrintStream systemOut;
    private ByteArrayOutputStream output;
    private SystemLogService logService;

    @Before
    public void setUp() {
        systemOut = System.out;
        output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true));
        final Bundle owner = bundle("owner.bundle", Bundle.ACTIVE);
        BundleContext context = (BundleContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { BundleContext.class }, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                return "getBundle".equals(method.getName()) ? owner : null;
            }
        });
        logService = new SystemLogService(context);
    }

    @After
    public void tearDown() {
        System.setOut(systemOut);
    }

    @Test
    public void testEntriesOfSpecifiedBundle() {
        Logger logger = logService.getLogger(bundle("other.bundle", Bundle.ACTIVE), "on.behalf", Logger.class);
        logger.info("hello");
        String line = output.toString().trim();
        Assert.assertTrue("A logger obtained for bundle other.bundle must write entries of that bundle, but the entry is " + line, line.contains(",bnd=other.bundle,"));
    }

    @Test
    public void testUnresolvedBundleRejected() {
        try {
            logService.getLogger(bundle("installed.bundle", Bundle.INSTALLED), "on.behalf", Logger.class);
        } catch (IllegalArgumentException expected) {
            return;
        }
        Assert.fail("getLogger(Bundle, ...) must throw IllegalArgumentException for a bundle that is not resolved (state INSTALLED), but it returned a logger");
    }

    private static Bundle bundle(final String symbolicName, final int state) {
        return (Bundle) Proxy.newProxyInstance(SystemLoggerForOtherBundleIssueTestCase.class.getClassLoader(), new Class<?>[] { Bundle.class }, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("getSymbolicName".equals(name) || "toString".equals(name))
                    return symbolicName;
                if ("getState".equals(name))
                    return state;
                if ("equals".equals(name))
                    return proxy == args[0];
                if ("hashCode".equals(name))
                    return System.identityHashCode(proxy);
                return null;
            }
        });
    }
}
