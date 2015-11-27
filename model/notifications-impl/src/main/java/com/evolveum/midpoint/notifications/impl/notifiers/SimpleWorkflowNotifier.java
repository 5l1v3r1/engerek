/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.notifications.impl.notifiers;

import com.evolveum.midpoint.notifications.api.events.Event;
import com.evolveum.midpoint.notifications.api.events.WorkItemEvent;
import com.evolveum.midpoint.notifications.api.events.WorkflowEvent;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.GeneralNotifierType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SimpleWorkflowNotifierType;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.Date;

/**
 * Default implementation of a notifier dealing with workflow events (related to both work items and process instances).
 *
 * @author mederly
 */
@Component
public class SimpleWorkflowNotifier extends GeneralNotifier {

    private static final Trace LOGGER = TraceManager.getTrace(SimpleWorkflowNotifier.class);

    @PostConstruct
    public void init() {
        register(SimpleWorkflowNotifierType.class);
    }

    @Override
    protected boolean quickCheckApplicability(Event event, GeneralNotifierType generalNotifierType, OperationResult result) {
        if (!(event instanceof WorkflowEvent)) {
            //LOGGER.trace("SimpleWorkflowNotifier is not applicable for this kind of event, continuing in the handler chain; event class = " + event.getClass());
			LOGGER.trace("Bu tip olay için SimpleWorkflowNotifier uygulanamaz, işleyici zincirine devam ediliyor; olay sınıfı = "+ event.getClass());
            return false;
        }
        return true;
    }

    @Override
    protected String getSubject(Event event, GeneralNotifierType generalNotifierType, String transport, Task task, OperationResult result) {

        if (event.isAdd()) {
            if (event instanceof WorkItemEvent) {
                return "Yeni bir iş akışı görevi oluşturuldu.";
            } else {
                return "İş akışı süreci başlatıldı.";
            }
        } else {
            if (event instanceof WorkItemEvent) {
                return "İş akışı görevi tamamlandı.";
            } else {
                return "İş akışı süreci bitti.";
            }
        }
    }

    @Override
    protected String getBody(Event event, GeneralNotifierType generalNotifierType, String transport, Task task, OperationResult result) throws SchemaException {

        WorkflowEvent workflowEvent = (WorkflowEvent) event;

        boolean techInfo = Boolean.TRUE.equals(generalNotifierType.isShowTechnicalInformation());

        StringBuilder body = new StringBuilder();

        body.append(getSubject(event, generalNotifierType, transport, task, result));
        body.append("\n\n");

        body.append("Süreç örneği adı: " + workflowEvent.getProcessInstanceName() + "\n");
        if (workflowEvent instanceof WorkItemEvent) {
            WorkItemEvent workItemEvent = (WorkItemEvent) workflowEvent;
            body.append("İş akışı öğesi: ").append(workItemEvent.getWorkItemName()).append("\n");
            ObjectType assigneeType = notificationsUtil.getObjectType(workItemEvent.getAssignee(), result);
            if (assigneeType != null) {
                body.append("Devralan: ").append(assigneeType.getName()).append("\n");
            }
        }
        body.append("\n");
        if (event.isDelete() && workflowEvent.isResultKnown()) {
            body.append("Sonuç: ").append(workflowEvent.isApproved() ? "ONAYLANDI" : "REDDEDİLDİ").append("\n\n");
        }
        body.append("Bildirim oluşturma tarihi: ").append(new Date()).append("\n\n");

        if (techInfo) {
            body.append("----------------------------------------\n");
            body.append("Teknik bilgi:\n\n");
            body.append(workflowEvent.getProcessInstanceState().debugDump());
        }

        return body.toString();
    }

    @Override
    protected Trace getLogger() {
        return LOGGER;
    }

}
