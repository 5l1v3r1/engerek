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

package com.evolveum.midpoint.web.page.admin.roles;

import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.button.AjaxLinkButton;
import com.evolveum.midpoint.web.component.button.ButtonType;
import com.evolveum.midpoint.web.component.data.ObjectDataProvider;
import com.evolveum.midpoint.web.component.data.TablePanel;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.LinkColumn;
import com.evolveum.midpoint.web.component.dialog.ConfirmationDialog;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lazyman
 */
public class PageRoles extends PageAdminRoles {

    private static final Trace LOGGER = TraceManager.getTrace(PageRoles.class);
    private static final String DOT_CLASS = PageRoles.class.getName() + ".";
    private static final String OPERATION_DELETE_ROLES = DOT_CLASS + "deleteRoles";

    private static final String DIALOG_CONFIRM_DELETE = "confirmDeletePopup";
    private static final String ID_MAIN_FORM = "mainForm";

    public PageRoles() {
        initLayout();
    }

    private void initLayout() {
        Form mainForm = new Form(ID_MAIN_FORM);
        add(mainForm);

        List<IColumn<RoleType, String>> columns = new ArrayList<IColumn<RoleType, String>>();

        IColumn column = new CheckBoxHeaderColumn<RoleType>();
        columns.add(column);

        column = new LinkColumn<SelectableBean<RoleType>>(createStringResource("pageRoles.name"), "name", "value.name") {

            @Override
            public void onClick(AjaxRequestTarget target, IModel<SelectableBean<RoleType>> rowModel) {
                RoleType role = rowModel.getObject().getValue();
                roleDetailsPerformed(target, role.getOid());
            }
        };
        columns.add(column);

        column = new PropertyColumn(createStringResource("pageRoles.description"), "value.description");
        columns.add(column);

        TablePanel table = new TablePanel<RoleType>("table", new ObjectDataProvider(PageRoles.this, RoleType.class), columns);
        table.setOutputMarkupId(true);
        mainForm.add(table);

        AjaxLinkButton delete = new AjaxLinkButton("delete", ButtonType.NEGATIVE, createStringResource("pageRoles.button.delete")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                deletePerformed(target);
            }
        };
        mainForm.add(delete);

        add(new ConfirmationDialog(DIALOG_CONFIRM_DELETE, createStringResource("pageRoles.dialog.title.confirmDelete"),
                createDeleteConfirmString()) {

            @Override
            public void yesPerformed(AjaxRequestTarget target) {
                close(target);
                deleteConfirmedPerformed(target);
            }
        });
    }

    private IModel<String> createDeleteConfirmString() {
        return new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                return createStringResource("pageRoles.message.deleteRoleConfirm",
                        getSelectedRoles().size()).getString();
            }
        };
    }

    private TablePanel getRoleTable() {
        return (TablePanel) get(ID_MAIN_FORM + ":table");
    }

    private ObjectDataProvider<RoleType> getRoleDataProvider() {
        DataTable table = getRoleTable().getDataTable();
        return (ObjectDataProvider<RoleType>) table.getDataProvider();
    }

    private List<RoleType> getSelectedRoles() {
        ObjectDataProvider<RoleType> provider = getRoleDataProvider();

        List<SelectableBean<RoleType>> rows = provider.getAvailableData();
        List<RoleType> selected = new ArrayList<RoleType>();
        for (SelectableBean<RoleType> row : rows) {
            if (row.isSelected()) {
                selected.add(row.getValue());
            }
        }

        return selected;
    }

    private void deletePerformed(AjaxRequestTarget target) {
        List<RoleType> selected = getSelectedRoles();
        if (selected.isEmpty()) {
            warn(getString("pageRoles.message.nothingSelected"));
            target.add(getFeedbackPanel());
            return;
        }

        ModalWindow dialog = (ModalWindow) get(DIALOG_CONFIRM_DELETE);
        dialog.show(target);
    }

    private void deleteConfirmedPerformed(AjaxRequestTarget target) {
        List<RoleType> selected = getSelectedRoles();

        OperationResult result = new OperationResult(OPERATION_DELETE_ROLES);
        for (RoleType role : selected) {
            try {
                Task task = createSimpleTask(OPERATION_DELETE_ROLES);

                ObjectDelta delta = ObjectDelta.createDeleteDelta(RoleType.class, role.getOid(), getPrismContext());
                getModelService().executeChanges(WebMiscUtil.createDeltaCollection(delta), null, task, result);
            } catch (Exception ex) {
                result.recordPartialError("Couldn't delete role.", ex);
                LoggingUtils.logException(LOGGER, "Couldn't delete role", ex);
            }
        }
        
        if (result.isUnknown()) {
        	result.recomputeStatus("Error occurred during role deleting.");
        }

        if (result.isSuccess()) {
            result.recordStatus(OperationResultStatus.SUCCESS, "The role(s) have been successfully deleted.");
        }

        ObjectDataProvider provider = getRoleDataProvider();
        provider.clearCache();
        
        showResult(result);
        target.add(getFeedbackPanel());
        target.add(getRoleTable());
    }

    private void roleDetailsPerformed(AjaxRequestTarget target, String oid) {
        PageParameters parameters = new PageParameters();
        parameters.add(PageRole.PARAM_ROLE_ID, oid);
        setResponsePage(PageRole.class, parameters);
    }
}
