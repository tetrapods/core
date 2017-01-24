package io.tetrapod.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.function.Supplier;

/**
 * @author paulm
 *         Created: 1/19/17
 */
public interface DiagnosticCommand {
   static final Logger logger = LoggerFactory.getLogger(DiagnosticCommand.class);

    String threadPrint(String... args);

    DiagnosticCommand local = ((Supplier<DiagnosticCommand>) () -> {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.sun.management",
                "type", "DiagnosticCommand");
            return JMX.newMBeanProxy(server, name, DiagnosticCommand.class);
        } catch(MalformedObjectNameException e) {
            throw new AssertionError(e);
        }
    }).get();

    static void consoleDump() {
       System.out.println("Thread Dump at " + new Date(System.currentTimeMillis()));
       System.out.println(local.threadPrint());
    }

    static void dump() {
        String print = local.threadPrint();
        Path path = Paths.get(LocalDateTime.now() + ".dump.txt");
        try {
            byte[] bytes = print.getBytes("ASCII");
            Files.write(path, bytes);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
