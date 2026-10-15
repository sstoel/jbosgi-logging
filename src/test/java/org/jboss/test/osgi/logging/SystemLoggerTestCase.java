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
package org.jboss.test.osgi.logging;

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
import org.osgi.service.log.LogService;
import org.osgi.service.log.Logger;

/**
 * Test the loggers of the {@link SystemLogService}
 */
public class SystemLoggerTestCase {

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

    private String getOutput() {
        String result = output.toString();
        output.reset();
        return result;
    }

    @Test
    public void testPlaceHolders() throws Exception {
        Logger logger = logService.getLogger("test.logger");
        Assert.assertEquals("test.logger", logger.getName());

        logger.info("Hello {} and {}", "foo", "bar");
        String line = getOutput();
        Assert.assertTrue(line, line.contains(" INFO,bnd=test.bundle,logger=test.logger,msg=Hello foo and bar]"));

        // An escaped place holder is a literal, a place holder without an argument stays
        logger.warn("Literal \\{} and {} and {}", "foo");
        line = getOutput();
        Assert.assertTrue(line, line.contains(" WARN,bnd=test.bundle,logger=test.logger,msg=Literal {} and foo and {}]"));

        logger.debug("Array {}", (Object) new String[] { "a", "b" });
        line = getOutput();
        Assert.assertTrue(line, line.contains("msg=Array [a, b]]"));
    }

    @Test
    public void testTrailingThrowable() throws Exception {
        Logger logger = logService.getLogger(SystemLoggerTestCase.class);
        Assert.assertEquals(SystemLoggerTestCase.class.getName(), logger.getName());

        // A trailing Throwable is not a message argument
        logger.error("Failed {}", "foo", new IllegalStateException("bar"));
        String text = getOutput();
        Assert.assertTrue(text, text.contains("msg=Failed foo,ex=java.lang.IllegalStateException: bar]"));

        logger.audit("Audit {}", "foo");
        text = getOutput();
        Assert.assertTrue(text, text.contains(" AUDIT,"));
        Assert.assertTrue(text, text.contains("msg=Audit foo]"));
    }

    @Test
    public void testFormatterLogger() throws Exception {
        FormatterLogger logger = logService.getLogger("test.formatter", FormatterLogger.class);

        logger.info("Hello %s, %d", "foo", 42);
        String line = getOutput();
        Assert.assertTrue(line, line.contains("msg=Hello foo, 42]"));

        // A format that does not fit its arguments is logged as it is
        logger.info("Hello %d", "foo");
        line = getOutput();
        Assert.assertTrue(line, line.contains("msg=Formatting failed (") && line.contains("format=Hello %d, arguments=[foo]]"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testLogService() throws Exception {
        logService.log(LogService.LOG_WARNING, "Deprecated call");
        String line = getOutput();
        Assert.assertTrue(line, line.contains("bnd=test.bundle,msg=Deprecated call]"));
        Assert.assertFalse(line, line.contains("logger="));
    }
}
