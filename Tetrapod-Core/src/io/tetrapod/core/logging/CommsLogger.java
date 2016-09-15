package io.tetrapod.core.logging;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

import org.slf4j.*;

import io.netty.buffer.ByteBuf;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.StructureAdapter;
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

   private static final int          LOG_FILE_VERSION = 1;

   /**
    * Maximum buffer size for un-written log items.
    */
   private static final int          MAX_LOG_BUFFER   = 1204 * 1024;

   private static CommsLogger        SINGLETON;

   private volatile boolean          shutdown         = false;

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
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
            }
         }
         try {
            out.flush();
         } catch (IOException e) {
            logger.error(e.getMessage(), e);
         }
         synchronized (buffer) {
            hasGap = false;
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

      logOpenTime = LocalDateTime.now();
      currentLogFile = new File(dir, String.format("%d-%02d-%02d_%02d.comms", logOpenTime.getYear(), logOpenTime.getMonthValue(),
            logOpenTime.getDayOfMonth(), logOpenTime.getHour()));
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

   /**
    * Re-saves current log file as a compressed file
    */
   private void archiveLogFile() throws IOException {
      final File gzFile = new File(currentLogFile.getParent(), String.format("%d-%02d-%02d_%02d.comms.gz", logOpenTime.getYear(),
            logOpenTime.getMonthValue(), logOpenTime.getDayOfMonth(), logOpenTime.getHour()));
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(currentLogFile)))) {
         @SuppressWarnings("unused")
         int ver = in.readInt();
         IOStreamDataSource dataIn = IOStreamDataSource.forReading(in);
         CommsLogFileHeader header = new CommsLogFileHeader();
         header.read(dataIn);

         try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(gzFile))))) {
            out.writeInt(LOG_FILE_VERSION);
            IOStreamDataSource dataOut = IOStreamDataSource.forWriting(out);
            header.write(dataOut);
            while (true) {
               CommsLogEntry.read(dataIn).write(out);
            }
         } catch (IOException e) {

         } catch (Exception e) {
            logger.error(e.getMessage(), e);
         }
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
         if (commsLog.isDebugEnabled()) {
            final String str = entry.toString();
            commsLog.debug(str);
            if (LOG_TEXT_CONSOLE) {
               logger.debug(str);
            }
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
         SINGLETON.append(new CommsLogEntry(new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.REQUEST, sending,
               session.getSessionType(), session.getSessionNum()), header, req));
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
         SINGLETON.append(new CommsLogEntry(new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.RESPONSE, sending,
               session.getSessionType(), session.getSessionNum()), header, res));
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
            return true;
      }
      return !commsLog.isDebugEnabled();
   }

   public static void shutdown() {
      SINGLETON.doShutdown();
   }

   public static List<CommsLogEntry> readLogFile(File file) throws FileNotFoundException, IOException {
      if (file.getName().endsWith(".gz")) {
         return readCompressedLogFile(file);
      } else {
         return readUncompressedLogFile(file);
      }
   }

   public static List<CommsLogEntry> readCompressedLogFile(File file) throws FileNotFoundException, IOException {
      try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
         return readLogFile(in);
      }
   }

   public static List<CommsLogEntry> readUncompressedLogFile(File file) throws FileNotFoundException, IOException {
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
         return readLogFile(in);
      }
   }

   public static List<CommsLogEntry> readLogFile(DataInputStream in) throws IOException {
      final List<CommsLogEntry> list = new ArrayList<>();
      @SuppressWarnings("unused")
      int ver = in.readInt();
      IOStreamDataSource data = IOStreamDataSource.forReading(in);
      CommsLogFileHeader header = new CommsLogFileHeader();
      header.read(data);
      for (StructDescription def : header.structs) {
         StructureFactory.addIfNew(new StructureAdapter(def));
      }
      while (true) {
         try {
            in.mark(1024);
            CommsLogEntry e = CommsLogEntry.read(data);
            if (e != null) {
               //logger.info("READING {}", e);
               list.add(e);
            }
         } catch (EOFException e) {
            break;
         } catch (IOException e) {
            // possibly a corrupt section of the file, we'll skip a byte and try again until we find something readable...
            logger.error(e.getMessage(), e);
            in.reset();
            in.skipBytes(1);
         }
      }

      return list;
   }

   public static List<File> filesForDateRange(File logDir, long minTime, long maxTime) {
      List<File> files = new ArrayList<>();
      LocalDateTime minDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(minTime / 1000), TimeZone.getDefault().toZoneId());
      LocalDateTime maxDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(maxTime / 1000), TimeZone.getDefault().toZoneId());
      LocalDateTime time = minDateTime;
      while (!time.isAfter(maxDateTime)) {
         File dir = new File(logDir, String.format("%d-%02d-%02d", time.getYear(), time.getMonthValue(), time.getDayOfMonth()));
         File file = new File(dir,
               String.format("%d-%02d-%02d_%02d.comms.gz", time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour()));
         if (file.exists()) {
            files.add(file);
         } else {
            file = new File(dir,
                  String.format("%d-%02d-%02d_%02d.comms", time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour()));
            if (file.exists()) {
               files.add(file);
            }
         }
         time = time.plusHours(1);
      }
      return files;
   }
}
