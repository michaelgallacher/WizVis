package com.sonos;

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;

class TransitionCell extends ListCell<StateModel> {

	private final Label stateLabel = new Label();
	private final VBox transitionItems = new VBox();
	private final VBox root = new VBox();

	private final Controller controller;

	TransitionCell(Controller xmlModel) {
		controller = xmlModel;

		root.getChildren().add(stateLabel);
		stateLabel.getStyleClass().add("statename");
		root.getChildren().add(transitionItems);
	}

	@Override
	protected void updateItem(StateModel item, boolean empty) {
		// calling super here is very important - don't skip this!
		super.updateItem(item, empty);

		if (empty || item == null || item.getTransitions().isEmpty()) {
			setGraphic(null);
			return;
		}

		stateLabel.setText(item.getId());

		// Remove the previous entries and add the new.
		ObservableList<Node> items = transitionItems.getChildren();
		items.clear();
		item.getTransitions().forEach(tm -> items.add(new TransitionContainer(tm, this::onClick)));

		setGraphic(root);
	}

	private void onClick(MouseEvent e) {
		Button src = (Button) e.getSource();
		controller.fireEvent(src.getText());
	}

	private class TransitionContainer extends BorderPane {
		private final Button eventButton = new Button();
		private final Label targetLabel = new Label();
		private final Label conditionLabel = new Label();

		TransitionContainer(TransitionModel tm, EventHandler<MouseEvent> ev) {
			getStyleClass().add("TransitionContainer");

			setLeft(eventButton);
			setCenter(targetLabel);

			eventButton.setText(tm.getEvent());
			eventButton.setOnMouseClicked(ev);

			targetLabel.setText(tm.getTarget());
			targetLabel.minHeightProperty().bind(eventButton.heightProperty());
			targetLabel.getStyleClass().add("targetnamelabel");

			String condition = tm.getCond();
			if (condition != null) {
				boolean eval = controller.eval(condition);
				eventButton.setDisable(!eval);
				conditionLabel.setVisible(!eval);
				conditionLabel.setText(condition);
				if (!eval) {
					setBottom(conditionLabel);
				}
			} else {
				eventButton.setDisable(false);
			}
		}
	}
}