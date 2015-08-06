package io.tetrapod.core.serialize;

import static io.tetrapod.protocol.core.TypeDescriptor.*;
import io.tetrapod.core.StructureFactory;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.*;

import java.io.*;
import java.util.List;

public class StructureAdapter extends Structure {

   private final StructDescription description;
   private final Object[]          fields;

   public StructureAdapter(StructDescription desc) {
      this.description = desc;
      this.fields = new Object[desc.types.length];
   }

   @SuppressWarnings("unchecked")
   @Override
   public void write(DataSource data) throws IOException {
      for (int i = 1; i < description.types.length; i++) {
         Object val = fields[i];
         // string is the only actual null value we pass through on the wire, for all other
         // fields null is illegal and means "unset", or null is legal but must correspond to 
         // default value since all non-primitives have default of null
         if (val == null && (description.types[i] == null || description.types[i].type != T_STRING))
            continue;
         switch (description.types[i].type) {
            case T_BYTE_LIST:
               data.write_byte(i, (List<Byte>) val);
               break;
            case T_INT_LIST:
               data.write_int(i, (List<Integer>) val);
               break;
            case T_BOOLEAN_LIST:
               data.write_boolean(i, (List<Boolean>) val);
               break;
            case T_LONG_LIST:
               data.write_long(i, (List<Long>) val);
               break;
            case T_DOUBLE_LIST:
               data.write_double(i, (List<Double>) val);
               break;
            case T_STRING_LIST:
               data.write_string(i, (List<String>) val);
               break;
            case T_STRUCT_LIST:
               data.write_struct(i, (List<Structure>) val);
               break;
            case T_BYTE:
               data.write(i, ((Byte) val).byteValue());
               break;
            case T_INT:
               data.write(i, ((Integer) val).intValue());
               break;
            case T_BOOLEAN:
               data.write(i, ((Boolean) val).booleanValue());
               break;
            case T_LONG:
               data.write(i, ((Long) val).longValue());
               break;
            case T_DOUBLE:
               data.write(i, ((Double) val).doubleValue());
               break;
            case T_STRING:
               data.write(i, ((String) val));
               break;
            case T_STRUCT:
               data.write(i, ((Structure) val));
               break;
         }
      }
      data.writeEndTag();
   }

   @Override
   public void read(DataSource data) throws IOException {
      while (true) {
         int tag = data.readTag();
         if (tag == Codec.END_TAG)
            return;
         if (tag > description.types.length - 1) {
            data.skip(tag);
            continue;
         }
         TypeDescriptor type = description.types[tag];
         if (type == null) {
            data.skip(tag);
            continue;
         }
         switch (type.type) {
            case T_BYTE_LIST:
               fields[tag] = data.read_byte_list(tag);
               break;
            case T_INT_LIST:
               fields[tag] = data.read_int_list(tag);
               break;
            case T_BOOLEAN_LIST:
               fields[tag] = data.read_boolean_list(tag);
               break;
            case T_LONG_LIST:
               fields[tag] = data.read_long_list(tag);
               break;
            case T_DOUBLE_LIST:
               fields[tag] = data.read_double_list(tag);
               break;
            case T_STRING_LIST:
               fields[tag] = data.read_string_list(tag);
               break;
            case T_STRUCT_LIST:
               fields[tag] = data.read_struct_list(tag, exemplar(tag));
               break;
            case T_BYTE:
               fields[tag] = data.read_byte(tag);
               break;
            case T_INT:
               fields[tag] = data.read_int(tag);
               break;
            case T_BOOLEAN:
               fields[tag] = data.read_boolean(tag);
               break;
            case T_LONG:
               fields[tag] = data.read_long(tag);
               break;
            case T_DOUBLE:
               fields[tag] = data.read_double(tag);
               break;
            case T_STRING:
               fields[tag] = data.read_string(tag);
               break;
            case T_STRUCT:
               fields[tag] = data.read_struct(tag, exemplar(tag));
               break;
         }
      }
   }

   @Override
   public int getStructId() {
      return description.types[0].structId;
   }

   @Override
   public int getContractId() {
      return description.types[0].contractId;
   }

   public String[] tagWebNames() {
      return description.tagWebNames;
   }

   @Override
   public Structure make() {
      return new StructureAdapter(this.description);
   }

   private Structure exemplar(int ix) {
      TypeDescriptor t = description.types[ix];
      return StructureFactory.make(t.contractId, t.structId);
   }
   
   @Override
   public String dump() {
      StringBuilder sw = new StringBuilder();
      sw.append(getClass().getSimpleName());
      sw.append("(" + getContractId() + "," + getStructId() + ") { ");
      for (int i = 1; i < fields.length; i++) {
         if (i > 1)
            sw.append(", ");
         sw.append(tagWebNames()[i]);
         sw.append(':');
         // TODO: this isn't @sensitive aware as that info isn't part of the description
         //       for now we don't dump any values
         // sw.append(""+dumpValue(fields[i], false));
         sw.append("~");
      }
      sw.append(" }");
      return sw.toString();
   }
}
