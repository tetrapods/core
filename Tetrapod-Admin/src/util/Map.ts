export default class Map<T> {
   private items: {[key: string]: T};

   constructor() {
      this.items = {};
   }

   set(key: string, value: T): void {
      this.items[key] = value;
   }

   has(key: string): boolean {
      return key in this.items;
   }

   get(key: string): T {
      return this.items[key];
   }

   delete(key: string): T {
      let T = this.items[key];
      delete this.items[key];
      return T;
   }

   keys(): string[] {
      const array: string[] = [];
      for (const name in this.items) {
         array.push(name);
      }
      return array;
   }

   values(): T[] {
      const array: T[] = [];
      for (const name in this.items) {
         array.push(this.items[name]);
      }
      return array;
   }
}
