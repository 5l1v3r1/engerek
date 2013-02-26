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

package com.evolveum.midpoint.web.component.data;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.configuration.dto.DebugObjectItem;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import org.apache.commons.lang.Validate;
import org.apache.wicket.Component;

import java.io.Serializable;
import java.util.*;

/**
 * @author lazyman
 */
public class RepositoryObjectDataProvider
        extends BaseSortableDataProvider<DebugObjectItem> {

    private static final String DOT_CLASS = RepositoryObjectDataProvider.class.getName() + ".";
    private static final String OPERATION_SEARCH_OBJECTS = DOT_CLASS + "searchObjects";
    private static final String OPERATION_LOAD_RESOURCE = DOT_CLASS + "loadResource";
    private static final String OPERATION_COUNT_OBJECTS = DOT_CLASS + "countObjects";

    private static final Trace LOGGER = TraceManager.getTrace(RepositoryObjectDataProvider.class);
    private Class<? extends ObjectType> type;

    private Map<String, ResourceDescription> resourceCache = new HashMap<String, ResourceDescription>();

    public RepositoryObjectDataProvider(Component component, Class<? extends ObjectType> type) {
        super(component, true);

        setType(type);
    }

    @Override
    public Iterator<DebugObjectItem> iterator(long first, long count) {
        LOGGER.trace("begin::iterator() from {} count {}.", new Object[]{first, count});
        getAvailableData().clear();

        OperationResult result = new OperationResult(OPERATION_SEARCH_OBJECTS);
        try {
            ObjectPaging paging = createPaging(first, count);
			ObjectQuery query = getQuery();
			if (query == null) {
				query = new ObjectQuery();
			}
			query.setPaging(paging);

            List<PrismObject<? extends ObjectType>> list = getModel().searchObjects((Class) type, query,
                    SelectorOptions.createCollection(new ItemPath(), GetOperationOptions.createRaw()),
                    getPage().createSimpleTask(OPERATION_SEARCH_OBJECTS), result);
            for (PrismObject<? extends ObjectType> object : list) {
                getAvailableData().add(createItem(object, result));
            }

            result.recordSuccess();
        } catch (Exception ex) {
            result.recordFatalError("Couldn't list objects.", ex);
        }

        if (!result.isSuccess()) {
            getPage().showResultInSession(result);
        }

        LOGGER.trace("end::iterator()");
        return getAvailableData().iterator();
    }

    private DebugObjectItem createItem(PrismObject object, OperationResult result)
            throws ObjectNotFoundException, SchemaException, SecurityViolationException {

        DebugObjectItem item = new DebugObjectItem(object.getOid(), WebMiscUtil.getName(object));
        if (!ResourceObjectShadowType.class.isAssignableFrom(object.getCompileTimeClass())) {
            return item;
        }

        PrismReference ref = object.findReference(new ItemPath(ResourceObjectShadowType.F_RESOURCE_REF));
        if (ref == null || ref.getValue() == null) {
            return item;
        }

        PrismReferenceValue refValue = ref.getValue();
        String resourceOid = refValue.getOid();
        ResourceDescription desc = resourceCache.get(resourceOid);
        if (desc == null) {
            desc = loadDescription(resourceOid, result);
            resourceCache.put(resourceOid, desc);
        }

        item.setResourceName(desc.getName());
        item.setResourceType(desc.getType());

        return item;
    }

    private ResourceDescription loadDescription(String oid, OperationResult result)
            throws ObjectNotFoundException, SchemaException, SecurityViolationException {

        Collection<SelectorOptions<GetOperationOptions>> options =
                SelectorOptions.createCollection(ResourceType.F_CONNECTOR, GetOperationOptions.createResolve());

        OperationResult subResult = result.createSubresult(OPERATION_LOAD_RESOURCE);
        PrismObject<ResourceType> resource;
        try {
            resource = getModel().getObject(ResourceType.class, oid, options,
                    getPage().createSimpleTask(OPERATION_LOAD_RESOURCE), subResult);
        } finally {
            subResult.recomputeStatus();
        }


        PrismReference ref = resource.findReference(ResourceType.F_CONNECTOR_REF);
        String type = null;
        if (ref != null && ref.getValue() != null) {
            PrismReferenceValue refValue = ref.getValue();
            if (refValue.getObject() != null) {
                PrismObject connector = refValue.getObject();
                PrismProperty<String> pType = connector.findProperty(ConnectorType.F_CONNECTOR_TYPE);
                if (pType != null && pType.getRealValue() != null) {
                    type = pType.getRealValue(String.class);
                }
            }
        }

        return new ResourceDescription(resource.getOid(), WebMiscUtil.getName(resource), type);
    }

    @Override
    protected int internalSize() {
        LOGGER.trace("begin::internalSize()");
        int count = 0;
        OperationResult result = new OperationResult(OPERATION_COUNT_OBJECTS);
        try {
            count = getModel().countObjects(type, getQuery(),
                    SelectorOptions.createCollection(new ItemPath(), GetOperationOptions.createRaw()),
                    getPage().createSimpleTask(OPERATION_COUNT_OBJECTS), result);

            result.recordSuccess();
        } catch (Exception ex) {
            result.recordFatalError("Couldn't count objects.", ex);
        }

        if (!result.isSuccess()) {
            getPage().showResultInSession(result);
        }
        LOGGER.trace("end::internalSize()");
        return count;
    }

    public void setType(Class<? extends ObjectType> type) {
        Validate.notNull(type);
        this.type = type;
    }

    public Class<? extends ObjectType> getType() {
        return type;
    }

    @Override
    protected CachedSize getCachedSize(Map<Serializable, CachedSize> cache) {
        return cache.get(new TypedCacheKey(getQuery(), type));
    }

    @Override
    protected void addCachedSize(Map<Serializable, CachedSize> cache, CachedSize newSize) {
        cache.put(new TypedCacheKey(getQuery(), type), newSize);
    }

    private static class ResourceDescription implements Serializable {

        private String oid;
        private String name;
        private String type;

        private ResourceDescription(String oid, String name, String type) {
            this.oid = oid;
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getOid() {
            return oid;
        }

        public String getType() {
            return type;
        }
    }
}
