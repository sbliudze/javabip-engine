package org.bip.engine;

import java.util.ArrayList;

import java.util.Hashtable;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.Accepts;
import org.bip.glue.BIPGlue;
import org.bip.glue.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Computes the BDD of the glue */
public class GlueEncoderImpl implements GlueEncoder {
	private Logger logger = LoggerFactory.getLogger(GlueEncoderImpl.class);
	private ArrayList<BDD> glueRequireBDDs = new ArrayList<BDD>();
	private ArrayList<BDD> glueAcceptBDDs = new ArrayList<BDD>();

	private BehaviourEncoder behenc; 
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;

	public void specifyGlue(BIPGlue glue){
		
       if (glue == null) {
        try {
			throw new BIPEngineException("Glue is null");
		} catch (BIPEngineException e) {
			e.printStackTrace();
			logger.error("Did not get glue from the XML file");
		}
      }
			 
		for (Requires requires : glue.requiresConstraints)
			glueRequireBDDs.addAll(decomposeRequireGlue(requires));
		if (glueRequireBDDs.isEmpty()) {
                   try {
					throw new BIPEngineException("Glue require is empty");
                   } catch (BIPEngineException e) {
                       e.printStackTrace();
                       logger.error("Did not get the require constraints from the XML file");
               }
          }
		
		for (Accepts accept : glue.acceptConstraints)
			glueAcceptBDDs.addAll(decomposeAcceptGlue(accept));
		if (glueAcceptBDDs.isEmpty()) {
            try {
				throw new BIPEngineException("Glue accept is empty");
            } catch (BIPEngineException e) {
                e.printStackTrace();
                logger.error("Did not get the accept constraints from the XML file");
        }
   }
	}

	ArrayList<BDD> decomposeRequireGlue(Requires require) {
		ArrayList<BDD> result = new ArrayList<BDD>();

		String requireComponentType = require.effect.specType;
		ArrayList<BIPComponent> requireEffectComponents = new ArrayList<BIPComponent>();
		ArrayList<Port> causePorts = new ArrayList<Port>();
		ArrayList<BIPComponent> requireCauseComponents = new ArrayList<BIPComponent>();
		Hashtable<Port, ArrayList<BIPComponent>> portToComponents = new Hashtable<Port, ArrayList<BIPComponent>>();

		/** Find all effect component instances */
		for (int k = 0; k < wrapper.getNoComponents(); k++) {
			if (requireComponentType.equals(wrapper.getBIPComponentBehaviour(k).getComponentType())){
				requireEffectComponents.add(wrapper.getBIPComponent(k));
			}
		}

		/** Find all causes component instances */
		causePorts = require.causes;
		int sizecauseports = causePorts.size();
		String RequireCausePortComponentType;
		for (int l = 0; l < sizecauseports; l++) {
			RequireCausePortComponentType = causePorts.get(l).specType;
			for (int m = 0; m < wrapper.getNoComponents(); m++) {

				if (RequireCausePortComponentType.equals(wrapper.getBIPComponentBehaviour(m).getComponentType())){
					requireCauseComponents.add(wrapper.getBIPComponent(m));
				}
			}
			portToComponents.put(causePorts.get(l), requireCauseComponents);

		}

		int effectsize = requireEffectComponents.size();
		for (int m = 0; m < effectsize; m++) {
			result.add(componentRequire(requireEffectComponents.get(m), require.effect, causePorts, portToComponents));
		}

		return result;

	}

