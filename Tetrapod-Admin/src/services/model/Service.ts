export class Service {
   public entityId:number;
   public name: string;
   public status: number;
   public hostname: string;
   public build: string;
   public removed: boolean;

   constructor(entity: any) {
      this.entityId = entity.entityId;
      this.name = entity.name;
      this.hostname = entity.hostname;
      this.build = entity.build;
   }

   public isGone() {}

   clearErrors() {

   }

   public subscribe(attempt:number){

   }

   public static compareServices(a:Service, b:Service):number {
      return (a.entityId - b.entityId);
   }

   statsUpdate(msg: any) {

   }
}
