module io.github.danthe1st.fxml_parser {
	requires java.base;
	requires java.compiler;
	requires java.xml;
	requires javafx.base;

	exports io.github.danthe1st.fxml_parser.api;

	provides javax.annotation.processing.Processor with io.github.danthe1st.fxml_parser.impl.FXMLProcessor;
}