	ArrayList<BDD> decomposeAcceptGlue(Accepts accept) {

		ArrayList<BDD> result = new ArrayList<BDD>();
		String AcceptComponentType = accept.effect.specType;
		ArrayList<BIPComponent> AcceptEffectComponents = new ArrayList<BIPComponent>();
		ArrayList<Port> CausePorts = new ArrayList<Port>();
		ArrayList<Port> AcceptCausePorts = new ArrayList<Port>();
		ArrayList<BIPComponent> AcceptCauseComponents = new ArrayList<BIPComponent>();

		/** Find all effect component instances */
		for (int k = 0; k < wrapper.getNoComponents(); k++) {
			if (AcceptComponentType.equals(wrapper.getBIPComponentBehaviour(k).getComponentType())){
				AcceptEffectComponents.add(wrapper.getBIPComponent(k));
			}
		}

		/** Find all causes component instances */
		CausePorts = accept.causes;
		int sizecauseports = CausePorts.size();
		String AcceptCausePortComponentType;
		for (int l = 0; l < sizecauseports; l++) {
			AcceptCausePortComponentType = CausePorts.get(l).specType;
			for (int m = 0; m < wrapper.getNoComponents(); m++) {
				if (AcceptCausePortComponentType.equals(wrapper.getBIPComponentBehaviour(m).getComponentType())){
					AcceptCauseComponents.add(wrapper.getBIPComponent(m));
					AcceptCausePorts.add(CausePorts.get(l));
				}
			}
		}

		int effectsize = AcceptEffectComponents.size();
		for (int m = 0; m < effectsize; m++)
			result.add(componentAccept(AcceptEffectComponents.get(m), accept.effect, AcceptCauseComponents, AcceptCausePorts));

		return result;
	}

	/** BDD for the Require Constraint */
	BDD requireBDD(BDD RequirePortHolder, ArrayList<Port> AuxPort, Hashtable<Port, ArrayList<BDD>> RequirePorts) {
		BDD tmp, tmp2, tmp3;
		BDD aux = engine.getBDDManager().zero();
		BDD aux2 = engine.getBDDManager().one();

		ArrayList<BDD> AuxPortBDDs = new ArrayList<BDD>();
		int size = RequirePorts.size();
		for (int j = 0; j < size; j++) {
			AuxPortBDDs.addAll(RequirePorts.get(AuxPort.get(j)));
			int size2 = AuxPortBDDs.size();
			for (int i = 0; i < size2; i++) {
				BDD aux3 = engine.getBDDManager().one();
				for (int k = 0; k < size2; k++) {
					if (i == k) {
						tmp3 = AuxPortBDDs.get(k).and(aux3);
						aux3.free();
						aux3 = tmp3;
					} else {
						tmp3 = AuxPortBDDs.get(k).not().and(aux3);
						aux3.free();
						aux3 = tmp3;
					}
				}
				tmp = aux3.or(aux);
				aux.free();
				aux = tmp;
			}
			tmp2 = aux.and(aux2);
			aux2.free();
			aux2 = tmp2;
			AuxPortBDDs.clear();

		}
		BDD require_bdd = RequirePortHolder.not().or(aux2);
		aux2.free();
		return require_bdd;

	}

	/** BDD for the Accept Constraint */
	BDD acceptBDD(BDD AcceptPortHolder, ArrayList<BDD> AcceptPorts) {
		BDD tmp;
		BDD accept_bdd = engine.getBDDManager().one();
		for (int i = 0; i < wrapper.getNoComponents(); i++) {
			int length = behenc.getPortBDDs().get(i).length;
			BDD[] ComponentPortTmp = behenc.getPortBDDs().get(i);
			for (int k = 0; k < length; k++) {
				boolean exist = false;
				BDD PortK = ComponentPortTmp[k];
				for (int j = 0; j < AcceptPorts.size(); j++) {
					if (AcceptPorts.get(j) == PortK) {
						exist = true;
						break;
					}
				}
				if (!exist && PortK != AcceptPortHolder) {
					tmp = PortK.not().and(accept_bdd);
					accept_bdd.free();
					accept_bdd = tmp;
				}
			}
		}
		return accept_bdd;
	}

