package io.tetrapod.core.logging;

import java.io.*;
import java.time.*;
import java.util.*;

import org.slf4j.*;

import io.tetrapod.core.utils.Util;

public class CommsLogQuery {
   static {
      System.setProperty("devMode", "local");
      Util.setProperty("devMode", "local");
   }
   private static final Logger logger = LoggerFactory.getLogger(CommsLogQuery.class);

   public static void main(String args[]) throws FileNotFoundException, IOException {
      File logDir = new File(args[0]);
      long contextId = 0;// 0x496EED642D2E7936L;//0x03E3125DFD7FF61El;
      long minTime = System.currentTimeMillis() - 1000 * 60 * 60 * 2; // searching last 4 hours
      long maxTime = System.currentTimeMillis();
      LocalDateTime minDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(minTime / 1000), TimeZone.getDefault().toZoneId());
      LocalDateTime maxDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(maxTime / 1000), TimeZone.getDefault().toZoneId());

      logger.info("CommsLogQuery search {} for contextId={} between {} and {}", logDir, contextId, minDateTime, maxDateTime);
      List<File> files = CommsLogger.filesForDateRange(logDir, minTime, maxTime);
      for (File f : files) {
              logger.info("considering FILE = {}", f);
         if (f.exists()) {
            logger.info("READING FILE = {}", f);
            try {
               for (CommsLogEntry e : CommsLogger.readLogFile(f)) {
                  if (e.matches(minTime, maxTime, contextId)) {
                     logger.info("MATCH: {}", e);
                  }
               }
            } catch (Exception e) {
               logger.error("Error Reading {} ", f);
               logger.error(e.getMessage(), e);
            }
         }
      }

   }
}
