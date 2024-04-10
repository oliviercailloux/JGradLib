package io.github.oliviercailloux.personsmanager;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

  public static PersonsManager empty() {
    return new MyPersonsManager();
  }

  public static PersonsManager given(Iterable<Person> persons) {
    final MyPersonsManager manager = new MyPersonsManager();
    manager.setPersons(ImmutableList.copyOf(persons));
    return manager;
  }

  @Override
  public void setPersons(List<Person> persons) {
    //
  }

  @Override
  public void setTaskForce(Person... persons) {
    //
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public boolean contains(String name) {
    return false;
  }

  @Override
  public boolean contains(InputStream personNameAsStream) throws IOException {
    return true;
  }

  @Override
  public Map<Integer, Person> toMap() {
    return null;
  }

  @Override
  public Iterator<Person> personIterator() {
    return null;
  }

  @Override
  public Iterator<Integer> idIterator() {
    return null;
  }

  @Override
  public RedundancyCounter getRedundancyCounter() {
    return new RedundancyCounter() {

      @Override
      public int getUniqueCount() {
        return 0;
      }

      @Override
      public int getRedundancyCount() {
        return 0;
      }
    };
  }
}
