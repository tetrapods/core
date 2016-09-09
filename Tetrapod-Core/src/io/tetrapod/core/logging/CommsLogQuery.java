package io.tetrapod.core.logging;

import java.io.*;
import java.util.List;

import org.slf4j.*;

import io.tetrapod.core.utils.Util;

public class CommsLogQuery {
   static {
      System.setProperty("devMode", "local");
      Util.setProperty("devMode", "local");
   }
   private static final Logger logger = LoggerFactory.getLogger(CommsLogQuery.class);

   public static void main(String args[]) throws FileNotFoundException, IOException {
      File logDir = new File("/Users/adavidson/workspace/tetrapod/core/Tetrapod-Web/logs/comms");
      long contextId = 0x496EED642D2E7936L;//0x03E3125DFD7FF61El;
      logger.info("CommsLogQuery search for contextId={}", contextId);
      long minTime = System.currentTimeMillis() - 1000 * 60 * 60 * 4; // searching last 4 hours
      long maxTime = System.currentTimeMillis();

      List<File> files = CommsLogger.filesForDateRange(logDir, minTime, maxTime);
      for (File f : files) {
         //     logger.info("FILE = {}", f);
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
