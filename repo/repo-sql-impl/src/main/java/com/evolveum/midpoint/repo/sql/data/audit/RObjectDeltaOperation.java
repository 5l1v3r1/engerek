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
 * Portions Copyrighted 2013 [name of copyright owner]
 */

package com.evolveum.midpoint.repo.sql.data.audit;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.repo.sql.data.common.OperationResult;
import com.evolveum.midpoint.repo.sql.data.common.enums.ROperationResultStatusType;
import com.evolveum.midpoint.repo.sql.util.DtoTranslationException;
import com.evolveum.midpoint.repo.sql.util.RUtil;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.prism.xml.ns._public.types_2.ObjectDeltaType;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Type;

import javax.persistence.*;

/**
 * @author lazyman
 */
@Entity
@IdClass(RObjectDeltaOperationId.class)
@Table(name = "m_audit_delta")
public class RObjectDeltaOperation implements OperationResult {

    private RAuditEventRecord record;
    private Long recordId;

    //delta
    private String delta;
    private String checksum;
    //operation result
    private String operation;
    private ROperationResultStatusType status;
    private Long token;
    private String messageCode;
    private String message;
    private String details;

    private String localizedMessage;
    private String params;
    private String partialResults;


    @ForeignKey(name = "none")
    @MapsId("record")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "record_id", referencedColumnName = "id")
    })
    public RAuditEventRecord getRecord() {
        return record;
    }

    @Id
    @Column(name = "record_id")
    public Long getRecordId() {
        if (recordId == null && record != null) {
            recordId = record.getId();
        }
        return recordId;
    }

    /**
     * This method is used for content comparing when querying database (we don't want to compare clob values).
     *
     * @return md5 hash of {@link RObjectDeltaOperation#delta} and {@link RObjectDeltaOperation#partialResults}
     */
    @Id
    @Column(length = 32, name = "checksum")
    public String getChecksum() {
        if (checksum == null) {
            recomputeChecksum();
        }

        return checksum;
    }

    @Lob
    @Type(type = RUtil.LOB_STRING_TYPE)
    public String getDelta() {
        return delta;
    }

    @Lob
    @Type(type = RUtil.LOB_STRING_TYPE)
    public String getParams() {
        return params;
    }

    @Lob
    @Type(type = RUtil.LOB_STRING_TYPE)
    public String getPartialResults() {
        return partialResults;
    }

    @Enumerated(EnumType.ORDINAL)
    public ROperationResultStatusType getStatus() {
        return status;
    }

    @Column(nullable = true)
    public Long getToken() {
        return token;
    }

    @Lob
    @Type(type = RUtil.LOB_STRING_TYPE)
    public String getDetails() {
        return details;
    }

    @Lob
    @Type(type = RUtil.LOB_STRING_TYPE)
    public String getLocalizedMessage() {
        return localizedMessage;
    }

    @Lob
    @Type(type = RUtil.LOB_STRING_TYPE)
    public String getMessage() {
        return message;
    }

    public String getMessageCode() {
        return messageCode;
    }

    @Lob
    @Type(type = RUtil.LOB_STRING_TYPE)
    public String getOperation() {
        return operation;
    }

    public void setRecord(RAuditEventRecord record) {
        this.record = record;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public void setChecksum(String checksum) {
        //checksum is always computed from delta and result, this setter is only to satisfy hibernate
    }

    public void setDelta(String delta) {
        this.delta = delta;

        recomputeChecksum();
    }

    public void setOperation(String operation) {
        this.operation = operation;

        recomputeChecksum();
    }

    public void setStatus(ROperationResultStatusType status) {
        this.status = status;
    }

    public void setToken(Long token) {
        this.token = token;
    }

    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
    }

    public void setMessage(String message) {
        this.message = message;

        recomputeChecksum();
    }

    public void setDetails(String details) {
        this.details = details;

        recomputeChecksum();
    }

    public void setLocalizedMessage(String localizedMessage) {
        this.localizedMessage = localizedMessage;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public void setPartialResults(String partialResults) {
        this.partialResults = partialResults;

        recomputeChecksum();
    }

    @Transient
    private void recomputeChecksum() {
        checksum = RUtil.computeChecksum(delta, operation, message, details, partialResults);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RObjectDeltaOperation that = (RObjectDeltaOperation) o;

        if (getChecksum() != null ? !getChecksum().equals(that.getChecksum()) : that.getChecksum() != null)
            return false;
        if (delta != null ? !delta.equals(that.delta) : that.delta != null) return false;
        if (details != null ? !details.equals(that.details) : that.details != null) return false;
        if (localizedMessage != null ? !localizedMessage.equals(that.localizedMessage) : that.localizedMessage != null)
            return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (messageCode != null ? !messageCode.equals(that.messageCode) : that.messageCode != null) return false;
        if (operation != null ? !operation.equals(that.operation) : that.operation != null) return false;
        if (params != null ? !params.equals(that.params) : that.params != null) return false;
        if (partialResults != null ? !partialResults.equals(that.partialResults) : that.partialResults != null)
            return false;
        if (status != that.status) return false;
        if (token != null ? !token.equals(that.token) : that.token != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result1 = delta != null ? delta.hashCode() : 0;
        result1 = 31 * result1 + (getChecksum() != null ? getChecksum().hashCode() : 0);
        result1 = 31 * result1 + (operation != null ? operation.hashCode() : 0);
        result1 = 31 * result1 + (status != null ? status.hashCode() : 0);
        result1 = 31 * result1 + (token != null ? token.hashCode() : 0);
        result1 = 31 * result1 + (messageCode != null ? messageCode.hashCode() : 0);
        result1 = 31 * result1 + (message != null ? message.hashCode() : 0);
        result1 = 31 * result1 + (details != null ? details.hashCode() : 0);
        result1 = 31 * result1 + (localizedMessage != null ? localizedMessage.hashCode() : 0);
        result1 = 31 * result1 + (params != null ? params.hashCode() : 0);
        result1 = 31 * result1 + (partialResults != null ? partialResults.hashCode() : 0);
        return result1;
    }

    public static RObjectDeltaOperation toRepo(RAuditEventRecord record, ObjectDeltaOperation operation,
                                               PrismContext prismContext) throws DtoTranslationException {
        RObjectDeltaOperation auditDelta = new RObjectDeltaOperation();
        auditDelta.setRecord(record);

        try {
            if (operation.getObjectDelta() != null) {
                ObjectDeltaType xmlDelta = DeltaConvertor.toObjectDeltaType(operation.getObjectDelta());
                auditDelta.setDelta(RUtil.toRepo(xmlDelta, prismContext));
            }

            if (operation.getExecutionResult() != null) {
                RUtil.copyResultFromJAXB(operation.getExecutionResult().createOperationResultType(),
                        auditDelta, prismContext);
            }
        } catch (Exception ex) {
            throw new DtoTranslationException(ex.getMessage(), ex);
        }

        return auditDelta;
    }
}
