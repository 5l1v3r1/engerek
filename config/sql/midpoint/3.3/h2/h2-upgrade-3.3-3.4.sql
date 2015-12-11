ALTER TABLE m_acc_cert_campaign ADD endTimestamp TIMESTAMP;
ALTER TABLE m_acc_cert_campaign ADD handlerUri VARCHAR(255);
ALTER TABLE m_acc_cert_campaign ADD ownerRef_relation VARCHAR(157);
ALTER TABLE m_acc_cert_campaign ADD ownerRef_targetOid VARCHAR(36);
ALTER TABLE m_acc_cert_campaign ADD ownerRef_type INTEGER;
ALTER TABLE m_acc_cert_campaign ADD stageNumber INTEGER;
ALTER TABLE m_acc_cert_campaign ADD startTimestamp TIMESTAMP;
ALTER TABLE m_acc_cert_campaign ADD state INTEGER;

ALTER TABLE m_acc_cert_definition ADD handlerUri VARCHAR(255);
ALTER TABLE m_acc_cert_definition ADD lastCampaignClosedTimestamp TIMESTAMP;
ALTER TABLE m_acc_cert_definition ADD lastCampaignStartedTimestamp TIMESTAMP;
ALTER TABLE m_acc_cert_definition ADD ownerRef_relation VARCHAR(157);
ALTER TABLE m_acc_cert_definition ADD ownerRef_targetOid VARCHAR(36);
ALTER TABLE m_acc_cert_definition ADD ownerRef_type INTEGER;

CREATE TABLE m_acc_cert_case (
  id                       INTEGER     NOT NULL,
  owner_oid                VARCHAR(36) NOT NULL,
  administrativeStatus     INTEGER,
  archiveTimestamp         TIMESTAMP,
  disableReason            VARCHAR(255),
  disableTimestamp         TIMESTAMP,
  effectiveStatus          INTEGER,
  enableTimestamp          TIMESTAMP,
  validFrom                TIMESTAMP,
  validTo                  TIMESTAMP,
  validityChangeTimestamp  TIMESTAMP,
  validityStatus           INTEGER,
  currentResponse          INTEGER,
  currentStageNumber       INTEGER,
  fullObject               BLOB,
  objectRef_relation       VARCHAR(157),
  objectRef_targetOid      VARCHAR(36),
  objectRef_type           INTEGER,
  orgRef_relation          VARCHAR(157),
  orgRef_targetOid         VARCHAR(36),
  orgRef_type              INTEGER,
  remediedTimestamp        TIMESTAMP,
  reviewDeadline           TIMESTAMP,
  reviewRequestedTimestamp TIMESTAMP,
  targetRef_relation       VARCHAR(157),
  targetRef_targetOid      VARCHAR(36),
  targetRef_type           INTEGER,
  tenantRef_relation       VARCHAR(157),
  tenantRef_targetOid      VARCHAR(36),
  tenantRef_type           INTEGER,
  PRIMARY KEY (id, owner_oid)
);

CREATE TABLE m_acc_cert_case_reference (
  owner_id        INTEGER      NOT NULL,
  owner_owner_oid VARCHAR(36)  NOT NULL,
  reference_type  INTEGER      NOT NULL,
  relation        VARCHAR(157) NOT NULL,
  targetOid       VARCHAR(36)  NOT NULL,
  containerType   INTEGER,
  PRIMARY KEY (owner_id, owner_owner_oid, reference_type, relation, targetOid)
);

CREATE TABLE m_acc_cert_decision (
  id                    INTEGER     NOT NULL,
  owner_id              INTEGER     NOT NULL,
  owner_owner_oid       VARCHAR(36) NOT NULL,
  reviewerComment       VARCHAR(255),
  response              INTEGER,
  reviewerRef_relation  VARCHAR(157),
  reviewerRef_targetOid VARCHAR(36),
  reviewerRef_type      INTEGER,
  stageNumber           INTEGER     NOT NULL,
  timestamp             TIMESTAMP,
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

