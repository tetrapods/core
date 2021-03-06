package io.tetrapod.core.tasks;
/*
 Copyright (C) 2016 Electronic Arts Inc.  All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1.  Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
 2.  Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
 3.  Neither the name of Electronic Arts, Inc. ("EA") nor the names of
     its contributors may be used to endorse or promote products derived
     from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY ELECTRONIC ARTS AND ITS CONTRIBUTORS "AS IS" AND ANY
 EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL ELECTRONIC ARTS OR ITS CONTRIBUTORS BE LIABLE FOR ANY
 DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import io.tetrapod.core.ServiceException;
import io.tetrapod.core.utils.CoreUtil;
import org.slf4j.MDC;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class TaskContext {
   private final static ThreadLocal<Deque<TaskContext>> contextStacks = new ThreadLocal<>();
   private final static WeakHashMap<Thread, Deque<TaskContext>> contextStacksMap = new WeakHashMap<>();
   private final static AtomicLong nextId = new AtomicLong(1);
   private static Set<String> mdcKeys = new HashSet<>();

   // human friendly id, for debugging
   private long id = nextId.getAndIncrement();

   private final ConcurrentHashMap<String, Object> properties = new ConcurrentHashMap<>();
   private ConcurrentHashMap<String, String> mdcVariables = new ConcurrentHashMap<>();

   private Executor defaultExecutor = null;

   public static synchronized void setMdcVariableName(String mdcVariable) {
      Set<String> keys = new HashSet<>(mdcKeys);
      keys.add(mdcVariable);
      mdcKeys = keys;
   }

   public Executor getDefaultExecutor() {
      return defaultExecutor;
   }

   public void setDefaultExecutor(Executor defaultExecutor) {
      this.defaultExecutor = defaultExecutor;
   }

   public static TaskContext pushNew()
   {
       final TaskContext context = new TaskContext();
       context.push();
       return context;
   }


   /**
    * Adds this execution context to the top of the context stack for the current thread.
    */
   public void push() {
      Deque<TaskContext> stack = contextStacks.get();
      if (stack == null) {
         // Attention! Do not use a concurrent collection for the stack
         // it has been measured that concurrent collections decrease the TaskContext's performance.
         stack = new LinkedList<>();
         contextStacks.set(stack);
         final Thread currentThread = Thread.currentThread();
         synchronized (contextStacksMap) {
            // this happens only once per thread, no need to optimize it
            contextStacksMap.put(currentThread, stack);
         }
      }
      stack.addLast(this);
      setMdc();
   }

   /**
    * Removes the this execution context from the context stack for the current thread.
    * This will fail with IllegalStateException if the current context is not at the top of the stack.
    */
   public void pop() {
      Deque<TaskContext> stack = contextStacks.get();
      if (stack == null) {
         throw new IllegalStateException("Invalid execution context stack state: " + stack + " trying to remove: " + this);
      }
      final TaskContext last = stack.pollLast();
      if (last != this) {
         if (last != null) {
            // returning it to the stack
            stack.addLast(last);
         }
         throw new IllegalStateException("Invalid execution context stack state: " + stack + " trying to remove: " + this + " but got: " + last);
      } else {
         if (!stack.isEmpty()) {
            stack.getLast().setMdc();
      }
   }
   }

   private void setMdc() {
      MDC.setContextMap(mdcVariables);
   }

   @Override
   public String toString() {
      return getClass().getSimpleName() + ":" + id;
   }

   /**
    * Gets the current execution context for this thread from the stack.
    *
    * @return the current context
    */
   public static TaskContext current(boolean nullIfNotSet) {
      final Deque<TaskContext> stack = contextStacks.get();
      if (stack == null) {
         if (nullIfNotSet) {
            return null;
         }
         throw new ServiceException("No current task context set.");  //todo: This may be too heavy handed but we want to fail fast where we don't have a context set
      }
      return stack.peekLast();
   }
   public static TaskContext current() {
      return current(false);
   }


   /**
    * Enables the application to peek into what is being executed in another thread.
    * This method is intended for debugging and profiling.
    */
   public static TaskContext currentFor(Thread thread) {
      final Deque<TaskContext> stack;
      synchronized (contextStacksMap) {
         // this should not be called very often, it's for profiling
         stack = contextStacksMap.get(thread);
      }
      // beware: this is peeking in a non synchronized LinkedList
      // just peeking is safe enough for profiling.
      return (stack != null) ? stack.peek() : null;
   }

   /**
    * @return all threads that have active contexts
    */
   public static Set<Thread> activeThreads() {
      synchronized (contextStacksMap) {
         return new HashSet<>(contextStacksMap.keySet());
      }
   }

   /**
    * Wraps a Runnable in such a way the it will push the current execution context before any code gets executed and pop it afterwards
    *
    * @param w the functional interface to be wrapped
    * @return wrapped object if there is a current execution context, or the same object if not.
    */
   public static Runnable wrap(Runnable w) {
      TaskContext c = current();
      if (c != null) {
         return () -> {
            c.push();
            try {
               w.run();
            } finally {
               c.pop();
            }
         };
      }
      return w;
   }

   /**
    * Wraps a BiConsumer in such a way the it will push the current execution context before any code gets executed and pop it afterwards
    *
    * @param w the functional interface to be wrapped
    * @return wrapped object if there is a current execution context, or the same object if not.
    */
   public static <T, U> BiConsumer<T, U> wrap(BiConsumer<T, U> w) {
      TaskContext c = current();
      if (c != null) {
         return (t, u) -> {
            c.push();
            try {
               w.accept(t, u);
            } finally {
               c.pop();
            }
         };
      }
      return w;
   }

   /**
    * Wraps a Consumer in such a way the it will push the current execution context before any code gets executed and pop it afterwards
    *
    * @param w the functional interface to be wrapped
    * @return wrapped object if there is a current execution context, or the same object if not.
    */
   public static <T> Consumer<T> wrap(Consumer<T> w) {
      TaskContext c = current();
      if (c != null) {
         return (t) -> {
            c.push();
            try {
               w.accept(t);
            } finally {
               c.pop();
            }
         };
      }
      return w;
   }

   /**
    * Wraps a Function in such a way the it will push the current execution context before any code gets executed and pop it afterwards
    *
    * @param w the functional interface to be wrapped
    * @return wrapped object if there is a current execution context, or the same object if not.
    */
   public static <T, R> Function<T, R> wrap(Function<T, R> w) {
      TaskContext c = current();
      if (c != null) {
         return (t) -> {
            c.push();
            try {
               return w.apply(t);
            } finally {
               c.pop();
            }
         };
      }
      return w;
   }

   public static <T, R> ThrowableFunction<T, R> wrap(ThrowableFunction<T, R> w) {
      TaskContext c = current();
      if (c != null) {
         return (t) -> {
            c.push();
            try {
               return w.apply(t);
            } finally {
               c.pop();
            }
         };
      }
      return w;
   }


   /**
    * Wraps a Function in such a way the it will push the current execution context before any code gets executed and pop it afterwards
    *
    * @param w the functional interface to be wrapped
    * @return wrapped object if there is a current execution context, or the same object if not.
    */
   public static <T, U, R> BiFunction<T, U, R> wrap(BiFunction<T, U, R> w) {
      TaskContext c = current();
      if (c != null) {
         return (t, u) -> {
            c.push();
            try {
               return w.apply(t, u);
            } finally {
               c.pop();
            }
         };
      }
      return w;
   }

   /**
    * Wraps a Supplier in such a way the it will push the current execution context before any code gets executed and pop it afterwards
    *
    * @param w the functional interface to be wrapped
    * @return wrapped object if there is a current execution context, or the same object if not.
    */
   public static <T> Supplier<T> wrap(Supplier<T> w) {
      TaskContext c = current();
      if (c != null) {
         return () -> {
            c.push();
            try {
               return w.get();
            } finally {
               c.pop();
            }
         };
      }
      return w;
   }

   /**
    * Returns the property with the given name registered in the current execution context,
    * {@code null} if there is no property by that name.
    * <p>
    * A property allows orbit extensions to exchange custom information.
    * </p>
    *
    * @param name the name of the property
    * @return an {@code Object} or
    * {@code null} if no property exists matching the given name.
    */
   public <T> T getProperty(String name) {
      return CoreUtil.cast(properties.get(name));
   }

   public synchronized <T> T getProperty(String name, Function<String, T> propertyProvider) {
      T val = CoreUtil.cast(properties.get(name));
      if (val == null) {
         val = propertyProvider.apply(name);
         properties.put(name, val);
      }
      return val;
   }

   /**
    * Binds an object to a given property name in the current execution context.
    * If the name specified is already used for a property,
    * this method will replace the value of the property with the new value.
    * <p>
    * A property allows orbit extensions to exchange custom information.
    * </p>
    * <p>
    * A null value will work to remove the property.
    * </p>
    *
    * @param name  a {@code String} the name of the property.
    * @param value an {@code Object} may be null
    */
   public void setProperty(String name, Object value) {
      if (value != null) {
         properties.put(name, value);
         if (mdcKeys.contains(name)) {
            mdcVariables.put(name, value.toString());
            MDC.setContextMap(mdcVariables);
         }
      } else {
         properties.remove(name);
         if (mdcVariables.remove(name) != null) {
            MDC.setContextMap(mdcVariables);
         }
      }
   }

   public void clearProperty(String name) {
      properties.remove(name);
      if (mdcVariables.remove(name) != null) {
         MDC.setContextMap(mdcVariables);
      }
   }

   public static void set(String name, Object value) {
      current().setProperty(name, value);
   }

   public static <T> T get(String name) {
      return CoreUtil.cast(current().getProperty(name));
   }
   public static void clear(String name) {
      current().clearProperty(name);
   }

   protected Map<String, Object> properties() {
      return properties;
   }


   @Override
   public TaskContext clone() {
      TaskContext clone = new TaskContext();
      clone.properties().putAll(properties());
      clone.mdcVariables.putAll(mdcVariables);
      return clone;
   }

   public static boolean hasCurrent() {
      final Deque<TaskContext> stack = contextStacks.get();
      return stack != null && !stack.isEmpty();
   }

   /**
    * Surrounds a function in a task context push-pop if there ins't one already established.  This is appropriate for places
    * where an entire tasks async context should be established on a system
    *
    * @param function  The function to wrap in a push-pop
    * @return  The wrapped runnable
    */
   public static <T> T doPushPopIfNeeded(Func0<T> function) {
      if (hasCurrent()) {
         return function.apply();
      } else {
         TaskContext ctx = TaskContext.pushNew();
         try {
            return function.apply();
         } finally {
            ctx.pop();
         }
      }
   }

   public static void doPushPopIfNeeded(Runnable runnable) {
      if (hasCurrent()) {
         runnable.run();
      } else {
         TaskContext ctx = TaskContext.pushNew();
         try {
            runnable.run();
         } finally {
            ctx.pop();
         }
      }

   }

   /**
    * Wraps a runnable in a push pop, to be executed when the runnable is executed.
    * @param runnable The runnable to wrap
    * @return The wrapped runnable
    */
   public static Runnable wrapPushPop(Runnable runnable) {
      return () -> {
         TaskContext ctx = TaskContext.pushNew();
         try {
            runnable.run();
         } finally {
            ctx.pop();
         }
      };
   }

   public static <T> T computeIfAbsent(String key, Function<String, T> valueProducer) {
      T value = get(key);
      if (value == null) {
         value = valueProducer.apply(key);
         set(key, value);
      }
      return value;
   }
}