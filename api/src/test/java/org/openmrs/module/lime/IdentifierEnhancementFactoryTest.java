package org.openmrs.module.lime;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Year;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
@PowerMockIgnore({"javax.management.*", "jdk.internal.reflect.*"})
public class IdentifierEnhancementFactoryTest {

    private IdentifierEnhancementFactory identifierEnhancementFactory;

    private static final String TEST_MSF_PATIENT_IDENTIFIER_PREFIX = "IQ146-";
    private static final String TEST_MSF_IDENTIFIER_SOURCE_UUID = "8549f706-7e85-4c1d-9424-217d50a2988b";

    @Mock
    private IdentifierSourceService identifierSourceService;

    @Before
    public void setUp() {
        identifierEnhancementFactory = new IdentifierEnhancementFactory();
        PowerMockito.mockStatic(Context.class);
        when(Context.getService(IdentifierSourceService.class)).thenReturn(identifierSourceService);
    }

    @Test
    public void shouldAddMSFIDFormatToIdentifier() {
        String identifier = "IQ146-1";
        SequentialIdentifierGenerator sequentialIdentifierGenerator = setUpIdentifierSource();
        when(identifierSourceService.getIdentifierSourceByUuid(TEST_MSF_IDENTIFIER_SOURCE_UUID)).thenReturn(sequentialIdentifierGenerator);

        String enhancedIdentifier = identifierEnhancementFactory.enhanceIdentifier(identifier);
        // for year 2024, id will be IQ146-24-000-001
        assertEquals("IQ146-" + getCurrentYear() + "-000-001", enhancedIdentifier);
    }

    @Test
    public void shouldResetMSFIDSequenceOnNewYear() {
        String identifier = "IQ146-999";
        int followingYear = getCurrentYear() + 1;
        setLastRecordedYear(followingYear);

        SequentialIdentifierGenerator sequentialIdentifierGenerator = setUpIdentifierSource();
        when(identifierSourceService.getIdentifierSourceByUuid(TEST_MSF_IDENTIFIER_SOURCE_UUID)).thenReturn(sequentialIdentifierGenerator);

        String enhancedIdentifier = identifierEnhancementFactory.enhanceIdentifier(identifier);
        // for year 2024, id will be IQ146-24-000-001
        assertEquals("IQ146-" + getCurrentYear() + "-000-001", enhancedIdentifier);
    }

    @Test
    public void shouldReturnOriginalIdentifierWhenSourceNotFound() {
        String identifier = "IQ146-1";
        when(identifierSourceService.getIdentifierSourceByUuid(TEST_MSF_IDENTIFIER_SOURCE_UUID)).thenReturn(null);

        String enhancedIdentifier = identifierEnhancementFactory.enhanceIdentifier(identifier);
        assertEquals(identifier, enhancedIdentifier);
    }

    private SequentialIdentifierGenerator setUpIdentifierSource() {
        SequentialIdentifierGenerator sequentialIdentifierGenerator = new SequentialIdentifierGenerator();
        sequentialIdentifierGenerator.setPrefix(TEST_MSF_PATIENT_IDENTIFIER_PREFIX);
        return sequentialIdentifierGenerator;
    }

    private void setLastRecordedYear(int year) {
        try {
            Field field = IdentifierEnhancementFactory.class.getDeclaredField("lastRecordedYear");
            field.setAccessible(true);
            if (Modifier.isStatic(field.getModifiers())) {
                field.set(null, year);
            } else {
                throw new IllegalAccessException("Field is not static");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private int getCurrentYear() {
        return Year.now().getValue() % 100;
    }
}
