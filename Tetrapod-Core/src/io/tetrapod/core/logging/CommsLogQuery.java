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
      long contextId = 0x58BBA6D520BED86Al;
      logger.info("CommsLogQuery search for contextId={}", contextId);
      long minTime = System.currentTimeMillis() - 1000 * 60 * 60 * 30;
      long maxTime = System.currentTimeMillis();

      List<File> files = CommsLogger.filesForDateRange(logDir, minTime, maxTime);
      for (File f : files) {
         logger.info("FILE = {}", f);
         if (f.exists()) {
            logger.info("READING FILE = {}", f);
            for (CommsLogEntry e : CommsLogger.readLogFile(f)) {
               if (e.matches(minTime, maxTime, contextId)) {
                  logger.info("{}", e.description());
               }
            }
         }
      }

   }
}
