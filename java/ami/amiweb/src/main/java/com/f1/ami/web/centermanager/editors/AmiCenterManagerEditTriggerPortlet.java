package com.f1.ami.web.centermanager.editors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebFormPortletAmiScriptField;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerAbstractTriggerEditor;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerTriggerEditor_AggregateTrigger;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerTriggerEditor_AmiscirptTrigger;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerTriggerEditor_DecorateTrigger;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerTriggerEditor_JoinTrigger;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerTriggerEditor_ProjectionTrigger;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerTriggerEditor_RelayTrigger;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Trigger;

import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.HtmlPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletMultiCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextAreaField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.CH;
import com.f1.utils.SH;
import com.f1.utils.string.sqlnode.AdminNode;

/**
 * EditTriggerPortlet<br>
 * ->Form: all the config fields<br>
 * ->TriggerEditor: all the trigger-type specific options<br>
 * ->->SmartEditor: all the smart editors<br>
 */
public class AmiCenterManagerEditTriggerPortlet extends AmiCenterManagerAbstractEditCenterObjectPortlet {
	public static final Set<String> COMMON_OPTIONS = CH.s("triggerName", "triggerOn", "triggerPriority", "triggerType");

	//the dependencies for these can only determined once the "target" field has been specified(for tge realy trigger only)
	public static final Set<String> IMPORT_DEFERRED_OPTIONS = CH.s("inserts", "updates", "deletes");
	//height const
	private static final int OPTION_FORM_HEIGHT = 90;//common option form height

	final private AmiWebService service;

	//option fields
	final private FormPortlet form;

	final private GridPortlet formAndTriggerConfigGrid;

	final private FormPortletTextField triggerNameField;
	final private FormPortletSelectField<Short> triggerTypeField;

	final private FormPortletMultiCheckboxField<String> triggerOnField;
	final private FormPortletTextField triggerPriorityField;
	private AmiCenterManagerAbstractTriggerEditor curEditor;

	//trigger editors
	final private AmiCenterManagerTriggerEditor_AmiscirptTrigger amiscriptEditor;
	final private AmiCenterManagerTriggerEditor_AggregateTrigger aggEditor;
	final private AmiCenterManagerTriggerEditor_ProjectionTrigger projectionEditor;
	final private AmiCenterManagerTriggerEditor_JoinTrigger joinEditor;
	final private AmiCenterManagerTriggerEditor_DecorateTrigger decorateEditor;
	final private AmiCenterManagerTriggerEditor_RelayTrigger relayEditor;

	//trigger-type-specific editor
	private InnerPortlet editorPanel;//all the type-specific fields excluding amiscript fields

