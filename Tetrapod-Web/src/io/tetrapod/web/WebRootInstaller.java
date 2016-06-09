package io.tetrapod.web;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.WebRootDef;

public class WebRootInstaller {
   private static final Logger           logger                    = LoggerFactory.getLogger(WebRootInstaller.class);

   private final Executor                webRootSequentialExecutor = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS,
         new LinkedBlockingQueue<>());

   private final Map<String, WebRoot>    contentRootMap            = new HashMap<>();
   private final Map<String, WebRootDef> pending                   = new HashMap<>();

   public void install(final WebRootDef def) {
      synchronized (pending) {
         pending.put(def.name, def);
      }
      webRootSequentialExecutor.execute(() -> doInstall(def));
   }

   public void uninstall(final String name) {
      synchronized (pending) {
         pending.remove(name);
      }
      webRootSequentialExecutor.execute(() -> doUninstall(name));
   }

   private void doInstall(WebRootDef def) {
      try {
         synchronized (pending) {
            if (pending.get(def.name) != def) {
               // a later add or remove happened on my name, skip this install
               return;
            }
            pending.remove(def.name);
         }
         if (!Util.isEmpty(def.file) && !Util.isEmpty(def.path)) {
            WebRoot wr = null;
            logger.debug("Installing WebRoot {} from {} on path {}", def.name, def.file, def.path);
            if (def.file.startsWith("http")) {
               wr = new WebRootLocalFilesystem(def.path, new URL(def.file));
            } else {
               wr = new WebRootLocalFilesystem(def.path, new File(def.file));
            }
            contentRootMap.put(def.name, wr);
         }
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
      }
   }

   private void doUninstall(String name) {
      logger.debug("Uninstalling WebRoot {} ", name);
      contentRootMap.remove(name);
   }

   public Map<String, WebRoot> getWebRoots() {
      return contentRootMap;
   }

}
