/*
 * Copyright (c) 2010-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.model.intest.scripting;

import com.evolveum.midpoint.common.LoggingConfigurationManager;
import com.evolveum.midpoint.model.api.PipelineItem;
import com.evolveum.midpoint.model.impl.ModelWebService;
import com.evolveum.midpoint.model.impl.scripting.ExecutionContext;
import com.evolveum.midpoint.model.impl.scripting.PipelineData;
import com.evolveum.midpoint.model.impl.scripting.ScriptingExpressionEvaluator;
import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.notifications.api.transports.Message;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.builder.DeltaBuilder;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.LogfileTestTailer;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;
import org.testng.collections.Sets;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.AssertJUnit.*;

/**
 * @author mederly
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestScriptingBasic extends AbstractInitializedModelIntegrationTest {
	
	public static final File TEST_DIR = new File("src/test/resources/scripting");
    private static final String DOT_CLASS = TestScriptingBasic.class.getName() + ".";
    private static final File LOG_FILE = new File(TEST_DIR, "log.xml");
    private static final File SEARCH_FOR_USERS_FILE = new File(TEST_DIR, "search-for-users.xml");
    private static final File SEARCH_FOR_USERS_RESOLVE_NAMES_FOR_ROLE_MEMBERSHIP_REF_FILE = new File(TEST_DIR, "search-for-users-resolve-names-for-roleMembershipRef.xml");
    private static final File SEARCH_FOR_USERS_RESOLVE_ROLE_MEMBERSHIP_REF_FILE = new File(TEST_DIR, "search-for-users-resolve-roleMembershipRef.xml");
    private static final File SEARCH_FOR_SHADOWS_FILE = new File(TEST_DIR, "search-for-shadows.xml");
    private static final File SEARCH_FOR_SHADOWS_NOFETCH_FILE = new File(TEST_DIR, "search-for-shadows-nofetch.xml");
    private static final File SEARCH_FOR_RESOURCES_FILE = new File(TEST_DIR, "search-for-resources.xml");
    private static final File SEARCH_FOR_ROLES_FILE = new File(TEST_DIR, "search-for-roles.xml");
    private static final File SEARCH_FOR_USERS_ACCOUNTS_FILE = new File(TEST_DIR, "search-for-users-accounts.xml");
    private static final File SEARCH_FOR_USERS_ACCOUNTS_NOFETCH_FILE = new File(TEST_DIR, "search-for-users-accounts-nofetch.xml");
    private static final File DISABLE_JACK_FILE = new File(TEST_DIR, "disable-jack.xml");
    private static final File ENABLE_JACK_FILE = new File(TEST_DIR, "enable-jack.xml");
    private static final File DELETE_AND_ADD_JACK_FILE = new File(TEST_DIR, "delete-and-add-jack.xml");
    private static final File MODIFY_JACK_FILE = new File(TEST_DIR, "modify-jack.xml");
    private static final File MODIFY_JACK_BACK_FILE = new File(TEST_DIR, "modify-jack-back.xml");
    private static final File RECOMPUTE_JACK_FILE = new File(TEST_DIR, "recompute-jack.xml");
    private static final File ASSIGN_TO_JACK_FILE = new File(TEST_DIR, "assign-to-jack.xml");
    private static final File ASSIGN_TO_JACK_2_FILE = new File(TEST_DIR, "assign-to-jack-2.xml");
    private static final File PURGE_DUMMY_BLACK_SCHEMA_FILE = new File(TEST_DIR, "purge-dummy-black-schema.xml");
    private static final File TEST_DUMMY_RESOURCE_FILE = new File(TEST_DIR, "test-dummy-resource.xml");
    private static final File NOTIFICATION_ABOUT_JACK_FILE = new File(TEST_DIR, "notification-about-jack.xml");
    private static final File NOTIFICATION_ABOUT_JACK_TYPE2_FILE = new File(TEST_DIR, "notification-about-jack-type2.xml");
	private static final File SCRIPTING_USERS_FILE = new File(TEST_DIR, "scripting-users.xml");
	private static final File GENERATE_PASSWORDS_FILE = new File(TEST_DIR, "generate-passwords.xml");
	private static final File GENERATE_PASSWORDS_2_FILE = new File(TEST_DIR, "generate-passwords-2.xml");
	private static final File GENERATE_PASSWORDS_3_FILE = new File(TEST_DIR, "generate-passwords-3.xml");
	private static final File ECHO_FILE = new File(TEST_DIR, "echo.xml");

    @Autowired
    private ScriptingExpressionEvaluator scriptingExpressionEvaluator;

    @Override
	public void initSystem(Task initTask, OperationResult initResult)
			throws Exception {
		super.initSystem(initTask, initResult);
		InternalMonitor.reset();

//		InternalMonitor.setTraceShadowFetchOperation(true);
//		InternalMonitor.setTraceResourceSchemaOperations(true);
	}

    @Test
    public void test100EmptySequence() throws Exception {
    	final String TEST_NAME = "test100EmptySequence";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        ExpressionSequenceType sequence = new ExpressionSequenceType();

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(sequence, task, result);

        // THEN
        dumpOutput(output, result);
        assertNoOutputData(output);
        result.computeStatus();
        TestUtil.assertSuccess(result);
    }

    @Test
    public void test110EmptyPipeline() throws Exception {
    	final String TEST_NAME = "test110EmptyPipeline";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        ExpressionPipelineType pipeline = new ExpressionPipelineType();

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(pipeline, task, result);

        // THEN
        dumpOutput(output, result);
        assertNoOutputData(output);
        result.computeStatus();
        TestUtil.assertSuccess(result);
    }

	@Test
	public void test112Echo() throws Exception {
		final String TEST_NAME = "test112Echo";
		TestUtil.displayTestTile(this, TEST_NAME);

		// GIVEN
		Task task = taskManager.createTaskInstance(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
		ExecuteScriptType executeScript = parseRealValue(ECHO_FILE);

		// WHEN
		ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(executeScript, task, result);

		// THEN
		dumpOutput(output, result);
		result.computeStatus();
		PipelineData data = output.getFinalOutput();
		assertEquals("Unexpected # of items in output", 4, data.getData().size());

		// TODO check correct serialization (MID-
	}


	@Test
    public void test120Log() throws Exception {
    	final String TEST_NAME = "test120Log";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> logAction = parseAnyData(LOG_FILE);

        LogfileTestTailer tailer = new LogfileTestTailer(LoggingConfigurationManager.AUDIT_LOGGER_NAME);
        tailer.tail();
        tailer.setExpecteMessage("Custom message:");

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(logAction.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        assertNoOutputData(output);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        tailer.tail();
        tailer.assertExpectedMessage();
    }

    private PrismProperty parseAnyData(File file) throws IOException, SchemaException {
        return (PrismProperty) prismContext.parserFor(file).parseItem();
    }

    private <T> T parseRealValue(File file) throws IOException, SchemaException {
        return prismContext.parserFor(file).parseRealValue();
    }

    @Test
    public void test200SearchUser() throws Exception {
    	final String TEST_NAME = "test200SearchUser";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
		PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_USERS_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(2, output.getFinalOutput().getData().size());
        //assertEquals("administrator", ((PrismObject<UserType>) output.getData().get(0)).asObjectable().getName().getOrig());
    }

    @Test
    public void test205SearchForResources() throws Exception {
    	final String TEST_NAME = "test205SearchForResources";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_RESOURCES_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(13, output.getFinalOutput().getData().size());
    }

    @Test
    public void test206SearchForRoles() throws Exception {
    	final String TEST_NAME = "test206SearchForRoles";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_ROLES_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        //assertEquals(9, output.getData().size());
    }

    @Test
    public void test210SearchForShadows() throws Exception {
    	final String TEST_NAME = "test210SearchForShadows";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_SHADOWS_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(5, output.getFinalOutput().getData().size());
        assertAttributesFetched(output.getFinalOutput().getData());
    }

    @Test
    public void test215SearchForShadowsNoFetch() throws Exception {
    	final String TEST_NAME = "test215SearchForShadowsNoFetch";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_SHADOWS_NOFETCH_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(5, output.getFinalOutput().getData().size());
        assertAttributesNotFetched(output.getFinalOutput().getData());
    }

    @Test
    public void test220SearchForUsersAccounts() throws Exception {
    	final String TEST_NAME = "test220SearchForUsersAccounts";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_USERS_ACCOUNTS_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(4, output.getFinalOutput().getData().size());
        assertAttributesFetched(output.getFinalOutput().getData());
    }

    @Test
    public void test225SearchForUsersAccountsNoFetch() throws Exception {
    	final String TEST_NAME = "test225SearchForUsersAccountsNoFetch";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_USERS_ACCOUNTS_NOFETCH_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(4, output.getFinalOutput().getData().size());
        assertAttributesNotFetched(output.getFinalOutput().getData());
    }

    @Test
    public void test300DisableJack() throws Exception {
    	final String TEST_NAME = "test300DisableJack";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(DISABLE_JACK_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        assertEquals("Disabled user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertAdministrativeStatusDisabled(searchObjectByName(UserType.class, "jack"));
    }

	@Test
    public void test310EnableJack() throws Exception {
    	final String TEST_NAME = "test310EnableJack";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(ENABLE_JACK_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals("Enabled user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertAdministrativeStatusEnabled(searchObjectByName(UserType.class, "jack"));
    }

    @Test
    public void test320DeleteAndAddJack() throws Exception {
    	final String TEST_NAME = "test320DeleteAndAddJack";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(DELETE_AND_ADD_JACK_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals("Deleted user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\nAdded user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertAdministrativeStatusEnabled(searchObjectByName(UserType.class, "jack"));
    }

    @Test
    public void test330ModifyJack() throws Exception {
    	final String TEST_NAME = "test330ModifyJack";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(MODIFY_JACK_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
		assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals("Modified user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertEquals("Nowhere", searchObjectByName(UserType.class, "jack").asObjectable().getLocality().getOrig());
    }

    @Test
    public void test340ModifyJackBack() throws Exception {
    	final String TEST_NAME = "test340ModifyJackBack";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(MODIFY_JACK_BACK_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals("Modified user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertEquals("Caribbean", searchObjectByName(UserType.class, "jack").asObjectable().getLocality().getOrig());
    }

    @Test
    public void test350RecomputeJack() throws Exception {
    	final String TEST_NAME = "test350RecomputeJack";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(RECOMPUTE_JACK_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals("Recomputed user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
    }

    @Test
    public void test360AssignToJack() throws Exception {
    	final String TEST_NAME = "test360AssignToJack";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(ASSIGN_TO_JACK_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        //assertEquals("Recomputed user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        PrismObject<UserType> jack = getUser(USER_JACK_OID);
        display("jack after assignments creation", jack);
        assertAssignedAccount(jack, "10000000-0000-0000-0000-000000000104");
        assertAssignedRole(jack, "12345678-d34d-b33f-f00d-55555555cccc");
    }

    @Test
    public void test370AssignToJackInBackground() throws Exception {
    	final String TEST_NAME = "test370AssignToJackInBackground";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        OperationResult result = new OperationResult(DOT_CLASS + TEST_NAME);
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(ASSIGN_TO_JACK_2_FILE);

        // WHEN
        Task task = taskManager.createTaskInstance();
        task.setOwner(getUser(USER_ADMINISTRATOR_OID));
        scriptingExpressionEvaluator.evaluateExpressionInBackground(expression.getAnyValue().getValue(), task, result);
        waitForTaskFinish(task.getOid(), false);
        task.refresh(result);

        // THEN
        display(task.getResult());
        TestUtil.assertSuccess(task.getResult());
        PrismObject<UserType> jack = getUser(USER_JACK_OID);
        display("jack after assignment creation", jack);
        assertAssignedRole(jack, "12345678-d34d-b33f-f00d-555555556677");
    }

    @Deprecated
    @Test
    public void test380DisableJackInBackgroundSimple() throws Exception {
    	final String TEST_NAME = "test380DisableJackInBackgroundSimple";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        OperationResult result = new OperationResult(DOT_CLASS + TEST_NAME);

        // WHEN
        Task task = taskManager.createTaskInstance();
        task.setOwner(getUser(USER_ADMINISTRATOR_OID));
        scriptingExpressionEvaluator.evaluateExpressionInBackground(UserType.COMPLEX_TYPE,
                ObjectQueryUtil.createOrigNameQuery("jack", prismContext).getFilter(),
                "disable", task, result);

        waitForTaskFinish(task.getOid(), false);
        task.refresh(result);

        // THEN
        display(task.getResult());
        TestUtil.assertSuccess(task.getResult());
        PrismObject<UserType> jack = getUser(USER_JACK_OID);
        display("jack after disable script", jack);
        assertAdministrativeStatusDisabled(jack);
    }

    @Test
    public void test400PurgeSchema() throws Exception {
    	final String TEST_NAME = "test400PurgeSchema";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(PURGE_DUMMY_BLACK_SCHEMA_FILE);

//        ResourceType dummy = modelService.getObject(ResourceType.class, RESOURCE_DUMMY_BLACK_OID, null, task, result).asObjectable();
//        IntegrationTestTools.display("dummy resource before purge schema", dummy.asPrismObject());
//        IntegrationTestTools.display("elements: " + dummy.getSchema().getDefinition().getAny().get(0).getElementsByTagName("*").getLength());
//        IntegrationTestTools.display("schema as XML: " + DOMUtil.printDom(dummy.getSchema().getDefinition().getAny().get(0)));

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(1, output.getFinalOutput().getData().size());

//        dummy = repositoryService.getObject(ResourceType.class, RESOURCE_DUMMY_BLACK_OID, null, result).asObjectable();
//        IntegrationTestTools.display("dummy resource from repo", dummy.asPrismObject());
//        IntegrationTestTools.display("elements: " + dummy.getSchema().getDefinition().getAny().get(0).getElementsByTagName("*").getLength());
//        IntegrationTestTools.display("schema as XML: " + DOMUtil.printDom(dummy.getSchema().getDefinition().getAny().get(0)));

        //AssertJUnit.assertNull("Schema is still present", dummy.getSchema());
        // actually, schema gets downloaded just after purging it
        assertEquals("Purged schema information from resource:10000000-0000-0000-0000-000000000305(Dummy Resource Black)\n", output.getConsoleOutput());
    }


    @Test
    public void test410TestResource() throws Exception {
    	final String TEST_NAME = "test410TestResource";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(TEST_DUMMY_RESOURCE_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        ResourceType dummy = modelService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, taskManager.createTaskInstance(), result).asObjectable();
        display("dummy resource after test connection", dummy.asPrismObject());
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(1, output.getFinalOutput().getData().size());
        assertEquals("Tested resource:10000000-0000-0000-0000-000000000004(Dummy Resource): SUCCESS\n", output.getConsoleOutput());
    }

    @Test
    public void test420NotificationAboutJack() throws Exception {
        final String TEST_NAME = "test420NotificationAboutJack";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(NOTIFICATION_ABOUT_JACK_FILE);
        prepareNotifications();

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
		assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        assertEquals("Produced 1 event(s)\n", output.getConsoleOutput());

        display("Dummy transport", dummyTransport);
        checkDummyTransportMessages("Custom", 1);
        Message m = dummyTransport.getMessages("dummy:Custom").get(0);
        assertEquals("Wrong message body", "jack/" + USER_JACK_OID, m.getBody());
        assertEquals("Wrong message subject", "Ad hoc notification", m.getSubject());
    }

    @Test
    public void test430NotificationAboutJackType2() throws Exception {
        final String TEST_NAME = "test430NotificationAboutJackType2";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<ScriptingExpressionType> expression = parseAnyData(NOTIFICATION_ABOUT_JACK_TYPE2_FILE);
        prepareNotifications();

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
		assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        assertEquals("Produced 1 event(s)\n", output.getConsoleOutput());

        display("Dummy transport", dummyTransport);
        checkDummyTransportMessages("Custom", 1);
        Message m = dummyTransport.getMessages("dummy:Custom").get(0);
        assertEquals("Wrong message body", "1", m.getBody());
        assertEquals("Wrong message subject", "Ad hoc notification 2", m.getSubject());

        checkDummyTransportMessages("CustomType2", 1);
        m = dummyTransport.getMessages("dummy:CustomType2").get(0);
        assertEquals("Wrong message body", "POV:user:c0c010c0-d34d-b33f-f00d-111111111111(jack)", m.getBody());
        assertEquals("Wrong message subject", "Failure notification of type 2", m.getSubject());
    }

    @Test
	public void test500ScriptingUsers() throws Exception {
		final String TEST_NAME = "test500ScriptingUsers";
		TestUtil.displayTestTile(this, TEST_NAME);

		// GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
		PrismProperty<ScriptingExpressionType> expression = parseAnyData(SCRIPTING_USERS_FILE);

		// WHEN
		ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

		// THEN
        dumpOutput(output, result);
        result.computeStatus();
		TestUtil.assertSuccess(result);
		PipelineData data = output.getFinalOutput();
		assertEquals("Unexpected # of items in output", 5, data.getData().size());
		Set<String> realOids = new HashSet<>();
        for (PipelineItem item : data.getData()) {
            PrismValue value = item.getValue();
			PrismObject<UserType> user = ((PrismObjectValue<UserType>) value).asPrismObject();
			assertEquals("Description not set", "Test", user.asObjectable().getDescription());
			realOids.add(user.getOid());
			assertSuccess(item.getResult());
		}
		assertEquals("Unexpected OIDs in output",
				Sets.newHashSet(Arrays.asList(USER_ADMINISTRATOR_OID, USER_JACK_OID, USER_BARBOSSA_OID, USER_GUYBRUSH_OID, USER_ELAINE_OID)),
				realOids);
	}

    @Test
	public void test510GeneratePasswords() throws Exception {
		final String TEST_NAME = "test510GeneratePasswords";
		TestUtil.displayTestTile(this, TEST_NAME);

		// GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
		PrismProperty<ScriptingExpressionType> expression = parseAnyData(GENERATE_PASSWORDS_FILE);

		addObject(PASSWORD_POLICY_GLOBAL_FILE);

		List<ItemDelta<?, ?>> itemDeltas = DeltaBuilder.deltaFor(SecurityPolicyType.class, prismContext)
				.item(SecurityPolicyType.F_CREDENTIALS, CredentialsPolicyType.F_PASSWORD,
						PasswordCredentialsPolicyType.F_PASSWORD_POLICY_REF)
				.add(new PrismReferenceValue(PASSWORD_POLICY_GLOBAL_OID))
				.asItemDeltas();
		modifySystemObjectInRepo(SecurityPolicyType.class, SECURITY_POLICY_OID, itemDeltas, result);


		// WHEN
		ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

		// THEN
        dumpOutput(output, result);
        result.computeStatus();
		TestUtil.assertSuccess(result);
		PipelineData data = output.getFinalOutput();
		assertEquals("Unexpected # of items in output", 5, data.getData().size());
		Set<String> realOids = new HashSet<>();
        for (PipelineItem item : data.getData()) {
            PrismValue value = item.getValue();
			UserType user = ((PrismObjectValue<UserType>) value).asObjectable();
            ProtectedStringType passwordValue = user.getCredentials().getPassword().getValue();
            assertNotNull("clearValue for password not set", passwordValue.getClearValue());
            realOids.add(user.getOid());
		}
		assertEquals("Unexpected OIDs in output",
				Sets.newHashSet(Arrays.asList(USER_ADMINISTRATOR_OID, USER_JACK_OID, USER_BARBOSSA_OID, USER_GUYBRUSH_OID, USER_ELAINE_OID)),
				realOids);
	}

    @Test
	public void test520GeneratePasswordsFullInput() throws Exception {
		final String TEST_NAME = "test520GeneratePasswordsFullInput";
		TestUtil.displayTestTile(this, TEST_NAME);

		// GIVEN
		Task task = taskManager.createTaskInstance(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
		ExecuteScriptType executeScript = parseRealValue(GENERATE_PASSWORDS_2_FILE);

		// WHEN
		ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(executeScript, task, result);

		// THEN
        dumpOutput(output, result);
        result.computeStatus();
		//TestUtil.assertSuccess(result);
		PipelineData data = output.getFinalOutput();
		List<PipelineItem> items = data.getData();
		assertEquals("Unexpected # of items in output", 4, items.size());
		assertSuccess(items.get(0).getResult());
		assertFailure(items.get(1).getResult());
		assertSuccess(items.get(2).getResult());
		assertSuccess(items.get(3).getResult());
	}

    @Test
	public void test530GeneratePasswordsReally() throws Exception {
		final String TEST_NAME = "test530GeneratePasswordsReally";
		TestUtil.displayTestTile(this, TEST_NAME);

		// GIVEN
		Task task = taskManager.createTaskInstance(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
		ExecuteScriptType executeScript = parseRealValue(GENERATE_PASSWORDS_3_FILE);

		// WHEN
		ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(executeScript, task, result);

		// THEN
        dumpOutput(output, result);
        result.computeStatus();
		PipelineData data = output.getFinalOutput();
		List<PipelineItem> items = data.getData();
		assertEquals("Unexpected # of items in output", 3, items.size());
		assertFailure(items.get(0).getResult());
		assertSuccess(items.get(1).getResult());
		assertSuccess(items.get(2).getResult());

		checkPassword(items.get(1), USER_GUYBRUSH_OID);
		checkPassword(items.get(2), USER_ELAINE_OID);
	}

	@SuppressWarnings("unchecked")
	private void checkPassword(PipelineItem item, String userOid)
			throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException,
			ConfigurationException, ExpressionEvaluationException, EncryptionException {
		PrismProperty<ProtectedStringType> returnedPassword = (PrismProperty<ProtectedStringType>)
				item.getValue().find(SchemaConstants.PATH_PASSWORD_VALUE);
		ProtectedStringType returnedRealValue = returnedPassword.getRealValue();
		PrismObject<UserType> user = getUser(userOid);
		ProtectedStringType repoRealValue = user.asObjectable().getCredentials().getPassword().getValue();
		String returnedClearValue = protector.decryptString(returnedRealValue);
		String repoClearValue = protector.decryptString(repoRealValue);
		System.out.println("Returned password = " + returnedClearValue + ", repo password = " + repoClearValue);
		assertEquals("Wrong password stored in repository", returnedClearValue, repoClearValue);
	}

	@Test
    public void test540SearchUserResolveNamesForRoleMembershipRef() throws Exception {
        final String TEST_NAME = "test540SearchUserResolveNamesForRoleMembershipRef";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
		OperationResult result = task.getResult();
        PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_USERS_RESOLVE_NAMES_FOR_ROLE_MEMBERSHIP_REF_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(2, output.getFinalOutput().getData().size());
        //assertEquals("administrator", ((PrismObject<UserType>) output.getData().get(0)).asObjectable().getName().getOrig());

		for (PipelineItem item : output.getFinalOutput().getData()) {
			PrismAsserts.assertHasTargetName((PrismContainerValue) item.getValue(), new ItemPath(UserType.F_ROLE_MEMBERSHIP_REF));
			PrismAsserts.assertHasNoTargetName((PrismContainerValue) item.getValue(), new ItemPath(UserType.F_LINK_REF));
		}
	}

    @Test
    public void test545SearchUserResolveRoleMembershipRef() throws Exception {
        final String TEST_NAME = "test545SearchUserResolveRoleMembershipRef";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
		Task task = createTask(DOT_CLASS + TEST_NAME);
        OperationResult result = task.getResult();
        PrismProperty<SearchExpressionType> expression = parseAnyData(SEARCH_FOR_USERS_RESOLVE_ROLE_MEMBERSHIP_REF_FILE);

        // WHEN
        ExecutionContext output = scriptingExpressionEvaluator.evaluateExpression(expression.getAnyValue().getValue(), task, result);

        // THEN
        dumpOutput(output, result);
        result.computeStatus();
        TestUtil.assertSuccess(result);
        assertEquals(2, output.getFinalOutput().getData().size());
        //assertEquals("administrator", ((PrismObject<UserType>) output.getData().get(0)).asObjectable().getName().getOrig());

		for (PipelineItem item : output.getFinalOutput().getData()) {
			PrismAsserts.assertHasObject((PrismContainerValue) item.getValue(), new ItemPath(UserType.F_ROLE_MEMBERSHIP_REF));
			PrismAsserts.assertHasNoObject((PrismContainerValue) item.getValue(), new ItemPath(UserType.F_LINK_REF));
		}
    }

    private void assertNoOutputData(ExecutionContext output) {
        assertTrue("Script returned unexpected data", output.getFinalOutput() == null || output.getFinalOutput().getData().isEmpty());
    }

	private void assertOutputData(ExecutionContext output, int size, OperationResultStatus status) {
		assertEquals("Wrong # of output items", size, output.getFinalOutput().getData().size());
		for (PipelineItem item : output.getFinalOutput().getData()) {
			assertEquals("Wrong op result status", status, item.getResult().getStatus());
		}
	}

	// the following tests are a bit crude but for now it should be OK

    private void assertAttributesNotFetched(List<PipelineItem> data) {
        for (PipelineItem item : data) {
            PrismValue value = item.getValue();
            if (((PrismObjectValue<ShadowType>) value).asObjectable().getAttributes().getAny().size() > 2) {
                throw new AssertionError("There are some unexpected attributes present in " + value.debugDump());
            }
        }
    }

    private void assertAttributesFetched(List<PipelineItem> data) {
        for (PipelineItem item : data) {
            PrismValue value = item.getValue();
            if (((PrismObjectValue<ShadowType>) value).asObjectable().getAttributes().getAny().size() <= 2) {
                throw new AssertionError("There are no attributes present in " + value.debugDump());
            }
        }
    }

    private void dumpOutput(ExecutionContext output, OperationResult result) throws JAXBException, SchemaException {
        display("output", output.getFinalOutput());
        display("stdout", output.getConsoleOutput());
        display(result);
        if (output.getFinalOutput() != null) {
            PipelineDataType bean = ModelWebService.prepareXmlData(output.getFinalOutput().getData(), null);
            display("output in XML", prismContext.xmlSerializer().root(new QName("output")).serializeRealValue(bean));
        }
    }

}
