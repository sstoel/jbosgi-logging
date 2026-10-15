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
package org.jboss.osgi.logging.internal;

import org.jboss.logging.Logger;
import org.osgi.framework.Bundle;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogLevel;

/**
 * A LogListener that logs LogEntrys to jboss-logging.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Mar-2009
 */
public class LogListenerBridge implements LogListener {

    // The logger names of the log entries of the framework events (OSGi Core R8, 101.7)
    private static final String[] EVENT_LOGGER_NAMES = { "Events.Bundle", "Events.Service", "Events.Framework" };

    public void logged(LogEntry entry) {
        Bundle bundle = entry.getBundle();
        Throwable throwable = entry.getException();

        // The log category is the bundle that logged, or the logger name when there is no bundle
        String loggerName = bundle != null ? bundle.getSymbolicName() : null;
        if (loggerName == null)
            loggerName = entry.getLoggerName();
        Logger log = Logger.getLogger(loggerName != null ? loggerName : LogListenerBridge.class.getName());

        String message = entry.getMessage();

        LogLevel level = entry.getLogLevel();
        switch (level != null ? level : LogLevel.INFO) {
            case TRACE:
                log.trace(message, throwable);
                break;
            case DEBUG:
                log.debug(message, throwable);
                break;
            case INFO:
                // Ignore the INFO entries of the framework events, e.g. 'BundleEvent STARTING'
                if (throwable != null || isFrameworkEventEntry(entry) == false) {
                    log.info(message, throwable);
                }
                break;
            case WARN:
                log.warn(message, throwable);
                break;
            case ERROR:
                log.error(message, throwable);
                break;
            case AUDIT:
                // Audit entries are always logged
                log.info(message, throwable);
                break;
        }
    }

    /**
     * Whether the entry is one of the entries that the framework logs for its events: their logger names are
     * Events.Bundle, Events.Service and Events.Framework, optionally followed by the symbolic name of the bundle
     */
    private static boolean isFrameworkEventEntry(LogEntry entry) {
        String loggerName = entry.getLoggerName();
        if (loggerName == null)
            return false;
        for (String eventLogger : EVENT_LOGGER_NAMES) {
            if (loggerName.equals(eventLogger) || loggerName.startsWith(eventLogger + "."))
                return true;
        }
        return false;
    }
}
