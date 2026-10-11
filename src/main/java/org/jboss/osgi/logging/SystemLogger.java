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
package org.jboss.osgi.logging;

import java.util.Arrays;

import org.osgi.framework.ServiceReference;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerConsumer;

/**
 * A basic Logger that writes to System.out through the {@link SystemLogService}.
 *
 * A {@link Logger} replaces <code>{}</code> place holders in the message, a {@link FormatterLogger}
 * uses the printf-style format of {@link String#format(String, Object...)}. A trailing
 * {@link Throwable} or {@link ServiceReference} argument is not part of the message.
 *
 * @since 11-Oct-2026
 */
final class SystemLogger implements FormatterLogger {

    private final SystemLogService logService;
    private final String name;
    private final boolean printfFormat;

    SystemLogger(SystemLogService logService, String name, boolean printfFormat) {
        this.logService = logService;
        this.name = name;
        this.printfFormat = printfFormat;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public void trace(String message) {
        log(LogLevel.TRACE, message);
    }

    @Override
    public void trace(String format, Object arg) {
        log(LogLevel.TRACE, format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        log(LogLevel.TRACE, format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        log(LogLevel.TRACE, format, arguments);
    }

    @Override
    public <E extends Exception> void trace(LoggerConsumer<E> consumer) throws E {
        consumer.accept(this);
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    @Override
    public void debug(String format, Object arg) {
        log(LogLevel.DEBUG, format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        log(LogLevel.DEBUG, format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        log(LogLevel.DEBUG, format, arguments);
    }

    @Override
    public <E extends Exception> void debug(LoggerConsumer<E> consumer) throws E {
        consumer.accept(this);
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    @Override
    public void info(String format, Object arg) {
        log(LogLevel.INFO, format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        log(LogLevel.INFO, format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        log(LogLevel.INFO, format, arguments);
    }

    @Override
    public <E extends Exception> void info(LoggerConsumer<E> consumer) throws E {
        consumer.accept(this);
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String message) {
        log(LogLevel.WARN, message);
    }

    @Override
    public void warn(String format, Object arg) {
        log(LogLevel.WARN, format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        log(LogLevel.WARN, format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        log(LogLevel.WARN, format, arguments);
    }

    @Override
    public <E extends Exception> void warn(LoggerConsumer<E> consumer) throws E {
        consumer.accept(this);
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    @Override
    public void error(String format, Object arg) {
        log(LogLevel.ERROR, format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        log(LogLevel.ERROR, format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        log(LogLevel.ERROR, format, arguments);
    }

    @Override
    public <E extends Exception> void error(LoggerConsumer<E> consumer) throws E {
        consumer.accept(this);
    }

    @Override
    public void audit(String message) {
        log(LogLevel.AUDIT, message);
    }

    @Override
    public void audit(String format, Object arg) {
        log(LogLevel.AUDIT, format, arg);
    }

    @Override
    public void audit(String format, Object arg1, Object arg2) {
        log(LogLevel.AUDIT, format, arg1, arg2);
    }

    @Override
    public void audit(String format, Object... arguments) {
        log(LogLevel.AUDIT, format, arguments);
    }

    private void log(LogLevel level, String format, Object... arguments) {
        Object[] args = arguments != null ? arguments : new Object[] { null };
        Throwable exception = null;
        ServiceReference<?> sref = null;

        // A trailing Throwable and ServiceReference are not message arguments
        for (int i = 0; i < 2 && args.length > 0; i++) {
            Object last = args[args.length - 1];
            if (last instanceof Throwable && exception == null) {
                exception = (Throwable) last;
            } else if (last instanceof ServiceReference && sref == null) {
                sref = (ServiceReference<?>) last;
            } else {
                break;
            }
            args = Arrays.copyOf(args, args.length - 1);
        }
        logService.write(sref, level.name(), name, formatMessage(format, args), exception);
    }

    private String formatMessage(String format, Object[] args) {
        if (format == null || args.length == 0)
            return format;

        if (printfFormat) {
            try {
                return String.format(format, args);
            } catch (RuntimeException ex) {
                return format + " " + Arrays.deepToString(args);
            }
        }

        // Replace the {} place holders, \{} is a literal
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int start = 0;
        while (true) {
            int index = format.indexOf("{}", start);
            if (index < 0) {
                result.append(format, start, format.length());
                break;
            }
            if (index > 0 && format.charAt(index - 1) == '\\') {
                result.append(format, start, index - 1).append("{}");
            } else if (argIndex >= args.length) {
                result.append(format, start, index + 2);
            } else {
                result.append(format, start, index).append(toString(args[argIndex++]));
            }
            start = index + 2;
        }
        return result.toString();
    }

    private static String toString(Object arg) {
        if (arg instanceof Object[])
            return Arrays.deepToString((Object[]) arg);
        return String.valueOf(arg);
    }
}
