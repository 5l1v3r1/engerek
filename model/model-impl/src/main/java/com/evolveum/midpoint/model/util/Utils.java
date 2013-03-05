/*
 * Copyright (c) 2012 Evolveum
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
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.model.util;

import java.util.List;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.common.crypto.EncryptionException;
import com.evolveum.midpoint.common.crypto.Protector;
import com.evolveum.midpoint.model.importer.ObjectImporter;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.Itemable;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.Visitable;
import com.evolveum.midpoint.prism.Visitor;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.Handler;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ProtectedStringType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;

import org.apache.commons.lang.Validate;

/**
 * @author lazyman
 */
public final class Utils {

    private Utils() {
    }

    public static void resolveResource(ResourceObjectShadowType shadow, ProvisioningService provisioning,
            OperationResult result) throws CommunicationException, SchemaException, ObjectNotFoundException, ConfigurationException, 
            SecurityViolationException {

        Validate.notNull(shadow, "Resource object shadow must not be null.");
        Validate.notNull(provisioning, "Provisioning service must not be null.");

        ResourceType resource = getResource(shadow, provisioning, result);
        shadow.setResourceRef(null);
        shadow.setResource(resource);
    }

    public static ResourceType getResource(ResourceObjectShadowType shadow, ProvisioningService provisioning,
            OperationResult result) throws CommunicationException, SchemaException, ObjectNotFoundException, ConfigurationException, 
            SecurityViolationException {

        if (shadow.getResource() != null) {
            return shadow.getResource();
        }

        if (shadow.getResourceRef() == null) {
            throw new IllegalArgumentException("Couldn't resolve resource. Resource object shadow doesn't" +
                    " contain resource nor resource ref.");
        }

        ObjectReferenceType resourceRef = shadow.getResourceRef();
        return provisioning.getObject(ResourceType.class, resourceRef.getOid(), null, result).asObjectable();
    }
    
    public static <T extends ObjectType> void encryptValues(final Protector protector, final PrismObject<T> object, OperationResult objectResult){
        final OperationResult result = objectResult.createSubresult(ObjectImporter.class.getName() + ".encryptValues");
        Visitor visitor = new Visitor() {
			@Override
			public void visit(Visitable visitable){
				if (!(visitable instanceof PrismPropertyValue)) {
					return;
				}
				PrismPropertyValue pval = (PrismPropertyValue)visitable;
				encryptValue(protector, pval, result);
			}
		};
		object.accept(visitor);
        result.recordSuccessIfUnknown();
    }
    
    public static <T extends ObjectType> void encryptValues(final Protector protector, final ObjectDelta<T> delta, OperationResult objectResult){
        final OperationResult result = objectResult.createSubresult(ObjectImporter.class.getName() + ".encryptValues");
        Visitor visitor = new Visitor() {
			@Override
			public void visit(Visitable visitable){
				if (!(visitable instanceof PrismPropertyValue)) {
					return;
				}
				PrismPropertyValue pval = (PrismPropertyValue)visitable;
				encryptValue(protector, pval, result);
			}
		};
		delta.accept(visitor);
        result.recordSuccessIfUnknown();
    }
    
    public static <T extends ObjectType> void encryptValue(Protector protector, PrismPropertyValue pval, OperationResult result){
    	Itemable item = pval.getParent();
    	if (item == null) {
    		return;
    	}
    	ItemDefinition itemDef = item.getDefinition();
    	if (itemDef == null || itemDef.getTypeName() == null) {
    		return;
    	}
    	if (!itemDef.getTypeName().equals(ProtectedStringType.COMPLEX_TYPE)) {
    		return;
    	}
    	QName propName = item.getName();
    	PrismPropertyValue<ProtectedStringType> psPval = (PrismPropertyValue<ProtectedStringType>)pval;
    	ProtectedStringType ps = psPval.getValue();
    	if (ps.getClearValue() != null) {
            try {
//                LOGGER.info("Encrypting cleartext value for field " + propName + " while importing " + object);
                protector.encrypt(ps);
            } catch (EncryptionException e) {
//                LOGGER.info("Faild to encrypt cleartext value for field " + propName + " while importing " + object);
                result.recordFatalError("Failed to encrypt value for field " + propName + ": " + e.getMessage(), e);
                return;
            }
        }
    	
    	checkItemAferEncrypt(psPval, item);
    }

	private static void checkItemAferEncrypt(PrismPropertyValue pval, Itemable item) {
		if (pval.getParent() == null){
			pval.setParent(item);
		}
	}
	
	public static <T extends ObjectType> void searchIterative(RepositoryService repositoryService, Class<T> type, ObjectQuery query, 
			Handler<PrismObject<T>> handler, int blockSize, OperationResult opResult) throws SchemaException {
		ObjectQuery myQuery = query.clone();
		// TODO: better handle original values in paging
		ObjectPaging myPaging = ObjectPaging.createPaging(0, blockSize);
		myQuery.setPaging(myPaging);
		boolean cont = true;
		while (cont) {
			List<PrismObject<T>> objects = repositoryService.searchObjects(type, myQuery, opResult);
			for (PrismObject<T> object: objects) {
				handler.handle(object);
			}
			cont = objects.size() == blockSize;
			myPaging.setOffset(myPaging.getOffset() + blockSize);
		}
	}
}
