package org.openmrs.module.patientidentifiergenerator;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
@PowerMockIgnore("javax.management.*")
public class IdentifierEnhancementFactoryTest {

    @Mock
    private AdministrationService administrationService;

    private IdentifierEnhancementFactory identifierEnhancementFactory;

    public static final String MSF_PATIENT_IDENTIFIER_PREFIX = "IQ146-";

    @Before
    public void setUp() {
        identifierEnhancementFactory = new IdentifierEnhancementFactory(administrationService);
        PowerMockito.mockStatic(Context.class);
        when(Context.getAdministrationService()).thenReturn(administrationService);
    }

    @Test
    public void shouldAddMSFIDFormatToPatientIdentifier() {
        Patient patient = setUpPatientData();

        identifierEnhancementFactory.enhanceIdentifier(patient);

        assertEquals("IQ146-24-000-000", patient.getPatientIdentifier().getIdentifier());
    }

    private Patient setUpPatientData() {
        Patient patient = new Patient();
        patient.setGender("M");
        PatientIdentifier patientIdentifier =
                new PatientIdentifier("24000000", new PatientIdentifierType(), new Location());
        HashSet<PatientIdentifier> patientIdentifiers = new HashSet<>();
        patientIdentifiers.add(patientIdentifier);
        patient.setIdentifiers(patientIdentifiers);
        PersonAttributeType personAttributeType = new PersonAttributeType();
        personAttributeType.setName("personAttribute");
        PersonAttribute personAttribute = new PersonAttribute(personAttributeType, "100");
        HashSet<PersonAttribute> personAttributes = new HashSet<>();
        personAttributes.add(personAttribute);
        patient.setAttributes(personAttributes);
        return patient;
    }
}
