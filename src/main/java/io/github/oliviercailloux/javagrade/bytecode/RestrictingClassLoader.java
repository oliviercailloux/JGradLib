package io.github.oliviercailloux.javagrade.bytecode;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;

public class RestrictingClassLoader extends URLClassLoader {

  public static RestrictingClassLoader noPermissions(URL url, ClassLoader parent) {
    return new RestrictingClassLoader(url, parent, new Permissions());
  }

  public static RestrictingClassLoader granting(URL url, ClassLoader parent,
      PermissionCollection permissions) {
    return new RestrictingClassLoader(url, parent, permissions);
  }

  private final PermissionCollection permissions;

  public RestrictingClassLoader(URL url, ClassLoader parent, PermissionCollection permissions) {
    super(new URL[] {url}, parent);
    this.permissions = checkNotNull(permissions);
  }

  public PermissionCollection getPermissions() {
    return permissions;
  }

  @Override
  protected PermissionCollection getPermissions(CodeSource codesource) {
    return permissions;
  }
}
