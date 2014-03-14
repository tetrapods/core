package io.tetrapod.core.registry;

import java.util.Map;

/**
 * All the meta data associated with a tetrapod entity
 */
public class Actor {

   public static final int     PARENT_ID_MASK    = 0x7FF00000; // top bytes denotes the parent

   // TODO: This all will go into a codegen info struct

   public final static byte    TYPE_TETRAPOD     = 1;
   public final static byte    TYPE_SERVICE      = 2;
   public final static byte    TYPE_ADMIN        = 3;
   public final static byte    TYPE_CLIENT       = 4;
   public final static byte    TYPE_ANONYMOUS    = 5;

   public final static int     STATUS_INIT       = 0b00000001;
   public final static int     STATUS_PAUSED     = 0b00000010;
   public final static int     STATUS_GONE       = 0b00000100;
   public final static int     STATUS_BUSY       = 0b00001000;
   public final static int     STATUS_OVERLOADED = 0b00010000;

   public int                  actorId;
   public int                  parentId;
   public long                 reclaimToken;
   public String               host;
   public int                  status;
   public byte                 type;
   public String               name;
   public int                  build;
   public int                  version;

   // TODO: stats

   private Map<Integer, Topic> topics;
   private Map<Integer, Topic> subscriptions;

}
