package com.sonos;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;

public class Main extends Application {

	private Controller controller;

	@Override
	public void stop() throws Exception {
		super.stop();
		controller.saveAppConfig();
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
		Parent root = loader.load();
		controller = loader.getController();

		Scene scene = new Scene(root);
		scene.getStylesheets().add("/default.css");

		primaryStage.setTitle("WizVis");
		primaryStage.setScene(scene);
		primaryStage.show();

		controller.setMainWindow(scene.getWindow());

		// must call after everything else is set up.
		controller.loadAppConfig();
	}

	public static void main(String[] args) {
		launch(args);
	}
}

