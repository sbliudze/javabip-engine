package org.bip.engine.api;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bip.api.BIPComponent;

public interface BIPEngineStarter {
	
	Semaphore informBlocker = new Semaphore(0);
	Set<BIPComponent> newComponents = new HashSet<BIPComponent>();
	Lock registrationLock = new ReentrantLock();
	Semaphore deregistrationBlocker = new Semaphore(0);
	
	/**
	 * Starts the BIPEngine
	 */
	public void start();
	
	void execute();

	void setEngineStarter(BIPEngineStarter starter);
	
	void setStartCallback(StarterCallback callback);
	
	void executeCallback();
	
	void blockNewComponent(BIPComponent component);
	
	void blockDeregistratingComponent(BIPComponent component);
}
