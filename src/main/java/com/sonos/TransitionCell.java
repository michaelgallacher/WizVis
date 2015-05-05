package com.sonos;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.apache.commons.scxml2.model.Transition;

public class TransitionCell extends ListCell<StateModel> {

	private Button eventButton = new Button();
	private Label conditionLabel = new Label();
	private Controller controller;

	public TransitionCell(Controller xmlModel) {
		controller = xmlModel;
		eventButton.setFocusTraversable(false);
	}

	public void updateSelected(boolean update) {
	}

	@Override
	protected void updateItem(StateModel item, boolean empty) {
		// calling super here is very important - don't skip this!
		super.updateItem(item, empty);

		if (empty) {
			setGraphic(null);
			return;
		}

		if (item == null) {
			eventButton.setText("");
			return;
		}

		HBox hbox = new HBox();
		String condition = item.getTransitions().get(0).getCond();
		if(condition != null) {
			boolean eval = controller.Eval(condition);
			eventButton.setDisable(!eval);
		}
		eventButton.setText(item.getTransitions().get(0).getEvent());
		eventButton.setOnMouseClicked(this::onClick);
		eventButton.setPadding(new Insets(4));

		hbox.getChildren().add(eventButton);

		conditionLabel.setText(condition);
		conditionLabel.setWrapText(false);
		conditionLabel.prefHeightProperty().bind(eventButton.heightProperty());
		conditionLabel.setPadding(new Insets(4));

		hbox.getChildren().add(conditionLabel);
		setGraphic(hbox);
	}

	private void onClick(MouseEvent e) {
		controller.FireEvent(eventButton.getText());
	}
}