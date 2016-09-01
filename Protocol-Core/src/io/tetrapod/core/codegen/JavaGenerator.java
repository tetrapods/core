package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;
import io.tetrapod.core.codegen.CodeGenContext.Class;
import io.tetrapod.core.codegen.CodeGenContext.ClassLike;
import io.tetrapod.core.codegen.CodeGenContext.Err;
import io.tetrapod.core.codegen.CodeGenContext.Field;

import java.io.*;
import java.util.*;

class JavaGenerator implements LanguageGenerator {

   private String outputDir;
   private String packageName;

   @Override
   public void parseOption(File currentFile, TokenizedLine line, CodeGenContext context) throws ParseException {
      if (!line.parts.get(0).equals("java"))
         return;
      String opt = line.parts.get(1);
      String val = line.parts.get(2);
      switch (opt) {
         case "package":
            context.serviceAnnotations.add("java.package", val);
            break;
         case "outdir":
            context.serviceAnnotations.add("java.outdir", new File(currentFile.getParent(), val).getPath());
            break;
         default:
            throw new ParseException("unknown java option");
      }
   }

   @Override
   public void generate(List<CodeGenContext> contexts) throws IOException, ParseException {
      for (CodeGenContext c : contexts)
         generate(c);
   }

   private void generate(CodeGenContext context) throws IOException, ParseException {
      outputDir = context.serviceAnnotations.getFirst("java.outdir");
      packageName = context.serviceAnnotations.getFirst("java.package");
      boolean allSync = context.serviceAnnotations.has("sync");
      boolean someAsync = hasSomeAsync(allSync, context.classes);
      for (File f : getFilename("c").getParentFile().listFiles()) {
         f.delete();
      }
      for (Class c : context.classes) {
         generateClass(c, context.serviceName + "Contract", context, allSync);
      }
      for (String name : context.enums.keySet()) {
         generateEnum(context.enums.get(name));
      }
      for (String name : context.flags.keySet()) {
         generateFlags(context.flags.get(name));
      }

      generateContract(context);
   }

   private boolean hasSomeAsync(boolean allSync, ArrayList<Class> classes) {
      if (allSync) {
         return false;
      }
      for (Class c : classes) {
         if (!c.annotations.has("sync")) {
            return true;
         }
      }
      return false;
   }

   private void generateContract(CodeGenContext context) throws IOException, ParseException {
      Template t = template("contract");
      String theClass = context.serviceName + "Contract";
      String serviceId = context.serviceAnnotations.getFirst("id");
      String subContractId = context.serviceAnnotations.getFirst("subid");
      if (subContractId == null) {
         subContractId = "1";
      }
      t.add("class", theClass);
      t.add("package", context.serviceAnnotations.getFirst("java.package"));
      t.add("version", context.serviceAnnotations.getFirst("version"));
      t.add("name", context.serviceName);
      if (serviceId.equals("dynamic")) {
         t.add("contractId", "Contract.UNASSIGNED");
         t.add("contractIdVolatile", "volatile");
         t.add("contractIdSet", theClass + ".CONTRACT_ID = id;");
         throw new ParseException("dynamic contract id's not supported yet");
      } else {
         t.add("contractId", serviceId);
         t.add("subContractId", subContractId);
         t.add("contractIdVolatile", "final");
         t.add("contractIdSet", "");
      }

      for (Field f : context.globalConstants) {
         String path = f.annotations.getFirst("webapi");
         if (path != null) {
            if (path.isEmpty())
               path = f.defaultValue;
            if (f.annotations.has("root")) {
               path = '/' + path;
            } else {
               path = '/' + context.serviceAnnotations.getFirst("web") + '/' + path;
            }
            // HACK this is horrible -- hard coding WebAPIRequest for requestClass... 
            Template sub = template("contract.webroutes.call").add("path", path)
                  .add("requestClass", "io.tetrapod.protocol.core.WebAPIRequest").add("contractClass", theClass);
            t.add("webRoutes", sub.expand());
         }
      }

      for (String sub : context.subscriptions)
         t.add("subscriptions", genSubscriptions(context, sub, theClass));

      for (Class c : context.classesByType("request")) {
         t.add("handlers", ", " + c.classname() + ".Handler", "\n");
         String path = c.annotations.getFirst("web");
         if (path != null) {
            if (path.isEmpty())
               path = Character.toLowerCase(c.name.charAt(0)) + c.name.substring(1);

            String prePath = "/";
            for (String p : context.serviceAnnotations.get("web")) {
               prePath += p + '/';
            }
            path = prePath  + path;

            Template sub = template("contract.webroutes.call").add("path", path).add("requestClass", c.classname())
                  .add("contractClass", theClass);
            t.add("webRoutes", sub.expand());
         }
      }
      for (Class c : context.classes) {
         t.add(c.type + "Adds", template("contract.adds.call").add("class", c.classname()));
      }

      t.add("classcomment", generateComment(context.serviceComment));
      addErrors(context.allErrors, true, context.serviceName, t);
      addConstantValues(context.globalConstants, t);
      t.expandAndTrim(getFilename(theClass));
   }

