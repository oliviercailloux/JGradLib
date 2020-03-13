package io.github.oliviercailloux.java_grade.ex.print_exec;

/**
 *
 * A solution to the exercice of the same name (except the class should be in
 * the default package).
 *
 */
public class PrintExec {
	public static void main(String[] args) {
		proceed(args);
//		proceed(new String[] { "Google/lib1/", "/home/user/Java/Google/other lib/", "GitHub/lib1/",
//				"MyProject/MySubProject/", "MyProject/", "MyMainClass", "true" });
//		proceed(new String[] { "MyProject/", "MyMainClass", "true" });
	}

	public static void proceed(String[] args) {
		final int length = args.length;
		final String lastArg = args[length - 1];
		final boolean useBin;
		final int lengthRestArgs;
		if (lastArg.equals("true")) {
			useBin = true;
			lengthRestArgs = length - 1;
		} else if (lastArg.equals("false")) {
			useBin = false;
			lengthRestArgs = length - 1;
		} else {
			lengthRestArgs = length;
			useBin = false;
		}
		final String className = args[lengthRestArgs - 1];
		final String folderName = args[lengthRestArgs - 2];

		final int lengthFolders = lengthRestArgs - 2;

		String classPathCompile = ".";
		for (int i = 0; i < lengthFolders; ++i) {
			classPathCompile += ":" + args[i];
		}

		final String destDirArg = useBin ? "-d 'bin' " : "";

		final String compile = "javac " + destDirArg + "-cp '" + classPathCompile + "' '" + folderName + className
				+ ".java'";
		System.out.println(compile);

		final String classPathExecute = classPathCompile + ":" + folderName + (useBin ? ":bin" : "");

		final String execute = "java -cp '" + classPathExecute + "' '" + className + "'";
		System.out.println(execute);
	}
}
