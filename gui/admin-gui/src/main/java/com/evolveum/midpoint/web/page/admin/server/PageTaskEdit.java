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

package com.evolveum.midpoint.web.page.admin.server;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.web.page.admin.server.dto.*;
import com.evolveum.midpoint.web.resource.img.ImgResources;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.datetime.markup.html.form.DateTextField;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.extensions.yui.calendar.DateTimeField;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.util.string.StringValue;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.ClusterStatusInformation;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskBinding;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.button.AjaxLinkButton;
import com.evolveum.midpoint.web.component.button.AjaxSubmitLinkButton;
import com.evolveum.midpoint.web.component.button.ButtonType;
import com.evolveum.midpoint.web.component.data.TablePanel;
import com.evolveum.midpoint.web.component.util.ListDataProvider;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.MisfireActionType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ScheduleType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ThreadStopActionType;

/**
 * @author lazyman
 * @author mserbak
 */
public class PageTaskEdit extends PageAdminTasks {
	private static final long serialVersionUID = -5933030498922903813L;

	private static final Trace LOGGER = TraceManager.getTrace(PageTaskEdit.class);
	private static final String DOT_CLASS = PageTaskAdd.class.getName() + ".";
	public static final String PARAM_TASK_EDIT_ID = "taskEditOid";
	private static final String OPERATION_LOAD_TASK = DOT_CLASS + "loadTask";
	private static final String OPERATION_SAVE_TASK = DOT_CLASS + "saveTask";
	private static final long ALLOWED_CLUSTER_INFO_AGE = 1200L;

	private IModel<TaskDto> model;
	private static boolean edit = false;

    public PageTaskEdit() {
		model = new LoadableModel<TaskDto>(false) {

			@Override
			protected TaskDto load() {
				return loadTask();
			}
		};

        edit = false;
        initLayout();
	}

    private boolean isRunnableOrRunning() {
        TaskDtoExecutionStatus exec = model.getObject().getExecution();
        return TaskDtoExecutionStatus.RUNNABLE.equals(exec) || TaskDtoExecutionStatus.RUNNING.equals(exec);
    }

    private boolean isRunning() {
        TaskDtoExecutionStatus exec = model.getObject().getExecution();
        return TaskDtoExecutionStatus.RUNNING.equals(exec);
    }

    private TaskDto loadTask() {
		OperationResult result = new OperationResult(OPERATION_LOAD_TASK);
		Task loadedTask = null;
		TaskManager manager = null;
		try {
			manager = getTaskManager();
			StringValue taskOid = getPageParameters().get(PARAM_TASK_EDIT_ID);
            //System.out.println("Task oid = " + taskOid);
			loadedTask = manager.getTask(taskOid.toString(), result);
			result.recordSuccess();
		} catch (Exception ex) {
			result.recordFatalError("Couldn't get task.", ex);
		}

		if (!result.isSuccess()) {
			showResult(result);
		}

		if (loadedTask == null) {
			getSession().error(getString("pageTaskEdit.message.cantTaskDetails"));

			if (!result.isSuccess()) {
				showResultInSession(result);
			}
			throw new RestartResponseException(PageTasks.class);
		}
		ClusterStatusInformation info = manager.getRunningTasksClusterwide(ALLOWED_CLUSTER_INFO_AGE, result);
		return new TaskDto(loadedTask, info, manager);
	}

