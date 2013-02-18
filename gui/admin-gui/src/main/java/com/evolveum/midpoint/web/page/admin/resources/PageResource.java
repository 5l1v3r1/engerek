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

package com.evolveum.midpoint.web.page.admin.resources;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ObjectOperationOption;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.button.AjaxLinkButton;
import com.evolveum.midpoint.web.component.data.TablePanel;
import com.evolveum.midpoint.web.component.util.ListDataProvider;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.page.admin.configuration.PageDebugView;
import com.evolveum.midpoint.web.page.admin.resources.dto.ResourceController;
import com.evolveum.midpoint.web.page.admin.resources.dto.ResourceDto;
import com.evolveum.midpoint.web.page.admin.resources.dto.ResourceObjectTypeDto;
import com.evolveum.midpoint.web.page.admin.resources.dto.ResourceStatus;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;

import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.SortOrder;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.PackageResourceReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author lazyman
 * @author Michal Serbak
 */
public class PageResource extends PageAdminResources {

    private static final Trace LOGGER = TraceManager.getTrace(PageResource.class);
    private static final String DOT_CLASS = PageResource.class.getName() + ".";
    private static final String OPERATION_IMPORT_FROM_RESOURCE = DOT_CLASS + "importFromResource";
    private static final String TEST_CONNECTION = DOT_CLASS + "testConnection";

    private IModel<ResourceDto> model;

    public PageResource() {
        model = new LoadableModel<ResourceDto>() {

            @Override
            protected ResourceDto load() {
                return loadResourceDto();
            }
        };
        initLayout();
    }

    private ResourceDto loadResourceDto() {
        Collection<SelectorOptions<GetOperationOptions>> options =
                SelectorOptions.createCollection(ResourceType.F_CONNECTOR, GetOperationOptions.createResolve());

        PrismObject<ResourceType> resource = loadResource(options);
        return new ResourceDto(resource, getPrismContext(), resource.asObjectable().getConnector(),
                initCapabilities(resource.asObjectable()));
    }

