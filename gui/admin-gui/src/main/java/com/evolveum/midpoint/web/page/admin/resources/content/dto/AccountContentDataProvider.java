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

package com.evolveum.midpoint.web.page.admin.resources.content.dto;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.AndFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.schema.util.ResourceObjectShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.BaseSortableDataProvider;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.SynchronizationSituationType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import org.apache.commons.lang.Validate;
import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author lazyman
 */
public class AccountContentDataProvider extends BaseSortableDataProvider<SelectableBean<AccountContentDto>> {

    private static final Trace LOGGER = TraceManager.getTrace(AccountContentDataProvider.class);
    private static final String DOT_CLASS = AccountContentDataProvider.class.getName() + ".";
    private static final String OPERATION_LOAD_ACCOUNTS = DOT_CLASS + "loadAccounts";
    private static final String OPERATION_LOAD_OWNER = DOT_CLASS + "loadOwner";

    private IModel<String> resourceOid;
    private IModel<QName> objectClass;

    public AccountContentDataProvider(Component component, IModel<String> resourceOid, IModel<QName> objectClass) {
        super(component);

        Validate.notNull(resourceOid, "Resource oid model must not be null.");
        Validate.notNull(objectClass, "Object class model must not be null.");
        this.resourceOid = resourceOid;
        this.objectClass = objectClass;
    }

    @Override
    public Iterator<? extends SelectableBean<AccountContentDto>> iterator(long first, long count) {
        LOGGER.trace("begin::iterator() from {} count {}.", new Object[]{first, count});
        getAvailableData().clear();

        OperationResult result = new OperationResult(OPERATION_LOAD_ACCOUNTS);
        try {
            ObjectPaging paging = createPaging(first, count);
            Task task = getPage().createSimpleTask(OPERATION_LOAD_ACCOUNTS);

            ObjectQuery baseQuery = ObjectQueryUtil.createResourceAndAccountQuery(resourceOid.getObject(),
                    objectClass.getObject(), getPage().getPrismContext());
            ObjectQuery query = getQuery();
            if (query != null) {
                ObjectFilter baseFilter = baseQuery.getFilter();
                ObjectFilter filter = query.getFilter();

                query = new ObjectQuery();
                ObjectFilter andFilter = AndFilter.createAnd(baseFilter, filter);
                query.setFilter(andFilter);
            } else {
                query = baseQuery;
            }

            query.setPaging(paging);
            List<PrismObject<AccountShadowType>> list = getModel().searchObjects(AccountShadowType.class,
                    query, null, task, result);

            AccountContentDto dto;
            for (PrismObject<AccountShadowType> object : list) {
                dto = createAccountContentDto(object, result);
                getAvailableData().add(new SelectableBean<AccountContentDto>(dto));
            }

            result.recordSuccess();
        } catch (Exception ex) {
            result.recordFatalError("Couldn't list objects.", ex);
            LoggingUtils.logException(LOGGER, "Couldn't list objects", ex);
        }

        if (!result.isSuccess()) {
            getPage().showResultInSession(result);
        }

        LOGGER.trace("end::iterator()");
        return getAvailableData().iterator();
    }

    @Override
    protected int internalSize() {
        return Integer.MAX_VALUE;
    }

    private AccountContentDto createAccountContentDto(PrismObject<AccountShadowType> object, OperationResult result)
            throws SchemaException, SecurityViolationException {

        AccountContentDto dto = new AccountContentDto();
        dto.setAccountName(WebMiscUtil.getName(object));
        dto.setAccountOid(object.getOid());

        Collection<ResourceAttribute<?>> identifiers = ResourceObjectShadowUtil.getIdentifiers(object);
        if (identifiers != null) {
            List<ResourceAttribute<?>> idList = new ArrayList<ResourceAttribute<?>>();
            idList.addAll(identifiers);
            dto.setIdentifiers(idList);
        }

        PrismObject<UserType> owner = loadOwner(dto.getAccountOid(), result);
        if (owner != null) {
            dto.setOwnerName(WebMiscUtil.getName(owner));
            dto.setOwnerOid(owner.getOid());
        }

        dto.setSituation(WebMiscUtil.getValue(object, ResourceObjectShadowType.F_SYNCHRONIZATION_SITUATION,
                SynchronizationSituationType.class));

        return dto;
    }

    private PrismObject<UserType> loadOwner(String accountOid, OperationResult result) {
        OperationResult ownerResult = result.createSubresult(OPERATION_LOAD_OWNER);
        Task task = getPage().createSimpleTask(OPERATION_LOAD_OWNER);
        try {
            return getModel().listAccountShadowOwner(accountOid, task, ownerResult);
        } catch (ObjectNotFoundException ex) {
            //owner was not found, it's possible and it's ok on unlinked accounts
        }
        return null;
    }

    @Override
    public boolean isSizeAvailable() {
        return false;
    }
}
