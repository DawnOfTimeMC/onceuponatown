package com.dotteam.onceuponatown.util;

import com.dotteam.onceuponatown.OuatConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OuatLog {
    public static final Logger LOG = LogManager.getLogger(OuatConstants.MOD_ID);

    public static void info(String info) {
        LOG.info(info);
    }

    public static void debug(String debug) {
        LOG.debug(debug);
    }

    public static void error(String error) {
        LOG.error(error);
    }
}
