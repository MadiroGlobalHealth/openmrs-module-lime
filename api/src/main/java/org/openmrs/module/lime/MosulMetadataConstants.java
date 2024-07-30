package org.openmrs.module.lime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MosulMetadataConstants {

    /**x
     * Users Names
     */
    public static final String REGISTRATION_OFFICER_USERNAME = "REG_officer";
    public static final String MENTAL_HEALTH_NURNSE_USERNAME = "MH_nurse";
    public static final String MENTAL_HEALTH_COUNSELOR_USERNAME = "MH_counselor";
    public static final String MENTAL_HEALTH_DOCTOR_USERNAME = "MH_doctor";
    public static final String MATERNITY_NURSE_USERNAME = "MAT_nurse";

    /**x
     * Roles Names
     */
    public static final String REGISTRATION_OFFICER_ROLE_NAME = "Registration Officer";
    public static final String MENTAL_HEALTH_NURNSE_ROLE_NAME = "Mental Health Nurse";
    public static final String MENTAL_HEALTH_COUNSELOR_ROLE_NAME = "Mental Health Counselor";
    public static final String MENTAL_HEALTH_DOCTOR_ROLE_NAME = "Mental Health Doctor";
    public static final String MATERNITY_NURSE_ROLE_NAME = "Maternity Nurse";

    /**x
     * usernames and there corresponding role names
     */
    public static final Map<String, String> USER_ROLES = Collections.unmodifiableMap(new HashMap<String, String>() {{
        put(REGISTRATION_OFFICER_USERNAME, REGISTRATION_OFFICER_ROLE_NAME);
        put(MENTAL_HEALTH_NURNSE_USERNAME, MENTAL_HEALTH_NURNSE_ROLE_NAME);
        put(MENTAL_HEALTH_COUNSELOR_USERNAME, MENTAL_HEALTH_COUNSELOR_ROLE_NAME);
        put(MENTAL_HEALTH_DOCTOR_USERNAME, MENTAL_HEALTH_DOCTOR_ROLE_NAME);
        put(MATERNITY_NURSE_USERNAME, MATERNITY_NURSE_ROLE_NAME);
    }});
}
