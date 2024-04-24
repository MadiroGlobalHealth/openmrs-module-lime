package org.openmrs.module.patientidentifiergenerator;

import java.time.Year;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;

public class IdentifierEnhancementFactory {

    public static final String MSF_IDENTIFIER_SOURCE_UUID = "8549f706-7e85-4c1d-9424-217d50a2988b";
    private static int lastRecordedYear = -1;

    protected Log log = LogFactory.getLog(getClass());
    
    public void enhanceIdentifier(Patient patient) {
        IdentifierSourceService identifierSourceService = Context.getService(IdentifierSourceService.class);
        SequentialIdentifierGenerator MSFIdentifierSource = (SequentialIdentifierGenerator) identifierSourceService.getIdentifierSourceByUuid(MSF_IDENTIFIER_SOURCE_UUID);

        if (MSFIdentifierSource == null) {
            log.error("Identifier Source with uuid " + MSF_IDENTIFIER_SOURCE_UUID + " is not found hence skipping MSF ID generation");
            return;
        }
        
        String prefix = getPrefix(MSFIdentifierSource);
        PatientIdentifier identifier = patient.getPatientIdentifier();
        String bashId = StringUtils.substringAfter(identifier.getIdentifier(), prefix);

        int translatedBashId;
        try {
            translatedBashId = Integer.valueOf(bashId);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Invalid MSF ID: " + bashId, e);
        }
        
        int currentYearPrefix = Year.now().getValue() % 100;
        // If the year has changed, reset patient id to 1
        if (lastRecordedYear != currentYearPrefix) {
            translatedBashId = 1;
            identifierSourceService.saveSequenceValue(MSFIdentifierSource, translatedBashId + 1);
            lastRecordedYear = currentYearPrefix;
        }
        translatedBashId = translatedBashId +  (currentYearPrefix * 1000000);
        bashId = String.valueOf(translatedBashId);

        // regex below inserts a hyphen after every group of three digits in the Identifier.
        String regex = "(\\d)(?=(\\d{3})+$)";
        StringBuilder enhancedId = new StringBuilder();
        enhancedId.append(prefix)
                  .append(bashId.replaceAll(regex, "$1-"));
        identifier.setIdentifier(enhancedId.toString());
    }

    private String getPrefix(SequentialIdentifierGenerator IdentifierSourceGenerator) {
        String prefix = IdentifierSourceGenerator.getPrefix();
        return prefix != null ? prefix : "";
    }
}
