package com.personal.plugin;

import com.personal.plugin.webservice.SandBoxAPI;
import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

	protected Collection<ServiceRegistration> registrationList;

	public void start(BundleContext context) {
		registrationList = new ArrayList<ServiceRegistration>();
		registrationList.add(
			context.registerService(
				SandBoxAPI.class.getName(),
				new SandBoxAPI(),
				null
			)
		);
	}

	public void stop(BundleContext context) {
		for (ServiceRegistration registration : registrationList) {
			registration.unregister();
		}
	}
}
