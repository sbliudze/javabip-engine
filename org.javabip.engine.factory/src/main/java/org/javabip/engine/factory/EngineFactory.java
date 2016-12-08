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
 * Author: Simon Bliudze, Anastasia Mavridou, Radoslaw Szymanek and Alina Zolotukhina
 */
package org.javabip.engine.factory;

import org.javabip.api.BIPEngine;
import org.javabip.api.BIPGlue;
import org.javabip.engine.BDDBIPEngineImpl;
import org.javabip.engine.BehaviourEncoderImpl;
import org.javabip.engine.CurrentStateEncoderImpl;
import org.javabip.engine.DataEncoderImpl;
import org.javabip.engine.GlueEncoderImpl;
import org.javabip.engine.api.BDDBIPEngine;
import org.javabip.engine.api.BIPCoordinator;
import org.javabip.engine.api.BehaviourEncoder;
import org.javabip.engine.api.CurrentStateEncoder;
import org.javabip.engine.api.DataEncoder;
import org.javabip.engine.api.GlueEncoder;
import org.javabip.engine.coordinator.BIPCoordinatorImpl;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.japi.Creator;

/**
 * Responsible for creating a BIP Engine actor either as BIP Coordinator or as Data Coordinator.
 *
 */
public class EngineFactory {
	ActorSystem actorSystem;

	public EngineFactory(ActorSystem actorSystem) {
		this.actorSystem = actorSystem;
	}

	public BIPEngine create(String id, BIPGlue glue) {

		GlueEncoder glueenc = new GlueEncoderImpl();
		BehaviourEncoder behenc = new BehaviourEncoderImpl();
		CurrentStateEncoder currstenc = new CurrentStateEncoderImpl();
		BDDBIPEngine bddBIPEngine = new BDDBIPEngineImpl();

		BIPCoordinator basicCoordinator = new BIPCoordinatorImpl(actorSystem, glueenc, behenc, currstenc, bddBIPEngine);

		BIPEngine bipEngine;

		if (glue.getDataWires().size() == 0) {
			bipEngine = basicCoordinator;
		} else {
			DataEncoder dataEncoder = new DataEncoderImpl();
			bipEngine = new org.javabip.engine.coordinator.DataCoordinatorKernel(basicCoordinator, dataEncoder);
		}

		final BIPEngine engine = bipEngine;

		BIPEngine actor = TypedActor.get(actorSystem).typedActorOf(
				new TypedProps<BIPEngine>(BIPEngine.class, new Creator<BIPEngine>() {
					public BIPEngine create() {
						return engine;
					}
				}), id);

		// TODO: make the DataCoordinatorImpl implement this function (after refactoring the coordinators)
		// executor.setProxy(actor);
		actor.initialize();

		actor.specifyGlue(glue);

		return actor;
	}

	public boolean destroy(BIPEngine engine) {

		// TODO EXTENSION when it is possible to deregister a component from BIP engine make sure it happens here.
		// executor.engine().deregister();

		if (TypedActor.get(actorSystem).isTypedActor(engine)) {
			TypedActor.get(actorSystem).poisonPill(engine);
			// TypedActor.get(actorSystem).stop(engine);
			// Future<Boolean> stopped =
			// gracefulStop(TypedActor.get(actorSystem).getActorRefFor(engine),
			// Duration.create(5, TimeUnit.SECONDS), actorSystem);
			// try {
			// Await.result(stopped, Duration.create(5, TimeUnit.SECONDS));
			// } catch (Exception e) {
			// // System.out.println("Engine not destroyed within a TimeOut");
			// // e.printStackTrace();
			// // System.out.flush();
			// }
			return true;
		} else {
			return false;
		}

	}

}
