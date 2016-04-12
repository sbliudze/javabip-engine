package org.bip.engine.coordinator;

import org.bip.engine.api.BIPEngineStarter;
import org.bip.engine.api.StarterCallback;

public class StartCallback implements StarterCallback {
	private BIPEngineStarter starter;

	public StartCallback(BIPEngineStarter starter) {
		this.starter = starter;
	}

	@Override
	public void execute() {
		starter.start();
		starter.execute();
	}
}
