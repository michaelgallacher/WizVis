package com.sonos;

import javafx.beans.binding.ListBinding;
import javafx.beans.property.*;
import javafx.collections.*;
import org.apache.commons.scxml2.io.SCXMLReader;
import org.apache.commons.scxml2.model.*;
import org.controlsfx.control.PropertySheet;
import org.json.simple.parser.ParseException;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class Controller {
	private StateMachine stateMachine = new StateMachine();

	public ObservableList<String> allStatesProperty = FXCollections.observableArrayList();
	public SimpleListProperty<StateModel> currentStatesProperty = new SimpleListProperty<>(FXCollections.observableArrayList());;
	public ObservableList<PropertySheet.Item> dataModelItems = FXCollections.observableArrayList();

	@SuppressWarnings("deprecation,unchecked")
	public void Initialize(String path) throws IOException, ModelException, XMLStreamException, ParseException {

		File file = new File(path);
		SCXML scxml = SCXMLReader.read(file.toURL());
		Data d = scxml.getDatamodel().getData().get(0);
		String jsonPath = d.getSrc();
		if (jsonPath != null) {
			Path tmpPath = file.toPath().getParent().resolve(jsonPath);
			jsonPath = tmpPath.toString();
		}

		stateMachine.Initialize(scxml, jsonPath);

		dataModelItems.clear();
		Set<Map.Entry> entries = stateMachine.getDatamodelJSON().entrySet();
		for (Map.Entry entry : entries) {
			PropertySheet.Item item = new DataModelProperty(entry.getKey().toString(), entry.getValue().toString(), String.class);
			dataModelItems.add(item);
		}

		currentStatesProperty.bindContent(stateMachine.currentStatesProperty);

		allStatesProperty.clear();
		allStatesProperty.addAll(stateMachine.allStatesProperty);
	}

	public boolean Eval(String expr)
	{
		return stateMachine.Eval(expr);
	}

	public void FireEvent(String event) {
		stateMachine.FireEvent(event);
	}

	public class DataModelProperty implements PropertySheet.Item {

		Datamodel dataModel;
		Class<?> dataType;
		String dataName;
		String dataValue;

		public DataModelProperty(String name, String value, Class<?> type) {//}, Datamodel model) {
			//	dataModel = model;
			dataType = type;
			dataName = name;
			dataValue = value;
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
			return dataName;
		}

		@Override
		public String getDescription() {
			return dataName;
		}

		@Override
		public Object getValue() {
			return dataValue;
		}

		@Override
		public void setValue(Object o) {

		}
	}
}
