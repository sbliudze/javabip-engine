package org.bip.coordinator;

import java.util.Set;

import org.bip.api.BIPComponent;
import org.bip.api.Data;
import org.bip.api.Port;
import org.bip.api.PortBase;

class DataContainerImpl {
	private String dataIn;
	private Object value;
	private BIPComponent component;
	private Set<Port> ports;

	//TODO replace Data with String
	public DataContainerImpl(Data<?> inDataItem, Object value, BIPComponent component, Set<Port> ports) {
		this.dataIn = inDataItem.name();
		this.component = component;
		this.value = value;
		this.ports = ports;
	}

	public String name() {
		return this.dataIn;
	}

	public Set<Port> ports() {
		return this.ports;
	}

	public Object value() {
		return this.value;
	}

	public BIPComponent component() {
		return this.component;
	}
}
