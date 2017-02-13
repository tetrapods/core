define([], function() { return Test });

function Test() {
   var self = this;

   self.doIt = function () {
      console.log("I'm doing it...");
   };

   return this;
}
