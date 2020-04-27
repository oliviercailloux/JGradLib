package io.github.oliviercailloux.samples.string_files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.google.common.collect.ImmutableList;

/**
 * <p>
 * This interface permits to deal with files whose paths are expressed using
 * <code>String</code>, instead of the more classical <code>Path</code>
 * instances (except for the method {@link #setReferenceFolder(Path)}). (Classes
 * implementing this interface will however typically use <code>Path</code>
 * instances internally to satisfy its contract.)
 * </p>
 * <p>
 * Instances of classes implementing this interface are associated to a
 * <em>reference</em> folder. Two methods of this interface (namely,
 * {@link #getAbsolutePath(String)} and
 * {@link #getContentUsingIso88591Charset(String)}), accept paths that are
 * relative to that reference folder. By default (when constructed), the
 * reference folder is the current folder.
 * </p>
 * <p>
 * Another method, {@link #getPathRelativeToReference(String)}, accepts a path
 * that is considered as relative to the <em>current</em> folder (not
 * necessarily the same as the reference folder associated to this instance,
 * thus).
 * </p>
 * <p>
 * All methods that accept string paths accept only relative paths (and reject
 * absolute paths). All such methods accept the string ".", or the empty string.
 * As usual, a path equal to ".", or empty, relative to a folder <em>f</em>,
 * simply represents the folder <em>f</em>.
 * </p>
 * <p>
 * All four methods of this interface systematically reject <code>null</code>
 * arguments.
 * </p>
 *
 */
public interface StringFilesUtils {
	/**
	 * Changes the reference folder.
	 *
	 * @param referenceFolder the new (relative or absolute) path to be considered
	 *                        as the <em>reference</em> folder for this instance. If
	 *                        this path is relative, it is considered as relative to
	 *                        the <em>current</em> folder.
	 * @return <code>true</code> iff the reference folder has changed as a result of
	 *         this call (if the new reference folder is simply another name for the
	 *         old reference, it does not count as a change).
	 * @throws IOException if an I/O error occurs
	 */
	public boolean setReferenceFolder(Path referenceFolder) throws IOException;

	/**
	 * Returns the absolute path corresponding to the path given as argument,
	 * considered as relative to the reference folder of this instance.
	 *
	 */
	public String getAbsolutePath(String pathRelativeToReference);

	/**
	 * Read all lines from the file represented by the given path, interpreted as
	 * relative to the current folder. This method ensures that the file is closed
	 * when all bytes have been read or an I/O error, or other runtime exception, is
	 * thrown. Bytes from the file are decoded into characters using the charset
	 * {@link StandardCharsets#ISO_8859_1}.
	 *
	 * <p>
	 * This method recognizes the following as line terminators:
	 * <ul>
	 * <li><code>&#92;u000D</code> followed by <code>&#92;u000A</code>, CARRIAGE
	 * RETURN followed by LINE FEED</li>
	 * <li><code>&#92;u000A</code>, LINE FEED</li>
	 * <li><code>&#92;u000D</code>, CARRIAGE RETURN</li>
	 * </ul>
	 * </p>
	 *
	 * @throws IOException if an I/O error occurs reading from the file or a
	 *                     malformed or unmappable byte sequence is read
	 *
	 */
	public ImmutableList<String> getContentUsingIso88591Charset(String pathRelativeToReference) throws IOException;

	/**
	 * Returns the relative path that, when considered as relative to the reference
	 * folder, corresponds to the path given as argument when it is considered
	 * relative to the current folder.
	 */
	public String getPathRelativeToReference(String pathRelativeToCurrent);
}
