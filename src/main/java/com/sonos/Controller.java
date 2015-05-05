package com.sonos;

import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.*;
import org.apache.commons.scxml2.io.SCXMLReader;
import org.apache.commons.scxml2.model.*;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.dialog.ExceptionDialog;
import org.json.simple.parser.ParseException;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Controller {
	final int MAX_MRU_SIZE = 10;
	public StateMachine stateMachine = new StateMachine();
	public ObservableList<PropertySheet.Item> dataModelItems = FXCollections.observableArrayList();

	@FXML
	TextArea xmlViewer;
	@FXML
	TreeView<String> statesTreeView;
	@FXML
	ListView<StateModel> transitionsListView;
	@FXML
	Label currentStateLabel;
	@FXML
	SplitMenuButton splitOpenButton;
	@FXML
	PropertySheet dataModelPropertiesView;
	@FXML
	TitledPane dataModelPropertiesPane;
	@FXML
	ImageView imageView;

	private Properties applicationProps = new Properties();
	private Window mainWindow;
	// Shame we can't bind this to the SplitButton items.
	private ObservableList<String> mruList = FXCollections.observableArrayList();

	public boolean Eval(String expr) {
		return stateMachine.Eval(expr);
	}

	public void FireEvent(String event) {
		stateMachine.FireEvent(event);
	}

	@SuppressWarnings("deprecation,unchecked")
	public void Initialize(String path) throws IOException, ModelException, XMLStreamException, ParseException {

		File file = new File(path);
		SCXML scxml = SCXMLReader.read(file.toURL());
		Datamodel dm = scxml.getDatamodel();
		String jsonPath = null;
		if (dm != null) {
			Data d = dm.getData().get(0);
			jsonPath = d.getSrc();
			if (jsonPath != null) {
				Path tmpPath = file.toPath().getParent().resolve(jsonPath);
				jsonPath = tmpPath.toString();
			}
		}

		stateMachine.Initialize(scxml, jsonPath);

		dataModelItems.clear();
		Set<Map.Entry> entries = stateMachine.getDatamodelJSON().entrySet();
		for (Map.Entry entry : entries) {
			PropertySheet.Item item = new DataModelProperty(entry.getKey().toString(), entry.getValue().toString(), String.class);
			dataModelItems.add(item);
		}
	}

	// Called by the FXMLLoader
	public void initialize() {

		populateTree();

		transitionsListView.setItems(stateMachine.activeStatesProperty);
		transitionsListView.setCellFactory(list -> new TransitionCell(this));
		transitionsListView.setFocusTraversable(false);

		// Can't be done in fxml because binding must be done at construction.
		dataModelPropertiesView = new PropertySheet(dataModelItems);
		dataModelPropertiesPane.setContent(dataModelPropertiesView);
		dataModelPropertiesView.getStyleClass().add("PropertySheet");

		//splitOpenButton = (SplitMenuButton) scene.lookup("#splitOpenButton");
		splitOpenButton.setOnMouseClicked(this::onClick);

		stateMachine.activeStatesProperty.addListener((ListChangeListener<StateModel>) change -> {
			ObservableList<? extends StateModel> list = change.getList();
			if (list.size() > 0) {
				currentStateLabel.setText(list.get(0).getId());
			} else {
				currentStateLabel.setText("<no states>");
			}
		});
	}

	/**
	 * This is used for parenting modal dialogs.
	 *
	 * @param window the main window
	 */
	public void setMainWindow(Window window) {
		mainWindow = window;
	}

	/**
	 * Read the user's configuration
	 */
	void loadAppConfig() {
		try (FileInputStream in = new FileInputStream("appConfig")) {
			applicationProps.load(in);

			// load our mru list
			for (int i = 0; i < MAX_MRU_SIZE; i++) {
				String pathStr = applicationProps.getProperty("MRU" + i, null);
				if (pathStr == null) {
					break;
				}
				mruList.add(pathStr);
			}

			populateMRU(mruList);

		} catch (Exception e) {
			// All done reading properties.
			//e.printStackTrace();
		}
	}

	/**
	 * Save the user's configuration.
	 *
	 * @throws Exception
	 */
	void saveAppConfig() throws Exception {
		System.out.println("saving appConfig");
		if (mruList.size() > 0) {
			try (FileOutputStream outStream = new FileOutputStream("appConfig")) {
				int COUNT = Math.min(MAX_MRU_SIZE, mruList.size());
				for (int i = 0; i < COUNT; i++) {
					applicationProps.setProperty("MRU" + i, mruList.get(i));
				}
				applicationProps.store(outStream, "MRU");
			}
		}
	}

	private void onClick(MouseEvent e) {
		FileChooser fileChooser = new FileChooser();
		File file = fileChooser.showOpenDialog(mainWindow);
		if (file != null) {
			openFile(file);
		}
	}

	private void onMenuItemClick(ActionEvent e) {
		MenuItem mi = (MenuItem) e.getSource();
		openFile(new File(mi.getId()));
	}

	private void openFile(File file) {
		Path filePath = file.toPath();
		String path = filePath.toString();
		try {
			// Load our xml and show it.
			String scxmlText = new String(Files.readAllBytes(filePath));
			xmlViewer.setText(scxmlText);

			// Take that string and parse it.
			this.Initialize(path);

			// See if this path is already in the MRU. If so, remove all instances of it
			// and let it be inserted at the top again.

			// Absolutely awful we can't bind the mru list to the menu item.
			List<String> oldMenuItems = mruList.stream().filter(m -> m.equals(path)).collect(Collectors.toList());
			oldMenuItems.stream().forEach(mruList::remove);
			mruList.add(0, path);

			populateMRU(mruList);
			populateTree();

		} catch (Exception e1) {
			ExceptionDialog d = new ExceptionDialog(e1);
			d.setTitle("Error");
			d.setHeaderText("Error while opening: " + path);
			d.showAndWait();
		}
	}

	private void populateMRU(List<String> source) {
		ObservableList<MenuItem> items = splitOpenButton.getItems();
		items.clear();
		for (String mruPath : source) {
			Path path = new File(mruPath).toPath();
			MenuItem mi = new MenuItem(path.getFileName().toString());
			mi.setOnAction(this::onMenuItemClick);
			mi.setId(mruPath);
			items.add(mi);
		}
	}

	private void addChildren(TreeItem<String> parent, StateMachine.StateTreeModel child) {
		TreeItem<String> childItem = new TreeItem<>(child.id);
		parent.getChildren().add(childItem);
		child.children.forEach(grandchidl -> addChildren(childItem, grandchidl));
		parent.setExpanded(true);
	}

	private void populateTree() {
		TreeItem<String> root = new TreeItem<>("root");
		List<StateMachine.StateTreeModel> tree = stateMachine.stateTreeModelProperty;
		for (StateMachine.StateTreeModel child : tree) {
			addChildren(root, child);
		}
		statesTreeView.setRoot(root);
	}

	public class DataModelProperty implements PropertySheet.Item {

		Datamodel dataModel;
		Class<?> dataType;
		String dataId;
		String dataValue;

		public DataModelProperty(String id, String value, Class<?> type) {//}, Datamodel model) {
			//	dataModel = model;
			dataType = type;
			dataId = id;
			dataValue = value;
		}

		@Override
		public String getCategory() {
			return "Category";
		}

		@Override
		public String getDescription() {
			return dataId;
		}

		@Override
		public String getName() {
			return dataId;
		}

		@Override
		public Class<?> getType() {
			return dataType;
		}

		@Override
		public Object getValue() {
			return dataValue;
		}

		@Override
		public void setValue(Object o) {

		}
	}
}
