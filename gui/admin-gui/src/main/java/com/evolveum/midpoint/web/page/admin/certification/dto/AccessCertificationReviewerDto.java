package com.evolveum.midpoint.web.page.admin.certification.dto;

import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationApprovalStrategyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;

import java.io.Serializable;
import java.util.List;

/**
 * Created by Kate on 15.12.2015.
 */
public class AccessCertificationReviewerDto implements Serializable {
    public static final String F_NAME =  "name";
    public static final String F_DESCRIPTION =  "description";
    public static final String F_USE_TARGET_OWNER =  "useTargetOwner";
    public static final String F_USE_TARGET_APPROVER =  "useTargetApprover";
    public static final String F_USE_OBJECT_OWNER =  "useObjectOwner";
    public static final String F_USE_OBJECT_APPROVER =  "useObjectApprover";
    public static final String F_USE_OBJECT_MANAGER =  "useObjectManager";
    public static final String F_REVIEWER_EXPRESSION =  "reviewerExpression";
    public static final String F_DEF_REVIEWER_REF =  "defaultReviewerRef";
    public static final String F_ADDITIONAL_REVIEWER_REF =  "additionalReviewerRef";
    public static final String F_APPROVAL_STRATEGY =  "approvalStrategy";

    private String name;
    private String description;
    private boolean useTargetOwner;
    private boolean useTargetApprover;
    private boolean useObjectOwner;
    private boolean useObjectApprover;
    private ManagerSearchDto useObjectManager;
    private List<ObjectReferenceType> defaultReviewerRef;
    private List<ObjectReferenceType> additionalReviewerRef;
    private AccessCertificationApprovalStrategyType approvalStrategy;
    public enum ApprovalStrategy {
        ONE_APPROVAL_APPROVES,
        ONE_DENY_DENIES,
        APPROVED_IF_NOT_DENIED,
        ALL_MUST_APPROVE
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isUseTargetOwner() {
        return useTargetOwner;
    }

    public void setUseTargetOwner(boolean useTargetOwner) {
        this.useTargetOwner = useTargetOwner;
    }

    public boolean isUseTargetApprover() {
        return useTargetApprover;
    }

    public void setUseTargetApprover(boolean useTargetApprover) {
        this.useTargetApprover = useTargetApprover;
    }

    public boolean isUseObjectOwner() {
        return useObjectOwner;
    }

    public void setUseObjectOwner(boolean useObjectOwner) {
        this.useObjectOwner = useObjectOwner;
    }

    public boolean isUseObjectApprover() {
        return useObjectApprover;
    }

    public void setUseObjectApprover(boolean useObjectApprover) {
        this.useObjectApprover = useObjectApprover;
    }

    public ManagerSearchDto getUseObjectManager() {
        return useObjectManager;
    }

    public void setUseObjectManager(ManagerSearchDto useObjectManager) {
        this.useObjectManager = useObjectManager;
    }

    public List<ObjectReferenceType> getDefaultReviewerRef() {
        return defaultReviewerRef;
    }

    public void setDefaultReviewerRef(List<ObjectReferenceType> defaultReviewerRef) {
        this.defaultReviewerRef = defaultReviewerRef;
    }

    public List<ObjectReferenceType> getAdditionalReviewerRef() {
        return additionalReviewerRef;
    }

    public void setAdditionalReviewerRef(List<ObjectReferenceType> additionalReviewerRef) {
        this.additionalReviewerRef = additionalReviewerRef;
    }

    public AccessCertificationApprovalStrategyType getApprovalStrategy() {
        return approvalStrategy;
    }

    public void setApprovalStrategy(AccessCertificationApprovalStrategyType approvalStrategy) {
        this.approvalStrategy = approvalStrategy;
    }
}
