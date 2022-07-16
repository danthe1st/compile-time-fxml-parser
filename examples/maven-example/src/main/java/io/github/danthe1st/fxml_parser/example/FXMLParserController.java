package io.github.danthe1st.fxml_parser.example;

import java.net.URL;
import java.time.LocalTime;
import java.util.ResourceBundle;

import io.github.danthe1st.fxml_parser.api.ParseFXML;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;

@ParseFXML("fxml/test.fxml")
public class FXMLParserController implements Initializable {
	@FXML
	private Text bottomText;
	
	@FXML
	private AnchorPane rootPane;
	
	@FXML
	void onBottomTextClicked(MouseEvent event) {
		bottomText.setText("click: " + LocalTime.now());
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		System.out.println(location + ", " + resources);
	}

	public void initialize() {
		bottomText.setText("click me");
	}
}
