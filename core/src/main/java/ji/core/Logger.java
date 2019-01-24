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

/**
 * A simple logger based on {@link System#err }.
 */
public abstract class Logger {

    public static final Logger LOG = new Logger() {};

    private final Level expect;

    private Logger() {
        final String name = System.getProperty("ji.logging.expect", Level.INFO.name());
        Level l;
        try {
            l = Level.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            Level.WARN.log("Supported expect " + name, null);
            l = Level.INFO;
        }

        expect = l;
    }

    public void debug(String format, Object... args) {
        log(Level.DEBUG, format, args);
    }

    public void info(String format, Object... args) {
        log(Level.INFO, format, args);
    }

    public void warn(String format, Object... args) {
        warn(null, format, args);
    }

    public void warn(Throwable cause, String format, Object... args) {
        log(Level.WARN, cause, format, args);
    }

    private void log(Level actual, String format, Object... args) {
        log(actual, null, format, args);
    }

    private void log(Level actual, Throwable cause, String format, Object... args) {
        if (actual.ordinal() >= expect.ordinal()) {
            actual.log(args.length == 0 ? format : String.format(format, args), cause);
        }
    }

    private enum Level {
        DEBUG, INFO, WARN;

        public void log(String message, Throwable cause) {
            synchronized (System.err) {
                System.err.printf("[JI] %5s - %s%n", name(), message);
                if (cause != null) cause.printStackTrace(System.err);
            }
        }
    }
}
