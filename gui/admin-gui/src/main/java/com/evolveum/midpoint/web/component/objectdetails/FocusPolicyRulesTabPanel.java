/*
 * Copyright (c) 2010-2017 Evolveum
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
package com.evolveum.midpoint.web.component.objectdetails;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.web.component.assignment.AssignmentDataTablePanel;
import com.evolveum.midpoint.web.component.assignment.AssignmentEditorDto;
import com.evolveum.midpoint.web.component.assignment.PolicyRuleDetailsPanel;
import com.evolveum.midpoint.web.component.assignment.PolicyRulesPanel;
import com.evolveum.midpoint.web.component.form.Form;
import com.evolveum.midpoint.web.component.prism.ObjectWrapper;
import com.evolveum.midpoint.web.page.admin.PageAdminFocus;
import com.evolveum.midpoint.web.page.admin.users.component.AssignmentPreviewDialog;
import com.evolveum.midpoint.web.page.admin.users.component.AssignmentsPreviewDto;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.IModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by honchar.
 */
public class FocusPolicyRulesTabPanel <F extends FocusType> extends AbstractObjectTabPanel{
    private static final long serialVersionUID = 1L;
    private LoadableModel<List<AssignmentEditorDto>> policyRulesModel;
    private static final String ID_POLICY_RULES_CONTAINER = "policyRulesContainer";
    private static final String ID_POLICY_RULES_PANEL = "policyRulesPanel";
    private static final String DOT_CLASS = FocusAssignmentsTabPanel.class.getName() + ".";

    private PolicyRulesPanel policyRulesPanel = null;

    public FocusPolicyRulesTabPanel(String id, Form mainForm, LoadableModel<ObjectWrapper<F>> focusWrapperModel,
                                    LoadableModel<List<AssignmentEditorDto>> policyRulesModel, PageBase page) {
        super(id, mainForm, focusWrapperModel, page);
        this.policyRulesModel = policyRulesModel;
        initLayout();
    }

    private void initLayout() {
        WebMarkupContainer policyRules = new WebMarkupContainer(ID_POLICY_RULES_CONTAINER);
        policyRules.setOutputMarkupId(true);
        add(policyRules);

        initPolicyRulesPanel();
        policyRules.add(policyRulesPanel);
    }

    private void initPolicyRulesPanel(){
        policyRulesPanel = new PolicyRulesPanel(ID_POLICY_RULES_PANEL, policyRulesModel, pageBase){
            private static final long serialVersionUID = 1L;

            @Override
            protected void assignmentDetailsPerformed(IModel<AssignmentEditorDto> policyRuleModel, PageBase pageBase, AjaxRequestTarget target){
                FocusPolicyRulesTabPanel.this.assignmentDetailsPerformed(policyRuleModel, pageBase, target);
            }
        };
    }

    private void assignmentDetailsPerformed(IModel<AssignmentEditorDto> policyRuleModel, PageBase pageBase, AjaxRequestTarget target){
        PolicyRuleDetailsPanel detailsPanel = new PolicyRuleDetailsPanel(ID_POLICY_RULES_PANEL, policyRuleModel, pageBase){
            private static final long serialVersionUID = 1L;

            @Override
            protected void redirectBack(AjaxRequestTarget target) {
                redirectBackFromAssignmentDetailsPanelPerformed(target);
            }
        };
        getPolicyRulesContainer().replace(detailsPanel);
        target.add(getPolicyRulesContainer());
    }

    private void redirectBackFromAssignmentDetailsPanelPerformed(AjaxRequestTarget target){
        if (policyRulesPanel == null){
            initPolicyRulesPanel();
        }
        getPolicyRulesContainer().replace(policyRulesPanel);
        target.add(getPolicyRulesContainer());
    }

    private WebMarkupContainer getPolicyRulesContainer(){
        return (WebMarkupContainer) get(ID_POLICY_RULES_CONTAINER);
    }
}
