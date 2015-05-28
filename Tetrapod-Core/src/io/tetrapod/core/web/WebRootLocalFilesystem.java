package io.tetrapod.core.web;

import io.tetrapod.core.utils.Util;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.*;

/**
 * A web root that pull files from the local file system
 */
public class WebRootLocalFilesystem implements WebRoot {

   public static final Logger logger   = LoggerFactory.getLogger(WebRootLocalFilesystem.class);

   private final List<Path>   roots    = new ArrayList<>();

   private String             rootPath = "/";

   public WebRootLocalFilesystem() {}

   public WebRootLocalFilesystem(String path, URL url) throws IOException {
      this.rootPath = path;
      final File cacheDir = new File(Util.getProperty("tetrapod.cache", "cache"));
      final String name = digest(url.toString());
      final File webDir = new File(cacheDir, name);
      final File zipFile = new File(cacheDir, name + ".zip");
      webDir.mkdirs();
      if (!zipFile.exists()) {
         try {
            logger.info("Downloading {} to {}", url, zipFile);
            Util.downloadFile(url, zipFile);
            logger.info("Extracting to {}", webDir);
            unzip(zipFile, webDir);
         } catch (Exception e) {
            zipFile.delete();
            throw e;
         }
      } 

      addFile(webDir.getAbsolutePath(), null);
   }

   private void unzip(File zipFile, File webDir) throws IOException {
      Runtime.getRuntime().exec(String.format("unzip %s -d %s", zipFile.getAbsolutePath(), webDir.getAbsolutePath()));
   }

   public WebRootLocalFilesystem(String path, File dir) {
      this.rootPath = path;
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
      if (path.startsWith(rootPath)) {
         path = path.substring(rootPath.length());
      } else {
         return null;
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

}
