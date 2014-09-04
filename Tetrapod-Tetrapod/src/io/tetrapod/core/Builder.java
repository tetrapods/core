package io.tetrapod.core;

import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;

import java.io.*;
import java.util.*;

import org.slf4j.*;

/**
 * Interface to build, deploy, launch system.
 */
public class Builder {

   public static final Logger logger = LoggerFactory.getLogger(Builder.class);

   public static List<BuildInfo> getServiceInfo() {
      List<BuildInfo> list = new ArrayList<>();
      File buildDir = new File(Util.getProperty("build.dir"));
      File clusterDir = new File(Util.getProperty("cluster.dir"));
      boolean canBuild = new File(buildDir, "build").exists();
      boolean canDeploy = new File(clusterDir, "deploy").exists();
      boolean canLaunch = new File(clusterDir, "launch").exists();

      for (String service : getServices(clusterDir)) {
         File serviceDir = new File(clusterDir, service);
         int currentBuild = getCurrentBuild(serviceDir);
         int[] knownBuilds = getKnownBuilds(serviceDir);
         BuildInfo bi = new BuildInfo(service, canBuild, canDeploy, canLaunch, currentBuild, knownBuilds);
         list.add(bi);
      }
      return list;
   }

   private static int[] getKnownBuilds(File serviceDir) {
      List<Integer> builds = new ArrayList<>();
      File[] files = serviceDir.listFiles();
      if (files != null) {
         for (File f : files) {
            if (f.getName().matches("\\d+")) {
               builds.add(Integer.parseInt(f.getName()));
            }
         }
      }
      return Util.toIntArray(builds);
   }

   private static int getCurrentBuild(File serviceDir) {
      try {
         String build = Util.readFileAsString(new File(serviceDir, "current/build_number.txt"));
         return Integer.parseInt(build.trim());
      } catch (IOException e) {
         logger.error("trouble reading build number", e);
         e.printStackTrace();
         return 0;
      }
   }

   private static List<String> getServices(File clusterDir) {
      String parts[] = Util.getProperty("build.exclude", "").split(",");
      Set<String> excludes = new HashSet<>();
      for (String s : parts) {
         if (!s.isEmpty())
            excludes.add(s);
      }
      List<String> services = new ArrayList<>();
      File[] files = clusterDir.listFiles();
      if (files != null) {
         for (File f : files) {
            if (new File(f, "current").exists() && !excludes.contains(f.getName())) {
               services.add(f.getName());
            }
         }
      }
      Collections.sort(services);
      return services;
   }
   
   private static class MyCallback implements Callback<String> {

      public void call(String data) throws Exception {
         // TODO: send BuildCommandProgress message
         logger.info(data);
      }
      
   }

   public static boolean executeCommand(BuildCommand command, TetrapodService tetrapodService) {
      File buildDir = new File(Util.getProperty("build.dir"));
      File clusterDir = new File(Util.getProperty("cluster.dir"));
      boolean canBuild = new File(buildDir, "pullBuild").exists();
      boolean canDeploy = new File(clusterDir, "deploy").exists();
      boolean canLaunch = new File(clusterDir, "launch").exists();

      MyCallback m = new MyCallback();
      try {
         switch (command.command) {
            case BuildCommand.BUILD:
               if (!canBuild)
                  return false;
               return doPullBuild(buildDir, command.build, m);
            case BuildCommand.DEPLOY:
               if (!canDeploy)
                  return false;
               return doDeploy(buildDir, clusterDir, command.serviceName, command.build, m);
            case BuildCommand.LAUNCH:
               if (!canLaunch)
                  return false;
               return doLaunch(clusterDir, command.serviceName, command.build, m);
            case BuildCommand.FULL_CYCLE:
               if (!canLaunch)
                  return false;
               return doFullCycle(clusterDir, command.serviceName, command.build, m);
         }
      } catch (IOException e) {
         logger.error("failed build command", e);
         return false;
      }
      return true;
   }

   private static boolean doPullBuild(File buildDir, int build, MyCallback callback) throws IOException {
      if (build == BuildCommand.DEPLOY_LATEST) {
         // force use of real build numbers
         return false;
      }
      String buildNum = "" + build; 
      int rc = Util.runProcess(callback, new File(buildDir, "pullBuild").getPath(), buildNum);
      return rc == 0;
   }

   private static boolean doDeploy(File buildDir, File clusterDir, String serviceName, int build, MyCallback callback) throws IOException  {
      if (build == BuildCommand.DEPLOY_LATEST) {
         // force use of real build numbers for deploy
         return false;
      }
      String buildNum = "" + build; 
      int rc = Util.runProcess(callback, new File(clusterDir, "deploy").getPath(), buildNum, serviceName);
      return rc == 0;
   }

   private static boolean doLaunch(File clusterDir, String serviceName, int build, MyCallback callback) throws IOException {
      String buildNum = build == BuildCommand.LAUNCH_DEPLOYED ? "current" : "" + build; 
      int rc = Util.runProcess(callback, new File(clusterDir, "launch").getPath(), buildNum, serviceName);
      return rc == 0;
   }

   private static boolean doFullCycle(File clusterDir, String serviceName, int build, MyCallback callback) throws IOException {
      String buildNum = build == BuildCommand.LAUNCH_DEPLOYED ? "current" : "" + build; 
      int rc = Util.runProcess(callback, new File(clusterDir, "fullCycle").getPath(), buildNum, serviceName);
      return rc == 0;
   }

}
