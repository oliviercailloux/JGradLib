package io.github.oliviercailloux.java_grade.bytecode;

import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Inspired by https://blog.jayway.com/2014/06/13/sandboxing-plugins-in-java/
 *
 */
@SuppressWarnings({ "removal", "deprecation" })
public class SandboxSecurityPolicy extends Policy {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SandboxSecurityPolicy.class);

	public static void setSecurity() {
		final SandboxSecurityPolicy myPolicy = new SandboxSecurityPolicy();
		Policy.setPolicy(myPolicy);
		System.setSecurityManager(new SecurityManager());
	}

	@Override
	public boolean implies(ProtectionDomain domain, Permission permission) {
		if (domain.getClassLoader() instanceof RestrictingClassLoader) {
			return domain.getPermissions().implies(permission);
		}
		return true;
	}
}