package com.sonos;


import org.apache.commons.scxml2.model.Transition;

public class TransitionModel {
	private String event;
	private String condition;
	private String target;

	public TransitionModel(Transition t) {
		event = t.getEvent();
		condition = t.getCond();
		target = t.getTargets().iterator().next().getId();
	}

	public String getEvent() {return event;}

	public String getCond() { return condition;}

	public String getTarget() { return target; }
}
