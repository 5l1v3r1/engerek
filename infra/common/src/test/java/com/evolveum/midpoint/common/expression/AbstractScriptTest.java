/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.common.expression;

import com.evolveum.midpoint.common.expression.functions.FunctionLibrary;
import com.evolveum.midpoint.common.expression.script.ScriptEvaluator;
import com.evolveum.midpoint.common.expression.script.ScriptExpression;
import com.evolveum.midpoint.common.expression.script.ScriptExpressionFactory;
import com.evolveum.midpoint.common.expression.script.ScriptVariables;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.MidPointPrismContextFactory;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.test.util.DirectoryFileObjectResolver;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ScriptExpressionEvaluatorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Radovan Semancik
 */
public abstract class AbstractScriptTest {

    private static final QName PROPERTY_NAME = new QName(MidPointConstants.NS_MIDPOINT_TEST_PREFIX, "whatever");
	private static final String NS_X = "http://example.com/xxx";
	private static final String NS_Y = "http://example.com/yyy";
	protected static File BASE_TEST_DIR = new File("src/test/resources/expression");
    protected static File OBJECTS_DIR = new File("src/test/resources/objects");

    protected ScriptExpressionFactory scriptExpressionfactory;
    protected ScriptEvaluator evaluator;
    
    @BeforeSuite
	public void setup() throws SchemaException, SAXException, IOException {
		PrettyPrinter.setDefaultNamespacePrefix(MidPointConstants.NS_MIDPOINT_PUBLIC_PREFIX);
		PrismTestUtil.resetPrismContext(MidPointPrismContextFactory.FACTORY);
	}

    @BeforeClass
    public void setupFactory() {
    	PrismContext prismContext = PrismTestUtil.getPrismContext();
    	ObjectResolver resolver = new DirectoryFileObjectResolver(OBJECTS_DIR);
        Collection<FunctionLibrary> functions = new ArrayList<FunctionLibrary>();
        functions.add(ExpressionUtil.createBasicFunctionLibrary(prismContext));
		scriptExpressionfactory = new ScriptExpressionFactory(resolver, prismContext, functions);
        evaluator = createEvaluator(prismContext);
        String languageUrl = evaluator.getLanguageUrl();
        System.out.println("Expression test for "+evaluator.getLanguageName()+": registering "+evaluator+" with URL "+languageUrl);
        scriptExpressionfactory.registerEvaluator(languageUrl, evaluator);
    }

	protected abstract ScriptEvaluator createEvaluator(PrismContext prismContext);
	
	protected abstract File getTestDir();
	
	protected boolean supportsRootNode() {
		return false;
	}
	
	@Test
    public void testExpressionSimple() throws Exception {
		evaluateAndAssertStringScalarExpresssion("expression-simple.xml", 
				"testExpressionSimple", null, "foobar");
    }


	@Test
    public void testExpressionStringVariables() throws Exception {
		evaluateAndAssertStringScalarExpresssion(
				"expression-string-variables.xml", 
				"testExpressionStringVariables", 
				ScriptVariables.create(
						new QName(NS_X, "foo"), "FOO",
						new QName(NS_Y, "bar"), "BAR"
				),
				"FOOBAR");
    }


    @Test
    public void testExpressionObjectRefVariables() throws Exception {
    	evaluateAndAssertStringScalarExpresssion(
    			"expression-objectref-variables.xml", 
    			"testExpressionObjectRefVariables", 
    			ScriptVariables.create(
						new QName(NS_X, "foo"), "Captain",
						new QName(NS_Y, "jack"), 
							MiscSchemaUtil.createObjectReference("c0c010c0-d34d-b33f-f00d-111111111111", UserType.COMPLEX_TYPE)
				), 
    			"Captain jack");
    }

    @Test
    public void testExpressionObjectRefVariablesPolyString() throws Exception {
    	evaluateAndAssertStringScalarExpresssion(
    			"expression-objectref-variables-polystring.xml", 
    			"testExpressionObjectRefVariablesPolyString",
    			ScriptVariables.create(
						new QName(NS_X, "foo"), "Captain",
						new QName(NS_Y, "jack"), 
							MiscSchemaUtil.createObjectReference("c0c010c0-d34d-b33f-f00d-111111111111", UserType.COMPLEX_TYPE)
				),
    			"Captain Jack Sparrow");
    }

    @Test
    public void testSystemVariables() throws Exception {
		evaluateAndAssertStringScalarExpresssion(
				"expression-system-variables.xml", 
    			"testSystemVariables", 
    			ScriptVariables.create(SchemaConstants.I_USER, 
    	    			MiscSchemaUtil.createObjectReference("c0c010c0-d34d-b33f-f00d-111111111111", UserType.COMPLEX_TYPE)),
    	    	"Jack");
    }

    @Test
    public void testRootNode() throws Exception {
    	if (!supportsRootNode()) {
    		return;
    	}
    	
    	evaluateAndAssertStringScalarExpresssion(
				"expression-root-node.xml", 
    			"testRootNode", 
    			ScriptVariables.create(null, 
    	    			MiscSchemaUtil.createObjectReference("c0c010c0-d34d-b33f-f00d-111111111111", UserType.COMPLEX_TYPE)),
    	    	"Black Pearl");
    }

