/**
 * A wrapper object for details about a raft node in the cluster
 */
define(["knockout", "jquery", "bootbox", "app", "build", "chart"], function(ko, $, bootbox, app, builder, Chart) {

   return RaftNode; // not using new means this returns a constructor function (ie class)

   // RaftNode Model
   function RaftNode(service) {
      var self = this;

      self.entityId = service.entityId;
      self.host = service.host;

      self.role = ko.observable();
      self.curTerm = ko.observable(0);
      self.lastTerm = ko.observable(0);
      self.lastIndex = ko.observable(0);
      self.commitIndex = ko.observable(0);
      self.numPeers = ko.observable(0);
      self.lastContact = ko.observable(0);

      self.update = update;
      self.leaveCluster = leaveCluster;

      self.update();

      function update() {
         app.server.sendTo("RaftStats", {}, service.entityId, function(info) {
            if (!info.isError()) {
               self.role(info.role);
               self.curTerm(info.curTerm);
               self.lastTerm(info.lastTerm);
               self.lastIndex(info.lastIndex);
               self.commitIndex(info.commitIndex);
               self.numPeers(info.numPeers);
               self.lastContact(new Date());
            } else {
               self.role(-1);
            }
         });
      }

      self.roleName = ko.pureComputed(function() {
         switch (self.role()) {
         case 0:
            return 'Joining';
         case 1:
            return 'Observer';
         case 2:
            return 'Follower';
         case 3:
            return 'Candidate';
         case 4:
            return 'Leader';
         case 5:
            return 'Failed';
         case 6:
            return 'Leaving';
         }
         return 'Unknown';
      });

      self.roleIcon = ko.pureComputed(function() {
         switch (self.role()) {
         case 0:
            return 'fa-ellipsis-h';
         case 1:
            return 'fa-dot-circle-o';
         case 2:
            return 'fa-circle-o';
         case 3:
            return 'fa-star-o';
         case 4:
            return 'fa-star';
         case 5:
            return 'fa-bomb';
         case 6:
            return 'fa-chain-broken';
         }
         return 'fa-question';
      });

      function leaveCluster() {

      }

   }
});