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

package com.evolveum.midpoint.web.component.assignment;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.RelationSelectorAssignablePanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.AjaxIconButton;
import com.evolveum.midpoint.web.component.data.BoxedTablePanel;
import com.evolveum.midpoint.web.component.data.column.*;
import com.evolveum.midpoint.web.component.input.DropDownChoicePanel;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.util.ListDataProvider;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.configuration.component.ChooseTypePanel;
import com.evolveum.midpoint.web.page.admin.dto.ObjectViewDto;
import com.evolveum.midpoint.web.page.admin.users.dto.UserDtoStatus;
import com.evolveum.midpoint.web.page.self.PageSelfProfile;
import com.evolveum.midpoint.web.session.AssignmentsTabStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import javax.jws.WebParam;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.evolveum.midpoint.gui.api.util.WebComponentUtil.safeLongToInteger;

/**
 * Created by honchar.
 */
public class AssignmentDataTablePanel extends AbstractAssignmentListPanel {
    private static final String ID_ASSIGNMENTS = "assignments";
    private static final String ID_RELATION = "relation";
    private static final String ID_ASSIGNMENTS_TABLE = "assignmentsTable";
    private static final String ID_NEW_ASSIGNMENT_BUTTON = "newAssignmentButton";
    private static final String ID_SHOW_ALL_ASSIGNMENTS_BUTTON = "showAllAssignmentsButton";

    private Map<RelationTypes, List<AssignmentEditorDto>> relationAssignmentsMap = new HashMap<>();
    private IModel<RelationTypes> relationModel = Model.of(RelationTypes.MEMBER);

    public AssignmentDataTablePanel(String id, IModel<List<AssignmentEditorDto>> assignmentsModel, PageBase pageBase){
        super(id, assignmentsModel, pageBase);
        initPaging();
        initLayout();
    }

