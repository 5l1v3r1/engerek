/*
 * Copyright (c) 2010-2015 Evolveum
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

package com.evolveum.midpoint.web.page.admin.certification.dto;

import com.evolveum.midpoint.certification.api.CertificationManager;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.BaseSortableDataProvider;
import com.evolveum.midpoint.web.page.error.PageError;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType;
import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author lazyman
 * @author mederly
 */
public class CertDecisionDtoProvider extends BaseSortableDataProvider<CertDecisionDto> {

    private static final Trace LOGGER = TraceManager.getTrace(CertDecisionDtoProvider.class);
    private static final String DOT_CLASS = CertDecisionDtoProvider.class.getName() + ".";
    private static final String OPERATION_SEARCH_OBJECTS = DOT_CLASS + "searchObjects";
    private static final String OPERATION_COUNT_OBJECTS = DOT_CLASS + "countObjects";

    private ObjectQuery campaignQuery;
    // case query is stored in super.query

    private boolean notDecidedOnly;
    private String reviewerOid;

    public CertDecisionDtoProvider(Component component) {
        super(component, false);        // TODO make this cacheable
    }

    @Override
    public Iterator<CertDecisionDto> internalIterator(long first, long count) {
        LOGGER.trace("begin::iterator() from {} count {}.", new Object[]{first, count});
        getAvailableData().clear();

        OperationResult result = new OperationResult(OPERATION_SEARCH_OBJECTS);
        try {
            ObjectPaging paging = createPaging(first, count);
            Task task = getPage().createSimpleTask(OPERATION_SEARCH_OBJECTS);
            
            ObjectQuery caseQuery = getQuery();
            if (caseQuery == null){
            	caseQuery = new ObjectQuery();
            }
            caseQuery.setPaging(paging);

            Collection<SelectorOptions<GetOperationOptions>> resolveNames =
                    SelectorOptions.createCollection(GetOperationOptions.createResolveNames());

            CertificationManager certificationManager = getPage().getCertificationManager();
            List<AccessCertificationCaseType> caseList = certificationManager.searchDecisions(caseQuery, reviewerOid, notDecidedOnly, resolveNames, task, result);

            for (AccessCertificationCaseType _case : caseList) {
                getAvailableData().add(new CertDecisionDto(_case, getPage()));
            }
        } catch (Exception ex) {
            result.recordFatalError("Couldn't list decisions.", ex);
            LoggingUtils.logException(LOGGER, "Couldn't list decisions", ex);
        } finally {
            result.computeStatusIfUnknown();
        }

        if (!WebMiscUtil.isSuccessOrHandledError(result)) {
            handleNotSuccessOrHandledErrorInIterator(result);
        }

        LOGGER.trace("end::iterator()");
        return getAvailableData().iterator();
    }

    protected void handleNotSuccessOrHandledErrorInIterator(OperationResult result){
        getPage().showResultInSession(result);
        throw new RestartResponseException(PageError.class);
    }

    // TODO replace searchDecisions with countDecisions (when it will be available)
    @Override
    protected int internalSize() {
        LOGGER.trace("begin::internalSize()");
        int count = 0;
        OperationResult result = new OperationResult(OPERATION_COUNT_OBJECTS);
        try {
            Task task = getPage().createSimpleTask(OPERATION_COUNT_OBJECTS);
            CertificationManager certificationManager = getPage().getCertificationManager();
            ObjectQuery query = getQuery().clone();
            query.setPaging(null);          // when counting decisions we need to exclude offset+size (and sorting info is irrelevant)
            List<AccessCertificationCaseType> caseList = certificationManager.searchDecisions(query, reviewerOid, notDecidedOnly, null, task, result);
            count = caseList.size();
        } catch (Exception ex) {
            result.recordFatalError("Couldn't count objects.", ex);
            LoggingUtils.logException(LOGGER, "Couldn't count objects", ex);
        } finally {
            result.computeStatusIfUnknown();
        }

        if (!WebMiscUtil.isSuccessOrHandledError(result)) {
            getPage().showResultInSession(result);
            throw new RestartResponseException(PageError.class);
        }

        LOGGER.trace("end::internalSize()");
        return count;
    }

    public ObjectQuery getCampaignQuery() {
        return campaignQuery;
    }

    public void setCampaignQuery(ObjectQuery campaignQuery) {
        this.campaignQuery = campaignQuery;
    }

    public String getReviewerOid() {
        return reviewerOid;
    }

    public void setReviewerOid(String reviewerOid) {
        this.reviewerOid = reviewerOid;
    }

    public boolean isNotDecidedOnly() {
        return notDecidedOnly;
    }

    public void setNotDecidedOnly(boolean notDecidedOnly) {
        this.notDecidedOnly = notDecidedOnly;
    }
}
