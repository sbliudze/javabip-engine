package org.bip.engine.api;

import org.bip.api.BIPComponent;

public interface Pool {

	public void initialize();

	public boolean addInstance(BIPComponent instance);

	public boolean removeInstance(BIPComponent instances);
}
