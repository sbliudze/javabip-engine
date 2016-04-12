package org.bip.engine.api;

public interface BIPEngineStarter {
	
	/**
	 * Starts the BIPEngine
	 */
	public void start();
	
	void execute();

	void setEngineStarter(BIPEngineStarter starter);
	
	void setStartCallback(StarterCallback callback);
	
	void executeCallback();
}
