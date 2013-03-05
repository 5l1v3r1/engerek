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
package com.evolveum.midpoint.provisioning;

import static com.evolveum.midpoint.test.IntegrationTestTools.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.EqualsFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.provisioning.test.impl.TestDummy;
import com.evolveum.midpoint.provisioning.ucf.impl.ConnectorFactoryIcfImpl;
import com.evolveum.midpoint.schema.constants.ConnectorTestOperation;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ConnectorTypeUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceObjectShadowUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.XmlSchemaType;

/**
 * @author semancik
 *
 */
public class ProvisioningTestUtil {
	
	public static final String COMMON_TEST_DIR_FILENAME = "src/test/resources/object/";

	public static void assertConnectorSchemaSanity(ConnectorType conn, PrismContext prismContext) throws SchemaException {
		XmlSchemaType xmlSchemaType = conn.getSchema();
		assertNotNull("xmlSchemaType is null",xmlSchemaType);
		Element connectorXsdSchemaElement = ConnectorTypeUtil.getConnectorXsdSchema(conn);
		assertNotNull("No schema", connectorXsdSchemaElement);
		Element xsdElement = ObjectTypeUtil.findXsdElement(xmlSchemaType);
		assertNotNull("No xsd:schema element in xmlSchemaType",xsdElement);
		display("XSD schema of "+conn, DOMUtil.serializeDOMToString(xsdElement));
		// Try to parse the schema
		PrismSchema schema = null;
		try {
			schema = PrismSchema.parse(xsdElement, "schema of "+conn, prismContext);
		} catch (SchemaException e) {
			throw new SchemaException("Error parsing schema of "+conn+": "+e.getMessage(),e);
		}
		assertConnectorSchemaSanity(schema, conn.toString());
	}
	
	public static void assertConnectorSchemaSanity(PrismSchema schema, String connectorDescription) {
		assertNotNull("Cannot parse connector schema of "+connectorDescription,schema);
		assertFalse("Empty connector schema in "+connectorDescription,schema.isEmpty());
		display("Parsed connector schema of "+connectorDescription,schema);
		
		// Local schema namespace is used here.
		PrismContainerDefinition configurationDefinition = 
			schema.findItemDefinition(ResourceType.F_CONNECTOR_CONFIGURATION.getLocalPart(), PrismContainerDefinition.class);
		assertNotNull("Definition of <configuration> property container not found in connector schema of "+connectorDescription,
				configurationDefinition);
		assertFalse("Empty definition of <configuration> property container in connector schema of "+connectorDescription,
				configurationDefinition.isEmpty());
		
		// ICFC schema is used on other elements
		PrismContainerDefinition configurationPropertiesDefinition = 
			configurationDefinition.findContainerDefinition(ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_ELEMENT_QNAME);
		assertNotNull("Definition of <configurationProperties> property container not found in connector schema of "+connectorDescription,
				configurationPropertiesDefinition);
		assertFalse("Empty definition of <configurationProperties> property container in connector schema of "+connectorDescription,
				configurationPropertiesDefinition.isEmpty());
		assertFalse("No definitions in <configurationProperties> in "+connectorDescription, configurationPropertiesDefinition.getDefinitions().isEmpty());

		// TODO: other elements
	}
	
