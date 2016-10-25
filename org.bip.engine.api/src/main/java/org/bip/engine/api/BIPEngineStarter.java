package org.bip.engine.api;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.bip.api.BIPComponent;

public interface BIPEngineStarter {
	
	Semaphore informBlocker = new Semaphore(0);
	Set<BIPComponent> newComponents = new HashSet<BIPComponent>();
	
	/**
	 * Starts the BIPEngine
	 */
	public void start();
	
	void execute();

	void setEngineStarter(BIPEngineStarter starter);
	
	void setStartCallback(StarterCallback callback);
	
	void executeCallback();
	
	void blockNewComponent(BIPComponent component);
}