	private void initLayout() {
		Form mainForm = new Form("mainForm");
		add(mainForm);

		initMainInfo(mainForm);
		initSchedule(mainForm);

		SortableDataProvider<OperationResult, String> provider = new ListDataProvider<OperationResult>(this,
				new PropertyModel<List<OperationResult>>(model, "opResult"));
		TablePanel result = new TablePanel<OperationResult>("operationResult", provider, initResultColumns());
		result.setStyle("padding-top: 0px;");
		result.setShowPaging(false);
		result.setOutputMarkupId(true);
		mainForm.add(result);

//		CheckBox runUntilNodeDown = new CheckBox("runUntilNodeDown", new PropertyModel<Boolean>(model,
//				"runUntilNodeDown"));
//		runUntilNodeDown.add(new VisibleEnableBehaviour() {
//			@Override
//			public boolean isEnabled() {
//				return edit;
//			}
//		});
//		mainForm.add(runUntilNodeDown);

		DropDownChoice threadStop = new DropDownChoice("threadStop", new Model<ThreadStopActionType>() {

			@Override
			public ThreadStopActionType getObject() {
				return model.getObject().getThreadStop();
			}

			@Override
			public void setObject(ThreadStopActionType object) {
				model.getObject().setThreadStop(object);
			}
		}, WebMiscUtil.createReadonlyModelFromEnum(ThreadStopActionType.class),
				new EnumChoiceRenderer<ThreadStopActionType>(PageTaskEdit.this));
		threadStop.add(new VisibleEnableBehaviour() {
			@Override
			public boolean isEnabled() {
				return edit;
			}
		});
		mainForm.add(threadStop);

		//mainForm.add(new TsaValidator(runUntilNodeDown, threadStop));

		initButtons(mainForm);
	}

	private void initMainInfo(Form mainForm) {
		RequiredTextField<String> name = new RequiredTextField<String>("name", new PropertyModel<String>(
				model, "name"));
		name.add(new VisibleEnableBehaviour() {

			@Override
			public boolean isEnabled() {
				return edit;
			}
		});
		name.add(new AttributeModifier("style", "width: 100%"));
		name.add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
		mainForm.add(name);

		Label oid = new Label("oid", new PropertyModel(model, "oid"));
		mainForm.add(oid);

		Label category = new Label("category", new PropertyModel(model, "category"));
		mainForm.add(category);

		Label uri = new Label("uri", new PropertyModel(model, "uri"));
		mainForm.add(uri);

		Label execution = new Label("execution", new AbstractReadOnlyModel<String>() {

			@Override
			public String getObject() {
                TaskDtoExecutionStatus executionStatus = model.getObject().getExecution();
                //System.out.println("Task execution status = " + executionStatus);
				return getString(TaskDtoExecutionStatus.class.getSimpleName() + "." + executionStatus.name());
			}
		});
		mainForm.add(execution);

		Label node = new Label("node", new AbstractReadOnlyModel<String>() {
			@Override
			public String getObject() {
				TaskDto dto = model.getObject();
				if (!TaskDtoExecutionStatus.RUNNING.equals(dto.getExecution())) {
					return null;
				}
				return PageTaskEdit.this.getString("pageTaskEdit.message.node", dto.getExecutingAt());
			}
		});
		mainForm.add(node);
	}

	private void initSchedule(Form mainForm) {
		final WebMarkupContainer container = new WebMarkupContainer("container");
		container.setOutputMarkupId(true);
		mainForm.add(container);

		final IModel<Boolean> recurringCheck = new PropertyModel<Boolean>(model, "recurring");
		final IModel<Boolean> boundCheck = new PropertyModel<Boolean>(model, "bound");
		
		WebMarkupContainer suspendReqRecurring = new WebMarkupContainer("suspendReqRecurring");
		suspendReqRecurring.add(new VisibleEnableBehaviour(){
			@Override
			public boolean isVisible() {
				return edit && isRunnableOrRunning();
			}
		});
		mainForm.add(suspendReqRecurring);

		final WebMarkupContainer boundContainer = new WebMarkupContainer("boundContainer");
		boundContainer.add(new VisibleEnableBehaviour() {

			@Override
			public boolean isVisible() {
				return recurringCheck.getObject();
			}

		});
		boundContainer.setOutputMarkupId(true);
		container.add(boundContainer);
		
		WebMarkupContainer suspendReqBound = new WebMarkupContainer("suspendReqBound");
		suspendReqBound.add(new VisibleEnableBehaviour(){
			@Override
			public boolean isVisible() {
				return edit && isRunnableOrRunning();
			}
		});
		boundContainer.add(suspendReqBound);

		final WebMarkupContainer intervalContainer = new WebMarkupContainer("intervalContainer");
		intervalContainer.add(new VisibleEnableBehaviour() {

			@Override
			public boolean isVisible() {
				return recurringCheck.getObject();
			}

		});
		intervalContainer.setOutputMarkupId(true);
		container.add(intervalContainer);

		final WebMarkupContainer cronContainer = new WebMarkupContainer("cronContainer");
		cronContainer.add(new VisibleEnableBehaviour() {

			@Override
			public boolean isVisible() {
				return recurringCheck.getObject() && !boundCheck.getObject();
			}

		});
		cronContainer.setOutputMarkupId(true);
		container.add(cronContainer);
		AjaxCheckBox recurring = new AjaxCheckBox("recurring", recurringCheck) {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				target.add(container);
				target.add(PageTaskEdit.this.get("mainForm:recurring"));
			}
		};
		recurring.setOutputMarkupId(true);
		recurring.add(new VisibleEnableBehaviour() {

			@Override
			public boolean isEnabled() {
                return edit && !isRunnableOrRunning();
			}
		});
		mainForm.add(recurring);

