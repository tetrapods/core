define(function() {

   return {
      setCookie: setCookie,
      getCookie: getCookie,
      deleteCookie: deleteCookie
   };

   function setCookie(c_name, value, exdays) {
      var c_value = encodeURIComponent(value) + " ;path=/";
      if (exdays != null) {
         var d = new Date();
         d.setTime(d.getTime() + (exdays * 24 * 60 * 60 * 1000));
         c_value += ";expires=" + d.toUTCString()+";secure";
      }
      document.cookie = c_name + "=" + c_value;
   }

   function getCookie(cname) {
      var name = cname + "=";
      var ca = document.cookie.split(';');
      for (var i = 0; i < ca.length; i++) {
         var c = ca[i].trim();
         if (c.indexOf(name) == 0)
            return decodeURIComponent(c.substring(name.length, c.length));
      }
      return null;
   }

   function deleteCookie(name) {
      document.cookie = name + '=;path=/;expires=-1;secure';
   }

});
