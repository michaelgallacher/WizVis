package com.sonos;


import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import org.apache.commons.scxml2.SCXMLExecutor;
import org.apache.commons.scxml2.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class SCXmlModel {
	SCXML scxml;
	Map<String,TransitionTarget> targets;

	public SimpleStringProperty currentState = new SimpleStringProperty("unset");

	public ObservableList<String> allStates = FXCollections.observableArrayList();
	public ObservableList<String> currentTransitions = FXCollections.observableArrayList();

	public SCXmlModel(SCXML scxmlParam) {
		this.scxml = scxmlParam;

		SCXMLExecutor exec;
		try {
			exec = new SCXMLExecutor();
			exec.setStateMachine(scxml);
			//	exec.addListener(<SCXML>, <SCXMLListener>);
			//	exec.setRootContext( < Context>);
			exec.go();
		} catch (ModelException me) {
			// Executor initialization failed, because the
			// state machine specified has inconsistencies
		}

		targets = scxml.getTargets();

		currentTransitions.clear();
		currentTransitions.addAll(targets.keySet());

		List<String> stateList = targets.values().stream().map(TransitionTarget::getId).collect(Collectors.toList());
		allStates.clear();
		allStates.addAll(stateList);

		String s = scxml.getInitial();
		currentState.set(s);

	}

	public String getInitial() { return scxml.getInitial(); }

}
