package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;

public interface InteractionExecutor {
	
	public void executeInteraction(Map<BIPComponent, Iterable<Port>> portsToFire);

}
