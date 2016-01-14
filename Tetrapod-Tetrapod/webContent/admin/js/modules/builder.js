define(["knockout", "jquery", "app", "alert"], function(ko, $, app, Alert) {
   return new Builder();

   function Builder() {
      var self = this;
      self.doDeploy = false;
      self.doLaunch = false;

      self.paused = false;
      self.buildNumber = "";
      self.buildName = "";
      self.run = run;
      self.services = [];
      self.load = load;
      self.upgradeHost = upgradeHost;
      self.hosts = [];

      var loading = {};
      var progressDialog;
      var BuildCommandConsts = app.server.consts['Tetrapod'].BuildCommand;

      function load(pods) {
         loading.num = pods.length;
         loading.oldServices = self.services;
         loading.oldHosts = self.hosts;

         self.hosts = [];
         self.services = [];

         for (var i = 0; i < pods.length; i++) {
            var old = findByName(pods[i].host, loading.oldHosts);
            self.hosts.push({
               name: pods[i].host,
               entityId: pods[i].entityId,
               isChecked: old && old.isChecked
            });
            loadOne(pods[i].entityId);
         }
      }

      function loadOne(id) {
         app.server.sendTo("Tetrapod.GetServiceBuildInfo", {}, id, function(res) {
            onLoaded(res);
            loading.num--;
            if (loading.num == 0 && !silent) {
               app.modalData(self);
               $("#buildExecute").button('reset');
               $("#buildModal").modal();
            }
         });
      }

      function onLoaded(result) {
         if (!result.isError()) {
            for (var i = 0; i < result.services.length; i++) {
               var s = result.services[i];
               var obj = findByName(s.serviceName, self.services);
               if (obj) {
                  obj.current = obj.current + "," + s.currentBuild
               } else {
                  obj = findByName(s.serviceName, loading.oldServices);
                  self.services.push({
                     name: s.serviceName,
                     isChecked: obj && obj.isChecked,
                     current: "" + s.currentBuild
                  });
               }
            }
         }
      }

      function run(onSuccess) {
         var array = [];
         var b = (self.buildName || "default") + "." + (self.buildNumber || "current");
         if (self.doDeploy) {
            array.push({
               serviceName: "",
               build: self.buildNumber.trim(),
               name: self.buildName,
               command: BuildCommandConsts.BUILD,
               display: "Pulling build " + b
            });
            for (var i = 0; i < self.services.length; i++) {
               var service = self.services[i];
               if (!service.isChecked)
                  continue;
               var command = {
                  serviceName: service.name,
                  build: self.buildNumber.trim(),
                  name: self.buildName,
                  command: BuildCommandConsts.DEPLOY,
                  display: "Deploying " + service.name
               };
               array.push(command);
            }
         }

         if (self.doLaunch) {
            for (var i = 0; i < self.services.length; i++) {
               var service = self.services[i];
               if (!service.isChecked)
                  continue;
               var command = {
                  serviceName: service.name,
                  build: self.buildNumber.trim() || BuildCommandConsts.LAUNCH_DEPLOYED,
                  name: self.buildName,
                  command: self.paused ? BuildCommandConsts.LAUNCH_PAUSED : BuildCommandConsts.LAUNCH,
                  display: "Launching " + service.name + " (" + b + ")"
               };
               array.push(command);
            }
         }

         $("#buildModal").modal('hide');
         progressDialog = Alert.progress("");
         for (var i = 0; i < self.hosts.length; i++) {
            var h = self.hosts[i];
            h.progress = "";
            h.commandsLeft = 0;
            if (h.isChecked) {
               h.commandsLeft = array.length;
               h.progress = "<b>" + h.name + "</b><br>";
               if (h.commandsLeft > 0) {
                  exec(h, array)
               } else {
                  updateProgress();
               }
            }
         }
      }

      function exec(host, commands) {
         var ix = commands.length - host.commandsLeft;
         var commandsList = [commands[ix]];
         host.progress += "&nbsp;&nbsp;&nbsp;&nbsp;" + commands[ix].display + " ..... ";
         updateProgress();
         app.server.sendTo("Tetrapod.ExecuteBuildCommand", {
            commands: commandsList
         }, host.entityId, function(res) {
            host.commandsLeft--;
            if (res.isError()) {
               host.progress += "<i class='fa fa-frown-o'></i><br>";
               host.commandsLeft = 0;
            } else {
               host.progress += "<i class='fa fa-smile-o'></i><br>";
            }
            updateProgress();
            if (host.commandsLeft > 0) {
               exec(host, commands);
            }
         });
      }

      function updateProgress() {
         if (progressDialog) {
            var p = "";
            var left = 0;
            for (var i = 0; i < self.hosts.length; i++) {
               p += self.hosts[i].progress;
               left += self.hosts[i].commandsLeft;
            }
            progressDialog.replace(p);
            if (left == 0) {
               progressDialog.addClose();
            }
         }
      }

      function findByName(name, array) {
         for (var i = 0; i < array.length; i++) {
            if (name == array[i].name) {
               return array[i];
            }
         }
      }

      function upgradeHost(hostname, hostId, buildName, buildNum, services) {         
         exec({
            name: hostname,
            entityId: hostId,
            isChecked: true,
            commandsLeft: 1,
            progress: hostname
         }, [{
            serviceName: 'all',
            build: buildNum.trim(),
            name: buildName.trim(),
            command: BuildCommandConsts.FULL_CYCLE,
            display: "UPGRADING " + hostname + " to " + buildName + "." + buildNum
         }]);
      }

   }
});
