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

import static com.evolveum.midpoint.test.IntegrationTestTools.assertSuccess;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.testng.AssertJUnit;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.icf.dummy.resource.DummyResource;
import com.evolveum.midpoint.model.test.DummyResourceContoller;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.SystemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserTemplateType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

/**
 * @author semancik
 *
 */
public class AbstractInitializedModelIntegrationTest extends AbstractConfiguredModelIntegrationTest {
	
	private static final Object NUM_FUNCTIONAL_ORGS = 6;
	private static final Object NUM_PROJECT_ORGS = 3;
	
	protected static final Trace LOGGER = TraceManager.getTrace(AbstractInitializedModelIntegrationTest.class);
	
	protected UserType userTypeJack;
	protected UserType userTypeBarbossa;
	protected UserType userTypeGuybrush;
	protected UserType userTypeElaine;
	
	protected ResourceType resourceOpenDjType;
	protected PrismObject<ResourceType> resourceOpenDj;
	
	protected static DummyResource dummyResource;
	protected static DummyResourceContoller dummyResourceCtl;
	protected ResourceType resourceDummyType;
	protected PrismObject<ResourceType> resourceDummy;
	
	protected static DummyResource dummyResourceRed;
	protected static DummyResourceContoller dummyResourceCtlRed;
	protected ResourceType resourceDummyRedType;
	protected PrismObject<ResourceType> resourceDummyRed;
	
	protected static DummyResource dummyResourceBlue;
	protected static DummyResourceContoller dummyResourceCtlBlue;
	protected ResourceType resourceDummyBlueType;
	protected PrismObject<ResourceType> resourceDummyBlue;
	
	protected static DummyResource dummyResourceWhite;
	protected static DummyResourceContoller dummyResourceCtlWhite;
	protected ResourceType resourceDummyWhiteType;
	protected PrismObject<ResourceType> resourceDummyWhite;
	
	protected static DummyResource dummyResourceGreen;
	protected static DummyResourceContoller dummyResourceCtlGreen;
	protected ResourceType resourceDummyGreenType;
	protected PrismObject<ResourceType> resourceDummyGreen;
	
	protected static DummyResource dummyResourceYellow;
	protected static DummyResourceContoller dummyResourceCtlYellow;
	protected ResourceType resourceDummyYellowType;
	protected PrismObject<ResourceType> resourceDummyYellow;
	
	protected ResourceType resourceDummySchemalessType;
	protected PrismObject<ResourceType> resourceDummySchemaless;
	
	public AbstractInitializedModelIntegrationTest() {
		super();
	}

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		LOGGER.trace("initSystem");
		super.initSystem(initTask, initResult);
		
		// Connectors
		addObjectFromFile(CONNECTOR_LDAP_FILENAME, ConnectorType.class, initResult);
		addObjectFromFile(CONNECTOR_DBTABLE_FILENAME, ConnectorType.class, initResult);
		addObjectFromFile(CONNECTOR_DUMMY_FILENAME, ConnectorType.class, initResult);
		
		// Resources
		resourceOpenDj = importAndGetObjectFromFile(ResourceType.class, RESOURCE_OPENDJ_FILENAME, RESOURCE_OPENDJ_OID, initTask, initResult);
		resourceOpenDjType = resourceOpenDj.asObjectable();
		openDJController.setResource(resourceOpenDj);
				
