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
                // Ignore INFO message of type 'BundleEvent STARTING'
                String[] splitMessage = message != null ? message.split("[\\s]") : new String[0];
                if (throwable != null || splitMessage.length != 2 || splitMessage[0].endsWith("Event") == false) {
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
}