	public AmiCenterManagerEditTriggerPortlet(PortletConfig config, boolean isAdd) {
		super(config, isAdd);
		service = AmiWebUtils.getService(getManager());
		this.form = new FormPortlet(generateConfig());
		amiscriptEditor = new AmiCenterManagerTriggerEditor_AmiscirptTrigger(generateConfig(), this);
		this.getManager().onPortletAdded(amiscriptEditor);
		aggEditor = new AmiCenterManagerTriggerEditor_AggregateTrigger(generateConfig(), this);
		this.getManager().onPortletAdded(aggEditor);
		projectionEditor = new AmiCenterManagerTriggerEditor_ProjectionTrigger(generateConfig(), this);
		this.getManager().onPortletAdded(projectionEditor);
		joinEditor = new AmiCenterManagerTriggerEditor_JoinTrigger(generateConfig(), this);
		this.getManager().onPortletAdded(joinEditor);
		decorateEditor = new AmiCenterManagerTriggerEditor_DecorateTrigger(generateConfig(), this);
		this.getManager().onPortletAdded(decorateEditor);
		relayEditor = new AmiCenterManagerTriggerEditor_RelayTrigger(generateConfig(), this);
		this.getManager().onPortletAdded(relayEditor);

		formAndTriggerConfigGrid = new GridPortlet(generateConfig());
		formAndTriggerConfigGrid.addChild(form, 0, 0);
		formAndTriggerConfigGrid.setRowSize(0, OPTION_FORM_HEIGHT);
		this.editorPanel = formAndTriggerConfigGrid.addChild(new HtmlPortlet(generateConfig()).setCssStyle("_bg=#e2e2e2"), 0, 1, 1, 1);

		triggerNameField = this.form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("Name")));
		//triggerNameField.setName(AmiCenterEntityConsts.GROUP_NAME_REQUIRED_FIELD);
		triggerNameField.setName("triggerName");
		triggerNameField.setWidth(NAME_WIDTH).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_TOPPOS);
		triggerNameField.setHelp("Name of the trigger to create, must be unique within the database");

		//trigger type
		triggerTypeField = this.form.addField(new FormPortletSelectField(short.class, AmiCenterManagerUtils.formatRequiredField("Type")));
		triggerTypeField.setName("triggerType");
		initTriggerTypes();
		triggerTypeField.setWidth(TYPE_WIDTH).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + DEFAULT_X_SPACING).setTopPosPx(DEFAULT_TOPPOS);

		triggerPriorityField = this.form.addField(new FormPortletTextField("PRIORITY"));
		triggerPriorityField.setName("triggerPriority");
		triggerPriorityField.setHelp("a number, timers with lowest value are executed first. Only considered when two or more timers have the same exact scheduled time");
		triggerPriorityField.setWidth(PRIORITY_WIDTH).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + TYPE_WIDTH + DEFAULT_X_SPACING * 2)
				.setTopPosPx(DEFAULT_TOPPOS);

		triggerOnField = this.form.addField(new FormPortletMultiCheckboxField(String.class, AmiCenterManagerUtils.formatRequiredField("ON")));
		triggerOnField.setName("triggerOn");
		triggerOnField.setName(AmiCenterEntityConsts.GROUP_NAME_REQUIRED_FIELD);
		triggerOnField.setHelp("Name of the table(s) that will cause the trigger to execute");
		//		triggerOnField.setWidth(ON_WIDTH).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + TYPE_WIDTH + DEFAULT_X_SPACING * 2)
		//				.setTopPosPx(DEFAULT_TOPPOS);
		triggerOnField.setWidth(ON_WIDTH).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_TOPPOS + DEFAULT_ROWHEIGHT * 2);
		triggerOnField.setBgColor("#ffffff");
		triggerOnField.setBorderColor("00FFFFFF");
		//set options for on field using the system object manager
		for (String k : CH.sort(service.getSystemObjectsManager().getTableNames())) {
			if (!SYSTEM_TABLES.contains(k))
				triggerOnField.addOption(k, k);
		}

		if (!isAdd) {
			this.form.addField(enableEditingCheckbox);
			enableEditingCheckbox.setWidth(20).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + TYPE_WIDTH + DEFAULT_X_SPACING * 3 + 60)
					.setTopPosPx(DEFAULT_TOPPOS);
		} else {
			editorPanel.setPortlet(amiscriptEditor);
			curEditor = amiscriptEditor;
		}

		this.addChild(formAndTriggerConfigGrid, 0, 0);
		this.addChild(buttonsFp, 0, 1);

		setRowSize(1, buttonsFp.getButtonPanelHeight());
		this.form.addFormPortletListener(this);
	}

	public AmiCenterManagerEditTriggerPortlet(PortletConfig config, String triggerSql, AmiCenterGraphNode_Trigger correlationNode) {
		this(config, false);
		this.correlationNode = correlationNode;
		//never allow editing trigger type
		this.triggerTypeField.setDisabled(true);
		this.importFromText(triggerSql, new StringBuilder());
		//add form portlet listener
		this.curEditor.getForm().addFormPortletListener(this);
		for (FormPortlet fp : this.curEditor.getSmartEditors())
			fp.addFormPortletListener(this);
		//by default editing is disabled
		enableEdit(false);
	}

	private void initTriggerTypes() {
		triggerTypeField.addOption(AmiCenterEntityConsts.TRIGGER_TYPE_CODE_AMISCRIPT, AmiCenterEntityConsts.TRIGGER_TYPE_AMISCRIPT);
		triggerTypeField.addOption(AmiCenterEntityConsts.TRIGGER_TYPE_CODE_AGGREGATE, AmiCenterEntityConsts.TRIGGER_TYPE_AGGREGATE);
		triggerTypeField.addOption(AmiCenterEntityConsts.TRIGGER_TYPE_CODE_PROJECTION, AmiCenterEntityConsts.TRIGGER_TYPE_PROJECTION);
		triggerTypeField.addOption(AmiCenterEntityConsts.TRIGGER_TYPE_CODE_JOIN, AmiCenterEntityConsts.TRIGGER_TYPE_JOIN);
		triggerTypeField.addOption(AmiCenterEntityConsts.TRIGGER_TYPE_CODE_DECORATE, AmiCenterEntityConsts.TRIGGER_TYPE_DECORATE);
		triggerTypeField.addOption(AmiCenterEntityConsts.TRIGGER_TYPE_CODE_RELAY, AmiCenterEntityConsts.TRIGGER_TYPE_RELAY);
	}

	private void updateTriggerTemplate(short type) {
		switch (type) {
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_AMISCRIPT:
				editorPanel.setPortlet(amiscriptEditor);
				curEditor = amiscriptEditor;
				break;
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_AGGREGATE:
				editorPanel.setPortlet(aggEditor);
				curEditor = aggEditor;
				break;
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_DECORATE:
				editorPanel.setPortlet(decorateEditor);
				curEditor = decorateEditor;
				break;
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_JOIN:
				editorPanel.setPortlet(joinEditor);
				curEditor = joinEditor;
				break;
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_PROJECTION:
				editorPanel.setPortlet(projectionEditor);
				curEditor = projectionEditor;
				break;
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_RELAY:
				editorPanel.setPortlet(relayEditor);
				curEditor = relayEditor;
				break;
			default:
				throw new RuntimeException("Unknow trigger type code: " + type);

		}
	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		return null;
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField node) {
	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		super.onFieldValueChanged(portlet, field, attributes);
		if (field == this.triggerTypeField) {
			short type = this.triggerTypeField.getValue();
			updateTriggerTemplate(type);
		} else if (field == this.triggerOnField) {
			onTriggerOnClauseChanged(field);
		} else if (!this.isAdd) {
			onFieldChanged(field);
		}
	}

	@Override
	public void onFieldChanged(FormPortletField<?> field) {
		super.onFieldChanged(field);
		if ("output".equals(field.getName())) {
			if (field.getForm() == this.aggEditor.getGroupByEditor()) {
				this.aggEditor.getGroupByEditor().onOutPutFieldChanged(field);
			} else if (field.getForm() == this.aggEditor.getSelectsEditor()) {
				this.aggEditor.getSelectsEditor().onOutPutFieldChanged(field);
			} else if (field.getForm() == this.decorateEditor.getSelectsEditor()) {
				this.decorateEditor.getSelectsEditor().onOutPutFieldChanged(field);
			} else if (field.getForm() == this.joinEditor.getSelectsEditor()) {
				this.joinEditor.getSelectsEditor().onOutPutFieldChanged(field);
			} else if (field.getForm() == this.projectionEditor.getSelectsEditor()) {
				this.projectionEditor.getSelectsEditor().onOutPutFieldChanged(field);
			}
		}

	}

	public void onTriggerOnClauseChanged(FormPortletField<?> field) {
		LinkedHashSet<String> onNames = ((FormPortletMultiCheckboxField) field).getValue();
		String[] namesArr = onNames.toArray(new String[0]);
		switch (this.triggerTypeField.getValue()) {
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_JOIN:
				if (namesArr.length == 3) {
					String leftTable = namesArr[0];
					String rightTable = namesArr[1];
					String resultTable = namesArr[2];
					joinEditor.setLeftTable(leftTable);
					joinEditor.setRightTable(rightTable);
					joinEditor.setResultTable(resultTable);
				} else
					joinEditor.resetDependency();
				break;
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_AGGREGATE:
				if (namesArr.length == 2) {
					String sourceTable = namesArr[0];
					String targetTable = namesArr[1];
					aggEditor.setSourceTable(sourceTable);
					aggEditor.setTargetTable(targetTable);
				} else
					aggEditor.resetDependency();
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_PROJECTION:
				if (namesArr.length >= 2) {
					List<String> sourceTables = new ArrayList<>(Arrays.asList(namesArr).subList(0, namesArr.length - 1));
					String targetTable = (String) namesArr[namesArr.length - 1];
					projectionEditor.setSourceTable(sourceTables);
					projectionEditor.setTargetTable(targetTable);
				} else
					projectionEditor.resetDependency();
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_DECORATE:
				if (namesArr.length == 2) {
					String sourceTable = namesArr[0];
					String targetTable = namesArr[1];
					decorateEditor.setSourceTable(sourceTable);
					decorateEditor.setTargetTable(targetTable);
				} else
					decorateEditor.resetDependency();
			case AmiCenterEntityConsts.TRIGGER_TYPE_CODE_RELAY:
				if (namesArr.length == 1) {
					String sourceTable = namesArr[0];
					relayEditor.setSourceTable(sourceTable);
					//relayEditor.setTargetTable(targetTable);
				} else
					relayEditor.resetDependency();
		}
	}

	@Override
	public void enableEdit(boolean enable) {
		for (FormPortletField<?> fpf : this.form.getFormFields()) {
			if (fpf == this.triggerTypeField)
				continue;
			if (fpf != this.enableEditingCheckbox)
				fpf.setDisabled(!enable);
		}
		onTriggerOnClauseChanged(this.triggerOnField);
		//enable/disable all the option fields
		this.curEditor.enableEdit(enable);

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}

	@Override
	public String prepareUseClause() {
		return curEditor.getKeyValuePairs();
	}

	public String getTriggerOnValue() {
		StringBuilder sb = new StringBuilder();
		Set<String> ons = triggerOnField.getValue();
		Iterator<String> i = ons.iterator();
		while (i.hasNext()) {
			sb.append(i.next());
			if (i.hasNext())
				sb.append(',');
		}
		return sb.toString();
	}

	@Override
	public String preparePreUseClause() {
		StringBuilder sb = new StringBuilder("CREATE TRIGGER ");
		if (SH.is(triggerNameField.getValue()))
			sb.append(triggerNameField.getValue());
		else
			sb.append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);
		sb.append(" OFTYPE ").append(triggerTypeField.getOption(triggerTypeField.getValue()).getName());

		if (SH.is(triggerOnField.getValue()))
			sb.append(" ON ").append(getTriggerOnValue());
		else
			sb.append(" ON ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (SH.is(triggerPriorityField.getValue()))
			sb.append(" PRIORITY ").append(triggerPriorityField.getValue());
		return sb.toString();
	}

	@Override
	public String exportToText() {
		return previewScript();
	}

	@Override
	public void importFromText(String text, StringBuilder sink) {
		AdminNode an = AmiCenterManagerUtils.scriptToAdminNode(text);
		Map<String, String> triggerConfig = AmiCenterManagerUtils.parseAdminNode_Trigger(an);
		String triggerType = triggerConfig.get("triggerType");
		this.triggerTypeField.setValue(AmiCenterManagerUtils.centerObjectTypeToCode(AmiCenterGraphNode.TYPE_TRIGGER, SH.toUpperCase(triggerType)));
		this.triggerTypeField.setDefaultValue(AmiCenterManagerUtils.centerObjectTypeToCode(AmiCenterGraphNode.TYPE_TRIGGER, SH.toUpperCase(triggerType)));
		this.onFieldValueChanged(this.form, this.triggerTypeField, null);

		for (Entry<String, String> e : triggerConfig.entrySet()) {
			String key = e.getKey();
			String value = e.getValue();
			if ("triggerName".equals(key)) {
				this.triggerNameField.setValue(value);
				this.triggerNameField.setDefaultValue(value);
			} else if ("triggerType".equals(key)) {
				continue;
			} else if ("triggerOn".equals(key)) {
				String[] l;
				//parse value
				if (value.contains(",")) {
					l = SH.split(',', value);
				} else
					l = new String[] { value };
				try {
					this.triggerOnField.setValue(CH.ls(l));
					this.triggerOnField.setDefaultValue(CH.ls(l));
				} catch (Exception ex) {
					AmiCenterManagerUtils.popDialog(service, ex.getMessage(), "Import Error");
				}

			} else if ("triggerPriority".equals(key)) {
				this.triggerPriorityField.setValue(value);
				this.triggerPriorityField.setDefaultValue(value);
			} else {//all the use options go here
					//1. locate the field
				FormPortletField<?> fpf = null;
				if (COMMON_OPTIONS.contains(key))
					fpf = this.form.getField(key);
				else {
					try {
						fpf = this.curEditor.getFieldByName(key);
					} catch (Exception ex) {
						AmiCenterManagerUtils.popDialog(service, ex.getMessage(), "Import Error");
					}
				}
				if (fpf == null)
					throw new NullPointerException("cannot find the field for name:" + key);
				//2. set the value for the field depending on the field type
				if (fpf instanceof FormPortletTextField) {
					((FormPortletTextField) fpf).setValue(value);
					((FormPortletTextField) fpf).setDefaultValue(value);
				} else if (fpf instanceof FormPortletTextAreaField) {
					((FormPortletTextAreaField) fpf).setValue(value);
					((FormPortletTextAreaField) fpf).setDefaultValue(value);
					if ("output".equals(fpf.getName())) {
						if (curEditor == this.aggEditor) {
							if ("selects".equals(key))
								aggEditor.getSelectsEditor().onOutPutFieldChanged(fpf);
							else if ("groupBys".equals(key))
								aggEditor.getGroupByEditor().onOutPutFieldChanged(fpf);
						} else if (curEditor == this.decorateEditor) {
							if ("selects".equals(key))
								decorateEditor.getSelectsEditor().onOutPutFieldChanged(fpf);
						} else if (curEditor == this.joinEditor) {
							if ("selects".equals(key))
								joinEditor.getSelectsEditor().onOutPutFieldChanged(fpf);
						} else if (curEditor == this.projectionEditor) {
							if ("selects".equals(key))
								projectionEditor.getSelectsEditor().onOutPutFieldChanged(fpf);
						}
					}

				} else if (fpf instanceof AmiWebFormPortletAmiScriptField) {
					((AmiWebFormPortletAmiScriptField) fpf).setValue(value);
					((AmiWebFormPortletAmiScriptField) fpf).setDefaultValue(value);
					if (fpf == this.curEditor.getFieldByName("vars") && this.curEditor == this.amiscriptEditor)
						this.amiscriptEditor.parseVars();
				} else if (fpf instanceof FormPortletCheckboxField) {
					if ("true".equals(value)) {
						((FormPortletCheckboxField) fpf).setValue(true);
						((FormPortletCheckboxField) fpf).setDefaultValue(true);
					} else {
						((FormPortletCheckboxField) fpf).setValue(false);
						((FormPortletCheckboxField) fpf).setDefaultValue(false);
					}

				} else if (fpf instanceof FormPortletSelectField) {
					String fieldName = fpf.getName();
					if (AmiCenterManagerTriggerEditor_JoinTrigger.TYPE_FIELD_VARNAME.equals(fieldName)) {
						((FormPortletSelectField) fpf).setValue(AmiCenterManagerUtils.toJoinTriggerTypeCode(value));
						((FormPortletSelectField) fpf).setDefaultValue(AmiCenterManagerUtils.toJoinTriggerTypeCode(value));
					}

				} else if (fpf instanceof FormPortletMultiCheckboxField) {
					//parse value
					String[] l;
					if (value.contains(",")) {
						l = SH.split(',', value);
					} else
						l = new String[] { value };
					//					if (IMPORT_DEFERRED_OPTIONS.contains(key))
					//						continue;
					if ("target".equals(key)) {
						((FormPortletMultiCheckboxField) fpf).setValueNoThrow(CH.ls(l));
						((FormPortletMultiCheckboxField) fpf).setDefaultValue(CH.ls(l));
						this.relayEditor.onFieldValueChanged(null, fpf, null);
					} else {
						((FormPortletMultiCheckboxField) fpf).setValueNoThrow(CH.ls(l));
						((FormPortletMultiCheckboxField) fpf).setDefaultValue(CH.ls(l));
					}

				}

			}
		}
		return;
	}

	//The abilities to query the backend
	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		if (response.getOk() && response.getTables().size() == 1) {
			Table t = response.getTables().get(0);
			//populate table names into the ON field options
			for (Row r : t.getRows()) {
				String name = (String) r.get("TableName");
				this.triggerOnField.addOption(name, name);
			}
		}
	}

	public AmiCenterQueryDsRequest prepareRequest() {
		AmiCenterQueryDsRequest request = getManager().getTools().nw(AmiCenterQueryDsRequest.class);

		request.setLimit(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_LIMIT);
		request.setTimeoutMs(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_TIMEOUT);
		request.setQuerySessionKeepAlive(true);
		request.setIsTest(false);
		request.setAllowSqlInjection(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_ALLOW_SQL_INJECTION);
		request.setInvokedBy(service.getUserName());
		request.setSessionVariableTypes(null);
		request.setSessionVariables(null);
		request.setPermissions(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PERMISSION);
		request.setType(AmiCenterQueryDsRequest.TYPE_QUERY);
		request.setOriginType(AmiCenterQueryDsRequest.ORIGIN_FRONTEND_SHELL);
		request.setDatasourceName(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_DS_NAME);
		return request;
	}

	protected void sendQueryToBackend(String query) {
		if (SH.isnt(query))
			return;
		AmiCenterQueryDsRequest request = prepareRequest();
		if (request == null)
			return;
		request.setQuery(query);
		request.setQuerySessionId(this.sessionId);
		service.sendRequestToBackend(this, request);
	}

	@Override
	public String previewEdit() {
		// TODO Auto-generated method stub
		return null;
	}

}
