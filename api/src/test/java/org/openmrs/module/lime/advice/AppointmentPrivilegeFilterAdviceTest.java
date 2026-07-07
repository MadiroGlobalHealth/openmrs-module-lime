package org.openmrs.module.lime.advice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
@PowerMockIgnore({"javax.management.*", "jdk.internal.reflect.*"})
public class AppointmentPrivilegeFilterAdviceTest {

    @Mock
    private MethodInvocation invocation;

    @Mock
    private User authenticatedUser;

    private AppointmentPrivilegeFilterAdvice advice;

    @Before
    public void setUp() {
        advice = new AppointmentPrivilegeFilterAdvice();
        PowerMockito.mockStatic(Context.class);
        when(Context.isAuthenticated()).thenReturn(true);
        when(Context.getAuthenticatedUser()).thenReturn(authenticatedUser);
        when(authenticatedUser.isSuperUser()).thenReturn(false);
        when(Context.hasPrivilege(AppointmentPrivilegeFilterAdvice.SENSITIVE_APPOINTMENTS_PRIVILEGE)).thenReturn(false);
    }

    private Appointment appointmentWithServiceName(String serviceName) {
        AppointmentServiceDefinition service = new AppointmentServiceDefinition();
        service.setName(serviceName);
        Appointment appointment = new Appointment();
        appointment.setService(service);
        return appointment;
    }

    @Test
    public void invoke_shouldReturnAllAppointments_whenUserIsSuperUser() throws Throwable {
        when(authenticatedUser.isSuperUser()).thenReturn(true);
        List<Appointment> appointments = new ArrayList<>();
        appointments.add(appointmentWithServiceName("Mental Health Counseling"));
        when(invocation.proceed()).thenReturn(appointments);

        Object result = advice.invoke(invocation);

        assertEquals(1, ((List<?>) result).size());
    }

    @Test
    public void invoke_shouldReturnAllAppointments_whenUserHasSensitivePrivilege() throws Throwable {
        when(Context.hasPrivilege(AppointmentPrivilegeFilterAdvice.SENSITIVE_APPOINTMENTS_PRIVILEGE)).thenReturn(true);
        List<Appointment> appointments = new ArrayList<>();
        appointments.add(appointmentWithServiceName("Mental Health Counseling"));
        when(invocation.proceed()).thenReturn(appointments);

        Object result = advice.invoke(invocation);

        assertEquals(1, ((List<?>) result).size());
    }

    @Test
    public void invoke_shouldRemoveSensitiveAppointmentsFromList_whenUserLacksPrivilege() throws Throwable {
        Appointment sensitive = appointmentWithServiceName("Mental Health Counseling");
        Appointment normal = appointmentWithServiceName("General Consultation");
        List<Appointment> appointments = new ArrayList<>();
        appointments.add(sensitive);
        appointments.add(normal);
        when(invocation.proceed()).thenReturn(appointments);

        Object result = advice.invoke(invocation);

        List<?> filtered = (List<?>) result;
        assertEquals(1, filtered.size());
        assertTrue(filtered.contains(normal));
    }

    @Test
    public void invoke_shouldReturnNullForSingleSensitiveAppointment_whenUserLacksPrivilege() throws Throwable {
        when(invocation.proceed()).thenReturn(appointmentWithServiceName("Mental Health Counseling"));

        Object result = advice.invoke(invocation);

        assertNull(result);
    }

    @Test
    public void invoke_shouldReturnSingleNonSensitiveAppointment_whenUserLacksPrivilege() throws Throwable {
        Appointment normal = appointmentWithServiceName("General Consultation");
        when(invocation.proceed()).thenReturn(normal);

        Object result = advice.invoke(invocation);

        assertEquals(normal, result);
    }

    @Test
    public void invoke_shouldFilterListsWithinConflictsMap_whenUserLacksPrivilege() throws Throwable {
        Appointment sensitive = appointmentWithServiceName("Mental Health Counseling");
        Appointment normal = appointmentWithServiceName("General Consultation");
        List<Appointment> conflictList = new ArrayList<>();
        conflictList.add(sensitive);
        conflictList.add(normal);
        Map<String, List<Appointment>> conflicts = new HashMap<>();
        conflicts.put("SERVICE_UNAVAILABLE", conflictList);
        when(invocation.proceed()).thenReturn(conflicts);

        Object result = advice.invoke(invocation);

        List<?> filtered = (List<?>) ((Map<?, ?>) result).get("SERVICE_UNAVAILABLE");
        assertEquals(1, filtered.size());
        assertTrue(filtered.contains(normal));
    }

    @Test
    public void invoke_shouldTreatAppointmentWithNullServiceAsNotSensitive() throws Throwable {
        Appointment appointment = new Appointment();
        appointment.setService(null);
        when(invocation.proceed()).thenReturn(appointment);

        Object result = advice.invoke(invocation);

        assertEquals(appointment, result);
    }
}
