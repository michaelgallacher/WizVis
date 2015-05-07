package com.sonos;

import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.CacheHint;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.*;
import org.apache.commons.scxml2.io.SCXMLReader;
import org.apache.commons.scxml2.model.*;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.dialog.ExceptionDialog;
import org.json.simple.parser.ParseException;

import javax.script.ScriptException;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class to handle mapping the state machine model to the UI.
 */
public final class Controller {
	private static final int MAX_MRU_SIZE = 10;
	private final StateMachine stateMachine = new StateMachine();
	private final ObservableList<PropertySheet.Item> dataModelItems = FXCollections.observableArrayList();
	private final Properties applicationProps = new Properties();
	private Path rootDirectory;

	private final String CONFIG_FILE_NAME = "appConfig.properties";
	private final String CONFIG_APP_FRAME = "appFrame";
	private final String CONFIG_STATE_TRANSITION_DIVIDER = "stateTransitionDivider";

	// Shame we can't bind this to the SplitButton items.
	private final ObservableList<String> mruList = FXCollections.observableArrayList();

	@FXML
	private TextArea logWindow;
	@FXML
	private TextArea xmlViewer;
	@FXML
	private TreeView<String> statesTreeView;
	@FXML
	private ListView<TransitionsModel> transitionsListView;
	@FXML
	private Label currentStateLabel;
	@FXML
	private SplitMenuButton splitOpenButton;
	@FXML
	private TitledPane dataModelPropertiesPane;
	@FXML
	private ImageView imageView;
	@FXML
	private AnchorPane imageViewRoot;
	@FXML
	private SplitPane stateTransitionSplitPane;
	@FXML
	private Accordion statesPropertiesView;

	private Window mainWindow;

	/**
	 * Passthrough to the state machine call.
	 */
	public boolean isExpressionTrue(String expr) {
		try {
			if (expr == null) {
				expr = "<null>";
				throw new NullPointerException();
			}
			return stateMachine.isExpressionTrue(expr);
		} catch (Exception e) {
			showException(e, "Error checking expression: " + expr);
		}
		return false;
	}

	/**
	 * Passthrough to the state machine call.
	 */
	public void fireEvent(String event) {
		try {
			stateMachine.fireEvent(event);
		} catch (Exception e) {
			showException(e, "Error firing update event.");
		}
	}

	/**
	 * Called by the FXMLLoader.
	 */
	public void initialize() {

		populateTree();

		imageView.setPreserveRatio(true);
		imageView.fitHeightProperty().bind(imageViewRoot.heightProperty());
		imageView.fitWidthProperty().bind(imageViewRoot.widthProperty());
		imageView.setSmooth(true);
		imageView.setCache(true);
		imageView.setCacheHint(CacheHint.QUALITY);

		transitionsListView.setItems(stateMachine.activeStatesProperty);
		transitionsListView.setCellFactory(list -> new TransitionCell(this));
		transitionsListView.setFocusTraversable(false);

		// Can't be done in fxml because binding to dataModelItems must be done at construction.
		PropertySheet dataModelPropertiesView = new PropertySheet(dataModelItems);
		dataModelPropertiesPane.setContent(dataModelPropertiesView);
		dataModelPropertiesView.getStyleClass().add("PropertySheet");
		statesPropertiesView.setExpandedPane(dataModelPropertiesPane);

		splitOpenButton.setOnMouseClicked(this::onOpenClick);

		stateMachine.activeStatesProperty.addListener(this::onStateModelsChanged);
	}

