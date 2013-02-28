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

import com.evolveum.midpoint.common.expression.ObjectDeltaObject;
import com.evolveum.midpoint.common.expression.StringPolicyResolver;
import com.evolveum.midpoint.common.expression.evaluator.GenerateExpressionEvaluator;
import com.evolveum.midpoint.common.mapping.Mapping;
import com.evolveum.midpoint.common.mapping.MappingFactory;
import com.evolveum.midpoint.common.refinery.RefinedAccountDefinition;
import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.ResourceShadowDiscriminator;
import com.evolveum.midpoint.model.lens.AccountConstruction;
import com.evolveum.midpoint.model.lens.LensContext;
import com.evolveum.midpoint.model.lens.LensProjectionContext;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.OriginType;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.GenerateExpressionEvaluatorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.MappingStrengthType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.MappingType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.StringPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ValuePolicyType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Processor that evaluates values of the outbound mappings. It does not create the deltas yet. It just collects the
 * evaluated mappings in account context.
 * 
 * @author Radovan Semancik
 */
@Component
public class OutboundProcessor {

    private static final Trace LOGGER = TraceManager.getTrace(OutboundProcessor.class);

    @Autowired(required = true)
    private PrismContext prismContext;

    @Autowired(required = true)
    private MappingFactory mappingFactory;

    void processOutbound(LensContext<UserType,AccountShadowType> context, LensProjectionContext<AccountShadowType> accCtx, OperationResult result) throws SchemaException,
            ExpressionEvaluationException, ObjectNotFoundException {

        ResourceShadowDiscriminator rat = accCtx.getResourceShadowDiscriminator();
        ObjectDelta<AccountShadowType> accountDelta = accCtx.getDelta();

        if (accountDelta != null && accountDelta.getChangeType() == ChangeType.DELETE) {
            LOGGER.trace("Processing outbound expressions for account {} skipped, DELETE account delta", rat);
            // No point in evaluating outbound
            return;
        }

        LOGGER.trace("Processing outbound expressions for account {} starting", rat);

        RefinedAccountDefinition rAccount = accCtx.getRefinedAccountDefinition();
        if (rAccount == null) {
            LOGGER.error("Definition for account type {} not found in the context, but it should be there, dumping context:\n{}", rat, context.dump());
            throw new IllegalStateException("Definition for account type " + rat + " not found in the context, but it should be there");
        }
        
        ObjectDeltaObject<UserType> userOdo = context.getFocusContext().getObjectDeltaObject();
        ObjectDeltaObject<AccountShadowType> accountOdo = accCtx.getObjectDeltaObject();
        
        AccountConstruction outboundAccountConstruction = new AccountConstruction(null, accCtx.getResource());
        
        String operation = accCtx.getOperation().getValue();

        for (QName attributeName : rAccount.getNamesOfAttributesWithOutboundExpressions()) {
			RefinedAttributeDefinition refinedAttributeDefinition = rAccount.getAttributeDefinition(attributeName);
						
			final MappingType outboundMappingType = refinedAttributeDefinition.getOutboundMappingType();
			if (outboundMappingType == null) {
			    continue;
			}
			
			// TODO: check access
			
			Mapping<? extends PrismPropertyValue<?>> mapping = mappingFactory.createMapping(outboundMappingType, 
			        "outbound mapping for " + PrettyPrinter.prettyPrint(refinedAttributeDefinition.getName())
			        + " in " + ObjectTypeUtil.toShortString(rAccount.getResourceType()));
			
			if (!mapping.isApplicableToChannel(context.getChannel())) {
				LOGGER.trace("Skipping outbound mapping for {} because the channel does not match", attributeName);
				continue;
			}
			
			// This is just supposed to be an optimization. The consolidation should deal with the weak mapping
			// even if it is there. But in that case we do not need to evaluate it at all.
			if (mapping.getStrength() == MappingStrengthType.WEAK && accCtx.hasValueForAttribute(attributeName)) {
				LOGGER.trace("Skipping outbound mapping for {} because it is weak", attributeName);
				continue;
			}
			
			mapping.setDefaultTargetDefinition(refinedAttributeDefinition);
			mapping.setSourceContext(userOdo);
			mapping.addVariableDefinition(ExpressionConstants.VAR_USER, userOdo);
			mapping.addVariableDefinition(ExpressionConstants.VAR_ACCOUNT, accountOdo);
			mapping.addVariableDefinition(ExpressionConstants.VAR_ITERATION, accCtx.getIteration());
			mapping.addVariableDefinition(ExpressionConstants.VAR_ITERATION_TOKEN, accCtx.getIterationToken());
			mapping.addVariableDefinition(ExpressionConstants.VAR_OPERATION, operation);
			mapping.setRootNode(userOdo);
			mapping.setOriginType(OriginType.OUTBOUND);
			
			StringPolicyResolver stringPolicyResolver = new StringPolicyResolver() {
				private ItemPath outputPath;
				private ItemDefinition outputDefinition;
				@Override
				public void setOutputPath(ItemPath outputPath) {
					this.outputPath = outputPath;
				}
				
				@Override
				public void setOutputDefinition(ItemDefinition outputDefinition) {
					this.outputDefinition = outputDefinition;
				}
				
				@Override
				public StringPolicyType resolve() {
					
					if (outboundMappingType.getExpression() != null){
						List<JAXBElement<?>> evaluators = outboundMappingType.getExpression().getExpressionEvaluator();
						if (evaluators != null){
							for (JAXBElement jaxbEvaluator : evaluators){
								Object object = jaxbEvaluator.getValue();
								if (object != null && object instanceof GenerateExpressionEvaluatorType && ((GenerateExpressionEvaluatorType) object).getValuePolicyRef() != null){
									ObjectReferenceType ref = ((GenerateExpressionEvaluatorType) object).getValuePolicyRef();
									try{
									ValuePolicyType valuePolicyType = mappingFactory.getObjectResolver().resolve(ref, ValuePolicyType.class, "resolving value policy for generate attribute "+ outputDefinition.getName()+"value", new OperationResult("Resolving value policy"));
									if (valuePolicyType != null){
										return valuePolicyType.getStringPolicy();
									}
									} catch (Exception ex){
										throw new SystemException(ex.getMessage(), ex);
									}
								}
							}
							
						}
					}
					return null;
				}
			};
			mapping.setStringPolicyResolver(stringPolicyResolver);
			// TODO: other variables?
			
			// Set condition masks. There are used as a brakes to avoid evaluating to nonsense values in case user is not present
			// (e.g. in old values in ADD situations and new values in DELETE situations).
			if (userOdo.getOldObject() == null) {
				mapping.setConditionMaskOld(false);
			}
			if (userOdo.getNewObject() == null) {
				mapping.setConditionMaskNew(false);
			}
			
			mapping.evaluate(result);
			
			outboundAccountConstruction.addAttributeConstruction(mapping);
        }
        
        accCtx.setOutboundAccountConstruction(outboundAccountConstruction);
    }
}
