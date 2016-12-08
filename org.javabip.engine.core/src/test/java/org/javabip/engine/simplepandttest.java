package org.javabip.engine;
//package org.bip.engine;
//import net.sf.javabdd.BDD;
//import net.sf.javabdd.BDDFactory;
//import java.util.*;
//
//import org.bip.engine.Engine;
//
//public class simplepandttest {
//	
//
//	//for this example each one will have 4 elements
//	//M1, M2, S1, S2		
//     static Hashtable <Integer, BDD> states = new Hashtable<Integer, BDD>(); //probably change it to ArrayList<BDD> later
//     static Hashtable <Integer, BDD[]> ports = new Hashtable<Integer, BDD[]>();
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
//		BDD[] a1 = new BDD[nofports];
//		for (int j=0;j<nofports;j++){
//			//create new variable in the BDD manager for the port of each component instance
//			a1[j]=Engine.bdd_mgr.ithVar(j+nofstates+sum);
//		}	
//		ports.put(ComponentID, a1);
//		
//	}
//	
//	static void FSMBDDs(int ComponentID){
//		BDD tmp1=Engine.bdd_mgr.zero();
//		int k=ports.get(ComponentID).length;
//		System.out.println("Length of ports: "+k);
//		BDD tmp2=states.get(ComponentID);
//		
//		
//		if(k==2){
//			BDD a=ports.get(ComponentID)[0];
//			BDD b=ports.get(ComponentID)[1];	
//			BDD nda=tmp2.and(a);
//			nda.andWith(b.not());
//			tmp1.orWith(nda);
//			BDD nda2=tmp2.and(b);
//			nda2.andWith(a.not());
//			tmp1.orWith(nda2);
//			BDD nda3=b.not().and(a.not());
//			tmp1.orWith(nda3);
//			}
//		
//		if(k==1){
//			BDD a=ports.get(ComponentID)[0];
//			BDD nda=tmp2.and(a);
//			tmp1.orWith(nda);
//			BDD nda2=a.not().and(tmp1);
//			tmp1.orWith(nda2);
//			}
//
//		FSMs.put(ComponentID, tmp1); 	
//		System.out.println("FSM of Component stored: "+ComponentID);
////tmp2.free();
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
//		int nofports=2;
//			
//		//1.Create BDD nodes for the states and ports of every component
//		int AuxSum=0;	
//		if (Engine.bdd_mgr.varNum() < 8) Engine.bdd_mgr.setVarNum(8);
//		 for (int i=1; i<=3;i++){
//			 if(i==3)nofports=1;
//			CreateBDDNodes(nofstates,nofports, AuxSum, i);
//			AuxSum=AuxSum+nofstates+nofports;
//		 }
//
//		//2. FSMs BDDs of every component
//		 for (int i=1; i<=nofComponents;i++){
//			 FSMBDDs(i);
//		 }
//			 
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
//		BDD tmp=Engine.bdd_mgr.zero();
//		BDD c=ports.get(3)[0];
//		BDD tmp2=tmp.or(c);
//		
//		BDD a1=ports.get(1)[0];
//		BDD ca1=a1.and(c);
//		a1.free();
//		BDD tmp3=tmp2.or(ca1);
//		
//		BDD a2=ports.get(2)[0];
//		BDD ca2=a2.and(c);
//		a2.free();
//		BDD Glue=tmp3.or(ca2);
//		
//		BDD b1=ports.get(1)[1];
//		BDD cb1=b1.and(c);
//		Glue.orWith(cb1);
//		BDD ca2b1=ca2.and(b1);
//		Glue.orWith(ca2b1);
//		
//		BDD b2=ports.get(2)[1];
//		BDD cb2=b2.and(c);
//		Glue.orWith(cb2);
//		BDD ca1b2=ca1.and(b2);
//		Glue.orWith(ca1b2);
//		b1.free();
//		b2.free();
//		ca1.free();
//		ca2.free();
//
//		
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
//		 engine.run();
//		
//	}
//
//}
//
