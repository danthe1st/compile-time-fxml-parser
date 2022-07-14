package io.github.danthe1st.fxml_parser.example;

import io.github.danthe1st.fxml_parser.api.ParseFXML;
import javafx.scene.layout.AnchorPane;

@ParseFXML("test.fxml")
public class FXMLParserController {
	private AnchorPane rootPane;

	public void setRootPane(AnchorPane rootPane) {
		this.rootPane = rootPane;
	}
}
