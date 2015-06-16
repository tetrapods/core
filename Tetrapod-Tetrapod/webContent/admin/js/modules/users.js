define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout");
   var Alert = require("alert");

   return Users;

   function Users(app) {
      var self = this;

      self.users = ko.observableArray([]);

      self.addAdminUser = addAdminUser;

      function addAdminUser() {
         // TODO
      }

      function User(def) {
         var self = this;

         self.email = def.email;

         self.deleteUser = deleteUser;
         self.changePassword = changePassword;

         function deleteUser() {
            // TODO
         }

         function changePassword() {
            // TODO
         }
      }

   }
});