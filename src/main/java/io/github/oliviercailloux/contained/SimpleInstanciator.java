package io.github.oliviercailloux.contained;

public interface SimpleInstanciator {
  <T> T newInstance(String clazz, Class<T> type) throws ClassNotFoundException, InstantiationException;
}
