package io.github.danthe1st.fxml_parser.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import io.github.danthe1st.fxml_parser.api.ParseFXML;

@SupportedAnnotationTypes("io.github.danthe1st.fxml_parser.api.ParseFXML")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class FXMLProcessor extends AbstractProcessor {
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for(Element element : roundEnv.getElementsAnnotatedWith(ParseFXML.class)){
			if(element.getKind() == ElementKind.CLASS){
				ParseFXML annotation = element.getAnnotationsByType(ParseFXML.class)[0];
				String fxmlFile = annotation.value();
				String targetClass = annotation.className();
				try{
					if(targetClass.isEmpty()){
						DeclaredType typeMirror = (DeclaredType) element.asType();
						targetClass = typeMirror.toString() + "FXMLRepresentation";
					}else if(!targetClass.contains(".")){
						targetClass = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName() + "." + targetClass;
					}
					parseFXML(element, fxmlFile, targetClass);
				}catch(Exception e){
					processingEnv.getMessager().printMessage(Kind.ERROR, "An exception occured trying to parse the FXML file " + fxmlFile + ": " + e.getClass().getCanonicalName() + (e.getMessage() == null ? "" : ": " + e.getMessage()), element);
					try(StringWriter sw = new StringWriter();
							PrintWriter pw = new PrintWriter(sw)){
						e.printStackTrace(pw);
						processingEnv.getMessager().printMessage(Kind.ERROR, sw.getBuffer(), element);
					}catch(IOException e1){
						// reported anyways
					}
				}
			}else{
				processingEnv.getMessager().printMessage(Kind.ERROR, "Only classes may be annotated with @" + ParseFXML.class.getSimpleName(), element);
			}
			
		}
		return false;
	}
	
	private void parseFXML(Element element, String fxmlFile, String targetClass) throws IOException, ParserConfigurationException, SAXException {
		int lastShashIndex = fxmlFile.lastIndexOf('/');
		FileObject fxmlFileObject = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, lastShashIndex == -1 ? "" : fxmlFile.substring(0, lastShashIndex), fxmlFile.substring(lastShashIndex + 1));
		File f = new java.io.File(fxmlFileObject.toUri());
		if(!f.exists()){
			processingEnv.getMessager().printMessage(Kind.ERROR, "FXML resource missing: " + fxmlFile + " (" + f + ")", element);
			return;
		}
		try(BufferedReader fxmlReader = new BufferedReader(fxmlFileObject.openReader(false))){
			FXMLParser.parseFXML(processingEnv, element, fxmlFile, fxmlReader, targetClass);
		}
	}
	
}
