package org.bip.engine;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Data;
import org.bip.behaviour.Port;

public class DataContainer {
	private String dataIn;
	private Object value;
	private BIPComponent component;
	private Iterable<Port> ports;

	public DataContainer(Data inDataItem, Object value, BIPComponent component, Iterable<Port> ports) {
		this.dataIn = inDataItem.name();
		this.component = component;
		this.value = value;
		this.ports = ports;
	}

	public String name() {
		return this.dataIn;
	}

	public Iterable<Port> ports() {
		return this.ports;
	}

	public Object value() {
		return this.value;
	}

	public BIPComponent component() {
		return this.component;
	}
}