   private Class getResponse(Class request, CodeGenContext context) {
      Collection<Class> responses = context.classesByType("response");
      for (Class response : responses) {
         if (response.name.equals(request.name)) {
            return response;
         }
      }
      return null;
   }

   private String genSubscriptions(CodeGenContext context, String subscription, String enclosingClass) throws IOException {
      Template t = template("contract.subscription");
      t.add("name", subscription);
      t.add("enclosingClass", enclosingClass);
      for (Class c : context.classesByType("message")) {
         if (subscription.equals(c.subscription)) {
            t.add("handlers", c.classname() + ".Handler", ",\n");
            t.add("adds", template("contract.adds.call").add("class", c.classname()));
         }
      }
      return t.expand();
   }

   private void generateClass(Class c, String serviceName, CodeGenContext context, boolean protocolSync) throws IOException, ParseException {
      boolean sync = protocolSync || c.annotations.has("sync");
      Template t = template(c.type.toLowerCase());
      t.add("rawname", c.name);
      t.add("class", c.classname());
      t.add("package", packageName);
      t.add("security", c.security.toUpperCase());
      t.add("dispatchFuncName", sync?"dispatch":"dispatchTask");
      t.add("taskPrefix", sync?"":"Task.from(");
      t.add("taskSuffix", sync?"":")");
      t.add("taskImport", sync?"":"import io.tetrapod.core.tasks.Task;\n");
      t.add("classcomment", generateComment(c.comment));
      t.add("maxtag", "" + c.maxTag());
      t.add("structid", c.getStructId());
      t.add("service", serviceName);
      t.add("securityCheck", generateSecurityCheck(c));
      Template t2 = template("sensitivity.method");
      boolean sensitivityUsed = false;
      for (Field f : c.fields) {
         if (f.annotations.has("sensitive")) {
            Template t3 = template("sensitivity.line");
            t2.add("sensitivityLine", t3.add("name", f.name));
            sensitivityUsed = true;
         }
      }
      if (sensitivityUsed) {
         t.add("sensitivityCheck", t2);
      }
      addFieldValues(c.fields, t);
      addConstantValues(c.fields, t);
      addErrors(c.errors, false, serviceName, t);
      if (c.type.equals("request")) {
         Class resp = getResponse(c, context);
         String typePrefix = sync?"":"Task";
         if (resp != null) {
            t.add("requestGenerics", typePrefix+"RequestWithResponse<" + resp.classname() + ">");
            t.add("responseClassType", sync?"Response":"Task<"+resp.classname()+">");
         } else {
            t.add("requestGenerics", typePrefix+"Request");
            t.add("responseClassType", sync?"Response":"Task<Response>");
         }
         t.add("genericResponseClassType", sync?"Response":"Task<? extends Response>");
      }
      t.expandAndTrim(getFilename(c.classname()));
   }

   private void addErrors(Collection<Err> errors, boolean globalScope, String serviceName, Template global) throws IOException,
         ParseException {
      for (Err err : errors) {
         Template t = template("field.errors");
         t.add("name", err.name);
         int hash = err.getValue();
         t.add("hash", "" + hash);
         t.add("service", serviceName);
         String[] lines = t.expand().split("\r\n|\n|\r");
         String line = globalScope ? lines[0] : lines[1];
         global.add("errors", generateComment(err.comment) + line);
      }
   }

   private void addFieldValues(List<Field> fields, Template global) throws ParseException, IOException {
      int instanceFields = 0;
      for (Field f : fields) {
         if (f.isConstant())
            continue;
         instanceFields++;
         Template sub = getFieldTemplate(f);
         String[] lines = sub.expand().split("\r\n|\n|\r");
         String comment = "";
         if (f.comment != null && !f.comment.trim().isEmpty()) {
            comment = generateComment(f.comment.trim());
         }
         String webName = f.getWebName();
         if (webName != null)
            global.add("webNames", template("struct.webnames").add("tag", f.tag).add("name", webName));
         global.add("field-declarations", comment + lines[0]);
         global.add("field-defaults", lines[1]);
         global.add("field-reads", lines[2]);
         global.add("field-writes", lines[3]);
         global.add("inline-declarations", lines[4], ", ");
         global.add("inline-initializers", lines[5]);
         global.add("description-fields", template("struct.description").add(sub));
         global.add("struct-equals", makeStructEquals(f, sub));
         global.add("struct-hashcode", makeStructHashcode(f, sub));
      }
      if (instanceFields > 0) {
         global.add("full-constructor", template("full.constructor").add(global));
      }
   }

