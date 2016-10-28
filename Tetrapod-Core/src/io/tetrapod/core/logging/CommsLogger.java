package io.tetrapod.core.logging;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.slf4j.*;

import io.netty.buffer.ByteBuf;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.raft.AppendEntriesRequest;

/**
 * Buffers and writes binary logs
 */
public class CommsLogger {

   private static final Logger       logger           = LoggerFactory.getLogger(CommsLogger.class);
   private static final Logger       commsLog         = LoggerFactory.getLogger("comms");

   /**
    * Disable / enable all of comms logging
    */
   private static boolean            ENABLED          = true;

   /**
    * For fast local debugging set to true and see all comms logs in the console
    */
   private static boolean            LOG_TEXT_CONSOLE = false;

   private static boolean            LOG_TEXT_FILE    = true;

   private static boolean            LOG_BINARY       = false;

   private static final int          LOG_FILE_VERSION = 1;

   /**
    * Maximum buffer size for un-written log items.
    */
   private static final int          MAX_LOG_BUFFER   = 1204 * 1024;

   private static CommsLogger        SINGLETON;

   private boolean                   hasGap           = false;

   private DefaultService            service;

   /**
    * Buffer of unwritten log items
    */
   private LinkedList<CommsLogEntry> buffer           = new LinkedList<>();

   // current log file details
   private LocalDateTime             logOpenTime;
   private File                      currentLogFile;
   private DataOutputStream          out;

   public CommsLogger(DefaultService service) throws IOException {
      this.service = service;
      Thread t = new Thread(() -> writerThread(), "CommsLogWriter");
      t.start();
   }

   public void doShutdown() {
      try {
         closeLogFile();
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
      }
   }

   private void writerThread() {
      while (!service.isShuttingDown()) {
         // starts a new log file every hour
         final LocalDateTime time = LocalDateTime.now();
         if (service.getEntityId() != 0) {

            LOG_TEXT_FILE = Util.getProperty("tetrapod.logs.file", true);
            LOG_TEXT_CONSOLE = Util.getProperty("tetrapod.logs.console", false);
            LOG_BINARY = Util.getProperty("tetrapod.logs.binary", false);

            if (LOG_BINARY) {
               if (logOpenTime == null || time.getHour() != logOpenTime.getHour() || time.getDayOfYear() != logOpenTime.getDayOfYear()) {
                  try {
                     openLogFile();
                  } catch (IOException e) {
                     logger.error(e.getMessage(), e);
                  }
               }
            }
            while (!buffer.isEmpty()) {
               CommsLogEntry entry = null;
               synchronized (buffer) {
                  entry = buffer.poll();
               }
               if (LOG_BINARY) {
                  try {
                     entry.write(out);
                  } catch (Exception e) {
                     logger.error(e.getMessage(), e);
                  }
               }
               if (LOG_TEXT_FILE) {
                  commsLog.info("{}", entry);
               }
            }
            try {
               if (out != null)
                  out.flush();
            } catch (IOException e) {
               logger.error(e.getMessage(), e);
            }
            synchronized (buffer) {
               hasGap = false;
            }
         }
         Util.sleep(100);
      }
      doShutdown();
   }

   private void openLogFile() throws IOException {
      closeLogFile();
      //      if (currentLogFile != null) {
      //         archiveLogFile();
      //      }
      File logs = new File(Util.getProperty("tetrapod.logs", "logs"), Util.getProperty("APPNAME"));
      LocalDate date = LocalDate.now();
      File dir = new File(logs, String.format("%d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
      dir.mkdirs();

      logOpenTime = LocalDateTime.now();
      currentLogFile = new File(dir, String.format("%d_%d-%02d-%02d_%02d.comms", service.getEntityId(), logOpenTime.getYear(),
            logOpenTime.getMonthValue(), logOpenTime.getDayOfMonth(), logOpenTime.getHour()));
      out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(currentLogFile, true)));

      CommsLogFileHeader header = new CommsLogFileHeader(StructureFactory.getAllKnownStructures(), service.getShortName(),
            service.getEntityId(), service.buildName, Util.getHostName());
      out.writeInt(LOG_FILE_VERSION);
      IOStreamDataSource data = IOStreamDataSource.forWriting(out);
      header.write(data);
   }

   private void closeLogFile() throws IOException {
      if (out != null) {
         out.close();
      }
   }

   private void append(CommsLogEntry entry) {
      try {
         synchronized (buffer) {
            if (buffer.size() < MAX_LOG_BUFFER) {
               buffer.add(entry);
            } else if (!hasGap) {
               hasGap = true;
               logger.warn("CommsLog buffer is full. Dropping items!");
            }
         }

         if (LOG_TEXT_CONSOLE) {
            logger.debug("{}", entry);
         }
      } catch (Throwable t) {
         logger.error(t.getMessage(), t);
      }
   }

