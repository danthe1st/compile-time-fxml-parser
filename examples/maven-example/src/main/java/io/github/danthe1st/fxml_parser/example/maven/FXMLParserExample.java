package io.github.danthe1st.fxml_parser.example.maven;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class FXMLParserExample extends Application {
	public static void main(String[] args) {
		Application.launch(args);
	}
	
	@Override
	public void start(Stage primaryStage) throws Exception {
		primaryStage.setTitle("compile-time-fxml-parser Maven Example");
		AnchorPane root = FXMLParserControllerFXMLRepresentation.createNode();
		
//		//alternative:
//		FXMLParserControllerFXMLRepresentation loader = new FXMLParserControllerFXMLRepresentation();
//		loader.setResourceBundle(ResourceBundle.getBundle("localization"));
//		Text rootText = new Text("the root can also be set manually if an fx:root construct is used");
//		AnchorPane root = new AnchorPane(rootText);
//		root.setBackground(new Background(new BackgroundFill(Color.LIGHTBLUE, CornerRadii.EMPTY, Insets.EMPTY)));
//		AnchorPane.setLeftAnchor(rootText, 50.0);
//		AnchorPane.setTopAnchor(rootText, 20.0);
//		loader.setRoot(root);
//		loader.buildNode();
		
		primaryStage.setScene(new Scene(root));
		primaryStage.show();
	}
}
