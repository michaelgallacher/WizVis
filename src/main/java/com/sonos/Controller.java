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

public final class Controller {
	private static final int MAX_MRU_SIZE = 10;
	private final StateMachine stateMachine = new StateMachine();
	private final ObservableList<PropertySheet.Item> dataModelItems = FXCollections.observableArrayList();
	private final Properties applicationProps = new Properties();

	// Shame we can't bind this to the SplitButton items.
	private final ObservableList<String> mruList = FXCollections.observableArrayList();
	@FXML
	private TextArea logWindow;
	@FXML
	private TextArea xmlViewer;
	@FXML
	private TreeView<String> statesTreeView;
	@FXML
	private ListView<StateModel> transitionsListView;
	@FXML
	private Label currentStateLabel;
	@FXML
	private SplitMenuButton splitOpenButton;
	@FXML
	private TitledPane dataModelPropertiesPane;
	@FXML
	private ImageView imageView;
	private Window mainWindow;

	public boolean eval(String expr) {
		return stateMachine.eval(expr);
	}

	public void fireEvent(String event) {
		stateMachine.fireEvent(event);
	}

	// Called by the FXMLLoader
	public void initialize() {

		populateTree();

		transitionsListView.setItems(stateMachine.activeStatesProperty);
		transitionsListView.setCellFactory(list -> new TransitionCell(this));
		transitionsListView.setFocusTraversable(false);

		// Can't be done in fxml because binding must be done at construction.
		PropertySheet dataModelPropertiesView = new PropertySheet(dataModelItems);
		dataModelPropertiesPane.setContent(dataModelPropertiesView);
		dataModelPropertiesView.getStyleClass().add("PropertySheet");

		//splitOpenButton = (SplitMenuButton) scene.lookup("#splitOpenButton");
		splitOpenButton.setOnMouseClicked(this::onOpenClick);

		stateMachine.activeStatesProperty.addListener((ListChangeListener<StateModel>) change -> {
			ObservableList<? extends StateModel> list = change.getList();
			if (!list.isEmpty()) {
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
			// Finished reading properties.
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
		if (!mruList.isEmpty()) {
			try (FileOutputStream outStream = new FileOutputStream("appConfig")) {
				int COUNT = Math.min(MAX_MRU_SIZE, mruList.size());
				for (int i = 0; i < COUNT; i++) {
					applicationProps.setProperty("MRU" + i, mruList.get(i));
				}
				applicationProps.store(outStream, "MRU");
			}
		}
	}

	private void addChildren(TreeItem<String> parent, StateTreeModel child) {
		TreeItem<String> childItem = new TreeItem<>(child.getId());
		parent.getChildren().add(childItem);
		child.getChildren().forEach(grandchild -> addChildren(childItem, grandchild));
		parent.setExpanded(true);
	}

	@SuppressWarnings("unchecked")
	private void initialize(String path) throws IOException, ModelException, XMLStreamException, ParseException {

		File file = new File(path);
		SCXML scxml = SCXMLReader.read(file.toURI().toURL());
		Datamodel dm = scxml.getDatamodel();
		String jsonPath = null;
		if (dm != null) {
			Data data = dm.getData().get(0);
			jsonPath = data.getSrc();
			if (jsonPath == null) {
				jsonPath = data.getExpr();
			}
			if (jsonPath != null) {
				Path tmpPath = file.toPath().getParent().resolve(jsonPath);
				jsonPath = tmpPath.toString();
			}
		}

		stateMachine.initialize(scxml, jsonPath);

		dataModelItems.clear();
		Set<Map.Entry> entries = stateMachine.getDatamodelJSON().entrySet();
		for (Map.Entry entry : entries) {
			PropertySheet.Item item = new DataModelProperty(stateMachine, entry.getKey().toString(), entry.getValue().toString(), String.class);
			dataModelItems.add(item);
		}
	}

	private void onMenuItemClick(ActionEvent e) {
		MenuItem mi = (MenuItem) e.getSource();
		openFile(new File(mi.getId()));
	}

	private void onOpenClick(MouseEvent e) {
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
			this.initialize(path);

			// See if this path is already in the MRU. If so, remove all instances of it
			// and let it be inserted at the top again.

			// Absolutely awful we can't bind the mru list to the menu item.
			List<String> oldMenuItems = mruList.stream().filter(mi -> mi.equals(path)).collect(Collectors.toList());
			oldMenuItems.stream().forEach(mruList::remove);
			mruList.add(0, path);

			populateMRU(mruList);
			populateTree();

		} catch (Exception e1) {
			ExceptionDialog dialog = new ExceptionDialog(e1);
			dialog.setTitle("Error");
			dialog.setHeaderText("Error while opening: " + path);
			dialog.showAndWait();
		}
	}

	private void populateMRU(Iterable<String> source) {
		ObservableList<MenuItem> items = splitOpenButton.getItems();
		items.clear();
		for (String mruPath : source) {
			Path path = new File(mruPath).toPath();
			MenuItem mi = new MenuItem(path.getFileName().toString());
			mi.setMnemonicParsing(false);
			mi.setOnAction(this::onMenuItemClick);
			mi.setId(mruPath);
			items.add(mi);
		}
	}

	private void populateTree() {
		TreeItem<String> root = new TreeItem<>("root");
		List<StateTreeModel> tree = stateMachine.stateTreeModelProperty;
		for (StateTreeModel child : tree) {
			addChildren(root, child);
		}
		statesTreeView.setRoot(root);
	}

	private static final class DataModelProperty implements PropertySheet.Item {

		private final Class<?> dataType;
		private final String dataId;
		private final String dataValue;
		private final StateMachine stateMachine;

		DataModelProperty(StateMachine sm, String id, String value, Class<?> type) {
			dataType = type;
			dataId = id;
			dataValue = value;
			stateMachine = sm;
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
			if (!dataValue.equals(o)) {
				stateMachine.getEngine().put(getName(), o);
				stateMachine.refresh();
			}
		}
	}
}
