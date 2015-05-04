package com.sonos;

import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.*;
import org.apache.commons.scxml2.io.SCXMLReader;
import org.apache.commons.scxml2.model.SCXML;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.dialog.ExceptionDialog;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main extends Application {

	final int MAX_MRU_SIZE = 10;

	Controller controller = new Controller();

	Properties applicationProps = new Properties();

	Window mainWindow;

	@SceneNode
	TextArea xmlViewer;
	@SceneNode
	ListView<String> statesListView;
	@SceneNode
	ListView<String> transitionsListView;
	@SceneNode
	Label currentStateLabel;
	@SceneNode
	SplitMenuButton splitOpenButton;
	@SceneNode
	PropertySheet dataModelPropertiesView;

	@SceneNode
	TitledPane dataModelPropertiesPane;


	@Override
	public void stop() throws Exception {
		try (FileOutputStream outStream = new FileOutputStream("appConfig")) {
			ObservableList<MenuItem> items = splitOpenButton.getItems();
			int COUNT = Math.min(MAX_MRU_SIZE, items.size());
			for (int i = 0; i < COUNT; i++) {
				applicationProps.setProperty("MRU" + i, items.get(i).getId());
			}
			applicationProps.store(outStream, "MRU");
		}
	}

	@Override
	public void start(Stage primaryStage) throws Exception {

		primaryStage.setMinWidth(600);
		primaryStage.setMinHeight(400);
		primaryStage.setTitle("WizVis");

		Parent root = FXMLLoader.load(getClass().getResource("/sample.fxml"));
		Scene scene = new Scene(root);
		scene.getStylesheets().add("/default.css");
		primaryStage.setScene(scene);
		primaryStage.show();

		mainWindow = scene.getWindow();
		initSceneNodes(scene);

		try (FileInputStream in = new FileInputStream("appConfig")) {
			applicationProps.load(in);
			ObservableList<MenuItem> items = splitOpenButton.getItems();

			for (int i = 0; i < MAX_MRU_SIZE; i++) {
				String pathStr = applicationProps.getProperty("MRU" + i, null);
				Path path = new File(pathStr).toPath();
				MenuItem mi = new MenuItem(path.getFileName().toString());
				mi.setOnAction(this::onMenuItemClick);
				mi.setId(path.toString());
				items.add(mi);
			}
		} catch (Exception e) {
			// All done reading properties.
			//e.printStackTrace();
		}

		// current state
		currentStateLabel.textProperty().bind(controller.currentStateProperty);
		statesListView.setItems(controller.allStatesProperty);
		transitionsListView.setItems(controller.currentTransitionsProperty);
		transitionsListView.setCellFactory(list -> new TransitionCell(controller));
		transitionsListView.setFocusTraversable(false);

		dataModelPropertiesView = new PropertySheet(controller.dataModelItems);
		dataModelPropertiesPane.setContent(dataModelPropertiesView);

		//primaryStage.iconifiedProperty().addListener(e->System.out.println(e.toString()));

		splitOpenButton = (SplitMenuButton) scene.lookup("#splitOpenButton");
		splitOpenButton.setOnMouseClicked(this::onClick);
	}

	private void onMenuItemClick(ActionEvent e) {
		MenuItem mi = (MenuItem) e.getSource();
		openFile(new File(mi.getId()));
	}

	private void onClick(MouseEvent e) {
		FileChooser fileChooser = new FileChooser();
		File file = fileChooser.showOpenDialog(mainWindow);
		if (file != null) {
			openFile(file);
		}
	}

	private void openFile(File file) {
		Path filePath = file.toPath();
		String path = filePath.toString();
		try {
			// Load our xml and show it.
			String scxmlText = new String(Files.readAllBytes(filePath));
			xmlViewer.setText(scxmlText);

			// Take that string and parse it.
			InputStream is = new ByteArrayInputStream(scxmlText.getBytes(Charset.defaultCharset()));
			SCXML scxml = SCXMLReader.read(is);
			controller.Initialize(scxml);

			// See if this path is already in the MRU. If so, remove all instances of it
			// and let it be inserted at the top again.
			ObservableList<MenuItem> items = splitOpenButton.getItems();
			List<MenuItem> oldMenuItems = items.stream().filter(m -> m.getId().equals(path)).collect(Collectors.toList());
			oldMenuItems.stream().forEach(items::remove);

			// Insert the item at the top.
			MenuItem mi = new MenuItem(filePath.getFileName().toString());
			mi.setId(path);
			mi.setOnAction(this::onMenuItemClick);
			items.add(0, mi);
		} catch (Exception e1) {
			ExceptionDialog d = new ExceptionDialog(new IOException());
			d.setTitle("Error");
			d.setHeaderText("Error while opening: " + path);
			d.showAndWait();
		}
	}

	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface SceneNode { }

	public void initSceneNodes(Scene scene) {
		try {
			Class<?> thisClass = this.getClass();
			for (Field field : thisClass.getDeclaredFields()) {
				if (field.getAnnotation(SceneNode.class) != null) {
					field.set(this, scene.lookup("#" + field.getName()));
				}
			}
		} catch (IllegalAccessException x) {
			x.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}

