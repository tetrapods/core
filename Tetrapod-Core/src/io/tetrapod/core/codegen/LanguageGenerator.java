package io.tetrapod.core.codegen;

import java.io.IOException;
import java.util.List;

interface LanguageGenerator {

   void parseOption(List<String> components) throws ParseException;

   void generate(CodeGenContext context) throws IOException, ParseException;

}