	/**
	 * Refresh the UI when the active states change in some way.
	 *
	 * @param change Describes the change to the list.
	 */
	private void onStateModelsChanged(ListChangeListener.Change<? extends TransitionsModel> change) {
		try {
			ObservableList<? extends TransitionsModel> list = change.getList();
			if (!list.isEmpty()) {
				boolean atLeastOne = false;
				for (TransitionsModel state : list) {
					String stateName = state.getId();
					currentStateLabel.setText(stateName);
					Path imagePath = rootDirectory.resolve(stateName + ".png");
					File imageFile = imagePath.toFile();
					if (imageFile.exists()) {
						Image image = new Image("file:" + imagePath.toString());
						imageView.setImage(image);
						atLeastOne = true;
						break;
					}
				}
				if (!atLeastOne) {
					imageView.setImage(null);
				}
			} else {
				imageView.setImage(null);
				currentStateLabel.setText("<no states>");
			}
		} catch (Exception e1) {
			showException(e1, "error displaying image.");
		}
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
		if (!(new File(CONFIG_FILE_NAME).exists())) {
			return;
		}

		try (FileInputStream in = new FileInputStream(CONFIG_FILE_NAME)) {
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

			// Set splitters
			String stateTransitionDivider = applicationProps.getProperty(CONFIG_STATE_TRANSITION_DIVIDER);
			stateTransitionSplitPane.setDividerPosition(0, Double.parseDouble(stateTransitionDivider));

			// update the app location (x,y,w,h)
			String rect = applicationProps.getProperty(CONFIG_APP_FRAME);
			String[] values = rect.split(",");
			mainWindow.setX(Double.parseDouble(values[0]));
			mainWindow.setY(Double.parseDouble(values[1]));
			mainWindow.setWidth(Double.parseDouble(values[2]));
			mainWindow.setHeight(Double.parseDouble(values[3]));

		} catch (Exception e) {
			// Finished reading properties.
			showException(e, "Error reading user configuration.");
		}
	}

	/**
	 * Save the user's configuration.
	 *
	 * @throws Exception
	 */
	void saveAppConfig() throws Exception {
		System.out.println("saving appConfig");

		// Main window location
		String rect = String.format("%f,%f,%f,%f", mainWindow.getX(), mainWindow.getY(), mainWindow.getWidth(), mainWindow.getHeight());
		applicationProps.setProperty(CONFIG_APP_FRAME, rect);

		// Splitters
		if (stateTransitionSplitPane.getDividerPositions().length > 0) {
			Double divider = stateTransitionSplitPane.getDividerPositions()[0];
			applicationProps.setProperty(CONFIG_STATE_TRANSITION_DIVIDER, divider.toString());
		}

		// MRU
		if (!mruList.isEmpty()) {
			try (FileOutputStream outStream = new FileOutputStream(CONFIG_FILE_NAME)) {
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

	private void initialize(String path) throws IOException, ModelException, XMLStreamException, ParseException {

		File file = new File(path);
		rootDirectory = file.toPath().getParent();
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
				Path tmpPath = rootDirectory.resolve(jsonPath);
				jsonPath = tmpPath.toString();
			}
		}

		stateMachine.initialize(scxml, jsonPath);

		dataModelItems.clear();
		if (jsonPath != null) {
			Set<Map.Entry> entries = stateMachine.getDatamodelJSON().entrySet();
			for (Map.Entry entry : entries) {
				PropertySheet.Item item = new DataModelProperty(stateMachine, entry.getKey().toString(), entry.getValue().toString(), String.class);
				dataModelItems.add(item);
			}
			dataModelItems.sort((d1, d2) -> d1.getName().compareTo(d2.getName()));
		}
	}

	private void showException(Exception e, String title) {
		ExceptionDialog dialog = new ExceptionDialog(e);
		dialog.setTitle("Error");
		dialog.setHeaderText(title);
		dialog.showAndWait();
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
			SCXMLValidator.validate(path);

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
			showException(e1, "Error opening " + path);
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

	private final class DataModelProperty implements PropertySheet.Item {

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
		public void setValue(Object val) {
			String name = getName();
			try {
				if (name == null) {
					name = "<null>";
					throw new NullPointerException();
				}
				if (val == null) {
					val = "<null>";
					throw new NullPointerException();
				}

				stateMachine.assignDataValue(getName(), val);
			} catch (ScriptException e) {
				showException(e, "Error assigning " + val.toString() + " to " + name);
			}
		}
	}
}
