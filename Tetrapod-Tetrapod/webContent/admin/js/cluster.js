define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout");
   var Alert = require("alert");
   var app = require("app");
   var Hosts = require("modules/hosts");
   var Raft = require("modules/raft");
   var Properties = require("modules/properties");
   var WebRoots = require("modules/webroots");
   var Builder = require("modules/builder");
   var Users = require("modules/users");

   return new ClusterModel();

   function ClusterModel() {
      var self = this;

      self.clear = clear;

      self.hosts = new Hosts(app);
      self.raft = new Raft(app);
      self.webroots = new WebRoots(app);
      self.properties = new Properties(app);
      self.users = new Users(app);

      function clear() {
         self.hosts.clear();
         self.raft.clear();
         self.webroots.clear();
         self.properties.clear();
         self.users.clear();
      }

   }

});