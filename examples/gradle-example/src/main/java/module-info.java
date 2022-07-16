module io.github.danthe1st.fxml_parser_example.gradle {
	requires java.base;
	requires javafx.base;
	requires javafx.controls;
	requires transitive javafx.graphics;
	requires javafx.fxml;

	exports io.github.danthe1st.fxml_parser.example.gradle to javafx.graphics;

	requires static io.github.danthe1st.fxml_parser;
}