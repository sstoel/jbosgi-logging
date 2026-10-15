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

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.osgi.logging.internal.LogListenerBridge;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;

/**
 * The LogListenerBridge, which writes the entries of the Log Service to jboss-logging (the server log), drops every
 * INFO entry whose message is two words of which the first ends with "Event" (e.g. "OrderEvent received"), because it
 * recognises the entries of the framework events ("BundleEvent STARTING") by the shape of the message.
 *
 * The jbosgi-logging part of the issue services/20 (LogBridgeDropsEventLikeMessagesIssueTestCase in the umbrella
 * integration testsuite). OSGi Core R8, 101.5: "all log messages must be sent to subscribers of the Log Reader Service
 * as they are recorded"; 101.7: the entries of the framework events have the logger names Events.Bundle,
 * Events.Service and Events.Framework.
 *
 * Code location: {@code src/main/java/org/jboss/osgi/logging/internal/LogListenerBridge.java} (case INFO).
 *
 * The test assumes that jboss-logging writes to java.util.logging, the only logging backend on the test class path.
 */
public class LogBridgeDropsEventLikeMessagesIssueTestCase {

    private static final String SYMBOLIC_NAME = "log.bridge.test.bundle";

    private final List<String> messages = new ArrayList<String>();
    private java.util.logging.Logger julLogger;
    private Handler handler;

    @Before
    public void setUp() {
        julLogger = java.util.logging.Logger.getLogger(SYMBOLIC_NAME);
        julLogger.setUseParentHandlers(false);
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        julLogger.addHandler(handler);
    }

    @After
    public void tearDown() {
        julLogger.removeHandler(handler);
        julLogger.setUseParentHandlers(true);
    }

    @Test
    public void testEventLikeInfoMessageLogged() {
        LogListenerBridge bridge = new LogListenerBridge();
        bridge.logged(entry("log.bridge.test", LogLevel.INFO, "Control received"));
        assertEquals("Precondition: an INFO entry reaches jboss-logging", "[Control received]", messages.toString());

        bridge.logged(entry("log.bridge.test", LogLevel.INFO, "OrderEvent received"));
        assertEquals("The INFO entry 'OrderEvent received' of a bundle logger must be logged", "[Control received, OrderEvent received]", messages.toString());
    }

    @Test
    public void testFrameworkEventEntriesIgnored() {
        LogListenerBridge bridge = new LogListenerBridge();
        bridge.logged(entry("Events.Bundle." + SYMBOLIC_NAME, LogLevel.INFO, "BundleEvent STARTING"));
        bridge.logged(entry("Events.Service." + SYMBOLIC_NAME, LogLevel.INFO, "ServiceEvent REGISTERED"));
        bridge.logged(entry("Events.Framework", LogLevel.INFO, "FrameworkEvent STARTED"));
        assertEquals("The INFO entries of the framework events are not logged", "[]", messages.toString());

        bridge.logged(entry("Events.Framework", LogLevel.ERROR, "FrameworkEvent ERROR"));
        assertEquals("The ERROR entries of the framework events are logged", "[FrameworkEvent ERROR]", messages.toString());
    }

    private static LogEntry entry(final String loggerName, final LogLevel level, final String message) {
        final Bundle bundle = (Bundle) Proxy.newProxyInstance(Bundle.class.getClassLoader(), new Class<?>[] { Bundle.class }, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return "getSymbolicName".equals(method.getName()) ? SYMBOLIC_NAME : null;
            }
        });
        return (LogEntry) Proxy.newProxyInstance(LogEntry.class.getClassLoader(), new Class<?>[] { LogEntry.class }, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("getBundle".equals(name))
                    return bundle;
                if ("getLoggerName".equals(name))
                    return loggerName;
                if ("getLogLevel".equals(name))
                    return level;
                if ("getMessage".equals(name))
                    return message;
                if ("getLevel".equals(name))
                    return Integer.valueOf(level.ordinal());
                if ("getTime".equals(name) || "getSequence".equals(name))
                    return Long.valueOf(0);
                return null;
            }
        });
    }
}
