module io.github.danthe1st.fxml_parser_example {
	requires java.base;
	requires javafx.base;
	requires javafx.controls;
	requires transitive javafx.graphics;
	
	exports io.github.danthe1st.fxml_parser.example to javafx.graphics;
	
	requires static io.github.danthe1st.fxml_parser;
}