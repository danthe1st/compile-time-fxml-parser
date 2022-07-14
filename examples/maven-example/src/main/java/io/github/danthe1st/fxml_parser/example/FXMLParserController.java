package io.github.danthe1st.fxml_parser.example;

import io.github.danthe1st.fxml_parser.api.ParseFXML;
import javafx.fxml.FXML;
import javafx.scene.layout.AnchorPane;

@ParseFXML("test.fxml")
public class FXMLParserController {
	@FXML
	private AnchorPane rootPane;
	
}
