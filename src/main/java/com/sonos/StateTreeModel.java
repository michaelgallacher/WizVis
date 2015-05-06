package com.sonos;

import java.util.*;

class StateTreeModel {
	private final String id;
	private final List<StateTreeModel> children = new ArrayList<>();

	StateTreeModel(String stateId) {
		id = stateId;
	}

	String getId() { return id; }

	List<StateTreeModel> getChildren() { return children; }

	void addChildren(List<StateTreeModel> stateIds) {
		children.addAll(stateIds);
	}
}