		final AjaxCheckBox bound = new AjaxCheckBox("bound", boundCheck) {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				target.add(container);
				target.add(PageTaskEdit.this.get("mainForm:recurring"));
			}
		};
		bound.add(new VisibleEnableBehaviour() {

			@Override
			public boolean isEnabled() {
				return edit && !isRunnableOrRunning();
			}
		});
		boundContainer.add(bound);
		
		final Image boundHelp = new Image("boundHelp", new PackageResourceReference(ImgResources.class,
				ImgResources.TOOLTIP_INFO));
		boundHelp.setOutputMarkupId(true);
		boundHelp.add(new AttributeAppender("original-title", getString("pageTaskEdit.boundHelp")));
		boundHelp.add(new AbstractDefaultAjaxBehavior() {

			@Override
			public void renderHead(Component component, IHeaderResponse response) {
				String js = "$('#"+ boundHelp.getMarkupId() +"').tipsy()";
				response.render(OnDomReadyHeaderItem.forScript(js));
				super.renderHead(component, response);
			}

			@Override
			protected void respond(AjaxRequestTarget target) {
			}
		});
		boundContainer.add(boundHelp);
		
		

		TextField<Integer> interval = new TextField<Integer>("interval",
				new PropertyModel<Integer>(model, "interval"));
		interval.add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
		interval.add(new VisibleEnableBehaviour() {
			@Override
			public boolean isEnabled() {
				return edit && (!isRunnableOrRunning() || !boundCheck.getObject());
			}
		});
		intervalContainer.add(interval);

		TextField<String> cron = new TextField<String>("cron", new PropertyModel<String>(
				model, "cronSpecification"));
		cron.add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
		cron.add(new VisibleEnableBehaviour() {
			@Override
			public boolean isEnabled() {
                return edit && (!isRunnableOrRunning() || !boundCheck.getObject());
			}
		});
		cronContainer.add(cron);
		
		final Image cronHelp = new Image("cronHelp", new PackageResourceReference(ImgResources.class,
				ImgResources.TOOLTIP_INFO));
		cronHelp.setOutputMarkupId(true);
		cronHelp.add(new AttributeAppender("original-title", getString("pageTaskEdit.cronHelp")));
		cronHelp.add(new AbstractDefaultAjaxBehavior() {
			@Override
			public void renderHead(Component component, IHeaderResponse response) {
				String js = "$('#"+ cronHelp.getMarkupId() +"').tipsy()";
				response.render(OnDomReadyHeaderItem.forScript(js));
				super.renderHead(component, response);
			}

			@Override
			protected void respond(AjaxRequestTarget target) {
			}
		});
		cronContainer.add(cronHelp);

		final DateTimeField notStartBefore = new DateTimeField("notStartBeforeField",
				new PropertyModel<Date>(model, "notStartBefore")) {
			@Override
			protected DateTextField newDateTextField(String id, PropertyModel dateFieldModel) {
				return DateTextField.forDatePattern(id, dateFieldModel, "dd/MMM/yyyy");
			}
		};
		notStartBefore.setOutputMarkupId(true);
		notStartBefore.add(new VisibleEnableBehaviour() {
			@Override
			public boolean isEnabled() {
				return edit && !isRunning();
			}
		});
		mainForm.add(notStartBefore);

		final DateTimeField notStartAfter = new DateTimeField("notStartAfterField", new PropertyModel<Date>(
				model, "notStartAfter")) {
			@Override
			protected DateTextField newDateTextField(String id, PropertyModel dateFieldModel) {
				return DateTextField.forDatePattern(id, dateFieldModel, "dd/MMM/yyyy");
			}
		};
		notStartAfter.setOutputMarkupId(true);
		notStartAfter.add(new VisibleEnableBehaviour() {
			@Override
			public boolean isEnabled() {
				return edit;
			}
		});
		mainForm.add(notStartAfter);

		DropDownChoice misfire = new DropDownChoice("misfireAction", new PropertyModel<MisfireActionType>(
				model, "misfireAction"), WebMiscUtil.createReadonlyModelFromEnum(MisfireActionType.class),
				new EnumChoiceRenderer<MisfireActionType>(PageTaskEdit.this));
		misfire.add(new VisibleEnableBehaviour() {

			@Override
			public boolean isEnabled() {
				return edit;
			}
		});
		mainForm.add(misfire);

		Label lastStart = new Label("lastStarted", new AbstractReadOnlyModel<String>() {

			@Override
			public String getObject() {
				TaskDto dto = model.getObject();
				if (dto.getLastRunStartTimestampLong() == null) {
					return "-";
				}
				Date date = new Date(dto.getLastRunStartTimestampLong());
				SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d. MMM yyyy HH:mm:ss");
				return dateFormat.format(date);
			}

		});
		mainForm.add(lastStart);

		Label lastFinished = new Label("lastFinished", new AbstractReadOnlyModel<String>() {

			@Override
			public String getObject() {
				TaskDto dto = model.getObject();
				if (dto.getLastRunFinishTimestampLong() == null) {
					return "-";
				}
				Date date = new Date(dto.getLastRunFinishTimestampLong());
				SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d. MMM yyyy HH:mm:ss");
				return dateFormat.format(date);
			}
		});
		mainForm.add(lastFinished);

		Label nextRun = new Label("nextRun", new AbstractReadOnlyModel<String>() {

			@Override
			public String getObject() {
				TaskDto dto = model.getObject();
                if (dto.getRecurring() && dto.getBound() && isRunning()) {
                    return getString("pageTasks.runsContinually");
                }
				if (dto.getNextRunStartTimeLong() == null) {
					return "-";
				}
				Date date = new Date(dto.getNextRunStartTimeLong());
				SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d. MMM yyyy HH:mm:ss");
				return dateFormat.format(date);
			}
		});
		mainForm.add(nextRun);

        mainForm.add(new StartEndDateValidator(notStartBefore, notStartAfter));
        mainForm.add(new ScheduleValidator(getTaskManager(), recurring, bound, interval, cron));
	}

	private void initButtons(final Form mainForm) {
		AjaxLinkButton backButton = new AjaxLinkButton("backButton",
				createStringResource("pageTaskEdit.button.back")) {

			@Override
			public void onClick(AjaxRequestTarget target) {
				edit = false;
				setResponsePage(PageTasks.class);
			}
		};
		mainForm.add(backButton);

		AjaxSubmitLinkButton saveButton = new AjaxSubmitLinkButton("saveButton", ButtonType.POSITIVE,
				createStringResource("pageTaskEdit.button.save")) {

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				savePerformed(target);
			}

			@Override
			protected void onError(AjaxRequestTarget target, Form<?> form) {
				target.add(getFeedbackPanel());
			}

		};
		saveButton.add(new VisibleEnableBehaviour() {
			@Override
			public boolean isVisible() {
				return edit;
			}
		});
		mainForm.add(saveButton);

		AjaxLinkButton editButton = new AjaxLinkButton("editButton",
				createStringResource("pageTaskEdit.button.edit")) {

			@Override
			public void onClick(AjaxRequestTarget target) {
				edit = true;
				target.add(mainForm);
			}
		};
		editButton.add(new VisibleEnableBehaviour() {
			@Override
			public boolean isVisible() {
				return !edit;
			}
		});
		mainForm.add(editButton);
	}

	private List<IColumn<OperationResult, String>> initResultColumns() {
		List<IColumn<OperationResult, String>> columns = new ArrayList<IColumn<OperationResult, String>>();

		columns.add(new PropertyColumn(createStringResource("pageTaskEdit.opResult.token"), "token"));
		columns.add(new PropertyColumn(createStringResource("pageTaskEdit.opResult.operation"), "operation"));
		columns.add(new PropertyColumn(createStringResource("pageTaskEdit.opResult.status"), "status"));
		columns.add(new PropertyColumn(createStringResource("pageTaskEdit.opResult.message"), "message"));
		return columns;
	}

	private void savePerformed(AjaxRequestTarget target) {
		LOGGER.debug("Saving new task.");
		OperationResult result = new OperationResult(OPERATION_SAVE_TASK);
		TaskDto dto = model.getObject();
		try {
			OperationResult loadTask = new OperationResult(OPERATION_LOAD_TASK);
			TaskManager manager = getTaskManager();
			Task loadedTask = manager.getTask(dto.getOid(), loadTask);
			Task task = updateTask(dto, loadedTask);
			
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Saving task modifications.");
			}
			task.savePendingModifications(result);
			edit = false;
			setResponsePage(PageTasks.class);
			result.recomputeStatus();
		} catch (Exception ex) {
			result.recomputeStatus();
			result.recordFatalError("Couldn't save task.", ex);
			LoggingUtils.logException(LOGGER, "Couldn't save task modifications", ex);
		}
		showResultInSession(result);
		target.add(getFeedbackPanel());
	}

	private Task updateTask(TaskDto dto, Task loadedTask) {

        if (!loadedTask.getName().equals(dto.getName())) {
		    loadedTask.setName(WebMiscUtil.createPolyFromOrigString(dto.getName()));
        }   // if they are equal, modifyObject complains ... it's probably a bug in repo; we'll fix it later?

		if (!dto.getRecurring()) {
			loadedTask.makeSingle();
		}
		loadedTask.setBinding(dto.getBound() == true ? TaskBinding.TIGHT : TaskBinding.LOOSE);

        ScheduleType schedule = new ScheduleType();

        schedule.setEarliestStartTime(MiscUtil.asXMLGregorianCalendar(dto.getNotStartBefore()));
        schedule.setLatestStartTime(MiscUtil.asXMLGregorianCalendar(dto.getNotStartAfter()));
        schedule.setMisfireAction(dto.getMisfire());
        if (loadedTask.getSchedule() != null) {
            schedule.setLatestFinishTime(loadedTask.getSchedule().getLatestFinishTime());
        }

        if (dto.getRecurring() == true) {

		    if (dto.getBound() == false && dto.getCronSpecification() != null) {
                schedule.setCronLikePattern(dto.getCronSpecification());
            } else {
                schedule.setInterval(dto.getInterval());
            }
            loadedTask.makeRecurrent(schedule);
        } else {
            loadedTask.makeSingle(schedule);
        }

        ThreadStopActionType tsa = dto.getThreadStop();
//        if (tsa == null) {
//            tsa = dto.getRunUntilNodeDown() ? ThreadStopActionType.CLOSE : ThreadStopActionType.RESTART;
//        }
        loadedTask.setThreadStopAction(tsa);
		return loadedTask;
	}

	private static class EmptyOnBlurAjaxFormUpdatingBehaviour extends AjaxFormComponentUpdatingBehavior {

		public EmptyOnBlurAjaxFormUpdatingBehaviour() {
			super("onBlur");
		}

		@Override
		protected void onUpdate(AjaxRequestTarget target) {
		}
	}
}
