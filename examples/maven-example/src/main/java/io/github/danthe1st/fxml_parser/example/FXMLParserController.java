package io.github.danthe1st.fxml_parser.example;

import java.net.URL;
import java.util.ResourceBundle;

import io.github.danthe1st.fxml_parser.api.ParseFXML;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.AnchorPane;

@ParseFXML("fxml/test.fxml")
public class FXMLParserController implements Initializable {
	@FXML
	private AnchorPane rootPane;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		System.out.println(location + ", " + resources);
	}
	
	public void initialize() {
		System.out.println(rootPane);
	}
}
