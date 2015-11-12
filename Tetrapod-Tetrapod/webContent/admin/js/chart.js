/**
 * Chart.js is a wrapper object for a small flot.js time series chart embedded in admin app views to show important
 * metrics
 */

define(["jquery"], function($) {
   return Chart; // not using new means this returns a constructor function (ie class)

   // Chart Model
   function Chart(tag, observable) {
      var self = this;

      self.value = observable;

      var chartOptions = {
         grid: {
            labelMargin: 0,
            axisMargin: 0,
            margin: {
               top: 0,
               right: 0,
               bottom: 0,
               left: 0
            },
            borderWidth: 0,
            color: "rgba(212, 212, 212, 0.25)"
         },
         xaxis: {
            show: false
         },
         yaxis: {
            show: false,
            min: 0
         },
         legend: {
            show: false,
         }
      };

      self.plot = null;
      self.series = {
         data: [],
         lines: {
            lineWidth: 1,
            fill: true
         },
         color: "rgba(112, 212, 212, 0.25)",
         shadowSize: 1
      };

      self.updatePlot = function(timeRange, value) {
         self.series.data.push([Date.now(), value]);
         while (self.series.data[0][0] < Date.now() - timeRange) {
            self.series.data.shift();
         }
         if (self.series.maxY) {
            if (value > self.series.maxY) {
               self.series.maxY = value;
            }
            chartOptions.yaxis.max = self.series.maxY;
         } else {
            chartOptions.yaxis.max = null;
         }
         try {
            var plot = self.getPlot();
            if (plot) {
               plot.setData([self.series]);
               plot.setupGrid();
               plot.draw();
            }
         } catch (e) {
            console.error(e.message);
         }
      }
      
      self.setPlotData = function(name, values) {
         var s = self.series[name];
         if (s) {
            s.data = [];
         } else {
            self.series[name] = s = {
               label: name,
               data: [],
               lines: {
                  lineWidth: 1,
                  fill: true
               },
               shadowSize: 1,
               order: self.series.length
            };
         }
         $.each(values, function(i, val) {
            s.data.push([i, val])
         });
         self.render();
      }

      self.getPlot = function() {
         if (self.plot) {
            return self.plot;
         }

         var container = document.getElementById(tag);
         if (!container) {
            console.warn("Could not find element #" + tag);
            return;
         }

         if (container.clientWidth == 0) { 
            return;
         }

         self.plot = $.plot(container, [], chartOptions);

         return self.plot;
      }

      self.render = function() {
         try {
            var plot = self.getPlot();
            if (plot) {
               var arr = [];
               for ( var key in self.series) {
                  arr.push(self.series[key]);
               }
               arr.sort(function(a, b) {
                  return (a.order - b.order);
               });

               plot.setData(arr);
               plot.setupGrid();
               plot.draw();
            }
         } catch (e) {
            console.error(e.message);
         }
      }

      self.resize = function() {
         self.plot = null;
         self.render();
      }
      
      $(window).resize(function () {
         self.resize();
      });

      
   }

});