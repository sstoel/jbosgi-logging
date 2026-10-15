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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.jboss.osgi.logging.LogServiceTracker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

/**
 * {@code LogServiceTracker} falls back to the System.out {@code SystemLogService} as soon as <em>any</em> tracked
 * {@code LogService} goes away, although another {@code LogService} is still registered (and even when the removed one
 * was not the one in use); its log calls then bypass the Log Service until a new LogService is registered.
 *
 * <h3>Severity</h3>
 * minor. {@code LogServiceTracker} is exported API of the jboss-osgi-logging bundle (package
 * {@code org.jboss.osgi.logging}, a capability bundle of the WildFly OSGi subsystem) for bundles that want "a
 * LogService, or System.out when there is none". With the framework's Log Service plus any second LogService (a log
 * service bundle, a test double, a bundle that registers a LogService facade) that is unregistered again, every
 * LogServiceTracker user silently switches to System.out (outside the server log handlers, no Log Reader entries) for
 * the rest of its life.
 *
 * <h3>Component</h3>
 * jbosgi-logging, {@code org.jboss.osgi.logging.LogServiceTracker#addingService} / {@code #removedService}.
 *
 * <h3>Found by</h3>
 * subrepos-a workstream, javadoc-vs-behaviour review of jbosgi-logging; reproduced with the real OSGi
 * {@code ServiceTracker} (osgi.core 8.0.0) driven by service events.
 *
 * <h3>Spec reference</h3>
 * The class javadoc ({@code src/main/java/org/jboss/osgi/logging/LogServiceTracker.java:31-35}): "If a LogService is
 * registered, the LogServiceTracker delegates to that LogService. If there is no LogService registered, the
 * LogServiceTracker delegates to the SystemLogService". OSGi Core R8 701.6.5.3
 * {@code ServiceTrackerCustomizer.removedService}: "A service tracked by the ServiceTracker has been removed." (called
 * for that one service; the other tracked services are still tracked).
 *
 * <h3>Code location</h3>
 * {@code src/main/java/org/jboss/osgi/logging/LogServiceTracker.java:50-60} on WF41-osgi-r8:
 * {@code addingService} sets {@code log} to the newest service, {@code removedService} unconditionally sets
 * {@code log = new SystemLogService(context)} without looking at the remaining tracked services (and without checking
 * which service was removed). Pre-existing since 3521a45 (2011), present on WF41.
 *
 * <h3>Root cause</h3>
 * The tracker keeps a single "current" field that is reset on every removal instead of being derived from the tracked
 * services ({@code getService()} returns the highest ranked remaining one).
 *
 * <h3>Reproduction</h3>
 * <pre>
 * cd jbosgi-logging
 * export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
 * mvn -Dmaven.compiler.proc=full test -Dtest=LogServiceTrackerFallbackIssueTestCase
 * </pre>
 *
 * <h3>Observed</h3>
 * <pre>
 * testOtherLogServiceRemoved: LogService B is still registered after A was unregistered, so the log call must reach B,
 *   but B received [] (the call went to System.out: [2026-10-14 00:41.57.890 INFO,bnd=tracker.bundle,msg=after A removed])
 *   expected:&lt;[[after A removed]]&gt; but was:&lt;[[]]&gt;
 * testSecondLogServiceRemoved: LogService A is still registered after B was unregistered, so the log call must reach A,
 *   but A received [] (the call went to System.out: [... INFO,bnd=tracker.bundle,msg=after B removed])
 * </pre>
 *
 * <h3>Expected</h3>
 * While at least one LogService is tracked, the calls go to a tracked LogService; System.out only when none is left.
 *
 * <h3>Reference behaviour</h3>
 * n/a (jboss-osgi specific API).
 *
 * <h3>Suggested fix</h3>
 * Drop the field and delegate to {@code LogService service = getService(); return service != null ? service : fallback;}
 * (fallback = one SystemLogService created in the constructor); or in removedService pick {@code getService()} of the
 * remaining services (trial patch {@code LogService next = getService(); log = next != null ? next : new
 * SystemLogService(context);} makes this test and SystemLoggerTestCase pass).
 *
 * <h3>Regression vs WF41</h3>
 * no (pre-existing).
 */
public class LogServiceTrackerFallbackIssueTestCase {

    private final List<ServiceListener> listeners = new ArrayList<ServiceListener>();
    private PrintStream systemOut;
    private ByteArrayOutputStream output;
    private BundleContext context;

