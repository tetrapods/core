import * as serverBase from 'protocol/server';
import * as core from 'protocol/core';
import * as web from 'protocol/web';
import * as tetrapod from 'protocol/tetrapod';
import App from "./App";

export class ChatboxContext {
   private static _instance: ChatboxContext;

   private server: any;
   private authToken?: string;
   private token: string;
   private email?: string;
   private sessionToken?: string;
   private accountId: number;
   private adminTopicKey?: string;
   private app: App;

   init() {
      this.server = serverBase(tetrapod, core, web);
      this.authToken = ChatboxContext.getCookie("auth-token");
   }

   public static get Instance() {
      return this._instance || (this._instance = new this());
   }

   public login(email: string, password: string, callback?:(error?:string) => void) {
      this.server.send("AdminLogin", {
         email: email,
         password: password
      }, (result: any) => {
         if (result.isError()) {
            if(callback) callback("Login Failed");
         } else {
            this.email = email;
            if(callback) callback();
         }
         this.onLogin(result);
      });
   }

   public run(app: App) {
      this.app = app;
      this.server.commsLog = true;
      this.authToken = ChatboxContext.getCookie("auth-token");
      this.connect();
   }

   public connect() {
      const port = window.location.port == "3000" ? 9904 : window.location.port;
      this.server.connect(window.location.hostname, window.location.protocol == 'https:', port)
         .listen(this.onConnected, this.onDisconnected);
   }

   public subscribeAdminTopic() {
      this.server.send("AdminSubscribe", {
         accountId: this.accountId,
         authToken: this.sessionToken
      }, (result: any) => {
         if (result.isError()) {
            this.server.logResponse(result);
         } else {
            this.adminTopicKey = result.publisherId + '.' + result.topicId;
         }
      });
   }

   public refreshLoginToken(callback?: Function) {
      this.server.send("AdminSessionToken", {
         accountId: this.accountId,
         authToken: this.authToken,
      }, (result: any) => {
         if (result.isError()) {
            this.onLogout(true);
         } else {
            this.sessionToken = result.sessionToken;
            if (callback)
               callback();
         }
      });
   }

   onConnected = () => {
      this.app.clearAll();
      this.server.sendDirect("Web.Register", {
         build: 0,
         contractId: 0,
         name: "Web-Admin",
         token: this.token
      }, this.onRegistered);
   };

   onDisconnected = () => {
      setTimeout(() => {
         this.connect();
      }, 1000);
   };

   onRegistered = (result: any) => {
      if (!result.isError()) {
         this.token = result.token;
         if (this.authToken != null && this.authToken != "") {
            this.server.send("AdminAuthorize", {
               token: this.authToken
            }, this.onLogin);
         } else {
            this.onLogout();
         }
      }
   };

   onLogin = (result: any) => {
      if (!result.isError()) {
         if (result.token) {
            this.authToken = result.token;
            ChatboxContext.setCookie("auth-token", result.token);
         }
         if (result.email) {
            this.email = result.email;
         }
         if (result.accountId) {
            this.accountId = result.accountId;
         }
         this.refreshLoginToken(() => {
            this.subscribeAdminTopic();
            setInterval(this.refreshLoginToken, 60000 * 10); // refresh token every 10 minutes
         });
         this.app.userLoggedIn(true);
      } else {
         this.onLogout();
      }
   };

   private onLogout(keepToken?: boolean) {
      if (keepToken != true) {
         this.authToken = undefined;
         ChatboxContext.deleteCookie("auth-token");
      }
      this.app.userLoggedIn(false);
      this.app.clearAll();
      this.email = undefined;
      this.sessionToken = undefined;
      this.accountId = 0;
   }

   private static setCookie(c_name: string, value: string, exdays?: number) {
      let c_value = encodeURIComponent(value) + " ;path=/";
      if (exdays != null) {
         let d = new Date();
         d.setTime(d.getTime() + (exdays * 24 * 60 * 60 * 1000));
         c_value += ";expires=" + d.toUTCString() + ";secure";
      }
      document.cookie = c_name + "=" + c_value;
   }

   private static getCookie(cname: string) {
      let name = cname + "=";
      let ca = document.cookie.split(';');
      for (let c of ca) {
         c = c.trim();
         if (c.indexOf(name) == 0)
            return decodeURIComponent(c.substring(name.length, c.length));
      }
      return undefined;
   }

   private static deleteCookie(name: string) {
      document.cookie = name + '=;path=/;expires=-1;secure';
   }

   private addArgs(args: any) {
      if (args._exactArgs) {
         args._exactArgs = undefined;
         return;
      }
      if (!args.hasOwnProperty("accountId"))
         args.accountId = this.accountId;
      if (!args.hasOwnProperty("authToken"))
         args.authToken = this.sessionToken;
   }

   public sendTo(reqName: string, args: any, toEntityId: number, callback?: (result: any) => any) {
      this.addArgs(args);
      this.server.sendTo(reqName, args, toEntityId, callback);
   }

   public sendAny(reqName: string, args: any, callback?: (result: any) => any) {
      this.addArgs(args);
      this.server.send(reqName, args, callback);
   }

   public sendDirect(reqName: string, args: any, callback?: (result: any) => any) {
      this.addArgs(args);
      this.server.sendDirect(reqName, args, callback);
   }

   public addMessageHandler(message: string, handler: (msg: any) => void) {
      this.server.addMessageHandler(message, handler);
   }

   public removeMessageHandler(message: string, handler: (msg: any) => void) {
      this.server.removeMessageHandler(message, handler);
   }

   getErrorStrings(errorCode: any) {
      return this.server.getErrorStrings(errorCode);
   }
}

export const chatboxContext = ChatboxContext.Instance;

