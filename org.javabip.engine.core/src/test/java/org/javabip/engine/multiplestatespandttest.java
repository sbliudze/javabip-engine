package org.javabip.engine;
//package org.bip.engine;
//import net.sf.javabdd.BDD;
//import net.sf.javabdd.BDDFactory;
//import java.util.*;
//
//import org.bip.engine.Engine;
//
//public class multiplestatespandttest {
//		
//     static Hashtable <Integer, BDD[]> states = new Hashtable<Integer, BDD[]>();
//     static Hashtable <Integer, BDD[]> ports = new Hashtable<Integer, BDD[]>();
//     static Hashtable <Integer, BDD> FSMs = new Hashtable<Integer, BDD>(); //
//     static Hashtable <Integer, BDD> CSs = new Hashtable<Integer, BDD>(); //for the current state BDDs
//     static BDD FSM; //conjunction of FSMs
//     
//	
//	
//	static void CreateBDDNodes(int nofstates, int nofports, int sum, int ComponentID){
//		BDD[] a2 = new BDD[nofstates];
//		for (int i=0;i<nofstates;i++){
//			//create new variable in the BDD manager for the state of each component instance
//			a2[i]=Engine.bdd_mgr.ithVar(i+sum);
//		}
//		states.put(ComponentID, a2);
//		
//		BDD[] a1 = new BDD[nofports];
//		for (int j=0;j<nofports;j++){
//			//create new variable in the BDD manager for the port of each component instance
//			a1[j]=Engine.bdd_mgr.ithVar(j+nofstates+sum);
//		}	
//		ports.put(ComponentID, a1);	
//	}
//	
//	static void FSMBDDs(int ComponentID){
//		BDD tmp1;
//		int nop=ports.get(ComponentID).length;
//		int nos=states.get(ComponentID).length;
//		System.out.println("Number of ports: "+nop+"  Number of states: "+nos);
//		
//		BDD [] B_ports= new BDD [nop];
//		BDD [] B_states = new BDD [nos];
//		
//		for (int i=0; i< nop; i++){
//			B_ports[i]=ports.get(ComponentID)[i];
//		}
//		for (int j=0; j< nos; j++){
//			B_states[j]=states.get(ComponentID)[j];
//		}
//		
//		if(nop==3){
//			BDD c1 =B_states[0].and(B_states[1].not()).and(B_ports[0]).and(B_ports[1].not()).and(B_ports[2].not());
//			BDD c2 =(B_states[0].not()).and(B_states[1]).and(B_ports[0]).and(B_ports[1].not()).and(B_ports[2].not());
//			BDD c3 =(B_states[0].not()).and(B_states[1]).and(B_ports[0].not()).and(B_ports[1]).and(B_ports[2].not());
//			BDD c4 =(B_states[0].not()).and(B_states[1]).and(B_ports[0].not()).and(B_ports[1].not()).and(B_ports[2]);
//			BDD c5 =B_ports[0].not().and(B_ports[1].not()).and(B_ports[2].not());
//			tmp1=c1.or(c2).or(c3).or(c4).or(c5);
//			c1.free(); c2.free(); c3.free(); c4.free(); c5.free();
//			}
//		
//		else {
//			BDD c1 =B_states[0].and(B_ports[0]).and(B_ports[1].not());
//			BDD c2 =B_states[0].and(B_ports[0].not()).and(B_ports[1]);
//			BDD c3 =B_ports[0].not().and(B_ports[1].not());
//			tmp1=c1.or(c2).or(c3);
//			c1.free(); c2.free(); c3.free();
//			}
//
//		//TODO: free B_ports and B_states
//		FSMs.put(ComponentID, tmp1); 	
//		System.out.println("FSM of Component stored: "+ComponentID);
//	}
//	
//
//	static void CurrentStateBDDs(int ComponentID){
//		
//		if (states.get(ComponentID).length==2)  CSs.put(ComponentID, states.get(ComponentID)[1].and(states.get(ComponentID)[0].not())); 
//		if (states.get(ComponentID).length==1)  CSs.put(ComponentID, states.get(ComponentID)[0]); 
//	}
//
//
//	public static void main(String[] args) {
//		Engine.bdd_mgr = BDDFactory.init("java", 30, 30);
//		Engine engine = new Engine();
//		int nofComponents=3;
//		int nofstates=2;
//		int nofports=3;
//			
//		//1.Create BDD nodes for the states and ports of every component
//		int AuxSum=0;	
//		if (Engine.bdd_mgr.varNum() < 13) Engine.bdd_mgr.setVarNum(13);
//		 for (int i=1; i<=3;i++){
//			 if(i==3){
//				 nofports=2;
//				 nofstates=1;
//			 }
//			CreateBDDNodes(nofstates,nofports, AuxSum, i);
//			AuxSum=AuxSum+nofstates+nofports;
//		 }
//		//2. FSMs BDDs of every component
//		 for (int i=1; i<=nofComponents;i++){
//			 FSMBDDs(i);
//		 }	 
//		 
//		//3. conjunction of all FSM BDDs (Λi Fi)
//		 BDD temp_FSM;
//		 FSM = Engine.bdd_mgr.one();
//		 for (int k=1; k<=nofComponents;k++){
//			 System.out.println("k= "+k);
//			 temp_FSM=FSMs.get(k).and(FSM);
//			 FSM.free();
//			 FSM = temp_FSM; //no need to free temp_FSM
//		 }
//		 		
//		 //4. Glue
//		BDD c=ports.get(3)[0];
//		BDD d1=ports.get(1)[0];
//		BDD d2=ports.get(2)[0];
//		BDD e=ports.get(3)[1];		
//		BDD a1=ports.get(1)[1];
//		BDD a2=ports.get(2)[1];
//		BDD b1=ports.get(1)[2];
//		BDD b2=ports.get(2)[2];
//		
//		BDD Glue=(c.and(d1.not()).and(d2.not())).or(e.and(d1).and(d2.not())).or(e.and(d2).and(d1.not())).or(c.and(a1)).or(c.and(a2)).or(c.and(b1).and(b2.not())).or(c.and(b2).and(b1.not()));
//	
//		c.free();
//		d1.free();
//		d2.free();
//		e.free();
//		a1.free();
//		a2.free();
//		b1.free();
//		b2.free();		 
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
//		 engine.run();
//		
//	}
//
//}
//
