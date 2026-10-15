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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LogService;
import org.osgi.service.log.Logger;

/**
 * A basic LogService that writes to System.out
 *
 * @author thomas.diesler@jboss.com
 * @since 11-Apr-2009
 */
public class SystemLogService implements LogService {
    private BundleContext context;

    public SystemLogService(BundleContext context) {
        this.context = context;
    }

    public void log(int level, String message) {
        logInternal(null, level, message, null);
    }

    public void log(int level, String message, Throwable exception) {
        logInternal(null, level, message, exception);
    }

    @SuppressWarnings("rawtypes")
    public void log(ServiceReference sr, int level, String message) {
        logInternal(sr, level, message, null);
    }

    @SuppressWarnings("rawtypes")
    public void log(ServiceReference sr, int level, String message, Throwable exception) {
        logInternal(sr, level, message, exception);
    }

    @Override
    public Logger getLogger(String name) {
        return getLogger(name, Logger.class);
    }

    @Override
    public Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName(), Logger.class);
    }

    @Override
    public <L extends Logger> L getLogger(Class<?> clazz, Class<L> loggerType) {
        return getLogger(clazz.getName(), loggerType);
    }

    @Override
    public <L extends Logger> L getLogger(Bundle bundle, String name, Class<L> loggerType) {
        // The entries are written on behalf of the given bundle, which must be resolved (101.3)
        if (bundle == null || (bundle.getState() & (Bundle.RESOLVED | Bundle.STARTING | Bundle.ACTIVE | Bundle.STOPPING)) == 0)
            throw new IllegalArgumentException("Bundle not resolved: " + bundle);
        return getLoggerInternal(bundle, name, loggerType);
    }

    @Override
    public <L extends Logger> L getLogger(String name, Class<L> loggerType) {
        return getLoggerInternal(null, name, loggerType);
    }

    private <L extends Logger> L getLoggerInternal(Bundle bundle, String name, Class<L> loggerType) {
        if (loggerType != Logger.class && loggerType != FormatterLogger.class)
            throw new IllegalArgumentException("Unsupported logger type: " + loggerType);
        return loggerType.cast(new SystemLogger(this, bundle, name, loggerType == FormatterLogger.class));
    }

    @SuppressWarnings("rawtypes")
    private void logInternal(ServiceReference sref, int level, String message, Throwable exception) {
        write(null, sref, logLevel(level), null, message, exception);
    }

    /**
     * Write an entry on behalf of the given bundle, or of the bundle that owns this log service if it is null
     */
    @SuppressWarnings("rawtypes")
    void write(Bundle loggerBundle, ServiceReference sref, String levelName, String loggerName, String message, Throwable exception) {
        Bundle bundle = loggerBundle;
        if (bundle == null) {
            try {
                bundle = context.getBundle();
            } catch (IllegalStateException ex) {
                // If this BundleContext is no longer valid.
                return;
            }
        }

        long time = System.currentTimeMillis();
        String bndStr = bundle.getSymbolicName();

        String srefStr = null;
        if (sref != null && sref.getBundle() != null)
            srefStr = sref.getBundle().getSymbolicName();

        String t = new SimpleDateFormat("yyyy-MM-dd HH:mm.ss.SSS").format(new Date(time));
        String l = " " + levelName;
        String n = loggerName != null ? ",logger=" + loggerName : "";
        String s = srefStr != null ? ",sref=" + srefStr : "";
        String b = ",bnd=" + bndStr;
        String m = ",msg=" + message;
        String e = exception != null ? ",ex=" + exception : "";

        System.out.println("[" + t + l + b + n + s + m + e + "]");

        if (exception != null)
            exception.printStackTrace(System.out);
    }

    @SuppressWarnings("deprecation")
    private String logLevel(int level) {
        String logLevel;
        switch (level) {
        case LogService.LOG_DEBUG:
            logLevel = "DEBUG";
            break;
        case LogService.LOG_INFO:
            logLevel = "INFO";
            break;
        case LogService.LOG_WARNING:
            logLevel = "WARN";
            break;
        case LogService.LOG_ERROR:
            logLevel = "ERROR";
            break;
        default:
            logLevel = "Level=" + level;
        }
        return logLevel;
    }
}