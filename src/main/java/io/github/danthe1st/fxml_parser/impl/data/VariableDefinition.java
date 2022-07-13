package io.github.danthe1st.fxml_parser.impl.data;

public class VariableDefinition {
	private final String type;
	private final String name;
	
	public VariableDefinition(String type, String name) {
		this.type = type;
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}
	
}