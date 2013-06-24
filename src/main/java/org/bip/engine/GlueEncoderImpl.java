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

		String acceptComponentType = accept.effect.specType;
		ArrayList<BIPComponent> acceptEffectComponents = new ArrayList<BIPComponent>();
		ArrayList<Port> causePorts = new ArrayList<Port>();
		ArrayList<BIPComponent> acceptCauseComponents = new ArrayList<BIPComponent>();
		Hashtable<Port, ArrayList<BIPComponent>> portToComponents = new Hashtable<Port, ArrayList<BIPComponent>>();

		/** Find all effect component instances */
		for (int k = 0; k < wrapper.getNoComponents(); k++) {
			if (acceptComponentType.equals(wrapper.getBIPComponentBehaviour(k).getComponentType())){
				acceptEffectComponents.add(wrapper.getBIPComponent(k));
			}
		}

		/** Find all causes component instances */
		causePorts = accept.causes;
		int sizecauseports = causePorts.size();
		String acceptCausePortComponentType;
		for (int l = 0; l < sizecauseports; l++) {
			acceptCausePortComponentType = causePorts.get(l).specType;
			for (int m = 0; m < wrapper.getNoComponents(); m++) {

				if (acceptCausePortComponentType.equals(wrapper.getBIPComponentBehaviour(m).getComponentType())){
					acceptCauseComponents.add(wrapper.getBIPComponent(m));
				}
			}
			portToComponents.put(causePorts.get(l), acceptCauseComponents);

		}

		int effectsize = acceptEffectComponents.size();
		for (int m = 0; m < effectsize; m++) {
			result.add(componentAccept(acceptEffectComponents.get(m), accept.effect, causePorts, portToComponents));
		}

		return result;

	}



	/** BDD for the Require Constraint */
	BDD requireBDD(BDD RequirePortHolder, ArrayList<Port> AuxPort, Hashtable<Port, ArrayList<BDD>> RequirePorts) {
		BDD tmp, tmp2, tmp3;
		BDD aux = engine.getBDDManager().zero();
		BDD aux2 = engine.getBDDManager().one();

		ArrayList<BDD> auxPortBDDs = new ArrayList<BDD>();
		int size = RequirePorts.size();
		for (int j = 0; j < size; j++) {
			auxPortBDDs.addAll(RequirePorts.get(AuxPort.get(j)));
			int size2 = auxPortBDDs.size();
			for (int i = 0; i < size2; i++) {
				BDD aux3 = engine.getBDDManager().one();
				for (int k = 0; k < size2; k++) {
					if (i == k) {
						tmp3 = auxPortBDDs.get(k).and(aux3);
						aux3.free();
						aux3 = tmp3;
					} else {
						tmp3 = auxPortBDDs.get(k).not().and(aux3);
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
			auxPortBDDs.clear();

		}
		BDD require_bdd = RequirePortHolder.not().or(aux2);
		aux2.free();
		return require_bdd;


	}

	/** BDD for the Accept Constraint */
	BDD acceptBDD(BDD acceptPortHolder, ArrayList<Port> auxPort, Hashtable<Port, ArrayList<BDD>> acceptPorts) {
		BDD tmp;
		BDD accept_bdd = engine.getBDDManager().one();
		int portBDDsize = behenc.getPortBDDs().size();
		ArrayList<BDD> totalPortBDDs= new ArrayList<BDD>();
		
		for (int i = 0; i < portBDDsize; i++) {
			BDD [] portBDD=behenc.getPortBDDs().get(i);
			for (int p=0; p<portBDD.length;p++){
				totalPortBDDs.add(portBDD[p]);
			}
		}
				
		for(int i=0; i<totalPortBDDs.size();i++){
				boolean exist = false;
				for (int j = 0; j < acceptPorts.size(); j++) {
					ArrayList<BDD> acceptBDDs=acceptPorts.get(auxPort.get(j));
					for(int k=0; k< acceptBDDs.size(); k++){				
						if (acceptBDDs.get(k).equals(totalPortBDDs.get(i))) {
							exist = true;
							break;
						}
				}
					if((totalPortBDDs.get(i)).equals(acceptPortHolder)){
						exist=true;
					}
				if (!exist) {
					tmp = totalPortBDDs.get(i).not().and(accept_bdd);
					accept_bdd.free();
					accept_bdd = tmp;
				}
			}
		}
		BDD acc_bdd = acceptPortHolder.not().or(accept_bdd);
		accept_bdd.free();
		return acc_bdd;
	}

	/** Require BDD */
	BDD componentRequire(BIPComponent HolderComponent, Port HolderPort, ArrayList<Port> RequiredPorts, Hashtable<Port, ArrayList<BIPComponent>> EffectPorttoComponents) {

		BDD PortBDD;
		Hashtable<Port, ArrayList<BDD>> RequiredBDDs = new Hashtable<Port, ArrayList<BDD>>();
		ArrayList<BDD> PortBDDs = new ArrayList<BDD>();
		Integer CompID = wrapper.getBIPComponentIdentity(HolderComponent);
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(CompID).getEnforceablePorts();
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
				ArrayList<Port> compPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(ComID).getEnforceablePorts();
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
	BDD componentAccept(BIPComponent HolderComponent, Port HolderPort, ArrayList<Port> acceptedPorts, Hashtable<Port, ArrayList<BIPComponent>> EffectPorttoComponents) {

		BDD PortBDD;
		Hashtable<Port, ArrayList<BDD>> acceptedBDDs = new Hashtable<Port, ArrayList<BDD>>();
		ArrayList<BDD> PortBDDs = new ArrayList<BDD>();
		Integer CompID = wrapper.getBIPComponentIdentity(HolderComponent);
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(CompID).getEnforceablePorts();
		int PortID = 0;
		for (int i = 1; i <= componentPorts.size(); i++) {
			if (componentPorts.get(i - 1).id.equals(HolderPort.id)) {
				PortID = i;
				break;
			}
		}
		PortBDD = behenc.getPortBDDs().get(CompID)[PortID - 1];

		ArrayList<BIPComponent> acceptedComponents = new ArrayList<BIPComponent>();
		ArrayList<Port> AuxPorts = new ArrayList<Port>();
		int size = EffectPorttoComponents.size();
		for (int p = 0; p < size; p++) {
			Port port = acceptedPorts.get(p);
			acceptedComponents.addAll(EffectPorttoComponents.get(port));
			for (int i = 0; i < acceptedComponents.size(); i++) {
				Integer ComID = wrapper.getBIPComponentIdentity(acceptedComponents.get(i));
				ArrayList<Port> compPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(ComID).getEnforceablePorts();
				int PID = 0;
				for (int j = 1; j <= compPorts.size(); j++) {
					if (compPorts.get(j - 1).id.equals(acceptedPorts.get(p).id)) {
						PID = j;
						break;
					}
				}
				PortBDDs.add(behenc.getPortBDDs().get(ComID)[PID - 1]);
			}
			acceptedBDDs.put(acceptedPorts.get(p), PortBDDs);
			AuxPorts.add(acceptedPorts.get(p));
		}

		BDD accept = acceptBDD(PortBDD, AuxPorts, acceptedBDDs);
		return accept;
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

		//Glue=GlueRequireBDD;
		Glue = GlueRequireBDD.and(GlueAcceptBDD);
		
		if (Glue == null) {
	        try {
				throw new BIPEngineException("Glue BDD is null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				logger.error("Glue BDD was not computed correctly");
			}
	      }
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

