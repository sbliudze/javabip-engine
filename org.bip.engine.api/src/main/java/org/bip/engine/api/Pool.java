package org.bip.engine.api;

import org.bip.api.BIPComponent;

public interface Pool {
	
	void initialize();
	
	boolean isValid();

	boolean addInstance(BIPComponent instance);

	boolean removeInstance(BIPComponent instances);
}
