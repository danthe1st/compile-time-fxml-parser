# Compile-time FXML-parser

[![Release](https://jitpack.io/v/danthe1st/compile-time-fxml-parser.svg)](https://jitpack.io/#danthe1st/compile-time-fxml-parser)

Compile-time FXML-parser is an annotation processor that parses FXML files at compile-time.

## User Setup

* Make sure a JDK >= 17 is installed and the `PATH` and `JAVA_HOME` environment variables point to it
* [Download](https://maven.apache.org/download.cgi) and [install](https://maven.apache.org/install.html) [Maven](https://maven.apache.org/)
* [Create a JavaFX project with Maven](https://openjfx.io/openjfx-docs/#maven)
* Add the [Jitpack repository](https://jitpack.io) to the `<repositories>` section of your `pom.xml`:
```xml
<repositories>
	<repository>
		<id>jitpack.io</id>
		<url>https://jitpack.io</url>
	</repository>
</repositories>
```
* Add the following dependency to the `pom.xml` of the project where you want to use Compile-time JSON-parser (If you want to use a specific version, you can specify any commit hash instead of `-SNAPSHOT`)
```xml
<dependency>
    <groupId>com.github.danthe1st</groupId>
    <artifactId>compile-time-json-parser</artifactId>
    <version>-SNAPSHOT</version>
</dependency>
```
* If you wish to use JPMS, also add the annotation processor to the `maven-compiler-plugin`. Note that the versions of Compile-time FXML-parser need to match
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
				<version>-SNAPSHOT</version>
			</annotationProcessorPath>
		</annotationProcessorPaths>
	</configuration>
</plugin>
```
* Enable annotation processing for your project in your IDE

### Developer Setup
* Make sure a JDK >= 17 is installed and the `PATH` and `JAVA_HOME` environment variables point to it
* [Download](https://maven.apache.org/download.cgi) and [install](https://maven.apache.org/install.html) [Maven](https://maven.apache.org/)
* Download the source code of this repository
* Run `mvn clean install` in the directory of Compile-time FXML-parser
* [Create a JavaFX project with Maven](https://openjfx.io/openjfx-docs/#maven)
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

This should automatically create a class with the name of the annotated class + "FXMLRepresentation" (the class name can be overwritten by specifying `className` in the annotation) with a method `createNode()`.
This method loads the UI elements specified in the FXML file and returns the root element.

An example can be found in [examples/maven-example](examples/maven-example).

### Limitations

This project is not finished and is therefore missing functionality.

Furthermore, the following things needed to be left out because of Compile-time FXML-parser's static nature:

- Builders (using `javafx.util.Builder`) are not supported because they require looking up attributes of arbitary subclasses in unsupported packages.
- Scripts (using `fx:script`) are not supported because of the static nature of Compile-time FXML-parser

Other limitations include:

- Big FXML files may fail compilation because of too many local variables. In that case, one split up the FXML file into multiple files using `fx:include`.
- `fx:id`s can only be accessed after being declared