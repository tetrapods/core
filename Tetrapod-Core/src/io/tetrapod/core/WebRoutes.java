package io.tetrapod.core;

import io.tetrapod.protocol.core.WebRoute;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebRoutes {

   private final Map<String, WebRoute> routes = new ConcurrentHashMap<>();

   public void setRoute(String path, int contractId, int structId) {
      // "/api/LoginRequest  contractId=2, structId=1244334
      WebRoute r = new WebRoute(path, structId, contractId);
      routes.put(path, r);
   }

   public WebRoute findRoute(String uri) {
      int ix = uri.indexOf('?');
      String path = ix < 0 ? uri : uri.substring(0, ix);

      WebRoute r = routes.get(path);
      if (r == null) {
         // special handling for @root paths
         for (WebRoute route : routes.values()) {
            if (path.startsWith(route.path + "/")) {
               return route;
            }
         }
      }

      if (r == null) {
         // allow /route/params instead of /route?params
         ix = uri.lastIndexOf('/');
         path = ix < 0 ? uri : uri.substring(0, ix);
         r = routes.get(path);
      }
      return r;
   }

   public void clear() {
      routes.clear();
   }

   /**
    * Removes all routes for a contract that aren't in the existing set
    */
   public void clear(int contractId, WebRoute[] existing) {
      for (WebRoute r : routes.values()) {
         if (r.contractId == contractId) {
            boolean found = false;
            for (WebRoute e : existing) {
               if (e.path.equals(r.path)) {
                  found = true;
                  continue;
               }
            }
            if (!found) {
               routes.remove(r.path);
            }
         }
      }
   }

}
