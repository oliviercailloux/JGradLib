package io.github.oliviercailloux.personsmanager;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * This class should be instanciated using one of its static factory method (to be created). One is
 * named “empty”, admits no parameters, and returns a manager that “manages” an empty set of
 * persons; the other one is named “given”, admits an iterable instance of persons as a parameter,
 * and uses it to initialize the set of persons that the returned instance will manage (this also
 * initializes correspondingly the redundancy counter).
 *
 */
class MyPersonsManager implements PersonsManager {

  private static class MyRedundancyCounter implements RedundancyCounter {
    private MyPersonsManager manager;

    private MyRedundancyCounter(MyPersonsManager manager) {
      this.manager = checkNotNull(manager);
    }

    @Override
    public int getRedundancyCount() {
      return manager.lastListSize - manager.size();
    }

    @Override
    public int getUniqueCount() {
      return manager.size();
    }
  }

  public static PersonsManager empty() {
    return new MyPersonsManager();
  }

  public static PersonsManager given(Iterable<Person> persons) {
    final MyPersonsManager manager = new MyPersonsManager();
    manager.setPersons(ImmutableList.copyOf(persons));
    return manager;
  }

  private Set<Person> persons;
  private int lastListSize;

  MyPersonsManager() {
    persons = new LinkedHashSet<>();
    lastListSize = 0;
  }

  @Override
  public void setPersons(List<Person> persons) {
    this.persons = new LinkedHashSet<>();
    this.persons.addAll(persons);
    lastListSize = persons.size();
  }

  @Override
  public void setTaskForce(Person... persons) {
    final ImmutableList<Person> personsList = ImmutableList.copyOf(persons);
    checkArgument(personsList.size() == 1 || personsList.size() == 2);
    setPersons(personsList);
  }

  @Override
  public int size() {
    return persons.size();
  }

  @Override
  public boolean contains(String name) {
    return persons.stream().anyMatch(p -> p.getName().equals(name));
  }

  @Override
  public boolean contains(InputStream personNameAsStream) throws IOException {
    return contains(new String(personNameAsStream.readAllBytes(), StandardCharsets.UTF_8));
  }

  @Override
  public Map<Integer, Person> toMap() {
    return persons.stream().collect(ImmutableMap.toImmutableMap(Person::getId, p -> p));
  }

  @Override
  public Iterator<Person> personIterator() {
    return persons.iterator();
  }

  @Override
  public Iterator<Integer> idIterator() {
    return Iterators.transform(persons.iterator(), Person::getId);
  }

  @Override
  public RedundancyCounter getRedundancyCounter() {
    return new MyRedundancyCounter(this);
  }

  @Override
  public String toString() {
    return "PersonsManager with " + size() + " entries";
  }
}
