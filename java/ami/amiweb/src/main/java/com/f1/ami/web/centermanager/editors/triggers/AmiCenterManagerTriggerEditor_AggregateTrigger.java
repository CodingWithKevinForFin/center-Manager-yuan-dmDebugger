package com.f1.ami.web.centermanager.editors.triggers;

import java.util.LinkedHashSet;
import java.util.Set;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerEditTriggerPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.ami.web.centermanager.editors.triggers.smarteditors.AmiCenterManagerTriggerEditor_AggregateGroupByEditor;
import com.f1.ami.web.centermanager.editors.triggers.smarteditors.AmiCenterManagerTriggerEditor_AggregateSelectEditor;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.utils.CH;
import com.f1.utils.SH;

public class AmiCenterManagerTriggerEditor_AggregateTrigger extends AmiCenterManagerAbstractTriggerEditor {
	/*strcture:
	 * <GridPortlet*formGrid:
	 *  1. FormPortlet*form1: nameField,onField<br>
	 *  2. groupbyForm*form2 indexConfigForm<br>
	 *  3. selectForm*form3 <br>
	 * 
	 * */

	final private AmiWebService service;

	final private FormPortletCheckboxField allowExternalUpdatesField;

	final private AmiCenterManagerTriggerEditor_AggregateGroupByEditor groupByEditor;
	final private AmiCenterManagerTriggerEditor_AggregateSelectEditor selectsEditor;

	private String sourceTable;
	private String targetTable;

	private Set<String> sourceTableColumns;
	private Set<String> targetTableColumns;

	public AmiCenterManagerTriggerEditor_AggregateTrigger(PortletConfig config, AmiCenterManagerEditTriggerPortlet mainEditor) {
		super(config, mainEditor);
		this.service = AmiWebUtils.getService(getManager());
		allowExternalUpdatesField = form.addField(new FormPortletCheckboxField("allowExternalUpdates"));
		allowExternalUpdatesField.setHelp("Optional. Value is either true or false (false by default)." + "<br>"
				+ "If true, then other processes (i.e triggers, UPDATEs) are allowed to perform UPDATEs on the target table." + "<br>"
				+ " Please use precaution when using this feature, since updating cells controlled by the aggregate trigger will result into an undesirable state.");
		allowExternalUpdatesField.setLeftPosPx(185).setTopPosPx(40);
		this.groupByEditor = new AmiCenterManagerTriggerEditor_AggregateGroupByEditor(generateConfig(), this);
		this.selectsEditor = new AmiCenterManagerTriggerEditor_AggregateSelectEditor(generateConfig(), this);

		addChild(form, 0, 0);
		addChild(groupByEditor, 0, 1, 1, 1);
		addChild(selectsEditor, 0, 2, 1, 1);
		setRowSize(0, 100);

	}

	@Override
	public String getKeyValuePairs() {
		StringBuilder sb = new StringBuilder();
		if (SH.is(groupByEditor.getOutput()))
			sb.append(" groupBys = ").append(SH.doubleQuote(groupByEditor.getOutput()));
		else
			sb.append(" groupBys = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (SH.is(selectsEditor.getOutput()))
			sb.append(" selects = ").append(SH.doubleQuote(selectsEditor.getOutput()));
		else
			sb.append(" selects = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (allowExternalUpdatesField.getBooleanValue())
			sb.append(" allowExternalUpdates = ").append(SH.doubleQuote("true"));
		return sb.toString();
	}

	public void setSourceTable(String src) {
		this.sourceTable = src;
		sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + this.sourceTable + "\";//source");
	}

	public void setTargetTable(String tgt) {
		this.targetTable = tgt;
		sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + this.targetTable + "\";//target");

	}

	public void resetDependency() {
		this.sourceTable = null;
		this.targetTable = null;
		this.sourceTableColumns = null;
		this.targetTableColumns = null;
	}

	public String getSourceTable() {
		return this.sourceTable;
	}

	public String getTargetTable() {
		return this.targetTable;
	}

	public Set<String> getTargetTableColumns() {
		return this.targetTableColumns;
	}

	public Set<String> getSourceTableColumns() {
		return this.sourceTableColumns;
	}

	@Override
	public FormPortletField<?> getFieldByName(String name) {
		if ("groupBys".equals(name))
			return this.groupByEditor.getOutputField();
		if ("selects".equals(name))
			return this.selectsEditor.getOutputField();
		if ("allowExternalUpdates".equals(name))
			return this.allowExternalUpdatesField;
		if ("groupBys_targetColumnField".equals(name))
			return this.groupByEditor.getTargetColumnField();
		if ("groupBys_sourceColumnField".equals(name))
			return this.groupByEditor.getSourceColumnField();
		if ("groupBys_addButtonField".equals(name))
			return this.groupByEditor.getAddButton();
		if ("groupBys_clearButtonField".equals(name))
			return this.groupByEditor.getClearButton();
		throw new NullPointerException("No such name:" + name);

	}

	public AmiCenterManagerTriggerEditor_AggregateGroupByEditor getGroupByEditor() {
		return this.groupByEditor;
	}

	public AmiCenterManagerTriggerEditor_AggregateSelectEditor getSelectsEditor() {
		return this.selectsEditor;
	}

	//The abilities to query the backend
	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		Action a = result.getRequestMessage().getAction();
		String query = null;
		if (a instanceof AmiCenterQueryDsRequest) {
			AmiCenterQueryDsRequest request = (AmiCenterQueryDsRequest) a;
			query = request.getQuery();
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		if (response.getOk() && response.getTables().size() == 1) {
			Table t = response.getTables().get(0);
			if (query.endsWith("//source")) {
				this.sourceTableColumns = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					this.sourceTableColumns.add((String) r.get("ColumnName"));
				this.groupByEditor.onSourceTableColumnsChanged();
			} else if (query.endsWith("//target")) {
				this.targetTableColumns = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					this.targetTableColumns.add((String) r.get("ColumnName"));
				this.groupByEditor.onTargetTableColumnsChanged();
				this.selectsEditor.onTargetTableColumnsChanged();
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
		service.sendRequestToBackend(this, request);
	}

	@Override
	public void enableEdit(boolean enable) {
		allowExternalUpdatesField.setDisabled(!enable);
		getFieldByName("groupBys").setDisabled(!enable);
		getFieldByName("selects").setDisabled(!enable);
		for (FormPortletField<?> fpf : this.groupByEditor.getFormFields())
			fpf.setDisabled(!enable);
		for (FormPortletField<?> fpf : this.selectsEditor.getFormFields())
			fpf.setDisabled(!enable);
	}

	@Override
	public Set<? extends FormPortlet> getSmartEditors() {
		return CH.s(this.selectsEditor, this.groupByEditor);
	}

}