	public static void assertIcfResourceSchemaSanity(ResourceSchema resourceSchema, ResourceType resourceType) {
		QName objectClassQname = new QName(ResourceTypeUtil.getResourceNamespace(resourceType), "AccountObjectClass");
		ObjectClassComplexTypeDefinition accountDefinition = resourceSchema.findObjectClassDefinition(objectClassQname);
		assertNotNull("No object class definition for "+objectClassQname+" in resource schema", accountDefinition);
		ObjectClassComplexTypeDefinition accountDef = resourceSchema.findDefaultAccountDefinition();
		assertTrue("Mismatched account definition: "+accountDefinition+" <-> "+accountDef, accountDefinition == accountDef);
		
		assertNotNull("No object class definition " + objectClassQname, accountDefinition);
		assertTrue("Object class " + objectClassQname + " is not account", accountDefinition.isAccountType());
		assertTrue("Object class " + objectClassQname + " is not default account", accountDefinition.isDefaultAccountType());
		assertFalse("Object class " + objectClassQname + " is empty", accountDefinition.isEmpty());
		assertFalse("Object class " + objectClassQname + " is empty", accountDefinition.isIgnored());
		
		Collection<ResourceAttributeDefinition> identifiers = accountDefinition.getIdentifiers();
		assertNotNull("Null identifiers for " + objectClassQname, identifiers);
		assertFalse("Empty identifiers for " + objectClassQname, identifiers.isEmpty());

		ResourceAttributeDefinition icfAttributeDefinition = accountDefinition.findAttributeDefinition(ConnectorFactoryIcfImpl.ICFS_UID);
		assertNotNull("No definition for attribute "+ConnectorFactoryIcfImpl.ICFS_UID, icfAttributeDefinition);
		assertTrue("Attribute "+ConnectorFactoryIcfImpl.ICFS_UID+" in not an identifier",icfAttributeDefinition.isIdentifier(accountDefinition));
		assertTrue("Attribute "+ConnectorFactoryIcfImpl.ICFS_UID+" in not in identifiers list",identifiers.contains(icfAttributeDefinition));
		
		Collection<ResourceAttributeDefinition> secondaryIdentifiers = accountDefinition.getSecondaryIdentifiers();
		assertNotNull("Null secondary identifiers for " + objectClassQname, secondaryIdentifiers);
		assertFalse("Empty secondary identifiers for " + objectClassQname, secondaryIdentifiers.isEmpty());
		
		ResourceAttributeDefinition nameAttributeDefinition = accountDefinition.findAttributeDefinition(ConnectorFactoryIcfImpl.ICFS_NAME);
		assertNotNull("No definition for attribute "+ConnectorFactoryIcfImpl.ICFS_NAME, nameAttributeDefinition);
		assertTrue("Attribute "+ConnectorFactoryIcfImpl.ICFS_NAME+" in not an identifier",nameAttributeDefinition.isSecondaryIdentifier(accountDefinition));
		assertTrue("Attribute "+ConnectorFactoryIcfImpl.ICFS_NAME+" in not in identifiers list",secondaryIdentifiers.contains(nameAttributeDefinition));

		assertNotNull("Null identifiers in account", accountDef.getIdentifiers());
		assertFalse("Empty identifiers in account", accountDef.getIdentifiers().isEmpty());
		assertNotNull("Null secondary identifiers in account", accountDef.getSecondaryIdentifiers());
		assertFalse("Empty secondary identifiers in account", accountDef.getSecondaryIdentifiers().isEmpty());
		assertNotNull("No naming attribute in account", accountDef.getNamingAttribute());
		assertFalse("No nativeObjectClass in account", StringUtils.isEmpty(accountDef.getNativeObjectClass()));

		ResourceAttributeDefinition uidDef = accountDef
				.findAttributeDefinition(ConnectorFactoryIcfImpl.ICFS_UID);
		assertEquals(1, uidDef.getMaxOccurs());
		assertEquals(0, uidDef.getMinOccurs());
		assertFalse("No UID display name", StringUtils.isBlank(uidDef.getDisplayName()));
		assertFalse("UID has create", uidDef.canCreate());
		assertFalse("UID has update",uidDef.canUpdate());
		assertTrue("No UID read",uidDef.canRead());
		assertTrue("UID definition not in identifiers", accountDef.getIdentifiers().contains(uidDef));

		ResourceAttributeDefinition nameDef = accountDef
				.findAttributeDefinition(ConnectorFactoryIcfImpl.ICFS_NAME);
		assertEquals(1, nameDef.getMaxOccurs());
		assertEquals(1, nameDef.getMinOccurs());
		assertFalse("No NAME displayName", StringUtils.isBlank(nameDef.getDisplayName()));
		assertTrue("No NAME create", nameDef.canCreate());
		assertTrue("No NAME update",nameDef.canUpdate());
		assertTrue("No NAME read",nameDef.canRead());
		assertTrue("NAME definition not in identifiers", accountDef.getSecondaryIdentifiers().contains(nameDef));
		
		assertNull("The _PASSSWORD_ attribute sneaked into schema", accountDef.findAttributeDefinition(new QName(ConnectorFactoryIcfImpl.NS_ICF_SCHEMA,"password")));
	}
	
	public static void assertDummyResourceSchemaSanity(ResourceSchema resourceSchema, ResourceType resourceType) {
		assertIcfResourceSchemaSanity(resourceSchema, resourceType);
		
		ObjectClassComplexTypeDefinition accountDef = resourceSchema.findDefaultAccountDefinition();
		
		ResourceAttributeDefinition fullnameDef = accountDef.findAttributeDefinition("fullname");
		assertNotNull("No definition for fullname", fullnameDef);
		assertEquals(1, fullnameDef.getMaxOccurs());
		assertEquals(1, fullnameDef.getMinOccurs());
		assertTrue("No fullname create", fullnameDef.canCreate());
		assertTrue("No fullname update", fullnameDef.canUpdate());
		assertTrue("No fullname read", fullnameDef.canRead());
		
	}

	public static void checkRepoShadow(PrismObject<AccountShadowType> repoShadow) {
		AccountShadowType repoShadowType = repoShadow.asObjectable();
		assertNotNull("No OID in repo shadow", repoShadowType.getOid());
		assertNotNull("No name in repo shadow", repoShadowType.getName());
		assertNotNull("No objectClass in repo shadow", repoShadowType.getObjectClass());
		PrismContainer<Containerable> attributesContainer = repoShadow.findContainer(AccountShadowType.F_ATTRIBUTES);
		assertNotNull("No attributes in repo shadow", attributesContainer);
		List<Item<?>> attributes = attributesContainer.getValue().getItems();
		assertFalse("Empty attributes in repo shadow", attributes.isEmpty());
		assertEquals("Unexpected number of attributes in repo shadow", 2, attributes.size());
	}

}
