package org.openmrs.module.patientidentifiergenerator;

import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAttribute;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.AdministrationServiceImpl;

import java.util.Collection;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class IdentifierEnhancementFactory {

    private AdministrationService administrationService;

    public IdentifierEnhancementFactory() {
        this.administrationService = new AdministrationServiceImpl();
    }

    public IdentifierEnhancementFactory(AdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    public static final String MSF_PATIENT_IDENTIFIER_PREFIX = "IQ146-";

    public void enhanceIdentifier(Patient patient) {
        PatientIdentifier identifier = patient.getPatientIdentifier();
        StringBuilder enhancedId = new StringBuilder();
        //regex below inserts a hyphen after every group of three digits in the Identifier.
        String regex = "(\\d)(?=(\\d{3})+$)";
        enhancedId.append(MSF_PATIENT_IDENTIFIER_PREFIX).append(identifier.getIdentifier().replaceAll(regex, "$1-"));
        identifier.setIdentifier(enhancedId.toString());
    }

}
