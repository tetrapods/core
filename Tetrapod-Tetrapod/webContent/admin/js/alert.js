/**
 * Provides a wrapper around modal, stackable, alerts.  
 * 
 * Right now it's using a slightly customized version of sweetalert
 * as the alert provider, although no where else in the program should
 * assume that's the case.
 */
define(function(require) {
   var sweetAlertLib = require("sweet-alert");
   var bootbox = require("bootbox");

   return {
      info: info,
      warn: warn,
      error: error,
      confirm: confirm,
      confirmWarn: confirmWarn,
      prompt: prompt,
      promptChoose: promptChoose
   }
   
   function info(message, callback) {
      return window.swal({
         title: "Info",    
         text: message,
         type: "info",   
         showCancelButton: false, 
         confirmButtonText: "OK",
         cancelFunction: undefined
      }, callback);
   }

   function warn(message, callback) {
      return window.swal({
         title: "Warning",    
         text: message,
         type: "warning",   
         showCancelButton: false, 
         confirmButtonText: "OK",
         cancelFunction: undefined
      }, callback);
   }

   function error(message, callback) {
      return window.swal({
         title: "Error",    
         text: message,
         type: "error",   
         showCancelButton: false, 
         confirmButtonText: "OK",
         cancelFunction: undefined
      }, callback);
   }

   function confirm(message, onSuccess, cancelFunction) {
      return window.swal({
         title: "Confirm",    
         text: message,
         type: "info",   
         showCancelButton: true, 
         confirmButtonText: "OK",
         cancelFunction: cancelFunction
      }, onSuccess);
   }

   function confirmWarn(message, onSuccess, cancelFunction) {
      return window.swal({
         title: "Confirm",    
         text: message,
         type: "warning",   
         showCancelButton: true, 
         confirmButtonText: "OK",
         cancelFunction: cancelFunction
      }, onSuccess);
   }
   
   function prompt(message, onSuccess, cancelFunction) {
      bootbox.prompt(
         message,
         function(result) {                
            if (result === null) {
               if (cancelFunction) cancelFunction();
            } else {
               onSuccess(result);
            }
         });
   }
   
   function promptChoose(message, choices, onChoice) {
      var buttons = {};
      for (var i=0 ; i < choices.length; i++) {
         buttons[choices[i]] = {
            label: choices[i],
            callback: onChoice(choices[i])
         }
      }
      return bootbox.dialog({
         message: message,
         closeButton: false,
         buttons: buttons
      });         
   }


});