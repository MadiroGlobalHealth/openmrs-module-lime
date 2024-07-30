package org.openmrs.module.lime;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.openmrs.Privilege;
import org.openmrs.api.context.Context;
import org.openmrs.module.datafilter.DataFilterContext;
import org.openmrs.module.datafilter.DataFilterListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MosulAppointmentsDataFilterListener implements DataFilterListener {
	private static final Logger log = LoggerFactory.getLogger(MosulAppointmentsDataFilterListener.class);

    @Override
    public boolean onEnableFilter(DataFilterContext filterContext) {
		if (Context.isAuthenticated() && Context.getAuthenticatedUser().isSuperUser()) {
			log.trace("Skipping enabling of filters for super user");

			return false;
		}
    if (filterContext.getFilterName().startsWith("lime_appointmentPrivilegeBasedAppointmentService")) {
			Collection<String> privileges = new HashSet<>();
			if (Context.isAuthenticated()) {
				Collection<String> allUserPrivileges = Context.getAuthenticatedUser().getPrivileges().stream().map(Privilege::getName).collect(Collectors.toSet());
				privileges.addAll(allUserPrivileges);
			}

			filterContext.setParameter("privileges", privileges);
		}
        return true;
    }

    @Override
    public boolean supports(String filterName) {
        return filterName.startsWith("lime_appointmentPrivilegeBasedAppointmentService");
    }
}
