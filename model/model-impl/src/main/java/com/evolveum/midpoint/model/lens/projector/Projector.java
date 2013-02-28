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
package com.evolveum.midpoint.model.lens.projector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.refinery.ResourceShadowDiscriminator;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.model.lens.LensContext;
import com.evolveum.midpoint.model.lens.LensFocusContext;
import com.evolveum.midpoint.model.lens.LensProjectionContext;
import com.evolveum.midpoint.model.lens.LensUtil;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceShadowDiscriminatorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

import static com.evolveum.midpoint.common.CompiletimeConfig.CONSISTENCY_CHECKS;

/**
 * Projector recomputes the context. It takes the context with a few basic data as input. It uses all the policies 
 * and mappings to derive all the other data. E.g. a context with only a user (primary) delta. It applies user template,
 * outbound mappings and the inbound mappings and then inbound and outbound mappings of other accounts and so on until
 * all the data are computed. The output is the original context with all the computed delta.
 * 
 * Primary deltas are in the input, secondary deltas are computed in projector. Projector "projects" primary deltas to
 * the secondary deltas of user and accounts.
 * 
 * Projector does NOT execute the deltas. It only recomputes the context. It may read a lot of objects (user, accounts, policies).
 * But it does not change any of them.
 * 
 * @author Radovan Semancik
 *
 */
@Component
public class Projector {
	
	@Autowired(required = true)
	private ContextLoader contextLoader;
	
	@Autowired(required = true)
    private UserPolicyProcessor userPolicyProcessor;

    @Autowired(required = true)
    private AssignmentProcessor assignmentProcessor;

    @Autowired(required = true)
    private InboundProcessor inboundProcessor;
    
    @Autowired(required = true)
    private AccountValuesProcessor accountValuesProcessor;

    @Autowired(required = true)
    private ReconciliationProcessor reconciliationProcessor;

    @Autowired(required = true)
    private CredentialsProcessor credentialsProcessor;

    @Autowired(required = true)
    private ActivationProcessor activationProcessor;
	
	private static final Trace LOGGER = TraceManager.getTrace(Projector.class);
	
