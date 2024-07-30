package org.openmrs.module.lime.advice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.module.lime.IdentifierEnhancementFactory;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;

import java.lang.reflect.Method;

public class BeforeSaveAdvice implements MethodBeforeAdvice, AfterReturningAdvice {

    private static final String methodToIntercept = "savePatient";
    private static IdentifierEnhancementFactory identifierEnhancementFactory = new IdentifierEnhancementFactory();
    private ThreadLocal<Patient> patientThreadLocal = new ThreadLocal<>();
    private Log log = LogFactory.getLog(getClass());

    public void before(Method method, Object[] objects, Object o) {
        if (method.getName().equalsIgnoreCase(methodToIntercept)) {
            Patient patient = (Patient) objects[0];
            if (patient.getPatientId() == null) {
                identifierEnhancementFactory.enhanceIdentifier(patient);
                patientThreadLocal.set(patient);
            }
        }
    }

    @Override
    public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
        if (method.getName().equalsIgnoreCase(methodToIntercept) && args[0] instanceof Patient) {
            log.warn("Patient Created succesfully." );

            Patient patient = patientThreadLocal.get();
            if (patient != null && identifierEnhancementFactory.hasIsIdentiferSequenceReset()) {
                log.warn("Attempting to save identifier Sequence after patient Creation." );
                patientThreadLocal.remove();
                identifierEnhancementFactory.saveNewIdentifierSequenceValue();;
            }
        }
    }
}