   private Template makeStructEquals(Field field, Template sub) throws IOException {
      if (field.collectionType != null && field.collectionType.equals("<array>"))
         return template("field.equals.array").add(sub);
      else if (JavaTypes.get(field.type).isPrimitive && !field.type.equals("string"))
         return template("field.equals.primitive").add(sub);
      else
         return template("field.equals.object").add(sub);
   }

   private Template makeStructHashcode(Field field, Template sub) throws IOException {
      if (field.collectionType != null && field.collectionType.equals("<array>"))
         return template("field.hashcode.array").add(sub);
      if (field.collectionType != null && field.collectionType.equals("<list>"))
         return template("field.hashcode.object").add(sub);
      else if (JavaTypes.get(field.type).isPrimitive && field.type.equals("long"))
         return template("field.hashcode.long").add(sub);
      else if (JavaTypes.get(field.type).isPrimitive && field.type.equals("boolean"))
         return template("field.hashcode.boolean").add(sub);
      else if (JavaTypes.get(field.type).isPrimitive && field.type.equals("double"))
         return template("field.hashcode.double").add(sub);
      else if (JavaTypes.get(field.type).isPrimitive && !field.type.equals("string"))
         return template("field.hashcode.primitive").add(sub);
      else
         return template("field.hashcode.object").add(sub);
   }

   private void addConstantValues(List<Field> fields, Template global) throws ParseException, IOException {
      checkForDupes(fields);
      for (Field f : fields) {
         if (!f.isConstant())
            continue;
         Template sub = getFieldTemplate(f);
         String[] lines = sub.expand().split("\r\n|\n|\r");
         String comment = "";
         if (f.comment != null && !f.comment.trim().isEmpty()) {
            comment = generateComment(f.comment.trim());
         }
         global.add("constants", comment + lines[0]);
      }
   }

   private void checkForDupes(List<Field> fields) throws ParseException{
      if (fields != null && fields.size() > 0) {
         Set<String> dupeCheckerName = new HashSet<>(fields.size());
         Set<String> dupeCheckerValue = new HashSet<>(fields.size());
         for (Field field : fields) {
            if (!dupeCheckerName.add(field.name)) {
               throw new ParseException("trying to add field " + field.name + " more then once");
            }
            if (field.isEnum() && !dupeCheckerValue.add(field.defaultValue)) {
               throw new ParseException("trying to add enum field " + field.name + " with a value " + field.defaultValue + " that's in use");
            }
         }
      }
   }

   private void addAllAsConstantValues(List<Field> fields, Template global) throws ParseException, IOException {
      checkForDupes(fields);
      for (Field f : fields) {
         Template sub = template("field.constants");
         sub.add("default", f.defaultValue);
         sub.add("name", f.name);
         JavaTypes.Info info = JavaTypes.get(f.type);
         sub.add("javatype", info != null ? info.base : f.type);
         String[] lines = sub.expand().split("\r\n|\n|\r");
         String comment = "";
         if (f.comment != null && !f.comment.trim().isEmpty()) {
            comment = generateComment(f.comment.trim());
         }
         global.add("constants", comment + (f.isEnum() ? lines[4] : lines[0]));
      }
   }

