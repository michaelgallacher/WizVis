package com.sonos;

import javafx.beans.property.SimpleListProperty;
import javafx.collections.*;
import org.apache.commons.scxml2.*;
import org.apache.commons.scxml2.env.javascript.*;
import org.apache.commons.scxml2.model.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import javax.script.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.security.InvalidParameterException;
import java.util.*;

public class StateMachine {
	private SCXMLExecutor exec;
	private ScriptEngine engine;
	private String dataId;

	public class StateTreeModel {
		String id;
		List<StateTreeModel> children = new ArrayList<>();

		public StateTreeModel(String stateId) {
			id = stateId;
		}

		public void add(StateTreeModel... stateIds) {
			Collections.addAll(children, stateIds);
		}
	}

	public ObservableList<StateTreeModel> stateTreeModelProperty = FXCollections.observableArrayList();
	public ObservableList<StateModel> activeStatesProperty = new SimpleListProperty<>(FXCollections.observableArrayList());

	public boolean Eval(String expr) {
		try {
			Object result = engine.eval(expr);
			if (result instanceof Boolean) {
				return (Boolean) result;
			}
		} catch (ScriptException e) {
			//
		}
		return false;
	}

	public void FireEvent(String event) {
		try {
			TriggerEvent te = new TriggerEvent(event, TriggerEvent.SIGNAL_EVENT);
			exec.triggerEvent(te);

			onActiveStatesChanged(getActiveStates());
		} catch (ModelException e) {
			e.printStackTrace();
		}
	}

	public void Initialize(SCXML scxml, String jsonPath) throws IOException, ParseException, ModelException {

		dataId = null;
		Datamodel dm = scxml.getDatamodel();
		if (dm != null) {
			Data data = dm.getData().get(0);
			dataId = data.getId();
		}

		engine = null;
		// Read the data model json.  This will serve as the default values in the iteration below.
		if (jsonPath != null) {
			byte[] encodedBaseline = Files.readAllBytes(Paths.get(jsonPath));
			String jsonDataModelBaseline = new String(encodedBaseline, Charset.defaultCharset());
			JSONObject jsonDataBaseline = (JSONObject) new JSONParser().parse(jsonDataModelBaseline);

			engine = new ScriptEngineManager().getEngineByName("JavaScript");
			engine.put(dataId, jsonDataBaseline);
		}

		exec = new SCXMLExecutor(new MyJSEvaluator(), null, null);
		exec.setStateMachine(scxml);
		exec.go();

		Map<String, EnterableState> idToStateMap = new HashMap<>();

		// Find all the reachable states in the graph.
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
		List<StateTreeModel> tree = populate(scxml.getChildren());
		stateTreeModelProperty = FXCollections.observableArrayList(tree);

		// notify listeners there is a new state.
		onActiveStatesChanged(idToStateMap.get(exec.getStateMachine().getInitial()));
	}

	private List<StateTreeModel> populate(List<EnterableState> states) {
		List<StateTreeModel> parents = new ArrayList<>();
		for (EnterableState state : states) {
			StateTreeModel parent = new StateTreeModel(state.getId());
			parents.add(parent);
			if (state instanceof TransitionalState) {
				TransitionalState ts = (TransitionalState) state;
				parent.add(populate(ts.getChildren()).toArray(new StateTreeModel[]{}));
			}
		}
		return parents;
	}

	public EnterableState[] getActiveStates() {
		Set<EnterableState> activeStates = exec.getStatus().getActiveStates();
		return activeStates.toArray(new EnterableState[activeStates.size()]);
	}

	public JSONObject getDatamodelJSON() {
		if (dataId == null || dataId.isEmpty()) {
			return new JSONObject();
		}
		return (JSONObject) engine.get(dataId);
	}

	private void onActiveStatesChanged(EnterableState newCurrentState) {
		onActiveStatesChanged(new EnterableState[]{newCurrentState});
	}

	private void onActiveStatesChanged(EnterableState[] newCurrentStates) {
		activeStatesProperty.clear();
		for (EnterableState newState : newCurrentStates) {
			if (!(newState instanceof TransitionalState)) {
				throw new InvalidParameterException();
			}
			activeStatesProperty.add(new StateModel(newState));
		}

		activeStatesProperty.sort((o1, o2) -> {
			return (o1.getId().length() > o2.getId().length()) ? -1 : (o1.getId().length() < o2.getId().length() ? 1 : 0);
		});
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
