package com.sonos;

import org.apache.commons.scxml2.model.*;

import java.util.*;

public class StateModel {
	private String id;
	private List<TransitionModel> transitions = new ArrayList<>();

	public StateModel(EnterableState state) {
		id = state.getId();
		if (state instanceof TransitionalState) {
			TransitionalState tstate = (TransitionalState) state;
			tstate.getTransitionsList().forEach(t -> transitions.add(new TransitionModel(t)));
		}
	}

	public String getId() { return id;}

	public List<TransitionModel> getTransitions() {return transitions;}
}
