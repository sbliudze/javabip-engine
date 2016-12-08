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
package org.javabip.engine;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import net.sf.javabdd.BDD;

import org.javabip.api.Accept;
import org.javabip.api.BIPComponent;
import org.javabip.api.BIPGlue;
import org.javabip.api.PortBase;
import org.javabip.api.Require;
import org.javabip.engine.api.BDDBIPEngine;
import org.javabip.engine.api.BIPCoordinator;
import org.javabip.engine.api.BehaviourEncoder;
import org.javabip.engine.api.GlueEncoder;
import org.javabip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives information about the glue and computes the glue BDD.
 * 
 * @author Anastasia Mavridou
 */

public class GlueEncoderImpl implements GlueEncoder {
	private Logger logger = LoggerFactory.getLogger(GlueEncoderImpl.class);

	// TODO: Dependencies to be simplified (see the BIPCoordinator implementation)
	private BehaviourEncoder behenc;
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;
	private BIPGlue glueSpec;

	/**
	 * Function called by the BIPCoordinator when the Glue xml file is parsed and its contents are stored as BIPGlue
	 * object that is given to this function as a parameter and stored in a global field of the class.
	 * 
	 * If the glue field is null throw an exception.
	 * 
	 * @throws BIPEngineException
	 */
	public void specifyGlue(BIPGlue glue) throws BIPEngineException {

		if (glue == null) {
			try {
				logger.error("The glue parser has failed to compute the glue object.\n"
						+ "\tPossible reasons: Corrupt or non-existant glue XML file.");
				throw new BIPEngineException("The glue parser has failed to compute the glue object.\n"
						+ "\tPossible reasons: Corrupt or non-existant glue XML file.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		this.glueSpec = glue;
	}

	/**
	 * Helper function that takes as an argument the list of ports at the causes part of a constraint macro and returns
	 * the component instances that correspond to each cause port.
	 * 
	 * @param requireCause
	 *            list of causes ports
	 * 
	 * @return Hashtable with the causes ports as keys and the set of component instances that correspond to them as
	 *         values
	 * 
	 * @throws BIPEngineException
	 *             when causes defined incorrectly.
	 * @throws InterruptedException
	 */
	Hashtable<PortBase, ArrayList<BDD>> findCausesComponents(Iterable<PortBase> requireCause) throws BIPEngineException {

		Hashtable<PortBase, ArrayList<BDD>> portToComponents = new Hashtable<PortBase, ArrayList<BDD>>();

		for (PortBase causePort : requireCause) {
			if (causePort.getSpecType() == null || causePort.getSpecType().isEmpty()) {
				logger.warn("Spec type not specified or empty in a macro cause. Skipping the port.");
			} else if (causePort.getId() == null || causePort.getId().isEmpty()) {
				logger.warn("Port name not specified or empty in a macro cause. Skipping the port.");
			} else {
				Iterable<BIPComponent> components = wrapper.getBIPComponentInstances(causePort.getSpecType());
				ArrayList<BDD> portBDDs = new ArrayList<BDD>();
				for (BIPComponent component : components) {
					logger.trace("Component: " + component.getId() + " has Causes ports: " + causePort);
					portBDDs.add(behenc.getBDDOfAPort(component, causePort.getId()));
				}
				logger.trace("Number of BDDs for port {} {}", causePort.getId(), portBDDs.size());

				if (portBDDs.isEmpty() || portBDDs == null || portBDDs.get(0) == null) {
					try {
						logger.error(
								"Port {} in causes was defined incorrectly. It does not match any registered port types",
								causePort.getId());
						throw new BIPEngineException("Port " + causePort.getId()
								+ " in causes was defined incorrectly. It does not match any registered port types");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;
					}
				}
				portToComponents.put(causePort, portBDDs);
			}
		}
		return portToComponents;
	}

	/**
	 * Helper function that takes as an argument the port at the effect part of a constraint macro and returns the
	 * component instances that correspond to this port.
	 * 
	 * @param effectPort
	 * 
	 * @return List with the set of component instances that correspond to the effect port
	 * 
	 * @throws BIPEngineException
	 *             when effect defined incorrectly.
	 * @throws InterruptedException
	 */
	List<BIPComponent> findEffectComponents(PortBase effectPort) throws BIPEngineException {

		assert (effectPort.getId() != null && !effectPort.getId().isEmpty());
		assert (effectPort.getSpecType() != null && !effectPort.getSpecType().isEmpty());

		List<BIPComponent> requireEffectComponents = wrapper.getBIPComponentInstances(effectPort.getSpecType());
		if (requireEffectComponents.isEmpty()) {
			try {
				logger.error(
						"Spec type in effect for component {} was defined incorrectly. It does not match any registered component types",
						effectPort.getSpecType());
				throw new BIPEngineException("Spec type in effect for component " + effectPort.getSpecType()
						+ " was defined incorrectly. It does not match any registered component types");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return requireEffectComponents;
	}

	/**
	 * Finds the BDDs of the ports of the components that are needed for computing one require macro and computes the
	 * BDD for this macro by calling the requireBDD method.
	 * 
	 * @param requires
	 *            interaction constraints
	 * 
	 * @return the BDD that corresponds to a Require macro
	 * 
	 * @throws BIPEngineException
	 * @throws InterruptedException
	 */
	ArrayList<BDD> decomposeRequireGlue(Require requires) throws BIPEngineException {
		ArrayList<BDD> result = new ArrayList<BDD>();

		if (requires.getEffect() == null) {
			try {
				logger.error("Effect part of a Require constraint was not specified.");
				throw new BIPEngineException("Effect part of a Require constraint was not specified.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}

		if (requires.getEffect().getId().isEmpty()) {
			try {
				logger.error("The port at the effect part of a Require constraint was not specified.");
				throw new BIPEngineException("The port at the effect part of a Require constraint was not specified.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}

		if (requires.getEffect().getSpecType().isEmpty()) {
			try {
				logger.error("The component type of a port at the effect part of a Require constraint was not specified.");
				throw new BIPEngineException(
						"The component type of a port at the effect part of a Require constraint was not specified.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		/* Find all effect component instances */
		List<BIPComponent> requireEffectComponents = findEffectComponents(requires.getEffect());

		if (requires.getCauses() == null) {
			try {
				logger.error("Causes part of a Require constraint was not specified in the macro.");
				throw new BIPEngineException("Causes part of a Require constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}

		/* Find all causes component instances */
		List<List<PortBase>> requireCauses = requires.getCauses();
		List<Hashtable<PortBase, ArrayList<BDD>>> allPorts = new ArrayList<Hashtable<PortBase, ArrayList<BDD>>>();
		List<Hashtable<PortBase, Integer>> allCardinalities = new ArrayList<Hashtable<PortBase, Integer>>();

		for (List<PortBase> requireCause : requireCauses) {

			allPorts.add(findCausesComponents(requireCause));
			Hashtable<PortBase, Integer> cardinalityForOneRequireCause = new Hashtable<PortBase, Integer>();

			/*
			 * Beginning of New Part for cardinalities
			 */
			for (PortBase oneCause : requireCause) {
				boolean identicalCause = false;
				int cardinality = 0;

				for (PortBase key : cardinalityForOneRequireCause.keySet()) {
					if (key.getId().equals(oneCause.getId()) && key.getSpecType().equals(oneCause.getSpecType())) {
						cardinality = cardinalityForOneRequireCause.get(key);
						cardinalityForOneRequireCause.remove(key);
						identicalCause = true;
					}
				}
				if (identicalCause) {
					cardinalityForOneRequireCause.put(oneCause, cardinality + 1);
				} else {
					cardinalityForOneRequireCause.put(oneCause, 1);
				}
			}
			allCardinalities.add(cardinalityForOneRequireCause);
			/*
			 * End of new part for cardinalities
			 */
		}

		// TODO: dont recompute the causes for each component instance
		for (BIPComponent effectInstance : requireEffectComponents) {
			logger.trace("Require Effect port type: " + requires.getEffect().getId() + " of component "
					+ requires.getEffect().getSpecType());
			result.add(requireBDD(behenc.getBDDOfAPort(effectInstance, requires.getEffect().getId()), allPorts,
					allCardinalities));
		}
		return result;
	}

	/**
	 * Finds the BDDs of the ports of the components that are needed for computing one accept macro and computes the BDD
	 * for this macro by calling the acceptBDD method.
	 * 
	 * @param accept
	 *            interaction constraints
	 * 
	 * @return the BDD that corresponds to an Accept macro
	 * 
	 * @throws BIPEngineException
	 * @throws InterruptedException
	 */
	ArrayList<BDD> decomposeAcceptGlue(Accept accept) throws BIPEngineException {
		ArrayList<BDD> result = new ArrayList<BDD>();

		if (accept.getEffect() == null) {
			try {
				logger.error("Effect part of an Accept constraint was not specified in the macro.");
				throw new BIPEngineException("Effect part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}

		if (accept.getEffect().getId().isEmpty()) {
			try {
				logger.error("The port at the effect part of an Accept constraint was not specified.");
				throw new BIPEngineException("The port at the effect part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}

		if (accept.getEffect().getSpecType().isEmpty()) {
			try {
				logger.error("The component type of a port at the effect part of an Accept constraint was not specified");
				throw new BIPEngineException(
						"The component type of a port at the effect part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		/* Find all effect component instances */
		List<BIPComponent> acceptEffectComponents = findEffectComponents(accept.getEffect());

		if (accept.getCauses() == null) {
			try {
				logger.error("Causes part of an Accept constraint was not specified in the macro.");
				throw new BIPEngineException("Causes part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		/* Find all causes component instances */
		Hashtable<PortBase, ArrayList<BDD>> portsToBDDs = findCausesComponents(accept.getCauses());

		for (BIPComponent effectInstance : acceptEffectComponents) {
			result.add(acceptBDD(behenc.getBDDOfAPort(effectInstance, accept.getEffect().getId()), portsToBDDs));
		}
		return result;
	}

	/**
	 * Computes the BDD that corresponds to a Require macro.
	 * 
	 * @param BDD
	 *            of the port of the component holder of the Require macro
	 * @param Hashtable
	 *            of ports of the "causes" part of the Require macro and the corresponding port BDDs of the component
	 *            instances
	 * 
	 * @return the BDD that corresponds to a Require macro.
	 */

	BDD requireBDD(BDD requirePortHolder, List<Hashtable<PortBase, ArrayList<BDD>>> allCausesPorts,
			List<Hashtable<PortBase, Integer>> requiredCardinalities) {

		BDD allDisjunctiveCauses = engine.getBDDManager().zero();
		logger.trace("Start computing the require BDDs");
		for (Hashtable<PortBase, ArrayList<BDD>> oneCausePorts : allCausesPorts) {

			BDD allCausesBDD = engine.getBDDManager().one();
			for (Enumeration<PortBase> portEnum = oneCausePorts.keys(); portEnum.hasMoreElements();) {

				while (portEnum.hasMoreElements()) {
					PortBase port = portEnum.nextElement();

					boolean checkingCardinalities = false;
					for (Hashtable<PortBase, Integer> auxPort : requiredCardinalities) {
						if (auxPort.containsKey(port)) {
							checkingCardinalities = true;
						}
					}

					if (checkingCardinalities == true) {
						ArrayList<BDD> setOfPortBDDs = oneCausePorts.get(port);

						/*
						 * Gets all different subsets of size equal to the cardinality
						 */

						/*
						 * TODO: Throw Exception if the cardinality specified in the Glue for a specific component type
						 * is greater than the number of registered instances of this component type
						 */
						ArrayList<HashSet<BDD>> subsetsOfGivenSize = HelperFunctions.enumerateSubsets(setOfPortBDDs,
								requiredCardinalities.get(allCausesPorts.indexOf(oneCausePorts)).get(port));

						logger.trace("Required port BDDs size: " + setOfPortBDDs.size());
						logger.trace("Required port: " + port.getId() + " " + port.getSpecType());
						int size = setOfPortBDDs.size();
						BDD oneCauseBDD = engine.getBDDManager().zero();

						for (HashSet<BDD> subset : subsetsOfGivenSize) {
							BDD monomial = engine.getBDDManager().one();
							for (int i = 0; i < size; i++) {
								if (subset.contains(setOfPortBDDs.get(i))) {
									/*
									 * Cannot use andWith here. Do not want to free the BDDs assigned to the ports at
									 * the Behaviour Encoder.
									 */
									BDD tmp = monomial.and(setOfPortBDDs.get(i));
									monomial.free();
									monomial = tmp;
								} else {
									/*
									 * Cannot use andWith here. Do not want to free the BDDs assigned to the ports at
									 * the Behaviour Encoder.
									 */
									BDD tmp = monomial.and(setOfPortBDDs.get(i).not());
									monomial.free();
									monomial = tmp;
								}
							}
							logger.trace("before one Cause OR");
							oneCauseBDD.orWith(monomial);
						}

						logger.trace("before all Causes AND");
						allCausesBDD.andWith(oneCauseBDD);
					}
				}
			}
			logger.trace("before all Disjunctive Causes OR");
			allDisjunctiveCauses.orWith(allCausesBDD);
		}
		logger.trace("Finished with the require BDDs");
		allDisjunctiveCauses.orWith(requirePortHolder.not());
		logger.trace("Finished with the disjunctive causes");
		return allDisjunctiveCauses;
	}

	/**
	 * Computes the BDD that corresponds to an Accept macro.
	 * 
	 * @param BDD
	 *            of the port of the component holder of the Accept macro
	 * @param Hashtable
	 *            of ports of the "causes" part of the Accept macro and the corresponding port BDDs of the component
	 *            instances
	 * 
	 * @return the BDD that corresponds to an Accept macro.
	 */

	BDD acceptBDD(BDD acceptPortHolder, Hashtable<PortBase, ArrayList<BDD>> acceptedPorts) {
		BDD tmp;

		ArrayList<BDD> totalPortBDDs = new ArrayList<BDD>();

		/*
		 * Get all port BDDs registered in the Behaviour Encoder and add them in the totalPortBDDs ArrayList.
		 */
		Map<BIPComponent, BDD[]> componentToBDDs = behenc.getPortBDDs();

		for (BIPComponent component : componentToBDDs.keySet()) {
			BDD[] portBDD = componentToBDDs.get(component);
			for (int p = 0; p < portBDD.length; p++) {
				totalPortBDDs.add(portBDD[p]);
			}
		}
		logger.trace("totalPortBDDs size: " + totalPortBDDs.size());
		BDD allCausesBDD = engine.getBDDManager().one();

		if (acceptedPorts.size() > 1) {
			logger.trace("Start computing the accept BDDs");
			for (BDD portBDD : totalPortBDDs) {
				boolean exist = false;

				for (Enumeration<PortBase> portEnum = acceptedPorts.keys(); portEnum.hasMoreElements();) {
					PortBase port = portEnum.nextElement();
					ArrayList<BDD> currentPortInstanceBDDs = acceptedPorts.get(port);
					logger.trace("currentPortInstanceBDDs size" + currentPortInstanceBDDs.size());
					int indexPortBDD = 0;

					if ((portBDD).equals(acceptPortHolder)) {
						exist = true;
					}
					while (!exist && indexPortBDD < currentPortInstanceBDDs.size()) {
						if (currentPortInstanceBDDs.get(indexPortBDD).equals(portBDD)) {
							exist = true;

						} else {
							indexPortBDD++;
						}
					}
				}
				if (!exist) {
					allCausesBDD.andWith(portBDD.not());
				}
			}
		} else {
			for (BDD portBDD : totalPortBDDs) {
				boolean exist = false;

				for (Enumeration<PortBase> portEnum = acceptedPorts.keys(); portEnum.hasMoreElements();) {
					PortBase port = portEnum.nextElement();
					ArrayList<BDD> currentPortInstanceBDDs = acceptedPorts.get(port);
					int indexPortBDD = 0;

					if ((portBDD).equals(acceptPortHolder)) {
						exist = true;
					}
					while (!exist && indexPortBDD < currentPortInstanceBDDs.size()) {
						if (currentPortInstanceBDDs.get(indexPortBDD).equals(portBDD)) {
							exist = true;
						} else {
							indexPortBDD++;
						}
					}
					if (!exist) {
						tmp = portBDD.not().and(allCausesBDD);
						allCausesBDD.free();
						allCausesBDD = tmp;
					}
				}
			}
		}
		logger.trace("Finished computing the accept BDDs");
		return allCausesBDD.orWith(acceptPortHolder.not());
	}

	public ArrayList<BDD> totalGlue() throws BIPEngineException {
		ArrayList<BDD> allGlueBDDs = new ArrayList<BDD>();

		if (!glueSpec.getRequiresConstraints().isEmpty() || !glueSpec.getRequiresConstraints().equals(null)) {
			logger.trace("Glue spec require Constraints size: {} ", glueSpec.getRequiresConstraints().size());
			logger.trace("Start conjunction of requires");
			for (Require requires : glueSpec.getRequiresConstraints()) {
				allGlueBDDs.addAll(decomposeRequireGlue(requires));

			}
		} else {
			logger.warn("No require constraints provided (usually there should be some).");
		}

		logger.trace("Glue spec accept Constraints size: {} ", glueSpec.getAcceptConstraints().size());
		if (!glueSpec.getAcceptConstraints().isEmpty() || !glueSpec.getAcceptConstraints().equals(null)) {
			for (Accept accepts : glueSpec.getAcceptConstraints()) {
				allGlueBDDs.addAll(decomposeAcceptGlue(accepts));
			}
		} else {
			logger.warn("No accept constraints were provided (usually there should be some).");
		}
		return allGlueBDDs;
	}

	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behenc = behaviourEncoder;
	}

	public void setEngine(BDDBIPEngine engine) {
		this.engine = engine;
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

}
