/*
 * Copyright 2019 Zhong Lunfu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ji.core;

import fj.F0;

/**
 * A simple logger based on {@link System#err }.
 */
public abstract class Logger {

    private static final Logger SINGLETON = new Logger() {};

    public static Logger singleton() {
        return SINGLETON;
    }

    private final Level level;

    private Logger() {
        final String name = System.getProperty("ji.logging.level", Level.INFO.name());
        Level l;
        try {
            l = Level.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            Level.WARN.log("Supported level " + name, null);
            l = Level.INFO;
        }

        level = l;
    }

    public void debug(String message) {
        debug(() -> message);
    }

    public void debug(F0<String> message) {
        log(Level.DEBUG, message);
    }

    public void info(String message) {
        info(() -> message);
    }

    public void info(F0<String> message) {
        log(Level.INFO, message);
    }

    public void warn(String message) {
        warn(message, null);
    }

    public void warn(String message, Throwable cause) {
        warn(() -> message, cause);
    }

    public void warn(F0<String> message) {
        warn(message, null);
    }

    public void warn(F0<String> message, Throwable cause) {
        log(Level.WARN, message, cause);
    }

    private void log(Level l, F0<String> message) {
        log(l, message, null);
    }

    private void log(Level l, F0<String> message, Throwable cause) {
        if (l.ordinal() >= level.ordinal()) l.log(message.f(), cause);
    }

    private enum Level {
        DEBUG, INFO, WARN;

        public void log(String message, Throwable cause) {
            System.err.printf("ji> [%5s] %s%n", name(), message);
            if (cause != null) cause.printStackTrace(System.err);
        }
    }
}