   private Template getFieldTemplate(Field f) throws IOException {
      JavaTypes.Info info = JavaTypes.get(f.type);
      String defaultVal = f.getEscapedDefaultValue();
      if (defaultVal == null)
         defaultVal = info.defaultValue;
      String primTemplate = "field.primitives";
      String structTemplate = "field.structs";
      String descContractId = "0";
      String descStructId = "0";
      String descType = "";
      String descArray = "";
      String interiorTypePrim = "";
      String interiorType = "";
      if (f.collectionType != null) {
         defaultVal = "null";
         switch (f.collectionType) {
            case "<array>":
               primTemplate = "field.array.primitives";
               structTemplate = "field.array.structs";
               descArray = "_LIST";
               break;
            case "<list>":
               primTemplate = "field.list.primitives";
               structTemplate = "field.list.structs";
               descArray = "_LIST";
               break;
         }
      }
      if (f.isConstant()) {
         primTemplate = structTemplate = "field.constants";
      }
      switch (f.type) {
         case "int":
            descType = "TypeDescriptor.T_INT";
            break;
         case "long":
            descType = "TypeDescriptor.T_LONG";
            break;
         case "boolean":
            descType = "TypeDescriptor.T_BOOLEAN";
            break;
         case "double":
            descType = "TypeDescriptor.T_DOUBLE";
            break;
         case "byte":
            descType = "TypeDescriptor.T_BYTE";
            break;
         case "string":
            descType = "TypeDescriptor.T_STRING";
            break;
         default:
            descType += "TypeDescriptor.T_STRUCT";
            descContractId = f.type + ".CONTRACT_ID";
            descStructId = f.type + ".STRUCT_ID";
            break;
      }
      if (f.interiorType != null) {
         interiorType = f.interiorType.replace('.', '_');
         interiorTypePrim = f.interiorType.substring(f.interiorType.indexOf('.') + 1);
         if (f.collectionType == null) {
            primTemplate = structTemplate = f.interiorType.startsWith("flags") ? "field.flags" : "field.enum";
         } else if (f.interiorType.startsWith("enum") && f.collectionType.equals("<list>")) {
            primTemplate = structTemplate = "field.list.enum";
         } else {
            primTemplate = structTemplate = f.interiorType.startsWith("flag") ? "field.array.flags" : "field.array.enum";
         }
         switch (interiorTypePrim) {
            case "int":
               descType = "TypeDescriptor.T_INT";
               break;
            case "long":
               descType = "TypeDescriptor.T_LONG";
               break;
            case "string":
               descType = "TypeDescriptor.T_STRING";
               break;
         }
         descContractId = "0";
         descStructId = "0";
      }
      Template t = template(info.isPrimitive ? primTemplate : structTemplate);
      t.add("tag", f.tag);
      t.add("default", defaultVal);
      t.add("name", f.name);
      t.add("javatype", info.base);
      t.add("interiortype", interiorType);
      t.add("interiortypePrim", interiorTypePrim);
      t.add("type", f.type);
      t.add("boxed", info.boxed);
      t.add("descType", descType + descArray);
      t.add("descContractId", descContractId);
      t.add("descStructId", descStructId);
      return t;
   }

   private File getFilename(String classname) {
      File f = new File(outputDir);
      for (String p : packageName.split("\\.")) {
         f = new File(f, p);
      }
      f.mkdirs();
      return new File(f, classname + ".java");
   }

   private String generateComment(String comment) {
      if (comment == null)
         return "";
      comment = comment.trim();
      if (comment.isEmpty())
         return "";
      StringBuilder sb = new StringBuilder();
      sb.append("\n/**\n * " + comment + "\n */\n");
      return sb.toString();
   }

   private String generateSecurityCheck(Class c) throws IOException, ParseException {
      if (!c.type.equals("request"))
         return "";
      switch (c.security) {
         case "protected":
         case "admin":
            String authId = "accountId";
            String authToken = "authToken";
            String adminRights = "0";
            int m = 0;
            for (Field f : c.fields) {
               if (f.annotations.has("authId")) {
                  authId = f.name;
               }
               if (f.annotations.has("authToken")) {
                  authToken = f.name;
               }
               if (f.name.equals(authToken)) {
                  f.annotations.add("sensitive", "");
               }
               if (f.name.equals(authId) || f.name.equals(authToken)) {
                  m++;
               }
            }
            if (c.annotations.has("rights")) {
               adminRights = c.annotations.getFirst("rights");
            }
            if (m != 2)
               throw new ParseException(c.name + " is " + c.security
                     + " and must have @authId and @authToken fields (or default accountId and authToken)");
            return template("request.security").add("authId", authId).add("authToken", authToken).add("adminRights", adminRights).expand();
         default:
            return "";
      }
   }

   public static String escapeString(String s) {
      s = s.replace("\\", "\\\\");
      s = s.replace("\"", "\\\"");
      return "\"" + s + "\"";
   }

   private Template template(String name) throws IOException {
      return Template.get(getClass(), "/templates/javatemplates/" + name + ".template");
   }

   private void generateFlags(ClassLike c) throws IOException, ParseException {
      Template t = template("flags");
      t.add("class", c.name);
      t.add("package", packageName);
      t.add("type", c.fields.get(0).type);
      addAllAsConstantValues(c.fields, t);
      t.expandAndTrim(getFilename(c.name));
   }

   private void generateEnum(ClassLike c) throws IOException, ParseException {
      checkForDupes(c.fields);
      Template t = template("enum");
      t.add("class", c.name);
      t.add("package", packageName);
      String type = c.fields.get(0).type;
      if (type.equalsIgnoreCase("string")) {
         t.add("type", "String");
         t.add("equals", ".equals");
         t.add("outRangeVal", "\"__out_of_range__\"");
         for (Field f : c.fields) {
            f.defaultValue = "\"" + f.defaultValue + "\"";
         }
      } else {
         t.add("type", type);
         t.add("equals", " == ");
         t.add("outRangeVal", "-99");
      }
      addAllAsConstantValues(c.fields, t);
      t.expandAndTrim(getFilename(c.name));
   }
}
