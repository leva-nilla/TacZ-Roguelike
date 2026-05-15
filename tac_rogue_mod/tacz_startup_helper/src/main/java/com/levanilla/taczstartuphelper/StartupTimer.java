package com.levanilla.taczstartuphelper;

import org.slf4j.Logger;

public final class StartupTimer {
    private static final long WARN_MS = Long.getLong("taczStartupHelper.warnMs", 250L);

    private StartupTimer() {}

    public static long start() {
        return System.nanoTime();
    }

    public static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    public static void log(Logger logger, String label, long startNs, Object detail) {
        long ms = elapsedMs(startNs);
        if (ms >= WARN_MS) {
            logger.info("{} {} took {} ms ({})", TaczStartupHelper.PREFIX, label, ms, detail);
        } else {
            logger.debug("{} {} took {} ms ({})", TaczStartupHelper.PREFIX, label, ms, detail);
        }
    }
}
