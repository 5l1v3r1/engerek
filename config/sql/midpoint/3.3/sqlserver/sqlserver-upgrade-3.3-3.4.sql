ALTER TABLE m_acc_cert_campaign ADD
endTimestamp            DATETIME2,
handlerUri              NVARCHAR(255) COLLATE database_default,
ownerRef_relation       NVARCHAR(157) COLLATE database_default,
ownerRef_targetOid      NVARCHAR(36) COLLATE database_default,
ownerRef_type           INT,
stageNumber             INT,
startTimestamp          DATETIME2,
state                   INT;

ALTER TABLE m_acc_cert_definition ADD
handlerUri NVARCHAR(255) COLLATE database_default,
lastCampaignClosedTimestamp DATETIME2,
lastCampaignStartedTimestamp DATETIME2,
ownerRef_relation NVARCHAR(157) COLLATE database_default,
ownerRef_targetOid NVARCHAR(36) COLLATE database_default,
ownerRef_type INT;

CREATE TABLE m_acc_cert_case (
  id                       INT                                   NOT NULL,
  owner_oid                NVARCHAR(36) COLLATE database_default NOT NULL,
  administrativeStatus     INT,
  archiveTimestamp         DATETIME2,
  disableReason            NVARCHAR(255) COLLATE database_default,
  disableTimestamp         DATETIME2,
  effectiveStatus          INT,
  enableTimestamp          DATETIME2,
  validFrom                DATETIME2,
  validTo                  DATETIME2,
  validityChangeTimestamp  DATETIME2,
  validityStatus           INT,
  currentResponse          INT,
  currentStageNumber       INT,
  fullObject               VARBINARY(MAX),
  objectRef_relation       NVARCHAR(157) COLLATE database_default,
  objectRef_targetOid      NVARCHAR(36) COLLATE database_default,
  objectRef_type           INT,
  orgRef_relation          NVARCHAR(157) COLLATE database_default,
  orgRef_targetOid         NVARCHAR(36) COLLATE database_default,
  orgRef_type              INT,
  remediedTimestamp        DATETIME2,
  reviewDeadline           DATETIME2,
  reviewRequestedTimestamp DATETIME2,
  targetRef_relation       NVARCHAR(157) COLLATE database_default,
  targetRef_targetOid      NVARCHAR(36) COLLATE database_default,
  targetRef_type           INT,
  tenantRef_relation       NVARCHAR(157) COLLATE database_default,
  tenantRef_targetOid      NVARCHAR(36) COLLATE database_default,
  tenantRef_type           INT,
  PRIMARY KEY (id, owner_oid)
);

CREATE TABLE m_acc_cert_case_reference (
  owner_id        INT                                    NOT NULL,
  owner_owner_oid NVARCHAR(36) COLLATE database_default  NOT NULL,
  reference_type  INT                                    NOT NULL,
  relation        NVARCHAR(157) COLLATE database_default NOT NULL,
  targetOid       NVARCHAR(36) COLLATE database_default  NOT NULL,
  containerType   INT,
  PRIMARY KEY (owner_id, owner_owner_oid, reference_type, relation, targetOid)
);

CREATE TABLE m_acc_cert_decision (
  id                    INT                                   NOT NULL,
  owner_id              INT                                   NOT NULL,
  owner_owner_oid       NVARCHAR(36) COLLATE database_default NOT NULL,
  reviewerComment       NVARCHAR(255) COLLATE database_default,
  response              INT,
  reviewerRef_relation  NVARCHAR(157) COLLATE database_default,
  reviewerRef_targetOid NVARCHAR(36) COLLATE database_default,
  reviewerRef_type      INT,
  stageNumber           INT                                   NOT NULL,
  timestamp             DATETIME2,
  PRIMARY KEY (id, owner_id, owner_owner_oid)
);

CREATE INDEX iCaseObjectRefTargetOid ON m_acc_cert_case (objectRef_targetOid);

CREATE INDEX iCaseTargetRefTargetOid ON m_acc_cert_case (targetRef_targetOid);

CREATE INDEX iCaseTenantRefTargetOid ON m_acc_cert_case (tenantRef_targetOid);

CREATE INDEX iCaseOrgRefTargetOid ON m_acc_cert_case (orgRef_targetOid);

CREATE INDEX iCaseReferenceTargetOid ON m_acc_cert_case_reference (targetOid);

ALTER TABLE m_acc_cert_decision
ADD CONSTRAINT uc_case_stage_reviewer UNIQUE (owner_owner_oid, owner_id, stageNumber, reviewerRef_targetOid);

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

