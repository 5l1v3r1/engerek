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

package com.evolveum.midpoint.repo.sql.helpers;

import com.evolveum.midpoint.common.InternalsConfig;
import com.evolveum.midpoint.common.crypto.CryptoUtil;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.prism.parser.XNodeProcessorEvaluationMode;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.sql.ObjectPagingAfterOid;
import com.evolveum.midpoint.repo.sql.SqlRepositoryConfiguration;
import com.evolveum.midpoint.repo.sql.SqlRepositoryServiceImpl;
import com.evolveum.midpoint.repo.sql.data.common.RObject;
import com.evolveum.midpoint.repo.sql.data.common.any.RAnyValue;
import com.evolveum.midpoint.repo.sql.data.common.any.RValueType;
import com.evolveum.midpoint.repo.sql.data.common.type.RObjectExtensionType;
import com.evolveum.midpoint.repo.sql.query.QueryEngine;
import com.evolveum.midpoint.repo.sql.query.QueryException;
import com.evolveum.midpoint.repo.sql.query.RQuery;
import com.evolveum.midpoint.repo.sql.query2.QueryEngine2;
import com.evolveum.midpoint.repo.sql.util.ClassMapper;
import com.evolveum.midpoint.repo.sql.util.DtoTranslationException;
import com.evolveum.midpoint.repo.sql.util.GetObjectResult;
import com.evolveum.midpoint.repo.sql.util.RUtil;
import com.evolveum.midpoint.repo.sql.util.ScrollableResultsIterator;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ResultHandler;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import org.hibernate.Criteria;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author lazyman, mederly
 */
@Component
public class ObjectRetriever {

    private static final Trace LOGGER = TraceManager.getTrace(ObjectRetriever.class);
    private static final Trace LOGGER_PERFORMANCE = TraceManager.getTrace(SqlRepositoryServiceImpl.PERFORMANCE_LOG_NAME);

    @Autowired
    @Qualifier("repositoryService")
    private RepositoryService repositoryService;

    @Autowired
    private LookupTableHelper lookupTableHelper;

    @Autowired
    private CertificationCaseHelper caseHelper;

    @Autowired
    private TransactionHelper transactionHelper;

    @Autowired
    private NameResolutionHelper nameResolutionHelper;

    @Autowired
    private PrismContext prismContext;

    public <T extends ObjectType> PrismObject<T> getObjectAttempt(Class<T> type, String oid,
                                                                  Collection<SelectorOptions<GetOperationOptions>> options,
                                                                  OperationResult result)
            throws ObjectNotFoundException, SchemaException {
        LOGGER_PERFORMANCE.debug("> get object {}, oid={}", type.getSimpleName(), oid);
        PrismObject<T> objectType = null;

        Session session = null;
        try {
            session = transactionHelper.beginReadOnlyTransaction();

            objectType = getObjectInternal(session, type, oid, options, false);

            session.getTransaction().commit();
        } catch (ObjectNotFoundException ex) {
            GetOperationOptions rootOptions = SelectorOptions.findRootOptions(options);
            transactionHelper.rollbackTransaction(session, ex, result, !GetOperationOptions.isAllowNotFound(rootOptions));
            throw ex;
        } catch (SchemaException ex) {
            transactionHelper.rollbackTransaction(session, ex, "Schema error while getting object with oid: "
                    + oid + ". Reason: " + ex.getMessage(), result, true);
            throw ex;
        } catch (QueryException | DtoTranslationException | RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Get object:\n{}", new Object[]{(objectType != null ? objectType.debugDump(3) : null)});
        }

        return objectType;
    }

