package com.sonos;

import javafx.beans.property.*;
import javafx.collections.*;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Jdk14Logger;
import org.apache.commons.scxml2.*;
import org.apache.commons.scxml2.env.LogUtils;
import org.apache.commons.scxml2.env.javascript.*;
import org.apache.commons.scxml2.model.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import javax.script.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class StateMachine {
	private SCXMLExecutor exec;
	private ScriptEngine engine;
	private String dataId;

	private Map<String, EnterableState> idToStateMap = new HashMap<>();

	public ObservableList<String> allStatesProperty = FXCollections.observableArrayList();
	public ObservableList<StateModel> currentStatesProperty = new SimpleListProperty<>(FXCollections.observableArrayList());

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

			onCurrentStateChanged(getCurrentState());
		} catch (ModelException e) {
			e.printStackTrace();
		}
	}

	public void Initialize(SCXML scxml, String jsonPath) throws IOException, ParseException, ModelException {

		Data listViewCellTemplate = scxml.getDatamodel().getData().get(0);
		dataId = listViewCellTemplate.getId();

		// Read the data model json.  This will serve as the default values in the iteration below.
		if (jsonPath != null) {
			byte[] encodedBaseline = Files.readAllBytes(Paths.get(jsonPath));
			String jsonDataModelBaseline = new String(encodedBaseline, Charset.defaultCharset());
			JSONObject jsonDataBaseline = (JSONObject) new JSONParser().parse(jsonDataModelBaseline);

			engine = new ScriptEngineManager().getEngineByName("JavaScript");
			engine.put(dataId, jsonDataBaseline);
		}

		MyListener listener = new MyListener();

		exec = new SCXMLExecutor(new MyJSEvaluator(), null, null);
		exec.setStateMachine(scxml);
		exec.addListener(scxml, listener);
		exec.go();

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

		allStatesProperty.clear();
		allStatesProperty.addAll(idToStateMap.keySet().stream().sorted().collect(Collectors.toList()));

		onCurrentStateChanged(idToStateMap.get(exec.getStateMachine().getInitial()));
	}

	public EnterableState[] getCurrentState() {
		return exec.getStatus().getActiveStates().toArray(new EnterableState[]{});
	}

	public JSONObject getDatamodelJSON() {
		return (JSONObject) engine.get(dataId);
	}

	private void onCurrentStateChanged(EnterableState newCurrentState) {
		onCurrentStateChanged(new EnterableState[]{newCurrentState});
	}

	private void onCurrentStateChanged(EnterableState[] newCurrentStates) {

		// Update the property.
		currentStatesProperty.clear();
		for (EnterableState newState : newCurrentStates) {

			if (!(newState instanceof TransitionalState)) {
				continue;
			}

			currentStatesProperty.add(new StateModel(newState));
		}
	}

	private final class MyListener implements SCXMLListener {
		private Jdk14Logger log = (Jdk14Logger) LogFactory.getLog(getClass());
		private List<List<Integer>> successfulWalks = new ArrayList<>();
		private LinkedList<ArrayList<Integer>> transitionsInCurrentWalk = new LinkedList<>();
		private boolean invalidTransition = false;

		MyListener() {
			log.getLogger().setLevel(Level.OFF);
			transitionsInCurrentWalk.push(new ArrayList<>());
		}

		@Override
		public void onEntry(EnterableState state) {
		}

		@Override
		public void onExit(EnterableState state) {
		}

		@Override
		public void onTransition(TransitionTarget from, TransitionTarget to, Transition transition, String event) {
			ArrayList<Integer> walked = transitionsInCurrentWalk.peek();
			// Here's where things get interesting.
			// If we have seen this transition and it's not the only one, then skip it.
			if (walked.contains(transition.getObservableId())) {
				EnterableState enterable = idToStateMap.get(from.getId());
				if (enterable instanceof TransitionalState) {
					TransitionalState fromState = (TransitionalState) enterable;
					List<Transition> transitions = fromState.getTransitionsList();
					invalidTransition = transitions.size() != 1;
				}
			}

			if (!invalidTransition) {
				walked.add(transition.getObservableId());

				if (log.isInfoEnabled()) {
					log.info("transition #" + transition.getObservableId() + ": " + LogUtils.transToString(from, to, transition, event));
				}
			}
		}

		List<Integer> CurrentWalk() {
			return Collections.unmodifiableList(transitionsInCurrentWalk.peek());
		}

		List<List<Integer>> SuccessfulWalks() {
			return Collections.unmodifiableList(successfulWalks);
		}
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
		public void evalAssign(final Context ctx, final String location, final Object data, final AssignType type,
							   final String attr) throws SCXMLExpressionException {
			super.evalAssign(ctx, location, data, type, attr);
		}
	}
}
