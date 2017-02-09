export class Property {
   key: string;
   secret: string;
   val: string;

   constructor(prop: any) {
      this.key = prop.key;
      this.secret = prop.secret;
      this.val = prop.val;

   }

   static compareProperties(a:Property, b:Property) {
      return a.key.localeCompare(b.key);
   }

}
