package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;

import java.io.*;
import java.util.List;

interface LanguageGenerator {

   void parseOption(File currentFile, TokenizedLine line, CodeGenContext currentContext) throws ParseException;

   void generate(List<CodeGenContext> contexts) throws IOException, ParseException;

}
