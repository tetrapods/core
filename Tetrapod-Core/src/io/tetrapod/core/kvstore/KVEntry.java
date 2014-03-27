package io.tetrapod.core.kvstore;

import io.tetrapod.core.StructureFactory;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.*;

import java.io.IOException;

/**
 * Our on disk serialization entry for the key value store.  Unlike code genned
 * structures this one does not have a 1-1 mapping of on disk fields to in memory
 * fields.
 * <p>
 * This class can be reused.
 */
class KVEntry extends Structure {
   static final int COMMAND_SET = 1;
   static final int COMMAND_REMOVE = 2;
   static final int COMMAND_CLEAR = 3;
   
   String key;
   Object value;
   int command = COMMAND_SET;
   
   public void clear() {
      this.command = COMMAND_CLEAR;
      this.key = null;
      this.value = null;
   }

   public void save(String key, Object value) {
      this.command = COMMAND_SET;
      this.key = key;
      this.value = value;
   }

   public void remove(String key) {
      this.command = COMMAND_REMOVE;
      this.key = key;
      this.value = null;
   }

   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1,  command);
      switch (command) {
         case COMMAND_CLEAR:
            return;
         case COMMAND_REMOVE:
            data.write(2, key);
            return;
         case COMMAND_SET:
            data.write(2, key);
            writeValue(data);
            return;
      }
   }
   
   private final void writeValue(DataSource data) throws IOException {
      if (value instanceof Structure) {
         Structure s = (Structure)value;
         data.write(3, s.getContractId());
         data.write(4, s.getStructId());
         data.write(5, s);
      } else if (value instanceof Integer) {
         data.write(6,  ((Integer)value).intValue());
      } else if (value instanceof Long) {
         data.write(7,  ((Long)value).longValue());
      } else if (value instanceof Boolean) {
         data.write(8,  ((Boolean)value).booleanValue());
      } else if (value instanceof String) {
         data.write(9,  ((String)value));
      } else if (value instanceof byte[]) {
         data.write(10, (byte[])value);
      }
      data.writeEndTag();
   }


   @Override
   public void read(DataSource data) throws IOException {
      int contractId = 0;
      int structId = 0;
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: command = data.read_int(tag); break;
            case 2: key = data.read_string(tag); break;
            case 3: contractId = data.read_int(tag); break;
            case 4: structId = data.read_int(tag); break;
            case 5: value = data.read_struct(tag, StructureFactory.make(contractId, structId)); break;
            case 6: value = Integer.valueOf(data.read_int(tag)); break;
            case 7: value = Long.valueOf(data.read_long(tag)); break;
            case 8: value = Boolean.valueOf(data.read_boolean(tag)); break;
            case 9: value = data.read_string(tag); break;
            case 10: value = data.read_byte_array(tag); break;
            case Codec.END_TAG: return;
         }
      }
   }

   @Override public int getStructId() { return 0; }
   @Override public int getContractId() { return 0; }
}