		dummyResourceCtl = DummyResourceContoller.create(null);
		dummyResourceCtl.extendDummySchema();
		dummyResource = dummyResourceCtl.getDummyResource();
		resourceDummy = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_FILENAME, RESOURCE_DUMMY_OID, initTask, initResult);
		resourceDummyType = resourceDummy.asObjectable();
		dummyResourceCtl.setResource(resourceDummy);
		
		dummyResourceCtlRed = DummyResourceContoller.create(RESOURCE_DUMMY_RED_NAME, resourceDummyRed);
		dummyResourceCtlRed.extendDummySchema();
		dummyResourceRed = dummyResourceCtlRed.getDummyResource();
		resourceDummyRed = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_RED_FILENAME, RESOURCE_DUMMY_RED_OID, initTask, initResult); 
		resourceDummyRedType = resourceDummyRed.asObjectable();
		dummyResourceCtlRed.setResource(resourceDummyRed);
		
		dummyResourceCtlBlue = DummyResourceContoller.create(RESOURCE_DUMMY_BLUE_NAME, resourceDummyBlue);
		dummyResourceCtlBlue.extendDummySchema();
		dummyResourceBlue = dummyResourceCtlBlue.getDummyResource();
		resourceDummyBlue = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_BLUE_FILENAME, RESOURCE_DUMMY_BLUE_OID, initTask, initResult); 
		resourceDummyBlueType = resourceDummyBlue.asObjectable();
		dummyResourceCtlBlue.setResource(resourceDummyBlue);		
		
		dummyResourceCtlWhite = DummyResourceContoller.create(RESOURCE_DUMMY_WHITE_NAME, resourceDummyWhite);
		dummyResourceCtlWhite.extendDummySchema();
		dummyResourceWhite = dummyResourceCtlWhite.getDummyResource();
		resourceDummyWhite = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_WHITE_FILENAME, RESOURCE_DUMMY_WHITE_OID, initTask, initResult);
		resourceDummyWhiteType = resourceDummyWhite.asObjectable();
		dummyResourceCtlWhite.setResource(resourceDummyWhite);

		dummyResourceCtlGreen = DummyResourceContoller.create(RESOURCE_DUMMY_GREEN_NAME, resourceDummyGreen);
		dummyResourceCtlGreen.extendDummySchema();
		dummyResourceGreen = dummyResourceCtlGreen.getDummyResource();
		resourceDummyGreen = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_GREEN_FILENAME, RESOURCE_DUMMY_GREEN_OID, initTask, initResult);
		resourceDummyGreenType = resourceDummyGreen.asObjectable();
		dummyResourceCtlGreen.setResource(resourceDummyGreen);
		
		dummyResourceCtlYellow = DummyResourceContoller.create(RESOURCE_DUMMY_YELLOW_NAME, resourceDummyYellow);
		dummyResourceCtlYellow.extendDummySchema();
		dummyResourceYellow = dummyResourceCtlYellow.getDummyResource();
		resourceDummyYellow = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_YELLOW_FILENAME, RESOURCE_DUMMY_YELLOW_OID, initTask, initResult);
		resourceDummyYellowType = resourceDummyYellow.asObjectable();
		dummyResourceCtlYellow.setResource(resourceDummyYellow);
		
		resourceDummySchemaless = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_SCHEMALESS_FILENAME, RESOURCE_DUMMY_SCHEMALESS_OID, initTask, initResult); 
		resourceDummySchemalessType = resourceDummySchemaless.asObjectable();

		
		postInitDummyResouce();
		
		dummyResourceCtl.addAccount(ACCOUNT_HERMAN_DUMMY_USERNAME, "Herman Toothrot", "Monkey Island");
		dummyResourceCtl.addAccount(ACCOUNT_GUYBRUSH_DUMMY_USERNAME, "Guybrush Threepwood", "Melee Island");
		dummyResourceCtl.addAccount(ACCOUNT_DAVIEJONES_DUMMY_USERNAME, "Davie Jones", "Davie Jones' Locker");
		dummyResourceCtl.addAccount(ACCOUNT_CALYPSO_DUMMY_USERNAME, "Tia Dalma", "Pantano River");
		
		dummyResourceCtl.addAccount(ACCOUNT_ELAINE_DUMMY_USERNAME, "Elaine Marley", "Melee Island");
		dummyResourceCtlRed.addAccount(ACCOUNT_ELAINE_DUMMY_USERNAME, "Elaine Marley", "Melee Island");
		dummyResourceCtlBlue.addAccount(ACCOUNT_ELAINE_DUMMY_USERNAME, "Elaine Marley", "Melee Island");
		
		// User Templates
		addObjectFromFile(USER_TEMPLATE_FILENAME, UserTemplateType.class, initResult);
		addObjectFromFile(USER_TEMPLATE_COMPLEX_FILENAME, UserTemplateType.class, initResult);

		// Accounts
		addObjectFromFile(ACCOUNT_HBARBOSSA_OPENDJ_FILENAME, AccountShadowType.class, initResult);
		addObjectFromFile(ACCOUNT_SHADOW_GUYBRUSH_DUMMY_FILENAME, AccountShadowType.class, initResult);
		addObjectFromFile(ACCOUNT_SHADOW_ELAINE_DUMMY_FILENAME, AccountShadowType.class, initResult);
		addObjectFromFile(ACCOUNT_SHADOW_ELAINE_DUMMY_RED_FILENAME, AccountShadowType.class, initResult);
		addObjectFromFile(ACCOUNT_SHADOW_ELAINE_DUMMY_BLUE_FILENAME, AccountShadowType.class, initResult);
		
		// Users
		userTypeJack = addObjectFromFile(USER_JACK_FILENAME, UserType.class, initResult).asObjectable();
		userTypeBarbossa = addObjectFromFile(USER_BARBOSSA_FILENAME, UserType.class, initResult).asObjectable();
		userTypeGuybrush = addObjectFromFile(USER_GUYBRUSH_FILENAME, UserType.class, initResult).asObjectable();
		userTypeElaine = addObjectFromFile(USER_ELAINE_FILENAME, UserType.class, initResult).asObjectable();
		
		// Roles
		addObjectFromFile(ROLE_PIRATE_FILENAME, RoleType.class, initResult);
		addObjectFromFile(ROLE_JUDGE_FILENAME, RoleType.class, initResult);
		addObjectFromFile(ROLE_DUMMIES_FILENAME, RoleType.class, initResult);
		
		// Orgstruct
		addObjectsFromFile(ORG_MONKEY_ISLAND_FILENAME, OrgType.class, initResult);
		
	}
	
	protected void postInitDummyResouce() {
		// Do nothing be default. Concrete tests may override this.
	}

	
		
	protected void assertUserJack(PrismObject<UserType> user) {
		assertUserJack(user, "Jack Sparrow", "Jack", "Sparrow");
	}
	
	protected void assertUserJack(PrismObject<UserType> user, String fullName) {
		assertUserJack(user, fullName, "Jack", "Sparrow");
	}
	
	protected void assertUserJack(PrismObject<UserType> user, String fullName, String givenName, String familyName) {
		assertUserJack(user, fullName, givenName, familyName, "Caribbean");
	}
	
	protected void assertUserJack(PrismObject<UserType> user, String fullName, String givenName, String familyName, String locality) {
		assertUser(user, USER_JACK_OID, "jack", fullName, givenName, familyName);
		UserType userType = user.asObjectable();
		PrismAsserts.assertEqualsPolyString("Wrong jack honorificPrefix", "Cpt.", userType.getHonorificPrefix());
		PrismAsserts.assertEqualsPolyString("Wrong jack honorificSuffix", "PhD.", userType.getHonorificSuffix());
		assertEquals("Wrong jack emailAddress", "jack.sparrow@evolveum.com", userType.getEmailAddress());
		assertEquals("Wrong jack telephoneNumber", "555-1234", userType.getTelephoneNumber());
		assertEquals("Wrong jack employeeNumber", "emp1234", userType.getEmployeeNumber());
		assertEquals("Wrong jack employeeType", "CAPTAIN", userType.getEmployeeType().get(0));
		if (locality == null) {
			assertNull("Locality sneaked to user jack", userType.getLocality());
		} else {
			PrismAsserts.assertEqualsPolyString("Wrong jack locality", locality, userType.getLocality());
		}
	}
	
	protected void assertDummyShadowRepo(PrismObject<AccountShadowType> accountShadow, String oid, String username) {
		assertShadowRepo(accountShadow, oid, username, resourceDummyType);
	}
	
	protected void assertDummyShadowModel(PrismObject<AccountShadowType> accountShadow, String oid, String username, String fullname) {
		assertShadowModel(accountShadow, oid, username, resourceDummyType);
		IntegrationTestTools.assertAttribute(accountShadow, dummyResourceCtl.getAttributeFullnameQName(), fullname);
	}

	protected DummyAccount getDummyAccount(String dummyInstanceName, String username) {
		DummyResource dummyResource = DummyResource.getInstance(dummyInstanceName);
		return dummyResource.getAccountByUsername(username);
	}
	
	protected void assertDummyAccount(String username, String fullname, boolean active) {
		assertDummyAccount(null, username, fullname, active);
	}
	
	protected void assertDummyAccount(String dummyInstanceName, String username, String fullname, boolean active) {
		DummyAccount account = getDummyAccount(dummyInstanceName, username);
		assertNotNull("No dummy("+dummyInstanceName+") account for username "+username, account);
		assertEquals("Wrong fullname for dummy("+dummyInstanceName+") account "+username, fullname, account.getAttributeValue("fullname"));
		assertEquals("Wrong activation for dummy("+dummyInstanceName+") account "+username, active, account.isEnabled());
	}

	protected void assertNoDummyAccount(String username) {
		assertNoDummyAccount(null, username);
	}
	
	protected void assertNoDummyAccount(String dummyInstanceName, String username) {
		DummyAccount account = getDummyAccount(dummyInstanceName, username);
		assertNull("Dummy account for username "+username+" exists while not expecting it ("+dummyInstanceName+")", account);
	}
	
	protected void assertDefaultDummyAccountAttribute(String username, String attributeName, Object... expectedAttributeValues) {
		assertDummyAccountAttribute(null, username, attributeName, expectedAttributeValues);
	}
	
	protected void assertDummyAccountAttribute(String dummyInstanceName, String username, String attributeName, Object... expectedAttributeValues) {
		DummyAccount account = getDummyAccount(dummyInstanceName, username);
		assertNotNull("No dummy account for username "+username, account);
		Set<Object> values = account.getAttributeValues(attributeName, Object.class);
		assertNotNull("No values for attribute "+attributeName+" of dummy account "+username, values);
		assertEquals("Unexpected number of values for attribute "+attributeName+" of dummy account "+username+": "+values, expectedAttributeValues.length, values.size());
		for (Object expectedValue: expectedAttributeValues) {
			if (!values.contains(expectedValue)) {
				AssertJUnit.fail("Value '"+expectedValue+"' expected in attribute "+attributeName+" of dummy account "+username+
						" but not found. Values found: "+values);
			}
		}
	}
		
	protected void setDefaultUserTemplate(String userTemplateOid)
			throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {

		PrismObjectDefinition<SystemConfigurationType> objectDefinition = prismContext.getSchemaRegistry()
				.findObjectDefinitionByCompileTimeClass(SystemConfigurationType.class);

		PrismReferenceValue userTemplateRefVal = new PrismReferenceValue(userTemplateOid);
		
		Collection<? extends ItemDelta> modifications = ReferenceDelta.createModificationReplaceCollection(
						SystemConfigurationType.F_DEFAULT_USER_TEMPLATE_REF,
						objectDefinition, userTemplateRefVal);

		OperationResult result = new OperationResult("Aplying default user template");

		repositoryService.modifyObject(SystemConfigurationType.class,
				SystemObjectsType.SYSTEM_CONFIGURATION.value(), modifications, result);
		display("Aplying default user template result", result);
		result.computeStatus();
		assertSuccess("Aplying default user template failed (result)", result);
	}

	protected void assertMonkeyIslandOrgSanity() throws ObjectNotFoundException, SchemaException, SecurityViolationException, CommunicationException, ConfigurationException {
		Task task = taskManager.createTaskInstance(AbstractInitializedModelIntegrationTest.class.getName() + ".assertMonkeyIslandOrgSanity");
        OperationResult result = task.getResult();
        
        PrismObject<OrgType> orgGovernorOffice = modelService.getObject(OrgType.class, ORG_GOVERNOR_OFFICE_OID, null, task, result);
        result.computeStatus();
        assertSuccess(result);
        OrgType orgGovernorOfficeType = orgGovernorOffice.asObjectable();
        assertEquals("Wrong governor office name", PrismTestUtil.createPolyStringType("F0001"), orgGovernorOfficeType.getName());
        
        List<PrismObject<OrgType>> governorSubOrgs = searchOrg(ORG_GOVERNOR_OFFICE_OID, null, 1, task, result);
        if (verbose) display("governor suborgs", governorSubOrgs);
        assertEquals("Unexpected number of governor suborgs", 3, governorSubOrgs.size());
        
        List<PrismObject<OrgType>> functionalOrgs = searchOrg(ORG_GOVERNOR_OFFICE_OID, null, null, task, result);
        if (verbose) display("functional orgs (null)", functionalOrgs);
        assertEquals("Unexpected number of functional orgs (null)", NUM_FUNCTIONAL_ORGS, functionalOrgs.size());
        
        functionalOrgs = searchOrg(ORG_GOVERNOR_OFFICE_OID, null, -1, task, result);
        if (verbose) display("functional orgs (-1)", functionalOrgs);
        assertEquals("Unexpected number of functional orgs (-1)", NUM_FUNCTIONAL_ORGS, functionalOrgs.size());
        
        List<PrismObject<OrgType>> prootSubOrgs = searchOrg(ORG_PROJECT_ROOT_OID, null, 1, task, result);
        if (verbose) display("project root suborgs", prootSubOrgs);
        assertEquals("Unexpected number of governor suborgs", 2, prootSubOrgs.size());
        
        List<PrismObject<OrgType>> projectOrgs = searchOrg(ORG_PROJECT_ROOT_OID, null, null, task, result);
        if (verbose) display("project orgs (null)", projectOrgs);
        assertEquals("Unexpected number of functional orgs (null)", NUM_PROJECT_ORGS, projectOrgs.size());
        
        projectOrgs = searchOrg(ORG_PROJECT_ROOT_OID, null, -1, task, result);
        if (verbose) display("project orgs (-1)", projectOrgs);
        assertEquals("Unexpected number of functional orgs (-1)", NUM_PROJECT_ORGS, projectOrgs.size());
        
        PrismObject<OrgType> orgScummBar = modelService.getObject(OrgType.class, ORG_SCUMM_BAR_OID, null, task, result);
        List<AssignmentType> scummBarAssignments = orgScummBar.asObjectable().getAssignment();
        assertEquals("Unexpected number of scumm bar assignments: "+scummBarAssignments,  1, scummBarAssignments.size());
	}
     	
}