    private void initLayout() {
        Form mainForm = new Form("mainForm");
        add(mainForm);

        SortableDataProvider<ResourceObjectTypeDto, String> provider = new ListDataProvider<ResourceObjectTypeDto>(this,
                new PropertyModel<List<ResourceObjectTypeDto>>(model, "objectTypes"));
        provider.setSort("displayName", SortOrder.ASCENDING);
        TablePanel objectTypes = new TablePanel<ResourceObjectTypeDto>("objectTypesTable", provider,
                initObjectTypesColumns());
        objectTypes.setShowPaging(true);
        objectTypes.setOutputMarkupId(true);
        mainForm.add(objectTypes);

        initResourceColumns(mainForm);
        initConnectorDetails(mainForm);
        createCapabilitiesList(mainForm);

        AjaxLink<String> link = new AjaxLink<String>("seeDebug",
                createStringResource("pageResource.seeDebug")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                PageParameters parameters = new PageParameters();
                parameters.add(PageDebugView.PARAM_OBJECT_ID, model.getObject().getOid());
                getSession().setAttribute("requestPage", getPage());
                setResponsePage(PageDebugView.class, parameters);
            }
        };
        mainForm.add(link);
        initButtons(mainForm);
    }

    private void initResourceColumns(Form mainForm) {
        mainForm.add(new Label("resourceOid", new PropertyModel<Object>(model, "oid")));
        mainForm.add(new Label("resourceName", new PropertyModel<Object>(model, "name")));
        mainForm.add(new Label("resourceType", new PropertyModel<Object>(model, "type")));
        mainForm.add(new Label("resourceVersion", new PropertyModel<Object>(model, "version")));
        mainForm.add(new Label("resourceProgress", new PropertyModel<Object>(model, "progress")));
    }

    private IModel<String> createTestConnectionStateTooltip(final String expression) {
        return new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                PropertyModel<ResourceStatus> pModel = new PropertyModel<ResourceStatus>(model, expression);
                ResourceStatus status = pModel.getObject();
                if (status == null) {
                    return "";
                }

                return PageResource.this.getString(ResourceStatus.class.getSimpleName() + "." + status.name());
            }
        };
    }

    private void initConnectorDetails(Form mainForm) {
        WebMarkupContainer container = new WebMarkupContainer("connectors");
        container.setOutputMarkupId(true);

        Image image = new Image("overallStatus", new AbstractReadOnlyModel() {

            @Override
            public Object getObject() {
                return new PackageResourceReference(PageResource.class, model.getObject().getState()
                        .getOverall().getIcon());
            }
        });
        image.add(new AttributeModifier("title", createTestConnectionStateTooltip("state.overall")));
        container.add(image);

        image = new Image("confValidation", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() {
                return new PackageResourceReference(PageResource.class, model.getObject().getState()
                        .getConfValidation().getIcon());
            }
        });
        image.add(new AttributeModifier("title", createTestConnectionStateTooltip("state.confValidation")));
        container.add(image);

        image = new Image("conInitialization", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() {

                return new PackageResourceReference(PageResource.class, model.getObject().getState()
                        .getConInitialization().getIcon());
            }
        });
        image.add(new AttributeModifier("title", createTestConnectionStateTooltip("state.conInitialization")));
        container.add(image);

        image = new Image("conConnection", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() {
                return new PackageResourceReference(PageResource.class, model.getObject().getState()
                        .getConConnection().getIcon());
            }
        });
        image.add(new AttributeModifier("title", createTestConnectionStateTooltip("state.conConnection")));
        container.add(image);

        /*container.add(new Image("conSanity", new AbstractReadOnlyModel() {
              @Override
              public Object getObject() {
                  return new PackageResourceReference(PageResource.class, model.getObject().getState()
                          .getConSanity().getIcon());
              }
          }));*/

        image = new Image("conSchema", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() {
                return new PackageResourceReference(PageResource.class, model.getObject().getState()
                        .getConSchema().getIcon());
            }
        });
        container.add(image);
        image.add(new AttributeModifier("title", createTestConnectionStateTooltip("state.conSchema")));
        mainForm.add(container);
    }

    private List<String> initCapabilities(ResourceType resource) {
        OperationResult result = new OperationResult("Load resource capabilities");
        List<String> capabilitiesName = new ArrayList<String>();
        try {
            List<Object> capabilitiesList = ResourceTypeUtil.getEffectiveCapabilities(resource);

            if (capabilitiesList != null && !capabilitiesList.isEmpty()) {
                for (int i = 0; i < capabilitiesList.size(); i++) {
                    capabilitiesName.add(ResourceTypeUtil.getCapabilityDisplayName(capabilitiesList.get(i)));
                }
            }
        } catch (Exception ex) {
            result.recordFatalError("Couldn't load resource capabilities for resource'"
                    + new PropertyModel<Object>(model, "name") + ".", ex);

        }
        return capabilitiesName;
    }

    private List<IColumn<ResourceObjectTypeDto, String>> initObjectTypesColumns() {
        List<IColumn<ResourceObjectTypeDto, String>> columns = new ArrayList<IColumn<ResourceObjectTypeDto, String>>();

        columns.add(new PropertyColumn(createStringResource("pageResource.objectTypes.displayName"),
                "displayName", "displayName"));
        columns.add(new PropertyColumn(createStringResource("pageResource.objectTypes.nativeObjectClass"),
                "nativeObjectClass"));
        columns.add(new PropertyColumn(createStringResource("pageResource.objectTypes.help"), "help"));
        columns.add(new PropertyColumn(createStringResource("pageResource.objectTypes.type"), "type"));

        return columns;
    }

    private void createCapabilitiesList(Form mainForm) {
        ListView<String> listCapabilities = new ListView<String>("listCapabilities", createCapabilitiesModel(model)) {

            @Override
            protected void populateItem(ListItem<String> item) {
                item.add(new Label("capabilities", item.getModel()));

            }
        };
        mainForm.add(listCapabilities);
    }

    private IModel<List<String>> createCapabilitiesModel(final IModel<ResourceDto> model) {
        return new LoadableModel<List<String>>(false) {

            @Override
            protected List<String> load() {
                ResourceDto resource = model.getObject();
                return resource.getCapabilities();
            }
        };
    }

    private void initButtons(Form mainForm) {
        AjaxLinkButton back = new AjaxLinkButton("back", createStringResource("pageResource.button.back")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                setResponsePage(PageResources.class);
            }
        };
        mainForm.add(back);

        AjaxLinkButton test = new AjaxLinkButton("test", createStringResource("pageResource.button.test")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                testConnectionPerformed(target);
            }
        };
        mainForm.add(test);

        AjaxLinkButton importAccounts = new AjaxLinkButton("importAccounts",
                createStringResource("pageResource.button.importAccounts")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                importFromResourcePerformed(target);
            }
        };
        mainForm.add(importAccounts);
    }

    private void testConnectionPerformed(AjaxRequestTarget target) {
        ResourceDto dto = model.getObject();
        if (dto == null || StringUtils.isEmpty(dto.getOid())) {
            error(getString("pageResource.message.oidNotDefined"));
            target.add(getFeedbackPanel());
            return;
        }

        OperationResult result = null;
        try {
            result = getModelService().testResource(dto.getOid(), createSimpleTask(TEST_CONNECTION));
            ResourceController.updateResourceState(dto.getState(), result);
        } catch (ObjectNotFoundException ex) {
            if (result == null) {
                result = new OperationResult(TEST_CONNECTION);
            }
            result.recordFatalError("Fail to test resource connection", ex);
        }

        WebMarkupContainer connectors = (WebMarkupContainer) get("mainForm:connectors");
        target.add(connectors);
        
        if (!result.isSuccess()) {
            showResult(result);
            target.add(getFeedbackPanel());
        }
    }

    private void importFromResourcePerformed(AjaxRequestTarget target) {
        ResourceDto dto = model.getObject();
        LOGGER.debug("Import accounts from resource {} ({}), object class {}",
                new Object[]{dto.getName(), dto.getOid(), dto.getDefaultAccountObjectClass()});

        OperationResult result = new OperationResult(OPERATION_IMPORT_FROM_RESOURCE);
        try {
            Task task = createSimpleTask(OPERATION_IMPORT_FROM_RESOURCE);
            getModelService().importAccountsFromResource(dto.getOid(), dto.getDefaultAccountObjectClass(), task, result);
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "Error occurred during accounts import from resource {} ({}), class {}",
                    ex, dto.getName(), dto.getOid(), dto.getDefaultAccountObjectClass());
            result.recordFatalError("Error occurred during importing accounts from resource.", ex);
        }

        result.computeStatus();
        
        showResult(result);
        target.add(getFeedbackPanel());
    }
}
