package io.github.danthe1st.fxml_parser.impl;

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
					FXMLParser.parseFXML(processingEnv, element, fxmlFile, targetClass);
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
	
}
