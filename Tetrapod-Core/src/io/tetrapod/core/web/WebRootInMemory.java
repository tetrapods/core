package io.tetrapod.core.web;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A collection of files to serve over the web.  This implementation is all in
 * memory but if we had a large web root to serve we could make it save files
 * to/from the local filesystem.
 */
public class WebRootInMemory implements WebRoot {
   
   private Map<String, byte[]> files = new ConcurrentHashMap<>();
   private volatile long modificationTime = System.currentTimeMillis();
   private AtomicInteger size = new AtomicInteger(0);
   
   public void clear() {
      this.modificationTime = System.currentTimeMillis();
      this.files.clear();
      this.size.set(0);
   }
   
   public void addFile(String path, byte[] content) {
      files.put(path,  content);
      size.addAndGet(content.length);
   }
   
   public FileResult getFile(String path) {
      if (path.endsWith("/")) {
         path += "index.html";
      }
      byte[] content = files.get(path);

      // a bit weird but if we don't have the file in memory lets just make extra special sure the user didn't forget a trailing slash
      if (content == null) {
         content = files.get(path+"/index.html");
      }

      if (content != null) {
         FileResult r = new FileResult();
         r.contents = content;
         r.modificationTime = this.modificationTime;
         r.path = path;
         r.isIndex = path.endsWith("index.html");
         return r;
      }
      return null;
   }
   
   public long getModificationTime() {
      return modificationTime;
   }

   public Collection<String> getAllPaths() {
      return files.keySet();
   }
   
   public int getMemoryFootprint() {
      return size.get();
   }
   
}
