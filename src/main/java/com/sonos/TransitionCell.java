package com.sonos;


import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

public class TransitionCell extends ListCell<String> {

	Button button = new Button();
	Controller model;

	public TransitionCell(Controller xmlModel) {
		model = xmlModel;
		button.setFocusTraversable(false);
	}

	public void updateSelected(boolean update) {
	}

	@Override
	protected void updateItem(String item, boolean empty) {
		// calling super here is very important - don't skip this!
		super.updateItem(item, empty);

		if (empty) {
			setGraphic(null);
		} else {
			button.setText(item == null ? "" : item);
			button.setOnMouseClicked(this::onClick);
			setGraphic(button);
		}
	}

	private void onClick(MouseEvent e) {
		model.FireEvent(button.getText());
	}
}