    private void initLayout(){
        WebMarkupContainer assignmentsContainer = new WebMarkupContainer(ID_ASSIGNMENTS);
        assignmentsContainer.setOutputMarkupId(true);
        add(assignmentsContainer);

        addOrReplaceAssignmentsTable(assignmentsContainer);

        AjaxIconButton newObjectIcon = new AjaxIconButton(ID_NEW_ASSIGNMENT_BUTTON, new Model<>("fa fa-plus"),
                createStringResource("MainObjectListPanel.newObject")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                RelationSelectorAssignablePanel panel = new RelationSelectorAssignablePanel(
                        getPageBase().getMainPopupBodyId(), RoleType.class, true, getPageBase()) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void addPerformed(AjaxRequestTarget target, List selected, RelationTypes relation) {
                        addSelectedAssignmentsPerformed(target, selected, relation);
                    }

                };
                panel.setOutputMarkupId(true);
                getPageBase().showMainPopup(panel, target);
            }
        };
        newObjectIcon.add(new VisibleEnableBehaviour(){
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible(){
                return WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_ADMIN_ASSIGN_ACTION_URI);
            }
        });
        assignmentsContainer.add(newObjectIcon);


    }

    protected void showAllAssignments(AjaxRequestTarget target) {
    }

    private void addSelectedAssignmentsPerformed(AjaxRequestTarget target, List<ObjectType> assignmentsList, RelationTypes relation){
        if (assignmentsList == null || assignmentsList.isEmpty()){
                warn(pageBase.getString("AssignmentTablePanel.message.noAssignmentSelected"));
                target.add(getPageBase().getFeedbackPanel());
                return;
        }
        for (ObjectType object : assignmentsList){
            AssignmentEditorDto dto = createAssignmentFromSelectedObjects(object, relation);
            getAssignmentModel().getObject().add(0, dto);
        }
        relationModel.setObject(relation);
        initPaging();
        addOrReplaceAssignmentsTable(getAssignmentsContainer());
        reloadMainFormButtons(target);
        target.add(getAssignmentsContainer());
    }

    private void addOrReplaceAssignmentsTable(WebMarkupContainer assignmentsContainer){
        fillInRelationAssignmentsMap();
        DropDownChoicePanel relation = WebComponentUtil.createEnumPanel(RelationTypes.class, ID_RELATION,
                WebComponentUtil.createReadonlyModelFromEnum(RelationTypes.class), relationModel, this, false);
        relation.getBaseFormComponent().add(new AjaxFormComponentUpdatingBehavior("change") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                relationModel.setObject((RelationTypes)relation.getBaseFormComponent().getConvertedInput());
                addOrReplaceAssignmentsTable(getAssignmentsContainer());
                target.add(AssignmentDataTablePanel.this.get(ID_ASSIGNMENTS).get(ID_ASSIGNMENTS_TABLE));
            }
        });
        relation.setOutputMarkupId(true);
        relation.setOutputMarkupPlaceholderTag(true);
        assignmentsContainer.addOrReplace(relation);

        AjaxButton showAllAssignmentsButton = new AjaxButton(ID_SHOW_ALL_ASSIGNMENTS_BUTTON,
                createStringResource("AssignmentTablePanel.menu.showAllAssignments")) {
            @Override
            public void onClick(AjaxRequestTarget ajaxRequestTarget) {
                showAllAssignments(ajaxRequestTarget);
            }
        };
        assignmentsContainer.addOrReplace(showAllAssignmentsButton);

        ListDataProvider<AssignmentEditorDto> assignmentsProvider = new ListDataProvider<AssignmentEditorDto>(this,
                Model.ofList(relationAssignmentsMap.get(relationModel.getObject())), false){
            private static final long serialVersionUID = 1L;

            @Override
            protected void saveProviderPaging(ObjectQuery query, ObjectPaging paging) {
                getAssignmentsStorage().setPaging(paging);
            }

        };
        BoxedTablePanel<AssignmentEditorDto> assignmentTable = new BoxedTablePanel<AssignmentEditorDto>(ID_ASSIGNMENTS_TABLE,
                assignmentsProvider, initColumns(), UserProfileStorage.TableId.ASSIGNMENTS_TAB_TABLE,
                (int) pageBase.getItemsPerPage(UserProfileStorage.TableId.ASSIGNMENTS_TAB_TABLE)){
            private static final long serialVersionUID = 1L;

            @Override
            protected Item<AssignmentEditorDto> customizeNewRowItem(Item<AssignmentEditorDto> item, IModel<AssignmentEditorDto> model) {
                    item.add(AttributeModifier.append("class", AssignmentsUtil.createAssignmentStatusClassModel(model)));
                return item;
            }

            @Override
            public int getItemsPerPage() {
                return pageBase.getSessionStorage().getUserProfile().getTables().get(UserProfileStorage.TableId.ASSIGNMENTS_TAB_TABLE);
            }

        };
        assignmentTable.setOutputMarkupId(true);
        assignmentTable.setCurrentPage(getAssignmentsStorage().getPaging());
        assignmentsContainer.addOrReplace(assignmentTable);

    }

    private List<IColumn<AssignmentEditorDto, String>> initColumns() {
        List<IColumn<AssignmentEditorDto, String>> columns = new ArrayList<>();

        columns.add(new CheckBoxHeaderColumn<AssignmentEditorDto>());

        columns.add(new IconColumn<AssignmentEditorDto>(Model.of("")){
            private static final long serialVersionUID = 1L;

            @Override
            protected IModel<String> createIconModel(IModel<AssignmentEditorDto> rowModel) {
                if (rowModel.getObject().getType() == null){
                    return Model.of("");
                }
                return Model.of(rowModel.getObject().getType().getIconCssClass());
            }

            @Override
            protected IModel<String> createTitleModel(IModel<AssignmentEditorDto> rowModel) {
                return AssignmentsUtil.createAssignmentIconTitleModel(AssignmentDataTablePanel.this, rowModel.getObject().getType());
            }

        });

        columns.add(new LinkColumn<AssignmentEditorDto>(createStringResource("AssignmentDataTablePanel.targetColumnName")){
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<AssignmentEditorDto>> cellItem, String componentId,
                                     final IModel<AssignmentEditorDto> rowModel) {
                if (rowModel.getObject().getTargetRef() == null){
                    cellItem.add(new Label(componentId, createLinkModel(rowModel)));
                } else {
                    super.populateItem(cellItem, componentId, rowModel);
                }
            }

            @Override
            protected IModel createLinkModel(IModel<AssignmentEditorDto> rowModel) {
                String targetObjectName = rowModel.getObject().getName();
                if (targetObjectName != null && targetObjectName.trim().endsWith("-")){
                    targetObjectName = targetObjectName.substring(0, targetObjectName.lastIndexOf("-"));
                }
                return Model.of(targetObjectName != null ? targetObjectName : "");
            }

            @Override
            public void onClick(AjaxRequestTarget target, IModel<AssignmentEditorDto> rowModel) {
                ObjectReferenceType targetObject = rowModel.getObject().getTargetRef();
                WebComponentUtil.dispatchToObjectDetailsPage(rowModel.getObject().getType().getType(), targetObject.getOid(),
                        AssignmentDataTablePanel.this, true);
            }
        });
        //commented since these columns are not used
