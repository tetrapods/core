package io.tetrapod.core.rpc;

abstract public class Message extends Structure {
   
   abstract public void dispatch(SubscriptionAPI handler);

}
