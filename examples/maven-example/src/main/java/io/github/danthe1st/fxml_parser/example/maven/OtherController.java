package io.github.danthe1st.fxml_parser.example.maven;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.Initializable;

public class OtherController implements Initializable {
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		System.out.println("child initialized: " + location + "; " + resources);
	}
	
}