//        columns.add(new DirectlyEditablePropertyColumn<AssignmentEditorDto>(createStringResource("AssignmentDataTablePanel.descriptionColumnName"), AssignmentEditorDto.F_DESCRIPTION){
//            private static final long serialVersionUID = 1L;
//
//            @Override
//            public void populateItem(Item<ICellPopulator<AssignmentEditorDto>> cellItem, String componentId,
//                                     final IModel<AssignmentEditorDto> rowModel) {
//                super.populateItem(cellItem, componentId, rowModel);
//                cellItem.add(AssignmentsUtil.getEnableBehavior(rowModel));
//            }
//        });
//        columns.add(new AbstractColumn<AssignmentEditorDto, String>(createStringResource("AssignmentDataTablePanel.organizationColumnName")){
//            private static final long serialVersionUID = 1L;
//
//            @Override
//            public void populateItem(Item<ICellPopulator<AssignmentEditorDto>> cellItem, String componentId, final IModel<AssignmentEditorDto> rowModel) {
//                ObjectQuery orgQuery = QueryBuilder.queryFor(OrgType.class, getPageBase().getPrismContext())
//                        .item(OrgType.F_TENANT).eq(false)
//                        .or().item(OrgType.F_TENANT).isNull()
//                        .build();
//                ChooseTypePanel orgPanel = getChooseOrgPanel(componentId, rowModel, orgQuery);
//                orgPanel.add(visibleIfRoleBehavior(rowModel));
//                cellItem.add(orgPanel);
//                cellItem.add(AssignmentsUtil.getEnableBehavior(rowModel));
//            }
//
//        });
//        columns.add(new AbstractColumn<AssignmentEditorDto, String>(createStringResource("AssignmentDataTablePanel.tenantColumnName")){
//            private static final long serialVersionUID = 1L;
//
//            @Override
//            public void populateItem(Item<ICellPopulator<AssignmentEditorDto>> cellItem, String componentId, final IModel<AssignmentEditorDto> rowModel) {
//                ObjectQuery tenantQuery = QueryBuilder.queryFor(OrgType.class, getPageBase().getPrismContext())
//                        .item(OrgType.F_TENANT).eq(true)
//                        .build();
//                ChooseTypePanel tenantPanel = getChooseOrgPanel(componentId, rowModel, tenantQuery);
//                tenantPanel.add(visibleIfRoleBehavior(rowModel));
//                cellItem.add(tenantPanel);
//                cellItem.add(AssignmentsUtil.getEnableBehavior(rowModel));
//            }
//
//        });
//        columns.add(new LinkColumn<AssignmentEditorDto>(createStringResource("AssignmentDataTablePanel.activationColumnName")) {
//            private static final long serialVersionUID = 1L;
//
//            @Override
//            public void populateItem(Item<ICellPopulator<AssignmentEditorDto>> cellItem, String componentId,
//                                     final IModel<AssignmentEditorDto> rowModel) {
//                super.populateItem(cellItem, componentId, rowModel);
//                cellItem.setEnabled(false);
////                cellItem.add(AssignmentsUtil.getEnableBehavior(rowModel));
//            }
//
//            @Override
//            protected IModel createLinkModel(IModel<AssignmentEditorDto> rowModel) {
//                IModel<String> activationLabelModel = AssignmentsUtil.createActivationTitleModel(rowModel,"", AssignmentDataTablePanel.this);
//                return StringUtils.isEmpty(activationLabelModel.getObject()) ?
//                        createStringResource("AssignmentEditorPanel.undefined") : activationLabelModel;
//            }
//
//            @Override
//            public void onClick(AjaxRequestTarget target, IModel<AssignmentEditorDto> rowModel) {
//                        AssignmentActivationPopupPanel popupPanel = new AssignmentActivationPopupPanel(pageBase.getMainPopupBodyId(), rowModel){
//                            private static final long serialVersionUID = 1L;
//
//                            @Override
//                            protected void reloadDateComponent(AjaxRequestTarget target) {
//                                target.add(getAssignmentsContainer());
//                            }
//                        };
//                        pageBase.showMainPopup(popupPanel, target);
//            }
//        });
        columns.add(new AbstractColumn<AssignmentEditorDto, String>(createStringResource("AssignmentDataTablePanel.activationColumnName")) {
            @Override
            public void populateItem(Item<ICellPopulator<AssignmentEditorDto>> cellItem, String componentId,
                                     final IModel<AssignmentEditorDto> rowModel) {
                IModel<String> activationLabelModel = AssignmentsUtil.createActivationTitleModel(rowModel,"", AssignmentDataTablePanel.this);
                cellItem.add(new Label(componentId, StringUtils.isEmpty(activationLabelModel.getObject()) ?
                        createStringResource("AssignmentEditorPanel.undefined") : activationLabelModel));
            }
        });

        if (WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_ADMIN_UNASSIGN_ACTION_URI)) {
            columns.add(new InlineMenuButtonColumn<AssignmentEditorDto>(getAssignmentMenuActions(), 1, pageBase));
        }
        return columns;
    }

    private List<InlineMenuItem> getAssignmentMenuActions(){
        List<InlineMenuItem> menuItems = new ArrayList<>();
        menuItems.add(new InlineMenuItem(createStringResource("PageBase.button.delete"),
                new Model<Boolean>(true), new Model<Boolean>(true), false,
                new ColumnMenuAction<SelectableBean<ResourceType>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        if (getRowModel() == null){
                            deleteAssignmentPerformed(target, null);
                        } else {
                            AssignmentEditorDto rowDto = (AssignmentEditorDto)getRowModel().getObject();
                            deleteAssignmentPerformed(target, rowDto);
                        }
                    }
                }, 0, GuiStyleConstants.CLASS_DELETE_MENU_ITEM, DoubleButtonColumn.BUTTON_COLOR_CLASS.DANGER.toString()));
        return menuItems;
    }

    private VisibleEnableBehaviour visibleIfRoleBehavior(IModel<AssignmentEditorDto> assignmentModel){
        return new VisibleEnableBehaviour(){
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible(){
                return AssignmentEditorDtoType.ROLE.equals(assignmentModel.getObject().getType());
            }
        };
    }
    private ChooseTypePanel<OrgType> getChooseOrgPanel(String id, IModel<AssignmentEditorDto> model, ObjectQuery query){
        ChooseTypePanel chooseOrgPanel = new ChooseTypePanel(id, new PropertyModel<ObjectViewDto>(model, AssignmentEditorDto.F_TENANT_REF)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected ObjectQuery getChooseQuery() {
                return query;
            }

            @Override
            public Class getObjectTypeClass(){
                return OrgType.class;
            }

            @Override
            protected boolean isSearchEnabled() {
                return true;
            }

            @Override
            protected QName getSearchProperty() {
                return OrgType.F_NAME;
            }
        };
        return chooseOrgPanel;
    }


    private void fillInRelationAssignmentsMap(){
        relationAssignmentsMap = new HashMap<>();
        if (getAssignmentModel() == null || getAssignmentModel().getObject() == null){
            return;
        }
        for (RelationTypes relation : RelationTypes.values()){
            List<AssignmentEditorDto> assignmentList = new ArrayList<>();
            for (AssignmentEditorDto assignmentDto : getAssignmentModel().getObject()){
                if (relation.equals(RelationTypes.MEMBER) && AssignmentEditorDtoType.CONSTRUCTION.equals(assignmentDto.getType())){
                    assignmentList.add(assignmentDto);
                    continue;
                }
                if (AssignmentEditorDtoType.USER.equals(assignmentDto.getType()) || AssignmentEditorDtoType.POLICY_RULE.equals(assignmentDto.getType())){
                    continue;
                }
                String relationLocalPart = relation.getRelation() == null ? SchemaConstants.ORG_DEFAULT.getLocalPart() : relation.getRelation().getLocalPart();
                String assignmentDtoRelation = assignmentDto.getRelation();
                if (relationLocalPart.equals(assignmentDtoRelation) || (relation.getRelation() == null && assignmentDtoRelation == null)){
                    assignmentList.add(assignmentDto);
                }
            }
            relationAssignmentsMap.put(relation, assignmentList);
        }
    }

    private WebMarkupContainer getAssignmentsContainer(){
        return (WebMarkupContainer)get(ID_ASSIGNMENTS);
    }

    protected void reloadMainAssignmentsComponent(AjaxRequestTarget target){
        addOrReplaceAssignmentsTable(getAssignmentsContainer());
        target.add(getAssignmentsContainer());
    }

    private AssignmentsTabStorage getAssignmentsStorage(){
        return pageBase.getSessionStorage().getAssignmentsTabStorage();
    }

    private void initPaging(){
        getAssignmentsStorage().setPaging(ObjectPaging.createPaging(0, (int) pageBase.getItemsPerPage(UserProfileStorage.TableId.ASSIGNMENTS_TAB_TABLE)));
    }
}
