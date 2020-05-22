package io.github.oliviercailloux.security;

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
public class SandboxSecurityPolicy extends Policy {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SandboxSecurityPolicy.class);

	@Override
	public boolean implies(ProtectionDomain domain, Permission permission) {
		if (!(domain.getClassLoader() instanceof PluginClassLoader)) {
			return true;
		}
		return false;
	}
}