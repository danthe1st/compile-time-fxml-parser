# Compile-time FXML-parser

Compile-time FXML-parser is an annotation processor that parses FXML files at compile-time.

### Setup
* Make sure a JDK >= 17 is installed and the `PATH` and `JAVA_HOME` environment variables point to it
* [Download](https://maven.apache.org/download.cgi) and [install](https://maven.apache.org/install.html) [Maven](https://maven.apache.org/)
* Download the source code of this repository
* Run `mvn clean install` in the directory of Compile-time FXML-parser
* Add the following dependency to the `pom.xml` of the project where you want to use Compile-time JSON-parser (replace `VERSION` with the version from the [`pom.xml` of Compile-time FXML-parser](pom.xml)
```xml
<dependency>
    <groupId>io.github.danthe1st</groupId>
    <artifactId>compile-time-json-parser</artifactId>
    <version>VERSION</version>
</dependency>
```
* If you wish to use JPMS, also add the annotation processor to the `maven-compiler-plugin`
```xml
<plugin>
	<artifactId>maven-compiler-plugin</artifactId>
	<version>3.8.1</version>
	<configuration>
		<release>11</release>
		<annotationProcessorPaths>
			<annotationProcessorPath>
				<groupId>io.github.danthe1st</groupId>
				<artifactId>compile-time-json-parser</artifactId>
				<version>VERSION</version>
			</annotationProcessorPath>
		</annotationProcessorPaths>
	</configuration>
</plugin>
```
* Enable annotation processing for your project in your IDE

### Usage
In order to parse an FXML file,
annotate a class with `@ParseFXML`
and specify the path of the FXML file in paranthesis.

The FXML file is resolved relative to the resource root.

This should automatically create a class with the name of the annotated class + "FXMLParser" (the class name can be overwritten by specifying `className` in the annotation) with a method `createNode()`.
This method loads the UI elements specified in the FXML file and returns the root element.

An example can be found in [examples/maven-example](examples/maven-example).
