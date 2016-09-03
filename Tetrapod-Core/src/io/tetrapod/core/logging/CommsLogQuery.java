package io.tetrapod.core.logging;

import java.io.File;
import java.util.List;

import org.slf4j.*;

import io.tetrapod.core.utils.Util;

public class CommsLogQuery {
   static {
      System.setProperty("devMode", "local");
      Util.setProperty("devMode", "local");
   }
   private static final Logger logger = LoggerFactory.getLogger(CommsLogQuery.class);

   public static void main(String args[]) {

      logger.info("CommsLogQuery");
      long minTime = System.currentTimeMillis() - 1000 * 60 * 60 * 30;
      long maxTime = System.currentTimeMillis();

      List<File> files = CommsLogger.filesForDateRange(minTime, maxTime);
      for (File f : files) {
         logger.info("FILE = {}", f);
      }

   }
}
