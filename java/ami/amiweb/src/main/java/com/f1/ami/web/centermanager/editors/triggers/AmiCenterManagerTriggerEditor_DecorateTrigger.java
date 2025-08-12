package com.f1.ami.web.centermanager.editors.triggers;

import java.util.LinkedHashSet;
import java.util.Set;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebMenuUtils;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerEditTriggerPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.ami.web.centermanager.editors.triggers.smarteditors.AmiCenterManagerTriggerEditor_DecorateSelectEditor;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.CH;
import com.f1.utils.SH;

public class AmiCenterManagerTriggerEditor_DecorateTrigger extends AmiCenterManagerAbstractTriggerEditor implements FormPortletContextMenuFactory, FormPortletContextMenuListener {
	final private FormPortletTextField onField;
	final private AmiCenterManagerTriggerEditor_DecorateSelectEditor selectsEditor;

	final private AmiWebService service;
	private String sourceTable;
	private String targetTable;
	private Set<String> sourceTableColumns;
	private Set<String> targetTableColumns;

	public AmiCenterManagerTriggerEditor_DecorateTrigger(PortletConfig config, AmiCenterManagerEditTriggerPortlet mainEditor) {
		super(config, mainEditor);
		service = AmiWebUtils.getService(getManager());
		onField = form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("on")));
		onField.setLeftPosPx(80).setWidth(500).setHeight(25).setTopPosPx(65);
		onField.setHasButton(true);
		onField.setHelp("An expression for how to relate the two tables in the form:" + "<br>"
				+ "<b><i style=\"color:blue\">leftColumn == rightColumn [ && leftColumn == rightColumn ... ]</i></b>");
		onField.setCorrelationData(new Formula() {
			@Override
			public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
				BasicWebMenu r = new BasicWebMenu();
				AmiWebMenuUtils.createOperatorsMenu(r, AmiWebUtils.getService(getManager()), "");

				//ADD TABLE NAMES AND COLUMNS
				if (sourceTable != null && targetTable != null && sourceTableColumns != null && targetTableColumns != null) {
					WebMenu tableNamesAndColumns = new BasicWebMenu("Table Names && Columns", true);

					//TODO: better action than "conq_" for?(color no quotes)
					WebMenu sourceTableCols = new BasicWebMenu(sourceTable + "&nbsp;&nbsp;&nbsp;<i>Source Table</i>", true);
					for (String c : CH.sort(sourceTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						sourceTableCols.add(new BasicWebMenuLink(c, true, "conq_" + sourceTable + "." + c).setAutoclose(false).setCssStyle("_fm=courier"));

					WebMenu targetTableCols = new BasicWebMenu(targetTable + "&nbsp;&nbsp;&nbsp;<i>Target Table</i>", true);
					for (String c : CH.sort(targetTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						targetTableCols.add(new BasicWebMenuLink(c, true, "conq_" + targetTable + "." + c).setAutoclose(false).setCssStyle("_fm=courier"));

					tableNamesAndColumns.add(sourceTableCols);
					tableNamesAndColumns.add(targetTableCols);
					r.add(tableNamesAndColumns);
				}

				if (sourceTable != null && targetTable != null) {
					//table names [left, right]
					WebMenu tableNames = new BasicWebMenu("Table Names", true);
					String sourceTableHTML = sourceTable + "&nbsp;&nbsp;&nbsp;<i>Source Table</i>";
					String targetTableHTML = targetTable + "&nbsp;&nbsp;&nbsp;<i>Target Table</i>";
					tableNames.add(new BasicWebMenuLink(sourceTableHTML, true, "var_" + sourceTable).setAutoclose(false).setCssStyle("_fm=courier"));
					tableNames.add(new BasicWebMenuLink(targetTableHTML, true, "var_" + targetTable).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(tableNames);
				}
				if (sourceTableColumns != null) {
					//table columns [leftColumn, rightColumn]
					WebMenu leftColumns = new BasicWebMenu("Left Table Columns", true);
					for (String c : CH.sort(sourceTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftColumns.add(new BasicWebMenuLink(c, true, "var_" + c).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(leftColumns);
				}
				if (targetTableColumns != null) {
					WebMenu rightColumns = new BasicWebMenu("Right Table Columns", true);
					for (String c : CH.sort(targetTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						rightColumns.add(new BasicWebMenuLink(c, true, "var_" + c).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(rightColumns);
				}

				return r;
			}

			@Override
			public void onContextMenu(FormPortletField field, String action) {
				AmiWebMenuUtils.processContextMenuAction(AmiWebUtils.getService(getManager()), action, field);

			}
		});

		selectsEditor = new AmiCenterManagerTriggerEditor_DecorateSelectEditor(generateConfig(), this);

		this.form.addMenuListener(this);
		this.form.setMenuFactory(this);
		addChild(form, 0, 0, 1, 1);
		addChild(this.selectsEditor, 0, 1, 1, 4);
	}

	public AmiCenterManagerTriggerEditor_DecorateSelectEditor getSelectsEditor() {
		return this.selectsEditor;
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField field) {
		if (field == this.onField) {
			Formula f = (Formula) this.onField.getCorrelationData();
			f.onContextMenu(field, action);
		}

	}
	public static interface Formula {
		public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition);
		public void onContextMenu(FormPortletField field, String action);
	}
	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		if (field == this.onField) {
			Formula f = (Formula) this.onField.getCorrelationData();
			return f.createMenu(formPortlet, field, cursorPosition);
		}
		return null;
	}

	@Override
	public String getKeyValuePairs() {
		StringBuilder sb = new StringBuilder();
		if (SH.is(onField.getValue()))
			sb.append(" on = ").append(SH.doubleQuote(onField.getValue()));
		else
			sb.append(" on = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (SH.is(selectsEditor.getOutput()))
			sb.append(" selects = ").append(SH.doubleQuote(selectsEditor.getOutput()));
		else
			sb.append(" selects = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		return sb.toString();
	}

	public void resetDependency() {
		this.sourceTable = null;
		this.targetTable = null;
		this.sourceTableColumns = null;
		this.targetTableColumns = null;
	}

	public void setSourceTable(String src) {
		this.sourceTable = src;
		sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + sourceTable + "\";//source");

	}
	public void setTargetTable(String tgt) {
		this.targetTable = tgt;
		sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + targetTable + "\";//target");
	}

	public String getSourceTable() {
		return this.sourceTable;
	}

	public String getTargetTable() {
		return this.targetTable;
	}

	public Set<String> getSourceTableColumns() {
		return this.sourceTableColumns;
	}

	public Set<String> getTargetTableColumns() {
		return this.targetTableColumns;
	}

	@Override
	public FormPortletField<?> getFieldByName(String name) {
		if ("on".equals(name))
			return this.onField;
		if ("selects".equals(name))
			return this.selectsEditor.getOutputField();
		throw new NullPointerException("No such name:" + name);

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
				this.selectsEditor.onSourceColumnsChanged();

			} else if (query.endsWith("//target")) {
				this.targetTableColumns = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					this.targetTableColumns.add((String) r.get("ColumnName"));
				this.selectsEditor.onTargetColumnsChanged();
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
		onField.setDisabled(!enable);
		getFieldByName("selects").setDisabled(!enable);
		for (FormPortletField<?> fpf : this.selectsEditor.getFormFields())
			fpf.setDisabled(!enable);
	}

	@Override
	public Set<? extends FormPortlet> getSmartEditors() {
		return CH.s(this.selectsEditor);
	}
}
