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
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerEditTriggerPortlet;
import com.f1.ami.web.centermanager.editors.triggers.smarteditors.AmiCenterManagerTriggerEditor_JoinSelectEditor;
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
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.CH;
import com.f1.utils.SH;

public class AmiCenterManagerTriggerEditor_JoinTrigger extends AmiCenterManagerAbstractTriggerEditor implements FormPortletContextMenuFactory, FormPortletContextMenuListener {
	private static final int FORM_LEFT_POSITION = 120;
	private static final int FORM_WIDTH = 550;
	private static final int FORM_HEIGHT = 300;

	public static final String TYPE_FIELD_VARNAME = "Join_Type";
	final private FormPortletSelectField<Short> typeField;
	//menu should contain tablenames(left and right), tablecolumn(left and right)
	final private FormPortletTextField onField;
	final private AmiCenterManagerTriggerEditor_JoinSelectEditor selectsEditor;
	final private FormPortletTextField wheresField;

	final private AmiWebService service;
	private String leftTable;
	private String rightTable;
	private String resultTable;

	private Set<String> leftTableColumns;
	private Set<String> rightTableColumns;
	private Set<String> resultTableColumns;

	public AmiCenterManagerTriggerEditor_JoinTrigger(PortletConfig config, AmiCenterManagerEditTriggerPortlet mainEditor) {
		super(config, mainEditor);
		service = AmiWebUtils.getService(getManager());
		typeField = form.addField(new FormPortletSelectField<Short>(short.class, AmiCenterManagerUtils.formatRequiredField("type")));
		typeField.setName(AmiCenterEntityConsts.GROUP_NAME_REQUIRED_FIELD);
		typeField.addOption(AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_CODE_INNER, AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_INNER);
		typeField.addOption(AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_CODE_LEFT, AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_LEFT);
		typeField.addOption(AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_CODE_RIGHT, AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_RIGHT);
		typeField.addOption(AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_CODE_OUTER, AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_OUTER);
		typeField.addOption(AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_CODE_LEFT_ONLY, AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_LEFT_ONLY);
		typeField.addOption(AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_CODE_RIGHT_ONLY, AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_RIGHT_ONLY);
		typeField.addOption(AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_CODE_OUTER_ONLY, AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_OUTER_ONLY);
		typeField.setName(TYPE_FIELD_VARNAME);
		typeField.setDefaultValue(AmiCenterEntityConsts.TRIGGER_JOIN_TYPE_CODE_INNER);
		typeField.setHelp("How to join the left and right tables");
		typeField.setLeftPosPx(80).setWidth(200).setHeight(20).setTopPosPx(20);

		onField = form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("on")));
		onField.setLeftPosPx(80).setWidth(500).setHeight(25).setTopPosPx(65);
		onField.setHasButton(true);
		onField.setCorrelationData(new Formula() {
			@Override
			public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
				BasicWebMenu r = new BasicWebMenu();
				AmiWebMenuUtils.createOperatorsMenu(r, AmiWebUtils.getService(getManager()), "");

				//ADD TABLE NAMES AND COLUMNS
				if (leftTable != null && rightTable != null && leftTableColumns != null && rightTableColumns != null) {
					WebMenu tableNamesAndColumns = new BasicWebMenu("Table Names && Columns", true);

					//TODO: better action than "conq_" for?(color no quotes)
					WebMenu leftTableCols = new BasicWebMenu(leftTable + "&nbsp;&nbsp;&nbsp;<i>Left Table</i>", true);
					for (String c : CH.sort(leftTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftTableCols.add(new BasicWebMenuLink(c, true, "conq_" + leftTable + "." + c).setAutoclose(false).setCssStyle("_fm=courier"));

					WebMenu rightTableCols = new BasicWebMenu(rightTable + "&nbsp;&nbsp;&nbsp;<i>Right Table</i>", true);
					for (String c : CH.sort(rightTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						rightTableCols.add(new BasicWebMenuLink(c, true, "conq_" + rightTable + "." + c).setAutoclose(false).setCssStyle("_fm=courier"));

					tableNamesAndColumns.add(leftTableCols);
					tableNamesAndColumns.add(rightTableCols);
					r.add(tableNamesAndColumns);
				}

				////////////////////////////////////
				if (leftTable != null && rightTable != null) {
					//table names [left, right]
					WebMenu tableNames = new BasicWebMenu("Table Names", true);
					String leftTableHTML = leftTable + "&nbsp;&nbsp;&nbsp;<i>Left Table</i>";
					String rightTableHTML = rightTable + "&nbsp;&nbsp;&nbsp;<i>Right Table</i>";
					tableNames.add(new BasicWebMenuLink(leftTableHTML, true, "var_" + leftTable).setAutoclose(false).setCssStyle("_fm=courier"));
					tableNames.add(new BasicWebMenuLink(rightTableHTML, true, "var_" + rightTable).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(tableNames);
				}
				if (leftTableColumns != null) {
					//table columns [leftColumn, rightColumn]
					WebMenu leftColumns = new BasicWebMenu("Left Table Columns", true);
					for (String c : CH.sort(leftTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftColumns.add(new BasicWebMenuLink(c, true, "var_" + c).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(leftColumns);
				}
				if (rightTableColumns != null) {
					WebMenu rightColumns = new BasicWebMenu("Right Table Columns", true);
					for (String c : CH.sort(rightTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
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

		onField.setHelp("An expression for how to relate the two tables in the form:" + "<br>"
				+ "<b><i style=\"color:blue\">leftColumn == rightColumn [ && leftColumn == rightColumn ... ]</i></b>");

		selectsEditor = new AmiCenterManagerTriggerEditor_JoinSelectEditor(generateConfig(), this);

		wheresField = form.addField(new FormPortletTextField("wheres"));
		wheresField.setHasButton(true);
		wheresField.setLeftPosPx(80).setWidth(500).setHeight(25).setTopPosPx(115);
		wheresField.setHelp("");
		wheresField.setCorrelationData(new Formula() {
			@Override
			public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
				BasicWebMenu r = new BasicWebMenu();
				AmiWebMenuUtils.createOperatorsMenu(r, AmiWebUtils.getService(getManager()), "");

				//ADD TABLE NAMES AND COLUMNS
				if (leftTable != null && rightTable != null && leftTableColumns != null && rightTableColumns != null) {
					WebMenu tableNamesAndColumns = new BasicWebMenu("Table Names && Columns", true);

					//TODO: better action than "conq_" for?(color no quotes)
					WebMenu leftTableCols = new BasicWebMenu(leftTable + "&nbsp;&nbsp;&nbsp;<i>Left Table</i>", true);
					for (String c : CH.sort(leftTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftTableCols.add(new BasicWebMenuLink(c, true, "conq_" + leftTable + "." + c).setAutoclose(false).setCssStyle("_fm=courier"));

					WebMenu rightTableCols = new BasicWebMenu(rightTable + "&nbsp;&nbsp;&nbsp;<i>Right Table</i>", true);
					for (String c : CH.sort(rightTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						rightTableCols.add(new BasicWebMenuLink(c, true, "conq_" + rightTable + "." + c).setAutoclose(false).setCssStyle("_fm=courier"));

					tableNamesAndColumns.add(leftTableCols);
					tableNamesAndColumns.add(rightTableCols);
					r.add(tableNamesAndColumns);
				}

				////////////////////////////////////
				if (leftTable != null && rightTable != null) {
					//table names [left, right]
					WebMenu tableNames = new BasicWebMenu("Table Names", true);
					String leftTableHTML = leftTable + "&nbsp;&nbsp;&nbsp;<i>Left Table</i>";
					String rightTableHTML = rightTable + "&nbsp;&nbsp;&nbsp;<i>Right Table</i>";
					tableNames.add(new BasicWebMenuLink(leftTableHTML, true, "var_" + leftTable).setAutoclose(false).setCssStyle("_fm=courier"));
					tableNames.add(new BasicWebMenuLink(rightTableHTML, true, "var_" + rightTable).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(tableNames);
				}
				if (leftTableColumns != null) {
					//table columns [leftColumn, rightColumn]
					WebMenu leftColumns = new BasicWebMenu("Left Table Columns", true);
					for (String c : CH.sort(leftTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftColumns.add(new BasicWebMenuLink(c, true, "var_" + c).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(leftColumns);
				}
				if (rightTableColumns != null) {
					WebMenu rightColumns = new BasicWebMenu("Right Table Columns", true);
					for (String c : CH.sort(rightTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
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

		this.form.addMenuListener(this);
		this.form.setMenuFactory(this);
		addChild(form, 0, 0, 1, 1);
		addChild(this.selectsEditor, 0, 1, 1, 4);
	}

	public AmiCenterManagerTriggerEditor_JoinSelectEditor getSelectsEditor() {
		return this.selectsEditor;
	}

	@Override
	public String getKeyValuePairs() {
		StringBuilder sb = new StringBuilder();
		sb.append(" type = ").append(SH.doubleQuote(typeField.getOption(typeField.getValue()).getName()));
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
		this.leftTable = null;
		this.rightTable = null;
		this.resultTable = null;
		this.leftTableColumns = null;
		this.rightTableColumns = null;
		this.resultTableColumns = null;
	}

	public void setLeftTable(String left) {
		this.leftTable = left;
		sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + leftTable + "\";//left");
	}

	public void setRightTable(String right) {
		this.rightTable = right;
		sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + rightTable + "\";//right");
	}

	public void setResultTable(String result) {
		this.resultTable = result;
		sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + resultTable + "\";//result");
	}

	public String getLeftTable() {
		return this.leftTable;
	}

	public String getRightTable() {
		return this.rightTable;
	}

	public String getResultTable() {
		return this.resultTable;
	}

	public Set<String> getLeftTableColumns() {
		return this.leftTableColumns;
	}
	public Set<String> getRightTableColumns() {
		return this.rightTableColumns;
	}
	public Set<String> getResultTableColumns() {
		return this.resultTableColumns;
	}

	@Override
	public FormPortletField<?> getFieldByName(String name) {
		if ("type".equals(name))
			return this.typeField;
		if ("selects".equals(name))
			return this.selectsEditor.getOutputField();
		if ("on".equals(name))
			return this.onField;
		if ("wheres".equals(name))
			return this.wheresField;
		throw new NullPointerException("No such name:" + name);

	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		if (field == this.onField) {
			Formula f = (Formula) this.onField.getCorrelationData();
			return f.createMenu(formPortlet, field, cursorPosition);
		} else if (field == this.wheresField) {
			Formula f = (Formula) this.onField.getCorrelationData();
			return f.createMenu(formPortlet, field, cursorPosition);
		}
		return null;
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField field) {
		if (field == this.onField) {
			Formula f = (Formula) this.onField.getCorrelationData();
			f.onContextMenu(field, action);
		} else if (field == this.wheresField) {
			Formula f = (Formula) this.wheresField.getCorrelationData();
			f.onContextMenu(field, action);
		}
	}

	public static interface Formula {
		public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition);
		public void onContextMenu(FormPortletField field, String action);
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
			if (query.endsWith("//left")) {
				this.leftTableColumns = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					this.leftTableColumns.add((String) r.get("ColumnName"));
			} else if (query.endsWith("//right")) {
				this.rightTableColumns = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					this.rightTableColumns.add((String) r.get("ColumnName"));

			} else if (query.endsWith("//result")) {
				this.resultTableColumns = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					this.resultTableColumns.add((String) r.get("ColumnName"));
				this.selectsEditor.onResultTableColumnsChanged();
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
		typeField.setDisabled(!enable);
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
