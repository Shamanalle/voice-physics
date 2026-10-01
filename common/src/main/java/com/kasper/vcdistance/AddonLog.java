package com.kasper.vcdistance;

import java.util.logging.Level;

/**
 * The addon's log, with {@code {}} placeholders like SLF4J. It writes to SLF4J where the game has it (Minecraft 1.18
 * and newer), to Log4j 2 where it does not (Minecraft 1.16 / 1.17 and the servers of those versions) and to
 * java.util.logging if neither is there.
 */
public final class AddonLog {
    private interface Sink {
        void log(int level, String message, Object[] args);
    }

    private static final int DEBUG = 0;
    private static final int INFO = 1;
    private static final int WARN = 2;
    private static final int ERROR = 3;

    private final Sink sink;

    AddonLog(String name) {
        Sink chosen;
        try {
            Class.forName("org.slf4j.LoggerFactory");
            chosen = Slf4j.of(name);
        } catch (Throwable t) {
            try {
                Class.forName("org.apache.logging.log4j.LogManager");
                chosen = Log4j.of(name);
            } catch (Throwable t2) {
                chosen = Jul.of(name);
            }
        }
        this.sink = chosen;
    }

    public void debug(String message, Object... args) {
        sink.log(DEBUG, message, args);
    }

    public void info(String message, Object... args) {
        sink.log(INFO, message, args);
    }

    public void warn(String message, Object... args) {
        sink.log(WARN, message, args);
    }

    public void error(String message, Object... args) {
        sink.log(ERROR, message, args);
    }

    private static final class Slf4j {
        static Sink of(String name) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(name);
            return (level, message, args) -> {
                switch (level) {
                    case DEBUG -> logger.debug(message, args);
                    case INFO -> logger.info(message, args);
                    case WARN -> logger.warn(message, args);
                    default -> logger.error(message, args);
                }
            };
        }
    }

    private static final class Log4j {
        static Sink of(String name) {
            org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(name);
            return (level, message, args) -> {
                switch (level) {
                    case DEBUG -> logger.debug(message, args);
                    case INFO -> logger.info(message, args);
                    case WARN -> logger.warn(message, args);
                    default -> logger.error(message, args);
                }
            };
        }
    }

    private static final class Jul {
        static Sink of(String name) {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
            return (level, message, args) -> {
                Level l = level == DEBUG ? Level.FINE : level == INFO ? Level.INFO : level == WARN ? Level.WARNING : Level.SEVERE;
                if (logger.isLoggable(l)) {
                    StringBuilder b = new StringBuilder();
                    int next = 0;
                    int from = 0;
                    int at;
                    while ((at = message.indexOf("{}", from)) >= 0 && next < args.length) {
                        b.append(message, from, at).append(args[next++]);
                        from = at + 2;
                    }
                    b.append(message.substring(from));
                    logger.log(l, b.toString());
                }
            };
        }
    }
}