    public <T extends ObjectType> PrismObject<T> getObjectInternal(Session session, Class<T> type, String oid,
                                                                   Collection<SelectorOptions<GetOperationOptions>> options,
                                                                   boolean lockForUpdate)
            throws ObjectNotFoundException, SchemaException, DtoTranslationException, QueryException {

        boolean lockedForUpdateViaHibernate = false;
        boolean lockedForUpdateViaSql = false;

        LockOptions lockOptions = new LockOptions();
        //todo fix lock for update!!!!!
        if (lockForUpdate) {
            if (getConfiguration().isLockForUpdateViaHibernate()) {
                lockOptions.setLockMode(LockMode.PESSIMISTIC_WRITE);
                lockedForUpdateViaHibernate = true;
            } else if (getConfiguration().isLockForUpdateViaSql()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Trying to lock object " + oid + " for update (via SQL)");
                }
                long time = System.currentTimeMillis();
                SQLQuery q = session.createSQLQuery("select oid from m_object where oid = ? for update");
                q.setString(0, oid);
                Object result = q.uniqueResult();
                if (result == null) {
                    return throwObjectNotFoundException(type, oid);
                }
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Locked via SQL (in " + (System.currentTimeMillis() - time) + " ms)");
                }
                lockedForUpdateViaSql = true;
            }
        }

        if (LOGGER.isTraceEnabled()) {
            if (lockedForUpdateViaHibernate) {
                LOGGER.trace("Getting object " + oid + " with locking for update (via hibernate)");
            } else if (lockedForUpdateViaSql) {
                LOGGER.trace("Getting object " + oid + ", already locked for update (via SQL)");
            } else {
                LOGGER.trace("Getting object " + oid + " without locking for update");
            }
        }

        GetObjectResult fullObject = null;
        if (!lockForUpdate) {
            Query query = session.getNamedQuery("get.object");
            query.setString("oid", oid);
            query.setResultTransformer(GetObjectResult.RESULT_TRANSFORMER);
            query.setLockOptions(lockOptions);

            fullObject = (GetObjectResult) query.uniqueResult();
        } else {
            // we're doing update after this get, therefore we load full object right now
            // (it would be loaded during merge anyway)
            // this just loads object to hibernate session, probably will be removed later. Merge after this get
            // will be faster. Read and use object only from fullObject column.
            // todo remove this later [lazyman]
            Criteria criteria = session.createCriteria(ClassMapper.getHQLTypeClass(type));
            criteria.add(Restrictions.eq("oid", oid));

            criteria.setLockMode(lockOptions.getLockMode());
            RObject obj = (RObject) criteria.uniqueResult();

            if (obj != null) {
                obj.toJAXB(prismContext, null).asPrismObject();
                fullObject = new GetObjectResult(obj.getFullObject(), obj.getStringsCount(), obj.getLongsCount(),
                        obj.getDatesCount(), obj.getReferencesCount(), obj.getPolysCount(), obj.getBooleansCount());
            }
        }

        LOGGER.trace("Got it.");
        if (fullObject == null) {
            throwObjectNotFoundException(type, oid);
        }


        LOGGER.trace("Transforming data to JAXB type.");
        PrismObject<T> prismObject = updateLoadedObject(fullObject, type, options, session);
        validateObjectType(prismObject, type);

        return prismObject;
    }

    protected SqlRepositoryConfiguration getConfiguration() {
        return ((SqlRepositoryServiceImpl) repositoryService).getConfiguration();
    }

    private <T extends ObjectType> PrismObject<T> throwObjectNotFoundException(Class<T> type, String oid)
            throws ObjectNotFoundException {
        throw new ObjectNotFoundException("Object of type '" + type.getSimpleName() + "' with oid '" + oid
                + "' was not found.", null, oid);
    }

    public <F extends FocusType> PrismObject<F> searchShadowOwnerAttempt(String shadowOid, Collection<SelectorOptions<GetOperationOptions>> options, OperationResult result) {
        LOGGER_PERFORMANCE.debug("> search shadow owner for oid={}", shadowOid);
        PrismObject<F> owner = null;
        Session session = null;
        try {
            session = transactionHelper.beginReadOnlyTransaction();
            LOGGER.trace("Selecting account shadow owner for account {}.", new Object[]{shadowOid});
            Query query = session.getNamedQuery("searchShadowOwner.getOwner");
            query.setString("oid", shadowOid);
            query.setResultTransformer(GetObjectResult.RESULT_TRANSFORMER);

            List<GetObjectResult> focuses = query.list();
            LOGGER.trace("Found {} focuses, transforming data to JAXB types.",
                    new Object[]{(focuses != null ? focuses.size() : 0)});

            if (focuses == null || focuses.isEmpty()) {
                // account shadow owner was not found
                return null;
            }

            if (focuses.size() > 1) {
                LOGGER.warn("Found {} owners for shadow oid {}, returning first owner.",
                        new Object[]{focuses.size(), shadowOid});
            }

            GetObjectResult focus = focuses.get(0);
            owner = updateLoadedObject(focus, (Class<F>) FocusType.class, options, session);

            session.getTransaction().commit();

        } catch (SchemaException | RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        return owner;
    }

    public PrismObject<UserType> listAccountShadowOwnerAttempt(String accountOid, OperationResult result)
            throws ObjectNotFoundException {
        LOGGER_PERFORMANCE.debug("> list account shadow owner oid={}", accountOid);
        PrismObject<UserType> userType = null;
        Session session = null;
        try {
            session = transactionHelper.beginReadOnlyTransaction();
            Query query = session.getNamedQuery("listAccountShadowOwner.getUser");
            query.setString("oid", accountOid);
            query.setResultTransformer(GetObjectResult.RESULT_TRANSFORMER);

            List<GetObjectResult> users = query.list();
            LOGGER.trace("Found {} users, transforming data to JAXB types.",
                    new Object[]{(users != null ? users.size() : 0)});

            if (users == null || users.isEmpty()) {
                // account shadow owner was not found
                return null;
            }

            if (users.size() > 1) {
                LOGGER.warn("Found {} users for account oid {}, returning first user. [interface change needed]",
                        new Object[]{users.size(), accountOid});
            }

            GetObjectResult user = users.get(0);
            userType = updateLoadedObject(user, UserType.class, null, session);

            session.getTransaction().commit();
        } catch (SchemaException | RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        return userType;
    }

    public <T extends ObjectType> int countObjectsAttempt(Class<T> type, ObjectQuery query, OperationResult result) {
        LOGGER_PERFORMANCE.debug("> count objects {}", new Object[]{type.getSimpleName()});

        int count = 0;

        Session session = null;
        try {
            Class<? extends RObject> hqlType = ClassMapper.getHQLTypeClass(type);

            session = transactionHelper.beginReadOnlyTransaction();
            Number longCount;
            if (query == null || query.getFilter() == null) {
                // this is 5x faster than count with 3 inner joins, it can probably improved also for queries which
                // filters uses only properties from concrete entities like RUser, RRole by improving interpreter [lazyman]
                SQLQuery sqlQuery = session.createSQLQuery("SELECT COUNT(*) FROM " + RUtil.getTableName(hqlType));
                longCount = (Number) sqlQuery.uniqueResult();
            } else {
                RQuery rQuery;
                if (isUseNewQueryInterpreter(query)) {
                    QueryEngine2 engine = new QueryEngine2(getConfiguration(), prismContext);
                    rQuery = engine.interpret(query, type, null, true, session);
                } else {
                    QueryEngine engine = new QueryEngine(getConfiguration(), prismContext);
                    rQuery = engine.interpret(query, type, null, true, session);
                }

                longCount = (Number) rQuery.uniqueResult();
            }
            LOGGER.trace("Found {} objects.", longCount);
            count = longCount != null ? longCount.intValue() : 0;
        } catch (QueryException | RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        return count;
    }


    public <T extends ObjectType> SearchResultList<PrismObject<T>> searchObjectsAttempt(Class<T> type, ObjectQuery query,
                                                                                        Collection<SelectorOptions<GetOperationOptions>> options,
                                                                                        OperationResult result) throws SchemaException {
        LOGGER_PERFORMANCE.debug("> search objects {}", new Object[]{type.getSimpleName()});
        List<PrismObject<T>> list = new ArrayList<>();
        Session session = null;
        try {
            session = transactionHelper.beginReadOnlyTransaction();
            RQuery rQuery;

            if (isUseNewQueryInterpreter(query)) {
                QueryEngine2 engine = new QueryEngine2(getConfiguration(), prismContext);
                rQuery = engine.interpret(query, type, options, false, session);
            } else {
                QueryEngine engine = new QueryEngine(getConfiguration(), prismContext);
                rQuery = engine.interpret(query, type, options, false, session);
            }

            List<GetObjectResult> objects = rQuery.list();
            LOGGER.trace("Found {} objects, translating to JAXB.", new Object[]{(objects != null ? objects.size() : 0)});

            for (GetObjectResult object : objects) {
                PrismObject<T> prismObject = updateLoadedObject(object, type, options, session);
                list.add(prismObject);
            }

            session.getTransaction().commit();
        } catch (QueryException | RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        return new SearchResultList<PrismObject<T>>(list);
    }

    public <C extends Containerable> SearchResultList<C> searchContainersAttempt(Class<C> type, ObjectQuery query,
                                                                                 Collection<SelectorOptions<GetOperationOptions>> options,
                                                                                 OperationResult result) throws SchemaException {

        if (!(AccessCertificationCaseType.class.equals(type))) {
            throw new UnsupportedOperationException("Only AccessCertificationCaseType is supported here now.");
        }

        LOGGER_PERFORMANCE.debug("> search containers {}", new Object[]{type.getSimpleName()});
        List<C> list = new ArrayList<>();
        Session session = null;
        try {
            session = transactionHelper.beginReadOnlyTransaction();

            QueryEngine2 engine = new QueryEngine2(getConfiguration(), prismContext);
            RQuery rQuery = engine.interpret(query, type, options, false, session);

            List<GetObjectResult> items = rQuery.list();
            LOGGER.trace("Found {} items, translating to JAXB.", items.size());

            for (GetObjectResult item : items) {
                C value = (C) caseHelper.updateLoadedCertificationCase(item, options, session);
                list.add(value);
            }

            session.getTransaction().commit();
        } catch (QueryException | RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        return new SearchResultList<C>(list);
    }

    /**
     * This method provides object parsing from String and validation.
     */
    private <T extends ObjectType> PrismObject<T> updateLoadedObject(GetObjectResult result, Class<T> type,
                                                                     Collection<SelectorOptions<GetOperationOptions>> options,
                                                                     Session session) throws SchemaException {

        String xml = RUtil.getXmlFromByteArray(result.getFullObject(), getConfiguration().isUseZip());
        PrismObject<T> prismObject;
        try {
            // "Postel mode": be tolerant what you read. We need this to tolerate (custom) schema changes
            prismObject = prismContext.parseObject(xml, XNodeProcessorEvaluationMode.COMPAT);
        } catch (SchemaException e) {
            LOGGER.debug("Couldn't parse object because of schema exception ({}):\nObject: {}", e, xml);
            throw e;
        } catch (RuntimeException e) {
            LOGGER.debug("Couldn't parse object because of unexpected exception ({}):\nObject: {}", e, xml);
            throw e;
        }

        if (FocusType.class.isAssignableFrom(prismObject.getCompileTimeClass())) {
            if (SelectorOptions.hasToLoadPath(FocusType.F_JPEG_PHOTO, options)) {
                //todo improve, use user.hasPhoto flag and take options into account [lazyman]
                //this is called only when options contains INCLUDE user/jpegPhoto
                Query query = session.getNamedQuery("get.focusPhoto");
                query.setString("oid", prismObject.getOid());
                byte[] photo = (byte[]) query.uniqueResult();
                if (photo != null) {
                    PrismProperty property = prismObject.findOrCreateProperty(FocusType.F_JPEG_PHOTO);
                    property.setRealValue(photo);
                }
            }
        } else if (ShadowType.class.equals(prismObject.getCompileTimeClass())) {
            //we store it because provisioning now sends it to repo, but it should be transient
            prismObject.removeContainer(ShadowType.F_ASSOCIATION);

            LOGGER.debug("Loading definitions for shadow attributes.");

            Short[] counts = result.getCountProjection();
            Class[] classes = GetObjectResult.EXT_COUNT_CLASSES;

            for (int i = 0; i < classes.length; i++) {
                if (counts[i] == null || counts[i] == 0) {
                    continue;
                }

                applyShadowAttributeDefinitions(classes[i], prismObject, session);
            }
            LOGGER.debug("Definitions for attributes loaded. Counts: {}", Arrays.toString(counts));
        } else if (LookupTableType.class.equals(prismObject.getCompileTimeClass())) {
            lookupTableHelper.updateLoadedLookupTable(prismObject, options, session);
        } else if (AccessCertificationCampaignType.class.equals(prismObject.getCompileTimeClass())) {
            caseHelper.updateLoadedCampaign(prismObject, options, session);
        }

        nameResolutionHelper.resolveNamesIfRequested(session, prismObject.getValue(), options);
        validateObjectType(prismObject, type);

        return prismObject;
    }


    private void applyShadowAttributeDefinitions(Class<? extends RAnyValue> anyValueType,
                                                 PrismObject object, Session session) throws SchemaException {

        PrismContainer attributes = object.findContainer(ShadowType.F_ATTRIBUTES);

        Query query = session.getNamedQuery("getDefinition." + anyValueType.getSimpleName());
        query.setParameter("oid", object.getOid());
        query.setParameter("ownerType", RObjectExtensionType.ATTRIBUTES);

        List<Object[]> values = query.list();
        if (values == null || values.isEmpty()) {
            return;
        }

        for (Object[] value : values) {
            QName name = RUtil.stringToQName((String) value[0]);
            QName type = RUtil.stringToQName((String) value[1]);
            Item item = attributes.findItem(name);

            // A switch statement used to be here
            // but that caused strange trouble with OpenJDK. This if-then-else works.
            if (item.getDefinition() == null) {
                RValueType rValType = (RValueType) value[2];
                if (rValType == RValueType.PROPERTY) {
                    PrismPropertyDefinition<Object> def = new PrismPropertyDefinition<Object>(name, type, object.getPrismContext());
                    item.applyDefinition(def, true);
                } else if (rValType == RValueType.REFERENCE) {
                    PrismReferenceDefinition def = new PrismReferenceDefinition(name, type, object.getPrismContext());
                    item.applyDefinition(def, true);
                } else {
                    throw new UnsupportedOperationException("Unsupported value type " + rValType);
                }
            }
        }
    }

    public <T extends ShadowType> List<PrismObject<T>> listResourceObjectShadowsAttempt(
            String resourceOid, Class<T> resourceObjectShadowType, OperationResult result)
            throws ObjectNotFoundException, SchemaException {

        LOGGER_PERFORMANCE.debug("> list resource object shadows {}, for resource oid={}",
                new Object[]{resourceObjectShadowType.getSimpleName(), resourceOid});
        List<PrismObject<T>> list = new ArrayList<>();
        Session session = null;
        try {
            session = transactionHelper.beginReadOnlyTransaction();
            Query query = session.getNamedQuery("listResourceObjectShadows");
            query.setString("oid", resourceOid);
            query.setResultTransformer(GetObjectResult.RESULT_TRANSFORMER);

            List<GetObjectResult> shadows = query.list();
            LOGGER.debug("Query returned {} shadows, transforming to JAXB types.",
                    new Object[]{(shadows != null ? shadows.size() : 0)});

            if (shadows != null) {
                for (GetObjectResult shadow : shadows) {
                    PrismObject<T> prismObject = updateLoadedObject(shadow, resourceObjectShadowType, null, session);
                    list.add(prismObject);
                }
            }
            session.getTransaction().commit();
        } catch (SchemaException | RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        return list;
    }

    private <T extends ObjectType> void validateObjectType(PrismObject<T> prismObject, Class<T> type)
            throws SchemaException {
        if (prismObject == null || !type.isAssignableFrom(prismObject.getCompileTimeClass())) {
            throw new SchemaException("Expected to find '" + type.getSimpleName() + "' but found '"
                    + prismObject.getCompileTimeClass().getSimpleName() + "' (" + prismObject.toDebugName()
                    + "). Bad OID in a reference?");
        }
        if (InternalsConfig.consistencyChecks) {
            prismObject.checkConsistence();
        }
        if (InternalsConfig.readEncryptionChecks) {
            CryptoUtil.checkEncrypted(prismObject);
        }
    }

    public <T extends ObjectType> String getVersionAttempt(Class<T> type, String oid, OperationResult result)
            throws ObjectNotFoundException, SchemaException {
        LOGGER_PERFORMANCE.debug("> get version {}, oid={}", new Object[]{type.getSimpleName(), oid});

        String version = null;
        Session session = null;
        try {
            session = transactionHelper.beginReadOnlyTransaction();
            Query query = session.getNamedQuery("getVersion");
            query.setString("oid", oid);

            Number versionLong = (Number) query.uniqueResult();
            if (versionLong == null) {
                throw new ObjectNotFoundException("Object '" + type.getSimpleName()
                        + "' with oid '" + oid + "' was not found.");
            }
            version = versionLong.toString();
        } catch (RuntimeException ex) {
            transactionHelper.handleGeneralRuntimeException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        return version;
    }

    public <T extends ObjectType> void searchObjectsIterativeAttempt(Class<T> type, ObjectQuery query,
                                                                     ResultHandler<T> handler,
                                                                     Collection<SelectorOptions<GetOperationOptions>> options,
                                                                     OperationResult result) throws SchemaException {
        Session session = null;
        try {
            session = transactionHelper.beginReadOnlyTransaction();
            RQuery rQuery;
            if (isUseNewQueryInterpreter(query)) {
                QueryEngine2 engine = new QueryEngine2(getConfiguration(), prismContext);
                rQuery = engine.interpret(query, type, options, false, session);
            } else {
                QueryEngine engine = new QueryEngine(getConfiguration(), prismContext);
                rQuery = engine.interpret(query, type, options, false, session);
            }

            ScrollableResults results = rQuery.scroll(ScrollMode.FORWARD_ONLY);
            try {
                Iterator<GetObjectResult> iterator = new ScrollableResultsIterator(results);
                while (iterator.hasNext()) {
                    GetObjectResult object = iterator.next();

                    PrismObject<T> prismObject = updateLoadedObject(object, type, options, session);
                    if (!handler.handle(prismObject, result)) {
                        break;
                    }
                }
            } finally {
                if (results != null) {
                    results.close();
                }
            }

            session.getTransaction().commit();
        } catch (SchemaException | QueryException | RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }
    }

    public <T extends ObjectType> void searchObjectsIterativeByPaging(Class<T> type, ObjectQuery query,
                                                                      ResultHandler<T> handler,
                                                                      Collection<SelectorOptions<GetOperationOptions>> options,
                                                                      OperationResult result)
            throws SchemaException {

        try {
            ObjectQuery pagedQuery = query != null ? query.clone() : new ObjectQuery();

            int offset;
            int remaining;
            final int batchSize = getConfiguration().getIterativeSearchByPagingBatchSize();

            ObjectPaging paging = pagedQuery.getPaging();

            if (paging == null) {
                paging = ObjectPaging.createPaging(0, 0);        // counts will be filled-in later
                pagedQuery.setPaging(paging);
                offset = 0;
                remaining = repositoryService.countObjects(type, query, result);
            } else {
                offset = paging.getOffset() != null ? paging.getOffset() : 0;
                remaining = paging.getMaxSize() != null ? paging.getMaxSize() : repositoryService.countObjects(type, query, result) - offset;
            }

main:       while (remaining > 0) {
                paging.setOffset(offset);
                paging.setMaxSize(remaining < batchSize ? remaining : batchSize);

                List<PrismObject<T>> objects = repositoryService.searchObjects(type, pagedQuery, options, result);

                for (PrismObject<T> object : objects) {
                    if (!handler.handle(object, result)) {
                        break main;
                    }
                }

                if (objects.size() == 0) {
                    break;                      // should not occur, but let's check for this to avoid endless loops
                }
                offset += objects.size();
                remaining -= objects.size();
            }
        } finally {
            if (result != null && result.isUnknown()) {
                result.computeStatus();
            }
        }
    }

    /**
     * Strictly-sequential version of paged search.
     *
     * Assumptions:
     *  - During processing of returned object(s), any objects can be added, deleted or modified.
     *
     * Guarantees:
     *  - We return each object that existed in the moment of search start:
     *     - exactly once if it was not deleted in the meanwhile,
     *     - at most once otherwise.
     *  - However, we may or may not return any objects that were added during the processing.
     *
     * Constraints:
     *  - There can be no ordering prescribed. We use our own ordering.
     *  - Moreover, for simplicity we disallow any explicit paging.
     *
     *  Implementation is very simple - we fetch objects ordered by OID, and remember last OID fetched.
     *  Obviously no object will be present in output more than once.
     *  Objects that are not deleted will be there exactly once, provided their oid is not changed.
     */
    public <T extends ObjectType> void searchObjectsIterativeByPagingStrictlySequential(
            Class<T> type, ObjectQuery query, ResultHandler<T> handler,
            Collection<SelectorOptions<GetOperationOptions>> options, OperationResult result)
            throws SchemaException {

        try {
            ObjectQuery pagedQuery = query != null ? query.clone() : new ObjectQuery();

            String lastOid = "";
            final int batchSize = getConfiguration().getIterativeSearchByPagingBatchSize();

            if (pagedQuery.getPaging() != null) {
                throw new IllegalArgumentException("Externally specified paging is not supported on strictly sequential iterative search.");
            }

            ObjectPagingAfterOid paging = new ObjectPagingAfterOid();
            pagedQuery.setPaging(paging);
main:       for (;;) {
                paging.setOidGreaterThan(lastOid);
                paging.setMaxSize(batchSize);

                List<PrismObject<T>> objects = repositoryService.searchObjects(type, pagedQuery, options, result);

                for (PrismObject<T> object : objects) {
                    lastOid = object.getOid();
                    if (!handler.handle(object, result)) {
                        break main;
                    }
                }

                if (objects.size() == 0) {
                    break;
                }
            }
        } finally {
            if (result != null && result.isUnknown()) {
                result.computeStatus();
            }
        }
    }

    public boolean isAnySubordinateAttempt(String upperOrgOid, Collection<String> lowerObjectOids) {
        Session session = null;
        try {
            session = transactionHelper.beginTransaction();

            Query query;
            if (lowerObjectOids.size() == 1) {
                query = session.getNamedQuery("isAnySubordinateAttempt.oneLowerOid");
                query.setString("dOid", lowerObjectOids.iterator().next());
            } else {
                query = session.getNamedQuery("isAnySubordinateAttempt.moreLowerOids");
                query.setParameterList("dOids", lowerObjectOids);
            }
            query.setString("aOid", upperOrgOid);

            Number number = (Number) query.uniqueResult();
            return number != null && number.longValue() != 0L;
        } catch (RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, null);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, null);
        }

        throw new SystemException("isAnySubordinateAttempt failed somehow, this really should not happen.");
    }

    public String executeArbitraryQueryAttempt(String queryString, OperationResult result) {
        LOGGER_PERFORMANCE.debug("> execute query {}", queryString);

        Session session = null;
        StringBuffer answer = new StringBuffer();
        try {
            session = transactionHelper.beginReadOnlyTransaction();       // beware, not all databases support read-only transactions!

            Query query = session.createQuery(queryString);
            List results = query.list();
            if (results != null) {
                answer.append("Result: ").append(results.size()).append(" item(s):\n\n");
                for (Object item : results) {
                    if (item instanceof Object[]) {
                        boolean first = true;
                        for (Object item1 : (Object[]) item) {
                            if (first) {
                                first = false;
                            } else {
                                answer.append(",");
                            }
                            answer.append(item1);
                        }
                    } else {
                        answer.append(item);
                    }
                    answer.append("\n");
                }
            }
            session.getTransaction().rollback();
        } catch (RuntimeException ex) {
            transactionHelper.handleGeneralException(ex, session, result);
        } finally {
            transactionHelper.cleanupSessionAndResult(session, result);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Executed query:\n{}\nwith result:\n{}", queryString, answer);
        }

        return answer.toString();
    }

    private boolean isUseNewQueryInterpreter(ObjectQuery query) {
        //return query == null || query.isUseNewQueryInterpreter();
        return true;
    }


}
