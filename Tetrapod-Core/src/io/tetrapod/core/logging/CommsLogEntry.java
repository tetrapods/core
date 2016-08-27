package io.tetrapod.core.logging;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.CommsLogHeader;

public class CommsLogEntry {

   public final CommsLogHeader header;
   public final Structure      struct;

   public CommsLogEntry(CommsLogHeader header, Structure struct) {
      this.header = header;
      this.struct = struct;
   }
}
