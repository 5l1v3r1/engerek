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

package com.evolveum.midpoint.model.lens.projector;

import static com.evolveum.midpoint.common.CompiletimeConfig.CONSISTENCY_CHECKS;

import com.evolveum.midpoint.common.mapping.Mapping;
import com.evolveum.midpoint.common.refinery.RefinedAccountDefinition;
import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.ResourceShadowDiscriminator;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.model.lens.AccountConstruction;
import com.evolveum.midpoint.model.lens.LensContext;
import com.evolveum.midpoint.model.lens.LensProjectionContext;
import com.evolveum.midpoint.model.lens.ItemValueWithOrigin;
import com.evolveum.midpoint.model.lens.LensUtil;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.MappingStrengthType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * This processor consolidate delta set triples acquired from account sync context and transforms them to
 * property deltas. It converts mappings to deltas considering exclusions, authoritativeness and strength of individual
 * mappings. It also (somehow indirectly) merges all the mappings together. It considers also property deltas from sync,
 * which already happened.
 *
 * @author Radovan Semancik
 * @author lazyman
 */
@Component
public class ConsolidationProcessor {

    public static final String PROCESS_CONSOLIDATION = ConsolidationProcessor.class.getName() + ".consolidateValues";
    private static final Trace LOGGER = TraceManager.getTrace(ConsolidationProcessor.class);
    
    @Autowired(required = true)
    private ProvisioningService provisioningService;
    
    @Autowired(required=true)
    PrismContext prismContext;

    /**
     * Converts delta set triples to a secondary account deltas.
     */
    void consolidateValues(LensContext<UserType,AccountShadowType> context, LensProjectionContext<AccountShadowType> accCtx, 
    		OperationResult result) 
    				throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException,
    				ConfigurationException, SecurityViolationException, PolicyViolationException {
    		//todo filter changes which were already in account sync delta

        //account was deleted, no changes are needed.
        if (wasAccountDeleted(accCtx)) {
            dropAllAccountDelta(accCtx);
            return;
        }

        SynchronizationPolicyDecision policyDecision = accCtx.getSynchronizationPolicyDecision();

        if (CONSISTENCY_CHECKS) context.checkConsistence();
        if (policyDecision == SynchronizationPolicyDecision.DELETE) {
            // Nothing to do
        } else {
            // This is ADD, KEEP, UNLINK or null. All are in fact the same as KEEP
            consolidateValuesModifyAccount(context, accCtx, result);
            if (CONSISTENCY_CHECKS) context.checkConsistence();
        }
        if (CONSISTENCY_CHECKS) context.checkConsistence();
    }

    private void dropAllAccountDelta(LensProjectionContext<AccountShadowType> accContext) {
        accContext.setPrimaryDelta(null);
        accContext.setSecondaryDelta(null);
    }

    private boolean wasAccountDeleted(LensProjectionContext<AccountShadowType> accContext) {
        ObjectDelta<AccountShadowType> delta = accContext.getSyncDelta();
        if (delta != null && ChangeType.DELETE.equals(delta.getChangeType())) {
            return true;
        }

        return false;
    }

