package org.openmrs.module.lime.tasks;

import org.openmrs.api.context.Context;
import org.openmrs.scheduler.tasks.AbstractTask;

public class RebuildSearchIndexTask extends AbstractTask {

	@Override
	public void execute() {
		updateSearchIndex();
	}

	void updateSearchIndex() {
		Context.updateSearchIndex();
	}
}
