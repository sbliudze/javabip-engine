/*
 * Copyright 2012-2016 École polytechnique fédérale de Lausanne (EPFL), Switzerland
 * Copyright 2012-2016 Crossing-Tech SA, Switzerland
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.javabip.engine.coordinator;

import java.util.Set;

import org.javabip.api.BIPComponent;
import org.javabip.api.Port;

/**
 * Class representing a data container which stores the data name, the data value, the component providing the data and
 * the ports providing the data.
 * 
 * @author Alina Zolotukhina
 *
 */
class DataContainer {
	private String dataIn;
	private Object value;
	private BIPComponent component;
	private Set<Port> ports;

	/**
	 * Data Container constructor.
	 * 
	 * @param dataName
	 *            the name of the data.
	 * @param value
	 *            the value of the data.
	 * @param component
	 *            the component providing the data.
	 * @param ports
	 *            the ports providing the data.
	 */
	public DataContainer(String dataName, Object value, BIPComponent component, Set<Port> ports) {
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
