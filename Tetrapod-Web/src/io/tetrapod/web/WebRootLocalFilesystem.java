package io.tetrapod.web;

import io.tetrapod.core.utils.Util;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

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
      final String name = Util.digest(url.toString());
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

   private void unzip(File source, File destination) throws IOException {
      try (FileInputStream fis = new FileInputStream(source)) {
         try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
               File file = new File(destination, entry.getName());
               if (entry.isDirectory()) {
                  file.mkdir();
               } else {
                  byte[] buffer = new byte[2048];
                  try (FileOutputStream fos = new FileOutputStream(file)) {
                     try (BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length)) {
                        int size;
                        while ((size = zis.read(buffer, 0, buffer.length)) != -1) {
                           bos.write(buffer, 0, size);
                        }
                        bos.flush();
                     }
                  }
               }
            }
         }
      } catch (IOException e) {
         throw new IOException(e);
      }
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
                  r.modificationTime = Files.getLastModifiedTime(p).toMillis();
                  r.path = "/" + path;
                  r.doNotCache = !r.path.startsWith("/vbf");
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
