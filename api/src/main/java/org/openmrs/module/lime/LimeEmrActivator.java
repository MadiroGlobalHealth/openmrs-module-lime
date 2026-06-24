/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.lime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.ModuleActivator;
import org.openmrs.scheduler.SchedulerService;
import org.openmrs.scheduler.TaskDefinition;
import org.openmrs.module.lime.tasks.RebuildSearchIndexTask;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
public class LimeEmrActivator implements ModuleActivator {

	public static final String REBUILD_SEARCH_INDEX_TASK_NAME = "Rebuild Search Index";

	public static final String REBUILD_SEARCH_INDEX_TASK_DESCRIPTION = "Weekly rebuild of the OpenMRS search index";

	public static final String REBUILD_SEARCH_INDEX_TASK_CLASS = RebuildSearchIndexTask.class.getName();

	public static final String REBUILD_SEARCH_INDEX_TASK_UUID = "4f55c008-6571-11f1-9b7d-5d5590d478d8";

	public static final String REBUILD_SEARCH_INDEX_START_TIME_PATTERN = "MM/dd/yyyy HH:mm:ss";

	public static final long WEEKLY_REPEAT_INTERVAL_SECONDS = 7L * 24 * 60 * 60;

	protected Log log = LogFactory.getLog(getClass());

	/**
	 * @see ModuleActivator#willRefreshContext()
	 */
	public void willRefreshContext() {
		log.info("Refreshing LIME Module");
	}

	/**
	 * @see ModuleActivator#contextRefreshed()
	 */
	public void contextRefreshed() {
		log.info("LIME Module refreshed");
	}

	/**
	 * @see ModuleActivator#willStart()
	 */
	public void willStart() {
		log.info("Starting LIME Module");
	}

	/**
	 * @see ModuleActivator#started()
	 */
	public void started() {
		ensureRebuildSearchIndexTaskScheduled();
		log.info("LIME Module started");
	}

	/**
	 * @see ModuleActivator#willStop()
	 */
	public void willStop() {
		log.info("Stopping LIME Module");
	}

	/**
	 * @see ModuleActivator#stopped()
	 */
	public void stopped() {
		log.info("LIME Module stopped");
	}

	void ensureRebuildSearchIndexTaskScheduled() {
		try {
			SchedulerService schedulerService = Context.getSchedulerService();
			TaskDefinition task = schedulerService.getTaskByUuid(REBUILD_SEARCH_INDEX_TASK_UUID);
			boolean existingTask = task != null;
			if (task == null) {
				task = schedulerService.getTaskByName(REBUILD_SEARCH_INDEX_TASK_NAME);
				if (task != null && !REBUILD_SEARCH_INDEX_TASK_UUID.equals(task.getUuid())) {
					log.warn("Found a task named '" + REBUILD_SEARCH_INDEX_TASK_NAME + "' with UUID "
					        + task.getUuid() + " which does not match the expected UUID. Creating a separate task.");
					task = null;
				}
				existingTask = task != null;
			}
			if (task == null) {
				task = new TaskDefinition();
				task.setUuid(REBUILD_SEARCH_INDEX_TASK_UUID);
			}

			task.setName(REBUILD_SEARCH_INDEX_TASK_NAME);
			task.setDescription(REBUILD_SEARCH_INDEX_TASK_DESCRIPTION);
			task.setTaskClass(REBUILD_SEARCH_INDEX_TASK_CLASS);
			task.setStartTimePattern(REBUILD_SEARCH_INDEX_START_TIME_PATTERN);
			task.setRepeatInterval(WEEKLY_REPEAT_INTERVAL_SECONDS);
			if (!existingTask) {
				task.setStartTime(getNextFridayAtFiveAm());
			}
			task.setStartOnStartup(Boolean.TRUE);
			task.setStarted(Boolean.TRUE);

			schedulerService.saveTaskDefinition(task);
			if (existingTask) {
				schedulerService.rescheduleTask(task);
				log.info("Rescheduled search index rebuild task");
			} else {
				schedulerService.scheduleTask(task);
				log.info("Created search index rebuild task for Friday at 05:00");
			}
		}
		catch (Exception e) {
			log.error("Failed to schedule the search index rebuild task", e);
		}
	}

	Date getNextFridayAtFiveAm() {
		ZoneId zone = ZoneId.systemDefault();
		LocalDateTime now = LocalDateTime.now(zone);
		LocalDate today = now.toLocalDate();
		int daysUntilFriday = (DayOfWeek.FRIDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
		LocalDate targetDate = today.plusDays(daysUntilFriday);
		LocalDateTime targetDateTime = LocalDateTime.of(targetDate, LocalTime.of(5, 0));
		if (!targetDateTime.isAfter(now)) {
			targetDateTime = targetDateTime.plusWeeks(1);
		}
		log.info("Search index rebuild scheduled for " + targetDateTime + " (zone: " + zone + ")");
		return Date.from(targetDateTime.atZone(zone).toInstant());
	}
}
