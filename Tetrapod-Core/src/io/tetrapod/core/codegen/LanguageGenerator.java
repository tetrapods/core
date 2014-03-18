package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;

import java.io.IOException;

interface LanguageGenerator {

   void parseOption(TokenizedLine line) throws ParseException;

   void generate(CodeGenContext context) throws IOException, ParseException;

}
