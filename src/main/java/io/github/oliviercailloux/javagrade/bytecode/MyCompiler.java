package io.github.oliviercailloux.javagrade.bytecode;

import io.github.oliviercailloux.javagrade.bytecode.Compiler.CompilationResultExt;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface MyCompiler {
  public CompilationResultExt compile(Path compiledDir, Set<Path> javaPaths) throws IOException;
}
