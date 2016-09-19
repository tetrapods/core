package io.tetrapod.core.logging;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.*;

import io.tetrapod.core.utils.Util;

public class CommsLogQuery {
   static {
      System.setProperty("devMode", "local");
      Util.setProperty("devMode", "local");
   }

   private static final Logger            logger    = LoggerFactory.getLogger(CommsLogQuery.class);
   private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

   public static void main(String args[]) throws FileNotFoundException, IOException {
      if (args.length == 0) {
         logger.info("USAGE: [-dir /service_logs/] [-c F88A4D122757541F] [-min '2016-09-21 16:00'] [-max '2016-09-21 22:00'] [-last 2]");
         logger.info("Default time range is last 2 hours");
      }

      File logDir = new File("logs/comms/");

      long contextId = 0;
      long maxTime = System.currentTimeMillis();
      long minTime = System.currentTimeMillis() - 1000 * 60 * 60 * 2;
      LocalDateTime minDateTime = null;
      LocalDateTime maxDateTime = null;

      int i = 1;
      while (i < args.length) {
         switch (args[i]) {
            case "-dir":
               logDir = new File(args[++i]);
               break;
            case "-c":
               contextId = Long.parseLong(args[++i], 16);
               break;
            case "-last":
               minTime = System.currentTimeMillis() - 1000 * 60 * 60 * Integer.parseInt(args[++i]);
               break;
            case "-min":
               minDateTime = LocalDateTime.parse(args[++i], formatter);
               break;
            case "-max":
               maxDateTime = LocalDateTime.parse(args[++i], formatter);
               break;
         }
         i++;
      }
      if (minDateTime == null) {
         minDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(minTime / 1000), TimeZone.getDefault().toZoneId());
      }
      if (maxDateTime == null) {
         maxDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(maxTime / 1000), TimeZone.getDefault().toZoneId());
      }

      logger.info("CommsLogQuery search {} for contextId={} between {} and {}", logDir, contextId, minDateTime, maxDateTime);

      for (File dir : logDir.listFiles()) {
         if (dir.isDirectory()) {
            final List<File> files = CommsLogger.filesForDateRange(dir, minDateTime, maxDateTime);
            for (File f : files) {
               if (f.exists()) {
                  logger.info("READING FILE = {}", f);
                  try {
                     CommsLogFile file = CommsLogger.readLogFile(f);
                     for (CommsLogEntry e : file.list) {
                        if (e.matches(minTime, maxTime, contextId)) {
                           logger.info("{} {} {}", file.header.host, file.header.serviceName, e);
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

   }
}
