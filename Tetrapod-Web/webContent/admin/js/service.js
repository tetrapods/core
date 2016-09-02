define(["knockout", "jquery", "bootbox", "alert", "app", "chart", "modules/builder"], function(ko, $, bootbox, Alert, app, Chart, builder) {
   // static variables

   var Core = app.coreConsts;

   return Service; // not using new means this returns a constructor function (ie class)

   // Service Model
   function Service(entity) {
      var self = this;
      self.entityId = entity.entityId;
      self.name = entity.name;
      self.host = entity.host;
      self.status = ko.observable(entity.status);
      self.commands = ko.observableArray();
      self.build = entity.build;
      self.mps = ko.observable();
      self.rps = ko.observable();
      self.latency = ko.observable();
      self.counter = ko.observable();
      self.disk = ko.observable();
      self.memory = ko.observable();
      self.load = ko.observable();
      self.threads = ko.observable();
      self.isSelected = ko.observable(false);
      self.showRequestStats = showRequestStats;
      self.requestStats = ko.observableArray([]);
      self.subscribe = subscribe;

      self.iconURL = ko.observable("media/gear.gif");

      function subscribe(attempt) {
         if (self.isGone())
            return;
         app.sendTo("ServiceDetails", {}, self.entityId, function(result) {
            if (!result.isError()) {
               self.iconURL(result.iconURL);
               self.metadata = result.metadata;
               if (result.commands) {
                  for (var i = 0; i < result.commands.length; i++) {
                     self.commands.push(result.commands[i]);
                  }
               }
               app.sendTo("ServiceStatsSubscribe", {}, self.entityId, app.server.logResponse)
            } else if ((result.errorCode == Core.SERVICE_UNAVAILABLE || result.errorCode == Core.INVALID_RIGHTS) && !self.removed) {
               setTimeout(function() {
                  subscribe(attempt + 1);
               }, 1000 * attempt);
            }
         });
      }

      // we need to run this after KO is done binding, or jquery can't find the element by id
      setTimeout(function() {
         var e = $('#dropdown-' + self.entityId);
         e.on('show.bs.dropdown', function() {
            self.isSelected(true);
         });
         e.on('hide.bs.dropdown', function() {
            self.isSelected(false);
         });
      }, 1);

      self.execute = function(command) {
         if (command.hasArgument) {
            bootbox.prompt("Enter argument value:", function(result) {
               if (result !== null) {
                  app.sendDirect(command.contractId, command.structId, {
                     data: result
                  }, self.entityId, app.alertResponse);
               }
            });
         } else {
            app.sendDirect(command.contractId, command.structId, {}, self.entityId, app.alertResponse);
         }
      }

      self.entityString = ko.computed(function() {
         return self.entityId + " (0x" + self.entityId.toString(16) + ")";
      });

      self.isPaused = function() {
         return (self.status() & Core.STATUS_PAUSED) != 0;
      }

      self.isGone = function() {
         return (self.status() & Core.STATUS_GONE) != 0;
      }

      self.isStarting = function() {
         return (self.status() & Core.STATUS_STARTING) != 0;
      }

      self.isFailed = function() {
         return (self.status() & Core.STATUS_FAILED) != 0;
      }

      self.isPassive = function() {
         return (self.status() & Core.STATUS_PASSIVE) != 0;
      }

      self.isStopping = function() {
         return (self.status() & Core.STATUS_STOPPING) != 0;
      }

      self.isOverloaded = function() {
         return (self.status() & Core.STATUS_OVERLOADED) != 0;
      }

      self.statusName = ko.computed(function() {
         if (self.isGone())
            return "GONE";
         if (self.isStopping())
            return "STOPPING";
         if (self.isFailed())
            return "FAILED";
         if (self.isOverloaded())
            return "OVERLOADED";
         if (self.isPaused())
            return "PAUSED";
         if (self.isStarting())
            return "STARTING";
         if (self.isPassive())
            return "PASSIVE";
         return "RUNNING";
      });

      self.row_style = ko.pureComputed(function() {
         if (self.isGone())
            return "gone-row";
         if (self.isSelected())
            return "selected";
         if (self.isStopping())
            return "stopping";
         if (self.isPaused())
            return "paused";
         if (self.isStarting())
            return "starting";
         return "";
      });

      self.status_style = ko.computed(function() {
         return self.statusName().toLowerCase() + " centered";
      });

      self.pause = function() {
         app.sendTo("Pause", {}, self.entityId);
      }

      self.purge = function() {
         app.sendTo("Purge", {}, self.entityId);
      }

      self.debug = function() {
         app.sendTo("Debug", {}, self.entityId);
      }

      self.releaseExcess = function() {
         bootbox.confirm("Are you sure you want to release excess rooms on: " + self.name + "[" + self.entityId + "]?", function(result) {
            if (result) {
               app.sendTo("ReleaseExcess", {}, self.entityId);
            }
         });
      }

      self.unpause = function() {
         app.sendTo("Unpause", {}, self.entityId);
      }
      self.restart = function() {
         bootbox.confirm("Are you sure you want to restart service: " + self.name + "[" + self.entityId + "]?", function(result) {
            if (result) {
               app.sendTo("Restart", {}, self.entityId);
            }
         });
      }
      self.shutdown = function() {
         bootbox.confirm("Are you sure you want to shutdown service: " + self.name + "[" + self.entityId + "]?", function(result) {
            if (result) {
               app.sendTo("Shutdown", {}, self.entityId);
            }
         });
      }

      self.clearErrors = function() {
         app.sendTo("ResetServiceErrorLogs", {}, self.entityId);
      }

      self.showErrors = function() {
         app.sendTo("ServiceErrorLogs", {}, self.entityId, function(result) {
            if (!result.isError()) {
               var messages = [];
               messages.push('<div class="service-logs" style="height: 100%; min-height: 200px">');
               messages.push('<ul>');

               for (var i = 0; i < result.errors.length; i++) {
                  var error = result.errors[i];
                  var levelStyle = self.getLogLevelStyle(error.level);
                  var timestamp = logtime(new Date(error.timestamp));
                  messages.push('<li class="' + levelStyle + '">');
                  messages.push(timestamp + "&nbsp;");
                  messages.push(error.thread + "&nbsp;");
                  messages.push(error.logger + "&nbsp;");
                  messages.push('<span class="service-logs-msg">' + error.msg + '</span>');
                  messages.push('</li>');
               }
               messages.push('</ul>');
               messages.push('</div>');

               var text = messages.join("");
               bootbox.dialog({
                  message: text
               }).find("div.modal-dialog").addClass("service-error-logs-dialog");
            }
         });
      }

      self.requestStats([]);
      self.rpcStat = ko.observable();
      self.reqSort = ko.observable(1);
      self.requestStatsTimeRange = ko.observable(0);
      self.requestStatsDomains = ko.observableArray([]);
      self.requestStatsDomain = ko.observable(null);
      self.reqChart = new Chart("service-stat-histogram-" + self.entityId);

      self.reqSort.subscribe(function() {
         fetchRequestStats();
      });

      self.requestStatsDomain.subscribe(function() {
         fetchRequestStats();
      });

      function statClicked(r) {
         self.rpcStat(r);
         self.reqChart.setPlotData('Selection', r.timeline);
      }

      function showRequestStats() {
         self.requestStatsDomain('Requests');
         self.requestStats([]);
         fetchRequestStats();
      }

      function fetchRequestStats() {
         var currentTimeMillis = new Date().getTime();
         var minTime = currentTimeMillis - 1000 * 60 * 15;
         self.rpcStat(null);
         self.reqChart.setPlotData('Selection', []);

         app.sendTo("ServiceRequestStats", {
            limit: 25,
            minTime: minTime,
            sortBy: self.reqSort(),
            domain: self.requestStatsDomain()
         }, self.entityId, function(result) {
            if (!result.isError()) {
               var maxCount = 0, maxTime = 0, maxAvgTime = 0;
               for (var i = 0; i < result.requests.length; i++) {
                  var r = result.requests[i];
                  r.totalTime = Math.round(r.time / 1000.0);
                  r.avgTime = ((r.time / 1000.0) / r.count);
                  maxCount = Math.max(maxCount, r.count);
                  maxTime = Math.max(maxTime, r.totalTime);
                  maxAvgTime = Math.max(maxAvgTime, r.avgTime);
                  r.numErrors = 0;
                  for (var j = 0; j < r.errors.length; j++) {
                     var error = r.errors[j];
                     if (error.id != 0) {
                        r.numErrors += error.count;
                     }
                  }
               }

               for (var i = 0; i < result.requests.length; i++) {
                  var r = result.requests[i];
                  r.countPercent = r.count / maxCount;
                  r.totalTimePercent = r.totalTime / maxTime;
                  r.avgTimePercent = r.avgTime / maxAvgTime;
                  r.errorRate = r.numErrors / r.count;
                  r.statClicked = statClicked;
               }
               self.requestStatsTimeRange(formatElapsedTime(result.curTime - result.minTime))
               self.requestStats(result.requests);
               self.requestStatsDomains(result.domains);
               self.reqChart.setPlotData('Timeline', result.timeline);
               var d = $('#request-stats-' + self.entityId);
               d.modal('show');
               d.on('shown.bs.modal', function() {
                  self.reqChart.render();
               });
            } else {
               self.requestStats([]);
            }
         });
      }

      function formatElapsedTime(delta) {
         if (delta < 0) {
            return delta + "ms"; // hmm...
         }
         if (delta < 60000) {
            return Math.round(delta / 1000) + " seconds";
         }
         var mins = Math.round(delta / 60000);
         var hours = Math.floor(mins / 60);
         mins = mins - (hours * 60);
         if (hours > 0) {
            return hours + "hours " + mins + " minutes";
         } else {
            return mins + " minutes";
         }
      }

      self.hasErrors = ko.pureComputed(function() {
         return (self.status() & Core.STATUS_ERRORS) != 0;
      });

      self.hasWarningsOnly = ko.pureComputed(function() {
         return (self.status() & Core.STATUS_WARNINGS) != 0 && (self.status() & Core.STATUS_ERRORS) == 0;
      });

      self.hasErrorsOrWarnings = ko.pureComputed(function() {
         return (self.status() & Core.STATUS_ERRORS) != 0 || (self.status() & Core.STATUS_WARNINGS) != 0;
      });

      self.setCommsLogLevel = function() {
         bootbox.dialog({
            message: 'Log Level: <select id="level">' + '<option value="off">Off</option>' + '<option value="error">Error</option>' + '<option value="warn">Warning</option>' + '<option value="info">Info</option>' + '<option value="debug">Debug</option>' + '<option value="trace">Trace</option>' + '<option value="all">All</option>' + '</select>',
            buttons: {
               success: {
                  label: "OK",
                  className: "btn-success",
                  callback: function() {
                     var level = $('#level').val();
                     app.sendTo("SetCommsLogLevel", {
                        level: level
                     }, self.entityId);
                  }
               },
               cancel: {
                  label: "Cancel",
                  className: "btn-cancel"
               }
            }
         });
      }

      self.deleteService = function() {
         app.sendAny("Unregister", {
            entityId: self.entityId
         });
      }

      self.canPurge = ko.pureComputed(function() {
         return self.isPaused();
      });

      self.canPause = ko.pureComputed(function() {
         return !self.isGone() && !self.isPaused();
      });

      self.canUnpause = ko.pureComputed(function() {
         return !self.isGone() && self.isPaused();
      });

      self.statsUpdate = function(msg) {
         self.latency(msg.latency);
         self.rps(msg.rps);
         self.mps(msg.mps);
         self.counter(msg.counter);
         self.load(msg.load);
         self.memory(msg.memory);
         self.disk(msg.disk);
         self.threads(msg.threads);
      }

      self.isRaftNode = ko.pureComputed(function() {
         return entity.type == Core.TYPE_TETRAPOD;
      });

      // ////////////////////////////////////// stats graphs ////////////////////////////////////////

      self.latencyChart = new Chart("service-chart-latency-" + self.entityId);
      self.rpsChart = new Chart("service-chart-rps-" + self.entityId);
      self.mpsChart = new Chart("service-chart-mps-" + self.entityId);
      self.counterChart = new Chart("service-chart-counter-" + self.entityId);

      self.latencyChart.series.maxY = 500;

      // updates all charts for this service
      self.chart = function() {
         if (!self.removed) {
            self.latencyChart.updatePlot(60000, self.latency());
            self.rpsChart.updatePlot(60000, self.rps());
            self.mpsChart.updatePlot(60000, self.mps());
            self.counterChart.updatePlot(60000, self.counter());
         }
      }

      var LogConsts = app.server.consts['Core'].ServiceLogEntry;

      var levels = [LogConsts.LEVEL_ALL, LogConsts.LEVEL_TRACE, LogConsts.LEVEL_DEBUG, LogConsts.LEVEL_INFO, LogConsts.LEVEL_WARN, LogConsts.LEVEL_ERROR, LogConsts.LEVEL_OFF];

      self.getLogLevelStyle = function(level) {
         switch (level) {
         case LogConsts.LEVEL_ALL:
            return 'all';
         case LogConsts.LEVEL_TRACE:
            return 'trace';
         case LogConsts.LEVEL_DEBUG:
            return 'debug';
         case LogConsts.LEVEL_INFO:
            return 'info';
         case LogConsts.LEVEL_WARN:
            return 'warn';
         case LogConsts.LEVEL_ERROR:
            return 'error';
         case LogConsts.LEVEL_OFF:
            return 'off';
         }
         return '';
      }

      self.logLevels = ko.observableArray(levels);

      var months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
      function logtime(d) {
         return d.getFullYear() + "-" + months[d.getMonth()] + "-" + d.getDate() + " " + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds() + ":" + d.getMilliseconds();
      }
      self.logLevel = ko.observable(LogConsts.LEVEL_INFO);
      self.logs = ko.observableArray([]);
      self.autoScrollLogs = ko.observable(true);
      var logRefreshPending = false;

      self.refreshLogs = function() {
         if (!logRefreshPending) {
            logRefreshPending = true;

            app.sendTo("ServiceLogs", {
               logId: self.lastLogId,
               level: self.logLevel(),
               maxItems: 100
            }, self.entityId, function(res) {
               logRefreshPending = false;
               if (!res.isError()) {
                  if (self.expanded()) {
                     self.lastLogId = res.lastLogId;
                     if (res.items.length > 0) {
                        $.each(res.items, function(i, item) {
                           item.levelStyle = self.getLogLevelStyle(item.level);
                           item.timestamp = logtime(new Date(item.timestamp))
                           self.logs.push(item);
                        });
                        // remove older items from list
                        while (self.logs.length > 1000) {
                           self.logs.shift();
                        }
                        if (self.autoScrollLogs()) {
                           var scroll = document.getElementById("service-logs-" + self.entityId);
                           scroll.scrollTop = scroll.scrollHeight;
                        }
                     }
                  }
               }
            })
         }
      };

      self.expanded = new ko.observable(false);
      self.showLogs = function() {
         self.expanded(!self.expanded());
         self.logs.removeAll();
         self.lastLogId = 0;
         if (self.expanded()) {
            self.refreshLogs();
         }
      }

      // called by our timer in cluster.js for periodic updates
      self.update = function() {
         self.chart();
         if (self.expanded()) {
            self.refreshLogs();
         }
      };

   }
});
