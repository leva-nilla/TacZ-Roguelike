package com.levanilla.taczstartuphelper;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TaczStartupHelper.MOD_ID)
public class TaczStartupHelper {
    public static final String MOD_ID = "tacz_startup_helper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String PREFIX = "[TacZ Startup Helper]";

    public TaczStartupHelper() {
        if (debugLogs()) {
            LOGGER.info("{} loaded", PREFIX);
        } else {
            LOGGER.debug("{} loaded", PREFIX);
        }
    }

    public static boolean debugLogs() {
        return Boolean.getBoolean("taczStartupHelper.debugLogs");
    }
}
