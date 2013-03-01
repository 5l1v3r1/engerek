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
package com.evolveum.midpoint.provisioning.ucf.impl;

import com.evolveum.midpoint.common.crypto.EncryptionException;
import com.evolveum.midpoint.common.crypto.Protector;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.prism.xml.XsdTypeMapper;
import com.evolveum.midpoint.provisioning.ucf.api.*;
import com.evolveum.midpoint.provisioning.ucf.query.FilterInterpreter;
import com.evolveum.midpoint.provisioning.ucf.util.UcfUtil;
import com.evolveum.midpoint.schema.constants.ConnectorTestOperation;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.holder.XPathHolder;
import com.evolveum.midpoint.schema.holder.XPathSegment;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceObjectShadowUtil;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.*;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_2.*;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_2.ScriptCapabilityType.Host;
import com.evolveum.prism.xml.ns._public.query_2.QueryType;
import com.evolveum.prism.xml.ns._public.types_2.PolyStringType;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.Validate;
import org.identityconnectors.common.pooling.ObjectPoolConfiguration;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.*;
import org.identityconnectors.framework.api.operations.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;

import java.io.File;
import java.lang.reflect.Array;
import java.util.*;

import static com.evolveum.midpoint.provisioning.ucf.impl.IcfUtil.processIcfException;

/**
 * Implementation of ConnectorInstance for ICF connectors.
 * <p/>
 * This class implements the ConnectorInstance interface. The methods are
 * converting the data from the "midPoint semantics" as seen by the
 * ConnectorInstance interface to the "ICF semantics" as seen by the ICF
 * framework.
 * 
 * @author Radovan Semancik
 */
public class ConnectorInstanceIcfImpl implements ConnectorInstance {

	private static final String CUSTOM_OBJECTCLASS_PREFIX = "Custom";
	private static final String CUSTOM_OBJECTCLASS_SUFFIX = "ObjectClass";

	private static final com.evolveum.midpoint.xml.ns._public.resource.capabilities_2.ObjectFactory capabilityObjectFactory 
		= new com.evolveum.midpoint.xml.ns._public.resource.capabilities_2.ObjectFactory();

	private static final Trace LOGGER = TraceManager.getTrace(ConnectorInstanceIcfImpl.class);

	ConnectorInfo cinfo;
	ConnectorType connectorType;
	ConnectorFacade icfConnectorFacade;
	String resourceSchemaNamespace;
	Protector protector;
	PrismContext prismContext;

	private boolean initialized = false;
	private ResourceSchema resourceSchema = null;
	private PrismSchema connectorSchema;
	Set<Object> capabilities = null;

	public ConnectorInstanceIcfImpl(ConnectorInfo connectorInfo, ConnectorType connectorType,
			String schemaNamespace, PrismSchema connectorSchema, Protector protector,
			PrismContext prismContext) {
		this.cinfo = connectorInfo;
		this.connectorType = connectorType;
		this.resourceSchemaNamespace = schemaNamespace;
		this.connectorSchema = connectorSchema;
		this.protector = protector;
		this.prismContext = prismContext;
	}

