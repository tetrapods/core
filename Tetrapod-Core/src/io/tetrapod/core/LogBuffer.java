package io.tetrapod.core;

import io.tetrapod.protocol.core.ServiceLogEntry;

import java.util.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.AppenderBase;

public class LogBuffer extends AppenderBase<ILoggingEvent> {
   private static final int                MAX_ITEMS = 1000;

   private final LinkedList<ILoggingEvent> items     = new LinkedList<>();
   private long                            count     = 0;

   @Override
   protected synchronized void append(ILoggingEvent e) {
      count++;
      items.addLast(e);
      if (items.size() > MAX_ITEMS) {
         items.removeFirst();
      }
   }

   public synchronized long getItems(long logId, Level level, int maxItems, List<ServiceLogEntry> list) {
      final Iterator<ILoggingEvent> iter = items.descendingIterator();
      int n = 0;
      while (iter.hasNext()) {
         ILoggingEvent item = iter.next();
         if (count - n++ <= logId)
            break;
         if (item.getLevel().isGreaterOrEqual(level)) {
            list.add(logEntryFrom(item));
            if (list.size() >= maxItems) {
               break;
            }
         }
      }
      Collections.reverse(list);
      return count;
   }

   private ServiceLogEntry logEntryFrom(ILoggingEvent e) {
      String msg = e.getFormattedMessage();
      if (e.getThrowableProxy() != null) {
         StringBuilder sb = new StringBuilder();

         sb.append('\n');
         logStack(sb, e.getThrowableProxy());
         msg = sb.toString();
      }
      return new ServiceLogEntry(msg, convert(e.getLevel()), e.getTimeStamp(), e.getThreadName(), e.getLoggerName());
   }

   private void logStack(StringBuilder sb, IThrowableProxy proxy) {
      sb.append(proxy.getClassName());
      sb.append(": ");
      sb.append(proxy.getMessage());
      sb.append('\n');
      for (StackTraceElementProxy st : proxy.getStackTraceElementProxyArray()) {
         sb.append('\t');
         sb.append(st.getSTEAsString());
         sb.append('\n');
      }
      if (proxy.getCause() != null) {
         sb.append("Caused by: ");
         logStack(sb, proxy.getCause());
      }
   }

   public Level convert(byte level) {
      switch (level) {
         case ServiceLogEntry.LEVEL_ALL:
            return Level.ALL;
         case ServiceLogEntry.LEVEL_TRACE:
            return Level.TRACE;
         case ServiceLogEntry.LEVEL_DEBUG:
            return Level.DEBUG;
         case ServiceLogEntry.LEVEL_INFO:
            return Level.INFO;
         case ServiceLogEntry.LEVEL_WARN:
            return Level.WARN;
         case ServiceLogEntry.LEVEL_ERROR:
            return Level.ERROR;
         case ServiceLogEntry.LEVEL_OFF:
            return Level.OFF;
      }
      return null;
   }

   public byte convert(Level level) {
      switch (level.levelInt) {
         case Level.ALL_INT:
            return ServiceLogEntry.LEVEL_ALL;
         case Level.TRACE_INT:
            return ServiceLogEntry.LEVEL_TRACE;
         case Level.DEBUG_INT:
            return ServiceLogEntry.LEVEL_DEBUG;
         case Level.INFO_INT:
            return ServiceLogEntry.LEVEL_INFO;
         case Level.WARN_INT:
            return ServiceLogEntry.LEVEL_WARN;
         case Level.ERROR_INT:
            return ServiceLogEntry.LEVEL_ERROR;
         case Level.OFF_INT:
            return ServiceLogEntry.LEVEL_OFF;
      }
      return 0;
   }
}
