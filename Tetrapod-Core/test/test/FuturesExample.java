package test;


import io.tetrapod.core.tasks.Task;

import java.util.*;
import java.util.function.Consumer;

//import static com.ea.async.Async.await;

/**
 * @author paulm
 *         Created: 5/24/16
 */
public class FuturesExample {


   /**
    * Example of using Java Futures for continuation chains
    */
   public Task<Boolean> sendToAddress(int customerId, String message) {
      return getCustomer(customerId)
              .thenCompose(cust -> getEmailAddress(cust.emailId))
              .thenCompose(email -> sendEmailWithCallback(email, message));
   }

   /**
    * Example of doing parallel operations
    */
   public Task<?> sendToAddresses(List<Integer> customerIds, String message) {
      List<Task<Boolean>> futures = new ArrayList<>();
      for (int customerId : customerIds) {
         Task<Boolean> future = getCustomer(customerId)
                 .thenCompose(cust -> getEmailAddress(cust.emailId))
                 .thenCompose(email -> sendEmailWithCallback(email, message));
         futures.add(future);
      }
      return Task.allOf(futures.toArray(new Task[futures.size()]));
   }


   /**
    * Example of using ea async-await lib which allow you to write imperatively
    */
   public Task<Boolean> sendToAddressUsingAwait(int customerId, String message) {
//      Customer customer = await(getCustomer(customerId));
//      EmailAddress email = await(getEmailAddress(customer.emailId));
//      boolean success = await(sendEmailWithCallback(email, message));
//      return Task.completedFuture(success);
      return Task.from(true);
   }


   /**
    * Example of how you might take a function "sendEmailWithCallback" and turn it into a function that returns a future
    */
   public Task<Boolean> sendEmailWithCallback(EmailAddress address, String message) {
      Task<Boolean> ret = new Task<>();
      sendEmailWithCallback(address.email, message, success -> {
         ret.complete(success);
      });
      return ret;
   }

   /**
    * STUB METHODS
    */

   public Task<Customer> getCustomer(int id) {
      //gets the customer by id asynchronously
      return null;
   }
   public Task<EmailAddress> getEmailAddress(int id) {
      //gets the email address by id asynchronously
      return null;
   }

   public void sendEmailWithCallback(String emailAddress, String message, Consumer<Boolean> completeCallback) {
      //sends and email and calls the callback
   }

   /**
    * STUB STRUCTS
    */

   public static class Customer {
      public int customerId;
      public int emailId;
   }

   public static class EmailAddress {
      public int emailId;
      public String email;
   }



}
