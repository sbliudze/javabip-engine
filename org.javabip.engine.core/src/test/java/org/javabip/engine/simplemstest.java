package org.javabip.engine;
//package org.bip.engine;
//import net.sf.javabdd.BDD;
//import net.sf.javabdd.BDDFactory;
//import java.util.*;
//
//import org.bip.engine.Engine;
//
//public class simplemstest {
//	
//
//	//for this example each one will have 4 elements
//	//M1, M2, S1, S2		
//     static Hashtable <Integer, BDD> states = new Hashtable<Integer, BDD>(); //probably change it to ArrayList<BDD> later
//     static Hashtable <Integer, BDD> ports = new Hashtable<Integer, BDD>();
//     static Hashtable <Integer, BDD> FSMs = new Hashtable<Integer, BDD>(); //
//     static Hashtable <Integer, BDD> CSs = new Hashtable<Integer, BDD>(); //for the current state BDDs
//     static BDD FSM; //conjunction of FSMs
//     
//	
//	
//	static void CreateBDDNodes(int nofstates, int nofports, int sum, int ComponentID){
//		
//		for (int i=0;i<nofstates;i++){
//			//create new variable in the BDD manager for the state of each component instance
//			states.put(ComponentID, (Engine.bdd_mgr.ithVar(i+sum))); 
//			
//		}
//		for (int j=0;j<nofports;j++){
//			//create new variable in the BDD manager for the port of each component instance
//			ports.put(ComponentID, Engine.bdd_mgr.ithVar(j+nofstates+sum));
//		}	
//	}
//	
//	static void FSMBDDs(int ComponentID){
//		BDD tmp=states.get(ComponentID).and(ports.get(ComponentID));
//		FSMs.put(ComponentID, tmp.or(ports.get(ComponentID).not())); 	
//		tmp.free();
//	}
//	
//
//	static void CurrentStateBDDs(int ComponentID){
//		CSs.put(ComponentID, states.get(ComponentID)); 
//	}
//
//
//	public static void main(String[] args) {
//		Engine.bdd_mgr = BDDFactory.init("java", 30, 30);
//		Engine engine = new Engine();
//		int nofComponents=3;
//		int nofstates=1;
//		int nofports=1;
//			
//		//1.Create BDD nodes for the states and ports of every component
//		int AuxSum=0;	
//		if (Engine.bdd_mgr.varNum() < 6) Engine.bdd_mgr.setVarNum(6);
//		 for (int i=1; i<=nofComponents;i++){
//			CreateBDDNodes(nofstates,nofports, AuxSum, i);
//			AuxSum=AuxSum+nofstates+nofports;
//		 }
//
//		//2. FSMs BDDs of every component
//		 for (int i=1; i<=nofComponents;i++)
//				FSMBDDs(i);
//		 
//		//3. conjunction of all FSM BDDs (Λi Fi)
//		 BDD temp_FSM;
//		 FSM = Engine.bdd_mgr.one();
//		 //FSM.isOne();
//		 for (int k=1; k<=nofComponents;k++){
//			 temp_FSM=FSMs.get(k).and(FSM);
//			 FSM.free();
//			 FSM = temp_FSM; //no need to free temp_FSM
//		 }
//		 		
//		 //4. Glue
////	BDD tmp= (ports.get(1).not()).or(ports.get(3));
////		 //Glue.andWith(tmp2);
////	BDD tmp2=(ports.get(2).not()).or(ports.get(3));
////	BDD Glue=tmp.and(tmp2);
////	tmp2.free();
////	tmp.free();
//		 //BDD Glue=ports.get(3);
//		 //BDD Glue=Engine.bdd_mgr.one();
//		 //BDD Glue=(ports.get(1).not().and(ports.get(2).not())).or(ports.get(1).and(ports.get(3))).or(ports.get(2).and(ports.get(3)));
//		 BDD Glue=(ports.get(1).not().and(ports.get(2).not())).or(ports.get(1).and(ports.get(3)).and(ports.get(2)));
//		 //BDD Glue=(ports.get(2).not().and(ports.get(1).not())).or(ports.get(3)).or(ports.get(1).and(ports.get(3))).or(ports.get(2).and(ports.get(3)));
//		 
//		 
//		//5. Λi Fi Λ G 
//		//BDD FSMGLUE;
//		//FSMGLUE=FSM.and(Glue);
//		engine.informFSM(FSM);
//		engine.informGlue(Glue);
//		
//		//FSM.free();
//		//Glue.free();
//			 
//		
//		//6. CurrentState BDDs
////		 for (int i=1; i <=nofComponents; i++){
////				CurrentStateBDDs(i); 
////				engine.informCS(i, CSs.get(i));
////		 }
//
//		 engine.run();
//		
//	}
//
//}
//
