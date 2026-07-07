# Remove datafilter dependency from lime-emr Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `lime-emr`'s hard dependency on the `datafilter` module while preserving the existing behavior that hides "Mental Health" appointments from users lacking the `Manage Sensitive Appointments` privilege.

**Architecture:** Replace the Hibernate-level row filter (`MosulAppointmentsDataFilterListener` + `appointment_privilege.json`, driven by the `datafilter` module) with a single Spring AOP `MethodInterceptor` (`AppointmentPrivilegeFilterAdvice`) advising the whole `org.openmrs.module.appointments.service.AppointmentsService` interface, registered via OpenMRS's native `<advice>` extension point in `config.xml` — the same mechanism already used for `BeforeSaveAdvice`/`IdentifierSourceService`.

**Tech Stack:** Java 8, Maven, JUnit 4, Mockito, PowerMock (`PowerMockRunner` + `PowerMockito.mockStatic` for `Context`), OpenMRS 2.6.6 API, `org.bahmni.module:appointments-api`.

## Global Constraints

- OpenMRS core API version: `2.6.6` (from `pom.xml` / `api/pom.xml` `openMRSVersion` property) — do not change.
- New dependency `org.bahmni.module:appointments-api` must be added at version `2.1.0-SNAPSHOT`, `provided` scope (matches what's already resolvable in `~/.m2` and what `appointments-omod` resolves to; consistent with how `idgen-api` is already declared `provided`).
- The `datafilter-api` dependency, its `datafilterVersion` property, and the `datafilter` `require_module` entry must be fully removed — no leftover references anywhere in `api/` or `omod/`.
- Preserve current runtime behavior exactly: sensitive appointments are silently hidden (`null` for single-fetch, removed from lists/map-values) — never throw an authorization exception (per approved spec).
- Follow the existing test pattern in `api/src/test/java/org/openmrs/module/lime/LimeEmrActivatorTest.java`: `@RunWith(PowerMockRunner.class)`, `@PrepareForTest(Context.class)`, `@PowerMockIgnore({"javax.management.*", "jdk.internal.reflect.*"})`, `PowerMockito.mockStatic(Context.class)` in `@Before`.

---

### Task 1: Add `appointments-api` Maven dependency

**Files:**
- Modify: `api/pom.xml:15-19` (properties block), `api/pom.xml:70-75` (dependencies block, right after the existing `idgen-api` dependency)

**Interfaces:**
- Produces: compile-time availability of `org.openmrs.module.appointments.service.AppointmentsService`, `org.openmrs.module.appointments.model.Appointment`, `org.openmrs.module.appointments.model.AppointmentServiceDefinition` for Task 2.

- [ ] **Step 1: Add the dependency**

In `api/pom.xml`, the properties block currently reads:

```xml
	<properties>
		<openMRSVersion>2.6.6</openMRSVersion>
		<idgenModuleVersion>4.11.0-SNAPSHOT</idgenModuleVersion>
		<datafilterVersion>2.3.0-SNAPSHOT</datafilterVersion>
	</properties>
```

Add an `appointmentsModuleVersion` property (leave `datafilterVersion` alone for now — it's removed in Task 3):

```xml
	<properties>
		<openMRSVersion>2.6.6</openMRSVersion>
		<idgenModuleVersion>4.11.0-SNAPSHOT</idgenModuleVersion>
		<datafilterVersion>2.3.0-SNAPSHOT</datafilterVersion>
		<appointmentsModuleVersion>2.1.0-SNAPSHOT</appointmentsModuleVersion>
	</properties>
```

Then, immediately after the existing `idgen-api` dependency block:

```xml
		<dependency>
			<groupId>org.openmrs.module</groupId>
			<artifactId>idgen-api</artifactId>
			<type>jar</type>
			<scope>provided</scope>
			<version>${idgenModuleVersion}</version>
		</dependency>
```

add:

```xml
		<dependency>
			<groupId>org.bahmni.module</groupId>
			<artifactId>appointments-api</artifactId>
			<type>jar</type>
			<scope>provided</scope>
			<version>${appointmentsModuleVersion}</version>
		</dependency>
```

- [ ] **Step 2: Verify the dependency resolves and the module still compiles**

Run: `mvn -q -pl api -am compile`
Expected: no output, exit code 0 (Maven `-q` prints nothing on success). If it fails, run without `-q` to see the resolution error.

- [ ] **Step 3: Commit**

```bash
git add api/pom.xml
git commit -m "Add appointments-api dependency ahead of datafilter removal"
```

---

### Task 2: Implement `AppointmentPrivilegeFilterAdvice` with tests

**Files:**
- Create: `api/src/test/java/org/openmrs/module/lime/advice/AppointmentPrivilegeFilterAdviceTest.java`
- Create: `api/src/main/java/org/openmrs/module/lime/advice/AppointmentPrivilegeFilterAdvice.java`

**Interfaces:**
- Consumes: `org.openmrs.api.context.Context.isAuthenticated()`, `.getAuthenticatedUser()`, `.hasPrivilege(String)`; `org.openmrs.User.isSuperUser()`; `org.openmrs.module.appointments.model.Appointment.getService()`; `org.openmrs.module.appointments.model.AppointmentServiceDefinition.getName()`.
- Produces: `public class AppointmentPrivilegeFilterAdvice implements org.aopalliance.intercept.MethodInterceptor` with public no-arg constructor, and package-visible constants `static final String SENSITIVE_APPOINTMENTS_PRIVILEGE = "Manage Sensitive Appointments"` and `static final String SENSITIVE_SERVICE_NAME_MARKER = "Mental Health"` — used by Task 3's `config.xml` wiring (by fully-qualified class name) and by this task's own tests.

- [ ] **Step 1: Write the failing tests**

Create `api/src/test/java/org/openmrs/module/lime/advice/AppointmentPrivilegeFilterAdviceTest.java`:

```java
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl api -am test -Dtest=AppointmentPrivilegeFilterAdviceTest`
Expected: compilation failure — `cannot find symbol: class AppointmentPrivilegeFilterAdvice` (the class doesn't exist yet).

- [ ] **Step 3: Write the implementation**

Create `api/src/main/java/org/openmrs/module/lime/advice/AppointmentPrivilegeFilterAdvice.java`:

```java
package org.openmrs.module.lime.advice;

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
            removeSensitive((List<?>) returnValue);
            return returnValue;
        }
        if (returnValue instanceof Map) {
            for (Object value : ((Map<?, ?>) returnValue).values()) {
                if (value instanceof List) {
                    removeSensitive((List<?>) value);
                }
            }
            return returnValue;
        }

        return returnValue;
    }

    private void removeSensitive(List<?> appointments) {
        appointments.removeIf(item -> item instanceof Appointment && isSensitive((Appointment) item));
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl api -am test -Dtest=AppointmentPrivilegeFilterAdviceTest`
Expected: no output, exit code 0. All 7 tests pass (`invoke_shouldReturnAllAppointments_whenUserIsSuperUser`, `invoke_shouldReturnAllAppointments_whenUserHasSensitivePrivilege`, `invoke_shouldRemoveSensitiveAppointmentsFromList_whenUserLacksPrivilege`, `invoke_shouldReturnNullForSingleSensitiveAppointment_whenUserLacksPrivilege`, `invoke_shouldReturnSingleNonSensitiveAppointment_whenUserLacksPrivilege`, `invoke_shouldFilterListsWithinConflictsMap_whenUserLacksPrivilege`, `invoke_shouldTreatAppointmentWithNullServiceAsNotSensitive`).

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/org/openmrs/module/lime/advice/AppointmentPrivilegeFilterAdvice.java api/src/test/java/org/openmrs/module/lime/advice/AppointmentPrivilegeFilterAdviceTest.java
git commit -m "Add AppointmentPrivilegeFilterAdvice to replace datafilter-based appointment filtering"
```

---

### Task 3: Remove the datafilter module dependency

**Files:**
- Delete: `api/src/main/java/org/openmrs/module/lime/MosulAppointmentsDataFilterListener.java`
- Delete: `api/src/main/resources/filters/hibernate/appointment_privilege.json`
- Modify: `api/pom.xml:15-19` (properties), `api/pom.xml` (remove `datafilter-api` dependency)
- Modify: `omod/src/main/resources/config.xml:16-25`

**Interfaces:**
- Consumes: `org.openmrs.module.lime.advice.AppointmentPrivilegeFilterAdvice` (fully-qualified class name, wired by string in `config.xml` — Task 2's produced class).

- [ ] **Step 1: Delete the datafilter listener and its filter definition**

```bash
git rm api/src/main/java/org/openmrs/module/lime/MosulAppointmentsDataFilterListener.java
git rm api/src/main/resources/filters/hibernate/appointment_privilege.json
```

- [ ] **Step 2: Remove the datafilter dependency from `api/pom.xml`**

The properties block (after Task 1's change) reads:

```xml
	<properties>
		<openMRSVersion>2.6.6</openMRSVersion>
		<idgenModuleVersion>4.11.0-SNAPSHOT</idgenModuleVersion>
		<datafilterVersion>2.3.0-SNAPSHOT</datafilterVersion>
		<appointmentsModuleVersion>2.1.0-SNAPSHOT</appointmentsModuleVersion>
	</properties>
```

Remove the `datafilterVersion` line:

```xml
	<properties>
		<openMRSVersion>2.6.6</openMRSVersion>
		<idgenModuleVersion>4.11.0-SNAPSHOT</idgenModuleVersion>
		<appointmentsModuleVersion>2.1.0-SNAPSHOT</appointmentsModuleVersion>
	</properties>
```

Remove this dependency block entirely:

```xml
		<dependency>
			<groupId>org.openmrs.module</groupId>
			<artifactId>datafilter-api</artifactId>
			<version>${datafilterVersion}</version>
			<scope>provided</scope>
		</dependency>
```

- [ ] **Step 3: Update `omod/src/main/resources/config.xml`**

Current content:

```xml
	<advice>
		<point>org.openmrs.module.idgen.service.IdentifierSourceService</point>
		<class>org.openmrs.module.lime.advice.BeforeSaveAdvice</class>
	</advice>

	<require_modules>
		<require_module version="4.10.0">org.openmrs.module.idgen</require_module>
		<require_module version="2.2.0">org.openmrs.module.datafilter</require_module>
		<require_module version="1.0.0">org.bahmni.module.appointments</require_module>
	</require_modules>
```

Replace with:

```xml
	<advice>
		<point>org.openmrs.module.idgen.service.IdentifierSourceService</point>
		<class>org.openmrs.module.lime.advice.BeforeSaveAdvice</class>
	</advice>

	<advice>
		<point>org.openmrs.module.appointments.service.AppointmentsService</point>
		<class>org.openmrs.module.lime.advice.AppointmentPrivilegeFilterAdvice</class>
	</advice>

	<require_modules>
		<require_module version="4.10.0">org.openmrs.module.idgen</require_module>
		<require_module version="1.0.0">org.bahmni.module.appointments</require_module>
	</require_modules>
```

- [ ] **Step 4: Verify no datafilter references remain**

Run: `grep -ril "datafilter" api/src omod/src api/pom.xml omod/pom.xml`
Expected: no output (no matches, exit code 1).

- [ ] **Step 5: Verify the full build passes**

Run: `mvn -q clean package`
Expected: no output, exit code 0. This confirms: `AppointmentPrivilegeFilterAdviceTest` still passes, nothing else references the deleted listener/JSON file, and both `api` and `omod` modules package successfully without `datafilter-api` on the classpath.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Remove datafilter module dependency, wire AppointmentPrivilegeFilterAdvice instead"
```

---

## Post-implementation note

This unblocks `lime-emr` startup in environments where `datafilter` is absent/incompatible, which in turn resolves the original `OpenmrsClassLoader could not load class: org.openmrs.module.lime.tasks.RebuildSearchIndexTask` error — that class was never actually broken; it was unreachable because the module containing it never started. No changes to `RebuildSearchIndexTask` itself are needed.