    private ObjectDelta<AccountShadowType> consolidateValuesToModifyDelta(LensContext<UserType,AccountShadowType> context,
    		LensProjectionContext<AccountShadowType> accCtx, boolean addUnchangedValues, OperationResult result) 
            		throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException,
            		ConfigurationException, SecurityViolationException, PolicyViolationException {

    	// "Squeeze" all the relevant mappings into a data structure that we can process conveniently. We want to have all the
    	// (meta)data about relevant for a specific attribute in one data structure, not spread over several account constructions.
    	Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedAttributes = sqeezeAttributes(accCtx); 
    	accCtx.setSqueezedAttributes(squeezedAttributes);
        
        ResourceShadowDiscriminator rat = accCtx.getResourceShadowDiscriminator();
        ObjectDelta<AccountShadowType> objectDelta = new ObjectDelta<AccountShadowType>(AccountShadowType.class, ChangeType.MODIFY, prismContext);
        objectDelta.setOid(accCtx.getOid());

        RefinedAccountDefinition rAccount = accCtx.getRefinedAccountDefinition();
        if (rAccount == null) {
            LOGGER.error("Definition for account type {} not found in the context, but it should be there, dumping context:\n{}", rat, context.dump());
            throw new IllegalStateException("Definition for account type " + rat + " not found in the context, but it should be there");
        }
        
        if (!accCtx.isFullShadow() && !accCtx.isAdd() && !accCtx.isDelete() && hasWeakMapping(squeezedAttributes)) {
        	// There is at least one weak mapping and the full account was not yet loaded. This will cause problems as
        	// the weak mapping may be applied even though it should not be applied. Therefore load the account now.
        	LOGGER.trace("Loading full account {} because there are weak mappings", accCtx);
        	PrismObject<AccountShadowType> objectOld = provisioningService.getObject(
        			AccountShadowType.class, accCtx.getOid(), null, result);
        	accCtx.setObjectOld(objectOld);
        	accCtx.setFullShadow(true);
        	accCtx.recompute();
        	if (LOGGER.isTraceEnabled()) {
        		LOGGER.trace("Loaded full account:\n{}", accCtx.dump());
        	}
        }
        
        ObjectDelta<AccountShadowType> existingDelta = accCtx.getDelta();

        // Iterate and process each attribute separately. Now that we have squeezed the data we can process each attribute just
        // with the data in ItemValueWithOrigin triples.
        for (Map.Entry<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> entry : squeezedAttributes.entrySet()) {
            QName attributeName = entry.getKey();
            ItemPath attributePath = new ItemPath(AccountShadowType.F_ATTRIBUTES, attributeName);
            DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>> triple = entry.getValue();

            LOGGER.trace("Consolidating account {}, attribute {}", rat, attributeName);
                        
            RefinedAttributeDefinition attributeDefinition = rAccount.findAttributeDefinition(attributeName);
            
            boolean forceAddUnchangedValues = false;
            PropertyDelta<?> existingAttributeDelta = null;
            if (existingDelta != null ) {
            	existingAttributeDelta = existingDelta.findPropertyDelta(attributePath);
            }
            if (existingAttributeDelta != null && existingAttributeDelta.isReplace()) {
            	// We need to add all values if there is replace delta. Otherwise the zero-set values will be
            	// lost
            	forceAddUnchangedValues = true;
            }
            
            // Use this common utility method to do the computation. It does most of the work.
			PropertyDelta<?> propDelta = (PropertyDelta<?>) LensUtil.consolidateTripleToDelta(
					attributePath, (DeltaSetTriple)triple, attributeDefinition, existingAttributeDelta, accCtx.getObjectNew(), 
            		addUnchangedValues || forceAddUnchangedValues, false, "account " + rat);
			
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Consolidated delta (before sync filter) for {}:\n{}",rat,propDelta==null?"null":propDelta.dump());
			}
            
			if (existingAttributeDelta != null && existingAttributeDelta.isReplace()) {
				// We cannot filter out any values if there is an replace delta. The replace delta cleans all previous
				// state and all the values needs to be passed on
				LOGGER.trace("Skipping consolidaiton with sync delta as there was a replace delta on top of that already");
			} else {
				// Also consider a synchronization delta (if it is present). This may filter out some deltas.
	            propDelta = consolidateWithSync(accCtx, propDelta);
	            if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Consolidated delta (after sync filter) for {}:\n{}",rat,propDelta==null?"null":propDelta.dump());
				}
			}

