package io.tetrapod.core.logging;

import java.io.*;
import java.util.LinkedList;

import org.slf4j.*;

import io.netty.buffer.*;
import io.tetrapod.core.Session;
import io.tetrapod.core.rpc.Request;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

/**
 * Buffers and writes binary logs
 */
public class CommsLogWriter {

   private static final Logger       logger           = LoggerFactory.getLogger(CommsLogWriter.class);
   private static final int          LOG_FILE_VERSION = 1;

   public static CommsLogWriter      SINGLETON;

   private DataOutputStream          out;
   private LinkedList<CommsLogEntry> buffer           = new LinkedList<>();
   private volatile boolean          shutdown         = false;

   public CommsLogWriter() throws IOException {
      openLogFile();
      Thread t = new Thread(() -> writerThread(), "CommsLogWriter");
      t.start();
   }

   public void shutdown() {
      shutdown = true;
      try {
         closeLogFile();
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
      }
   }

   @SuppressWarnings("unused")
   private void writerThread() {
      final ByteBuf buf = Unpooled.buffer(1024, 1024 * 1204 * 10);
      final ByteBufDataSource data = new ByteBufDataSource(buf);

      while (!shutdown) {
         while (!buffer.isEmpty()) {
            CommsLogEntry entry = null;
            synchronized (buffer) {
               entry = buffer.poll();
            }
            try {
               buf.clear();
               entry.header.write(data);
               out.write(buf.array(), buf.arrayOffset(), buf.writerIndex());
            } catch (IOException e) {
               logger.error(e.getMessage(), e);
            }
         }
         try {
            out.flush();
         } catch (IOException e) {
            logger.error(e.getMessage(), e);
         }
         if (false) {
            try {
               closeLogFile();
               openLogFile();
               archiveLogFile();
            } catch (IOException e) {
               logger.error(e.getMessage(), e);
            }
         }
         Util.sleep(100);

      }
   }

   private void openLogFile() throws IOException {
      File dir = new File("logs/comms/");
      dir.mkdirs();
      File file = new File(dir, "current.log");
      out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      out.writeInt(LOG_FILE_VERSION);
   }

   private void closeLogFile() throws IOException {
      if (out != null) {
         out.close();
      }
   }

   private void archiveLogFile() throws IOException {
      // rename and gzip/upload
   }

   public void append(CommsLogEntry entry) {
      synchronized (buffer) {
         buffer.add(entry);
      }
   }

   public static void append(Session session, RequestHeader header, Request req) {
      SINGLETON.append(new CommsLogEntry(new CommsLogHeader(System.currentTimeMillis(), LogHeaderType.REQUEST), req));
   }

   public static void init() throws IOException {
      SINGLETON = new CommsLogWriter();
   }

}
