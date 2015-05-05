package com.sonos;

import javafx.application.Application;
import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.*;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.dialog.ExceptionDialog;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main extends Application {

	final int MAX_MRU_SIZE = 10;

	private Controller controller = new Controller();

	private Properties applicationProps = new Properties();

	private Window mainWindow;

	@SceneNode
	TextArea xmlViewer;
	@SceneNode
	ListView<String> statesListView;
	@SceneNode
	ListView<StateModel> transitionsListView;
	@SceneNode
	Label currentStateLabel;
	@SceneNode
	SplitMenuButton splitOpenButton;
	@SceneNode
	PropertySheet dataModelPropertiesView;
	@SceneNode
	TitledPane dataModelPropertiesPane;
	@SceneNode
	ImageView imageView;

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

		Parent root = FXMLLoader.load(getClass().getResource("/main.fxml"));
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

		statesListView.setItems(controller.allStatesProperty);

		transitionsListView.setItems(controller.currentStatesProperty);
		transitionsListView.setCellFactory(list -> new TransitionCell(controller));
		transitionsListView.setFocusTraversable(false);

		dataModelPropertiesView = new PropertySheet(controller.dataModelItems);
		dataModelPropertiesPane.setContent(dataModelPropertiesView);

		//primaryStage.iconifiedProperty().addListener(e->System.out.println(e.toString()));

		splitOpenButton = (SplitMenuButton) scene.lookup("#splitOpenButton");
		splitOpenButton.setOnMouseClicked(this::onClick);

		controller.currentStatesProperty.addListener((ListChangeListener<StateModel>) c -> {
			if(c.getList().size() > 0) {
				currentStateLabel.setText(c.getList().get(0).getName());
			}
			else {
				currentStateLabel.setText("");
			}
		});
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
			controller.Initialize(path);

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
			ExceptionDialog d = new ExceptionDialog(e1);
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

