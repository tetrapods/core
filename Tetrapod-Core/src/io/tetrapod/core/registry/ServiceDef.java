package io.tetrapod.core.registry;

import java.util.*;

public class ServiceDef {
   /**
    * Fully qualified service name
    */
   public final String             name;

   /**
    * An icon url for internal admin use, may be null
    */
   public final String             iconURL;

   /**
    * A locally unique serviceId for more frugal references
    */
   public final int                serviceId;

   /**
    * A list of clients that provide this service
    */
   public final Set<EntityInfo> providers = new HashSet<>();

   public ServiceDef(String name, String iconURL, int serviceId) {
      this.name = name;
      this.iconURL = iconURL;
      this.serviceId = serviceId;
   }

}
