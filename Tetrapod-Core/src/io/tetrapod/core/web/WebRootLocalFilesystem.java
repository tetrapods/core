package io.tetrapod.core.web;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * A web root that pull files from the local filesystem
 */
public class WebRootLocalFilesystem implements WebRoot {

   private final List<Path> roots = new ArrayList<>();
   
   @Override
   public void clear() {
      roots.clear();
   }

   @Override
   public void addFile(String path, byte[] content) {
      File f = new File(path);
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
         Path p = rr.resolve(path);
         if (Files.exists(p)) {
            FileResult r = new FileResult();
            r.isIndex = path.endsWith("index.html");
            r.modificationTime = Files.getLastModifiedTime(p).toMillis();
            r.path = "/" + path;
            r.contents = Files.readAllBytes(p);
            return r;
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

}
