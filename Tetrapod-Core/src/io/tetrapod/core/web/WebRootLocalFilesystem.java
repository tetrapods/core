package io.tetrapod.core.web;

import io.tetrapod.core.rpc.Async;
import io.tetrapod.protocol.core.AddWebFileRequest;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * A web root that pull files from the local filesystem
 */
public class WebRootLocalFilesystem implements WebRoot {

   private final List<Path> roots = new ArrayList<>();

   public WebRootLocalFilesystem() {}

   public WebRootLocalFilesystem(File dir) {
      addAllFiles(dir);
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
   }

   @Override
   public FileResult getFile(String path) throws IOException {
      if (path.endsWith("/")) {
         path += "index.html";
      }
      if (path.startsWith("/")) {
         path = path.substring(1);
      }
      for (Path rr : roots) {
         if (rr != null) {
            Path p = rr.resolve(path);
            if (Files.exists(p)) {
               // OPTIMIZE: Add an in memory cache
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
      return null;
   }

   @Override
   public Collection<String> getAllPaths() {
      // TODO implement
      return null;
   }

   @Override
   public int getMemoryFootprint() {
      return 0;
   }

   private void addAllFiles(File dir) {
      for (File f : dir.listFiles()) {
         if (f.isDirectory()) {
            addAllFiles(f);
         } else {
            int ix = f.getName().lastIndexOf(".");
            String ext = ix < 0 ? "" : f.getName().substring(ix + 1).toLowerCase();
            if (VALID_EXTENSIONS.contains(ext)) {
               String path = "/" + dir.toPath().relativize(f.toPath()).toString();
               addFile(path, null);
            }
         }
      }

   }

}
