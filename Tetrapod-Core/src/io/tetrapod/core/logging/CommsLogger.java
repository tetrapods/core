package io.tetrapod.core.logging;

import java.io.*;
import java.time.*;
import java.util.LinkedList;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;
import org.slf4j.*;

import io.tetrapod.core.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.raft.AppendEntriesRequest;

/**
 * Buffers and writes binary logs
 */
public class CommsLogger {

   private static final Logger       logger           = LoggerFactory.getLogger(CommsLogger.class);
   private static final Logger       commsLog         = LoggerFactory.getLogger("comms");

   private static final int          LOG_FILE_VERSION = 1;

   private static CommsLogger        SINGLETON;

   private DataOutputStream          out;
   private LinkedList<CommsLogEntry> buffer           = new LinkedList<>();
   private volatile boolean          shutdown         = false;
   private LocalDateTime             logOpenTime;
   private File                      currentLogFile;

   public CommsLogger() throws IOException {
      Thread t = new Thread(() -> writerThread(), "CommsLogWriter");
      t.start();
   }

   public void doShutdown() {
      shutdown = true;
      try {
         closeLogFile();
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
      }
   }

   private void writerThread() {
      while (!shutdown) {
         // starts a new log file every hour
         final LocalDateTime time = LocalDateTime.now();
         if (logOpenTime == null || time.getHour() != logOpenTime.getHour() || time.getDayOfYear() != logOpenTime.getDayOfYear()) {
            try {
               openLogFile();
            } catch (IOException e) {
               logger.error(e.getMessage(), e);
            }
         }

         while (!buffer.isEmpty()) {
            CommsLogEntry entry = null;
            synchronized (buffer) {
               entry = buffer.poll();
            }
            try {
               entry.write(out);
            } catch (IOException e) {
               logger.error(e.getMessage(), e);
            }
         }
         try {
            out.flush();
         } catch (IOException e) {
            logger.error(e.getMessage(), e);
         }

         Util.sleep(100);
      }
   }

   private void openLogFile() throws IOException {
      closeLogFile();
      if (currentLogFile != null) {
         archiveLogFile();
      }
      File logs = new File(Util.getProperty("tetrapod.logs.comms", "logs/comms/"));
      LocalDate date = LocalDate.now();
      File dir = new File(logs, String.format("%d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
      dir.mkdirs();
      currentLogFile = new File(dir, "current.log");
      out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(currentLogFile, false)));
      out.writeInt(LOG_FILE_VERSION);
      logOpenTime = LocalDateTime.now();
   }

   private void closeLogFile() throws IOException {
      if (out != null) {
         out.close();
      }
   }

   private void archiveLogFile() throws IOException {
      // rename and gzip/upload
      final File file = new File(currentLogFile.getParent(), String.format("%d-%02d-%02d_%02d.comms", logOpenTime.getYear(),
            logOpenTime.getMonthValue(), logOpenTime.getDayOfMonth(), logOpenTime.getHour()));
      currentLogFile.renameTo(file);
      final File gzFile = new File(currentLogFile.getParent(), String.format("%d-%02d-%02d_%02d.comms.gz", logOpenTime.getYear(),
            logOpenTime.getMonthValue(), logOpenTime.getDayOfMonth(), logOpenTime.getHour()));
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
         @SuppressWarnings("unused")
         int ver = in.readInt();
         try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(gzFile))))) {
            out.writeInt(LOG_FILE_VERSION);
            while (true) {
               CommsLogEntry.read(in).write(out);
            }
         } catch (IOException e) {}
      }

   }

   public void append(CommsLogEntry entry) {
      synchronized (buffer) {
         buffer.add(entry);
      }
   }

   public static void append(Session session, RequestHeader header, Request req) {
      StructDescription def = req.makeDescription(); // FIXME
      SINGLETON.append(new CommsLogEntry(new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.REQUEST, def), header, req));
   }

   public static void init() throws IOException {
      SINGLETON = new CommsLogger();
   }

   public static boolean commsLog(Session ses, String format, Object... args) {
      if (commsLog.isDebugEnabled()) {
         for (int i = 0; i < args.length; i++) {
            if (args[i] == ses) {
               args[i] = String.format("%s:%d", ses.getClass().getSimpleName().substring(0, 4), ses.getSessionNum());
            }
         }
         commsLog.debug(String.format(format, args));
         //logger.debug(String.format(format, args));
      }
      return true;
   }

   public String getNameFor(MessageHeader header) {
      return StructureFactory.getName(header.contractId, header.structId);
   }

   public static boolean commsLogIgnore(Structure struct) {
      return commsLogIgnore(struct.getStructId());
   }

   public static boolean commsLogIgnore(int structId) {
      if (commsLog.isTraceEnabled())
         return false;
      switch (structId) {
         case ServiceLogsRequest.STRUCT_ID:
         case ServiceStatsMessage.STRUCT_ID:
         case DummyRequest.STRUCT_ID:
         case AppendEntriesRequest.STRUCT_ID:
         case RaftStatsRequest.STRUCT_ID:
         case RaftStatsResponse.STRUCT_ID:
            return true;
      }
      return !commsLog.isDebugEnabled();
   }

   public static void shutdown() {
      SINGLETON.doShutdown();
   }

   @Test
   public void test() throws FileNotFoundException, IOException {
      File file = new File("/Users/adavidson/workspace/tetrapod/core/Tetrapod-Tetrapod/logs/comms/2016-08-31/current.log");
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
         @SuppressWarnings("unused")
         int ver = in.readInt();
         while (true) {
            CommsLogEntry e = CommsLogEntry.read(in);
            System.out.println(
                  e.header.timestamp + " " + e.header.type + " " + e.header.def.name + " : " + e.struct.dump() + "\n\t" + e.payload.dump());
         }
      } catch (IOException e) {}
   }
}
