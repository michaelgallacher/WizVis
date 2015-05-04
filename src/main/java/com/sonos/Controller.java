package com.sonos;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.*;
import org.apache.commons.scxml2.*;
import org.apache.commons.scxml2.model.*;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.dialog.ExceptionDialog;

import java.util.*;
import java.util.stream.Collectors;

public class Controller {
	SCXML scxml;
	SCXMLExecutor exec;
	private SimpleStringProperty currentState = new SimpleStringProperty("unset");
	//public List<String> allStates = new ArrayList<>();
	//	public List<String> currentTransitions = new ArrayList<>();


	public ObservableStringValue currentStateProperty = currentState;
	public ObservableList<String> allStatesProperty = FXCollections.observableArrayList();
	public ObservableList<String> currentTransitionsProperty = FXCollections.observableArrayList();
	private Map<String, EnterableState> allStates = new HashMap<>();

	public ObservableList<PropertySheet.Item> dataModelItems = FXCollections.observableArrayList();

	public void Initialize(SCXML _scxml) {
		scxml = _scxml;

		try {
			exec = new SCXMLExecutor();
			exec.setStateMachine(scxml);
			//	exec.addListener(<SCXML>, <SCXMLListener>);
			//	exec.setRootContext( < Context>);
			exec.go();
		} catch (ModelException me) {
			//
		}

		dataModelItems.clear();
		try {
			PropertySheet.Item item = new DataModelProperty(Integer.class, scxml.getDatamodel());
			dataModelItems.add(item);
		}
		catch (Exception e){
			new ExceptionDialog(e).showAndWait();
		}

		// Find all the reachable states in the graph.
		Queue<EnterableState> q = new LinkedList<>();
		q.addAll(scxml.getChildren());
		while (!q.isEmpty()) {
			EnterableState es = q.remove();
			if (!allStates.containsKey(es.getId())) {
				allStates.put(es.getId(), es);
				if (es instanceof TransitionalState) {
					TransitionalState ts = (TransitionalState) es;
					q.addAll(ts.getChildren());
				}
			}
		}

		// Update the allStates  property.
		allStatesProperty.clear();
		allStatesProperty.addAll(allStates.keySet().stream().sorted().collect(Collectors.toList()));

		String init = exec.getStatus().getActiveStates().iterator().next().getId();
		SetCurrentState(init);
	}

	public void FireEvent(String event) {
		try {
			TriggerEvent te = new TriggerEvent(event, TriggerEvent.SIGNAL_EVENT);
			exec.triggerEvent(te);

			SetCurrentState(exec.getStatus().getActiveStates().iterator().next().getId());
		} catch (ModelException e) {
			e.printStackTrace();
		}
	}

	private void SetCurrentState(String newCurrent) {

		// Update the property.
		currentState.setValue(newCurrent);
		currentTransitionsProperty.clear();

		EnterableState cur = allStates.get(newCurrent);
		if (!(cur instanceof TransitionalState)) {
			return;
		}

		TransitionalState ts = (TransitionalState) cur;
		for (Transition transition : ts.getTransitionsList()) {
			currentTransitionsProperty.addAll(transition.getEvents());
		}


	}

	public class DataModelProperty implements PropertySheet.Item {

		Datamodel dataModel;
		Class<?> dataType;
		public DataModelProperty(Class<?> type, Datamodel model) {
			dataModel = model;
			dataType = type;
		}
		@Override
		public Class<?> getType() {
			return dataType;
		}

		@Override
		public String getCategory() {
			return "Category";
		}

		@Override
		public String getName() {
			return "theName";
		}

		@Override
		public String getDescription() {
			return "thedesc";
		}

		@Override
		public Object getValue() {
			return 1;
		}

		@Override
		public void setValue(Object o) {

		}
	}
}
