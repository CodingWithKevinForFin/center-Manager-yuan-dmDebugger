package com.f1.ami.web.centermanager.editors.triggers;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebMenuUtils;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerAbstractEditCenterObjectPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerEditTriggerPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;

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
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletMultiCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletTextAreaField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.CH;
import com.f1.utils.SH;

public class AmiCenterManagerTriggerEditor_RelayTrigger extends AmiCenterManagerAbstractTriggerEditor
		implements FormPortletContextMenuFactory, FormPortletContextMenuListener, FormPortletListener {
	public static final int DEFAULT_ROWHEIGHT = AmiCenterManagerAbstractEditCenterObjectPortlet.DEFAULT_ROWHEIGHT;
	public static final int DEFAULT_LEFTPOS = AmiCenterManagerAbstractEditCenterObjectPortlet.DEFAULT_LEFTPOS; //164
	public static final int DEFAULT_Y_SPACING = AmiCenterManagerAbstractEditCenterObjectPortlet.DEFAULT_Y_SPACING * 3;
	public static final int DEFAULT_X_SPACING = AmiCenterManagerAbstractEditCenterObjectPortlet.DEFAULT_X_SPACING;
	public static final int DEFAULT_TOPPOS = DEFAULT_Y_SPACING + 10;

	public static final int OPTION_FORM_HEIGHT = 50;
	public static final int AMISCRIPT_FORM_HEIGHT = 600;
	public static final int AMISCRIPT_FORM_PADDING = 0;

	private static final int FORM_LEFT_POSITION = 120;
	private static final int FORM_WIDTH = 670;
	private static final int FORM_HEIGHT = 200;

	final private AmiWebService service;

	final private FormPortletTextField hostField;
	final private FormPortletTextField portField;
	final private FormPortletTextField loginField;
	final private FormPortletTextField keystoreFileField;
	final private FormPortletTextField keystorePassField;
	final private FormPortletMultiCheckboxField<String> targetField;

	final private FormPortletMultiCheckboxField<String> insertsField;
	final private FormPortletMultiCheckboxField<String> updatesField;
	final private FormPortletMultiCheckboxField<String> deletesField;

	final private FormPortletTextField whereField;

	final private FormPortletTextAreaField derivedValuesField;

	private String targetTable; //from "target" field
	private String sourceTable; //from "on"
	private Set<String> sourceTableColumns;
	private Set<String> targetTableColumns;

	public AmiCenterManagerTriggerEditor_RelayTrigger(PortletConfig config, AmiCenterManagerEditTriggerPortlet mainEditor) {
		super(config, mainEditor);
		service = AmiWebUtils.getService(getManager());
		//row 1
		hostField = form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("host")));
		hostField.setName(AmiCenterEntityConsts.GROUP_NAME_REQUIRED_FIELD);
		hostField.setWidth(180).setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_TOPPOS).setHeight(DEFAULT_ROWHEIGHT);
		hostField.setHelp("hostname of the relay instance");

		portField = form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("port")));
		portField.setName(AmiCenterEntityConsts.GROUP_NAME_REQUIRED_FIELD);
		portField.setWidth(100).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS + 260).setTopPosPx(DEFAULT_TOPPOS);
		portField.setHelp("port for the relay instance defined by the property <b><i style=\"color:blue\">ami.port</i></b>");

		loginField = form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("login")));
		loginField.setName(AmiCenterEntityConsts.GROUP_NAME_REQUIRED_FIELD);
		loginField.setWidth(100).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS + 450).setTopPosPx(DEFAULT_TOPPOS);
		loginField.setHelp("the unique id to identify the process/application");

		//row2
		keystoreFileField = form.addField(new FormPortletTextField("keystoreFile"));
		keystoreFileField.setWidth(160).setLeftPosPx(DEFAULT_LEFTPOS + 10).setTopPosPx(DEFAULT_Y_SPACING * 2 + DEFAULT_ROWHEIGHT + 10).setHeight(DEFAULT_ROWHEIGHT);
		keystoreFileField.setHelp("optional, location of a keystore file");

		keystorePassField = form.addField(new FormPortletTextField("keystorePass"));
		keystorePassField.setWidth(160).setLeftPosPx(DEFAULT_LEFTPOS + 220 + DEFAULT_X_SPACING).setTopPosPx(DEFAULT_Y_SPACING * 2 + DEFAULT_ROWHEIGHT + 10)
				.setHeight(DEFAULT_ROWHEIGHT);
		keystorePassField.setHelp(" optional, the keystore password, this will be encrypted using the <b><i style=\"color:blue\">strEncrypt</i></b> method first");

		targetField = form.addField(new FormPortletMultiCheckboxField(String.class, "target"));
		targetField.setWidth(185).setLeftPosPx(DEFAULT_LEFTPOS + 450 + DEFAULT_X_SPACING).setTopPosPx(DEFAULT_Y_SPACING * 2 + DEFAULT_ROWHEIGHT + 10).setHeight(DEFAULT_ROWHEIGHT)
				.setBgColor("#ffffff");
		targetField.setHelp("The name of the target table, if not defined assumes the same name as the source");
		sendQueryToBackend("SELECT TableName FROM SHOW TABLES WHERE DefinedBy==\"USER\";");

		//each of the following field occupies one row
		insertsField = form.addField(new FormPortletMultiCheckboxField(String.class, "inserts"));
		insertsField.setWidth(FORM_WIDTH).setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_Y_SPACING * 3 + DEFAULT_ROWHEIGHT * 2 + 10).setHeight(DEFAULT_ROWHEIGHT)
				.setBgColor("#ffffff");
		insertsField.setHelp(" comma delimited list of target columns to be sent on an onInserted event on the source table." + "<br>"
				+ " If your target table has a unique constraint, in most cases you will want to add that column(s) to this list");

		updatesField = form.addField(new FormPortletMultiCheckboxField(String.class, "updates"));
		updatesField.setWidth(FORM_WIDTH).setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_Y_SPACING * 4 + DEFAULT_ROWHEIGHT * 3 + 10).setHeight(DEFAULT_ROWHEIGHT)
				.setBgColor("#ffffff");
		updatesField.setHelp("comma delimited list of target columns to be sent on an onUpdated event on the source table. " + "<br>"
				+ "If your target table has a unique constraint, a unique identifier column(s) needs to be in this list");

		deletesField = form.addField(new FormPortletMultiCheckboxField(String.class, "deletes"));
		deletesField.setWidth(FORM_WIDTH).setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_Y_SPACING * 5 + DEFAULT_ROWHEIGHT * 4 + 10).setHeight(DEFAULT_ROWHEIGHT)
				.setBgColor("#ffffff");
		deletesField.setHelp(" comma delimited list of target columns to be sent on an onDeleted event on the source table." + "<br>"
				+ "If your target table has a unique constraint, a unique identifier column(s) needs to be in this list");

		whereField = form.addField(new FormPortletTextField("where"));
		whereField.setWidth(FORM_WIDTH).setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_Y_SPACING * 6 + DEFAULT_ROWHEIGHT * 5 + 10).setHeight(DEFAULT_ROWHEIGHT);
		whereField.setHasButton(true);
		whereField.setCorrelationData(new Formula() {
			@Override
			public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
				BasicWebMenu r = new BasicWebMenu();
				AmiWebMenuUtils.createOperatorsMenu(r, AmiWebUtils.getService(getManager()), "");
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
					WebMenu leftColumns = new BasicWebMenu("Source Table Columns", true);
					for (String c : CH.sort(sourceTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftColumns.add(new BasicWebMenuLink(c, true, "var_" + c).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(leftColumns);
				}
				if (targetTableColumns != null) {
					WebMenu rightColumns = new BasicWebMenu("Target Table Columns", true);
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

		derivedValuesField = this.form.addField(new FormPortletTextAreaField("derivedValues"));
		derivedValuesField.setWidth(FORM_WIDTH).setLeftPosPx(DEFAULT_LEFTPOS + 20).setTopPosPx(DEFAULT_Y_SPACING * 7 + DEFAULT_ROWHEIGHT * 6 + 10).setHeight(DEFAULT_ROWHEIGHT * 5);
		derivedValuesField.setHasButton(true);
		derivedValuesField.setCorrelationData(new Formula() {
			@Override
			public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
				BasicWebMenu r = new BasicWebMenu();
				AmiWebMenuUtils.createOperatorsMenu(r, AmiWebUtils.getService(getManager()), "");
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
					WebMenu leftColumns = new BasicWebMenu("Source Table Columns", true);
					for (String c : CH.sort(sourceTableColumns, SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftColumns.add(new BasicWebMenuLink(c, true, "var_" + c).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(leftColumns);
				}
				if (targetTableColumns != null) {
					WebMenu rightColumns = new BasicWebMenu("Target Table Columns", true);
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

		this.form.addFormPortletListener(this);
		this.form.addMenuListener(this);
		this.form.setMenuFactory(this);
		addChild(form, 0, 0, 1, 1);
	}
	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField field) {
		if (field == this.whereField) {
			Formula f = (Formula) this.whereField.getCorrelationData();
			f.onContextMenu(field, action);
		} else if (field == this.derivedValuesField) {
			Formula f = (Formula) this.derivedValuesField.getCorrelationData();
			f.onContextMenu(field, action);
		}

	}
	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		if (field == this.whereField) {
			Formula f = (Formula) this.whereField.getCorrelationData();
			return f.createMenu(formPortlet, field, cursorPosition);
		} else if (field == this.derivedValuesField) {
			Formula f = (Formula) this.derivedValuesField.getCorrelationData();
			return f.createMenu(formPortlet, field, cursorPosition);
		}
		return null;
	}
	@Override
	public String getKeyValuePairs() {
		StringBuilder sb = new StringBuilder();
		if (SH.is(hostField.getValue()))
			sb.append(" host = ").append(SH.doubleQuote(hostField.getValue()));
		else
			sb.append(" host = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (SH.is(portField.getValue()))
			sb.append(" port = ").append(SH.doubleQuote(portField.getValue()));
		else
			sb.append(" port = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (SH.is(loginField.getValue()))
			sb.append(" login = ").append(SH.doubleQuote(loginField.getValue()));
		else
			sb.append(" login = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		//check there is only one target or null
		String[] targets = targetField.getValue().toArray(new String[0]);
		if (targets.length == 1) {
			sb.append(" target = ").append(SH.doubleQuote(targets[0]));
		}

		if (SH.is(derivedValuesField.getValue()))
			sb.append(" derivedValues = ").append(SH.doubleQuote(derivedValuesField.getValue()));

		if (SH.is(insertsField.getValue()))
			sb.append(" inserts = ").append(SH.doubleQuote(getMultiCkboxValue(insertsField.getValue())));

		if (SH.is(updatesField.getValue()))
			sb.append(" updates = ").append(SH.doubleQuote(getMultiCkboxValue(updatesField.getValue())));

		if (SH.is(deletesField.getValue()))
			sb.append(" deletes = ").append(SH.doubleQuote(getMultiCkboxValue(deletesField.getValue())));

		if (SH.is(whereField.getValue()))
			sb.append(" where = ").append(SH.doubleQuote(whereField.getValue()));

		return sb.toString();
	}

	public static String getMultiCkboxValue(Set<String> sink) {
		StringBuilder sb = new StringBuilder();
		Iterator<String> i = sink.iterator();
		while (i.hasNext()) {
			sb.append(i.next());
			if (i.hasNext())
				sb.append(',');
		}
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

	public void onTargetTableColumnsChanged() {
		this.insertsField.clear();
		this.updatesField.clear();
		this.deletesField.clear();
		for (String targetCol : this.targetTableColumns) {
			this.insertsField.addOption(targetCol, targetCol);
			this.updatesField.addOption(targetCol, targetCol);
			this.deletesField.addOption(targetCol, targetCol);
		}
	}

	@Override
	public FormPortletField<?> getFieldByName(String name) {
		if ("host".equals(name))
			return this.hostField;
		if ("port".equals(name))
			return this.portField;
		if ("login".equals(name))
			return this.loginField;
		if ("keystoreFile".equals(name))
			return this.keystoreFileField;
		if ("keystorePass".equals(name))
			return this.keystorePassField;
		if ("target".equals(name))
			return this.targetField;
		if ("inserts".equals(name))
			return this.insertsField;
		if ("updates".equals(name))
			return this.updatesField;
		if ("deletes".equals(name))
			return this.deletesField;
		if ("derivedValues".equals(name))
			return this.derivedValuesField;
		if ("where".equals(name))
			return this.whereField;

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
				//TODO:this.selectEditor.onSourceColumnsChanged();

			} else if (query.endsWith("//target")) {
				this.targetTableColumns = new LinkedHashSet<String>();
				for (Row r : t.getRows())
					this.targetTableColumns.add((String) r.get("ColumnName"));
				onTargetTableColumnsChanged();
			} else if (query.equals("SELECT TableName FROM SHOW TABLES WHERE DefinedBy==\"USER\";")) {
				for (Row r : t.getRows()) {
					String name = (String) r.get("TableName");
					this.targetField.addOption(name, name);
				}
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

	public static interface Formula {
		public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition);
		public void onContextMenu(FormPortletField field, String action);
	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		if (field == this.targetField) {
			Set<String> target = targetField.getValue();
			if (target.size() == 1) {
				String targetTableName = target.toArray(new String[0])[0];
				sendQueryToBackend("SELECT ColumnName FROM SHOW COLUMNS WHERE TableName==\"" + targetTableName + "\";//target");
			}

		}

	}
	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}
	@Override
	public void enableEdit(boolean enable) {
		hostField.setDisabled(!enable);
		portField.setDisabled(!enable);
		loginField.setDisabled(!enable);
		keystorePassField.setDisabled(!enable);
		keystoreFileField.setDisabled(!enable);
		targetField.setDisabled(!enable);
		insertsField.setDisabled(!enable);
		updatesField.setDisabled(!enable);
		deletesField.setDisabled(!enable);
		whereField.setDisabled(!enable);
		derivedValuesField.setDisabled(!enable);
	}

	@Override
	public Set<? extends FormPortlet> getSmartEditors() {
		return CH.s();
	}
}
