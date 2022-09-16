package io.github.oliviercailloux.java_grade.bytecode;

import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResultExt;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface MyCompiler {
	public CompilationResultExt compile(Path compiledDir, Set<Path> javaPaths) throws IOException;
}