	public String getSchemaNamespace() {
		return resourceSchemaNamespace;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance#configure
	 * (com.evolveum.midpoint.xml.ns._public.common.common_2.Configuration)
	 */
	@Override
	public void configure(PrismContainerValue configuration, OperationResult parentResult)
			throws CommunicationException, GenericFrameworkException, SchemaException, ConfigurationException {

		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".configure");
		result.addParam("configuration", configuration);

		try {
			// Get default configuration for the connector. This is important,
			// as it contains types of connector configuration properties.

			// Make sure that the proper configuration schema is applied. This
			// will cause that all the "raw" elements are parsed
			configuration.applyDefinition(getConfigurationContainerDefinition());

			APIConfiguration apiConfig = cinfo.createDefaultAPIConfiguration();

			// Transform XML configuration from the resource to the ICF
			// connector
			// configuration
			try {
				transformConnectorConfiguration(apiConfig, configuration);
			} catch (SchemaException e) {
				result.recordFatalError(e.getMessage(), e);
				throw e;
			}

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Configuring connector {}", connectorType);
				for (String propName : apiConfig.getConfigurationProperties().getPropertyNames()) {
					LOGGER.trace("P: {} = {}", propName,
							apiConfig.getConfigurationProperties().getProperty(propName).getValue());
				}
			}

			// Create new connector instance using the transformed configuration
			icfConnectorFacade = ConnectorFacadeFactory.getInstance().newInstance(apiConfig);

			result.recordSuccess();
		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, result);
			result.computeStatus("Removing attribute values failed");
			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof CommunicationException) {
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof SchemaException) {
				throw (SchemaException) midpointEx;
			} else if (midpointEx instanceof ConfigurationException) {
				throw (ConfigurationException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else {
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}

		}

	}

	private PrismContainerDefinition getConfigurationContainerDefinition() throws SchemaException {
		if (connectorSchema == null) {
			generateConnectorSchema();
		}
		QName configContainerQName = new QName(connectorType.getNamespace(),
				ResourceType.F_CONNECTOR_CONFIGURATION.getLocalPart());
		PrismContainerDefinition configContainerDef = connectorSchema
				.findContainerDefinitionByElementName(configContainerQName);
		if (configContainerDef == null) {
			throw new SchemaException("No definition of container " + configContainerQName
					+ " in configuration schema for connector " + this);
		}
		return configContainerDef;
	}

	/**
	 * @param cinfo
	 * @param connectorType
	 */
	public PrismSchema generateConnectorSchema() {

		LOGGER.trace("Generating configuration schema for {}", this);
		APIConfiguration defaultAPIConfiguration = cinfo.createDefaultAPIConfiguration();
		ConfigurationProperties icfConfigurationProperties = defaultAPIConfiguration
				.getConfigurationProperties();

		if (icfConfigurationProperties == null || icfConfigurationProperties.getPropertyNames() == null
				|| icfConfigurationProperties.getPropertyNames().isEmpty()) {
			LOGGER.debug("No configuration schema for {}", this);
			return null;
		}

		PrismSchema mpSchema = new PrismSchema(connectorType.getNamespace(), prismContext);

		// Create configuration type - the type used by the "configuration"
		// element
		PrismContainerDefinition configurationContainerDef = mpSchema.createPropertyContainerDefinition(
				ResourceType.F_CONNECTOR_CONFIGURATION.getLocalPart(),
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONFIGURATION_TYPE_LOCAL_NAME);

		// element with "ConfigurationPropertiesType" - the dynamic part of
		// configuration schema
		ComplexTypeDefinition configPropertiesTypeDef = mpSchema.createComplexTypeDefinition(new QName(
				connectorType.getNamespace(),
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_TYPE_LOCAL_NAME));

		// Create definition of "configurationProperties" type
		// (CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_TYPE_LOCAL_NAME)
		for (String icfPropertyName : icfConfigurationProperties.getPropertyNames()) {
			ConfigurationProperty icfProperty = icfConfigurationProperties.getProperty(icfPropertyName);

			QName propXsdType = icfTypeToXsdType(icfProperty.getType(), icfProperty.isConfidential());
			LOGGER.trace("{}: Mapping ICF config schema property {} from {} to {}", new Object[] { this,
					icfPropertyName, icfProperty.getType(), propXsdType });
			PrismPropertyDefinition propertyDefinifion = configPropertiesTypeDef.createPropertyDefinition(
					icfPropertyName, propXsdType);
			propertyDefinifion.setDisplayName(icfProperty.getDisplayName(null));
			propertyDefinifion.setHelp(icfProperty.getHelpMessage(null));
			if (isMultivaluedType(icfProperty.getType())) {
				propertyDefinifion.setMaxOccurs(-1);
			} else {
				propertyDefinifion.setMaxOccurs(1);
			}
			if (icfProperty.isRequired()) {
				propertyDefinifion.setMinOccurs(1);
			} else {
				propertyDefinifion.setMinOccurs(0);
			}

		}

		// Create common ICF configuration property containers as a references
		// to a static schema
		configurationContainerDef.createContainerDefinition(
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_ELEMENT,
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_TYPE, 0, 1);
		configurationContainerDef.createPropertyDefinition(
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_PRODUCER_BUFFER_SIZE_ELEMENT,
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_PRODUCER_BUFFER_SIZE_TYPE, 0, 1);
		configurationContainerDef.createContainerDefinition(
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_TIMEOUTS_ELEMENT,
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_TIMEOUTS_TYPE, 0, 1);

		// No need to create definition of "configuration" element.
		// midPoint will look for this element, but it will be generated as part
		// of the PropertyContainer serialization to schema

		configurationContainerDef.createContainerDefinition(
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_ELEMENT_QNAME,
				configPropertiesTypeDef, 1, 1);

		LOGGER.debug("Generated configuration schema for {}: {} definitions", this, mpSchema.getDefinitions()
				.size());
		connectorSchema = mpSchema;
		return mpSchema;
	}

	private QName icfTypeToXsdType(Class<?> type, boolean isConfidential) {
		// For arrays we are only interested in the component type
		if (isMultivaluedType(type)) {
			type = type.getComponentType();
		}
		QName propXsdType = null;
		if (GuardedString.class.equals(type) || 
				(String.class.equals(type) && isConfidential)) {
			// GuardedString is a special case. It is a ICF-specific
			// type
			// implementing Potemkin-like security. Use a temporary
			// "nonsense" type for now, so this will fail in tests and
			// will be fixed later
			propXsdType = SchemaConstants.R_PROTECTED_STRING_TYPE;
		} else if (GuardedByteArray.class.equals(type) || 
				(Byte.class.equals(type) && isConfidential)) {
			// GuardedString is a special case. It is a ICF-specific
			// type
			// implementing Potemkin-like security. Use a temporary
			// "nonsense" type for now, so this will fail in tests and
			// will be fixed later
			propXsdType = SchemaConstants.R_PROTECTED_BYTE_ARRAY_TYPE;
		} else {
			propXsdType = XsdTypeMapper.toXsdType(type);
		}
		return propXsdType;
	}

	private boolean isMultivaluedType(Class<?> type) {
		// We consider arrays to be multi-valued
		// ... unless it is byte[] or char[]
		return type.isArray() && !type.equals(byte[].class) && !type.equals(char[].class);
	}

	/**
	 * Retrieves schema from the resource.
	 * <p/>
	 * Transforms native ICF schema to the midPoint representation.
	 * 
	 * @return midPoint resource schema.
	 * @see com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance#initialize(com.evolveum.midpoint.schema.result.OperationResult)
	 */
	@Override
	public void initialize(OperationResult parentResult) throws CommunicationException,
			GenericFrameworkException, ConfigurationException {

		// Result type for this operation
		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".initialize");
		result.addContext("connector", connectorType);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ConnectorFactoryIcfImpl.class);

		if (icfConnectorFacade == null) {
			result.recordFatalError("Attempt to use unconfigured connector");
			throw new IllegalStateException("Attempt to use unconfigured connector "
					+ ObjectTypeUtil.toShortString(connectorType));
		}

		// Connector operation cannot create result for itself, so we need to
		// create result for it
		OperationResult icfResult = result.createSubresult(ConnectorFacade.class.getName() + ".schema");
		icfResult.addContext("connector", icfConnectorFacade.getClass());

		org.identityconnectors.framework.common.objects.Schema icfSchema = null;
		try {

			// Fetch the schema from the connector (which actually gets that
			// from the resource).
			icfSchema = icfConnectorFacade.schema();

			icfResult.recordSuccess();
		} catch (UnsupportedOperationException ex) {
			// The connector does no support schema() operation.
			result.recordStatus(OperationResultStatus.NOT_APPLICABLE, "Connector does not support schema");
			resourceSchema = null;
			initialized = true;
			return;
		} catch (Exception ex) {
			// conditions.
			// Therefore this kind of heavy artillery is necessary.
			// ICF interface does not specify exceptions or other error
			// TODO maybe we can try to catch at least some specific exceptions
			Exception midpointEx = processIcfException(ex, icfResult);

			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof CommunicationException) {
				//communication error is not critical.do not add it to the result as 
//				result.recordFatalError("ICF communication error: " + midpointEx.getMessage(), midpointEx);
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof ConfigurationException) {
				result.recordFatalError("ICF configuration error: " + midpointEx.getMessage(), midpointEx);
				throw (ConfigurationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				result.recordFatalError("ICF error: " + midpointEx.getMessage(), midpointEx);
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				result.recordFatalError("ICF error: " + midpointEx.getMessage(), midpointEx);
				throw (RuntimeException) midpointEx;
			} else {
				result.recordFatalError("Internal error: " + midpointEx.getMessage(), midpointEx);
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
		}

		parseResourceSchema(icfSchema);
		
		initialized = true;

		result.recordSuccess();
	}

	@Override
	public ResourceSchema getResourceSchema(OperationResult parentResult) throws CommunicationException,
			GenericFrameworkException, ConfigurationException {

		// Result type for this operation
		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".getResourceSchema");
		result.addContext("connector", connectorType);

		if (!initialized) {
			// initialize the connector if it was not initialized yet
			try {
				initialize(result);
			} catch (CommunicationException ex) {
				result.recordFatalError(ex);
				throw ex;
			} catch (ConfigurationException ex) {
				result.recordFatalError(ex);
				throw ex;
			} catch (GenericFrameworkException ex) {
				result.recordFatalError(ex);
				throw ex;
			}
		}

		result.recordSuccess();

		return resourceSchema;
	}

	private void parseResourceSchema(org.identityconnectors.framework.common.objects.Schema icfSchema) {

		boolean capPassword = false;
		boolean capEnable = false;

		// New instance of midPoint schema object
		resourceSchema = new ResourceSchema(getSchemaNamespace(), prismContext);

		// Let's convert every objectclass in the ICF schema ...
		Set<ObjectClassInfo> objectClassInfoSet = icfSchema.getObjectClassInfo();
		for (ObjectClassInfo objectClassInfo : objectClassInfoSet) {

			// "Flat" ICF object class names needs to be mapped to QNames
			QName objectClassXsdName = objectClassToQname(objectClassInfo.getType());

			// ResourceObjectDefinition is a midPpoint way how to represent an
			// object class.
			// The important thing here is the last "type" parameter
			// (objectClassXsdName). The rest is more-or-less cosmetics.
			ObjectClassComplexTypeDefinition roDefinition = resourceSchema
					.createObjectClassDefinition(objectClassXsdName);

			// The __ACCOUNT__ objectclass in ICF is a default account
			// objectclass. So mark it appropriately.
			if (ObjectClass.ACCOUNT_NAME.equals(objectClassInfo.getType())) {
				roDefinition.setAccountType(true);
				roDefinition.setDefaultAccountType(true);
			}

			// Every object has UID in ICF, therefore add it right now
			ResourceAttributeDefinition uidDefinition = roDefinition.createAttributeDefinition(
					ConnectorFactoryIcfImpl.ICFS_UID, DOMUtil.XSD_STRING);
			// DO NOT make it mandatory. It must not be present on create hence it cannot be mandatory.
			uidDefinition.setMinOccurs(0);
			uidDefinition.setMaxOccurs(1);
			// Make it read-only
			uidDefinition.setReadOnly();
			// Set a default display name
			uidDefinition.setDisplayName("ICF UID");
			// Uid is a primary identifier of every object (this is the ICF way)
			roDefinition.getIdentifiers().add(uidDefinition);

			// Let's iterate over all attributes in this object class ...
			Set<AttributeInfo> attributeInfoSet = objectClassInfo.getAttributeInfo();
			for (AttributeInfo attributeInfo : attributeInfoSet) {

				if (OperationalAttributes.PASSWORD_NAME.equals(attributeInfo.getName())) {
					// This attribute will not go into the schema
					// instead a "password" capability is used
					capPassword = true;
					// Skip this attribute, capability is sufficient
					continue;
				}

				if (OperationalAttributes.ENABLE_NAME.equals(attributeInfo.getName())) {
					capEnable = true;
					// Skip this attribute, capability is sufficient
					continue;
				}

				QName attrXsdName = convertAttributeNameToQName(attributeInfo.getName());
				QName attrXsdType = icfTypeToXsdType(attributeInfo.getType(), false);

				// Create ResourceObjectAttributeDefinition, which is midPoint
				// way how to express attribute schema.
				ResourceAttributeDefinition roaDefinition = roDefinition.createAttributeDefinition(
						attrXsdName, attrXsdType);

				
				if (attrXsdName.equals(ConnectorFactoryIcfImpl.ICFS_NAME)) {
					// Set a better display name for __NAME__. The "name" is s very
					// overloaded term, so let's try to make things
					// a bit clearer
					roaDefinition.setDisplayName("ICF NAME");
					roDefinition.getSecondaryIdentifiers().add(roaDefinition);
				}

				// Now we are going to process flags such as optional and
				// multi-valued
				Set<Flags> flagsSet = attributeInfo.getFlags();
				// System.out.println(flagsSet);

				roaDefinition.setMinOccurs(0);
				roaDefinition.setMaxOccurs(1);
				boolean canCreate = true;
				boolean canUpdate = true;
				boolean canRead = true;

				for (Flags flags : flagsSet) {
					if (flags == Flags.REQUIRED) {
						roaDefinition.setMinOccurs(1);
					}
					if (flags == Flags.MULTIVALUED) {
						roaDefinition.setMaxOccurs(-1);
					}
					if (flags == Flags.NOT_CREATABLE) {
						canCreate = false;
					}
					if (flags == Flags.NOT_READABLE) {
						canRead = false;
					}
					if (flags == Flags.NOT_UPDATEABLE) {
						canUpdate = false;
					}
					if (flags == Flags.NOT_RETURNED_BY_DEFAULT) {
						// TODO
					}
				}

				roaDefinition.setCreate(canCreate);
				roaDefinition.setUpdate(canUpdate);
				roaDefinition.setRead(canRead);

			}

			// Add schema annotations
			roDefinition.setNativeObjectClass(objectClassInfo.getType());
			roDefinition.setDisplayNameAttribute(ConnectorFactoryIcfImpl.ICFS_NAME);
			roDefinition.setNamingAttribute(ConnectorFactoryIcfImpl.ICFS_NAME);

		}

		capabilities = new HashSet<Object>();

		if (capEnable) {
			ActivationCapabilityType capAct = new ActivationCapabilityType();
			ActivationEnableDisableCapabilityType capEnableDisable = new ActivationEnableDisableCapabilityType();
			capAct.setEnableDisable(capEnableDisable);

			capabilities.add(capabilityObjectFactory.createActivation(capAct));
		}

		if (capPassword) {
			CredentialsCapabilityType capCred = new CredentialsCapabilityType();
			PasswordCapabilityType capPass = new PasswordCapabilityType();
			capCred.setPassword(capPass);
			capabilities.add(capabilityObjectFactory.createCredentials(capCred));
		}

		// Create capabilities from supported connector operations

		Set<Class<? extends APIOperation>> supportedOperations = icfConnectorFacade.getSupportedOperations();

		if (supportedOperations.contains(SyncApiOp.class)) {
			LiveSyncCapabilityType capSync = new LiveSyncCapabilityType();
			capabilities.add(capabilityObjectFactory.createLiveSync(capSync));
		}

		if (supportedOperations.contains(TestApiOp.class)) {
			TestConnectionCapabilityType capTest = new TestConnectionCapabilityType();
			capabilities.add(capabilityObjectFactory.createTestConnection(capTest));
		}

		if (supportedOperations.contains(ScriptOnResourceApiOp.class)
				|| supportedOperations.contains(ScriptOnConnectorApiOp.class)) {
			ScriptCapabilityType capScript = new ScriptCapabilityType();
			if (supportedOperations.contains(ScriptOnResourceApiOp.class)) {
				Host host = new Host();
				host.setType(ProvisioningScriptHostType.RESOURCE);
				capScript.getHost().add(host);
				// language is unknown here
			}
			if (supportedOperations.contains(ScriptOnConnectorApiOp.class)) {
				Host host = new Host();
				host.setType(ProvisioningScriptHostType.CONNECTOR);
				capScript.getHost().add(host);
				// language is unknown here
			}
			capabilities.add(capabilityObjectFactory.createScript(capScript));
		}

	}

	@Override
	public Set<Object> getCapabilities(OperationResult parentResult) throws CommunicationException,
			GenericFrameworkException, ConfigurationException {

		// Result type for this operation
		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".getCapabilities");
		result.addContext("connector", connectorType);

		if (!initialized) {
			// initialize the connector if it was not initialized yet
			try {
				initialize(result);
			} catch (CommunicationException ex) {
				result.recordFatalError(ex);
				throw ex;
			} catch (ConfigurationException ex) {
				result.recordFatalError(ex);
				throw ex;
			} catch (GenericFrameworkException ex) {
				result.recordFatalError(ex);
				throw ex;
			}
		}

		result.recordSuccess();

		return capabilities;
	}

	@Override
	public <T extends ResourceObjectShadowType> PrismObject<T> fetchObject(Class<T> type,
			ObjectClassComplexTypeDefinition objectClassDefinition,
			Collection<? extends ResourceAttribute> identifiers, boolean returnDefaultAttributes,
			Collection<? extends ResourceAttributeDefinition> attributesToReturn, OperationResult parentResult)
			throws ObjectNotFoundException, CommunicationException, GenericFrameworkException,
			SchemaException, SecurityViolationException {

		// Result type for this operation
		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".fetchObject");
		result.addParam("resourceObjectDefinition", objectClassDefinition);
		result.addParam("identifiers", identifiers);
		result.addContext("connector", connectorType);

		if (icfConnectorFacade == null) {
			result.recordFatalError("Attempt to use unconfigured connector");
			throw new IllegalStateException("Attempt to use unconfigured connector "
					+ ObjectTypeUtil.toShortString(connectorType));
		}

		// Get UID from the set of identifiers
		Uid uid = getUid(identifiers);
		if (uid == null) {
			result.recordFatalError("Required attribute UID not found in identification set while attempting to fetch object identified by "
					+ identifiers + " from " + ObjectTypeUtil.toShortString(connectorType));
			throw new IllegalArgumentException(
					"Required attribute UID not found in identification set while attempting to fetch object identified by "
							+ identifiers + " from " + ObjectTypeUtil.toShortString(connectorType));
		}

		ObjectClass icfObjectClass = objectClassToIcf(objectClassDefinition);
		if (icfObjectClass == null) {
			result.recordFatalError("Unable to detemine object class from QName "
					+ objectClassDefinition.getTypeName()
					+ " while attempting to fetch object identified by " + identifiers + " from "
					+ ObjectTypeUtil.toShortString(connectorType));
			throw new IllegalArgumentException("Unable to detemine object class from QName "
					+ objectClassDefinition.getTypeName()
					+ " while attempting to fetch object identified by " + identifiers + " from "
					+ ObjectTypeUtil.toShortString(connectorType));
		}

		ConnectorObject co = null;
		try {

			// Invoke the ICF connector
			co = fetchConnectorObject(icfObjectClass, uid, returnDefaultAttributes, attributesToReturn,
					result);

		} catch (CommunicationException ex) {
			result.recordFatalError("ICF invocation failed due to communication problem");
			// This is fatal. No point in continuing. Just re-throw the
			// exception.
			throw ex;
		} catch (GenericFrameworkException ex) {
			result.recordFatalError("ICF invocation failed due to a generic ICF framework problem");
			// This is fatal. No point in continuing. Just re-throw the
			// exception.
			throw ex;
		}

		if (co == null) {
			result.recordFatalError("Object not found");
			throw new ObjectNotFoundException("Object identified by " + identifiers + " was not found by "
					+ ObjectTypeUtil.toShortString(connectorType));
		}

		PrismObjectDefinition<T> shadowDefinition = toShadowDefinition(objectClassDefinition);
		PrismObject<T> shadow = convertToResourceObject(co, shadowDefinition, false);

		result.recordSuccess();
		return shadow;

	}

	private <T extends ResourceObjectShadowType> PrismObjectDefinition<T> toShadowDefinition(
			ObjectClassComplexTypeDefinition objectClassDefinition) {
		ResourceAttributeContainerDefinition resourceAttributeContainerDefinition = objectClassDefinition
				.toResourceAttributeContainerDefinition(ResourceObjectShadowType.F_ATTRIBUTES);
		return resourceAttributeContainerDefinition.toShadowDefinition();
	}

	/**
	 * Returns null if nothing is found.
	 */
	private ConnectorObject fetchConnectorObject(ObjectClass icfObjectClass, Uid uid,
			boolean returnDefaultAttributes,
			Collection<? extends ResourceAttributeDefinition> attributesToReturn, OperationResult parentResult)
			throws ObjectNotFoundException, CommunicationException, GenericFrameworkException, SecurityViolationException {

		// Connector operation cannot create result for itself, so we need to
		// create result for it
		OperationResult icfResult = parentResult.createSubresult(ConnectorFacade.class.getName()
				+ ".getObject");
		icfResult.addParam("objectClass", icfObjectClass.toString());
		icfResult.addParam("uid", uid.getUidValue());
		icfResult.addContext("connector", icfConnectorFacade.getClass());

		ConnectorObject co = null;
		try {
			OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();

			// Invoke the ICF connector
			co = icfConnectorFacade.getObject(icfObjectClass, uid, optionsBuilder.build());

			icfResult.recordSuccess();
		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, icfResult);
			icfResult.computeStatus("Add object failed");

			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof CommunicationException) {
				icfResult.muteError();
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else if (midpointEx instanceof SecurityViolationException){
				throw (SecurityViolationException) midpointEx;
			} else{
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
		
		}

		return co;
	}

	@Override
	public Collection<ResourceAttribute<?>> addObject(PrismObject<? extends ResourceObjectShadowType> object,
			Collection<Operation> additionalOperations, OperationResult parentResult) throws CommunicationException,
			GenericFrameworkException, SchemaException, ObjectAlreadyExistsException {
		validateShadow(object, "add", false);

		ResourceAttributeContainer attributesContainer = ResourceObjectShadowUtil
				.getAttributesContainer(object);
		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".addObject");
		result.addParam("resourceObject", object);
		result.addParam("additionalOperations", additionalOperations);

		// getting icf object class from resource object class
		ObjectClass objectClass = objectClassToIcf(object);

		if (objectClass == null) {
			result.recordFatalError("Couldn't get icf object class from " + object);
			throw new IllegalArgumentException("Couldn't get icf object class from " + object);
		}

		// setting ifc attributes from resource object attributes
		Set<Attribute> attributes = null;
		try {
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("midPoint object before conversion:\n{}", attributesContainer.dump());
			}
			attributes = convertFromResourceObject(attributesContainer, result);

			if (object.asObjectable() instanceof AccountShadowType) {
				AccountShadowType account = (AccountShadowType) object.asObjectable();
				if (account.getCredentials() != null && account.getCredentials().getPassword() != null) {
					PasswordType password = account.getCredentials().getPassword();
					ProtectedStringType protectedString = password.getValue();
					GuardedString guardedPassword = toGuardedString(protectedString, "new password");
					attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME,
							guardedPassword));
				}
				
				if (account.getActivation() != null && account.getActivation().isEnabled() != null){
					attributes.add(AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, account.getActivation().isEnabled().booleanValue()));
				}
			}
			
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("ICF attributes after conversion:\n{}", IcfUtil.dump(attributes));
			}
		} catch (SchemaException ex) {
			result.recordFatalError(
					"Error while converting resource object attributes. Reason: " + ex.getMessage(), ex);
			throw new SchemaException("Error while converting resource object attributes. Reason: "
					+ ex.getMessage(), ex);
		}
		if (attributes == null) {
			result.recordFatalError("Couldn't set attributes for icf.");
			throw new IllegalStateException("Couldn't set attributes for icf.");
		}

		// Look for a password change operation
		// if (additionalOperations != null) {
		// for (Operation op : additionalOperations) {
		// if (op instanceof PasswordChangeOperation) {
		// PasswordChangeOperation passwordChangeOperation =
		// (PasswordChangeOperation) op;
		// // Activation change means modification of attributes
		// convertFromPassword(attributes, passwordChangeOperation);
		// }
		//
		// }
		// }

		OperationResult icfResult = result.createSubresult(ConnectorFacade.class.getName() + ".create");
		icfResult.addParam("objectClass", objectClass);
		icfResult.addParam("attributes", attributes);
		icfResult.addParam("options", null);
		icfResult.addContext("connector", icfConnectorFacade);

		Uid uid = null;
		try {

			checkAndExecuteAdditionalOperation(additionalOperations, ProvisioningScriptOrderType.BEFORE);

			// CALL THE ICF FRAMEWORK
			uid = icfConnectorFacade.create(objectClass, attributes, new OperationOptionsBuilder().build());

			checkAndExecuteAdditionalOperation(additionalOperations, ProvisioningScriptOrderType.AFTER);

		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, icfResult);
			result.computeStatus("Add object failed");

			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof ObjectAlreadyExistsException) {
				throw (ObjectAlreadyExistsException) midpointEx;
			} else if (midpointEx instanceof CommunicationException) {
//				icfResult.muteError();
//				result.muteError();
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof SchemaException) {
				throw (SchemaException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else {
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
		}

		if (uid == null || uid.getUidValue() == null || uid.getUidValue().isEmpty()) {
			icfResult.recordFatalError("ICF did not returned UID after create");
			result.computeStatus("Add object failed");
			throw new GenericFrameworkException("ICF did not returned UID after create");
		}

		ResourceAttributeDefinition uidDefinition = getUidDefinition(attributesContainer.getDefinition());
		if (uidDefinition == null) {
			throw new IllegalArgumentException("No definition for ICF UID attribute found in definition "
					+ attributesContainer.getDefinition());
		}
		ResourceAttribute attribute = createUidAttribute(uid, uidDefinition);
		attributesContainer.getValue().addReplaceExisting(attribute);
		icfResult.recordSuccess();

		result.recordSuccess();
		return attributesContainer.getAttributes();
	}

	private void validateShadow(PrismObject<? extends ResourceObjectShadowType> shadow, String operation,
			boolean requireUid) {
		if (shadow == null) {
			throw new IllegalArgumentException("Cannot " + operation + " null " + shadow);
		}
		PrismContainer<?> attributesContainer = shadow.findContainer(ResourceObjectShadowType.F_ATTRIBUTES);
		if (attributesContainer == null) {
			throw new IllegalArgumentException("Cannot " + operation + " shadow without attributes container");
		}
		ResourceAttributeContainer resourceAttributesContainer = ResourceObjectShadowUtil
				.getAttributesContainer(shadow);
		if (resourceAttributesContainer == null) {
			throw new IllegalArgumentException("Cannot " + operation
					+ " shadow without attributes container of type ResourceAttributeContainer, got "
					+ attributesContainer.getClass());
		}
		if (requireUid) {
			Collection<ResourceAttribute<?>> identifiers = resourceAttributesContainer.getIdentifiers();
			if (identifiers == null || identifiers.isEmpty()) {
				throw new IllegalArgumentException("Cannot " + operation + " shadow without identifiers");
			}
		}
	}

	@Override
	public Set<PropertyModificationOperation> modifyObject(ObjectClassComplexTypeDefinition objectClass,
			Collection<? extends ResourceAttribute> identifiers, Collection<Operation> changes,
			OperationResult parentResult) throws ObjectNotFoundException, CommunicationException,
			GenericFrameworkException, SchemaException, SecurityViolationException {

		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".modifyObject");
		result.addParam("objectClass", objectClass);
		result.addParam("identifiers", identifiers);
		result.addParam("changes", changes);
		
		if (changes.isEmpty()){
			LOGGER.info("No modifications for connector object specified. Skipping processing.");
			result.recordSuccess();
			return new HashSet<PropertyModificationOperation>();
		}

		ObjectClass objClass = objectClassToIcf(objectClass);
		
		Uid uid = getUid(identifiers);
		String originalUid = uid.getUidValue();

		Collection<ResourceAttribute<?>> addValues = new HashSet<ResourceAttribute<?>>();
		Collection<ResourceAttribute<?>> updateValues = new HashSet<ResourceAttribute<?>>();
		Collection<ResourceAttribute<?>> valuesToRemove = new HashSet<ResourceAttribute<?>>();

		Set<Operation> additionalOperations = new HashSet<Operation>();
		PasswordChangeOperation passwordChangeOperation = null;
		Collection<PropertyDelta> activationDeltas = new HashSet<PropertyDelta>();
		PropertyDelta<ProtectedStringType> passwordDelta = null;

		for (Operation operation : changes) {
			if (operation instanceof PropertyModificationOperation) {
				PropertyModificationOperation change = (PropertyModificationOperation) operation;
				PropertyDelta<?> delta = change.getPropertyDelta();

				if (delta.getParentPath().equals(new ItemPath(ResourceObjectShadowType.F_ATTRIBUTES))) {
					if (delta.getDefinition() == null || !(delta.getDefinition() instanceof ResourceAttributeDefinition)) {
						ResourceAttributeDefinition def = objectClass
								.findAttributeDefinition(delta.getName());
						delta.applyDefinition(def);
					}
					// Change in (ordinary) attributes. Transform to the ICF
					// attributes.
					if (delta.isAdd()) {
						ResourceAttribute addAttribute = (ResourceAttribute) delta.instantiateEmptyProperty();
						addAttribute.addValues(PrismValue.cloneCollection(delta.getValuesToAdd()));
						if (addAttribute.getDefinition().isMultiValue()) {
							addValues.add(addAttribute);
						} else {
							// Force "update" for single-valued attributes instead of "add". This is saving one
							// read in some cases. It should also make no substantial difference in such case.
							// But it is working around some connector bugs.
							updateValues.add(addAttribute);
						}
					}
					if (delta.isDelete()) {
						ResourceAttribute deleteAttribute = (ResourceAttribute) delta.instantiateEmptyProperty();
						if (deleteAttribute.getDefinition().isMultiValue()) {
							deleteAttribute.addValues(PrismValue.cloneCollection(delta.getValuesToDelete()));
							valuesToRemove.add(deleteAttribute);
						} else {
							// Force "update" for single-valued attributes instead of "add". This is saving one
							// read in some cases. 
							// Update attribute to no values. This will efficiently clean up the attribute.
							// It should also make no substantial difference in such case. 
							// But it is working around some connector bugs.
							updateValues.add(deleteAttribute);
						}
					}
					if (delta.isReplace()) {
						ResourceAttribute updateAttribute = (ResourceAttribute) delta
								.instantiateEmptyProperty();
						updateAttribute.addValues(PrismValue.cloneCollection(delta.getValuesToReplace()));
						updateValues.add(updateAttribute);
					}
				} else if (delta.getParentPath().equals(new ItemPath(AccountShadowType.F_ACTIVATION))) {
					activationDeltas.add(delta);
				} else if (delta.getParentPath().equals(
						new ItemPath(new ItemPath(AccountShadowType.F_CREDENTIALS),
								CredentialsType.F_PASSWORD))) {
					passwordDelta = (PropertyDelta<ProtectedStringType>) delta;
				} else {
					throw new SchemaException("Change of unknown attribute " + delta.getName());
				}

			} else if (operation instanceof PasswordChangeOperation) {
				passwordChangeOperation = (PasswordChangeOperation) operation;
				// TODO: check for multiple occurrences and fail

			} else if (operation instanceof ExecuteProvisioningScriptOperation) {
				ExecuteProvisioningScriptOperation scriptOperation = (ExecuteProvisioningScriptOperation) operation;
				additionalOperations.add(scriptOperation);

			} else {
				throw new IllegalArgumentException("Unknown operation type " + operation.getClass().getName()
						+ ": " + operation);
			}

		}

		// Needs three complete try-catch blocks because we need to create
		// icfResult for each operation
		// and handle the faults individually

		checkAndExecuteAdditionalOperation(additionalOperations, ProvisioningScriptOrderType.BEFORE);

		OperationResult icfResult = null;
		try {
			if (addValues != null && !addValues.isEmpty()) {
				Set<Attribute> attributes = null;
				try {
					attributes = convertFromResourceObject(addValues, result);
				} catch (SchemaException ex) {
					result.recordFatalError("Error while converting resource object attributes. Reason: "
							+ ex.getMessage(), ex);
					throw new SchemaException("Error while converting resource object attributes. Reason: "
							+ ex.getMessage(), ex);
				}
				OperationOptions options = new OperationOptionsBuilder().build();
				icfResult = result.createSubresult(ConnectorFacade.class.getName() + ".addAttributeValues");
				icfResult.addParam("objectClass", objectClass);
				icfResult.addParam("uid", uid.getUidValue());
				icfResult.addParam("attributes", attributes);
				icfResult.addParam("options", options);
				icfResult.addContext("connector", icfConnectorFacade);

				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace(
							"Invoking ICF addAttributeValues(), objectclass={}, uid={}, attributes=\n{}",
							new Object[] { objClass, uid, dumpAttributes(attributes) });
				}

				uid = icfConnectorFacade.addAttributeValues(objClass, uid, attributes, options);

				icfResult.recordSuccess();
			}
		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, icfResult);
			result.computeStatus("Adding attribute values failed");
			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof ObjectNotFoundException) {
				throw (ObjectNotFoundException) midpointEx;
			} else if (midpointEx instanceof CommunicationException) {
				//in this situation this is not a critical error, becasue we know to handle it..so mute the error and sign it as expected
				result.muteError();
				icfResult.muteError();
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof SchemaException) {
				throw (SchemaException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else if (midpointEx instanceof SecurityViolationException){
				throw (SecurityViolationException) midpointEx;
			}else{
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
		}

		if (updateValues != null && !updateValues.isEmpty() || activationDeltas != null
				|| passwordDelta != null) {

			Set<Attribute> updateAttributes = null;

			try {
				updateAttributes = convertFromResourceObject(updateValues, result);
			} catch (SchemaException ex) {
				result.recordFatalError(
						"Error while converting resource object attributes. Reason: " + ex.getMessage(), ex);
				throw new SchemaException("Error while converting resource object attributes. Reason: "
						+ ex.getMessage(), ex);
			}

			if (activationDeltas != null) {
				// Activation change means modification of attributes
				convertFromActivation(updateAttributes, activationDeltas);
			}

			if (passwordDelta != null) {
				// Activation change means modification of attributes
				convertFromPassword(updateAttributes, passwordDelta);
			}

			OperationOptions options = new OperationOptionsBuilder().build();
			icfResult = result.createSubresult(ConnectorFacade.class.getName() + ".update");
			icfResult.addParam("objectClass", objectClass);
			icfResult.addParam("uid", uid.getUidValue());
			icfResult.addParam("attributes", updateAttributes);
			icfResult.addParam("options", options);
			icfResult.addContext("connector", icfConnectorFacade);

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Invoking ICF update(), objectclass={}, uid={}, attributes=\n{}", new Object[] {
						objClass, uid, dumpAttributes(updateAttributes) });
			}

			try {
				// Call ICF
				uid = icfConnectorFacade.update(objClass, uid, updateAttributes, options);

				icfResult.recordSuccess();
			} catch (Exception ex) {
				Exception midpointEx = processIcfException(ex, icfResult);
				result.computeStatus("Update failed");
				// Do some kind of acrobatics to do proper throwing of checked
				// exception
				if (midpointEx instanceof ObjectNotFoundException) {
					throw (ObjectNotFoundException) midpointEx;
				} else if (midpointEx instanceof CommunicationException) {
					//in this situation this is not a critical error, becasue we know to handle it..so mute the error and sign it as expected
					result.muteError();
					icfResult.muteError();
					throw (CommunicationException) midpointEx;
				} else if (midpointEx instanceof GenericFrameworkException) {
					throw (GenericFrameworkException) midpointEx;
				} else if (midpointEx instanceof SchemaException) {
					throw (SchemaException) midpointEx;
				} else if (midpointEx instanceof RuntimeException) {
					throw (RuntimeException) midpointEx;
				} else {
					throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
				}
			}
		}

		try {
			if (valuesToRemove != null && !valuesToRemove.isEmpty()) {
				Set<Attribute> attributes = null;
				try {
					attributes = convertFromResourceObject(valuesToRemove, result);
				} catch (SchemaException ex) {
					result.recordFatalError("Error while converting resource object attributes. Reason: "
							+ ex.getMessage(), ex);
					throw new SchemaException("Error while converting resource object attributes. Reason: "
							+ ex.getMessage(), ex);
				}
				OperationOptions options = new OperationOptionsBuilder().build();
				icfResult = result.createSubresult(ConnectorFacade.class.getName() + ".update");
				icfResult.addParam("objectClass", objectClass);
				icfResult.addParam("uid", uid.getUidValue());
				icfResult.addParam("attributes", attributes);
				icfResult.addParam("options", options);
				icfResult.addContext("connector", icfConnectorFacade);

				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace(
							"Invoking ICF removeAttributeValues(), objectclass={}, uid={}, attributes=\n{}",
							new Object[] { objClass, uid, dumpAttributes(attributes) });
				}

				uid = icfConnectorFacade.removeAttributeValues(objClass, uid, attributes, options);
				icfResult.recordSuccess();
			}
		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, icfResult);
			result.computeStatus("Removing attribute values failed");
			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof ObjectNotFoundException) {
				throw (ObjectNotFoundException) midpointEx;
			} else if (midpointEx instanceof CommunicationException) {
				//in this situation this is not a critical error, becasue we know to handle it..so mute the error and sign it as expected
				result.muteError();
				icfResult.muteError();
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof SchemaException) {
				throw (SchemaException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else {
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
		}
		checkAndExecuteAdditionalOperation(additionalOperations, ProvisioningScriptOrderType.AFTER);
		result.recordSuccess();

		Set<PropertyModificationOperation> sideEffectChanges = new HashSet<PropertyModificationOperation>();
		if (!originalUid.equals(uid.getUidValue())) {
			// UID was changed during the operation, this is most likely a
			// rename
			PropertyDelta uidDelta = createUidDelta(uid, getUidDefinition(identifiers));
			PropertyModificationOperation uidMod = new PropertyModificationOperation(uidDelta);
			sideEffectChanges.add(uidMod);
		}
		return sideEffectChanges;
	}

	private PropertyDelta createUidDelta(Uid uid, ResourceAttributeDefinition uidDefinition) {
		PropertyDelta uidDelta = new PropertyDelta(new ItemPath(ResourceObjectShadowType.F_ATTRIBUTES),
				uidDefinition);
		uidDelta.setValueToReplace(new PrismPropertyValue<String>(uid.getUidValue()));
		return uidDelta;
	}

	private String dumpAttributes(Set<Attribute> attributes) {
		if (attributes == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder();
		for (Attribute attr : attributes) {
			for (Object value : attr.getValue()) {
				sb.append(attr.getName());
				sb.append(" = ");
				sb.append(value);
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	@Override
	public void deleteObject(ObjectClassComplexTypeDefinition objectClass,
			Collection<Operation> additionalOperations, Collection<? extends ResourceAttribute> identifiers,
			OperationResult parentResult) throws ObjectNotFoundException, CommunicationException,
			GenericFrameworkException {

		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".deleteObject");
		result.addParam("identifiers", identifiers);

		ObjectClass objClass = objectClassToIcf(objectClass);
		Uid uid = getUid(identifiers);

		OperationResult icfResult = result.createSubresult(ConnectorFacade.class.getName() + ".delete");
		icfResult.addParam("uid", uid);
		icfResult.addParam("objectClass", objClass);
		icfResult.addContext("connector", icfConnectorFacade);

		try {

			checkAndExecuteAdditionalOperation(additionalOperations, ProvisioningScriptOrderType.BEFORE);

			icfConnectorFacade.delete(objClass, uid, new OperationOptionsBuilder().build());

			
			
			checkAndExecuteAdditionalOperation(additionalOperations, ProvisioningScriptOrderType.AFTER);
			icfResult.recordSuccess();

		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, icfResult);
			result.computeStatus("Removing attribute values failed");
			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof ObjectNotFoundException) {
				throw (ObjectNotFoundException) midpointEx;
			} else if (midpointEx instanceof CommunicationException) {
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof SchemaException) {
				// Schema exception during delete? It must be a missing UID
				throw new IllegalArgumentException(midpointEx.getMessage(), midpointEx);
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else {
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
		}

		result.recordSuccess();
	}

	@Override
	public PrismProperty<?> deserializeToken(Object serializedToken) {
		return createTokenProperty(serializedToken);
	}

	@Override
	public PrismProperty<?> fetchCurrentToken(ObjectClassComplexTypeDefinition objectClass,
			OperationResult parentResult) throws CommunicationException, GenericFrameworkException {

		OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".fetchCurrentToken");
		result.addParam("objectClass", objectClass);

		ObjectClass icfObjectClass = objectClassToIcf(objectClass);
		
		OperationResult icfResult = result.createSubresult(ConnectorFacade.class.getName() + ".sync");
		icfResult.addContext("connector", icfConnectorFacade);
		icfResult.addParam("icfObjectClass", icfObjectClass);
		
		SyncToken syncToken = null;
		try {
			syncToken = icfConnectorFacade.getLatestSyncToken(icfObjectClass);
			icfResult.recordSuccess();
			icfResult.addReturn("syncToken", syncToken==null?null:syncToken.getValue());
		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, icfResult);
			result.computeStatus();
			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof CommunicationException) {
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else {
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
		}

		if (syncToken == null) {
			result.recordWarning("Resource have not provided a current sync token");
			return null;
		}

		PrismProperty<?> property = getToken(syncToken);
		result.recordSuccess();
		return property;
	}

	@Override
	public List<Change> fetchChanges(ObjectClassComplexTypeDefinition objectClass, PrismProperty<?> lastToken,
			OperationResult parentResult) throws CommunicationException, GenericFrameworkException,
			SchemaException, ConfigurationException {

		OperationResult subresult = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".fetchChanges");
		subresult.addContext("objectClass", objectClass);
		subresult.addParam("lastToken", lastToken);

		// create sync token from the property last token
		SyncToken syncToken = null;
		try {
			syncToken = getSyncToken(lastToken);
			LOGGER.trace("Sync token created from the property last token: {}", syncToken==null?null:syncToken.getValue());
		} catch (SchemaException ex) {
			subresult.recordFatalError(ex.getMessage(), ex);
			throw new SchemaException(ex.getMessage(), ex);
		}

		final List<SyncDelta> syncDeltas = new ArrayList<SyncDelta>();
		// get icf object class
		ObjectClass icfObjectClass = objectClassToIcf(objectClass);

		SyncResultsHandler syncHandler = new SyncResultsHandler() {

			@Override
			public boolean handle(SyncDelta delta) {
				LOGGER.trace("Detected sync delta: {}", delta);
				return syncDeltas.add(delta);

			}
		};

		OperationResult icfResult = subresult.createSubresult(ConnectorFacade.class.getName() + ".sync");
		icfResult.addContext("connector", icfConnectorFacade);
		icfResult.addParam("icfObjectClass", icfObjectClass);
		icfResult.addParam("syncToken", syncToken);
		icfResult.addParam("syncHandler", syncHandler);

		try {
			icfConnectorFacade.sync(icfObjectClass, syncToken, syncHandler,
					new OperationOptionsBuilder().build());
			icfResult.recordSuccess();
			icfResult.addReturn(OperationResult.RETURN_COUNT, syncDeltas.size());
		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, icfResult);
			subresult.computeStatus();
			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof CommunicationException) {
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof SchemaException) {
				throw (SchemaException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else {
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
		}
		// convert changes from icf to midpoint Change
		List<Change> changeList = null;
		try {
			PrismSchema schema = getResourceSchema(subresult);
			changeList = getChangesFromSyncDeltas(icfObjectClass, syncDeltas, schema, subresult);
		} catch (SchemaException ex) {
			subresult.recordFatalError(ex.getMessage(), ex);
			throw new SchemaException(ex.getMessage(), ex);
		}

		subresult.recordSuccess();
		subresult.addReturn(OperationResult.RETURN_COUNT, changeList == null ? 0 : changeList.size());
		return changeList;
	}

	@Override
	public void test(OperationResult parentResult) {

		OperationResult connectionResult = parentResult
				.createSubresult(ConnectorTestOperation.CONNECTOR_CONNECTION.getOperation());
		connectionResult.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ConnectorInstance.class);
		connectionResult.addContext("connector", connectorType);

		try {
			icfConnectorFacade.test();
			connectionResult.recordSuccess();
		} catch (UnsupportedOperationException ex) {
			// Connector does not support test connection.
			connectionResult.recordStatus(OperationResultStatus.NOT_APPLICABLE,
					"Operation not supported by the connector", ex);
			// Do not rethrow. Recording the status is just OK.
		} catch (Exception icfEx) {
			Exception midPointEx = processIcfException(icfEx, connectionResult);
			connectionResult.recordFatalError(midPointEx);
		}
	}


	@Override
	public <T extends ResourceObjectShadowType> void search(ObjectClassComplexTypeDefinition objectClassDefinition, final ObjectQuery query,
			final ResultHandler<T> handler, OperationResult parentResult) throws CommunicationException,
			GenericFrameworkException, SchemaException {

		// Result type for this operation
		final OperationResult result = parentResult.createSubresult(ConnectorInstance.class.getName()
				+ ".search");
		result.addParam("objectClass", objectClassDefinition);
		result.addContext("connector", connectorType);

		if (objectClassDefinition == null) {
			result.recordFatalError("Object class not defined");
			throw new IllegalArgumentException("objectClass not defined");
		}

		ObjectClass icfObjectClass = objectClassToIcf(objectClassDefinition);
		if (icfObjectClass == null) {
			IllegalArgumentException ex = new IllegalArgumentException(
					"Unable to detemine object class from QName " + objectClassDefinition
							+ " while attempting to search objects by "
							+ ObjectTypeUtil.toShortString(connectorType));
			result.recordFatalError("Unable to detemine object class", ex);
			throw ex;
		}
		final PrismObjectDefinition<T> objectDefinition = toShadowDefinition(objectClassDefinition);


		ResultsHandler icfHandler = new ResultsHandler() {
			int count = 0;
			@Override
			public boolean handle(ConnectorObject connectorObject) {
				// Convert ICF-specific connector object to a generic
				// ResourceObject
				if (query != null && query.getPaging() != null && query.getPaging().getOffset() != null
						&& query.getPaging().getMaxSize() != null) {
					if (!(count >= query.getPaging().getOffset() && count < (query.getPaging().getOffset() + query.getPaging().getMaxSize()))) {
						count++;
						return true;
					}

				}
				PrismObject<T> resourceObject;
				try {
					resourceObject = convertToResourceObject(connectorObject, objectDefinition, false);
				} catch (SchemaException e) {
					throw new IntermediateException(e);
				}

				// .. and pass it to the handler
				boolean cont = handler.handle(resourceObject);
				if (!cont) {
					result.recordPartialError("Stopped on request from the handler");

				}
				count++;
				return cont;
			}
		};

		// Connector operation cannot create result for itself, so we need to
		// create result for it
		OperationResult icfResult = result.createSubresult(ConnectorFacade.class.getName() + ".search");
		icfResult.addParam("objectClass", icfObjectClass);
		icfResult.addContext("connector", icfConnectorFacade.getClass());

		try {

			Filter filter = null;
			if (query != null && query.getFilter() != null) {
				// TODO : translation between connector filter and midpoint
				// filter
				FilterInterpreter interpreter = new FilterInterpreter(getSchemaNamespace());
				LOGGER.trace("Start to convert filter: {}", query.getFilter().dump());
				filter = interpreter.interpret(query.getFilter());

				LOGGER.trace("ICF filter: {}", filter.toString());
			}
			icfConnectorFacade.search(icfObjectClass, filter, icfHandler, null);

			icfResult.recordSuccess();
		} catch (IntermediateException inex) {
			SchemaException ex = (SchemaException) inex.getCause();
			throw ex;
		} catch (Exception ex) {
			Exception midpointEx = processIcfException(ex, icfResult);
			result.computeStatus();
			// Do some kind of acrobatics to do proper throwing of checked
			// exception
			if (midpointEx instanceof CommunicationException) {
				throw (CommunicationException) midpointEx;
			} else if (midpointEx instanceof GenericFrameworkException) {
				throw (GenericFrameworkException) midpointEx;
			} else if (midpointEx instanceof SchemaException) {
				throw (SchemaException) midpointEx;
			} else if (midpointEx instanceof RuntimeException) {
				throw (RuntimeException) midpointEx;
			} else {
				throw new SystemException("Got unexpected exception: " + ex.getClass().getName(), ex);
			}
			// ICF interface does not specify exceptions or other error
			// conditions.
			// Therefore this kind of heavy artillery is necessary.
			// TODO maybe we can try to catch at least some specific exceptions
//			icfResult.recordFatalError(ex);
//			result.recordFatalError("ICF invocation failed");
			// This is fatal. No point in continuing.
//			throw new GenericFrameworkException(ex);
		}

		if (result.isUnknown()) {
			result.recordSuccess();
		}
	}

	// UTILITY METHODS

	private QName convertAttributeNameToQName(String icfAttrName) {
		LOGGER.trace("icf attribute: {}", icfAttrName);
		QName attrXsdName = new QName(getSchemaNamespace(), icfAttrName,
				ConnectorFactoryIcfImpl.NS_ICF_RESOURCE_INSTANCE_PREFIX);
		// Handle special cases
		if (Name.NAME.equals(icfAttrName)) {
			// this is ICF __NAME__ attribute. It will look ugly in XML and may
			// even cause problems.
			// so convert to something more friendly such as icfs:name
			attrXsdName = ConnectorFactoryIcfImpl.ICFS_NAME;
		}
		LOGGER.trace("attr xsd name: {}", attrXsdName);
		return attrXsdName;
	}
	
	private void increase(int count){
		count++;
	}

//	private String convertAttributeNameToIcf(QName attrQName, OperationResult parentResult)
//			throws SchemaException {
//		// Attribute QNames in the resource instance namespace are converted
//		// "as is"
//		if (attrQName.getNamespaceURI().equals(getSchemaNamespace())) {
//			return attrQName.getLocalPart();
//		}
//
//		// Other namespace are special cases
//
//		if (ConnectorFactoryIcfImpl.ICFS_NAME.equals(attrQName)) {
//			return Name.NAME;
//		}
//
//		if (ConnectorFactoryIcfImpl.ICFS_UID.equals(attrQName)) {
//			// UID is strictly speaking not an attribute. But it acts as an
//			// attribute e.g. in create operation. Therefore we need to map it.
//			return Uid.NAME;
//		}
//
//		// No mapping available
//
//		throw new SchemaException("No mapping from QName " + attrQName + " to an ICF attribute name");
//	}

	/**
	 * Maps ICF native objectclass name to a midPoint QName objctclass name.
	 * <p/>
	 * The mapping is "stateless" - it does not keep any mapping database or any
	 * other state. There is a bi-directional mapping algorithm.
	 * <p/>
	 * TODO: mind the special characters in the ICF objectclass names.
	 */
	private QName objectClassToQname(String icfObjectClassString) {
		if (ObjectClass.ACCOUNT_NAME.equals(icfObjectClassString)) {
			return new QName(getSchemaNamespace(), ConnectorFactoryIcfImpl.ACCOUNT_OBJECT_CLASS_LOCAL_NAME,
					ConnectorFactoryIcfImpl.NS_ICF_SCHEMA_PREFIX);
		} else if (ObjectClass.GROUP_NAME.equals(icfObjectClassString)) {
			return new QName(getSchemaNamespace(), ConnectorFactoryIcfImpl.GROUP_OBJECT_CLASS_LOCAL_NAME,
					ConnectorFactoryIcfImpl.NS_ICF_SCHEMA_PREFIX);
		} else {
			return new QName(getSchemaNamespace(), CUSTOM_OBJECTCLASS_PREFIX + icfObjectClassString
					+ CUSTOM_OBJECTCLASS_SUFFIX, ConnectorFactoryIcfImpl.NS_ICF_RESOURCE_INSTANCE_PREFIX);
		}
	}

	private ObjectClass objectClassToIcf(PrismObject<? extends ResourceObjectShadowType> object) {

		ResourceObjectShadowType shadowType = object.asObjectable();
		QName qnameObjectClass = shadowType.getObjectClass();
		if (qnameObjectClass == null) {
			ResourceAttributeContainer attrContainer = ResourceObjectShadowUtil
					.getAttributesContainer(shadowType);
			if (attrContainer == null) {
				return null;
			}
			ResourceAttributeContainerDefinition objectClassDefinition = attrContainer.getDefinition();
			qnameObjectClass = objectClassDefinition.getTypeName();
		}

		return objectClassToIcf(qnameObjectClass);
	}

	/**
	 * Maps a midPoint QName objctclass to the ICF native objectclass name.
	 * <p/>
	 * The mapping is "stateless" - it does not keep any mapping database or any
	 * other state. There is a bi-directional mapping algorithm.
	 * <p/>
	 * TODO: mind the special characters in the ICF objectclass names.
	 */
	private ObjectClass objectClassToIcf(ObjectClassComplexTypeDefinition objectClassDefinition) {
		QName qnameObjectClass = objectClassDefinition.getTypeName();
		return objectClassToIcf(qnameObjectClass);
	}

	private ObjectClass objectClassToIcf(QName qnameObjectClass) {
		if (!getSchemaNamespace().equals(qnameObjectClass.getNamespaceURI())) {
			throw new IllegalArgumentException("ObjectClass QName " + qnameObjectClass
					+ " is not in the appropriate namespace for "
					+ ObjectTypeUtil.toShortString(connectorType) + ", expected: " + getSchemaNamespace());
		}
		String lname = qnameObjectClass.getLocalPart();
		if (ConnectorFactoryIcfImpl.ACCOUNT_OBJECT_CLASS_LOCAL_NAME.equals(lname)) {
			return ObjectClass.ACCOUNT;
		} else if (ConnectorFactoryIcfImpl.GROUP_OBJECT_CLASS_LOCAL_NAME.equals(lname)) {
			return ObjectClass.GROUP;
		} else if (lname.startsWith(CUSTOM_OBJECTCLASS_PREFIX) && lname.endsWith(CUSTOM_OBJECTCLASS_SUFFIX)) {
			String icfObjectClassName = lname.substring(CUSTOM_OBJECTCLASS_PREFIX.length(), lname.length()
					- CUSTOM_OBJECTCLASS_SUFFIX.length());
			return new ObjectClass(icfObjectClassName);
		} else {
			throw new IllegalArgumentException("Cannot recognize objectclass QName " + qnameObjectClass
					+ " for " + ObjectTypeUtil.toShortString(connectorType) + ", expected: "
					+ getSchemaNamespace());
		}
	}

	/**
	 * Looks up ICF Uid identifier in a (potentially multi-valued) set of
	 * identifiers. Handy method to convert midPoint identifier style to an ICF
	 * identifier style.
	 * 
	 * @param identifiers
	 *            midPoint resource object identifiers
	 * @return ICF UID or null
	 */
	private Uid getUid(Collection<? extends ResourceAttribute> identifiers) {
		for (ResourceAttribute attr : identifiers) {
			if (attr.getName().equals(ConnectorFactoryIcfImpl.ICFS_UID)) {
				return new Uid(((ResourceAttribute<String>) attr).getValue().getValue());
			}
		}
		return null;
	}

	private ResourceAttributeDefinition getUidDefinition(ResourceAttributeContainerDefinition def) {
		return def.findAttributeDefinition(ConnectorFactoryIcfImpl.ICFS_UID);
	}

	private ResourceAttributeDefinition getUidDefinition(Collection<? extends ResourceAttribute> identifiers) {
		for (ResourceAttribute attr : identifiers) {
			if (attr.getName().equals(ConnectorFactoryIcfImpl.ICFS_UID)) {
				return attr.getDefinition();
			}
		}
		return null;
	}

	private ResourceAttribute createUidAttribute(Uid uid, ResourceAttributeDefinition uidDefinition) {
		ResourceAttribute uidRoa = uidDefinition.instantiate();
		uidRoa.setValue(new PrismPropertyValue<String>(uid.getUidValue()));
		return uidRoa;
	}

	/**
	 * Converts ICF ConnectorObject to the midPoint ResourceObject.
	 * <p/>
	 * All the attributes are mapped using the same way as they are mapped in
	 * the schema (which is actually no mapping at all now).
	 * <p/>
	 * If an optional ResourceObjectDefinition was provided, the resulting
	 * ResourceObject is schema-aware (getDefinition() method works). If no
	 * ResourceObjectDefinition was provided, the object is schema-less. TODO:
	 * this still needs to be implemented.
	 * 
	 * @param co
	 *            ICF ConnectorObject to convert
	 * @param def
	 *            ResourceObjectDefinition (from the schema) or null
	 * @param full
	 *            if true it describes if the returned resource object should
	 *            contain all of the attributes defined in the schema, if false
	 *            the returned resource object will contain only attributed with
	 *            the non-null values.
	 * @return new mapped ResourceObject instance.
	 * @throws SchemaException
	 */
	private <T extends ResourceObjectShadowType> PrismObject<T> convertToResourceObject(ConnectorObject co,
			PrismObjectDefinition<T> objectDefinition, boolean full) throws SchemaException {

		PrismObject<T> shadowPrism = null;
		if (objectDefinition != null) {
			shadowPrism = objectDefinition.instantiate();
		} else {
			throw new SchemaException("No definition");
		}

		// LOGGER.trace("Instantiated prism object {} from connector object.",
		// shadowPrism.dump());

		T shadow = shadowPrism.asObjectable();
		ResourceAttributeContainer attributesContainer = (ResourceAttributeContainer) shadowPrism
				.findOrCreateContainer(ResourceObjectShadowType.F_ATTRIBUTES);
		ResourceAttributeContainerDefinition attributesContainerDefinition = attributesContainer.getDefinition();
		shadow.setObjectClass(attributesContainerDefinition.getTypeName());

		LOGGER.trace("Resource attribute container definition {}.", attributesContainerDefinition.dump());

		// Uid is always there
		Uid uid = co.getUid();
		ResourceAttribute<?> uidRoa = createUidAttribute(uid, getUidDefinition(attributesContainerDefinition));
		attributesContainer.getValue().add(uidRoa);

		for (Attribute icfAttr : co.getAttributes()) {
			if (icfAttr.getName().equals(Uid.NAME)) {
				// UID is handled specially (see above)
				continue;
			}
			if (icfAttr.getName().equals(OperationalAttributes.PASSWORD_NAME)) {
				// password has to go to the credentials section
				if (shadow instanceof AccountShadowType) {
					AccountShadowType accountShadowType = (AccountShadowType) shadow;
					ProtectedStringType password = getSingleValue(icfAttr, ProtectedStringType.class);
					ResourceObjectShadowUtil.setPassword(accountShadowType, password);
				} else {
					throw new SchemaException("Attempt to set password for non-account object type "
							+ objectDefinition);
				}
				continue;
			}
			if (icfAttr.getName().equals(OperationalAttributes.ENABLE_NAME)) {
				if (shadow instanceof AccountShadowType) {
					AccountShadowType accountShadowType = (AccountShadowType) shadow;
					Boolean enabled = getSingleValue(icfAttr, Boolean.class);
					ActivationType activationType = ResourceObjectShadowUtil
							.getOrCreateActivation(accountShadowType);
					activationType.setEnabled(enabled);
				} else {
					throw new SchemaException(
							"Attempt to set activation/enabled for non-account object type "
									+ objectDefinition);
				}
				continue;
			}

			QName qname = convertAttributeNameToQName(icfAttr.getName());

			ResourceAttributeDefinition attributeDefinition = attributesContainerDefinition.findAttributeDefinition(qname);

			if (attributeDefinition == null) {
				throw new SchemaException("Unknown attribute "+qname+" in definition of object class "+attributesContainerDefinition.getTypeName()+". Original ICF name: "+icfAttr.getName(), qname);
			}

			ResourceAttribute resourceAttribute = attributeDefinition.instantiate(qname);

			LOGGER.trace("attribute name: " + qname);
			LOGGER.trace("attribute value: " + icfAttr.getValue());
			// if true, we need to convert whole connector object to the
			// resource object also with the null-values attributes
			if (full) {
				if (icfAttr.getValue() != null) {
					// Convert the values. While most values do not need
					// conversions, some
					// of them may need it (e.g. GuardedString)
					for (Object icfValue : icfAttr.getValue()) {
						Object value = convertValueFromIcf(icfValue, qname);
						resourceAttribute.add(new PrismPropertyValue<Object>(value));
					}
				}

				attributesContainer.getValue().add(resourceAttribute);

				// in this case when false, we need only the attributes with the
				// non-null values.
			} else {
				if (icfAttr.getValue() != null && !icfAttr.getValue().isEmpty()) {
					// Convert the values. While most values do not need
					// conversions, some
					// of them may need it (e.g. GuardedString)
					boolean empty = true;
					for (Object icfValue : icfAttr.getValue()) {
						if (icfValue != null) {
							Object value = convertValueFromIcf(icfValue, qname);
							empty = false;
							resourceAttribute.add(new PrismPropertyValue<Object>(value));
						}
					}

					if (!empty) {
						attributesContainer.getValue().add(resourceAttribute);
					}

				}
			}

		}

		return shadowPrism;
	}

	private <T> T getSingleValue(Attribute icfAttr, Class<T> type) throws SchemaException {
		List<Object> values = icfAttr.getValue();
		if (values != null && !values.isEmpty()) {
			if (values.size() > 1) {
				throw new SchemaException("Expected single value for " + icfAttr.getName());
			}
			Object val = convertValueFromIcf(values.get(0), null);
			if (type.isAssignableFrom(val.getClass())) {
				return (T) val;
			} else {
				throw new SchemaException("Expected type " + type.getName() + " for " + icfAttr.getName()
						+ " but got " + val.getClass().getName());
			}
		} else {
			throw new SchemaException("Empty value for " + icfAttr.getName());
		}

	}

	private Set<Attribute> convertFromResourceObject(ResourceAttributeContainer attributesPrism,
			OperationResult parentResult) throws SchemaException {
		Collection<ResourceAttribute<?>> resourceAttributes = attributesPrism.getAttributes();
		return convertFromResourceObject(resourceAttributes, parentResult);
	}

	private Set<Attribute> convertFromResourceObject(Collection<ResourceAttribute<?>> resourceAttributes,
			OperationResult parentResult) throws SchemaException {

		Set<Attribute> attributes = new HashSet<Attribute>();
		if (resourceAttributes == null) {
			// returning empty set
			return attributes;
		}

		for (ResourceAttribute<?> attribute : resourceAttributes) {

			String attrName = UcfUtil.convertAttributeNameToIcf(attribute.getName(), getSchemaNamespace(), parentResult);

			Set<Object> convertedAttributeValues = new HashSet<Object>();
			for (PrismPropertyValue<?> value : attribute.getValues()) {
				convertedAttributeValues.add(UcfUtil.convertValueToIcf(value, protector, attribute.getName()));
			}

			Attribute connectorAttribute = AttributeBuilder.build(attrName, convertedAttributeValues);

			attributes.add(connectorAttribute);
		}
		return attributes;
	}

//	private Object convertValueToIcf(Object value, QName propName) throws SchemaException {
//		if (value == null) {
//			return null;
//		}
//
//		if (value instanceof PrismPropertyValue) {
//			return convertValueToIcf(((PrismPropertyValue) value).getValue(), propName);
//		}
//
//		if (value instanceof ProtectedStringType) {
//			ProtectedStringType ps = (ProtectedStringType) value;
//			return toGuardedString(ps, propName.toString());
//		}
//		return value;
//	}

	private Object convertValueFromIcf(Object icfValue, QName propName) {
		if (icfValue == null) {
			return null;
		}
		if (icfValue instanceof GuardedString) {
			return fromGuardedString((GuardedString) icfValue);
		}
		return icfValue;
	}

	private void convertFromActivation(Set<Attribute> updateAttributes,
			Collection<PropertyDelta> activationDeltas) throws SchemaException {

		for (PropertyDelta propDelta : activationDeltas) {
			if (propDelta.getName().equals(ActivationType.F_ENABLED)) {
				// Not entirely correct, TODO: refactor later
				updateAttributes.add(AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, propDelta
						.getPropertyNew().getValue(Boolean.class).getValue()));
			} else {
				throw new SchemaException("Got unknown activation attribute delta " + propDelta.getName());
			}
		}

	}

	private void convertFromPassword(Set<Attribute> attributes, PropertyDelta<ProtectedStringType> passwordDelta) throws SchemaException {
		if (passwordDelta == null) {
			throw new IllegalArgumentException("No password was provided");
		}

		if (passwordDelta.getName().equals(PasswordType.F_VALUE)) {
			GuardedString guardedPassword = toGuardedString(passwordDelta
					.getPropertyNew().getValue().getValue(), "new password");
			attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, guardedPassword));
		}

	}

	private List<Change> getChangesFromSyncDeltas(ObjectClass objClass, Collection<SyncDelta> icfDeltas, PrismSchema schema,
			OperationResult parentResult) throws SchemaException, GenericFrameworkException {
		List<Change> changeList = new ArrayList<Change>();

		Validate.notNull(icfDeltas, "Sync result must not be null.");
		for (SyncDelta icfDelta : icfDeltas) {

			if (icfDelta.getObject() != null){
				objClass = icfDelta.getObject().getObjectClass();
			}
				QName objectClass = objectClassToQname(objClass.getObjectClassValue());
				ObjectClassComplexTypeDefinition objClassDefinition = (ObjectClassComplexTypeDefinition) schema
				.findComplexTypeDefinition(objectClass);
				
			
			// we don't want resource container deifinition, instead we need the
			// objectClassDef
			// ResourceAttributeContainerDefinition objectClassDefinition =
			// (ResourceAttributeContainerDefinition) schema
			// .findContainerDefinitionByType(objectClass);
			
			// ResourceAttributeContainerDefinition resourceAttributeDef =
			// (ResourceAttributeContainerDefinition) objClassDefinition
			// .findContainerDefinition(ResourceObjectShadowType.F_ATTRIBUTES);
			// FIXME: we are hadcoding Account here, but we should not
			

			

			if (SyncDeltaType.DELETE.equals(icfDelta.getDeltaType())) {
				LOGGER.debug("START creating delta of type DELETE");
				ObjectDelta<ResourceObjectShadowType> objectDelta = new ObjectDelta<ResourceObjectShadowType>(
						ResourceObjectShadowType.class, ChangeType.DELETE, prismContext);
				ResourceAttribute uidAttribute = createUidAttribute(
						icfDelta.getUid(),
						getUidDefinition(objClassDefinition
								.toResourceAttributeContainerDefinition(ResourceObjectShadowType.F_ATTRIBUTES)));
				Collection<ResourceAttribute<?>> identifiers = new ArrayList<ResourceAttribute<?>>(1);
				identifiers.add(uidAttribute);
				Change change = new Change(identifiers, objectDelta, getToken(icfDelta.getToken()));
				change.setObjectClassDefinition(objClassDefinition);
				changeList.add(change);
				LOGGER.debug("END creating delta of type DELETE");

			} else if (SyncDeltaType.CREATE_OR_UPDATE.equals(icfDelta.getDeltaType())) {
				PrismObjectDefinition<AccountShadowType> objectDefinition = toShadowDefinition(objClassDefinition);
				LOGGER.trace("Object definition: {}", objectDefinition);
				
				LOGGER.debug("START creating delta of type CREATE_OR_UPDATE");
				PrismObject<AccountShadowType> currentShadow = convertToResourceObject(icfDelta.getObject(),
						objectDefinition, false);

				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Got current shadow: {}", currentShadow.dump());
				}

				Collection<ResourceAttribute<?>> identifiers = ResourceObjectShadowUtil.getIdentifiers(currentShadow);

				Change change = new Change(identifiers, currentShadow, getToken(icfDelta.getToken()));
				change.setObjectClassDefinition(objClassDefinition);
				changeList.add(change);
				LOGGER.debug("END creating delta of type CREATE_OR_UPDATE");

			} else {
				throw new GenericFrameworkException("Unexpected sync delta type " + icfDelta.getDeltaType());
			}

		}
		return changeList;
	}

	private Element getModificationPath(Document doc) {
		List<XPathSegment> segments = new ArrayList<XPathSegment>();
		XPathSegment attrSegment = new XPathSegment(SchemaConstants.I_ATTRIBUTES);
		segments.add(attrSegment);
		XPathHolder t = new XPathHolder(segments);
		Element xpathElement = t.toElement(SchemaConstants.I_PROPERTY_CONTAINER_REFERENCE_PATH, doc);
		return xpathElement;
	}

	private SyncToken getSyncToken(PrismProperty tokenProperty) throws SchemaException {
		if (tokenProperty == null){
			return null;
		}
		if (tokenProperty.getValue() == null) {
			return null;
		}
		Object tokenValue = tokenProperty.getValue().getValue();
		if (tokenValue == null) {
			return null;
		}
		SyncToken syncToken = new SyncToken(tokenValue);
		return syncToken;
	}

	private PrismProperty<?> getToken(SyncToken syncToken) {
		Object object = syncToken.getValue();
		return createTokenProperty(object);
	}

	private <T> PrismProperty<T> createTokenProperty(T object) {
		QName type = XsdTypeMapper.toXsdType(object.getClass());

		Set<PrismPropertyValue<T>> syncTokenValues = new HashSet<PrismPropertyValue<T>>();
		syncTokenValues.add(new PrismPropertyValue<T>(object));
		PrismPropertyDefinition propDef = new PrismPropertyDefinition(SchemaConstants.SYNC_TOKEN,
				SchemaConstants.SYNC_TOKEN, type, prismContext);
		propDef.setDynamic(true);
		PrismProperty<T> property = propDef.instantiate();
		property.addValues(syncTokenValues);
		return property;
	}

	/**
	 * check additional operation order, according to the order are scrip
	 * executed before or after operation..
	 * 
	 * @param additionalOperations
	 * @param order
	 */
	private void checkAndExecuteAdditionalOperation(Collection<Operation> additionalOperations, ProvisioningScriptOrderType order) {

		if (additionalOperations == null) {
			// TODO: add warning to the result
			return;
		}

		for (Operation op : additionalOperations) {
			if (op instanceof ExecuteProvisioningScriptOperation) {

				ExecuteProvisioningScriptOperation executeOp = (ExecuteProvisioningScriptOperation) op;
				LOGGER.trace("Find execute script operation: {}", SchemaDebugUtil.prettyPrint(executeOp));
				// execute operation in the right order..
				if (order.equals(executeOp.getScriptOrder())) {
					executeScript(executeOp);
				}
			}
		}

	}

	private void executeScript(ExecuteProvisioningScriptOperation executeOp) {

		// convert execute script operation to the script context required from
		// the connector
		ScriptContext scriptContext = convertToScriptContext(executeOp);
		// check if the script should be executed on the connector or the
		// resoruce...
		if (executeOp.isConnectorHost()) {
			LOGGER.debug("Start running script on connector.");
			icfConnectorFacade.runScriptOnConnector(scriptContext, new OperationOptionsBuilder().build());
			LOGGER.debug("Finish running script on connector.");
		}
		if (executeOp.isResourceHost()) {
			LOGGER.debug("Start running script on resource.");
			icfConnectorFacade.runScriptOnResource(scriptContext, new OperationOptionsBuilder().build());
			LOGGER.debug("Finish running script on resource.");
		}

	}

	private ScriptContext convertToScriptContext(ExecuteProvisioningScriptOperation executeOp) {
		// creating script arguments map form the execute script operation
		// arguments
		Map<String, Object> scriptArguments = new HashMap<String, Object>();
		for (ExecuteScriptArgument argument : executeOp.getArgument()) {
			scriptArguments.put(argument.getArgumentName(), argument.getArgumentValue());
		}
		ScriptContext scriptContext = new ScriptContext(executeOp.getLanguage(), executeOp.getTextCode(),
				scriptArguments);
		return scriptContext;
	}

	/**
	 * Transforms midPoint XML configuration of the connector to the ICF
	 * configuration.
	 * <p/>
	 * The "configuration" part of the XML resource definition will be used.
	 * <p/>
	 * The provided ICF APIConfiguration will be modified, some values may be
	 * overwritten.
	 * 
	 * @param apiConfig
	 *            ICF connector configuration
	 * @param resourceType
	 *            midPoint XML configuration
	 * @throws SchemaException
	 * @throws ConfigurationException
	 */
	private void transformConnectorConfiguration(APIConfiguration apiConfig, PrismContainerValue configuration)
			throws SchemaException, ConfigurationException {

		ConfigurationProperties configProps = apiConfig.getConfigurationProperties();

		// The namespace of all the configuration properties specific to the
		// connector instance will have a connector instance namespace. This
		// namespace can be found in the resource definition.
		String connectorConfNs = connectorType.getNamespace();

		PrismContainer configurationPropertiesContainer = configuration
				.findContainer(ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_ELEMENT_QNAME);
		if (configurationPropertiesContainer == null) {
			// Also try this. This is an older way.
			configurationPropertiesContainer = configuration.findContainer(new QName(connectorConfNs,
					ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_ELEMENT_LOCAL_NAME));
		}

		int numConfingProperties = transformConnectorConfiguration(configProps,
				configurationPropertiesContainer, connectorConfNs);

		PrismContainer connectorPoolContainer = configuration.findContainer(new QName(
				ConnectorFactoryIcfImpl.NS_ICF_CONFIGURATION,
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_XML_ELEMENT_NAME));
		ObjectPoolConfiguration connectorPoolConfiguration = apiConfig.getConnectorPoolConfiguration();
		transformConnectorPoolConfiguration(connectorPoolConfiguration, connectorPoolContainer);

		PrismProperty producerBufferSizeProperty = configuration.findProperty(new QName(
				ConnectorFactoryIcfImpl.NS_ICF_CONFIGURATION,
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_PRODUCER_BUFFER_SIZE_XML_ELEMENT_NAME));
		if (producerBufferSizeProperty != null) {
			apiConfig.setProducerBufferSize(parseInt(producerBufferSizeProperty));
		}

		PrismContainer connectorTimeoutsContainer = configuration.findContainer(new QName(
				ConnectorFactoryIcfImpl.NS_ICF_CONFIGURATION,
				ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_TIMEOUTS_XML_ELEMENT_NAME));
		transformConnectorTimeoutsConfiguration(apiConfig, connectorTimeoutsContainer);

		if (numConfingProperties == 0) {
			throw new SchemaException("No configuration properties found. Wrong namespace? (expected: "
					+ connectorConfNs + ")");
		}

	}

	private int transformConnectorConfiguration(ConfigurationProperties configProps,
			PrismContainer<?> configurationPropertiesContainer, String connectorConfNs)
			throws ConfigurationException {

		int numConfingProperties = 0;

		if (configurationPropertiesContainer == null || configurationPropertiesContainer.getValue() == null) {
			LOGGER.warn("No configuration properties in connectorType.getOid()");
			return numConfingProperties;
		}

		for (PrismProperty prismProperty : configurationPropertiesContainer.getValue().getProperties()) {
			QName propertyQName = prismProperty.getName();

			// All the elements must be in a connector instance
			// namespace.
			if (propertyQName.getNamespaceURI() == null
					|| !propertyQName.getNamespaceURI().equals(connectorConfNs)) {
				LOGGER.warn("Found element with a wrong namespace ({}) in connector OID={}",
						propertyQName.getNamespaceURI(), connectorType.getOid());
			} else {

				numConfingProperties++;

				// Local name of the element is the same as the name
				// of ICF configuration property
				String propertyName = propertyQName.getLocalPart();
				ConfigurationProperty property = configProps.getProperty(propertyName);
				
				if (property == null) {
					throw new ConfigurationException("Unknown configuration property "+propertyName);
				}

				// Check (java) type of ICF configuration property,
				// behave accordingly
				Class<?> type = property.getType();
				if (type.isArray()) {
					property.setValue(convertToIcfArray(prismProperty, type.getComponentType()));
					// property.setValue(prismProperty.getRealValuesArray(type.getComponentType()));
				} else {
					// Single-valued property are easy to convert
					property.setValue(convertToIcfSingle(prismProperty, type));
					// property.setValue(prismProperty.getRealValue(type));
				}
			}
		}
		return numConfingProperties;
	}

	private void transformConnectorPoolConfiguration(ObjectPoolConfiguration connectorPoolConfiguration,
			PrismContainer<?> connectorPoolContainer) throws SchemaException {

		if (connectorPoolContainer == null || connectorPoolContainer.getValue() == null) {
			return;
		}

		for (PrismProperty prismProperty : connectorPoolContainer.getValue().getProperties()) {
			QName propertyQName = prismProperty.getName();
			if (propertyQName.getNamespaceURI().equals(ConnectorFactoryIcfImpl.NS_ICF_CONFIGURATION)) {
				String subelementName = propertyQName.getLocalPart();
				if (ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MIN_EVICTABLE_IDLE_TIME_MILLIS
						.equals(subelementName)) {
					connectorPoolConfiguration.setMinEvictableIdleTimeMillis(parseLong(prismProperty));
				} else if (ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MIN_IDLE
						.equals(subelementName)) {
					connectorPoolConfiguration.setMinIdle(parseInt(prismProperty));
				} else if (ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MAX_IDLE
						.equals(subelementName)) {
					connectorPoolConfiguration.setMaxIdle(parseInt(prismProperty));
				} else if (ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MAX_OBJECTS
						.equals(subelementName)) {
					connectorPoolConfiguration.setMaxObjects(parseInt(prismProperty));
				} else if (ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_MAX_WAIT
						.equals(subelementName)) {
					connectorPoolConfiguration.setMaxWait(parseLong(prismProperty));
				} else {
					throw new SchemaException(
							"Unexpected element "
									+ propertyQName
									+ " in "
									+ ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_XML_ELEMENT_NAME);
				}
			} else {
				throw new SchemaException(
						"Unexpected element "
								+ propertyQName
								+ " in "
								+ ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_CONNECTOR_POOL_CONFIGURATION_XML_ELEMENT_NAME);
			}
		}
	}

	private void transformConnectorTimeoutsConfiguration(APIConfiguration apiConfig,
			PrismContainer<?> connectorTimeoutsContainer) throws SchemaException {

		if (connectorTimeoutsContainer == null || connectorTimeoutsContainer.getValue() == null) {
			return;
		}

		for (PrismProperty prismProperty : connectorTimeoutsContainer.getValue().getProperties()) {
			QName propertQName = prismProperty.getName();

			if (ConnectorFactoryIcfImpl.NS_ICF_CONFIGURATION.equals(propertQName.getNamespaceURI())) {
				String opName = propertQName.getLocalPart();
				Class<? extends APIOperation> apiOpClass = ConnectorFactoryIcfImpl.resolveApiOpClass(opName);
				if (apiOpClass != null) {
					apiConfig.setTimeout(apiOpClass, parseInt(prismProperty));
				} else {
					throw new SchemaException("Unknown operation name " + opName + " in "
							+ ConnectorFactoryIcfImpl.CONNECTOR_SCHEMA_TIMEOUTS_XML_ELEMENT_NAME);
				}
			}
		}
	}

	private int parseInt(PrismProperty<?> prop) {
		return prop.getRealValue(Integer.class);
	}

	private long parseLong(PrismProperty<?> prop) {
		Object realValue = prop.getRealValue();
		if (realValue instanceof Long) {
			return (Long) realValue;
		} else if (realValue instanceof Integer) {
			return ((Integer) realValue);
		} else {
			throw new IllegalArgumentException("Cannot convert " + realValue.getClass() + " to long");
		}
	}

	private Object convertToIcfSingle(PrismProperty<?> configProperty, Class<?> expectedType)
			throws ConfigurationException {
		if (configProperty == null) {
			return null;
		}
		PrismPropertyValue<?> pval = configProperty.getValue();
		return convertToIcf(pval, expectedType);
	}

	private Object[] convertToIcfArray(PrismProperty prismProperty, Class<?> componentType)
			throws ConfigurationException {
		List<PrismPropertyValue> values = prismProperty.getValues();
		Object valuesArrary = Array.newInstance(componentType, values.size());
		for (int j = 0; j < values.size(); ++j) {
			Object icfValue = convertToIcf(values.get(j), componentType);
			Array.set(valuesArrary, j, icfValue);
		}
		return (Object[]) valuesArrary;
	}

	private Object convertToIcf(PrismPropertyValue<?> pval, Class<?> expectedType) throws ConfigurationException {
		Object midPointRealValue = pval.getValue();
		if (expectedType.equals(GuardedString.class)) {
			// Guarded string is a special ICF beast
			// The value must be ProtectedStringType
			if (midPointRealValue instanceof ProtectedStringType) {
				ProtectedStringType ps = (ProtectedStringType) pval.getValue();
				return toGuardedString(ps, pval.getParent().getName().getLocalPart());
			} else {
				throw new ConfigurationException(
						"Expected protected string as value of configuration property "
								+ pval.getParent().getName().getLocalPart() + " but got "
								+ midPointRealValue.getClass());
			}

		} else if (expectedType.equals(GuardedByteArray.class)) {
			// Guarded string is a special ICF beast
			// TODO
			return new GuardedByteArray(Base64.decodeBase64((String) pval.getValue()));
		} else if (midPointRealValue instanceof PolyString) {
			return ((PolyString)midPointRealValue).getOrig();
		} else if (midPointRealValue instanceof PolyStringType) {
			return ((PolyStringType)midPointRealValue).getOrig();
		} else if (expectedType.equals(File.class) && midPointRealValue instanceof String) {
			return new File((String)midPointRealValue);
		} else if (expectedType.equals(String.class) && midPointRealValue instanceof ProtectedStringType) {
			try {
				return protector.decryptString((ProtectedStringType)midPointRealValue);
			} catch (EncryptionException e) {
				throw new ConfigurationException(e);
			}
		} else {
			return midPointRealValue;
		}
	}

	private GuardedString toGuardedString(ProtectedStringType ps, String propertyName) {
		if (ps == null) {
			return null;
		}
		if (!protector.isEncrypted(ps)) {
			if (ps.getClearValue() == null) {
				return null;
			}
			LOGGER.warn("Using cleartext value for {}", propertyName);
			return new GuardedString(ps.getClearValue().toCharArray());
		}
		try {
			return new GuardedString(protector.decryptString(ps).toCharArray());
		} catch (EncryptionException e) {
			LOGGER.error("Unable to decrypt value of element {}: {}",
					new Object[] { propertyName, e.getMessage(), e });
			throw new SystemException("Unable to dectypt value of element " + propertyName + ": "
					+ e.getMessage(), e);
		}
	}

	private ProtectedStringType fromGuardedString(GuardedString icfValue) {
		final ProtectedStringType ps = new ProtectedStringType();
		icfValue.access(new GuardedString.Accessor() {
			@Override
			public void access(char[] passwordChars) {
				try {
					ps.setClearValue(new String(passwordChars));
					protector.encrypt(ps);
				} catch (EncryptionException e) {
					throw new IllegalStateException("Protector failed to encrypt password");
				}
			}
		});
		return ps;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ConnectorInstanceIcfImpl(" + ObjectTypeUtil.toShortString(connectorType) + ")";
	}

}
