package io.github.oliviercailloux.persons_manager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Permits to manage several persons. Before setting any persons explicitly, the set of managed
 * persons is empty.
 * <p>
 * This interface views the persons it manages mainly as a set (thus, without duplicates), but some
 * methods also permit to deal with redundant entries.
 * <p>
 * A person duplicates another one iff they have <em>both</em> the same id and name. In other words,
 * two persons having the same id but different names, or the same names but different ids, do not
 * count as duplicate.
 * <p>
 * A manager is linked to a redundancy counter.
 *
 * @see Person
 */
public interface PersonsManager {
	/**
	 * <p>
	 * Sets the persons that this instance manages. This replaces any persons previously set.
	 *
	 * @param persons not {@code null}, may contain non identical persons sharing an id.
	 */
	public void setPersons(List<Person> persons);

	/**
	 * Sets the persons that this instance manages as a “task force”, understood as a set of one or
	 * two persons. This replaces any persons previously set.
	 *
	 * @param persons not {@code null}, may contain non identical persons sharing an id.
	 * @throws IllegalArgumentException if zero or more than two persons are given.
	 */
	public void setTaskForce(Person... persons);

	/**
	 * Returns the number of unique persons that this instance manages, thus, not counting duplicates.
	 *
	 * @return a non-negative number.
	 */
	public int size();

	/**
	 * Indicates whether some managed person has that name
	 *
	 * @param name not {@code null}, not empty
	 * @return {@code true} iff at least one person managed by this instance has that name
	 */
	public boolean contains(String name);

	/**
	 * Indicates whether some managed person has that name.
	 * <p>
	 * This method does not close the given stream.
	 * <p>
	 * Hint: use the appropriate {@link String} constructor to decode an array of bytes.
	 *
	 * @param personNameAsStream not {@code null}, contains the name of a person encoded in
	 *        {@link StandardCharsets#UTF_8 UTF-8}.
	 * @return {@code true} iff at least one person managed by this instance has the name given in the
	 *         stream
	 * @throws IOException if an I/O error occurs
	 */
	public boolean contains(InputStream personNameAsStream) throws IOException;

	/**
	 * Precondition: the set of persons that this instance manages must have unique ids.
	 * <p>
	 * Returns an immutable copy of the persons this instance manages, as a mapping, using the
	 * person’s id as a key.
	 *
	 * @return not {@code null}
	 */
	public Map<Integer, Person> toMap();

	/**
	 * Returns an iterator over the set of persons this object manages. The iterator never exhibit
	 * duplicated persons. The iterator will accordingly iterate over {@link #size()} elements.
	 * <p>
	 * (The returned iterator need not support removal.)
	 *
	 * @return an iterator that iterates over the set of managed persons.
	 */
	public Iterator<Person> personIterator();

	/**
	 * Returns an iterator over the set of ids of persons this object manages. The iterator may
	 * exhibit duplicated ids, but when it does, the corresponding names differ. The iterator will
	 * accordingly iterate over {@link #size()} elements.
	 * <p>
	 * (The returned iterator need not support removal.)
	 *
	 * @return an iterator that iterates over the set of managed ids.
	 */
	public Iterator<Integer> idIterator();

	/**
	 * Returns an instance linked to this manager, that is able to indicate how many persons were
	 * redundant in the given list last time {@link #setPersons(List)} was called (when two persons in
	 * the list are equal, it counts for one redundant entry; when three persons are equal, it counts
	 * for two redundant entries; when two persons are equal and another three persons are equal, that
	 * makes three redundant entries…); and how many unique persons are present in this manager.
	 * <p>
	 * Both numbers are zero as long as {@link #setPersons(List)} is not called.
	 * <p>
	 * The returned counter is linked to this instance in the sense that it is not just a snapshot of
	 * the state of this manager at the time this method is called: when this manager later gets
	 * updated, any previously returned counter must also reflect its new state.
	 */
	public RedundancyCounter getRedundancyCounter();

	/**
	 * &#x52&#x65&#x74&#x75&#x72&#x6e&#x73&#x20&#x74&#x68&#x65&#x20
	 * &#x73&#x74&#x72&#x69&#x6E&#x67&#x20
	 * <code>&#x50&#x65&#x72&#x73&#x6f&#x6e&#x73&#x4d&#x61&#x6e&#x61&#x67&#x65&#x72
	 * &#x20&#x77&#x69&#x74&#x68&#x20<em>&#x78</em>
	 * &#x20&#x65&#x6e&#x74&#x72&#x69&#x65&#x73</code>&#x2c
	 * &#x20&#x77&#x68&#x65&#x72&#x65&#x20<em>&#x78</em>&#x20&#x69&#x73&#x20
	 * &#x74&#x68&#x65&#x20&#x6e&#x75&#x6d&#x62&#x65&#x72&#x20&#x6f&#x66&#x20
	 * &#x75&#x6e&#x69&#x71&#x75&#x65&#x20&#x70&#x65&#x72&#x73&#x6f&#x6e&#x20&#x69&#x6e
	 * &#x20&#x74&#x68&#x69&#x73&#x20&#x6d&#x61&#x6e&#x61&#x67&#x65&#x72&#x2e
	 *
	 * @return a short string describing this instance.
	 */
	@Override
	public String toString();
}
