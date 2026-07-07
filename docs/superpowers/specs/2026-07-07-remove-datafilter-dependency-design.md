# Remove datafilter dependency from lime-emr

## Problem

`lime-emr` hard-requires the `datafilter` module (`config.xml`: `<require_module version="2.2.0">org.openmrs.module.datafilter</require_module>`). In the target environment, `datafilter` cannot run against the OpenMRS core version in use (it needs a Hibernate Search 6 port that doesn't exist upstream). When `datafilter` is removed/incompatible, `ModuleFactory` refuses to start `lime-emr`, which aborts the Spring context refresh partway through, leaving core beans (e.g. `transactionInterceptor`) unregistered. This cascades into failures across FHIR, stockmanagement, reporting, and the scheduler — surfacing as a misleading "Failed to obtain JDBC connection" FATAL, and as `OpenmrsClassLoader could not load class: org.openmrs.module.lime.tasks.RebuildSearchIndexTask` (the class lookup fails because the module that contains it never started).

`lime-emr`'s only real coupling to `datafilter` is one class, `MosulAppointmentsDataFilterListener`, which enables a Hibernate row-level filter (`lime_appointmentPrivilegeBasedAppointmentServiceFilter`, defined in `api/src/main/resources/filters/hibernate/appointment_privilege.json`) that hides `Appointment` rows whose service name contains "Mental Health" from any user lacking the `Manage Sensitive Appointments` privilege. This is a genuine confidentiality control, not incidental — it must be preserved, just reimplemented without the `datafilter` module.

## Goal

Remove the `datafilter` module dependency from `lime-emr` entirely, while preserving the existing behavior: users without `Manage Sensitive Appointments` cannot see Mental Health appointments through any `AppointmentsService` read method; superusers and privileged users are unaffected.

## Approach

Replace the Hibernate-level filter with a single Spring AOP `MethodInterceptor` advising the whole `org.openmrs.module.appointments.service.AppointmentsService` interface, registered via OpenMRS's native `<advice>` extension point in `config.xml` — the same mechanism `lime-emr` already uses for `BeforeSaveAdvice` (advising `IdentifierSourceService`).

`AppointmentsService` is a single interface; every read method returns `Appointment`, `List<Appointment>`, or `Map<Enum, List<Appointment>>` (conflict-check methods). One interceptor that post-filters the return value by type covers every appointment-read path in this codebase's actual usage — there is no code here that touches `Appointment` entities outside this service interface.

**Alternative considered:** replicate the Hibernate `@Filter`/`@FilterDef` directly and enable/disable it per-request against the shared `SessionFactory`. This would also catch raw HQL/Criteria queries that bypass `AppointmentsService`, but requires reaching into OpenMRS's shared Hibernate session factory configuration from a module — significantly more invasive and fragile for no benefit given the actual usage in this codebase. Rejected in favor of the AOP approach.

## Components

- **New:** `api/src/main/java/org/openmrs/module/lime/advice/AppointmentPrivilegeFilterAdvice.java`
  - Implements `MethodInterceptor`.
  - After `invocation.proceed()`:
    - Bypass filtering if `Context.isAuthenticated() && Context.getAuthenticatedUser().isSuperUser()`, or if `Context.hasPrivilege("Manage Sensitive Appointments")` is true — matches the bypass rules in the listener being replaced.
    - An appointment is "sensitive" if `appointment.getService() != null && appointment.getService().getName() != null && appointment.getService().getName().contains("Mental Health")`.
    - Dispatch on return type:
      - `Appointment` → return `null` if sensitive.
      - `List<Appointment>` → `removeIf` sensitive entries.
      - `Map<Enum, List<Appointment>>` (from `getAppointmentConflicts`/`getAppointmentsConflicts`) → filter each value list.
    - Any other/void return → pass through unchanged.

- **Modified:** `omod/src/main/resources/config.xml`
  - Remove `<require_module version="2.2.0">org.openmrs.module.datafilter</require_module>`.
  - Add a new `<advice>` block:

    ```xml
    <advice>
        <point>org.openmrs.module.appointments.service.AppointmentsService</point>
        <class>org.openmrs.module.lime.advice.AppointmentPrivilegeFilterAdvice</class>
    </advice>
    ```

- **Modified:** `api/pom.xml`
  - Remove the `datafilterVersion` property and the `datafilter-api` dependency.
  - Add a `provided`-scope dependency on `org.bahmni.module:appointments-api` (version `2.1.0-SNAPSHOT`, matching what's already resolvable in the environment) so the new advice class can compile against `AppointmentsService`/`Appointment`.

- **Deleted:**
  - `api/src/main/java/org/openmrs/module/lime/MosulAppointmentsDataFilterListener.java`
  - `api/src/main/resources/filters/hibernate/appointment_privilege.json`

## Data flow

Any caller of `AppointmentsService` (REST controllers, the appointments atom feed, etc.) invokes the Spring AOP proxy → `AppointmentPrivilegeFilterAdvice.invoke()` → the real service method executes unfiltered → the advice strips/nulls sensitive entries before the result reaches the caller. Write paths (`validateAndSave`, `changeStatus`, etc.) are not filtered — only read methods return appointment data that needs filtering.

## Error handling

No new exceptions. Matches today's behavior: sensitive appointments are silently hidden (null for single-fetch, removed from lists) rather than raising an authorization error. If `appointment.getService()` is null, treat as not-sensitive rather than throwing.

## Testing

Unit tests for `AppointmentPrivilegeFilterAdvice` (Mockito, consistent with the existing test setup in `api/pom.xml`):

- Superuser: sees all appointments (including Mental Health) regardless of privilege.
- User with `Manage Sensitive Appointments`: sees all appointments.
- Unprivileged, unauthenticated-safe default: Mental Health appointments removed from `List<Appointment>` results; `null` returned from single-`Appointment` methods; entries removed from each list in `Map<Enum, List<Appointment>>` results.
- Non-sensitive appointments always pass through regardless of privilege.
- `appointment.getService() == null` does not throw and is treated as non-sensitive.

## Out of scope

- Any other module's use of `datafilter` (none found elsewhere in this repo).
- Fixing the pre-existing `require_module` version floor mismatches for `idgen`/`appointments` (unrelated to this change).
- The `RebuildSearchIndexTask` scheduler registration itself — this change unblocks it (by letting `lime-emr` start), but the scheduler class-not-found investigation is otherwise complete once this fix lands.
