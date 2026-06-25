package org.openmrs.module.lime.tasks;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "jdk.internal.reflect.*"})
public class RebuildSearchIndexTaskTest {

    @Test
    public void execute_shouldUpdateSearchIndex() {
        RebuildSearchIndexTask task = spy(new RebuildSearchIndexTask());
        doNothing().when(task).updateSearchIndex();

        task.execute();

        verify(task, times(1)).updateSearchIndex();
    }
}