            if (propDelta != null && !propDelta.isEmpty()) {
            	if (existingAttributeDelta == null || !existingAttributeDelta.isReplace()) {
            		// We cannot simplify if there is already a replace delta. This might result in
            		// two replace deltas and therefore some information may be lost
            		propDelta.simplify();
            	}
            	
            	// Validate the delta. i.e. make sure it conforms to schema (that it does not have more values than allowed, etc.)
            	if (existingAttributeDelta != null) {
            		// Let's make sure that both the previous delta and this delta makes sense
            		PropertyDelta<?> mergedDelta = existingAttributeDelta.clone();
            		mergedDelta.merge((PropertyDelta)propDelta);
            		mergedDelta.validate();
            	} else {
            		propDelta.validate();            		
            	}
                objectDelta.addModification(propDelta);
            }
            
        }

        return objectDelta;
    }

	private boolean hasWeakMapping(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedAttributes) {
		for (Map.Entry<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> entry : squeezedAttributes.entrySet()) {
			DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>> ivwoTriple = entry.getValue();
			for (ItemValueWithOrigin<? extends PrismPropertyValue<?>> ivwo: ivwoTriple.getAllValues()) {
				if (ivwo.getMapping().getStrength() == MappingStrengthType.WEAK) {
					return true;
				}
			}
		}
		return false;
	}

    private void consolidateValuesModifyAccount(LensContext<UserType,AccountShadowType> context, 
    		LensProjectionContext<AccountShadowType> accCtx, OperationResult result) 
    				throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, 
    				CommunicationException, ConfigurationException, SecurityViolationException, PolicyViolationException {

        boolean addUnchangedValues = false;
        if (accCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.ADD) {
        	addUnchangedValues = true;
        }
        
		ObjectDelta<AccountShadowType> modifyDelta = consolidateValuesToModifyDelta(context, accCtx, addUnchangedValues, result);
        if (modifyDelta == null || modifyDelta.isEmpty()) {
        	return;
        }
        ObjectDelta<AccountShadowType> accountSecondaryDelta = accCtx.getSecondaryDelta();
        if (accountSecondaryDelta != null) {
            accountSecondaryDelta.merge(modifyDelta);
        } else {
            accCtx.setSecondaryDelta(modifyDelta);
        }
    }

    /**
     * This method checks {@link com.evolveum.midpoint.prism.delta.PropertyDelta} created during consolidation with
     * account sync deltas. If changes from property delta are in account sync deltas than they must be removed,
     * because they already had been applied (they came from sync, already happened).
     *
     * @param accCtx current account sync context
     * @param delta  new delta created during consolidation process
     * @return method return updated delta, or null if delta was empty after filtering (removing unnecessary values).
     */
    private <T> PropertyDelta<T> consolidateWithSync(LensProjectionContext<AccountShadowType> accCtx, PropertyDelta<T> delta) {
        if (delta == null) {
            return null;
        }

        ObjectDelta<AccountShadowType> syncDelta = accCtx.getSyncDelta();
        if (syncDelta == null) {
            return consolidateWithSyncAbsolute(accCtx, delta);
        }

        PropertyDelta<T> alreadyDoneDelta = syncDelta.findPropertyDelta(delta.getPath());
        if (alreadyDoneDelta == null) {
            return delta;
        }

        cleanupValues(delta.getValuesToAdd(), alreadyDoneDelta);
        cleanupValues(delta.getValuesToDelete(), alreadyDoneDelta);

        if (delta.getValues(Object.class).isEmpty()) {
            return null;
        }

        return delta;
    }

    /**
     * This method consolidate property delta against account absolute state which came from sync (not as delta)
     *
     * @param accCtx
     * @param delta
     * @return method return updated delta, or null if delta was empty after filtering (removing unnecessary values).
     */
    private <T> PropertyDelta<T> consolidateWithSyncAbsolute(LensProjectionContext<AccountShadowType> accCtx, PropertyDelta<T> delta) {
        if (delta == null || accCtx.getObjectOld() == null) {
            return delta;
        }

        PrismObject<AccountShadowType> absoluteAccountState = accCtx.getObjectOld();
        PrismProperty<T> absoluteProperty = absoluteAccountState.findProperty(delta.getPath());
        if (absoluteProperty == null) {
            return delta;
        }

        cleanupAbsoluteValues(delta.getValuesToAdd(), true, absoluteProperty);
        cleanupAbsoluteValues(delta.getValuesToDelete(), false, absoluteProperty);

        if (delta.getValues(Object.class).isEmpty()) {
            return null;
        }

        return delta;
    }

    /**
     * Method removes values from property delta values list (first parameter).
     *
     * @param values   collection with {@link PrismPropertyValue} objects to add or delete (from {@link PropertyDelta}
     * @param adding   if true we removing {@link PrismPropertyValue} from {@link Collection} values parameter if they
     *                 already are in {@link PrismProperty} parameter. Otherwise we're removing {@link PrismPropertyValue}
     *                 from {@link Collection} values parameter if they already are not in {@link PrismProperty} parameter.
     * @param property property with absolute state
     */
    private <T> void cleanupAbsoluteValues(Collection<PrismPropertyValue<T>> values, boolean adding, PrismProperty<T> property) {
        if (values == null) {
            return;
        }

        Iterator<PrismPropertyValue<T>> iterator = values.iterator();
        while (iterator.hasNext()) {
            PrismPropertyValue<T> value = iterator.next();
            if (adding && property.hasRealValue(value)) {
                iterator.remove();
            }

            if (!adding && !property.hasRealValue(value)) {
                iterator.remove();
            }
        }
    }

    /**
     * Simple util method which checks property values against already done delta from sync. See method
     * {@link ConsolidationProcessor#consolidateWithSync(com.evolveum.midpoint.model.AccountSyncContext,
     * com.evolveum.midpoint.prism.delta.PropertyDelta)}.
     *
     * @param values           collection which has to be filtered
     * @param alreadyDoneDelta already applied delta from sync
     */
    private <T> void cleanupValues(Collection<PrismPropertyValue<T>> values, PropertyDelta<T> alreadyDoneDelta) {
        if (values == null) {
            return;
        }

        Iterator<PrismPropertyValue<T>> iterator = values.iterator();
        while (iterator.hasNext()) {
            PrismPropertyValue<T> valueToAdd = iterator.next();
            if (alreadyDoneDelta.isRealValueToAdd(valueToAdd)) {
                iterator.remove();
            }
        }
    }

	private Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> sqeezeAttributes(LensProjectionContext<AccountShadowType> accCtx) {
		Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap 
						= new HashMap<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>>();
		if (accCtx.getAccountConstructionDeltaSetTriple() != null) {
			sqeezeAttributesFromAccountConstructionTriple(squeezedMap, accCtx.getAccountConstructionDeltaSetTriple());		
		}
		if (accCtx.getOutboundAccountConstruction() != null) {
			// The plus-minus-zero status of outbound account construction is determined by the type of account delta
			if (accCtx.isAdd()) {
				sqeezeAttributesFromAccountConstructionNonminusToPlus(squeezedMap, accCtx.getOutboundAccountConstruction());
			} else if (accCtx.isDelete()) {
				sqeezeAttributesFromAccountConstructionNonminusToMinus(squeezedMap, accCtx.getOutboundAccountConstruction());
			} else {
				sqeezeAttributesFromAccountConstruction(squeezedMap, accCtx.getOutboundAccountConstruction());
			}
		}
		return squeezedMap;
	}

	private void sqeezeAttributesFromAccountConstructionTriple(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap,
			PrismValueDeltaSetTriple<PrismPropertyValue<AccountConstruction>> accountConstructionDeltaSetTriple) {
		// Zero account constructions go normally, plus to plus, minus to minus
		sqeezeAttributesFromAccountConstructionSet(squeezedMap, accountConstructionDeltaSetTriple.getZeroSet());
		// Plus accounts: zero and plus values go to plus
		sqeezeAttributesFromAccountConstructionSetNonminusToPlus(squeezedMap, accountConstructionDeltaSetTriple.getPlusSet());
		// Minus accounts: zero and plus values go to minus
		sqeezeAttributesFromAccountConstructionSetNonminusToMinus(squeezedMap, accountConstructionDeltaSetTriple.getMinusSet());
	}

	private void sqeezeAttributesFromAccountConstructionSet(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap,
			Collection<PrismPropertyValue<AccountConstruction>> accountConstructionSet) {
		if (accountConstructionSet == null) {
			return;
		}
		for (PrismPropertyValue<AccountConstruction> accountConstruction: accountConstructionSet) {
			sqeezeAttributesFromAccountConstruction(squeezedMap, accountConstruction.getValue());
		}
	}
	
	private void sqeezeAttributesFromAccountConstructionSetNonminusToPlus(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap,
			Collection<PrismPropertyValue<AccountConstruction>> accountConstructionSet) {
		if (accountConstructionSet == null) {
			return;
		}
		for (PrismPropertyValue<AccountConstruction> accountConstruction: accountConstructionSet) {
			sqeezeAttributesFromAccountConstructionNonminusToPlus(squeezedMap, accountConstruction.getValue());
		}
	}
	
	private void sqeezeAttributesFromAccountConstructionSetNonminusToMinus(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap,
			Collection<PrismPropertyValue<AccountConstruction>> accountConstructionSet) {
		if (accountConstructionSet == null) {
			return;
		}
		for (PrismPropertyValue<AccountConstruction> accountConstruction: accountConstructionSet) {
			sqeezeAttributesFromAccountConstructionNonminusToMinus(squeezedMap, accountConstruction.getValue());
		}
	}

	private void sqeezeAttributesFromAccountConstruction(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap,
			AccountConstruction accountConstruction) {
		for (Mapping<? extends PrismPropertyValue<?>> vc: accountConstruction.getAttributeConstructions()) {
			PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> vcTriple = vc.getOutputTriple();
			if (vcTriple == null) {
				continue;
			}
			DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>> squeezeTriple = getSqueezeMapTriple(squeezedMap, vc.getItemName());
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getZeroSet(), vc, accountConstruction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getPlusSet(), vc, accountConstruction);
			convertSqueezeSet(vcTriple.getMinusSet(), squeezeTriple.getMinusSet(), vc, accountConstruction);
		}
	}
	
	private void sqeezeAttributesFromAccountConstructionNonminusToPlus(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap,
			AccountConstruction accountConstruction) {
		for (Mapping<? extends PrismPropertyValue<?>> vc: accountConstruction.getAttributeConstructions()) {
			PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> vcTriple = vc.getOutputTriple();
			if (vcTriple == null) {
				continue;
			}
			DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>> squeezeTriple = getSqueezeMapTriple(squeezedMap, vc.getItemName());
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getPlusSet(), vc, accountConstruction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getPlusSet(), vc, accountConstruction);
			// Ignore minus set
		}
	}

	private void sqeezeAttributesFromAccountConstructionNonminusToMinus(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap,
			AccountConstruction accountConstruction) {
		for (Mapping<? extends PrismPropertyValue<?>> vc: accountConstruction.getAttributeConstructions()) {
			PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> vcTriple = vc.getOutputTriple();
			if (vcTriple == null) {
				continue;
			}
			DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>> squeezeTriple 
													= getSqueezeMapTriple(squeezedMap, vc.getItemName());
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getMinusSet(), vc, accountConstruction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getMinusSet(), vc, accountConstruction);
		}
	}

	private void convertSqueezeSet(Collection<? extends PrismPropertyValue<?>> fromSet,
			Collection<ItemValueWithOrigin<? extends PrismPropertyValue<?>>> toSet,
			Mapping<? extends PrismPropertyValue<?>> valueConstruction, AccountConstruction accountConstruction) {
		if (fromSet != null) {
			for (PrismValue from: fromSet) {
				ItemValueWithOrigin<PrismPropertyValue<?>> pvwo = new ItemValueWithOrigin(from, valueConstruction, accountConstruction);
				toSet.add(pvwo);
			}
		}
	}

	private DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>> getSqueezeMapTriple(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>> squeezedMap, QName itemName) {
		DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>> triple = squeezedMap.get(itemName);
		if (triple == null) {
			triple = new DeltaSetTriple<ItemValueWithOrigin<? extends PrismPropertyValue<?>>>();
			squeezedMap.put(itemName, triple);
		}
		return triple;
	}

}
