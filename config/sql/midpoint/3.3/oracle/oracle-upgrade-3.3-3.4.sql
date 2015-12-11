ALTER TABLE m_acc_cert_campaign ADD (
endTimestamp TIMESTAMP,
handlerUri VARCHAR2(255 CHAR),
ownerRef_relation VARCHAR2(157 CHAR),
ownerRef_targetOid VARCHAR2(36 CHAR),
ownerRef_type NUMBER(10, 0),
stageNumber NUMBER(10, 0),
startTimestamp TIMESTAMP,
state NUMBER(10, 0));

ALTER TABLE m_acc_cert_definition ADD (
handlerUri VARCHAR2(255 CHAR),
lastCampaignClosedTimestamp TIMESTAMP,
lastCampaignStartedTimestamp TIMESTAMP,
ownerRef_relation VARCHAR2(157 CHAR),
ownerRef_targetOid VARCHAR2(36 CHAR),
ownerRef_type NUMBER(10, 0));

CREATE TABLE m_acc_cert_case (
  id                       NUMBER(10, 0)     NOT NULL,
  owner_oid                VARCHAR2(36 CHAR) NOT NULL,
  administrativeStatus     NUMBER(10, 0),
  archiveTimestamp         TIMESTAMP,
  disableReason            VARCHAR2(255 CHAR),
  disableTimestamp         TIMESTAMP,
  effectiveStatus          NUMBER(10, 0),
  enableTimestamp          TIMESTAMP,
  validFrom                TIMESTAMP,
  validTo                  TIMESTAMP,
  validityChangeTimestamp  TIMESTAMP,
  validityStatus           NUMBER(10, 0),
  currentResponse          NUMBER(10, 0),
  currentStageNumber       NUMBER(10, 0),
  fullObject               BLOB,
  objectRef_relation       VARCHAR2(157 CHAR),
  objectRef_targetOid      VARCHAR2(36 CHAR),
  objectRef_type           NUMBER(10, 0),
  orgRef_relation          VARCHAR2(157 CHAR),
  orgRef_targetOid         VARCHAR2(36 CHAR),
  orgRef_type              NUMBER(10, 0),
  remediedTimestamp        TIMESTAMP,
  reviewDeadline           TIMESTAMP,
  reviewRequestedTimestamp TIMESTAMP,
  targetRef_relation       VARCHAR2(157 CHAR),
  targetRef_targetOid      VARCHAR2(36 CHAR),
  targetRef_type           NUMBER(10, 0),
  tenantRef_relation       VARCHAR2(157 CHAR),
  tenantRef_targetOid      VARCHAR2(36 CHAR),
  tenantRef_type           NUMBER(10, 0),
  PRIMARY KEY (id, owner_oid)
) INITRANS 30;

CREATE TABLE m_acc_cert_case_reference (
  owner_id        NUMBER(10, 0)      NOT NULL,
  owner_owner_oid VARCHAR2(36 CHAR)  NOT NULL,
  reference_type  NUMBER(10, 0)      NOT NULL,
  relation        VARCHAR2(157 CHAR) NOT NULL,
  targetOid       VARCHAR2(36 CHAR)  NOT NULL,
  containerType   NUMBER(10, 0),
  PRIMARY KEY (owner_id, owner_owner_oid, reference_type, relation, targetOid)
) INITRANS 30;

CREATE TABLE m_acc_cert_decision (
  id                    NUMBER(10, 0)     NOT NULL,
  owner_id              NUMBER(10, 0)     NOT NULL,
  owner_owner_oid       VARCHAR2(36 CHAR) NOT NULL,
  reviewerComment       VARCHAR2(255 CHAR),
  response              NUMBER(10, 0),
  reviewerRef_relation  VARCHAR2(157 CHAR),
  reviewerRef_targetOid VARCHAR2(36 CHAR),
  reviewerRef_type      NUMBER(10, 0),
  stageNumber           NUMBER(10, 0)     NOT NULL,
  timestamp             TIMESTAMP,
  PRIMARY KEY (id, owner_id, owner_owner_oid)
) INITRANS 30;

CREATE INDEX iCaseObjectRefTargetOid ON m_acc_cert_case (objectRef_targetOid) INITRANS 30;

CREATE INDEX iCaseTargetRefTargetOid ON m_acc_cert_case (targetRef_targetOid) INITRANS 30;

CREATE INDEX iCaseTenantRefTargetOid ON m_acc_cert_case (tenantRef_targetOid) INITRANS 30;

CREATE INDEX iCaseOrgRefTargetOid ON m_acc_cert_case (orgRef_targetOid) INITRANS 30;

CREATE INDEX iCaseReferenceTargetOid ON m_acc_cert_case_reference (targetOid) INITRANS 30;

ALTER TABLE m_acc_cert_decision
ADD CONSTRAINT uc_case_stage_reviewer UNIQUE (owner_owner_oid, owner_id, stageNumber, reviewerRef_targetOid) INITRANS 30;

ALTER TABLE m_acc_cert_case
ADD CONSTRAINT fk_acc_cert_case_owner
FOREIGN KEY (owner_oid)
REFERENCES m_object;

ALTER TABLE m_acc_cert_case_reference
ADD CONSTRAINT fk_acc_cert_case_ref_owner
FOREIGN KEY (owner_id, owner_owner_oid)
REFERENCES m_acc_cert_case;

ALTER TABLE m_acc_cert_decision
ADD CONSTRAINT fk_acc_cert_decision_owner
FOREIGN KEY (owner_id, owner_owner_oid)
REFERENCES m_acc_cert_case;
