define(["knockout", "jquery", "app"], function(ko, $, app) {
   return new Builder();
   
   function Builder() {
      self = this;
      self.doBuild = false;
      self.doDeploy = false;
      self.doLaunch = false;
      self.doFullCycle = false;
      self.buildNumber = "";
      self.run = run;
      self.services = [];
      self.load = load;
      
      var entityId = 0;
      var BuildCommandConsts = app.server.consts["Tetrapod.BuildCommand"];
      
      function load(id) {
         entityId = id;
         app.server.send("Tetrapod.GetServiceBuildInfo", {}, entityId).handle(onLoaded);
      }
      
      function onLoaded(result) {
         if (!result.isError()) {
            var array = [];
            for (var i = 0; i < result.services.length; i++) {
               var s = result.services[i];
               array.push({ name: s.serviceName, isChecked: isChecked(s.serviceName), current: s.currentBuild});
            }
            self.services = array;
            app.modalData(self);
            $("#buildExecute").button('reset');
            $("#buildModal").modal();
         }
      }
      
      function run() {
         var array = [];
         if (self.doDeploy) {
            array.push({ serviceName: "", build: self.buildNumber, command: BuildCommandConsts.BUILD});
            for (var i = 0; i < self.services.length; i++) {
               var service = self.services[i];
               if (!service.isChecked)
                  continue;
               var command = { serviceName: service.name, build: self.buildNumber, command: BuildCommandConsts.DEPLOY };
               array.push(command);
            }
         }
         if (self.doLaunch) {
            for (var i = 0; i < self.services.length; i++) {
               var service = self.services[i];
               if (!service.isChecked)
                  continue;
               var command = { serviceName: service.name, build: self.buildNumber, command: BuildCommandConsts.LAUNCH };
               if (command.build.length == 0) {
                  command.build = BuildCommandConsts.LAUNCH_DEPLOYED;
               }
               array.push(command);
            }
         }
         if (self.fullCycle) {
            array.push({ serviceName: "", build: self.buildNumber, command: BuildCommandConsts.FULL_CYCLE});
         }
         $("#buildExecute").button('loading');
         app.server.send("Tetrapod.ExecuteBuildCommand", { commands: array }, entityId).handle(function (res) {
            load(entityId);
         });
      }
      
      function isChecked(serviceName) {
         for (var i = 0; i < self.services.length; i++) {
            var service = self.services[i];
            if (service.name == serviceName) {
               return service.isChecked;
            }
         }
         return false;
      }
   }
});