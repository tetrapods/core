export interface DialogCallback {
   submit:((result: any)=>void);
}

export default class DialogState<I,T> {
   isOpen: boolean;
   title?: string;
   body?: JSX.Element;
   item?: I;
   type?: T;
   dialogCallback?: DialogCallback;

   constructor() {
      this.isOpen = false;
   }

   submit(callback:(result: any)=>void) {
      if(this.dialogCallback) {
         this.dialogCallback.submit(callback);
      }
   }

   open(title:string, body:JSX.Element, item?:I, type?:T, dialogCallback?:DialogCallback) {
      this.title = title;
      this.body = body;
      this.item = item;
      this.type = type;
      this.dialogCallback = dialogCallback;
      return true;
   }

   close() {
      this.isOpen = false;
      this.title = undefined;
      this.body = undefined;
      this.item = undefined;
      this.type = undefined;
      this.dialogCallback = undefined;
      return false;
   }
}
