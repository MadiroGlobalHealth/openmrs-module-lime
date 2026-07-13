package org.openmrs.module.lime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.apache.commons.logging.Log;
import org.openmrs.api.context.Context;
import org.openmrs.scheduler.SchedulerService;
import org.openmrs.scheduler.TaskDefinition;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
@PowerMockIgnore({"javax.management.*", "jdk.internal.reflect.*"})
public class LimeEmrActivatorTest {

    @Mock
    private SchedulerService schedulerService;

    @Mock
    private Log log;

    private LimeEmrActivator activator;

    @Before
    public void setUp() {
        activator = new LimeEmrActivator();
        activator.log = log;
        PowerMockito.mockStatic(Context.class);
        when(Context.getSchedulerService()).thenReturn(schedulerService);
    }

    @Test
    public void started_shouldCreateAndScheduleSearchIndexRebuildTask() throws Exception {
        when(schedulerService.getTaskByUuid(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID)).thenReturn(null);
        when(schedulerService.getTaskByName(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_NAME)).thenReturn(null);

        activator.started();

        ArgumentCaptor<TaskDefinition> taskCaptor = ArgumentCaptor.forClass(TaskDefinition.class);
        verify(schedulerService).saveTaskDefinition(taskCaptor.capture());
        verify(schedulerService).scheduleTask(any(TaskDefinition.class));
        verify(schedulerService, never()).rescheduleTask(any(TaskDefinition.class));

        TaskDefinition task = taskCaptor.getValue();
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_NAME, task.getName());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_DESCRIPTION, task.getDescription());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_CLASS, task.getTaskClass());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID, task.getUuid());
        assertEquals("MM/dd/yyyy HH:mm:ss", task.getStartTimePattern());
        assertEquals(Long.valueOf(LimeEmrActivator.DAILY_REPEAT_INTERVAL_SECONDS), task.getRepeatInterval());
        assertTrue(task.getStartOnStartup());
        assertTrue(task.getStarted());
        assertNotNull(task.getStartTime());

        ZonedDateTime scheduledTime = ZonedDateTime.ofInstant(task.getStartTime().toInstant(), ZoneId.systemDefault());
        assertEquals(5, scheduledTime.getHour());
        assertEquals(0, scheduledTime.getMinute());
        assertEquals(0, scheduledTime.getSecond());
        assertFalse(scheduledTime.isBefore(ZonedDateTime.now()));
        verify(log).info(contains("Created search index rebuild task"));
    }

    @Test
    public void started_shouldUpdateAndRescheduleExistingSearchIndexRebuildTask() throws Exception {
        TaskDefinition existingTask = new TaskDefinition();
        existingTask.setId(15);
        existingTask.setName("Legacy task");
        existingTask.setUuid(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID);
        existingTask.setStartOnStartup(Boolean.FALSE);
        existingTask.setStarted(Boolean.FALSE);
        existingTask.setRepeatInterval(60L);
        existingTask.setStartTime(java.util.Date.from(Instant.now()));

        when(schedulerService.getTaskByUuid(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID)).thenReturn(existingTask);

        activator.started();

        ArgumentCaptor<TaskDefinition> taskCaptor = ArgumentCaptor.forClass(TaskDefinition.class);
        verify(schedulerService).saveTaskDefinition(taskCaptor.capture());
        verify(schedulerService).rescheduleTask(any(TaskDefinition.class));
        verify(schedulerService, never()).scheduleTask(any(TaskDefinition.class));

        TaskDefinition task = taskCaptor.getValue();
        assertEquals(Integer.valueOf(15), task.getId());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_NAME, task.getName());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_DESCRIPTION, task.getDescription());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_CLASS, task.getTaskClass());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID, task.getUuid());
        assertEquals(Long.valueOf(LimeEmrActivator.DAILY_REPEAT_INTERVAL_SECONDS), task.getRepeatInterval());
        assertTrue(task.getStartOnStartup());
        assertTrue(task.getStarted());
        verify(log).info(contains("Rescheduled search index rebuild task"));
    }

    @Test
    public void started_shouldPreserveStartTimeOfExistingTask() throws Exception {
        java.util.Date originalStartTime = java.util.Date.from(Instant.now());
        TaskDefinition existingTask = new TaskDefinition();
        existingTask.setUuid(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID);
        existingTask.setStartTime(originalStartTime);
        existingTask.setStarted(Boolean.TRUE);

        when(schedulerService.getTaskByUuid(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID)).thenReturn(existingTask);

        activator.started();

        ArgumentCaptor<TaskDefinition> taskCaptor = ArgumentCaptor.forClass(TaskDefinition.class);
        verify(schedulerService).saveTaskDefinition(taskCaptor.capture());
        assertEquals(originalStartTime, taskCaptor.getValue().getStartTime());
    }

    @Test
    public void started_shouldNotOverwriteForeignTaskWithSameNameButDifferentUuid() throws Exception {
        String foreignUuid = "00000000-0000-0000-0000-000000000001";
        TaskDefinition foreignTask = new TaskDefinition();
        foreignTask.setUuid(foreignUuid);
        foreignTask.setName(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_NAME);
        foreignTask.setTaskClass("com.other.module.SomeOtherTask");

        when(schedulerService.getTaskByUuid(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID)).thenReturn(null);
        when(schedulerService.getTaskByName(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_NAME)).thenReturn(foreignTask);

        activator.started();

        ArgumentCaptor<TaskDefinition> taskCaptor = ArgumentCaptor.forClass(TaskDefinition.class);
        verify(schedulerService).scheduleTask(any(TaskDefinition.class));
        verify(schedulerService, never()).rescheduleTask(any(TaskDefinition.class));
        verify(schedulerService).saveTaskDefinition(taskCaptor.capture());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_UUID, taskCaptor.getValue().getUuid());
        assertEquals(LimeEmrActivator.REBUILD_SEARCH_INDEX_TASK_CLASS, taskCaptor.getValue().getTaskClass());
        assertEquals(foreignUuid, foreignTask.getUuid());
        assertEquals("com.other.module.SomeOtherTask", foreignTask.getTaskClass());
    }
}
