package io.github.oliviercailloux.java_grade.ex_extractor;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.primitives.Booleans;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExExtractorGrader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExExtractorGrader.class);

	public ExExtractorGrader() {
		mavenAbsoluteRoot = null;
	}

	Path mavenAbsoluteRoot;

	Mark writeMark() {
		final Optional<SimpleExtractor> inst = newInstance();
		if (!inst.isPresent()) {
			return Mark.zero("Could not instanciate SimpleExtractor implementation.");
		}
		final SimpleExtractor instance = inst.get();
		LOGGER.info("Instantiated: {}.", instance.getClass().getName());

		final boolean testWhite;
		final boolean testNull;
		final boolean testNoSpuriousClose;
		final boolean testSimple;
		final boolean testThrows;
		final boolean testClosesOwn;
		final boolean testRestoredDefault;
		final boolean testExplicitStripper;
		final StringBuilder commentBuilder = new StringBuilder();

		try (InputStream input = getClass().getResourceAsStream("White.pdf")) {
			assert input != null;
			@SuppressWarnings("resource")
			final MyStringWriter output = new MyStringWriter();
			Throwable thrown = null;
			try {
				instance.writeText(input, output);
			} catch (Throwable t) {
				thrown = t;
			}
			final String obtained = output.toString();
			testWhite = obtained.equals("") && thrown == null;
			testNoSpuriousClose = !output.hasBeenClosed();
			commentBuilder.append("White: ");
			if (testWhite) {
				commentBuilder.append("Ok");
			} else if (thrown != null) {
				commentBuilder.append("KO – thrown " + thrown.toString());
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
			commentBuilder.append("\nDoes not close writer: ");
			if (testNoSpuriousClose) {
				commentBuilder.append("Ok");
			} else {
				commentBuilder.append("KO – writer has been closed.");
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try {
			final StringWriter output = new StringWriter();
			final InputStream input = null;
			instance.writeText(input, output);
			final String obtained = output.toString();
			testNull = obtained.equals("");
			commentBuilder.append("\nNull input: ");
			if (testNull) {
				commentBuilder.append("Ok");
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
			final StringWriter output = new StringWriter();
			Throwable thrown = null;
			try {
				instance.writeText(input, output);
			} catch (Throwable t) {
				thrown = t;
			}
			final String obtained = output.toString();
			testSimple = obtained.equals("Hé ̸=\n");
			commentBuilder.append("\nHé: ");
			if (testSimple) {
				commentBuilder.append("Ok");
			} else if (thrown != null) {
				commentBuilder.append("KO – thrown " + thrown.toString());
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
			final StringWriter output = new StringWriter();
			instance.setStripper(throwingStripper());
			boolean thrown = false;
			final int openBefore = JavaMarkHelper.getOpenFileDescriptorCount();
			try {
				instance.writeText(input, output);
			} catch (IOException e) {
				assert e.getMessage().equals("Ad-hoc exception");
				thrown = true;
			}
			final int openAfter = JavaMarkHelper.getOpenFileDescriptorCount();
			final String obtained = output.toString();
			if (!thrown && !obtained.toString().isEmpty()) {
				throw new GradingException(
						String.format("Did not throw, but yet obtained text: '%s'.", obtained));
			}
			testThrows = thrown;
			commentBuilder.append("\nThrows: ");
			if (testThrows) {
				commentBuilder.append("Ok");
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
			testClosesOwn = (openAfter == openBefore);
			commentBuilder.append("\nCloses own streams: ");
			if (testClosesOwn) {
				commentBuilder.append("Ok");
			} else {
				commentBuilder.append("KO");
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
			final StringWriter output = new StringWriter();
			instance.setStripper(null);
			Throwable thrown = null;
			try {
				instance.writeText(input, output);
			} catch (Throwable t) {
				thrown = t;
			}
			final String obtained = output.toString();
			testRestoredDefault = obtained.equals("Hé ̸=\n");
			commentBuilder.append("\nRestored default: ");
			if (testRestoredDefault) {
				commentBuilder.append("Ok");
			} else if (thrown != null) {
				commentBuilder.append("KO – thrown " + thrown.toString());
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
			final StringWriter output = new StringWriter();
			instance.setStripper(new PDFTextStripper());
			Throwable thrown = null;
			try {
				instance.writeText(input, output);
			} catch (Throwable t) {
				thrown = t;
			}
			final String obtained = output.toString();
			testExplicitStripper = obtained.equals("Hé ̸=\n");
			commentBuilder.append("\nExplicit stripper: ");
			if (testExplicitStripper) {
				commentBuilder.append("Ok");
			} else if (thrown != null) {
				commentBuilder.append("KO – thrown " + thrown.toString());
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}

		double fracPoints = 0d;
		if ((testSimple || testExplicitStripper) && testNoSpuriousClose) {
			fracPoints += 1d / 8d;
		}
		if (testSimple && testWhite) {
			fracPoints += 1d / 8d;
		}
		if (testThrows && testClosesOwn) {
			if (testSimple) {
				fracPoints += 1d / 2d;
			} else if (testExplicitStripper) {
				fracPoints += 3d / 8d;
			}
		}
		if (testSimple && testThrows && testClosesOwn && testRestoredDefault) {
			fracPoints += 1d / 4d;
		} else if (testExplicitStripper && testThrows && testClosesOwn) {
			fracPoints += 1d / 8d;
		}
		final Mark mark = Mark.given(fracPoints, commentBuilder.toString());
		return mark;
	}

	private PDFTextStripper throwingStripper() throws IOException {
		final IOException ioException = new IOException("Ad-hoc exception");
		return new PDFTextStripper() {
			@Override
			public String getText(PDDocument doc) throws IOException {
				throw ioException;
			}

			@Override
			public void writeText(PDDocument doc, Writer outputStream) throws IOException {
				throw ioException;
			}

			@Override
			public void showTextString(byte[] string) throws IOException {
				throw ioException;
			}

			@Override
			public void showTextStrings(COSArray array) throws IOException {
				throw ioException;
			}
		};
	}

	private Optional<Class<?>> getNamedClass(String simpleName) {
		requireNonNull(simpleName);
		URL url;
		try {
			url = mavenAbsoluteRoot.resolve("target/classes").toUri().toURL();
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
		final Class<?> classToLoad;
		try (URLClassLoader child =
				new URLClassLoader(new URL[] {url}, this.getClass().getClassLoader())) {
			final ClassGraph graph =
					new ClassGraph().overrideClassLoaders(child).enableAllInfo().whitelistPackages("*");
			final String name;
			try (ScanResult scanResult = graph.scan()) {
				final ClassInfoList classes = scanResult.getAllStandardClasses();
				LOGGER.debug("Size all: {}.", classes.size());
				final ImmutableList<ClassInfo> extractorUserClasses =
						classes.stream().filter((i) -> i.getName().endsWith("." + simpleName))
								.collect(ImmutableList.toImmutableList());
				// for (ClassInfo info : classes) {
				// LOGGER.info("Name: {}.", info.getName());
				// }
				if (extractorUserClasses.size() >= 2) {
					throw new GradingException("Multiple impl.");
				}
				if (extractorUserClasses.size() == 1) {
					final ClassInfo classInfo = extractorUserClasses.iterator().next();
					name = classInfo.getName();
					checkState(classInfo.getSimpleName().equals(simpleName));
				} else {
					name = "";
				}
			}

			final Optional<Class<?>> opt;
			if (name.equals("")) {
				opt = Optional.empty();
			} else {
				classToLoad = Class.forName(name, true, child);
				LOGGER.debug("Class: {}.", classToLoad.getCanonicalName());
				opt = Optional.of(classToLoad);
			}
			return opt;
		} catch (IOException | SecurityException | ClassNotFoundException e) {
			throw new GradingException(e);
		}
	}

	@SuppressWarnings("unused")
	private Mark userWriteToFileMark() {
		final Optional<Class<?>> namedClass = getNamedClass("ExtractorUser");
		if (namedClass.isEmpty()) {
			return Mark.zero();
		}

		final String methodName = "writeTextToFile";
		final Class<?> cls = namedClass.get();
		final Optional<Method> methodOpt = getMethod(cls, methodName);

		final boolean writtenOk;
		final boolean throwsIOE;
		final String comment;
		if (methodOpt.isPresent()) {
			final Method method = methodOpt.get();
			final ImmutableSet<Class<?>> exc = ImmutableSet.copyOf(method.getExceptionTypes());
			throwsIOE = exc.contains(IOException.class);
			try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
				final Path outPath = fs.getPath("out.txt");
				try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
					method.invoke(method, input, outPath);
				}
				if (!Files.exists(outPath)) {
					writtenOk = false;
				} else {
					final String written = Files.readString(outPath);
					writtenOk = written.equals("Hé ≠\n");
					if (!writtenOk) {
						throw new GradingException(
								String.format("Written, but not the right content: '%s'", written));
					}
				}
			} catch (IOException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new IllegalStateException(e);
			}
			comment = (writtenOk ? "" : "No file written; ")
					+ (throwsIOE ? "IOException declared ok" : "Fails to declare IOException");
		} else {
			writtenOk = false;
			throwsIOE = false;
			comment = "Method ExtractorUser#" + methodName + " not found";
		}

		return Mark.given(Booleans.countTrue(writtenOk, throwsIOE) / 2d, comment);

	}

	private Optional<Method> getMethod(Class<?> cls, String methodName) {
		final Method[] methods = cls.getMethods();
		final ImmutableList<Method> writeFileMethods = Stream.of(methods)
				.filter((m) -> m.getName().equals(methodName)).collect(ImmutableList.toImmutableList());
		if (writeFileMethods.size() >= 2) {
			throw new GradingException("Multiple methods.");
		}
		final Optional<Method> methodOpt;
		if (writeFileMethods.size() == 1) {
			methodOpt = Optional.of(writeFileMethods.iterator().next());
		} else {
			methodOpt = Optional.empty();
		}
		return methodOpt;
	}

	private Optional<SimpleExtractor> newInstance() {
		LOGGER.debug("Start new inst.");
		URL url;
		try {
			url = mavenAbsoluteRoot.resolve("target/classes").toUri().toURL();
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
		try (URLClassLoader child =
				new URLClassLoader(new URL[] {url}, this.getClass().getClassLoader())) {
			final ClassGraph graph =
					new ClassGraph().overrideClassLoaders(child).enableAllInfo().whitelistPackages("*");
			final String name;
			try (ScanResult scanResult = graph.scan()) {
				final ClassInfoList impl =
						scanResult.getClassesImplementing(SimpleExtractor.class.getName());
				if (impl.size() >= 2) {
					throw new GradingException("Multiple impl.");
				}
				if (impl.size() == 1) {
					name = impl.iterator().next().getName();
				} else {
					name = "";
				}
				final ClassInfoList cls = scanResult.getAllClasses();
				LOGGER.debug("Size all: {}.", cls.size());
				for (ClassInfo info : cls) {
					LOGGER.debug("Name: {}.", info.getName());
				}
			}
			if (name.equals("")) {
				return Optional.empty();
			}
			/**
			 * Probably doesn’t work when loading a second class having the same name?
			 */
			final Class<? extends SimpleExtractor> classToLoad =
					Class.forName(name, true, child).asSubclass(SimpleExtractor.class);
			LOGGER.debug("Class: {}.", classToLoad.getCanonicalName());
			final Constructor<? extends SimpleExtractor> constructor = classToLoad.getConstructor();
			final SimpleExtractor created = constructor.newInstance();
			return Optional.of(created);
		} catch (IOException | NoSuchMethodException | SecurityException | ClassNotFoundException
				| InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new GradingException(e);
		}
	}
}
