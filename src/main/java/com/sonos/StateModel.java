package com.sonos;

import org.apache.commons.scxml2.model.*;

import java.util.*;

public class StateModel {
	private String name;
	private List<TransitionModel> transitions = new ArrayList<>();

	public StateModel(EnterableState state) {
		name = state.getId();
		if (state instanceof TransitionalState) {
			TransitionalState tstate = (TransitionalState) state;
			tstate.getTransitionsList().forEach(t -> transitions.add(new TransitionModel(t.getEvent(), t.getCond())));
		}
	}


	public String getName() { return name;}


	public List<TransitionModel> getTransitions() {return transitions;}
}
