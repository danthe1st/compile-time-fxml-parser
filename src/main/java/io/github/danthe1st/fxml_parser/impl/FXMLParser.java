package io.github.danthe1st.fxml_parser.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import io.github.danthe1st.fxml_parser.api.ParseFXML;
import io.github.danthe1st.fxml_parser.impl.data.VariableDefinition;
import javafx.beans.NamedArg;

@SupportedAnnotationTypes("io.github.danthe1st.fxml_parser.api.ParseFXML")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class FXMLParser extends AbstractProcessor {
	// FXML spec:
	// https://docs.oracle.com/javase/8/javafx/api/javafx/fxml/doc-files/introduction_to_fxml.html

	private DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

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
						targetClass = typeMirror.toString() + "FXMLParser";
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
		if(!new java.io.File(fxmlFileObject.toUri()).exists()){
			processingEnv.getMessager().printMessage(Kind.ERROR, "FXML resource missing: " + fxmlFile, element);
			return;
		}
		try(BufferedReader fxmlReader = new BufferedReader(fxmlFileObject.openReader(false))){
			parseFXML(element, fxmlReader, targetClass);
		}
	}

	private void parseFXML(Element element, BufferedReader fxmlReader, String targetClass) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
		InputSource source = new InputSource(fxmlReader);
		Document fxmlDocument = docBuilder.parse(source);
		NodeList childNodes = fxmlDocument.getChildNodes();
		boolean allowFurtherElements = true;
		Map<String, String> imports = new HashMap<>();
		for(int i = 0; i < childNodes.getLength(); i++){
			Node item = childNodes.item(i);
			if(!allowFurtherElements){
				processingEnv.getMessager().printMessage(Kind.ERROR, "invalid token in FXML file, name: " + item.getNodeName() + (item.getNodeValue() == null ? "" : ", value: " + item.getNodeValue()), element);
			}
			if("import".equals(item.getNodeName())){
				String name = item.getNodeValue();
				String simpleName = name.substring(name.lastIndexOf('.') + 1);
				imports.put(simpleName, item.getNodeValue());
			}else{
				Element[] importElements = new Element[imports.size() + 1];
				importElements[0] = element;
				int j = 1;
				for(String anImport : imports.values()){
					importElements[j++] = processingEnv.getElementUtils().getTypeElement(anImport);
				}
				JavaFileObject targetClassObject = processingEnv.getFiler().createSourceFile(targetClass, importElements);
				allowFurtherElements = false;
				int lastDotIndex = targetClass.lastIndexOf(".");
				String packageName = lastDotIndex == -1 ? null : targetClass.substring(0, lastDotIndex);
				String className = targetClass.substring(lastDotIndex + 1);
				try(ClassWriter writer = new ClassWriter(new BufferedWriter(targetClassObject.openWriter()))){
					if(packageName != null){
						writer.packageDeclaration(packageName);
					}
					for(String anImport : imports.values()){
						writer.addImport(anImport);
					}
					writer.beginClass(className);
					writer.beginMethod("createNode", item.getNodeName());
					parseNode(writer, item, new IntHolder(0), imports);
					writer.addReturn("node0");
					writer.endMethod();
					writer.endClass();
				}
			}
		}
	}
	
	private void parseNode(ClassWriter writer, Node item, IntHolder currentNodeId, Map<String, String> imports) throws IOException {
		// TODO what to do with text nodes?
		// TODO FXML namespace
		// TODO builders
		
		int nodeId = currentNodeId.value++;
		String typeName = item.getNodeName();
		TypeElement typeElem = getTypeMirrorFromName(typeName, imports);
		if(typeElem == null){
			throw new IllegalStateException("Invalid type in FXML file: " + typeName + " - an import may be missing or the class is not present in the module path");
		}
		NamedNodeMap attributes = item.getAttributes();
		List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(typeElem);
		List<ExecutableElement> constructors = getConstructors(typeElem);
		boolean constructorFound = false;
		outer: for(ExecutableElement constructor : constructors){
			List<String> paramExpressions = new ArrayList<>();
			for(VariableElement param : constructor.getParameters()){
				String name = param.getSimpleName().toString();
				NamedArg namedArg = param.getAnnotation(NamedArg.class);
				if(namedArg != null){
					name = namedArg.value();
				}
				Node node = attributes.getNamedItem(name);
				String value;
				if(node == null){
					value = namedArg.defaultValue();
				}else{
					value = node.getNodeValue();
				}
				
				try{
					paramExpressions.add(evaluateExpression(value, param.asType()));
				}catch(IllegalStateException e){
					continue outer;
				}
			}
			writer.addVariable(new VariableDefinition(typeName, "node" + nodeId), "new " + typeName + "(" + paramExpressions.stream().collect(Collectors.joining(", ")) + ")");
			constructorFound = true;
			break;
		}
		if(!constructorFound){
			throw new IllegalStateException("No constructor found for " + typeName + " in FXML file");
		}
		
		for(int i = 0; i < (attributes == null ? 0 : attributes.getLength()); i++){
			Node attr = attributes.item(i);
			String paramName = attr.getNodeName();
			String paramValue = attr.getNodeValue();
			String receiver = "node" + nodeId;
			List<? extends Element> membersCopy = members;
			boolean isStaticCall = false;
			if(paramName.contains(".")){
				int lastDotIndex = paramName.lastIndexOf('.');
				receiver = paramName.substring(0, lastDotIndex);
				String oldValue = paramName;
				paramName = paramName.substring(lastDotIndex + 1);
				TypeElement mirror = getTypeMirrorFromName(receiver, imports);
				if(mirror == null){
					processingEnv.getMessager().printMessage(Kind.WARNING, "Invalid type in FXML file: " + oldValue);
					continue;// TODO replae with exception
				}
				membersCopy = processingEnv.getElementUtils().getAllMembers(mirror);
				isStaticCall = true;
			}
			String accessorSuffix = Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
			String setterName = "set" + accessorSuffix;
			for(Element member : membersCopy){
				if(member.getKind() == ElementKind.METHOD && setterName.equals(member.getSimpleName().toString())){
					ExecutableType memberType = (ExecutableType) member.asType();
					if((isStaticCall && memberType.getParameterTypes().size() == 2) || (!isStaticCall && memberType.getParameterTypes().size() == 1)){
						TypeMirror setterParam = memberType.getParameterTypes().get(isStaticCall ? 1 : 0);
						String expression = evaluateExpression(paramValue, setterParam);
						if(isStaticCall){
							writer.addMethodCall(receiver, setterName, "node" + nodeId, expression);
						}else{
							writer.addMethodCall(receiver, setterName, expression);
						}
						break;
					}
				}
			}
		}
		
		// TODO attributes (using 'params')
		NodeList children = item.getChildNodes();
		for(int i = 0; i < children.getLength(); i++){
			Node child = children.item(i);
			if(child.getNodeName().startsWith("#")){
				// TODO
			}else{
				
				String nodeName = child.getNodeName();
				String accessorSuffix = Character.toUpperCase(nodeName.charAt(0)) + nodeName.substring(1);
				String getterName = "get" + accessorSuffix;
				String setterName = "set" + accessorSuffix;
				
				boolean isList = false;
				
				for(Element member : members){
					if(member.getKind() == ElementKind.METHOD && getterName.equals(member.getSimpleName().toString())){
						ExecutableType memberType = (ExecutableType) member.asType();
						if(memberType.getParameterTypes().isEmpty() && processingEnv.getTypeUtils().isSubtype(processingEnv.getTypeUtils().erasure(memberType.getReturnType()), processingEnv.getTypeUtils().erasure(processingEnv.getElementUtils().getTypeElement("java.util.List").asType()))){
							isList = true;
						}
					}
				}
				
				if(isList){
					NodeList grandChildren = child.getChildNodes();
					for(int j = 0; j < grandChildren.getLength(); j++){
						Node grandChild = grandChildren.item(j);
						if(grandChild.getNodeName().startsWith("#")){
							// TODO
						}else{
							int childNodeId = currentNodeId.value;
							parseNode(writer, grandChild, currentNodeId, imports);
							writer.addMethodCall("node" + nodeId + "." + getterName + "()", "add", "node" + childNodeId);
						}
					}
				}else{
					int childNodeId = currentNodeId.value;
					NodeList grandChildren = child.getChildNodes();
					Node grandChild = null;
					for(int j = 0; j < grandChildren.getLength(); j++){
						Node c = grandChildren.item(j);
						if(c.getNodeName().startsWith("#")){
							// TODO
						}else{
							if(grandChild == null){
								grandChild = c;
							}else{
								var x = members.stream().filter(m -> m.getSimpleName().toString().equals("getChildren")).findAny().orElse(null);
								throw new IllegalStateException("too many children in element " + child.getNodeName() + " in FXML file");
							}
						}
					}
					parseNode(writer, grandChild, currentNodeId, imports);
					writer.addMethodCall("node" + nodeId, setterName, "node" + childNodeId);
				}
			}
		}
	}
	
	private String evaluateExpression(String paramValue, TypeMirror expressionType) {
		if(expressionType.getKind().isPrimitive() || processingEnv.getTypeUtils().isAssignable(expressionType, processingEnv.getElementUtils().getTypeElement("java.lang.Number").asType())){
			// TODO check for wrapper type
			return paramValue;
		}else if(processingEnv.getTypeUtils().isSameType(expressionType, processingEnv.getElementUtils().getTypeElement("java.lang.String").asType())){
			return '"' + paramValue + '"';
		}else if(expressionType instanceof DeclaredType t && t.asElement().getKind() == ElementKind.ENUM){
			return t + "." + paramValue.toUpperCase();
		}else{
			throw new IllegalStateException("trying to set unknown type in FXML file: " + expressionType);
		}
	}

	private TypeElement getTypeMirrorFromName(String name, Map<String, String> imports) {
		if(imports.containsKey(name)){
			name = imports.get(name);
		}
		for(ModuleElement module : processingEnv.getElementUtils().getAllModuleElements()){
			TypeElement type = processingEnv.getElementUtils().getTypeElement(module, name);
			if(type != null){
				return type;
			}
		}
		return processingEnv.getElementUtils().getTypeElement(name);
	}

	private List<ExecutableElement> getConstructors(Element elem) {
		List<ExecutableElement> constructors = new ArrayList<>();
		for(Element element : elem.getEnclosedElements()){
			if(element instanceof ExecutableElement e && element.getKind() == ElementKind.CONSTRUCTOR && element.getModifiers().contains(Modifier.PUBLIC)){
				if(e.getParameters().isEmpty()){
					constructors.add(0, e);
				}else{
					constructors.add(e);
				}
			}
		}
		return constructors;
	}

	private class IntHolder {
		int value;

		public IntHolder(int value) {
			this.value = value;
		}
	}
}
