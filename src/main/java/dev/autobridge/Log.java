package dev.autobridge;

/** 统一日志前缀，方便在 logs 里搜 {@code [AutoBridge]}。 */
public final class Log {

    private static final System.Logger LOGGER = System.getLogger("AutoBridge");

    private Log() {
    }

    public static void info(String message) {
        LOGGER.log(System.Logger.Level.INFO, "[AutoBridge] " + message);
    }

    public static void warn(String message) {
        LOGGER.log(System.Logger.Level.WARNING, "[AutoBridge] " + message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.log(System.Logger.Level.ERROR, "[AutoBridge] " + message, throwable);
    }
}
