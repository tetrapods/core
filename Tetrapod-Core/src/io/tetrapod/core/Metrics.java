package io.tetrapod.core;

import io.tetrapod.protocol.core.ServerAddress;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.*;
import com.codahale.metrics.graphite.*;

/**
 * Service metrics instrumentation
 */
public class Metrics {

   /**
    * Singleton metrics registry for the process
    */
   public final static MetricRegistry metrics = new MetricRegistry();

   public static GraphiteReporter     graphiteReporter;

   public static Counter counter(Object o, String... names) {
      return metrics.counter(MetricRegistry.name(o.getClass(), names));
   }

   public static Timer timer(Object o, String... names) {
      return metrics.timer(MetricRegistry.name(o.getClass(), names));
   }

   public static Meter meter(Object o, String... names) {
      return metrics.meter(MetricRegistry.name(o.getClass(), names));
   }

   public static Counter counter(Class<?> c, String... names) {
      return metrics.counter(MetricRegistry.name(c, names));
   }

   public static Meter meter(Class<?> c, String... names) {
      return metrics.meter(MetricRegistry.name(c, names));
   }

   public static Timer timer(Class<?> c, String... names) {
      return metrics.timer(MetricRegistry.name(c, names));
   }

   /**
    * Start reporting to graphite
    * 
    * @param graphite the Graphite server
    * @param prefix our prefix (typically our hostname)
    */
   public synchronized static void startGraphite(ServerAddress graphite, String prefix) {
      assert (graphiteReporter == null);
      graphiteReporter = GraphiteReporter.forRegistry(metrics).prefixedWith(prefix).convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS).filter(MetricFilter.ALL)
            .build(new Graphite(new InetSocketAddress(graphite.host, graphite.port)));
      graphiteReporter.start(1, TimeUnit.MINUTES);
   }

   public synchronized static void stopGraphite() {
      assert graphiteReporter != null;
      if (graphiteReporter != null) {
         graphiteReporter.stop();
         graphiteReporter = null;
      }
   }

}
