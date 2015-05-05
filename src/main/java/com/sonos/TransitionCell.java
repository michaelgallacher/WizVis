package com.sonos;

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;

public class TransitionCell extends ListCell<StateModel> {

	private Label stateLabel = new Label();
	private VBox transitionItems = new VBox();
	private VBox root = new VBox();

	private Controller controller;

	public TransitionCell(Controller xmlModel) {
		controller = xmlModel;

		root.getChildren().add(stateLabel);
		stateLabel.getStyleClass().add("statenamebutton");
		root.getChildren().add(transitionItems);
	}

	@Override
	protected void updateItem(StateModel item, boolean empty) {
		// calling super here is very important - don't skip this!
		super.updateItem(item, empty);

		if (empty || item == null || item.getTransitions().size() == 0) {
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
		controller.FireEvent(src.getText());
	}

	private class TransitionContainer extends HBox {
		private Button eventButton = new Button();
		private Label targetLabel = new Label();
		private Label conditionLabel = new Label();

		public TransitionContainer(TransitionModel tm, EventHandler<MouseEvent> ev) {
			getStyleClass().add("TransitionContainer");

			getChildren().addAll(eventButton, targetLabel);

			eventButton.setText(tm.getEvent());
			eventButton.setOnMouseClicked(ev);

			targetLabel.setText(tm.getTarget());
			targetLabel.minHeightProperty().bind(eventButton.heightProperty());
			targetLabel.getStyleClass().add("targetnamelabel");

			String condition = tm.getCond();
			if (condition != null) {
				boolean eval = controller.Eval(condition);
				eventButton.setDisable(!eval);
			}

			conditionLabel.setText(condition);
			//	conditionLabel.setPadding(new Insets(4));
		}
	}
}