	@Test
    public void testExpressionList() throws Exception {
		evaluateAndAssertStringListExpresssion(
				"expression-list.xml", 
    			"testExpressionList", 
    			ScriptVariables.create(
						new QName(NS_Y, "jack"), 
							MiscSchemaUtil.createObjectReference("c0c010c0-d34d-b33f-f00d-111111111111", UserType.COMPLEX_TYPE)
				),
    			"Leaders", "Followers");		
    }
	
	@Test
    public void testExpressionFunc() throws Exception {
		evaluateAndAssertStringScalarExpresssion("expression-func.xml", 
    			"testExpressionFunc", null, "gulocka v jamocke");
    }
	
	@Test
    public void testExpressionFuncConcatName() throws Exception {
		evaluateAndAssertStringScalarExpresssion("expression-func-concatname.xml", 
    			"testExpressionFuncConcatName", null, "Horatio Torquemada Marley");
    }

	
	private ScriptExpressionEvaluatorType parseScriptType(String fileName) throws SchemaException, FileNotFoundException, JAXBException {
		JAXBElement<ScriptExpressionEvaluatorType> expressionTypeElement = PrismTestUtil.unmarshalElement(
                new File(getTestDir(), fileName), ScriptExpressionEvaluatorType.class);
		return expressionTypeElement.getValue();
	}
	
	private <T> List<PrismPropertyValue<T>> evaluateExpression(ScriptExpressionEvaluatorType scriptType, ItemDefinition outputDefinition, 
			ScriptVariables variables, String shortDesc, OperationResult result) throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException {
		ScriptExpression scriptExpression = scriptExpressionfactory.createScriptExpression(scriptType, outputDefinition, shortDesc);
		return scriptExpression.evaluate(variables, null, shortDesc, result);
	}
	
	private <T> List<PrismPropertyValue<T>> evaluateExpression(ScriptExpressionEvaluatorType scriptType, QName typeName, boolean scalar, 
			ScriptVariables variables, String shortDesc, OperationResult result) throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException {
		ItemDefinition outputDefinition = new PrismPropertyDefinition(PROPERTY_NAME, PROPERTY_NAME, typeName, PrismTestUtil.getPrismContext());
		if (!scalar) {
			outputDefinition.setMaxOccurs(-1);
		}
		return evaluateExpression(scriptType, outputDefinition, variables, shortDesc, result);
	}
	
	private <T> PrismPropertyValue<T> evaluateExpressionScalar(ScriptExpressionEvaluatorType scriptType, QName typeName, 
			ScriptVariables variables, String shortDesc, OperationResult result) throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException {
		List<PrismPropertyValue<T>> expressionResultList = evaluateExpression(scriptType, typeName, true, variables, shortDesc, result);
		return asScalar(expressionResultList, shortDesc);
	}
	
	private <T> PrismPropertyValue<T> asScalar(List<PrismPropertyValue<T>> expressionResultList, String shortDesc) {
		if (expressionResultList.size() > 1) {
			AssertJUnit.fail("Expression "+shortDesc+" produces a list of "+expressionResultList.size()+" while only expected a single value: "+expressionResultList);
		}
		if (expressionResultList.isEmpty()) {
			return null;
		}
		return expressionResultList.iterator().next();
	}
	
	private void evaluateAndAssertStringScalarExpresssion(String fileName, String testName, ScriptVariables variables, String expectedValue) throws SchemaException, FileNotFoundException, JAXBException, ExpressionEvaluationException, ObjectNotFoundException {
		List<PrismPropertyValue<String>> expressionResultList = evaluateStringExpresssion(fileName, testName, variables, true);
		PrismPropertyValue<String> expressionResult = asScalar(expressionResultList, testName);
		assertEquals("Expression "+testName+" resulted in wrong value", expectedValue, expressionResult.getValue());
	}

	private void evaluateAndAssertStringListExpresssion(String fileName, String testName, ScriptVariables variables, String... expectedValues) throws SchemaException, FileNotFoundException, JAXBException, ExpressionEvaluationException, ObjectNotFoundException {
		List<PrismPropertyValue<String>> expressionResultList = evaluateStringExpresssion(fileName, testName, variables, true);
		TestUtil.assertSetEquals("Expression "+testName+" resulted in wrong values", PrismPropertyValue.getValues(expressionResultList), expectedValues);
	}

	private List<PrismPropertyValue<String>> evaluateStringExpresssion(String fileName, String testName, ScriptVariables variables, boolean scalar) throws SchemaException, FileNotFoundException, JAXBException, ExpressionEvaluationException, ObjectNotFoundException {
		displayTestTitle(testName);
		ScriptExpressionEvaluatorType scriptType = parseScriptType(fileName);
        OperationResult opResult = new OperationResult(testName);

        return evaluateExpression(scriptType, DOMUtil.XSD_STRING, true, variables, testName, opResult);        
	}
	
    
	private void displayTestTitle(String testName) {
		System.out.println("===[ "+evaluator.getLanguageName()+": "+testName+" ]===========================");
	}

}
