package org.openmrs.module.lime.advice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;

/**
 * Advises {@link org.openmrs.module.appointments.service.AppointmentsService} to hide
 * appointments for services whose name contains "Mental Health" from any user who lacks
 * the "Manage Sensitive Appointments" privilege. Replaces the Hibernate-level row filter
 * that previously required the datafilter module.
 */
public class AppointmentPrivilegeFilterAdvice implements MethodInterceptor {

    static final String SENSITIVE_APPOINTMENTS_PRIVILEGE = "Manage Sensitive Appointments";

    static final String SENSITIVE_SERVICE_NAME_MARKER = "Mental Health";

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object returnValue = invocation.proceed();

        if (canViewSensitiveAppointments()) {
            return returnValue;
        }

        if (returnValue instanceof Appointment) {
            return isSensitive((Appointment) returnValue) ? null : returnValue;
        }
        if (returnValue instanceof List) {
            return filterSensitive((List<?>) returnValue);
        }
        if (returnValue instanceof Map) {
            Map<Object, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) returnValue).entrySet()) {
                Object value = entry.getValue();
                filtered.put(entry.getKey(), value instanceof List ? filterSensitive((List<?>) value) : value);
            }
            return filtered;
        }

        return returnValue;
    }

    private List<Object> filterSensitive(List<?> appointments) {
        List<Object> filtered = new ArrayList<>();
        for (Object item : appointments) {
            if (item instanceof Appointment && isSensitive((Appointment) item)) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private boolean canViewSensitiveAppointments() {
        if (!Context.isAuthenticated()) {
            return false;
        }
        User authenticatedUser = Context.getAuthenticatedUser();
        return (authenticatedUser != null && authenticatedUser.isSuperUser())
                || Context.hasPrivilege(SENSITIVE_APPOINTMENTS_PRIVILEGE);
    }

    private boolean isSensitive(Appointment appointment) {
        AppointmentServiceDefinition service = appointment.getService();
        return service != null && service.getName() != null
                && service.getName().contains(SENSITIVE_SERVICE_NAME_MARKER);
    }
}
