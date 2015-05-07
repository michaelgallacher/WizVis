package com.sonos;

import javafx.beans.property.SimpleListProperty;
import javafx.collections.*;
import jdk.nashorn.internal.runtime.*;
import org.apache.commons.scxml2.*;
import org.apache.commons.scxml2.Context;
import org.apache.commons.scxml2.env.javascript.*;
import org.apache.commons.scxml2.model.*;
import org.codehaus.groovy.antlr.UnicodeEscapingReader;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import javax.script.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

final class StateMachine {

	/**
	 * The list of active (current) states.
	 */
	public ObservableList<TransitionsModel> activeStatesProperty = new SimpleListProperty<>(FXCollections.observableArrayList());

	/**
	 * A model of the parent/child relationship for all the states.
	 */
	public ObservableList<StateTreeModel> stateTreeModelProperty = FXCollections.observableArrayList();

	private SCXMLExecutor exec;
	private ScriptEngine engine;
	private String dataModelId;
	Map<String, EnterableState> idToStateMap = new HashMap<>();

	/**
	 * Assign a value in the datamodel.
	 *
	 * @param name The name of the property, without the data model prefix (i.e., 'dm.').
	 * @param val  The value to assign.  Quotes will be added as necessary.
	 */
	public void assignDataValue(String name, Object val) throws ScriptException {
		String valString = val.toString().replace("\\", "\\\\");
		if (!valString.toLowerCase().equals("true") && !valString.toLowerCase().equals("false")) {
			valString = "\"" + valString + "\"";
		}

		System.out.println("setting " + name + " to " + valString);
		engine.eval(dataModelId + "." + name + "=" + valString);

		// Notify listeners there may be a new state.
		onActiveStatesChanged();
	}

	/**
	 * Causes an event to be triggered in the state machine.
	 *
	 * @param event The event name.
	 */
	public void fireEvent(String event) throws ModelException {
		TriggerEvent te = new TriggerEvent(event, TriggerEvent.SIGNAL_EVENT);
		exec.triggerEvent(te);

		// Notify listeners there may be a new state.
		onActiveStatesChanged();
	}

	/**
	 * The JSON representation of the current data model.
	 *
	 * @return JSON data from the imported file.
	 */
	public JSONObject getDatamodelJSON() {
		if (dataModelId == null || dataModelId.isEmpty()) {
			return new JSONObject();
		}
		return (JSONObject) engine.get(dataModelId);
	}

	/**
	 * This is essentially the constructor for the class.
	 * All data should be cleared and re-created if appropriate.
	 *
	 * @param scxml    The SCXML model
	 * @param jsonPath The full path to the JSON datamodel file.
	 *
	 * @throws IOException
	 * @throws ParseException
	 * @throws ModelException
	 */
	public void initialize(SCXML scxml, String jsonPath) throws IOException, ParseException, ModelException {

		// Get the datamodel ID from the xml.
		dataModelId = null;
		Datamodel dm = scxml.getDatamodel();
		if (dm != null) {
			Data data = dm.getData().get(0);
			dataModelId = data.getId();
		}

		engine = null;
		// Read the data model json.
		if (jsonPath != null) {
			byte[] encodedBaseline = Files.readAllBytes(Paths.get(jsonPath));
			String jsonDataModelBaseline = new String(encodedBaseline, Charset.defaultCharset());
			JSONObject jsonDataBaseline = (JSONObject) new JSONParser().parse(jsonDataModelBaseline);

			engine = new ScriptEngineManager().getEngineByName("JavaScript");
			engine.put(dataModelId, jsonDataBaseline);
		}

		exec = new SCXMLExecutor(new MyJSEvaluator(), null, null);
		exec.setStateMachine(scxml);
		exec.go();

		idToStateMap.clear();

		// Create a map of all the reachable states in the graph.
		Queue<EnterableState> q = new LinkedList<>();
		q.addAll(scxml.getChildren());
		while (!q.isEmpty()) {
			EnterableState es = q.remove();
			if (!idToStateMap.containsKey(es.getId())) {
				idToStateMap.put(es.getId(), es);
				if (es instanceof TransitionalState) {
					TransitionalState ts = (TransitionalState) es;
					q.addAll(ts.getChildren());
				}
			}
		}

		stateTreeModelProperty.clear();
		List<StateTreeModel> tree = createStateTreeModel(scxml.getChildren());
		stateTreeModelProperty = FXCollections.observableArrayList(tree);

		// Notify listeners there may be a new state.
		onActiveStatesChanged();
	}

	/**
	 * Evaluates the javascript expression and returns whether it is true.
	 *
	 * @param expr The javascript expression.
	 *
	 * @return true if the javascript expression evaluates to true.
	 */
	public boolean isExpressionTrue(String expr) throws ScriptException {
		// Couldn't find a decent escape method in under two minutes so this will have to do.
		expr = expr.replace("\\", "\\\\");
		Object result = engine.eval(expr);
		if (result instanceof Boolean) {
			System.out.println("evaluated: " + expr + " as " + result.toString());
			return (Boolean) result;
		}
		return false;
	}

	/**
	 * Announce that something about the active states have changed so UI can update.
	 */
	private void onActiveStatesChanged() {
		activeStatesProperty.clear();
		Set<EnterableState> states = exec.getStatus().getActiveStates();
		// Turn the list into a list of StateModels
		activeStatesProperty.addAll(states.stream().map(TransitionsModel::new).collect(Collectors.toList()));

		// Sort longest to shortest.  Fragile since it relies on embedded states to have longer
		// names than their parent.  The alternative is to do parent child lookups.
		activeStatesProperty.sort((o1, o2) -> {
			EnterableState s1 = idToStateMap.get(o1.getId());
			EnterableState s2 = idToStateMap.get(o2.getId());

			EnterableState cur = s1.getParent();
			while (cur != null) {
				if (cur.getId().equals(s2.getId())) { return -1; }
				cur = cur.getParent();
			}
			cur = s2.getParent();
			while (cur != null) {
				if (s2.getParent().getId().equals(s1.getId())) { return 1; }
				cur = cur.getParent();
			}
			return 0;
		});
	}

	private List<StateTreeModel> createStateTreeModel(List<EnterableState> states) {
		List<StateTreeModel> parents = new ArrayList<>();
		for (EnterableState state : states) {
			StateTreeModel parent = new StateTreeModel(state.getId());
			parents.add(parent);
			if (state instanceof TransitionalState) {
				TransitionalState ts = (TransitionalState) state;
				parent.addChildren(createStateTreeModel(ts.getChildren()));
			}
		}
		return parents;
	}

	private final class MyJSEvaluator extends JSEvaluator {
		@Override
		public Object eval(Context context, String expression) throws SCXMLExpressionException {
			try {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				JSBindings jsBindings = new JSBindings(context, bindings);
				return engine.eval(expression, jsBindings);
			} catch (Exception x) {
				throw new SCXMLExpressionException("Error evaluating ['" + expression + "'] " + x);
			}
		}

		@Override
		public void evalAssign(final Context ctx, final String location, final Object data, final AssignType type, final String attr) throws SCXMLExpressionException {
			super.evalAssign(ctx, location, data, type, attr);
		}
	}
}
