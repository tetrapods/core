package io.tetrapod.core.serialize;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.core.utils.Util;

import java.io.*;

import org.slf4j.*;

import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.*;
import com.hazelcast.nio.*;
import com.hazelcast.nio.serialization.StreamSerializer;

/**
 * Utility class to serialize our structures in hazelcast using our serailizers.  Allows
 * us to keep the actual object in the hazelcast map.
 * <p>
 * A major caveat of this class is that all structures serialized by hazelcast using 
 * instances of this class must have distinct structure ids.
 */
public class HazelcastSerializer<T extends Structure> implements StreamSerializer<T> {
   
   public static class LoggingMembershipListener implements MembershipListener {
      private static final Logger logger = LoggerFactory.getLogger(LoggingMembershipListener.class);

      public void memberAdded(MembershipEvent membersipEvent) {
         logger.info("Hazelcast Member Added: " + membersipEvent);
      }

      public void memberRemoved(MembershipEvent membersipEvent) {
         logger.info("Hazelcast Member Removed: " + membersipEvent);
      }

      public void memberAttributeChanged(MemberAttributeEvent membersipEvent) {
         logger.info("Hazelcast Attribute Changed: " + membersipEvent);
      }
   }
   
   public static String hazelcastConfigFile(String file) {
      try {
         String s = Util.readFileAsString(new File(file));
         String awsAccess = Util.getProperty("aws.hazelcast.accessKey", "?");
         String awsSecret = Util.getProperty("aws.hazelcast.secretKey", "?");
         boolean aws = Util.getProperty("aws.hazelcast", false);
         s = s.replace("{{multicastOn}}", Boolean.toString(!aws));
         s = s.replace("{{awsOn}}", Boolean.toString(aws));
         s = s.replace("{{awsAccess}}", awsAccess);
         s = s.replace("{{awsSecret}}", awsSecret);
         return s;
      } catch (IOException e) {
         return null;
      }
   }
   
   public static <S extends Structure> SerializerConfig getSerializerConfig(S example) {
      return new SerializerConfig().
            setImplementation(new HazelcastSerializer<S>(example)).
            setTypeClass(example.getClass());
   }

   private final T exemplar;

   public HazelcastSerializer(T exemplar) {
      this.exemplar = exemplar;
   }

   @Override
   public int getTypeId() {
      return exemplar.getStructId();
   }

   @Override
   public void destroy() {}

   @Override
   public void write(ObjectDataOutput out, T object) throws IOException {
      out.write((byte[])object.toRawForm(TempBufferDataSource.forWriting()));
   }

   @Override
   public T read(ObjectDataInput in) throws IOException {
      final InputStream inputStream = (InputStream) in;
      DataSource ds = IOStreamDataSource.forReading(inputStream);
      @SuppressWarnings("unchecked")
      T obj = (T) exemplar.make();
      obj.read(ds);
      return obj;
   }

}
