package io.github.danthe1st.fxml_parser.api;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation for FXML files that should be parsed at compile-time.<br/>
 * Only classes should be annotated with {@link ParseFXML}
 */
@Retention(SOURCE)
@Target(ElementType.TYPE)
public @interface ParseFXML {
	/**
	 * The name of the class to generate. <br/>
	 * The class name is relative to the annotated class if it does not contain any
	 * dots. If it does contain dots, it is the fully qualified name of the class.
	 *
	 * @return the name of the class to generate
	 */
	String className() default "";
	
	/**
	 * Path of the FXML file relative to the resource root.
	 *
	 * @return path of the FXML file
	 */
	String value();
}
