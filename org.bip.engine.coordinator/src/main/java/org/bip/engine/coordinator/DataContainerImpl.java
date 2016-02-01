package org.bip.engine.coordinator;

import java.util.Set;

import org.bip.api.BIPComponent;
import org.bip.api.Port;

class DataContainerImpl {
	private String dataIn;
	private Object value;
	private BIPComponent component;
	private Set<Port> ports;

	public DataContainerImpl(String dataName, Object value,	BIPComponent component, Set<Port> ports) {
		this.dataIn = dataName;
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
	
	public String toString() {

		StringBuilder result = new StringBuilder();

		result.append("DataContainer=(");
		result.append("name = " + name());
		result.append(", value = " + value());
		result.append(")");

		return result.toString();
	}
}