	public <F extends ObjectType, P extends ObjectType> void project(LensContext<F,P> context, String activityDescription, 
			OperationResult parentResult) 
			throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException, 
			ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException {
		
		LensUtil.traceContext(LOGGER, activityDescription, "projector start", false, context, false);
		
		if (CONSISTENCY_CHECKS) context.checkConsistence();
		
		context.normalize();
		context.resetProjectionWave();
		
		OperationResult result = parentResult.createSubresult(Projector.class.getName() + ".project");
		result.addContext("executionWave", context.getExecutionWave());
		
		// Projector is using a set of "processors" to do parts of its work. The processors will be called in sequence
		// in the following code.
		
		try {
		
			contextLoader.load(context, activityDescription, result);
			// Set the "fresh" mark now so following consistency check will be stricter
			context.setFresh(true);
			if (CONSISTENCY_CHECKS) context.checkConsistence();
			
			sortAccountsToWaves(context);
	        // Let's do one extra wave with no accounts in it. This time we expect to get the results of the execution to the user
	        // via inbound, e.g. identifiers generated by the resource, DNs and similar things. Hence the +2 instead of +1
	        int maxWaves = context.getMaxWave() + 2;
	        // Note that the number of waves may be recomputed as new accounts appear in the context (usually after assignment step).
	                
	        // Start the waves ....
	        LOGGER.trace("Starting the waves. There will be {} waves (or so we think now)", maxWaves);
	        context.setProjectionWave(0);
	        while (context.getProjectionWave() < maxWaves) {
	    
	        	// Process the user-related aspects of the context. That means inbound, user policy
	        	// and assignments.
	        	
	        	if (CONSISTENCY_CHECKS) context.checkConsistence();
		        // Loop through the account changes, apply inbound expressions
		        inboundProcessor.processInbound(context, result);
		        if (CONSISTENCY_CHECKS) context.checkConsistence();
		        context.recomputeFocus();
		        LensUtil.traceContext(LOGGER, activityDescription, "inbound", false, context, false);
		        if (CONSISTENCY_CHECKS) context.checkConsistence();
		
		        userPolicyProcessor.processUserPolicy(context, result);
		        context.recomputeFocus();
		        LensUtil.traceContext(LOGGER, activityDescription,"user policy", false, context, false);
		        if (CONSISTENCY_CHECKS) context.checkConsistence();
		        
		        checkContextSanity(context, "inbound and user policy", result);
		
		        assignmentProcessor.processAssignmentsProjections(context, result);
		        assignmentProcessor.processOrgAssignments(context, result);
		        context.recompute();
		        sortAccountsToWaves(context);
		        maxWaves = context.getMaxWave() + 2;
		        LensUtil.traceContext(LOGGER, activityDescription,"assignments", false, context, true);
		        if (CONSISTENCY_CHECKS) context.checkConsistence();
		        
		        assignmentProcessor.checkForAssignmentConflicts(context, result);
		
		        // User-related processing is over. Now we will process accounts in a loop.
		        for (LensProjectionContext<P> projectionContext: context.getProjectionContexts()) {
		        	if (projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN) {
						
						continue;
		        	}
		        	if (projectionContext.getWave() != context.getProjectionWave()) {
		        		// Let's skip accounts that do not belong into this wave.
		        		continue;
		        	}
		        	if (!projectionContext.isShadow()) {
		        		continue;
		        	}
		        	
		        	if (CONSISTENCY_CHECKS) context.checkConsistence();
		        	
		        	// This is a "composite" processor. it contains several more processor invocations inside
		        	accountValuesProcessor.process(context, projectionContext, activityDescription, result);
		        	
		        	if (CONSISTENCY_CHECKS) context.checkConsistence();
		        	
		        	projectionContext.recompute();
		        	//SynchronizerUtil.traceContext("values", context, false);
		        	if (CONSISTENCY_CHECKS) context.checkConsistence();
		        	
		        	credentialsProcessor.processCredentials(context, projectionContext, result);
		        	
		        	projectionContext.recompute();
		        	//SynchronizerUtil.traceContext("credentials", context, false);
		        	if (CONSISTENCY_CHECKS) context.checkConsistence();
		        	
		        	activationProcessor.processActivation(context, projectionContext, result);
			        
		        	context.recompute();
		        	LensUtil.traceContext(LOGGER, activityDescription, "values, credentials and activation", false, context, true);
			        if (CONSISTENCY_CHECKS) context.checkConsistence();
			
			        reconciliationProcessor.processReconciliation(context, projectionContext, result);
			        context.recompute();
			        LensUtil.traceContext(LOGGER, activityDescription, "reconciliation", false, context, false);
			        if (CONSISTENCY_CHECKS) context.checkConsistence();
		        }
		        
		        if (CONSISTENCY_CHECKS) context.checkConsistence();
		        
		        context.incrementProjectionWave();
	        }
	        
	        if (CONSISTENCY_CHECKS) context.checkConsistence();
	        
	        result.recordSuccess();
	        
		} catch (SchemaException e) {
			result.recordFatalError(e);
			throw e;
		} catch (PolicyViolationException e) {
			result.recordFatalError(e);
			throw e;
		} catch (ExpressionEvaluationException e) {
			result.recordFatalError(e);
			throw e;
		} catch (ObjectNotFoundException e) {
			result.recordFatalError(e);
			throw e;
		} catch (ObjectAlreadyExistsException e) {
			result.recordFatalError(e);
			throw e;
		} catch (CommunicationException e) {
			result.recordFatalError(e);
			throw e;
		} catch (ConfigurationException e) {
			result.recordFatalError(e);
			throw e;
		} catch (SecurityViolationException e) {
			result.recordFatalError(e);
			throw e;
		} catch (RuntimeException e) {
			result.recordFatalError(e);
			// This should not normally happen unless there is something really bad or there is a bug.
			// Make sure that it is logged.
			LOGGER.error("Runtime error in projector: {}", e.getMessage(), e);
			throw e;
		}
		
	}
	
	public <F extends ObjectType, P extends ObjectType> void sortAccountsToWaves(LensContext<F,P> context) throws PolicyViolationException {
		for (LensProjectionContext<P> projectionContext: context.getProjectionContexts()) {
			determineAccountWave(context, projectionContext);
		}
	}
	
	// TODO: check for circular dependencies
	private <F extends ObjectType, P extends ObjectType> void determineAccountWave(LensContext<F,P> context, LensProjectionContext<P> accountContext) throws PolicyViolationException {
		if (accountContext.getWave() >= 0) {
			// This was already processed
			return;
		}
		int wave = 0;
		for (ResourceShadowDiscriminatorType dependency :accountContext.getDependencies()) {
			ResourceShadowDiscriminator refRat = new ResourceShadowDiscriminator(dependency);
			LensProjectionContext<P> dependencyAccountContext = context.findProjectionContext(refRat);
			if (dependencyAccountContext == null) {
				throw new PolicyViolationException("Unsatisfied dependency of account "+accountContext.getResourceShadowDiscriminator()+
						" dependent on "+refRat+": Account not provisioned");
			}
			determineAccountWave(context, dependencyAccountContext);
			if (dependencyAccountContext.getWave() + 1 > wave) {
				wave = dependencyAccountContext.getWave() + 1;
			}
		}
//		LOGGER.trace("Wave for {}: {}", accountContext.getResourceAccountType(), wave);
		accountContext.setWave(wave);
	}

	private <F extends ObjectType, P extends ObjectType> void checkContextSanity(LensContext<F,P> context, String activityDescription, 
			OperationResult result) throws SchemaException {
		LensFocusContext<F> focusContext = context.getFocusContext();
		if (focusContext != null) {
			PrismObject<F> focusObjectNew = focusContext.getObjectNew();
			if (focusObjectNew != null) {
				if (focusObjectNew.asObjectable().getName() == null) {
					throw new SchemaException("Focus "+focusObjectNew+" does not have a name after "+activityDescription);
				}
			}
		}
	}

}
