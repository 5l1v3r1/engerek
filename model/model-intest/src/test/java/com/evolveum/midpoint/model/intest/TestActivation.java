/**
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
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.model.intest;

import static org.testng.AssertJUnit.assertNotNull;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayTestTile;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.midpoint.common.crypto.EncryptionException;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.common.refinery.ShadowDiscriminatorObjectDelta;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.ObjectOperationOption;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.holder.XPathHolder;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.SchemaTestConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ConsistencyViolationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountSynchronizationSettingsType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AssignmentPolicyEnforcementType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ConnectorConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.CredentialsType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.PasswordType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ProtectedStringType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ValuePolicyType;

/**
 * @author semancik
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestActivation extends AbstractInitializedModelIntegrationTest {
			
	private String accountOid;
	private String accountRedOid;

	public TestActivation() throws JAXBException {
		super();
	}
			
	@Test
    public void test050CheckJackEnabled() throws Exception {
        displayTestTile(this, "test050CheckJackEnabled");

        // GIVEN, WHEN
        // this happens during test initialization when user-jack.xml is added
        
        // THEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test050CheckJackEnabled");
        OperationResult result = task.getResult();
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");
        
		assertEnabled(userJack);
	}
	
	@Test
    public void test051ModifyUserJackDisable() throws Exception {
        displayTestTile(this, "test051ModifyUserJackDisable");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test051ModifyUserJackDisable");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
		// WHEN
        modifyUserReplace(USER_JACK_OID, ACTIVATION_ENABLED_PATH, task, result, false);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");
        
		assertDisabled(userJack);
	}
	
	@Test
    public void test052ModifyUserJackEnable() throws Exception {
        displayTestTile(this, "test052ModifyUserJackEnable");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test052ModifyUserJackEnable");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
		// WHEN
        modifyUserReplace(USER_JACK_OID, ACTIVATION_ENABLED_PATH, task, result, true);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");
        
		assertEnabled(userJack);
	}
	
	@Test
    public void test100ModifyUserJackAssignAccount() throws Exception {
        displayTestTile(this, "test100ModifyUserJackAssignAccount");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test100ModifyUserJackAssignAccount");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
        Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<ObjectDelta<? extends ObjectType>>();
        ObjectDelta<UserType> accountAssignmentUserDelta = createAccountAssignmentUserDelta(USER_JACK_OID, RESOURCE_DUMMY_OID, null, true);
        deltas.add(accountAssignmentUserDelta);
                
		// WHEN
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
		PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack);
        accountOid = getSingleUserAccountRef(userJack);
        
		// Check shadow
        PrismObject<AccountShadowType> accountShadow = repositoryService.getObject(AccountShadowType.class, accountOid, result);
        assertDummyShadowRepo(accountShadow, accountOid, "jack");
        
        // Check account
        PrismObject<AccountShadowType> accountModel = modelService.getObject(AccountShadowType.class, accountOid, null, task, result);
        assertDummyShadowModel(accountModel, accountOid, "jack", "Jack Sparrow");
        
        // Check account in dummy resource
        assertDummyAccount("jack", "Jack Sparrow", true);
        
        assertDummyEnabled("jack");
	}
	
	@Test
    public void test101ModifyUserJackDisable() throws Exception {
        displayTestTile(this, "test051ModifyUserJackDisable");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test051ModifyUserJackDisable");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
		// WHEN
        modifyUserReplace(USER_JACK_OID, ACTIVATION_ENABLED_PATH, task, result, false);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");
        
		assertDisabled(userJack);
		assertDummyDisabled("jack");
	}
	
	@Test
    public void test102ModifyUserJackEnable() throws Exception {
        displayTestTile(this, "test052ModifyUserJackEnable");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test052ModifyUserJackEnable");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
		// WHEN
        modifyUserReplace(USER_JACK_OID, ACTIVATION_ENABLED_PATH, task, result, true);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");
        
		assertEnabled(userJack);
		assertDummyEnabled("jack");
	}
	

	/**
	 * Modify account activation. User's activation should be unchanged
	 */
	@Test
    public void test111ModifyAccountJackDisable() throws Exception {
        displayTestTile(this, "test111ModifyAccountJackDisable");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test111ModifyAccountJackDisable");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
		// WHEN
        modifyAccountShadowReplace(accountOid, ACTIVATION_ENABLED_PATH, task, result, false);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");
        
		assertEnabled(userJack);
		assertDummyDisabled("jack");
	}
	
	/**
	 * Re-enabling the user should enable the account sa well. Even if the user is already enabled.
	 */
	@Test
    public void test112ModifyUserJackEnable() throws Exception {
        displayTestTile(this, "test112ModifyUserJackEnable");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test112ModifyUserJackEnable");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
		// WHEN
        modifyUserReplace(USER_JACK_OID, ACTIVATION_ENABLED_PATH, task, result, true);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");
        
		assertEnabled(userJack);
		assertDummyEnabled("jack");
	}
	
	/**
	 * Modify both user and account activation. As password outbound mapping is weak the user should have its own state
	 * and account should have its own state.
	 */
	@Test
    public void test113ModifyJackActivationUserAndAccount() throws Exception {
        displayTestTile(this, "test113ModifyJackActivationUserAndAccount");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test113ModifyJackActivationUserAndAccount");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
        ObjectDelta<UserType> userDelta = createModifyUserReplaceDelta(USER_JACK_OID, ACTIVATION_ENABLED_PATH, true);
        ObjectDelta<AccountShadowType> accountDelta = createModifyAccountShadowReplaceDelta(accountOid, resourceDummy, 
        		ACTIVATION_ENABLED_PATH, false);        
		
        Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(userDelta, accountDelta);
                        
		// WHEN
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");
        
		assertEnabled(userJack);
		assertDummyDisabled("jack");
	}
	
	/**
	 * Add red dummy resource to the mix. This would be fun.
	 */
	@Test
    public void test120ModifyUserJackAssignAccountDummyRed() throws Exception {
        displayTestTile(this, "test120ModifyUserJackAssignAccountDummyRed");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test120ModifyUserJackAssignAccountDummyRed");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
        Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<ObjectDelta<? extends ObjectType>>();
        ObjectDelta<UserType> accountAssignmentUserDelta = createAccountAssignmentUserDelta(USER_JACK_OID, RESOURCE_DUMMY_RED_OID, null, true);
        deltas.add(accountAssignmentUserDelta);
                
		// WHEN
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
		PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack);
		assertAccounts(USER_JACK_OID, 2);
        accountRedOid = getUserAccountRef(userJack, RESOURCE_DUMMY_RED_OID);
                
        // Check account in dummy resource
        assertDummyAccount(RESOURCE_DUMMY_RED_NAME, "jack", "Jack Sparrow", true);
        
        assertEnabled(userJack);
		assertDummyDisabled("jack");
		assertDummyEnabled(RESOURCE_DUMMY_RED_NAME, "jack");
	}
	
	/**
	 * Modify both user and account activation. Red dummy has a strong mapping. User change should override account
	 * change.
	 */
	@Test
    public void test121ModifyJackUserAndAccountRed() throws Exception {
        displayTestTile(this, "test121ModifyJackUserAndAccountRed");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test121ModifyJackPasswordUserAndAccountRed");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        

        ObjectDelta<UserType> userDelta = createModifyUserReplaceDelta(USER_JACK_OID, ACTIVATION_ENABLED_PATH, false);
        
        ObjectDelta<AccountShadowType> accountDelta = createModifyAccountShadowReplaceDelta(accountRedOid, resourceDummy, 
        		ACTIVATION_ENABLED_PATH, true);        
		
        Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(userDelta, accountDelta);
                        
		// WHEN
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");

        assertDisabled(userJack);
		assertDummyDisabled("jack");
		assertDummyDisabled(RESOURCE_DUMMY_RED_NAME, "jack");
	}
	
	@Test
    public void test130ModifyAccountDefaultAndRed() throws Exception {
        displayTestTile(this, "test130ModifyAccountDefaultAndRed");

        // GIVEN
        Task task = taskManager.createTaskInstance(TestActivation.class.getName() + ".test121ModifyJackPasswordUserAndAccountRed");
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        
        ObjectDelta<AccountShadowType> accountDeltaDefault = createModifyAccountShadowReplaceDelta(accountOid, 
        		resourceDummy, ACTIVATION_ENABLED_PATH, true);
        ObjectDelta<AccountShadowType> accountDeltaRed = createModifyAccountShadowReplaceDelta(accountRedOid, 
        		resourceDummyRed, ACTIVATION_ENABLED_PATH, true);
		Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(accountDeltaDefault, accountDeltaRed);
		
		// WHEN
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
        PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("User after change execution", userJack);
		assertUserJack(userJack, "Jack Sparrow");

        assertDisabled(userJack);
		assertDummyEnabled("jack");
		assertDummyEnabled(RESOURCE_DUMMY_RED_NAME, "jack");
	}
	
	private void assertDummyActivationEnabledState(String userId, boolean expectedEnabled) {
		assertDummyActivationEnabledState(null, userId, expectedEnabled);
	}
	
	private void assertDummyActivationEnabledState(String instance, String userId, boolean expectedEnabled) {
		DummyAccount account = getDummyAccount(instance, userId);
		assertNotNull("No dummy account "+userId, account);
		assertEquals("Wrong enabled flag in dummy '"+instance+"' account "+userId, expectedEnabled, account.isEnabled());
	}
	
	private void assertDummyEnabled(String userId) {
		assertDummyActivationEnabledState(userId, true);
	}
	
	private void assertDummyDisabled(String userId) {
		assertDummyActivationEnabledState(userId, false);
	}
	
	private void assertDummyEnabled(String instance, String userId) {
		assertDummyActivationEnabledState(instance, userId, true);
	}
	
	private void assertDummyDisabled(String instance, String userId) {
		assertDummyActivationEnabledState(instance, userId, false);
	}
	
	private void assertDisabled(PrismObject<UserType> user) {
		PrismProperty<Boolean> enabledProperty = user.findProperty(ACTIVATION_ENABLED_PATH);
		assert enabledProperty != null : "No enabled property in "+user;
		Boolean enabled = enabledProperty.getRealValue();
		assert enabled != null : "No enabled property is null in "+user;
		assert !enabled : "Enabled property is true in "+user;
	}
	
}