    @Before
    public void setUp() {
        systemOut = System.out;
        output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true));
        final Bundle bundle = (Bundle) proxy(Bundle.class, "tracker.bundle");
        context = (BundleContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { BundleContext.class }, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                String name = method.getName();
                if ("getBundle".equals(name))
                    return bundle;
                if ("createFilter".equals(name))
                    return FrameworkUtil.createFilter((String) args[0]);
                if ("addServiceListener".equals(name)) {
                    listeners.add((ServiceListener) args[0]);
                    return null;
                }
                if ("getService".equals(name))
                    return services.get(refs.indexOf(args[0]));
                if ("ungetService".equals(name))
                    return Boolean.TRUE;
                if ("equals".equals(name))
                    return proxy == args[0];
                if ("hashCode".equals(name))
                    return System.identityHashCode(proxy);
                return null;
            }
        });
    }

    @After
    public void tearDown() {
        System.setOut(systemOut);
    }

    private final List<ServiceReference<?>> refs = new ArrayList<ServiceReference<?>>();
    private final List<RecordingLogService> services = new ArrayList<RecordingLogService>();

    @Test
    @SuppressWarnings("deprecation")
    public void testOtherLogServiceRemoved() {
        LogServiceTracker tracker = new LogServiceTracker(context);
        ServiceReference<?> refA = register("A");
        register("B");
        unregister(refA);

        tracker.log(LogService.LOG_INFO, "after A removed");
        assertEquals("LogService B is still registered after A was unregistered, so the log call must reach B, but B received " + services.get(1).messages
                + " (the call went to System.out: " + output.toString().trim() + ")", "[after A removed]", services.get(1).messages.toString());
        assertEquals("Precondition: B is still tracked", 1, tracker.size());
        tracker.close();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testSecondLogServiceRemoved() {
        LogServiceTracker tracker = new LogServiceTracker(context);
        register("A");
        ServiceReference<?> refB = register("B");
        unregister(refB);

        tracker.log(LogService.LOG_INFO, "after B removed");
        assertEquals("LogService A is still registered after B was unregistered, so the log call must reach A, but A received " + services.get(0).messages
                + " (the call went to System.out: " + output.toString().trim() + ")", "[after B removed]", services.get(0).messages.toString());
        tracker.close();
    }

    private ServiceReference<?> register(String name) {
        ServiceReference<?> ref = (ServiceReference<?>) proxy(ServiceReference.class, "ref" + name);
        refs.add(ref);
        services.add(new RecordingLogService(name));
        fire(new ServiceEvent(ServiceEvent.REGISTERED, ref));
        return ref;
    }

    private void unregister(ServiceReference<?> ref) {
        fire(new ServiceEvent(ServiceEvent.UNREGISTERING, ref));
    }

    private void fire(ServiceEvent event) {
        for (ServiceListener listener : listeners)
            listener.serviceChanged(event);
    }

    private static Object proxy(Class<?> type, final String name) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String m = method.getName();
                if ("equals".equals(m))
                    return proxy == args[0];
                if ("hashCode".equals(m))
                    return System.identityHashCode(proxy);
                if ("toString".equals(m) || "getSymbolicName".equals(m))
                    return name;
                if ("getProperty".equals(m))
                    return null;
                if ("compareTo".equals(m))
                    return 0;
                return null;
            }
        });
    }

    static class RecordingLogService implements LogService {
        final String name;
        final List<String> messages = new ArrayList<String>();

        RecordingLogService(String name) {
            this.name = name;
        }

        @Override
        public void log(int level, String message) {
            messages.add(message);
        }

        @Override
        public void log(int level, String message, Throwable exception) {
            messages.add(message);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void log(ServiceReference sr, int level, String message) {
            messages.add(message);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void log(ServiceReference sr, int level, String message, Throwable exception) {
            messages.add(message);
        }

        @Override
        public org.osgi.service.log.Logger getLogger(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.osgi.service.log.Logger getLogger(Class<?> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <L extends org.osgi.service.log.Logger> L getLogger(String name, Class<L> loggerType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <L extends org.osgi.service.log.Logger> L getLogger(Class<?> clazz, Class<L> loggerType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <L extends org.osgi.service.log.Logger> L getLogger(org.osgi.framework.Bundle bundle, String name, Class<L> loggerType) {
            throw new UnsupportedOperationException();
        }
    }
}
