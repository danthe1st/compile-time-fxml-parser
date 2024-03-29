# Compile-time FXML-parser - Maven example

This is an example how to use Compile-time FXML-parser with Maven.


### Running

If a suiting JDK, and Maven are installed, this project can be run using the command `mvn clean compile javafx:run`.

That should open a window similar to this:

![image](https://user-images.githubusercontent.com/34687786/178830955-e1d77fa8-1bcf-4265-b0f8-0c7f656ae804.png)

### Important files and directories
* The file [pom.xml](pom.xml) contains project configuration.
* The FXML file parsed is located in [src/main/resources/fxml/test.fxml](src/main/resources/fxml/test.fxml).
* The class [FXMLParserController](src/main/java/io/github/danthe1st/fxml_parser/example/maven/FXMLParserController.java) is annotated with `@ParseFXML("test.fxml")` which results in the class `FXMLParserControllerFXMLFXMLRepresentation` being generated and accessible.
* The class [FXMLParserExample](src/main/java/io/github/danthe1st/fxml_parser/maven/example/FXMLParserController.java) is the main class.
