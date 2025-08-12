
package com.f1.ami.web.centermanager.editors.triggers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebMenuUtils;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerEditTriggerPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.ami.web.centermanager.editors.triggers.smarteditors.AmiCenterManagerTriggerEditor_ProjectionSelectEditor;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.CH;
import com.f1.utils.SH;

public class AmiCenterManagerTriggerEditor_ProjectionTrigger extends AmiCenterManagerAbstractTriggerEditor
		implements FormPortletListener, FormPortletContextMenuFactory, FormPortletContextMenuListener {
	private static final int FORM_LEFT_POSITION = 100;
	private static final int FORM_WIDTH = 550;
	private static final int FORM_HEIGHT = 300;

	final private FormPortletCheckboxField allowExternalUpdatesField;
	final private FormPortletTextField wheresField;
	final private AmiCenterManagerTriggerEditor_ProjectionSelectEditor selectsEditor;
	final private AmiWebService service;

	private String targetTable;
	private List<String> sourceTables;

	private Set<String>[] sourceTableColumns;
	private Set<String> targetTableColumns;

	public AmiCenterManagerTriggerEditor_ProjectionTrigger(PortletConfig config, AmiCenterManagerEditTriggerPortlet mainEditor) {
		super(config, mainEditor);
		service = AmiWebUtils.getService(getManager());
		allowExternalUpdatesField = form.addField(new FormPortletCheckboxField("allowExternalUpdates"));
		allowExternalUpdatesField.setHelp("Optional. Value is either true or false (false by default)." + "<br>"
				+ "If true, then other processes (i.e triggers, UPDATEs) are allowed to perform UPDATEs on the target table." + "<br>"
				+ " Please use precaution when using this feature, since updating cells controlled by the aggregate trigger will result into an undesirable state.");
		allowExternalUpdatesField.setLeftPosPx(185).setTopPosPx(40);

		wheresField = form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("wheres")));
		wheresField.setName(AmiCenterEntityConsts.GROUP_NAME_REQUIRED_FIELD);
		wheresField.setLeftPosPx(FORM_LEFT_POSITION).setWidth(600).setHeight(25).setTopPosPx(90);
		wheresField.setHelp("A comma-delimited list of boolean expressions that must all be true on a source table's row in order for it to be projected into the target table:"
				+ "<br>" + "<b><i style=\"color:blue\">expression_on_sourceTableColumns,[ expression_on_sourceTableColumns ...]</i></b>");
		wheresField.setHasButton(true);
		wheresField.setCorrelationData(new Formula() {

			@Override
			public void onContextMenu(FormPortletField field, String action) {
				AmiWebMenuUtils.processContextMenuAction(AmiWebUtils.getService(getManager()), action, field);
			}

			@Override
			public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
				BasicWebMenu r = new BasicWebMenu();
				AmiWebMenuUtils.createOperatorsMenu(r, AmiWebUtils.getService(getManager()), "");
				//1. Table Names: [source0, source1, ..., target]
				WebMenu tableNames = new BasicWebMenu("Table Names", true);
				//source table
				List<String> sourceTableNamesSorted = CH.sort(sourceTables, SH.COMPARATOR_CASEINSENSITIVE_STRING);
				for (int i = 0; i < sourceTableNamesSorted.size(); i++) {
					String tableName = sourceTableNamesSorted.get(i);
					tableNames.add(new BasicWebMenuLink(tableName + "&nbsp;&nbsp;&nbsp;<i>Source Table </i>" + i, true, "var_" + tableName).setAutoclose(false)
							.setCssStyle("_fm=courier"));
				}
				//target table
				tableNames.add(
						new BasicWebMenuLink(targetTable + "&nbsp;&nbsp;&nbsp;<i>Target Table </i>", true, "var_" + targetTable).setAutoclose(false).setCssStyle("_fm=courier"));
				r.add(tableNames);

				//2. Source i Column Names 
				for (int i = 0; i < sourceTableColumns.length; i++) {
					WebMenu columnNames = new BasicWebMenu("Source " + i + " Column Names", true);
					Set<String> columnNamesAtThisIndex = sourceTableColumns[i];
					for (String col : columnNamesAtThisIndex)
						columnNames.add(new BasicWebMenuLink(col, true, "var_" + col).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(columnNames);
				}

				return r;
			}
		});

		selectsEditor = new AmiCenterManagerTriggerEditor_ProjectionSelectEditor(generateConfig(), this);
		addChild(form, 0, 0, 1, 1);
		addChild(selectsEditor, 0, 1, 1, 1);
		setRowSize(0, 150);

		this.form.addFormPortletListener(this);
		this.form.addMenuListener(this);
		this.form.setMenuFactory(this);
	}

	public AmiCenterManagerTriggerEditor_ProjectionSelectEditor getSelectsEditor() {
		return this.selectsEditor;
	}

	public static interface Formula {
		public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition);
		public void onContextMenu(FormPortletField field, String action);
	}

	//add
	public static WebMenu createVariablesMenu(String menuName, Set<String> columns) {
		WebMenu variables = new BasicWebMenu(menuName, true);

		for (String column : CH.sort(columns, SH.COMPARATOR_CASEINSENSITIVE_STRING)) {
			variables.add(new BasicWebMenuLink(column, true, "var_" + column).setAutoclose(false).setCssStyle("_fm=courier"));
		}
		return variables;
	}

	@Override
	public String getKeyValuePairs() {
		StringBuilder sb = new StringBuilder();
		if (SH.is(wheresField.getValue()))
			sb.append(" wheres = ").append(SH.doubleQuote(wheresField.getValue()));
		else
			sb.append(" wheres = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (SH.is(selectsEditor.getOutput()))
			sb.append(" selects = ").append(SH.doubleQuote(selectsEditor.getOutput()));
		else
			sb.append(" selects = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (allowExternalUpdatesField.getBooleanValue())
			sb.append(" allowExternalUpdates = ").append(SH.doubleQuote("true"));
		return sb.toString();
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField field) {
		if (field == wheresField) {
			Formula cb = (Formula) field.getCorrelationData();
			cb.onContextMenu(field, action);
		}

	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		Formula t = (Formula) field.getCorrelationData();
		return t.createMenu(formPortlet, field, cursorPosition);
	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}
	public void setSourceTable(List<String> source) {
		this.sourceTables = source;
		int sourceTableSize = source.size();
		this.sourceTableColumns = new LinkedHashSet[sourceTableSize];
		for (int i = 0; i < source.size(); i++)
			sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + source.get(i) + "\";//index" + i + "source");
	}

	public void setTargetTable(String target) {
		this.targetTable = target;
		sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + targetTable + "\";//target");

	}

	public List<String> getSourceTables() {
		return this.sourceTables;
	}

	public String getTargetTable() {
		return this.targetTable;
	}

	public void resetDependency() {
		this.sourceTables = null;
		this.targetTable = null;
	}

	public Set<String>[] getSourceTableColumns() {
		return this.sourceTableColumns;
	}

	public Set<String> getTargetTableColumns() {
		return this.targetTableColumns;
	}

	@Override
	public FormPortletField<?> getFieldByName(String name) {
		if ("wheres".equals(name))
			return this.wheresField;
		if ("selects".equals(name))
			return this.selectsEditor.getOutputField();
		if ("allowExternalUpdates".equals(name))
			return this.allowExternalUpdatesField;
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
			if (query.endsWith("source")) {
				Integer index = -1;
				Pattern pattern = Pattern.compile("index(\\d+)source");
				Matcher matcher = pattern.matcher(query);
				if (matcher.find()) {
					index = Integer.parseInt(matcher.group(1));
				} else
					throw new NullPointerException();
				Set<String> sourceColumnsAtThisIndex = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					sourceColumnsAtThisIndex.add((String) r.get("ColumnName"));
				sourceTableColumns[index] = sourceColumnsAtThisIndex;
			} else if (query.endsWith("target")) {
				this.targetTableColumns = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					this.targetTableColumns.add((String) r.get("ColumnName"));
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
		wheresField.setDisabled(!enable);
		getFieldByName("selects").setDisabled(!enable);
		for (FormPortletField<?> fpf : this.selectsEditor.getFormFields())
			fpf.setDisabled(!enable);
	}

	@Override
	public Set<? extends FormPortlet> getSmartEditors() {
		return CH.s(this.selectsEditor);
	}

}