   public static void append(Session session, boolean sending, MessageHeader header, ByteBuf in) {
      if (ENABLED && !commsLogIgnore(header.structId)) {
         byte[] data = new byte[in.readableBytes()];
         in.getBytes(in.readerIndex(), data);
         SINGLETON.append(new CommsLogEntry(new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.MESSAGE, sending,
               session.getSessionType(), session.getSessionNum()), header, data));
      }
   }

   public static void append(Session session, boolean sending, MessageHeader header, Message msg) {
      if (ENABLED && !commsLogIgnore(header.structId)) {
         SINGLETON.append(new CommsLogEntry(new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.MESSAGE, sending,
               session.getSessionType(), session.getSessionNum()), header, msg));
      }
   }

   public static boolean append(Session session, boolean sending, RequestHeader header, ByteBuf in) {
      if (ENABLED && !commsLogIgnore(header.structId)) {
         byte[] data = new byte[in.readableBytes()];
         in.getBytes(in.readerIndex(), data);
         SINGLETON.append(new CommsLogEntry(new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.REQUEST, sending,
               session.getSessionType(), session.getSessionNum()), header, data));
         return true;
      }
      return false;
   }

   public static boolean append(Session session, boolean sending, RequestHeader header, Structure req) {
      if (ENABLED && !commsLogIgnore(header.structId)) {

         SINGLETON.append(new CommsLogEntry(
               new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.REQUEST, sending,
                     session == null ? SessionType.NONE : session.getSessionType(), session == null ? 0 : session.getSessionNum()),
               header, req));
         return true;
      }
      return false;
   }

   public static boolean append(Session session, boolean sending, ResponseHeader header, ByteBuf in, int requestStructId) {
      if (ENABLED && !commsLogIgnore(header.structId) && !commsLogIgnore(requestStructId)) {
         byte[] data = new byte[in.readableBytes()];
         in.getBytes(in.readerIndex(), data);
         SINGLETON.append(new CommsLogEntry(new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.RESPONSE, sending,
               session.getSessionType(), session.getSessionNum()), header, data));
         return true;
      }
      return false;
   }

   public static boolean append(Session session, boolean sending, ResponseHeader header, Structure res, int requestStructId) {
      if (ENABLED && !commsLogIgnore(header.structId) && !commsLogIgnore(requestStructId)) {
         SINGLETON.append(new CommsLogEntry(
               new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.RESPONSE, sending,
                     session == null ? SessionType.NONE : session.getSessionType(), session == null ? 0 : session.getSessionNum()),
               header, res));
         return true;
      }
      return false;
   }

   public static void init(DefaultService service) throws IOException {
      SINGLETON = new CommsLogger(service);
   }

   public static boolean commsLog(Session ses, String format, Object... args) {
      if (commsLog.isDebugEnabled()) {
         final String str = String.format("%s:%d ", ses.getClass().getSimpleName().substring(0, 4), ses.getSessionNum())
               + String.format(format, args);
         commsLog.debug(str);
         if (LOG_TEXT_CONSOLE) {
            logger.debug(str);
         }
      }
      return true;
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
         case RetainOwnershipRequest.STRUCT_ID:
         case RetainOwnershipMessage.STRUCT_ID:
            return true;
      }
      return !commsLog.isInfoEnabled();
   }

   public static CommsLogFile readLogFile(File file) throws FileNotFoundException, IOException {
      if (file.getName().endsWith(".gz")) {
         return readCompressedLogFile(file);
      } else {
         return readUncompressedLogFile(file);
      }
   }

   public static CommsLogFile readCompressedLogFile(File file) throws FileNotFoundException, IOException {
      try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
         return new CommsLogFile(in);
      }
   }

   public static CommsLogFile readUncompressedLogFile(File file) throws FileNotFoundException, IOException {
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
         return new CommsLogFile(in);
      }
   }

   private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");

   public static List<File> filesForDateRange(File logDir, LocalDateTime minDateTime, LocalDateTime maxDateTime) {
      final List<File> files = new ArrayList<>();

      // look in directory for each day in our range
      LocalDateTime time = minDateTime;
      while (!time.isAfter(maxDateTime)) {
         try {
            File dir = new File(logDir, String.format("%d-%02d-%02d", time.getYear(), time.getMonthValue(), time.getDayOfMonth()));
            if (dir.exists()) {
               // look at all files in dir
               for (File f : dir.listFiles()) {
                  if (f.isFile() && (f.getName().endsWith(".comms") || f.getName().endsWith(".comms.gz"))) {
                     String parts[] = f.getName().split("_");
                     // if the hours put it in range of our query, add it
                     LocalDateTime t = LocalDateTime.parse(parts[1] + " " + parts[2].substring(0, 2), formatter);
                     if (!t.isAfter(maxDateTime) && !t.isBefore(minDateTime)) {
                        files.add(f);
                     }
                  }
               }
            }
         } finally {
            time = time.plusDays(1);
         }
      }

      return files;
   }
}
