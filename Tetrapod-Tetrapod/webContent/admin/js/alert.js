/**
 * Provides a wrapper around modal, stackable, alerts.  
 */
define(function(require) {
   var vex = require("vex");
   var vexDialog = require("vex.dialog");

   vex.defaultOptions.className = "vex-theme-os";
   vexDialog.buttons.NO.click = function($vexContent, event) {
      $vexContent.data().vex.value = null;
      return vex.close($vexContent.data().vex.id);
   }
   vexDialog.defaultOptions.value = null;
   vexDialog.defaultOptions.escapeButtonCloses = true;
   vexDialog.defaultOptions.overlayClosesOnClick = true;
   
   return {
      info: info,
      notify: notify,
      warn: warn,
      error: error,
      confirm: confirm,
      confirmWarn: confirmWarn,
      prompt: prompt,
      promptChoose: promptChoose
   }
   
   function info(message, callback) {
      vexDialog.alert({
         message: message,
         callback: callback
      });
   }
   
   function notify(message, callback) {
      var $vexContent = vexDialog.alert({
         message: message,
         callback: callback,
         escapeButtonCloses: false,
         overlayClosesOnClick: false,
         buttons: {}
      });
      return {
         close: function () { vex.close($vexContent.data().vex.id); }
      }
   }

   function warn(message, callback) {
      // TODO: style differently
      vexDialog.alert({
         message: message,
         callback: callback
      });
   }

   function error(message, callback) {
      // TODO: style differently
      vexDialog.alert({
         message: message,
         callback: callback
      });
   }

   // Can be called 
   //     confirm(message, function onSuccess() {})
   //     confirm(message, function onSuccessOrFail(res) {})
   function confirm(message, callback) {
      if (callback && callback.length == 0) {
         var oldCallback = callback;
         callback = function(res) { if (res !== null) oldCallback(); };
      }
      vexDialog.confirm({
         message: message,
         callback: callback
      });
      
   }
   
   function confirmWarn(message, callback) {
      if (callback && callback.length == 0) {
         var oldCallback = callback;
         callback = function(res) { if (res !== null) oldCallback(); };
      }
      // TODO: style differently
      vexDialog.confirm({
         message: message,
         callback: callback
      });
   }
   
   function prompt(message, callback, defaultValue, placeholder) {
      vexDialog.prompt({
         message: message,
         value: defaultValue,
         placeholder: placeholder,
         callback: callback
      });
   }
   
   function promptChoose(message, choices, onChoice) {
      var buttons = {};
      for (var i=0 ; i < choices.length; i++) {
         var s = choices[i];
         buttons[s] = makeVexButton(s, s);
      }
      vexDialog.confirm({
         message: message,
         buttons: buttons,
         callback: onChoice,
         // there's no real "cancel" so make sure to force
         escapeButtonCloses: false,
         overlayClosesOnClick: false
      });
   }
   
   function makeVexButton(text, val, className) {
      return {
         text: text,
         type: 'button',
         className: className || 'vex-dialog-button-secondary',
         click: function($vexContent, event) {
            $vexContent.data().vex.value = val;
            return vex.close($vexContent.data().vex.id);
         }
      }
   }


});