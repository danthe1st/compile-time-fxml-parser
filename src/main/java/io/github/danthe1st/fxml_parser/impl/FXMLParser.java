package io.github.danthe1st.fxml_parser.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
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

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import io.github.danthe1st.fxml_parser.impl.data.VariableDefinition;

class FXMLParser {
	// FXML spec:
	// https://docs.oracle.com/javase/8/javafx/api/javafx/fxml/doc-files/introduction_to_fxml.html
	
	private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
	private final ProcessingEnvironment processingEnv;
	private final String fxmlFile;
	private final Element element;
	private final String targetClass;
	private int currentNodeId = 0;
	private ClassWriter writer;
	private final Map<String, String> imports = new HashMap<>();
	private TypeElement controller = null;
	private final Map<String, Map.Entry<String, TypeElement>> fxIds = new HashMap<>();
	
	public static TypeElement parseFXML(ProcessingEnvironment processingEnv, Element element, String fxmlFile, String targetClass) throws IOException, ParserConfigurationException, SAXException {
		int lastShashIndex = fxmlFile.lastIndexOf('/');
		FileObject fxmlFileObject = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, lastShashIndex == -1 ? "" : fxmlFile.substring(0, lastShashIndex), fxmlFile.substring(lastShashIndex + 1));
		File f = new java.io.File(fxmlFileObject.toUri());
		if(!f.exists()){
			processingEnv.getMessager().printMessage(Kind.ERROR, "FXML resource missing: " + fxmlFile + " (" + f + ")", element);
			return null;
		}
		try(BufferedReader fxmlReader = new BufferedReader(fxmlFileObject.openReader(false))){
			return parseFXML(processingEnv, element, fxmlFile, fxmlReader, targetClass);
		}
	}
	
	private static TypeElement parseFXML(ProcessingEnvironment processingEnv, Element element, String fxmlFile, BufferedReader fxmlReader, String targetClass) throws ParserConfigurationException, SAXException, IOException {
		return new FXMLParser(processingEnv, targetClass, element, fxmlFile).parseFXML(fxmlReader);
	}
	
	private FXMLParser(ProcessingEnvironment processingEnv, String targetClass, Element element, String fxmlFile) {
		super();
		this.processingEnv = processingEnv;
		this.fxmlFile = fxmlFile;
		this.element = element;
		this.targetClass = targetClass;
	}
	
	private TypeElement parseFXML(BufferedReader fxmlReader) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
		InputSource source = new InputSource(fxmlReader);
		Document fxmlDocument = docBuilder.parse(source);
		NodeList childNodes = fxmlDocument.getChildNodes();
		boolean allowFurtherElements = true;
		String rootTypeName = null;
		for(int i = 0; i < childNodes.getLength(); i++){
			Node item = childNodes.item(i);
			if(!allowFurtherElements){
				processingEnv.getMessager().printMessage(Kind.ERROR, "invalid token in FXML file, name: " + item.getNodeName() + (item.getNodeValue() == null ? "" : ", value: " + item.getNodeValue()), element);
			}
			if("import".equals(item.getNodeName())){
				String name = item.getNodeValue();
				String simpleName = splitByLast(name, '.').getValue();
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
				Entry<String, String> targetSplit = splitByLast(targetClass, '.');
				String packageName = targetSplit.getKey();
				String className = targetSplit.getValue();
				try(ClassWriter writer = new ClassWriter(new BufferedWriter(targetClassObject.openWriter()))){
					this.writer = writer;
					if(packageName != null){
						writer.packageDeclaration(packageName);
					}
					for(String anImport : imports.values()){
						writer.addImport(anImport);
					}
					writer.beginClass(className);
					String nodeType = item.getNodeName();
					boolean isFXRoot = false;
					if("fx:root".equals(nodeType)){
						Node typeNode = item.getAttributes().getNamedItem("type");
						if(typeNode == null){
							processingEnv.getMessager().printMessage(Kind.ERROR, "fx:root element without type attribute present in FXML file", element);
							return null;
						}
						nodeType = typeNode.getNodeValue();
						isFXRoot = true;
					}
					rootTypeName = nodeType;
					writer.addVariable(new VariableDefinition("private " + nodeType, "rootNode"));
					writer.addVariable(new VariableDefinition("private " + ResourceBundle.class.getCanonicalName(), "resourceBundle"));
					writer.beginMethod(new String[] { "public" }, "buildNode", "void");
					if(!isFXRoot){
						writer.beginIf("rootNode != null");
						writer.addThrow("new IllegalStateException(\"Cannot call buildNode() multiple times\")");
						writer.endIf();
					}
					parseNode(item, imports);
					if(controller != null){
						addFXIdsToController();
						writeControllerInitialization(writer);
					}
					writer.addAssignment("rootNode", "node0");
					writer.endMethod();
					if(controller != null){
						writer.addVariable(new VariableDefinition("private " + controller.asType().toString(), "controller"));
						writer.beginMethod(new String[] { "public" }, "getController", controller.asType().toString());
						writer.addReturn("controller");
						writer.endMethod();
					}
					writer.beginMethod(new String[] { "public" }, "getRoot", nodeType);
					writer.addReturn("rootNode");
					writer.endMethod();
					
					if(isFXRoot){
						writer.beginMethod(new String[] { "public" }, "setRoot", "void", new VariableDefinition(nodeType, "root"));
						writer.addAssignment("rootNode", "root");
						writer.endMethod();
					}
					
					writer.beginMethod(new String[] { "public", "static" }, "createNode", nodeType);
					writer.addVariable(new VariableDefinition(targetClass, "loader"), "new " + targetClass + "()");
					writer.addMethodCall("loader", "buildNode");
					writer.addReturn("loader.getRoot()");
					writer.endMethod();
					
					writer.beginMethod(new String[] { "public" }, "setResourceBundle", "void", new VariableDefinition(ResourceBundle.class.getCanonicalName(), "bundle"));
					writer.beginIf("rootNode != null");
					writer.addThrow("new IllegalStateException(\"setResourceBundle() needs to be called before buildNode()\")");
					writer.endIf();
					writer.addAssignment("resourceBundle", "bundle");
					writer.endMethod();
					
					writer.endClass();
				}
			}
		}
		return rootTypeName == null ? null : getTypeMirrorFromName(rootTypeName, imports);
	}
	
	private void writeControllerInitialization(ClassWriter writer) throws IOException {
		TypeElement initializable = processingEnv.getElementUtils().getTypeElement("javafx.fxml.Initializable");
		if(initializable != null && processingEnv.getTypeUtils().isAssignable(controller.asType(), initializable.asType())){
			writer.addMethodCall("controller", "initialize", "getClass().getClassLoader().getResource(\"" + splitByLast(fxmlFile, '/').getKey() + "/\")", "resourceBundle");
		}
		List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(controller);
		for(Element member : members){
			if(member.getKind() == ElementKind.METHOD && "initialize".equals(member.getSimpleName().toString()) && ((ExecutableElement) member).getParameters().isEmpty() && isAnnotated(member, "javafx.fxml.FXML")){
				writer.addMethodCall("controller", "initialize");
			}
		}
	}
	
	private Map.Entry<String, String> splitByLast(String toSplit, char delim) {
		int lastIndex = toSplit.lastIndexOf(delim);
		String preDelim = lastIndex == -1 ? "" : toSplit.substring(0, lastIndex);
		String postDelim = toSplit.substring(lastIndex + 1);
		return Map.entry(preDelim, postDelim);
	}
	
	private void addFXIdsToController() throws IOException {
		try{
			fxIds.forEach((id, elemInfo) -> {
				String nodeName = elemInfo.getKey();
				TypeElement nodeType = elemInfo.getValue();
				try{
					writeParameter(imports, "controller", processingEnv.getElementUtils().getAllMembers(controller), id, ":" + nodeName, nodeType);
				}catch(IOException e){
					throw new UncheckedIOException(e);
				}
			});
		}catch(UncheckedIOException e){
			throw e.getCause();
		}
	}
	
	private void parseNode(Node item, Map<String, String> imports) throws IOException, DOMException, ParserConfigurationException, SAXException {
		// TODO what to do with text nodes?
		
		int nodeId = currentNodeId++;
		String typeName = item.getNodeName();
		NamedNodeMap attributes = item.getAttributes();
		boolean needToCreate = true;
		boolean isFXRoot = false;
		if("fx:root".equals(typeName)){
			Node typeNode = attributes.getNamedItem("type");
			if(typeName == null){
				processingEnv.getMessager().printMessage(Kind.ERROR, "fx:root element without type attribute present in FXML file", element);
				return;
			}
			if(nodeId != 0){
				processingEnv.getMessager().printMessage(Kind.ERROR, "fx:root is allowed only for the first element", element);
			}
			typeName = typeNode.getNodeValue();
			isFXRoot = true;
		}else if("fx:include".equals(typeName)){
			Node fxSource = attributes.getNamedItem("source");
			if(fxSource == null){
				processingEnv.getMessager().printMessage(Kind.ERROR, "fx:include element without source attribute present in FXML file", element);
				return;
			}
			String includeFXML = fxSource.getNodeValue();
			if(!includeFXML.startsWith("/") && fxmlFile.contains("/")){
				includeFXML = splitByLast(fxmlFile, '/').getKey() + "/" + includeFXML;
			}
			String includeClass = targetClass + "Include" + nodeId;
			TypeElement includedRoot = parseFXML(processingEnv, element, includeFXML, includeClass);
			String includeLoaderName = "node" + nodeId + "Loader";
			writer.addVariable(new VariableDefinition(includeClass, includeLoaderName), "new " + includeClass + "()");
			writer.addMethodCall(includeLoaderName, "setResourceBundle", "this.resourceBundle");
			writer.addMethodCall(includeLoaderName, "buildNode");
			writer.addVariable(new VariableDefinition(includedRoot.getQualifiedName().toString(), "node" + nodeId), includeLoaderName + ".getRoot()");
			typeName = includedRoot.getQualifiedName().toString();
			needToCreate = false;
		}else if("fx:reference".equals(typeName)){
			Node sourceAttr = attributes.getNamedItem("source");
			if(sourceAttr == null){
				throw new IllegalStateException("fx:reference element missing source attribute in FXML file");
			}
			Map.Entry<String, TypeElement> sourceEntry = fxIds.get(sourceAttr.getNodeValue());
			if(sourceEntry == null){
				throw new IllegalStateException("fx:reference element referring to unknown element " + sourceAttr.getNodeValue() + " in FXML file - make sure a matching fx:id is declared");
			}
			writer.addVariable(new VariableDefinition(sourceEntry.getValue().getQualifiedName().toString(), "node" + nodeId), sourceEntry.getKey());
			return;
		}else if("fx:copy".equals(typeName)){
			Node sourceAttr = attributes.getNamedItem("source");
			if(sourceAttr == null){
				throw new IllegalStateException("fx:source element missing source attribute in FXML file");
			}
			Map.Entry<String, TypeElement> sourceEntry = fxIds.get(sourceAttr.getNodeValue());
			if(sourceEntry == null){
				throw new IllegalStateException("fx:source element referring to unknown element " + sourceAttr.getNodeValue() + " in FXML file - make sure a matching fx:id is declared");
			}
			writer.addVariable(new VariableDefinition(sourceEntry.getValue().getQualifiedName().toString(), "node" + nodeId), "new " + sourceEntry.getValue().getQualifiedName() + "(" + sourceEntry.getKey() + ")");
			return;
		}
		TypeElement typeElem = getTypeMirrorFromName(typeName, imports);
		if(typeElem == null){
			throw new IllegalStateException("Invalid type in FXML file: " + typeName + " - an import may be missing or the class is not present in the module path");
		}
		
		List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(typeElem);
		if(needToCreate){
			if(isFXRoot){
				writer.addVariable(new VariableDefinition(typeName, "node" + nodeId), "rootNode");
				writer.beginIf("node" + nodeId + " == null");
				tryCreateInstance(nodeId + 1, typeName, attributes, typeElem);
				writer.addAssignment("node" + nodeId, "node" + (nodeId + 1));
				writer.endIf();
			}else{
				tryCreateInstance(nodeId, typeName, attributes, typeElem);
			}
			if(processingEnv.getTypeUtils().isSubtype(processingEnv.getTypeUtils().erasure(typeElem.asType()), processingEnv.getTypeUtils().erasure(processingEnv.getElementUtils().getTypeElement("java.util.Map").asType()))){
				for(int i = 0; i < attributes.getLength(); i++){
					Node attr = attributes.item(i);
					String paramName = attr.getNodeName();
					String paramValue = attr.getNodeValue();
					TypeMirror type = processingEnv.getElementUtils().getTypeElement("java.lang.String").asType();
					// TODO try to infer actual type from target
					writer.addMethodCall("node" + nodeId, "put", evaluateExpression(paramName, type), evaluateExpression(paramValue, type));
				}
				return;
			}
		}
		if(attributes != null){
			for(int i = 0; i < attributes.getLength(); i++){
				Node attr = attributes.item(i);
				String paramName = attr.getNodeName();
				String paramValue = attr.getNodeValue();
				if(needToCreate || !"source".equals(paramName)){
					writeParameter(imports, "node" + nodeId, members, paramName, paramValue, typeElem);
				}
			}
		}
		NodeList children = item.getChildNodes();
		for(int i = 0; i < children.getLength(); i++){
			Node child = children.item(i);
			if(child.getNodeName().startsWith("#")){
				// TODO
			}else{
				String nodeName = child.getNodeName();
				String attributeName = nodeName;
				String receiver = "node" + nodeId;
				if(attributeName.contains(".")){
					Entry<String, String> split = splitByLast(attributeName, '.');
					attributeName = split.getValue();
					receiver = split.getKey();
					members = processingEnv.getElementUtils().getAllMembers(getTypeMirrorFromName(receiver, imports));
				}
				String accessorSuffix = Character.toUpperCase(attributeName.charAt(0)) + (attributeName.length() == 1 ? "" : attributeName.substring(1));
				String getterName = "get" + accessorSuffix;
				String setterName = "set" + accessorSuffix;
				
				boolean isList = false;
				
				for(Element member : members){
					if(member.getKind() == ElementKind.METHOD && getterName.equals(member.getSimpleName().toString())){
						ExecutableType memberType = (ExecutableType) member.asType();
						if(memberType.getParameterTypes().isEmpty() && processingEnv.getTypeUtils().isSubtype(processingEnv.getTypeUtils().erasure(memberType.getReturnType()), processingEnv.getTypeUtils().erasure(processingEnv.getElementUtils().getTypeElement("java.util.List").asType()))){
							isList = true;
							break;
						}
					}
				}
				
				if(isList){
					NodeList grandChildren = child.getChildNodes();
					for(int j = 0; j < grandChildren.getLength(); j++){
						Node grandChild = grandChildren.item(j);
						if(grandChild.getNodeName().equals("#text")){
							// TODO
						}else{
							int childNodeId = currentNodeId;
							parseNode(grandChild, imports);
							writer.addMethodCall("node" + nodeId + "." + getterName + "()", "add", "node" + childNodeId);
						}
					}
				}else{
					int childNodeId = currentNodeId;
					NodeList grandChildren = child.getChildNodes();
					Node grandChild = null;
					for(int j = 0; j < grandChildren.getLength(); j++){
						Node c = grandChildren.item(j);
						if(c.getNodeName().equals("#text")){
							// TODO
						}else{
							if(grandChild == null){
								grandChild = c;
							}else{
								throw new IllegalStateException("too many children in element " + child.getNodeName() + " in FXML file");
							}
						}
					}
					resolveAttributeChildren(imports, nodeId, members, child, nodeName, receiver, setterName, childNodeId, grandChild);
				}
			}
		}
	}
	
	private void resolveAttributeChildren(Map<String, String> imports, int nodeId, List<? extends Element> members, Node child, String nodeName, String receiver, String setterName, int childNodeId, Node grandChild) throws IOException, ParserConfigurationException, SAXException {
		if(grandChild == null){
			for(Element member : members){
				if(member.getKind() == ElementKind.METHOD && setterName.equals(member.getSimpleName().toString()) && ((nodeName.contains(".") && ((ExecutableElement) member).getParameters().size() == 2) || (!nodeName.contains(".") && ((ExecutableElement) member).getParameters().size() == 1))){
					VariableElement paramType = ((ExecutableElement) member).getParameters().get(nodeName.contains(".") ? 1 : 0);
					try{
						String expression = evaluateExpression(child.getTextContent(), paramType.asType());
						if(nodeName.contains(".")){
							writer.addMethodCall(receiver, setterName, "node" + nodeId, expression);
						}else{
							writer.addMethodCall("node" + nodeId, setterName, expression);
						}
						return;
					}catch(IllegalStateException e){
						// handled by loop
					}
				}
			}
			throw new IllegalStateException("Element " + child.getNodeName() + " cannot be evaluated in FXML file");
		}else{
			parseNode(grandChild, imports);
		}
		writer.addMethodCall("node" + nodeId, setterName, "node" + childNodeId);
	}
	
	private Optional<String> getValueFromAnnotation(Element element, String annotationName, String annotationValueName) {
		return element
			.getAnnotationMirrors()
			.stream()
			.filter(mirror -> mirror.getAnnotationType().toString().equals(annotationName))
			.map(AnnotationMirror::getElementValues)
			.map(Map::entrySet)
			.flatMap(Set::stream)
			.filter(e -> e.getKey().toString().equals(annotationValueName + "()"))
			.map(e -> e.getValue().getValue().toString())
			.findAny();
	}
	
	private boolean isAnnotated(Element elem, String annotationName) {
		return elem
			.getAnnotationMirrors()
			.stream()
			.anyMatch(mirror -> mirror.getAnnotationType().toString().equals(annotationName));
	}
	
	private void tryCreateInstance(int nodeId, String typeName, NamedNodeMap attributes, TypeElement typeElem) throws IOException {
		Node fxValue = attributes.getNamedItem("fx:value");
		if(fxValue != null){
			for(Element member : typeElem.getEnclosedElements()){
				if(member.getKind() == ElementKind.METHOD && member.getSimpleName().toString().equals("valueOf") && ((ExecutableElement) member).getParameters().size() == 1){
					String expressionResult;
					try{
						expressionResult = evaluateExpression(fxValue.getNodeValue(), ((ExecutableElement) member).getParameters().get(0).asType());
						writer.addVariable(new VariableDefinition(typeName, "node" + nodeId), typeName + ".valueOf(" + expressionResult + ")");
						return;
					}catch(IllegalStateException e){
						// handled by loop
					}
				}
			}
			throw new IllegalStateException("fx:value present in " + typeName + " in FXML file but no matching valueOf method was found");
		}
		Node fxFactory = attributes.getNamedItem("fx:factory");
		if(fxFactory != null){
			writer.addVariable(new VariableDefinition(typeName, "node" + nodeId), typeName + "." + fxFactory.getNodeValue() + "()");
			return;
		}
		Node fxConstant = attributes.getNamedItem("fx:constant");
		if(fxConstant != null){
			writer.addVariable(new VariableDefinition(typeName, "node" + nodeId), typeName + "." + fxConstant.getNodeValue());
			return;
		}
		List<ExecutableElement> constructors = getConstructors(typeElem);
		List<String> maxParamExpressions = null;
		for(ExecutableElement constructor : constructors){
			Map<String, String> params = new HashMap<>();
			for(int i = 0; i < attributes.getLength(); i++){
				Node item = attributes.item(i);
				params.put(item.getNodeName(), item.getNodeValue());
			}
			List<String> paramExpressions = evaluateParameters(constructor, params);
			if(paramExpressions != null && (maxParamExpressions == null || paramExpressions.size() > maxParamExpressions.size())){
				maxParamExpressions = paramExpressions;
			}
		}
		if(maxParamExpressions != null){
			writer.addVariable(new VariableDefinition(typeName, "node" + nodeId), "new " + typeName + "(" + maxParamExpressions.stream().collect(Collectors.joining(", ")) + ")");
			return;
		}
		throw new IllegalStateException("No constructor found for " + typeName + " in FXML file");
	}
	
	private List<String> evaluateParameters(ExecutableElement constructor, Map<String, String> params) {
		List<String> paramExpressions = new ArrayList<>();
		for(VariableElement param : constructor.getParameters()){
			String name = getValueFromAnnotation(param, "javafx.beans.NamedArg", "value")
				.orElse(param.getSimpleName().toString());
			String value;
			if(params.containsKey(name)){
				value = params.get(name);
			}else{
				value = getValueFromAnnotation(param, "javafx.beans.NamedArg", "defaultValue")
					.orElse("");
			}
			
			try{
				paramExpressions.add(evaluateExpression(value, param.asType()));
			}catch(IllegalStateException e){
				paramExpressions = null;
				break;
			}
		}
		return paramExpressions;
	}
	
	private void writeParameter(Map<String, String> imports, String nodeVariableName, List<? extends Element> members, String paramName, String paramValue, TypeElement nodeType) throws IOException {
		String receiver = nodeVariableName;
		boolean isStaticCall = false;
		if(paramName.startsWith("fx:")){
			controller = writeFXParameter(paramName, paramValue, controller, imports, nodeVariableName, nodeType);// TODO move this call out of this method
			return;
		}
		if(paramName.contains(".")){
			Entry<String, String> paramNameSplit = splitByLast(paramName, '.');
			receiver = paramNameSplit.getKey();
			String oldValue = paramName;
			paramName = paramNameSplit.getValue();
			TypeElement mirror = getTypeMirrorFromName(receiver, imports);
			if(mirror == null){
				throw new IllegalStateException("Invalid type in FXML file: " + oldValue);
			}
			members = processingEnv.getElementUtils().getAllMembers(mirror);
			isStaticCall = true;
		}
		String accessorSuffix = Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
		String setterName = "set" + accessorSuffix;
		boolean paramSet = false;
		for(Element member : members){
			if(member.getKind() == ElementKind.METHOD && setterName.equals(member.getSimpleName().toString())){
				ExecutableType memberType = (ExecutableType) member.asType();
				if((isStaticCall && memberType.getParameterTypes().size() == 2) || (!isStaticCall && memberType.getParameterTypes().size() == 1)){
					TypeMirror setterParam = memberType.getParameterTypes().get(isStaticCall ? 1 : 0);
					String expression = evaluateExpression(paramValue, setterParam);
					if(isStaticCall){
						writer.addMethodCall(receiver, setterName, nodeVariableName, expression);
					}else{
						writer.addMethodCall(receiver, setterName, expression);
					}
					return;
				}
			}
			
			if(member.getKind() == ElementKind.FIELD && paramName.equals(member.getSimpleName().toString()) && (member.getModifiers().contains(Modifier.PUBLIC) || (!member.getModifiers().contains(Modifier.PRIVATE) && splitByLast(member.getEnclosingElement().asType().toString(), '.').getKey().equals(splitByLast(targetClass, '.').getKey())))){
				writer.addAssignment(receiver + "." + paramName, evaluateExpression(paramValue, ((VariableElement) member).asType()));
				return;
			}else if(member.getKind() == ElementKind.FIELD && paramName.equals(member.getSimpleName().toString()) && isAnnotated(member, "javafx.fxml.FXML") && processingEnv.getElementUtils().getModuleOf(member).equals(processingEnv.getElementUtils().getModuleOf(element))){
				writer.beginTryBlock();
				writer.addVariable(new VariableDefinition("java.lang.reflect.Field", "field"), receiver + ".getClass().getDeclaredField(\"" + paramName + "\")");
				writer.addMethodCall("field", "setAccessible", "true");
				writer.addMethodCall("field", "set", receiver, evaluateExpression(paramValue, ((VariableElement) member).asType()));
				writer.swtichToCatchBlock("java.lang.Exception", "e");
				writer.addStatement("throw new java.lang.AssertionError(e)");
				writer.endTryBlock();
				return;
			}
		}
		if(!paramSet && !paramName.startsWith("xmlns")){
			processingEnv.getMessager().printMessage(Kind.MANDATORY_WARNING, "unused parameter in FXML file: " + paramName, element);
		}
	}
	
	private TypeElement writeFXParameter(String paramName, String paramValue, TypeElement controller, Map<String, String> imports, String nodeVariableName, TypeElement nodeType) throws IOException {
		paramName = paramName.substring(paramName.indexOf(':') + 1);
		switch(paramName) {
		case "controller":
			if(controller != null){
				throw new IllegalStateException("duplicate controller");
			}
			TypeElement controllerType = getTypeMirrorFromName(paramValue, imports);
			if(controllerType == null){
				throw new IllegalStateException("unknown controller type: " + paramValue);
			}
			writer.addAssignment("controller", "new " + paramValue + "()");
			return controllerType;
		case "id":
			Entry<String, TypeElement> prev = fxIds.put(paramValue, Map.entry(nodeVariableName, nodeType));
			if(prev != null){
				throw new IllegalArgumentException("Duplicate fx:id: " + paramValue);
			}
			break;
		case "value", "factory", "constant":
			break;// handled elsewhere
		default:
			throw new IllegalArgumentException("Unexpected value: " + paramName);
		}
		return controller;
	}
	
	private String evaluateExpression(String paramValue, TypeMirror expressionType) {
		if((expressionType.getKind().isPrimitive() || processingEnv.getTypeUtils().isAssignable(expressionType, processingEnv.getElementUtils().getTypeElement("java.lang.Number").asType())) && paramValue.matches("true|false|[0-9.]+")){
			// TODO check for wrapper type properly if possible
			if(paramValue.isEmpty()){
				paramValue = "0";
			}
			return paramValue;
		}else if(processingEnv.getTypeUtils().isSubtype(processingEnv.getElementUtils().getTypeElement("java.lang.String").asType(), expressionType)){
			return '"' + paramValue + '"';
		}else if(expressionType instanceof DeclaredType t && t.asElement().getKind() == ElementKind.ENUM){
			return t + "." + paramValue.toUpperCase();
		}else if(fxIds.containsKey(paramValue) && processingEnv.getTypeUtils().isAssignable(fxIds.get(paramValue).getValue().asType(), expressionType)){
			return fxIds.get(paramValue).getKey();
		}else if(paramValue.startsWith(":node")){
			return paramValue.substring(1);
		}else if(paramValue.startsWith("#")){
			if(controller == null){
				processingEnv.getMessager().printMessage(Kind.ERROR, "try to set listener but no controller is specified in FXML file", element);
				return "unused->{/*ERROR: missing controller*/}";
			}else{
				return "controller::" + paramValue.substring(1);
			}
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
	
}
