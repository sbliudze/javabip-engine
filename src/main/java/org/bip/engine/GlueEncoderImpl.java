package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.api.Accept;
import org.bip.api.BIPComponent;
import org.bip.api.BIPGlue;
import org.bip.api.PortBase;
import org.bip.api.Require;
import org.bip.engine.api.BDDBIPEngine;
import org.bip.engine.api.BIPCoordinator;
import org.bip.engine.api.BehaviourEncoder;
import org.bip.engine.api.GlueEncoder;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives information about the glue and computes the glue BDD.
 * @author mavridou
 */

/**
 * Computes the BDD of the glue
 * 
 * @author Anastasia Mavridou
 */
public class GlueEncoderImpl implements GlueEncoder {
	private Logger logger = LoggerFactory.getLogger(GlueEncoderImpl.class);

	// TODO: Dependencies to be simplified (see the BIPCoordinator
	// implementation)
	private BehaviourEncoder behenc;
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;
	private BIPGlue glueSpec;

	/**
	 * Function called by the BIPCoordinator when the Glue xml file is parsed
	 * and its contents are stored as BIPGlue object that is given to this
	 * function as a parameter and stored in a global field of the class.
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
	 * Helper function that takes as an argument the list of ports at the causes
	 * part of a constraint macro and returns the component instances that
	 * correspond to each cause port.
	 * 
	 * @param ArrayList
	 *            of causes ports
	 * 
	 * @return Hashtable with the causes ports as keys and the set of component
	 *         instances that correspond to them as values
	 * 
	 * @throws BIPEngineException
	 * @throws InterruptedException
	 */
	Hashtable<PortBase, ArrayList<BDD>> findCausesComponents(Iterable<PortBase> requireCause)
			throws BIPEngineException {

		Hashtable<PortBase, ArrayList<BDD>> portToComponents = new Hashtable<PortBase, ArrayList<BDD>>();

		for (PortBase causePort : requireCause) {
			if (causePort.getSpecType() == null || causePort.getSpecType().isEmpty()) {
				logger.warn("Spec type not specified or empty in a macro cause. Skipping the port.");
			} else if (causePort.getId() == null || causePort.getId().isEmpty()) {
				logger.warn("Port name not specified or empty in a macro cause. Skipping the port.");
			} else {
				Iterable<BIPComponent> components = wrapper.getBIPComponentInstances(causePort.getSpecType());
				ArrayList<BDD> portBDDs = new ArrayList<BDD>();
				logger.debug("Before going through all components");
				try {
					for (BIPComponent component : components) {
						// logger.trace("Component: " + component.getId() + "
						// has Causes ports: " + causePort);
						portBDDs.add(behenc.getBDDOfAPort(component, causePort.getId()));
						logger.debug("Done with getting the BDD of the port {}", causePort);
					}
				} catch (Exception e) {
					e.printStackTrace();
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
	 * Helper function that takes as an argument the port at the effect part of
	 * a constraint macro and returns the component instances that correspond to
	 * this port.
	 * 
	 * @param Effect
	 *            port
	 * 
	 * @return ArrayList with the set of component instances that correspond to
	 *         the effect port
	 * 
	 * @throws BIPEngineException
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

	private boolean checkCauseIsSatisfied(List<PortBase> cause) {
		Map<String, Integer> cardinalitiesPerType = new HashMap<String, Integer>();
		Integer cardinality;
		for (PortBase portBase : cause) {
			if (cardinalitiesPerType.containsKey(portBase.getSpecType())) {
				cardinality = cardinalitiesPerType.get(portBase.getSpecType());
				cardinalitiesPerType.put(portBase.getSpecType(), cardinality + 1);
			} else {
				cardinalitiesPerType.put(portBase.getSpecType(), 1);
			}
		}

		for (Map.Entry<String, Integer> entry : cardinalitiesPerType.entrySet()) {
			if (wrapper.getBIPComponentInstances(entry.getKey()).size() < entry.getValue())
				return false;
		}

		return true;
	}

	/**
	 * Finds the BDDs of the ports of the components that are needed for
	 * computing one require macro and computes the BDD for this macro by
	 * calling the requireBDD method.
	 * 
	 * @param require
	 *            interaction constraints
	 * 
	 * @return the BDD that corresponds to a Require macro
	 * 
	 * @throws BIPEngineException
	 * @throws InterruptedException
	 */
	ArrayList<BDD> decomposeRequireGlue(Require requires) throws BIPEngineException {
		logger.debug("Decomposing the glue for {} Requires {}", requires.getEffect(), requires.getCauses().toString());
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
				logger.error(
						"The component type of a port at the effect part of a Require constraint was not specified.");
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
		boolean found = false;
		List<List<PortBase>> requireCauses = requires.getCauses();
		List<Hashtable<PortBase, ArrayList<BDD>>> allPorts = new ArrayList<Hashtable<PortBase, ArrayList<BDD>>>();
		List<Hashtable<PortBase, Integer>> allCardinalities = new ArrayList<Hashtable<PortBase, Integer>>();

		for (List<PortBase> requireCause : requireCauses) {
			if (!checkCauseIsSatisfied(requireCause))
				continue;

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
			found = true;
			/*
			 * End of new part for cardinalities
			 */
		}

		if (!found)
			throw new BIPEngineException("Require contraint of type " + requires.getEffect().getSpecType()
					+ " has no satisfied clause amongst " + requires.getCauses());

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
	 * Finds the BDDs of the ports of the components that are needed for
	 * computing one accept macro and computes the BDD for this macro by calling
	 * the acceptBDD method.
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
				logger.error(
						"The component type of a port at the effect part of an Accept constraint was not specified");
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
		Hashtable<PortBase, ArrayList<BDD>> portsToBDDs = findAcceptCausesComponents(accept.getCauses());

		for (BIPComponent effectInstance : acceptEffectComponents) {
			result.add(acceptBDD(behenc.getBDDOfAPort(effectInstance, accept.getEffect().getId()), portsToBDDs));
		}
		return result;
	}

	private Hashtable<PortBase, ArrayList<BDD>> findAcceptCausesComponents(Collection<PortBase> causes)
			throws BIPEngineException {

		Hashtable<PortBase, ArrayList<BDD>> portToComponents = new Hashtable<PortBase, ArrayList<BDD>>();

		for (PortBase causePort : causes) {
			if (causePort.getSpecType() == null || causePort.getSpecType().isEmpty()) {
				logger.warn("Spec type not specified or empty in a macro cause. Skipping the port.");
			} else if (causePort.getId() == null || causePort.getId().isEmpty()) {
				logger.warn("Port name not specified or empty in a macro cause. Skipping the port.");
			} else {
				Iterable<BIPComponent> components = new ArrayList<BIPComponent>();
				try {
					components = wrapper.getBIPComponentInstances(causePort.getSpecType());
				} catch (BIPEngineException e) {
				}
				ArrayList<BDD> portBDDs = new ArrayList<BDD>();
				for (BIPComponent component : components) {
					logger.trace("Component: " + component + " has Causes ports: " + causePort);
					portBDDs.add(behenc.getBDDOfAPort(component, causePort.getId()));
				}
				logger.trace("Number of BDDs for port {} {}", causePort.getId(), portBDDs.size());

				portToComponents.put(causePort, portBDDs);
			}
		}
		return portToComponents;
	}

	/**
	 * Computes the BDD that corresponds to a Require macro.
	 * 
	 * @param BDD
	 *            of the port of the component holder of the Require macro
	 * @param Hashtable
	 *            of ports of the "causes" part of the Require macro and the
	 *            corresponding port BDDs of the component instances
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
						 * Gets all different subsets of size equal to the
						 * cardinality
						 */

						/*
						 * TODO: Throw Exception if the cardinality specified in
						 * the Glue for a specific component type is greater
						 * than the number of registered instances of this
						 * component type
						 */

						int requiredCardinality = requiredCardinalities.get(allCausesPorts.indexOf(oneCausePorts))
								.get(port);
						// logger.debug(
						// "For type " + port.getSpecType()
						// + " required cardinality {}, number of port instances
						// {}",
						// requiredCardinality, setOfPortBDDs.size());
						if (setOfPortBDDs.size() < requiredCardinality) {
							return engine.getBDDManager().one();
						}
						ArrayList<HashSet<BDD>> subsetsOfGivenSize = HelperFunctions.enumerateSubsets(setOfPortBDDs,
								requiredCardinality);

						logger.trace("Required port BDDs size: " + setOfPortBDDs.size());
						logger.trace("Required port: " + port.getId() + " " + port.getSpecType());
						int size = setOfPortBDDs.size();
						BDD oneCauseBDD = engine.getBDDManager().zero();

						for (HashSet<BDD> subset : subsetsOfGivenSize) {
							BDD monomial = engine.getBDDManager().one();
							for (int i = 0; i < size; i++) {
								if (subset.contains(setOfPortBDDs.get(i))) {
									/*
									 * Cannot use andWith here. Do not want to
									 * free the BDDs assigned to the ports at
									 * the Behaviour Encoder.
									 */
									BDD tmp = monomial.and(setOfPortBDDs.get(i));
									monomial.free();
									monomial = tmp;
								} else {
									/*
									 * Cannot use andWith here. Do not want to
									 * free the BDDs assigned to the ports at
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
	 *            of ports of the "causes" part of the Accept macro and the
	 *            corresponding port BDDs of the component instances
	 * 
	 * @return the BDD that corresponds to an Accept macro.
	 */

	BDD acceptBDD(BDD acceptPortHolder, Hashtable<PortBase, ArrayList<BDD>> acceptedPorts) {
		BDD tmp;

		ArrayList<BDD> totalPortBDDs = new ArrayList<BDD>();

		/*
		 * Get all port BDDs registered in the Behaviour Encoder and add them in
		 * the totalPortBDDs ArrayList.
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
				try {
					wrapper.getBIPComponentInstances(requires.getEffect().getSpecType());
					allGlueBDDs.addAll(decomposeRequireGlue(requires));
				} catch (BIPEngineException e) {
					// if BIPEngineException, then we just do not have any
					// instance of this type but it does not matter since we
					// know the system is valid from the component pool
				}
			}
		} else {
			logger.warn("No require constraints provided (usually there should be some).");
		}

		logger.trace("Glue spec accept Constraints size: {} ", glueSpec.getAcceptConstraints().size());
		if (!glueSpec.getAcceptConstraints().isEmpty() || !glueSpec.getAcceptConstraints().equals(null)) {
			for (Accept accepts : glueSpec.getAcceptConstraints()) {
				try {
					wrapper.getBIPComponentInstances(accepts.getEffect().getSpecType());
					allGlueBDDs.addAll(decomposeAcceptGlue(accepts));
				} catch (BIPEngineException e) {
				}
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
