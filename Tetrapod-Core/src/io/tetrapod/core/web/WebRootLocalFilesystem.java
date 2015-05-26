package io.tetrapod.core.web;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.slf4j.*;

/**
 * A web root that pull files from the local filesystem
 */
public class WebRootLocalFilesystem implements WebRoot {

   public static final Logger logger = LoggerFactory.getLogger(WebRootLocalFilesystem.class);

   private final List<Path>   roots  = new ArrayList<>();

   public WebRootLocalFilesystem() {}

   public WebRootLocalFilesystem(File dir) {
      addFile(dir.getAbsolutePath(), null);
   }

   @Override
   public synchronized void clear() {
      roots.clear();
   }

   @Override
   public synchronized void addFile(String path, byte[] content) {
      assert path != null;
      File f = new File(path);
      assert f != null;
      assert f.toPath() != null;
      roots.add(f.toPath());
      logger.info("Added web root {}", path);
   }

   @Override
   public FileResult getFile(String path) throws IOException {
      if (path.endsWith("/")) {
         path += "index.html";
      }
      if (path.startsWith("/")) {
         path = path.substring(1);
      }

      int ix = path.lastIndexOf(".");
      String ext = ix < 0 ? "" : path.substring(ix + 1).toLowerCase();
      if (VALID_EXTENSIONS.contains(ext)) {
         for (Path rr : roots) {
            if (rr != null) {
               Path p = rr.resolve(path);
               if (Files.exists(p)) {
                  FileResult r = new FileResult();
                  r.doNotCache = path.endsWith(".html");
                  r.modificationTime = Files.getLastModifiedTime(p).toMillis();
                  r.path = "/" + path;
                  if (Files.isDirectory(p)) {
                     r.isDirectory = true;
                  } else {
                     r.contents = Files.readAllBytes(p);
                  }
                  return r;
               }
            }
         }
      }
      return null;
   }

   //   private void addAllFiles(File dir, File root) {
   //      for (File f : dir.listFiles()) {
   //         if (f.isDirectory()) {
   //            addAllFiles(f, dir);
   //         } else {
   //            int ix = f.getName().lastIndexOf(".");
   //            String ext = ix < 0 ? "" : f.getName().substring(ix + 1).toLowerCase();
   //            if (VALID_EXTENSIONS.contains(ext)) {
   //               String path = "/" + root.toPath().relativize(f.toPath()).toString();
   //               //addFile(path, null);
   //            }
   //         }
   //      } 
   //   }

}
