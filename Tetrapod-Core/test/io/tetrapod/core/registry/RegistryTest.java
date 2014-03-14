package io.tetrapod.core.registry;

import org.junit.*;

public class RegistryTest {

   @Test
   public void testTopics() {
      Topic topic = new Topic(1, 1000);
      topic.subscribe(100);
      topic.subscribe(100);
      topic.subscribe(200);
      topic.subscribe(300);
      topic.subscribe(100);
      topic.subscribe(400);
      topic.subscribe(300);
      Assert.assertEquals(4, topic.getNumScubscribers());
      Assert.assertEquals(false, topic.unsubscribe(100));
      Assert.assertEquals(4, topic.getNumScubscribers());
      Assert.assertEquals(true, topic.unsubscribe(400));
      Assert.assertEquals(3, topic.getNumScubscribers());
      Assert.assertEquals(false, topic.unsubscribe(100));
      Assert.assertEquals(3, topic.getNumScubscribers());
      Assert.assertEquals(true, topic.unsubscribe(100));
      Assert.assertEquals(2, topic.getNumScubscribers());
   }

}