	/** Require BDD */
	BDD componentRequire(BIPComponent HolderComponent, Port HolderPort, ArrayList<Port> RequiredPorts, Hashtable<Port, ArrayList<BIPComponent>> EffectPorttoComponents) {

		BDD PortBDD;
		Hashtable<Port, ArrayList<BDD>> RequiredBDDs = new Hashtable<Port, ArrayList<BDD>>();
		ArrayList<BDD> PortBDDs = new ArrayList<BDD>();
		Integer CompID = wrapper.getBIPComponentIdentity(HolderComponent);
		ArrayList<Port> componentPorts = wrapper.getBIPComponentBehaviour(CompID).getEnforceablePorts();
		int PortID = 0;
		for (int i = 1; i <= componentPorts.size(); i++) {
			if (componentPorts.get(i - 1).id.equals(HolderPort.id)) {
				PortID = i;
				break;
			}
		}
		PortBDD = behenc.getPortBDDs().get(CompID)[PortID - 1];

		ArrayList<BIPComponent> RequiredComponents = new ArrayList<BIPComponent>();
		ArrayList<Port> AuxPorts = new ArrayList<Port>();
		int size = EffectPorttoComponents.size();
		for (int p = 0; p < size; p++) {
			Port port = RequiredPorts.get(p);
			RequiredComponents.addAll(EffectPorttoComponents.get(port));
			for (int i = 0; i < RequiredComponents.size(); i++) {
				Integer ComID = wrapper.getBIPComponentIdentity(RequiredComponents.get(i));
				ArrayList<Port> compPorts = wrapper.getBIPComponentBehaviour(ComID).getEnforceablePorts();
				int PID = 0;
				for (int j = 1; j <= compPorts.size(); j++) {
					if (compPorts.get(j - 1).id.equals(RequiredPorts.get(p).id)) {
						PID = j;
						break;
					}
				}
				PortBDDs.add(behenc.getPortBDDs().get(ComID)[PID - 1]);
			}
			RequiredBDDs.put(RequiredPorts.get(p), PortBDDs);
			AuxPorts.add(RequiredPorts.get(p));
		}

		BDD Require = requireBDD(PortBDD, AuxPorts, RequiredBDDs);
		return Require;
	}

	/** Accept BDD */
	BDD componentAccept(BIPComponent HolderComponent, Port HolderPort, ArrayList<BIPComponent> AcceptedComponents, ArrayList<Port> AcceptedPorts) {
		BDD PortBDD;
		ArrayList<BDD> AcceptedBDDs = new ArrayList<BDD>();
		Integer CompID = wrapper.getBIPComponentIdentity(HolderComponent);
		ArrayList<Port> componentPorts = wrapper.getBIPComponentBehaviour(CompID).getEnforceablePorts();
		int PortID = 0;
		for (int i = 1; i <= componentPorts.size(); i++) {
			if (componentPorts.get(i - 1).id.equals(HolderPort.id)) {
				PortID = i;
				break;
			}
		}

		PortBDD = behenc.getPortBDDs().get(CompID)[PortID - 1];

		for (int i = 0; i < AcceptedComponents.size(); i++) {
			Integer ComID = wrapper.getBIPComponentIdentity(AcceptedComponents.get(i));
			ArrayList<Port> compPorts = wrapper.getBIPComponentBehaviour(ComID).getEnforceablePorts();
			int PID = 0;
			for (int j = 1; j <= compPorts.size(); j++) {
				if (compPorts.get(j - 1).id.equals(AcceptedPorts.get(i).id)) {
					PID = j;
					break;
				}
			}
			AcceptedBDDs.add(behenc.getPortBDDs().get(ComID)[PID - 1]);
		}
		BDD Accept = acceptBDD(PortBDD, AcceptedBDDs);
		return Accept;
	}

	public BDD totalGlue() {

		BDD Glue;

		BDD GlueRequireBDD = engine.getBDDManager().one();
		BDD tmp;
		int requiresize = glueRequireBDDs.size();
		for (int k = 0; k < requiresize; k++) {
			tmp = GlueRequireBDD.and(glueRequireBDDs.get(k));
			GlueRequireBDD.free();
			GlueRequireBDD = tmp;
		}

		BDD GlueAcceptBDD = engine.getBDDManager().one();
		BDD tmp2;
		int acceptsize = glueAcceptBDDs.size();
		for (int k = 0; k < acceptsize; k++) {
			tmp2 = GlueAcceptBDD.and(glueAcceptBDDs.get(k));
			GlueAcceptBDD.free();
			GlueAcceptBDD = tmp2;
		}

		Glue = GlueRequireBDD;
		return Glue;

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

