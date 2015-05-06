package com.sonos;

import org.apache.commons.scxml2.model.*;

import java.util.*;

class StateModel {
	private final String id;
	private final List<TransitionModel> transitions = new ArrayList<>();

	StateModel(EnterableState state) {
		id = state.getId();
		if (state instanceof TransitionalState) {
			TransitionalState tstate = (TransitionalState) state;
			tstate.getTransitionsList().forEach(t -> transitions.add(new TransitionModel(t)));
		}
	}

	String getId() { return id;}

	List<TransitionModel> getTransitions() {return transitions;}
}
