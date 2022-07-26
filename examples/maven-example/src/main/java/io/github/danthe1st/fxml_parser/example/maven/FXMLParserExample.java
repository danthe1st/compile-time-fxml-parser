package io.github.danthe1st.fxml_parser.example.maven;

import java.util.ResourceBundle;

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
		// AnchorPane root = FXMLParserControllerFXMLRepresentation.createNode();
		
//		//alternative:
		FXMLParserControllerFXMLRepresentation loader = new FXMLParserControllerFXMLRepresentation();
		loader.setResourceBundle(ResourceBundle.getBundle("localization"));
		loader.buildNode();
		AnchorPane root = loader.getRoot();
		
		primaryStage.setScene(new Scene(root));
		primaryStage.show();
	}
}
