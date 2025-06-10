package org.openmrs.module.lime.advice;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.idgen.IdentifierSource;
import org.openmrs.module.lime.IdentifierEnhancementFactory;

import java.lang.reflect.Method;

public class BeforeSaveAdvice implements MethodInterceptor{

    private static final String METHOD_TO_INTERCEPT = "generateIdentifier";

    private static IdentifierEnhancementFactory identifierEnhancementFactory = new IdentifierEnhancementFactory();
    private ThreadLocal<String> identifierThreadLocal = new ThreadLocal<>();
    private Log log = LogFactory.getLog(getClass());

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        
        Boolean shouldGenerateIdentifier = method.getName().equalsIgnoreCase(METHOD_TO_INTERCEPT) && 
            method.getParameterTypes().length == 2 && 
            IdentifierSource.class.isAssignableFrom(method.getParameterTypes()[0]);

        // Before advice logic
        if (shouldGenerateIdentifier) {
            log.info("Preparing to intercept identifier generation");
        }
        
        // Proceed with the original method call
        Object returnValue = invocation.proceed();
        
        // After advice logic
        if (shouldGenerateIdentifier) {
            log.info("Preparing to intercept identifier generation");

            if (returnValue instanceof String) {
                String generatedIdentifier = (String) returnValue;
                log.info("Generated identifier: " + generatedIdentifier);
                
                // Enhance the identifier and use it as the new return value
                String enhancedIdentifier = identifierEnhancementFactory.enhanceIdentifier(generatedIdentifier);
                identifierThreadLocal.set(enhancedIdentifier);
                
                if (identifierEnhancementFactory.hasIsIdentiferSequenceReset()) {
                    log.warn("Attempting to save identifier Sequence after identifier generation.");
                    identifierEnhancementFactory.saveNewIdentifierSequenceValue();
                }
                
                // Return the enhanced identifier instead of the original
                returnValue = enhancedIdentifier;
            } else {
                log.warn("Generated identifier is not a String: " + 
                    (returnValue != null ? returnValue.getClass().getName() : "null"));
            }
            
            identifierThreadLocal.remove();
        }
        
        return returnValue;
    }
}
